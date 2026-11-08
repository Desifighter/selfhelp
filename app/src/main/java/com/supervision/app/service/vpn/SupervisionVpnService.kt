package com.supervision.app.service.vpn

import android.app.PendingIntent
import android.content.Intent
import android.net.VpnService
import android.os.ParcelFileDescriptor
import androidx.core.app.NotificationCompat
import com.supervision.app.R
import com.supervision.app.SupervisionApp
import com.supervision.app.presentation.MainActivity
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import timber.log.Timber
import java.io.FileInputStream
import java.io.FileOutputStream
import java.net.DatagramPacket
import java.net.DatagramSocket
import java.net.InetAddress
import javax.inject.Inject

@AndroidEntryPoint
class SupervisionVpnService : VpnService() {

    @Inject
    lateinit var dnsInterceptor: DnsInterceptor

    private var vpnInterface: ParcelFileDescriptor? = null
    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private var isRunning = false

    companion object {
        const val ACTION_START = "com.supervision.app.VPN_START"
        const val ACTION_STOP = "com.supervision.app.VPN_STOP"
        private const val VPN_MTU = 1500
        private const val VPN_ADDRESS = "10.0.0.2"
        private const val VPN_ROUTE = "0.0.0.0"
        private const val DNS_SERVER = "10.0.0.1"
        private const val NOTIFICATION_ID = 1
        const val BLOCKED_IP = "0.0.0.0"
    }

