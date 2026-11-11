package com.supervision.app.service.vpn

import android.app.PendingIntent
import android.content.Intent
import android.net.VpnService
import android.os.ParcelFileDescriptor
import androidx.core.app.NotificationCompat
import com.supervision.app.R
import com.supervision.app.SupervisionApp
import com.supervision.app.presentation.MainActivity
import com.supervision.app.data.repository.BlockedRequestRepository
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
import java.util.concurrent.ConcurrentHashMap
import javax.inject.Inject

@AndroidEntryPoint
class SupervisionVpnService : VpnService() {

    @Inject
    lateinit var dnsInterceptor: DnsInterceptor

    @Inject
    lateinit var blockedRequestRepository: BlockedRequestRepository

    private var vpnInterface: ParcelFileDescriptor? = null
    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private var isRunning = false

    private data class CachedDnsRecord(
        val payload: ByteArray,
        val isBlocked: Boolean,
        val timestamp: Long = System.currentTimeMillis()
    )

    private val dnsMemoryCache = ConcurrentHashMap<String, CachedDnsRecord>()

    companion object {
        const val ACTION_START = "com.supervision.app.VPN_START"
        const val ACTION_STOP = "com.supervision.app.VPN_STOP"
        private const val VPN_MTU = 1500
        private const val VPN_ADDRESS = "10.0.0.2"
        private const val DNS_SERVER = "10.0.0.1"
        private const val NOTIFICATION_ID = 1
        private const val DNS_CACHE_TTL_MS = 600_000L // 10 minutes high-speed cache

        private val UPSTREAM_SERVERS = listOf(
            "1.1.1.3",         // Cloudflare Families (Malware & Adult Content Blocker)
            "1.0.0.3",         // Cloudflare Families Secondary
            "185.228.168.168", // CleanBrowsing Adult Filter
            "8.8.8.8"          // Google DNS fallback
        )
    }

    override fun onCreate() {
        super.onCreate()
        Timber.d("SupervisionVpnService created")
    }

    override fun onTaskRemoved(rootIntent: Intent?) {
        super.onTaskRemoved(rootIntent)
        Timber.i("SupervisionVpnService onTaskRemoved — ensuring persistent background execution")
        try {
            val restartIntent = Intent(applicationContext, SupervisionVpnService::class.java).apply {
                action = ACTION_START
            }
            val pendingIntent = android.app.PendingIntent.getForegroundService(
                applicationContext,
                102,
                restartIntent,
                android.app.PendingIntent.FLAG_IMMUTABLE or android.app.PendingIntent.FLAG_ONE_SHOT
            )
            val alarmManager = getSystemService(android.content.Context.ALARM_SERVICE) as? android.app.AlarmManager
            alarmManager?.setExactAndAllowWhileIdle(
                android.app.AlarmManager.RTC_WAKEUP,
                System.currentTimeMillis() + 1000L,
                pendingIntent
            )
        } catch (e: Exception) {
            Timber.e(e, "Failed to schedule VPN resurrection on task removed")
        }
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
            val builder = Builder()
                .setSession("Supervision")
                .addAddress(VPN_ADDRESS, 32)
                .addRoute(DNS_SERVER, 32)
                .addDnsServer(DNS_SERVER)
                .addAddress("fd00::2", 128)
                .addRoute("fd00::1", 128)
                .addDnsServer("fd00::1")
                .setMtu(VPN_MTU)
                .setBlocking(true)

            // Essential work, notification, system and banking packages bypass VPN completely
            // Guarantees zero latency impact, zero notification delays (Teams, Outlook, FCM), and full 5G speed
            val excludedPackages = listOf(
                "com.microsoft.teams",
                "com.microsoft.office.outlook",
                "com.microsoft.windowsintune.companyportal",
                "com.microsoft.emmx",
                "com.microsoft.skydrive",
                "com.microsoft.office.officehubrow",
                "com.google.android.gms",             // FCM Push Notifications & Play Services
                "com.google.android.gsf",             // Google Services Framework
                "cris.org.in.prs.ima",                // IRCTC Rail Connect
                "org.cris.aikyam",                    // CRIS Aikyam
                "com.motorola.securevault",
                "com.android.phone",
                "com.android.dialer",
                "com.google.android.dialer",
                "com.android.messaging",
                "com.google.android.apps.messaging",
                "com.android.emergency"
            )

            for (pkg in excludedPackages) {
                try {
                    builder.addDisallowedApplication(pkg)
                    Timber.d("Disallowed application from VPN: $pkg")
                } catch (_: Exception) {}
            }

            vpnInterface = builder.establish()

            if (vpnInterface == null) {
                Timber.e("Failed to establish VPN interface")
                stopSelf()
                return
            }

            isRunning = true
            Timber.i("VPN interface established (High-Performance DNS engine with IPv4/IPv6 support)")

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
                    serviceScope.launch(Dispatchers.IO) {
                        try {
                            val response = processDnsPacket(packet)
                            if (response != null) {
                                synchronized(outputStream) {
                                    outputStream.write(response)
                                    outputStream.flush()
                                }
                            }
                        } catch (e: Exception) {
                            Timber.e(e, "Error processing DNS packet")
                        }
                    }
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
        if (version == 4) {
            val protocol = packet[9].toInt() and 0xFF
            if (protocol != 17) return false
            val headerLength = (packet[0].toInt() and 0x0F) * 4
            if (packet.size < headerLength + 8) return false
            val destPort = ((packet[headerLength + 2].toInt() and 0xFF) shl 8) or
                    (packet[headerLength + 3].toInt() and 0xFF)
            return destPort == 53
        } else if (version == 6) {
            if (packet.size < 48) return false
            val nextHeader = packet[6].toInt() and 0xFF
            if (nextHeader != 17) return false
            val destPort = ((packet[42].toInt() and 0xFF) shl 8) or
                    (packet[43].toInt() and 0xFF)
            return destPort == 53
        }
        return false
    }

