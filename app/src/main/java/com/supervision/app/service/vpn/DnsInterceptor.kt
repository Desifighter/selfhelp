package com.supervision.app.service.vpn

import com.supervision.app.data.repository.AppRestrictionRepository
import com.supervision.app.data.repository.BlockedRequestRepository
import com.supervision.app.data.repository.DomainClassificationRepository
import com.supervision.app.data.repository.UsageRepository
import com.supervision.app.domain.model.ClassificationCategory
import com.supervision.app.domain.model.RestrictionType
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
    private val intelligenceEngine: DomainIntelligenceEngine,
    private val appRestrictionRepo: AppRestrictionRepository,
    private val usageRepo: UsageRepository
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

        // 1. Social media check with combined usage accounting (Instagram, X, Reddit)
        val socialDecision = checkSocialMediaLimits(normalized)
        if (socialDecision != null) {
            if (socialDecision.blocked) {
                recordBlock(normalized, socialDecision)
            }
            return socialDecision
        }

        // 2. In-memory cache for fast lookups
        blockCache[normalized]?.let { cached ->
            if (cached.blocked) {
                recordBlock(normalized, cached)
            }
            return cached
        }

        // 3. Check allowlist
        if (classificationRepo.isAllowlisted(normalized)) {
            val decision = BlockDecision(false, "Allowlisted", ClassificationCategory.SAFE)
            blockCache[normalized] = decision
            return decision
        }

        // 4. Check permanent blocklist (Room database)
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

        // 5. Unknown domain — initiate dynamic classification
        return handleUnknownDomain(normalized)
    }

    private suspend fun checkSocialMediaLimits(domain: String): BlockDecision? {
        // Instagram check: combined accounting for app and web browsing
        if (domain == "instagram.com" || domain.endsWith(".instagram.com") ||
            domain == "cdninstagram.com" || domain.endsWith(".cdninstagram.com")) {
            val instagramRestriction = appRestrictionRepo.getRestriction("com.instagram.android")
            if (instagramRestriction != null) {
                when (instagramRestriction.restrictionType) {
                    RestrictionType.COMPLETELY_BLOCKED -> {
                        return BlockDecision(
                            blocked = true,
                            reason = "Instagram: Platform Blocked",
                            category = ClassificationCategory.SOCIAL_MEDIA
                        )
                    }
                    RestrictionType.TIME_LIMITED -> {
                        val usedToday = usageRepo.getUsageForToday("com.instagram.android")
                        val limit = instagramRestriction.dailyLimitMinutes ?: 30
                        if (usedToday >= limit) {
                            return BlockDecision(
                                blocked = true,
                                reason = "Instagram: Daily limit ($limit min) reached",
                                category = ClassificationCategory.SOCIAL_MEDIA
                            )
                        }
                    }
                    RestrictionType.ALWAYS_ALLOWED -> { /* Proceed */ }
                }
            }
        }

        // X / Twitter check
        if (domain == "twitter.com" || domain.endsWith(".twitter.com") ||
            domain == "x.com" || domain.endsWith(".x.com") ||
            domain == "twimg.com" || domain.endsWith(".twimg.com")) {
            val twitterRestriction = appRestrictionRepo.getRestriction("com.twitter.android")
            if (twitterRestriction?.restrictionType == RestrictionType.COMPLETELY_BLOCKED) {
                return BlockDecision(
                    blocked = true,
                    reason = "X / Twitter: Platform Blocked",
                    category = ClassificationCategory.SOCIAL_MEDIA
                )
            }
        }

        // Reddit check
        if (domain == "reddit.com" || domain.endsWith(".reddit.com") ||
            domain == "redd.it" || domain.endsWith(".redd.it") ||
            domain == "redditmedia.com" || domain.endsWith(".redditmedia.com")) {
            val redditRestriction = appRestrictionRepo.getRestriction("com.reddit.frontpage")
            if (redditRestriction?.restrictionType == RestrictionType.COMPLETELY_BLOCKED) {
                return BlockDecision(
                    blocked = true,
                    reason = "Reddit: Platform Blocked",
                    category = ClassificationCategory.SOCIAL_MEDIA
                )
            }
        }

        // TikTok check
        if (domain == "tiktok.com" || domain.endsWith(".tiktok.com") ||
            domain == "tiktokcdn.com" || domain.endsWith(".tiktokcdn.com")) {
            val tiktokRestriction = appRestrictionRepo.getRestriction("com.zhiliaoapp.musically")
            if (tiktokRestriction?.restrictionType == RestrictionType.COMPLETELY_BLOCKED) {
                return BlockDecision(
                    blocked = true,
                    reason = "TikTok: Platform Blocked",
                    category = ClassificationCategory.SOCIAL_MEDIA
                )
            }
        }

        return null
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

