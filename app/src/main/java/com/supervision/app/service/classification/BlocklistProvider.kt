package com.supervision.app.service.classification

import android.content.Context
import com.supervision.app.domain.model.ClassificationCategory
import com.supervision.app.domain.model.ClassificationSource
import com.supervision.app.domain.model.ClassificationStatus
import com.supervision.app.domain.model.DomainClassification
import dagger.hilt.android.qualifiers.ApplicationContext
import timber.log.Timber
import java.io.BufferedReader
import java.io.InputStreamReader
import java.time.Instant
import java.util.concurrent.ConcurrentHashMap
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class BlocklistProvider @Inject constructor(
    @ApplicationContext private val context: Context
) {
    private val blockedDomains = ConcurrentHashMap<String, ClassificationCategory>()
    @Volatile
    private var isLoaded = false

    suspend fun initialize() {
        if (isLoaded) return
        loadBundledBlocklist()
        isLoaded = true
        Timber.i("Blocklist loaded with ${blockedDomains.size} domains")
    }

    fun checkDomain(domain: String): DomainClassification? {
        val category = blockedDomains[domain]
        if (category != null) {
            return createClassification(domain, category)
        }

        val parts = domain.split(".")
        for (i in 1 until parts.size - 1) {
            val parent = parts.subList(i, parts.size).joinToString(".")
            val parentCategory = blockedDomains[parent]
            if (parentCategory != null) {
                return createClassification(domain, parentCategory)
            }
        }

        return null
    }

    private fun createClassification(
        domain: String,
        category: ClassificationCategory
    ): DomainClassification {
        return DomainClassification(
            domain = domain,
            category = category,
            status = ClassificationStatus.CONFIRMED,
            source = ClassificationSource.BLOCKLIST,
            confidence = 1.0f,
            firstDetectedAt = Instant.now(),
            lastClassifiedAt = Instant.now(),
            expiresAt = null,
            isAutomatic = true
        )
    }

    private fun loadBundledBlocklist() {
        try {
            val inputStream = context.assets.open("blocklists/adult_domains.txt")
            BufferedReader(InputStreamReader(inputStream)).use { reader ->
                reader.forEachLine { line ->
                    val trimmed = line.trim()
                    if (trimmed.isNotEmpty() && !trimmed.startsWith("#")) {
                        val domain = trimmed.lowercase()
                            .removePrefix("www.")
                            .removePrefix("0.0.0.0 ")
                            .removePrefix("127.0.0.1 ")
                            .trim()
                        if (domain.isNotEmpty() && domain.contains(".")) {
                            blockedDomains[domain] = ClassificationCategory.PORNOGRAPHY
                        }
                    }
                }
            }
        } catch (e: Exception) {
            Timber.e(e, "Failed to load bundled blocklist")
            loadFallbackBlocklist()
        }
    }

    private fun loadFallbackBlocklist() {
        val criticalDomains = listOf(
            "pornhub.com", "xvideos.com", "xnxx.com", "xhamster.com",
            "redtube.com", "youporn.com", "tube8.com", "spankbang.com",
            "brazzers.com", "bangbros.com", "realitykings.com",
            "chaturbate.com", "livejasmin.com", "stripchat.com",
            "onlyfans.com", "fansly.com", "manyvids.com",
            "rule34.xxx", "e-hentai.org", "nhentai.net",
            "porntrex.com", "tnaflix.com", "drtuber.com",
            "beeg.com", "sunporno.com", "txxx.com"
        )
        criticalDomains.forEach { domain ->
            blockedDomains[domain] = ClassificationCategory.PORNOGRAPHY
        }
        Timber.w("Using fallback blocklist with ${criticalDomains.size} critical domains")
    }

    fun addDomain(domain: String, category: ClassificationCategory) {
        blockedDomains[domain.lowercase()] = category
    }

    fun removeDomain(domain: String) {
        blockedDomains.remove(domain.lowercase())
    }

    fun getDomainCount(): Int = blockedDomains.size

    fun getAllDomains(): Set<String> = blockedDomains.keys.toSet()
}