    private suspend fun processDnsPacket(packet: ByteArray): ByteArray? {
        try {
            val version = (packet[0].toInt() shr 4) and 0x0F
            val ipHeaderLength = if (version == 6) 40 else (packet[0].toInt() and 0x0F) * 4
            val udpStart = ipHeaderLength
            val dnsPayload = packet.copyOfRange(udpStart + 8, packet.size)

            val parsed = extractDomainAndTypeFromDns(dnsPayload)
            val domainName = parsed?.first
            val qType = parsed?.second ?: 1

            if (domainName.isNullOrEmpty()) {
                return forwardDnsAndBuildResponse(packet, dnsPayload, null, qType)
            }

            // 1. High-speed In-Memory Cache (<0.1ms lookup)
            val cacheKey = "$domainName:$qType"
            val cached = dnsMemoryCache[cacheKey]
            if (cached != null && System.currentTimeMillis() - cached.timestamp < DNS_CACHE_TTL_MS) {
                if (cached.isBlocked) {
                    return buildBlockedDnsResponse(packet, dnsPayload, qType)
                } else if (cached.payload.size > 12) {
                    val fastResponse = cached.payload.copyOf()
                    fastResponse[0] = dnsPayload[0]
                    fastResponse[1] = dnsPayload[1]
                    return buildIpUdpPacket(packet, fastResponse, swapAddresses = true)
                }
            }

            // 2. Check local rules & blocklists
            val result = dnsInterceptor.shouldBlock(domainName)
            if (result.blocked) {
                Timber.i("Blocking DNS for: $domainName (reason: ${result.reason})")
                dnsMemoryCache[cacheKey] = CachedDnsRecord(byteArrayOf(), isBlocked = true)
                return buildBlockedDnsResponse(packet, dnsPayload, qType)
            }

            // 3. Forward to fast adult-filtering upstream resolvers (Cloudflare Families 1.1.1.3)
            return forwardDnsAndBuildResponse(packet, dnsPayload, domainName, qType)
        } catch (e: Exception) {
            Timber.e(e, "Error processing DNS packet")
            return null
        }
    }

