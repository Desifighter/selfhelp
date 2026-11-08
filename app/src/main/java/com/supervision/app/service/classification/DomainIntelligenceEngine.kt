package com.supervision.app.service.classification

import com.supervision.app.domain.model.ClassificationCategory
import com.supervision.app.domain.model.DomainClassification
import timber.log.Timber
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class DomainIntelligenceEngine @Inject constructor(
    private val blocklistProvider: BlocklistProvider,
    private val heuristicClassifier: HeuristicDomainClassifier
) {
    suspend fun classifyDomain(domain: String): DomainClassification? {
        val normalized = domain.lowercase().removePrefix("www.").trim()
        Timber.d("Classifying domain: $normalized")

        val blocklistResult = blocklistProvider.checkDomain(normalized)
        if (blocklistResult != null) {
            Timber.i("Domain $normalized found in blocklist: ${blocklistResult.category}")
            return blocklistResult
        }

        val heuristicResult = heuristicClassifier.classify(normalized)
        if (heuristicResult != null && heuristicResult.category != ClassificationCategory.UNKNOWN) {
            Timber.i("Domain $normalized heuristically classified: ${heuristicResult.category}")
            return heuristicResult
        }

        Timber.d("Domain $normalized: no classification available")
        return null
    }
}
