package com.supervision.app.service.classification

import com.supervision.app.domain.model.ClassificationCategory
import com.supervision.app.domain.model.ClassificationSource
import com.supervision.app.domain.model.ClassificationStatus
import com.supervision.app.domain.model.DomainClassification
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import timber.log.Timber
import java.net.DatagramPacket
import java.net.DatagramSocket
import java.net.InetAddress
import java.time.Instant
import java.time.temporal.ChronoUnit
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Upstream Categorization Provider — verifies domain categorization by querying
 * privacy-focused, zero-logging adult filtering DNS providers (Cloudflare Families and CleanBrowsing).
 *
 * Cloudflare 1.1.1.3 returns 0.0.0.0 when a domain is categorized as adult/pornography.
 * CleanBrowsing 185.228.168.168 returns 185.228.168.10 or 0.0.0.0 for adult content.
 *
 * This provides deterministic, low-latency, privacy-first dynamic classification
 * without requiring any third-party API keys or sending full URLs.
 */
@Singleton
class UpstreamCategorizationProvider @Inject constructor() {

    companion object {
        private const val CLOUDFLARE_FAMILY_DNS = "1.1.1.3"
        private const val CLEANBROWSING_ADULT_DNS = "185.228.168.168"
        private const val DNS_PORT = 53
        private const val TIMEOUT_MS = 3000
        private const val BLOCKED_IP_ZERO = "0.0.0.0"
        private const val CLEANBROWSING_BLOCK_IP = "185.228.168.10"
    }

    suspend fun queryCategorization(domain: String): DomainClassification? = withContext(Dispatchers.IO) {
        val normalized = domain.lowercase().removePrefix("www.").trim()

        try {
            // Check Cloudflare 1.1.1.3
            val isCloudflareBlocked = checkDnsFilter(normalized, CLOUDFLARE_FAMILY_DNS)
            if (isCloudflareBlocked) {
                Timber.i("Domain $normalized classified as PORNOGRAPHY by Cloudflare Families")
                return@withContext DomainClassification(
                    domain = normalized,
                    category = ClassificationCategory.PORNOGRAPHY,
                    status = ClassificationStatus.CONFIRMED,
                    source = ClassificationSource.EXTERNAL_API,
                    confidence = 0.98f,
                    firstDetectedAt = Instant.now(),
                    lastClassifiedAt = Instant.now(),
                    expiresAt = Instant.now().plus(60, ChronoUnit.DAYS),
                    isAutomatic = true
                )
            }

            // Cross-check with CleanBrowsing Adult Filter
            val isCleanBrowsingBlocked = checkDnsFilter(normalized, CLEANBROWSING_ADULT_DNS)
            if (isCleanBrowsingBlocked) {
                Timber.i("Domain $normalized classified as PORNOGRAPHY by CleanBrowsing")
                return@withContext DomainClassification(
                    domain = normalized,
                    category = ClassificationCategory.PORNOGRAPHY,
                    status = ClassificationStatus.CONFIRMED,
                    source = ClassificationSource.EXTERNAL_API,
                    confidence = 0.98f,
                    firstDetectedAt = Instant.now(),
                    lastClassifiedAt = Instant.now(),
                    expiresAt = Instant.now().plus(60, ChronoUnit.DAYS),
                    isAutomatic = true
                )
            }

            // Both filtering services resolved normally — domain is not flagged as adult
            return@withContext DomainClassification(
                domain = normalized,
                category = ClassificationCategory.SAFE,
                status = ClassificationStatus.CONFIRMED,
                source = ClassificationSource.EXTERNAL_API,
                confidence = 0.90f,
                firstDetectedAt = Instant.now(),
                lastClassifiedAt = Instant.now(),
                expiresAt = Instant.now().plus(30, ChronoUnit.DAYS),
                isAutomatic = true
            )
        } catch (e: Exception) {
            Timber.w(e, "Categorization query failed for $normalized: ${e.message}")
            return@withContext null
        }
    }

