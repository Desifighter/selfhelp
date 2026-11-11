package com.supervision.app.service.classification

import com.supervision.app.domain.model.ClassificationCategory
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class HeuristicDomainClassifierTest {

    private lateinit var classifier: HeuristicDomainClassifier

    @Before
    fun setup() {
        classifier = HeuristicDomainClassifier()
    }

    @Test
    fun `safe overrides are never classified as adult`() {
        val safeDomains = listOf(
            "google.com",
            "www.google.com",
            "wikipedia.org",
            "healthline.com",
            "education.com",
            "coursera.org",
            "who.int",
            "nih.gov"
        )

        for (domain in safeDomains) {
            val result = classifier.classify(domain)
            assertNotNull("Domain $domain should have a result", result)
            assertEquals("Domain $domain must be classified SAFE", ClassificationCategory.SAFE, result?.category)
        }
    }

    @Test
    fun `adult TLDs are classified as PORNOGRAPHY with high confidence`() {
        val adultTldDomains = listOf(
            "content.xxx",
            "portal.porn",
            "members.adult"
        )

        for (domain in adultTldDomains) {
            val result = classifier.classify(domain)
            assertNotNull("Domain $domain should be classified", result)
            assertEquals("Domain $domain must be PORNOGRAPHY", ClassificationCategory.PORNOGRAPHY, result?.category)
            assertTrue("Confidence must be >= 0.9", (result?.confidence ?: 0f) >= 0.9f)
        }
    }

    @Test
    fun `strong adult keywords are classified as PORNOGRAPHY`() {
        val keywordDomains = listOf(
            "randompornsite.com",
            "xxxmovieshub.net",
            "myxvideosstream.org",
            "desibabe.co",
            "desisexclips.com",
            "bhabhixxx.net"
        )

        for (domain in keywordDomains) {
            val result = classifier.classify(domain)
            assertNotNull("Domain $domain should be classified", result)
            assertEquals("Domain $domain must be PORNOGRAPHY", ClassificationCategory.PORNOGRAPHY, result?.category)
        }
    }

    @Test
    fun `unrelated benign domains return null for heuristic classification`() {
        val benignDomains = listOf(
            "acme-widgets-corp.org",
            "myrecipesbook.net",
            "weather-forecast-live.io"
        )

        for (domain in benignDomains) {
            val result = classifier.classify(domain)
            assertNull("Benign domain $domain should return null from heuristics", result)
        }
    }
}

