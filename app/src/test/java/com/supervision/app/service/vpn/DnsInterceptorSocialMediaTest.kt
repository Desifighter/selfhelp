package com.supervision.app.service.vpn

import com.supervision.app.data.repository.AppRestrictionRepository
import com.supervision.app.data.repository.BlockedRequestRepository
import com.supervision.app.data.repository.DomainClassificationRepository
import com.supervision.app.data.repository.UsageRepository
import com.supervision.app.domain.model.AppRestriction
import com.supervision.app.domain.model.RestrictionType
import com.supervision.app.service.classification.DomainIntelligenceEngine
import io.mockk.coEvery
import io.mockk.mockk
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class DnsInterceptorSocialMediaTest {

    private val classificationRepo = mockk<DomainClassificationRepository>(relaxed = true)
    private val blockedRequestRepo = mockk<BlockedRequestRepository>(relaxed = true)
    private val intelligenceEngine = mockk<DomainIntelligenceEngine>(relaxed = true)
    private val appRestrictionRepo = mockk<AppRestrictionRepository>(relaxed = true)
    private val usageRepo = mockk<UsageRepository>(relaxed = true)

    private lateinit var interceptor: DnsInterceptor

    @Before
    fun setup() {
        interceptor = DnsInterceptor(
            classificationRepo = classificationRepo,
            blockedRequestRepo = blockedRequestRepo,
            intelligenceEngine = intelligenceEngine,
            appRestrictionRepo = appRestrictionRepo,
            usageRepo = usageRepo
        )
    }

    @Test
    fun `instagram web domain is blocked when daily allowance is exhausted`() = runTest {
        coEvery { appRestrictionRepo.getRestriction("com.instagram.android") } returns AppRestriction(
            packageName = "com.instagram.android",
            appName = "Instagram",
            restrictionType = RestrictionType.TIME_LIMITED,
            dailyLimitMinutes = 30
        )
        coEvery { usageRepo.getUsageForToday("com.instagram.android") } returns 35

        val decision = interceptor.shouldBlock("instagram.com")
        assertTrue("Instagram must be blocked when limit is exhausted", decision.blocked)
        assertTrue(decision.reason.contains("Daily limit"))
    }

    @Test
    fun `instagram web domain is allowed when allowance remains`() = runTest {
        coEvery { appRestrictionRepo.getRestriction("com.instagram.android") } returns AppRestriction(
            packageName = "com.instagram.android",
            appName = "Instagram",
            restrictionType = RestrictionType.TIME_LIMITED,
            dailyLimitMinutes = 30
        )
        coEvery { usageRepo.getUsageForToday("com.instagram.android") } returns 15
        coEvery { classificationRepo.isBlocked(any()) } returns false
        coEvery { intelligenceEngine.classifyDomain("instagram.com") } returns null

        val decision = interceptor.shouldBlock("instagram.com")
        // Not blocked by social media check
        assertFalse(decision.reason.contains("Daily limit"))
    }

    @Test
    fun `twitter domain is blocked when platform is configured as completely blocked`() = runTest {
        coEvery { appRestrictionRepo.getRestriction("com.twitter.android") } returns AppRestriction(
            packageName = "com.twitter.android",
            appName = "X",
            restrictionType = RestrictionType.COMPLETELY_BLOCKED
        )

        val decision = interceptor.shouldBlock("twitter.com")
        assertTrue("Twitter must be blocked when platform blocked", decision.blocked)
        assertTrue(decision.reason.contains("Platform Blocked"))
    }

    @Test
    fun `reddit cdn and media domains are blocked when reddit is completely blocked`() = runTest {
        coEvery { appRestrictionRepo.getRestriction("com.reddit.frontpage") } returns AppRestriction(
            packageName = "com.reddit.frontpage",
            appName = "Reddit",
            restrictionType = RestrictionType.COMPLETELY_BLOCKED
        )

        val decision = interceptor.shouldBlock("redditmedia.com")
        assertTrue("Reddit media must be blocked", decision.blocked)
    }
}