    private fun extractDomainAndTypeFromDns(dnsPayload: ByteArray): Pair<String, Int>? {
        try {
            if (dnsPayload.size < 12) return null

            var offset = 12
            val parts = mutableListOf<String>()

            while (offset < dnsPayload.size) {
                val labelLength = dnsPayload[offset].toInt() and 0xFF
                if (labelLength == 0) {
                    offset++
                    break
                }
                if (offset + 1 + labelLength > dnsPayload.size) return null

                parts.add(String(dnsPayload, offset + 1, labelLength))
                offset += 1 + labelLength
            }

            val domain = if (parts.isNotEmpty()) parts.joinToString(".").lowercase() else return null
            val qType = if (offset + 2 <= dnsPayload.size) {
                ((dnsPayload[offset].toInt() and 0xFF) shl 8) or (dnsPayload[offset + 1].toInt() and 0xFF)
            } else 1

            return Pair(domain, qType)
        } catch (e: Exception) {
            return null
        }
    }

    private fun buildBlockedDnsResponse(originalPacket: ByteArray, dnsPayload: ByteArray, qType: Int = 1): ByteArray {
        val isAaaa = qType == 28
        val rdataLen = if (isAaaa) 16 else 4
        val responsePayload = ByteArray(dnsPayload.size + 12 + rdataLen)
        System.arraycopy(dnsPayload, 0, responsePayload, 0, dnsPayload.size)

        responsePayload[2] = (0x81).toByte()
        responsePayload[3] = (0x80).toByte()

        responsePayload[6] = 0
        responsePayload[7] = 1

        var answerOffset = dnsPayload.size
        responsePayload[answerOffset++] = 0xC0.toByte()
        responsePayload[answerOffset++] = 0x0C.toByte()

        if (isAaaa) {
            responsePayload[answerOffset++] = 0x00
            responsePayload[answerOffset++] = 0x1C.toByte() // TYPE 28 (AAAA)
        } else {
            responsePayload[answerOffset++] = 0x00
            responsePayload[answerOffset++] = 0x01.toByte() // TYPE 1 (A)
        }

        responsePayload[answerOffset++] = 0x00
        responsePayload[answerOffset++] = 0x01

        responsePayload[answerOffset++] = 0x00
        responsePayload[answerOffset++] = 0x00
        responsePayload[answerOffset++] = 0x01
        responsePayload[answerOffset++] = 0x2C.toByte()

        responsePayload[answerOffset++] = 0x00
        responsePayload[answerOffset++] = rdataLen.toByte()

        for (i in 0 until rdataLen) {
            responsePayload[answerOffset++] = 0x00
        }

        val finalPayload = responsePayload.copyOf(answerOffset)
        return buildIpUdpPacket(originalPacket, finalPayload, swapAddresses = true)
    }

    private fun forwardDnsAndBuildResponse(
        originalPacket: ByteArray,
        dnsPayload: ByteArray,
        domainName: String?,
        qType: Int
    ): ByteArray? {
        for (serverIp in UPSTREAM_SERVERS) {
            var socket: DatagramSocket? = null
            try {
                socket = DatagramSocket()
                protect(socket)
                socket.soTimeout = 1200

                val upstreamDns = InetAddress.getByName(serverIp)
                val request = DatagramPacket(dnsPayload, dnsPayload.size, upstreamDns, 53)
                socket.send(request)

                val responseBuffer = ByteArray(4096)
                val response = DatagramPacket(responseBuffer, responseBuffer.size)
                socket.receive(response)

                val responseData = responseBuffer.copyOf(response.length)

                if (isBlockedUpstreamResponse(responseData) && domainName != null) {
                    Timber.i("Upstream resolver ($serverIp) blocked adult content: $domainName")
                    dnsMemoryCache["$domainName:$qType"] = CachedDnsRecord(responseData, isBlocked = true)
                    serviceScope.launch {
                        try {
                            blockedRequestRepository.recordBlockedRequest(
                                domain = domainName,
                                category = "PORNOGRAPHY",
                                wasAutoDiscovered = true
                            )
                        } catch (_: Exception) {}
                    }
                    return buildBlockedDnsResponse(originalPacket, dnsPayload, qType)
                }

                if (domainName != null && responseData.size > 12) {
                    dnsMemoryCache["$domainName:$qType"] = CachedDnsRecord(responseData, isBlocked = false)
                }

                return buildIpUdpPacket(originalPacket, responseData, swapAddresses = true)
            } catch (_: Exception) {
                // Try next upstream resolver
            } finally {
                try { socket?.close() } catch (_: Exception) {}
            }
        }
        return null
    }