    override fun onCreate() {
        super.onCreate()
        Timber.d("SupervisionVpnService created")
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_STOP -> {
                stopVpn()
                return START_NOT_STICKY
            }
            else -> {
                startVpn()
                return START_STICKY
            }
        }
    }

    private fun startVpn() {
        if (isRunning) {
            Timber.w("VPN already running")
            return
        }

        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.Q) {
            startForeground(
                NOTIFICATION_ID,
                createNotification(),
                android.content.pm.ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE
            )
        } else {
            startForeground(NOTIFICATION_ID, createNotification())
        }

        try {
            vpnInterface = Builder()
                .setSession("Supervision")
                .addAddress(VPN_ADDRESS, 32)
                .addRoute(VPN_ROUTE, 0)
                .addDnsServer(DNS_SERVER)
                .setMtu(VPN_MTU)
                .setBlocking(true)
                .establish()

            if (vpnInterface == null) {
                Timber.e("Failed to establish VPN interface")
                stopSelf()
                return
            }

            isRunning = true
            Timber.i("VPN interface established")

            serviceScope.launch {
                handleVpnTraffic()
            }
        } catch (e: Exception) {
            Timber.e(e, "Failed to start VPN")
            stopSelf()
        }
    }

    private suspend fun handleVpnTraffic() {
        val vpnFd = vpnInterface ?: return
        val inputStream = FileInputStream(vpnFd.fileDescriptor)
        val outputStream = FileOutputStream(vpnFd.fileDescriptor)

        val buffer = ByteArray(VPN_MTU)

        try {
            while (isRunning) {
                val length = inputStream.read(buffer)
                if (length <= 0) continue

                val packet = buffer.copyOf(length)

                if (isDnsPacket(packet)) {
                    val response = processDnsPacket(packet)
                    if (response != null) {
                        outputStream.write(response)
                        outputStream.flush()
                    }
                } else {
                    forwardPacket(packet, outputStream)
                }
            }
        } catch (e: Exception) {
            if (isRunning) {
                Timber.e(e, "VPN traffic handler error")
            }
        } finally {
            try { inputStream.close() } catch (_: Exception) {}
            try { outputStream.close() } catch (_: Exception) {}
        }
    }

    private fun isDnsPacket(packet: ByteArray): Boolean {
        if (packet.size < 28) return false

        val version = (packet[0].toInt() shr 4) and 0x0F
        if (version != 4) return false

        val protocol = packet[9].toInt() and 0xFF
        if (protocol != 17) return false

        val headerLength = (packet[0].toInt() and 0x0F) * 4
        val destPort = ((packet[headerLength + 2].toInt() and 0xFF) shl 8) or
                (packet[headerLength + 3].toInt() and 0xFF)

        return destPort == 53
    }

    private suspend fun processDnsPacket(packet: ByteArray): ByteArray? {
        try {
            val ipHeaderLength = (packet[0].toInt() and 0x0F) * 4
            val udpStart = ipHeaderLength
            val dnsPayload = packet.copyOfRange(udpStart + 8, packet.size)

            val domainName = extractDomainFromDns(dnsPayload)
            if (domainName.isNullOrEmpty()) {
                return forwardDnsAndBuildResponse(packet, dnsPayload)
            }

            Timber.d("DNS query for: $domainName")
            val result = dnsInterceptor.shouldBlock(domainName)

            return if (result.blocked) {
                Timber.i("Blocking DNS for: $domainName (reason: ${result.reason})")
                buildBlockedDnsResponse(packet, dnsPayload)
            } else {
                forwardDnsAndBuildResponse(packet, dnsPayload)
            }
        } catch (e: Exception) {
            Timber.e(e, "Error processing DNS packet")
            return null
        }
    }

    private fun extractDomainFromDns(dnsPayload: ByteArray): String? {
        try {
            if (dnsPayload.size < 12) return null

            var offset = 12
            val parts = mutableListOf<String>()

            while (offset < dnsPayload.size) {
                val labelLength = dnsPayload[offset].toInt() and 0xFF
                if (labelLength == 0) break
                if (offset + 1 + labelLength > dnsPayload.size) return null

                parts.add(String(dnsPayload, offset + 1, labelLength))
                offset += 1 + labelLength
            }

            return if (parts.isNotEmpty()) parts.joinToString(".").lowercase() else null
        } catch (e: Exception) {
            Timber.e(e, "Failed to extract domain from DNS")
            return null
        }
    }

    private fun buildBlockedDnsResponse(originalPacket: ByteArray, dnsPayload: ByteArray): ByteArray {
        val responsePayload = ByteArray(dnsPayload.size + 16)
        System.arraycopy(dnsPayload, 0, responsePayload, 0, dnsPayload.size)

        responsePayload[2] = (0x81).toByte()
        responsePayload[3] = (0x80).toByte()

        responsePayload[6] = 0
        responsePayload[7] = 1

        var answerOffset = dnsPayload.size
        responsePayload[answerOffset++] = 0xC0.toByte()
        responsePayload[answerOffset++] = 0x0C.toByte()
        responsePayload[answerOffset++] = 0x00
        responsePayload[answerOffset++] = 0x01
        responsePayload[answerOffset++] = 0x00
        responsePayload[answerOffset++] = 0x01
        responsePayload[answerOffset++] = 0x00
        responsePayload[answerOffset++] = 0x00
        responsePayload[answerOffset++] = 0x01
        responsePayload[answerOffset++] = 0x2C.toByte()
        responsePayload[answerOffset++] = 0x00
        responsePayload[answerOffset++] = 0x04
        responsePayload[answerOffset++] = 0x00
        responsePayload[answerOffset++] = 0x00
        responsePayload[answerOffset++] = 0x00
        responsePayload[answerOffset++] = 0x00

        val finalPayload = responsePayload.copyOf(answerOffset)
        return buildIpUdpPacket(originalPacket, finalPayload, swapAddresses = true)
    }

    private fun forwardDnsAndBuildResponse(originalPacket: ByteArray, dnsPayload: ByteArray): ByteArray? {
        try {
            val socket = DatagramSocket()
            protect(socket)

            val upstreamDns = InetAddress.getByName("8.8.8.8")
            val request = DatagramPacket(dnsPayload, dnsPayload.size, upstreamDns, 53)
            socket.soTimeout = 5000
            socket.send(request)

            val responseBuffer = ByteArray(1024)
            val response = DatagramPacket(responseBuffer, responseBuffer.size)
            socket.receive(response)
            socket.close()

            val responseData = responseBuffer.copyOf(response.length)
            return buildIpUdpPacket(originalPacket, responseData, swapAddresses = true)
        } catch (e: Exception) {
            Timber.e(e, "Failed to forward DNS query")
            return null
        }
    }

    private fun buildIpUdpPacket(
        originalPacket: ByteArray,
        payload: ByteArray,
        swapAddresses: Boolean
    ): ByteArray {
        val ipHeaderLength = (originalPacket[0].toInt() and 0x0F) * 4
        val totalLength = ipHeaderLength + 8 + payload.size
        val result = ByteArray(totalLength)

        System.arraycopy(originalPacket, 0, result, 0, ipHeaderLength)

        result[2] = ((totalLength shr 8) and 0xFF).toByte()
        result[3] = (totalLength and 0xFF).toByte()

        if (swapAddresses) {
            for (i in 0 until 4) {
                val temp = result[12 + i]
                result[12 + i] = result[16 + i]
                result[16 + i] = temp
            }
        }

        result[10] = 0
        result[11] = 0

        var checksum = 0L
        for (i in 0 until ipHeaderLength step 2) {
            checksum += ((result[i].toInt() and 0xFF) shl 8) or (result[i + 1].toInt() and 0xFF)
        }
        while (checksum > 0xFFFF) {
            checksum = (checksum and 0xFFFF) + (checksum shr 16)
        }
        val ipChecksum = checksum.inv().toInt() and 0xFFFF
        result[10] = ((ipChecksum shr 8) and 0xFF).toByte()
        result[11] = (ipChecksum and 0xFF).toByte()

        val udpStart = ipHeaderLength
        if (swapAddresses) {
            val srcPort0 = originalPacket[udpStart]
            val srcPort1 = originalPacket[udpStart + 1]
            result[udpStart] = originalPacket[udpStart + 2]
            result[udpStart + 1] = originalPacket[udpStart + 3]
            result[udpStart + 2] = srcPort0
            result[udpStart + 3] = srcPort1
        }

        val udpLength = 8 + payload.size
        result[udpStart + 4] = ((udpLength shr 8) and 0xFF).toByte()
        result[udpStart + 5] = (udpLength and 0xFF).toByte()

        result[udpStart + 6] = 0
        result[udpStart + 7] = 0

        System.arraycopy(payload, 0, result, udpStart + 8, payload.size)
        return result
    }

    private fun forwardPacket(packet: ByteArray, outputStream: FileOutputStream) {
        try {
            outputStream.write(packet)
            outputStream.flush()
        } catch (e: Exception) {
            Timber.e(e, "Error forwarding packet")
        }
    }

    private fun stopVpn() {
        isRunning = false
        try {
            vpnInterface?.close()
        } catch (e: Exception) {
            Timber.e(e, "Error closing VPN interface")
        }
        vpnInterface = null
        serviceScope.cancel()
        stopForeground(STOP_FOREGROUND_REMOVE)
        stopSelf()
        Timber.i("VPN stopped")
    }

    private fun createNotification(): android.app.Notification {
        val pendingIntent = PendingIntent.getActivity(
            this,
            0,
            Intent(this, MainActivity::class.java),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        return NotificationCompat.Builder(this, SupervisionApp.CHANNEL_VPN)
            .setContentTitle("Supervision Active")
            .setContentText("Website filtering is active")
            .setSmallIcon(R.drawable.ic_shield)
            .setContentIntent(pendingIntent)
            .setOngoing(true)
            .setSilent(true)
            .build()
    }

    override fun onDestroy() {
        stopVpn()
        super.onDestroy()
    }

    override fun onRevoke() {
        Timber.w("VPN permission revoked")
        stopVpn()
        super.onRevoke()
    }
}

