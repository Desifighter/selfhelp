package com.supervision.app.service.vpn

import com.supervision.app.data.repository.AppRestrictionRepository
import com.supervision.app.data.repository.BlockedRequestRepository
import com.supervision.app.data.repository.DomainClassificationRepository
import com.supervision.app.data.repository.UsageRepository
import com.supervision.app.domain.model.ClassificationCategory
import com.supervision.app.domain.model.RestrictionType
import com.supervision.app.domain.model.UnknownDomainPolicy
import com.supervision.app.service.classification.DomainIntelligenceEngine
import kotlinx.coroutines.CompletableDeferred
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
    private val pendingClassifications = ConcurrentHashMap<String, CompletableDeferred<BlockDecision>>()

    private var unknownDomainPolicy: UnknownDomainPolicy = UnknownDomainPolicy.BALANCED

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

        // YouTube & YouTube Short URLs check (app & web browser access)
        if (domain == "youtu.be" || domain.endsWith(".youtu.be")) {
            // youtu.be is dedicated to YouTube short URLs and links - always blocked
            return BlockDecision(
                blocked = true,
                reason = "YouTube Short URL: Access Blocked",
                category = ClassificationCategory.SOCIAL_MEDIA
            )
        }

        if (domain == "youtube.com" || domain.endsWith(".youtube.com") ||
            domain == "ytimg.com" || domain.endsWith(".ytimg.com") ||
            domain == "googlevideo.com" || domain.endsWith(".googlevideo.com")) {
            val ytRestriction = appRestrictionRepo.getRestriction("com.google.android.youtube")
            if (ytRestriction != null) {
                when (ytRestriction.restrictionType) {
                    RestrictionType.COMPLETELY_BLOCKED -> {
                        return BlockDecision(
                            blocked = true,
                            reason = "YouTube: Platform Blocked",
                            category = ClassificationCategory.SOCIAL_MEDIA
                        )
                    }
                    RestrictionType.TIME_LIMITED -> {
                        val usedToday = usageRepo.getUsageForToday("com.google.android.youtube")
                        val limit = ytRestriction.dailyLimitMinutes ?: 30
                        if (usedToday >= limit) {
                            return BlockDecision(
                                blocked = true,
                                reason = "YouTube: Daily limit ($limit min) reached",
                                category = ClassificationCategory.SOCIAL_MEDIA
                            )
                        }
                    }
                    RestrictionType.ALWAYS_ALLOWED -> { /* Proceed */ }
                }
            }
        }

        // Telegram check: app & web platform blocking (telegram.org, t.me, telesco.pe, etc.)
        if (domain == "t.me" || domain.endsWith(".t.me") ||
            domain == "telegram.org" || domain.endsWith(".telegram.org") ||
            domain == "telegram.me" || domain.endsWith(".telegram.me") ||
            domain == "telesco.pe" || domain.endsWith(".telesco.pe") ||
            domain == "telegram.dog" || domain.endsWith(".telegram.dog") ||
            domain == "tdesktop.com" || domain.endsWith(".tdesktop.com")) {
            val telegramRestriction = appRestrictionRepo.getRestriction("org.telegram.messenger")
            if (telegramRestriction != null) {
                when (telegramRestriction.restrictionType) {
                    RestrictionType.COMPLETELY_BLOCKED -> {
                        return BlockDecision(
                            blocked = true,
                            reason = "Telegram: Platform Blocked",
                            category = ClassificationCategory.SOCIAL_MEDIA
                        )
                    }
                    RestrictionType.TIME_LIMITED -> {
                        val usedToday = usageRepo.getUsageForToday("org.telegram.messenger")
                        val limit = telegramRestriction.dailyLimitMinutes ?: 0
                        if (usedToday >= limit) {
                            return BlockDecision(
                                blocked = true,
                                reason = "Telegram: Daily limit reached",
                                category = ClassificationCategory.SOCIAL_MEDIA
                            )
                        }
                    }
                    RestrictionType.ALWAYS_ALLOWED -> { /* Proceed */ }
                }
            } else {
                return BlockDecision(
                    blocked = true,
                    reason = "Telegram: Platform Blocked",
                    category = ClassificationCategory.SOCIAL_MEDIA
                )
            }
        }

        return null
    }

    private suspend fun handleUnknownDomain(domain: String): BlockDecision {
        val existing = pendingClassifications[domain]
        if (existing != null) {
            // Concurrent query (e.g. A and AAAA) while classification is running — wait for the result
            return existing.await()
        }

        val newDeferred = CompletableDeferred<BlockDecision>()
        val winner = pendingClassifications.putIfAbsent(domain, newDeferred)
        if (winner != null) {
            return winner.await()
        }

        try {
            val classification = intelligenceEngine.classifyDomain(domain)

            val decision = if (classification != null) {
                classificationRepo.saveClassification(classification)

                if (classification.isBlocked) {
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
            } else {
                when (unknownDomainPolicy) {
                    UnknownDomainPolicy.STRICT -> {
                        BlockDecision(
                            blocked = true,
                            reason = "Unclassified domain (strict mode)",
                            category = ClassificationCategory.UNKNOWN
                        )
                    }
                    UnknownDomainPolicy.BALANCED -> {
                        BlockDecision(
                            blocked = false,
                            reason = "Unclassified domain (balanced mode)",
                            category = ClassificationCategory.UNKNOWN
                        )
                    }
                }
            }

            blockCache[domain] = decision
            if (decision.blocked) {
                recordBlock(domain, decision)
            }
            newDeferred.complete(decision)
            return decision
        } catch (e: Exception) {
            val fallback = BlockDecision(false, "Classification error fallback", ClassificationCategory.UNKNOWN)
            newDeferred.complete(fallback)
            return fallback
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