    private fun isBlockedUpstreamResponse(responseData: ByteArray): Boolean {
        if (responseData.size < 12) return false
        val anCount = ((responseData[6].toInt() and 0xFF) shl 8) or (responseData[7].toInt() and 0xFF)
        if (anCount == 0) return false

        val cleanBrowsingIp = byteArrayOf(185.toByte(), 228.toByte(), 168.toByte(), 10.toByte())
        for (i in 12 until responseData.size - 4) {
            if (responseData[i] == cleanBrowsingIp[0] &&
                responseData[i + 1] == cleanBrowsingIp[1] &&
                responseData[i + 2] == cleanBrowsingIp[2] &&
                responseData[i + 3] == cleanBrowsingIp[3]) {
                return true
            }
        }

        var offset = 12
        val qdCount = ((responseData[4].toInt() and 0xFF) shl 8) or (responseData[5].toInt() and 0xFF)
        try {
            for (q in 0 until qdCount) {
                while (offset < responseData.size) {
                    val len = responseData[offset].toInt() and 0xFF
                    if (len == 0) {
                        offset += 5
                        break
                    }
                    if ((len and 0xC0) == 0xC0) {
                        offset += 6
                        break
                    }
                    offset += 1 + len
                }
            }

            if (offset < responseData.size) {
                if ((responseData[offset].toInt() and 0xC0) == 0xC0) {
                    offset += 2
                } else {
                    while (offset < responseData.size && responseData[offset] != 0.toByte()) {
                        offset += 1 + (responseData[offset].toInt() and 0xFF)
                    }
                    offset++
                }

                if (offset + 10 <= responseData.size) {
                    val type = ((responseData[offset].toInt() and 0xFF) shl 8) or (responseData[offset + 1].toInt() and 0xFF)
                    offset += 8
                    val rdLength = ((responseData[offset].toInt() and 0xFF) shl 8) or (responseData[offset + 1].toInt() and 0xFF)
                    offset += 2

                    if (type == 1 && rdLength == 4 && offset + 4 <= responseData.size) {
                        val isAllZero = responseData[offset] == 0.toByte() &&
                                responseData[offset + 1] == 0.toByte() &&
                                responseData[offset + 2] == 0.toByte() &&
                                responseData[offset + 3] == 0.toByte()
                        if (isAllZero) return true
                    } else if (type == 28 && rdLength == 16 && offset + 16 <= responseData.size) {
                        var isAllZero = true
                        for (j in 0 until 16) {
                            if (responseData[offset + j] != 0.toByte()) {
                                isAllZero = false
                                break
                            }
                        }
                        if (isAllZero) return true
                    }
                }
            }
        } catch (_: Exception) {}

        return false
    }

    private fun buildIpUdpPacket(
        originalPacket: ByteArray,
        payload: ByteArray,
        swapAddresses: Boolean
    ): ByteArray {
        val version = (originalPacket[0].toInt() shr 4) and 0x0F
        if (version == 6) {
            val ipHeaderLength = 40
            val totalLength = ipHeaderLength + 8 + payload.size
            val result = ByteArray(totalLength)

            System.arraycopy(originalPacket, 0, result, 0, ipHeaderLength)

            val v6PayloadLength = 8 + payload.size
            result[4] = ((v6PayloadLength shr 8) and 0xFF).toByte()
            result[5] = (v6PayloadLength and 0xFF).toByte()

            if (swapAddresses) {
                for (i in 0 until 16) {
                    val temp = result[8 + i]
                    result[8 + i] = result[24 + i]
                    result[24 + i] = temp
                }
            }

            val udpStart = 40
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

