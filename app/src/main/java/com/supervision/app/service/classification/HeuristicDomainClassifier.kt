package com.supervision.app.service.classification

import com.supervision.app.domain.model.ClassificationCategory
import com.supervision.app.domain.model.ClassificationSource
import com.supervision.app.domain.model.ClassificationStatus
import com.supervision.app.domain.model.DomainClassification
import timber.log.Timber
import java.time.Instant
import java.time.temporal.ChronoUnit
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class HeuristicDomainClassifier @Inject constructor() {

    private val adultTlds = setOf(
        ".xxx", ".porn", ".sex", ".adult", ".sexy"
    )

    private val strongAdultKeywords = setOf(
        "porn", "xxx", "xvideo", "xnxx", "xhamster", "redtube",
        "youporn", "hentai", "milf", "brazzers", "bangbros",
        "chaturbate", "livejasmin", "stripchat", "camgirl",
        "onlyfans", "fansly", "manyvids", "pornstar",
        "spankbang", "tube8", "porntube", "sextube"
    )

    private val weakAdultKeywords = setOf(
        "nsfw", "nude", "naked", "erotic", "fetish", "bdsm"
    )

    private val safeOverrides = setOf(
        // Search & Infrastructure
        "google.com", "wikipedia.org", "amazon.com", "apple.com",
        "github.com", "gitlab.com", "stackoverflow.com", "medium.com",
        "cloudflare.com", "amazonaws.com", "akamai.net",
        "googleapis.com", "gstatic.com", "googleusercontent.com",
        "fbcdn.net", "cdninstagram.com", "twimg.com",
        "cloudflare-dns.com", "one.one.one.one", "dns.google",

        // Speed Test & Streaming
        "fast.com", "speedtest.net", "ooklaserver.net",
        "netflix.com", "nflxext.com", "nflxvideo.net", "spotify.com", "twitch.tv",

        // Work, Corporate Portal & Microsoft MDM / Intune
        "microsoft.com", "microsoftonline.com", "msauth.net", "msftauth.net",
        "windows.net", "office.com", "office365.com", "azure.com", "live.com",
        "sharepoint.com", "manage.microsoft.com", "microsoftapp.net",
        "azureedge.net", "trafficmanager.net", "companyportal.microsoft.com",
        "intuneweb.azurewebsites.net", "teams.microsoft.com", "onedrive.com",

        // Communication & Productivity
        "zoom.us", "webex.com", "slack.com", "atlassian.net", "jira.com",
        "salesforce.com", "notion.so", "figma.com", "box.com", "dropbox.com",
        "whatsapp.com", "telegram.org", "linkedin.com",

        // Health & Education
        "healthline.com", "webmd.com", "mayoclinic.org",
        "nih.gov", "cdc.gov", "who.int",
        "education.com", "coursera.org", "khanacademy.org",

        // Essential Services & Railway Ticketing (IRCTC / CRIS)
        "irctc.co.in", "cris.org.in", "indianrail.gov.in", "irctc.com"
    )

    fun classify(domain: String): DomainClassification? {
        val normalized = domain.lowercase()

        if (isSafeOverride(normalized)) {
            return DomainClassification(
                domain = normalized,
                category = ClassificationCategory.SAFE,
                status = ClassificationStatus.CONFIRMED,
                source = ClassificationSource.ON_DEVICE,
                confidence = 1.0f,
                isAutomatic = true
            )
        }

        for (tld in adultTlds) {
            if (normalized.endsWith(tld)) {
                Timber.d("Domain $normalized has adult TLD: $tld")
                return DomainClassification(
                    domain = normalized,
                    category = ClassificationCategory.PORNOGRAPHY,
                    status = ClassificationStatus.CONFIRMED,
                    source = ClassificationSource.ON_DEVICE,
                    confidence = 0.95f,
                    expiresAt = Instant.now().plus(30, ChronoUnit.DAYS),
                    isAutomatic = true
                )
            }
        }

        val domainWithoutTld = normalized.substringBeforeLast(".")
        for (keyword in strongAdultKeywords) {
            if (domainWithoutTld.contains(keyword)) {
                Timber.d("Domain $normalized contains strong adult keyword: $keyword")
                return DomainClassification(
                    domain = normalized,
                    category = ClassificationCategory.PORNOGRAPHY,
                    status = ClassificationStatus.CONFIRMED,
                    source = ClassificationSource.ON_DEVICE,
                    confidence = 0.85f,
                    expiresAt = Instant.now().plus(7, ChronoUnit.DAYS),
                    isAutomatic = true
                )
            }
        }

        for (keyword in weakAdultKeywords) {
            if (domainWithoutTld.contains(keyword)) {
                Timber.d("Domain $normalized contains weak adult keyword: $keyword")
                return DomainClassification(
                    domain = normalized,
                    category = ClassificationCategory.UNCERTAIN,
                    status = ClassificationStatus.PENDING,
                    source = ClassificationSource.ON_DEVICE,
                    confidence = 0.5f,
                    expiresAt = Instant.now().plus(1, ChronoUnit.DAYS),
                    isAutomatic = true
                )
            }
        }

        return null
    }

    private fun isSafeOverride(domain: String): Boolean {
        if (safeOverrides.contains(domain)) return true
        for (safeDomain in safeOverrides) {
            if (domain.endsWith(".$safeDomain")) return true
        }
        return false
    }
}

