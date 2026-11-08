package com.supervision.app.service.classification

import com.supervision.app.domain.model.ClassificationCategory
import com.supervision.app.domain.model.DomainClassification
import timber.log.Timber
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Domain Intelligence Engine — classifies unknown domains using multiple sources:
 * 1. Maintained Local Blocklists (Bundled Adult Domains)
 * 2. High-Confidence On-Device Heuristic Classifier (Adult TLDs and Keywords)
 * 3. Upstream Privacy-Preserving Categorization Providers (Cloudflare Families & CleanBrowsing)
 */
@Singleton
class DomainIntelligenceEngine @Inject constructor(
    private val blocklistProvider: BlocklistProvider,
    private val heuristicClassifier: HeuristicDomainClassifier,
    private val upstreamProvider: UpstreamCategorizationProvider
) {
    suspend fun classifyDomain(domain: String): DomainClassification? {
        val normalized = domain.lowercase().removePrefix("www.").trim()
        Timber.d("Classifying domain: $normalized")

        // Source 1: Check maintained local blocklists
        val blocklistResult = blocklistProvider.checkDomain(normalized)
        if (blocklistResult != null) {
            Timber.i("Domain $normalized found in blocklist: ${blocklistResult.category}")
            return blocklistResult
        }

        // Source 2: Check high-confidence on-device heuristics
        val heuristicResult = heuristicClassifier.classify(normalized)
        if (heuristicResult != null && heuristicResult.category != ClassificationCategory.UNKNOWN &&
            heuristicResult.category != ClassificationCategory.UNCERTAIN) {
            Timber.i("Domain $normalized heuristically classified: ${heuristicResult.category}")
            return heuristicResult
        }

        // Source 3: Query external upstream categorization provider
        val upstreamResult = upstreamProvider.queryCategorization(normalized)
        if (upstreamResult != null) {
            Timber.i("Domain $normalized classified by upstream provider: ${upstreamResult.category}")
            return upstreamResult
        }

        // Fallback to uncertain heuristic if present
        if (heuristicResult != null && heuristicResult.category == ClassificationCategory.UNCERTAIN) {
            return heuristicResult
        }

        Timber.d("Domain $normalized: no classification available")
        return null
    }
}