    private fun checkDnsFilter(domain: String, dnsServerIp: String): Boolean {
        var socket: DatagramSocket? = null
        try {
            socket = DatagramSocket()
            socket.soTimeout = TIMEOUT_MS

            val query = buildDnsQuery(domain)
            val server = InetAddress.getByName(dnsServerIp)
            val packet = DatagramPacket(query, query.size, server, DNS_PORT)
            socket.send(packet)

            val responseBuffer = ByteArray(512)
            val responsePacket = DatagramPacket(responseBuffer, responseBuffer.size)
            socket.receive(responsePacket)

            val resolvedIps = extractIpsFromDnsResponse(responseBuffer, responsePacket.length)

            // If response resolved to 0.0.0.0 or CleanBrowsing block IP, it is blocked!
            for (ip in resolvedIps) {
                if (ip == BLOCKED_IP_ZERO || ip == CLEANBROWSING_BLOCK_IP || ip.startsWith("127.")) {
                    return true
                }
            }
            return false
        } catch (e: Exception) {
            Timber.d("DNS filter check against $dnsServerIp failed for $domain")
            return false
        } finally {
            socket?.close()
        }
    }

    private fun buildDnsQuery(domain: String): ByteArray {
        val domainParts = domain.split(".")
        var totalLength = 12 // Header
        for (part in domainParts) {
            totalLength += 1 + part.length
        }
        totalLength += 1 // Root null byte
        totalLength += 4 // Type A (2) + Class IN (2)

        val query = ByteArray(totalLength)
        // Transaction ID
        query[0] = 0x12
        query[1] = 0x34
        // Flags: RD=1 (Recursion Desired)
        query[2] = 0x01
        query[3] = 0x00
        // QDCOUNT = 1
        query[4] = 0x00
        query[5] = 0x01

        var offset = 12
        for (part in domainParts) {
            query[offset++] = part.length.toByte()
            val bytes = part.toByteArray(Charsets.US_ASCII)
            System.arraycopy(bytes, 0, query, offset, bytes.size)
            offset += bytes.size
        }
        query[offset++] = 0x00 // Null terminator

        // QTYPE = A (1)
        query[offset++] = 0x00
        query[offset++] = 0x01
        // QCLASS = IN (1)
        query[offset++] = 0x00
        query[offset] = 0x01

        return query
    }

    private fun extractIpsFromDnsResponse(response: ByteArray, length: Int): List<String> {
        val ips = mutableListOf<String>()
        try {
            if (length < 12) return ips

            val anCount = ((response[6].toInt() and 0xFF) shl 8) or (response[7].toInt() and 0xFF)
            if (anCount == 0) return ips

            // Skip header and question section
            var offset = 12
            while (offset < length && response[offset].toInt() != 0) {
                val labelLen = response[offset].toInt() and 0xFF
                if ((labelLen and 0xC0) == 0xC0) {
                    offset += 2
                    break
                }
                offset += 1 + labelLen
            }
            if (offset < length && response[offset].toInt() == 0) {
                offset += 1 // Skip null byte
            }
            offset += 4 // Skip QTYPE and QCLASS

            // Parse Answer section
            for (i in 0 until anCount) {
                if (offset >= length) break

                // Name (can be pointer or label)
                if ((response[offset].toInt() and 0xC0) == 0xC0) {
                    offset += 2
                } else {
                    while (offset < length && response[offset].toInt() != 0) {
                        offset += 1 + (response[offset].toInt() and 0xFF)
                    }
                    offset += 1
                }

                if (offset + 10 > length) break

                val type = ((response[offset].toInt() and 0xFF) shl 8) or (response[offset + 1].toInt() and 0xFF)
                offset += 8 // Type (2), Class (2), TTL (4)

                val rdLength = ((response[offset].toInt() and 0xFF) shl 8) or (response[offset + 1].toInt() and 0xFF)
                offset += 2

                if (type == 1 && rdLength == 4 && offset + 4 <= length) { // IPv4 Address
                    val ip = "${response[offset].toInt() and 0xFF}.${response[offset + 1].toInt() and 0xFF}." +
                            "${response[offset + 2].toInt() and 0xFF}.${response[offset + 3].toInt() and 0xFF}"
                    ips.add(ip)
                }
                offset += rdLength
            }
        } catch (e: Exception) {
            Timber.d("Error parsing DNS response: ${e.message}")
        }
        return ips
    }
}

