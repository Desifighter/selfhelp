package com.supervision.app.domain.model

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.Instant
import java.time.temporal.ChronoUnit

class DomainClassificationModelTest {

    @Test
    fun `confirmed adult category is considered blocked`() {
        val classification = DomainClassification(
            domain = "badsite.xxx",
            category = ClassificationCategory.PORNOGRAPHY,
            status = ClassificationStatus.CONFIRMED,
            source = ClassificationSource.BLOCKLIST
        )

        assertTrue(classification.isBlocked)
    }

    @Test
    fun `pending adult category is not considered confirmed blocked`() {
        val classification = DomainClassification(
            domain = "unknownsite.com",
            category = ClassificationCategory.PORNOGRAPHY,
            status = ClassificationStatus.PENDING,
            source = ClassificationSource.ON_DEVICE
        )

        assertFalse(classification.isBlocked)
    }

    @Test
    fun `safe category is never blocked`() {
        val classification = DomainClassification(
            domain = "wikipedia.org",
            category = ClassificationCategory.SAFE,
            status = ClassificationStatus.CONFIRMED,
            source = ClassificationSource.ALLOWLIST
        )

        assertFalse(classification.isBlocked)
    }

    @Test
    fun `expired classification reports needsReclassification`() {
        val expired = DomainClassification(
            domain = "temp.com",
            category = ClassificationCategory.PORNOGRAPHY,
            status = ClassificationStatus.EXPIRED,
            source = ClassificationSource.ON_DEVICE
        )

        assertTrue(expired.needsReclassification)

        val pastExpiresAt = DomainClassification(
            domain = "temp2.com",
            category = ClassificationCategory.PORNOGRAPHY,
            status = ClassificationStatus.CONFIRMED,
            source = ClassificationSource.ON_DEVICE,
            expiresAt = Instant.now().minus(1, ChronoUnit.HOURS)
        )

        assertTrue(pastExpiresAt.needsReclassification)
    }
}

