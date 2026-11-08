package com.supervision.app.service.vpn

import com.supervision.app.data.repository.BlockedRequestRepository
import com.supervision.app.data.repository.DomainClassificationRepository
import com.supervision.app.domain.model.ClassificationCategory
import com.supervision.app.domain.model.UnknownDomainPolicy
import com.supervision.app.service.classification.DomainIntelligenceEngine
import timber.log.Timber
import java.util.concurrent.ConcurrentHashMap
import javax.inject.Inject
import javax.inject.Singleton

data class BlockDecision(
    val blocked: Boolean,
    val reason: String,
    val category: ClassificationCategory? = null
)

@Singleton
class DnsInterceptor @Inject constructor(
    private val classificationRepo: DomainClassificationRepository,
    private val blockedRequestRepo: BlockedRequestRepository,
    private val intelligenceEngine: DomainIntelligenceEngine
) {
    private val blockCache = ConcurrentHashMap<String, BlockDecision>()
    private val pendingClassifications = ConcurrentHashMap<String, Boolean>()

    private var unknownDomainPolicy: UnknownDomainPolicy = UnknownDomainPolicy.STRICT

    fun setUnknownDomainPolicy(policy: UnknownDomainPolicy) {
        this.unknownDomainPolicy = policy
        Timber.d("Unknown domain policy set to: $policy")
    }

    suspend fun shouldBlock(domain: String): BlockDecision {
        val normalized = normalizeDomain(domain)

        blockCache[normalized]?.let { cached ->
            if (cached.blocked) {
                recordBlock(normalized, cached)
            }
            return cached
        }

        if (classificationRepo.isAllowlisted(normalized)) {
            val decision = BlockDecision(false, "Allowlisted", ClassificationCategory.SAFE)
            blockCache[normalized] = decision
            return decision
        }

        if (classificationRepo.isBlocked(normalized)) {
            val classification = classificationRepo.getClassification(normalized)
            val decision = BlockDecision(
                blocked = true,
                reason = "Blocklist: ${classification?.category}",
                category = classification?.category
            )
            blockCache[normalized] = decision
            recordBlock(normalized, decision)
            return decision
        }

        return handleUnknownDomain(normalized)
    }

    private suspend fun handleUnknownDomain(domain: String): BlockDecision {
        if (pendingClassifications.putIfAbsent(domain, true) != null) {
            return when (unknownDomainPolicy) {
                UnknownDomainPolicy.STRICT -> BlockDecision(
                    blocked = true,
                    reason = "Pending classification (strict mode)",
                    category = ClassificationCategory.UNKNOWN
                )
                UnknownDomainPolicy.BALANCED -> BlockDecision(
                    blocked = false,
                    reason = "Pending classification (balanced mode)",
                    category = ClassificationCategory.UNKNOWN
                )
            }
        }

        try {
            val classification = intelligenceEngine.classifyDomain(domain)

            if (classification != null) {
                classificationRepo.saveClassification(classification)

                val decision = if (classification.isBlocked) {
                    BlockDecision(
                        blocked = true,
                        reason = "Auto-classified: ${classification.category} (${classification.source})",
                        category = classification.category
                    )
                } else {
                    BlockDecision(
                        blocked = false,
                        reason = "Classified safe: ${classification.source}",
                        category = classification.category
                    )
                }

                blockCache[domain] = decision
                if (decision.blocked) {
                    recordBlock(domain, decision)
                }
                return decision
            }

            return when (unknownDomainPolicy) {
                UnknownDomainPolicy.STRICT -> {
                    val decision = BlockDecision(
                        blocked = true,
                        reason = "Unclassified domain (strict mode)",
                        category = ClassificationCategory.UNKNOWN
                    )
                    blockCache[domain] = decision
                    decision
                }
                UnknownDomainPolicy.BALANCED -> {
                    val decision = BlockDecision(
                        blocked = false,
                        reason = "Unclassified domain (balanced mode)",
                        category = ClassificationCategory.UNKNOWN
                    )
                    blockCache[domain] = decision
                    decision
                }
            }
        } finally {
            pendingClassifications.remove(domain)
        }
    }

    private suspend fun recordBlock(domain: String, decision: BlockDecision) {
        try {
            blockedRequestRepo.recordBlockedRequest(
                domain = domain,
                category = decision.category?.name ?: "UNKNOWN",
                wasAutoDiscovered = decision.reason.contains("Auto-classified")
            )
        } catch (e: Exception) {
            Timber.e(e, "Failed to record blocked request for $domain")
        }
    }

    fun clearCache() {
        blockCache.clear()
        Timber.d("DNS block cache cleared")
    }

    fun refreshBlocklist(blockedDomains: List<String>) {
        blockedDomains.forEach { domain ->
            blockCache[domain] = BlockDecision(
                blocked = true,
                reason = "Blocklist",
                category = ClassificationCategory.PORNOGRAPHY
            )
        }
        Timber.d("Refreshed blocklist cache with ${blockedDomains.size} domains")
    }

    private fun normalizeDomain(domain: String): String {
        return domain.lowercase()
            .removePrefix("www.")
            .trim()
    }
}
