# Project Supervision — Privacy-First Autonomous Digital Self-Control

[![Target SDK](https://img.shields.io/badge/Target%20SDK-Android%2016%20(API%2036)-green.svg)](https://developer.android.com)
[![Min SDK](https://img.shields.io/badge/Min%20SDK-Android%2012%20(API%2031)-blue.svg)](https://developer.android.com)
[![Kotlin](https://img.shields.io/badge/Kotlin-2.1.0-purple.svg)](https://kotlinlang.org)
[![Compose](https://img.shields.io/badge/UI-Jetpack%20Compose%20(M3)-brightgreen.svg)](https://developer.android.com/jetpack/compose)
[![Architecture](https://img.shields.io/badge/Architecture-Clean%20%2B%20MVVM-orange.svg)](https://developer.android.com)

Supervision is a privacy-first, on-device Android digital self-control application designed to help users eliminate compulsive browsing, adult content consumption, doomscrolling, and social media addiction.

The application allows users to independently install, configure, activate, and lock restrictions **without requiring a second person, external accountability partner, or cloud backend**.

---

## Key Features

### 1. Dynamic Adult Website Detection & Auto-Blocking
- **Local DNS Interception:** Uses Android `VpnService` to capture UDP port 53 DNS queries locally without proxying HTTPS payloads, keeping browsing private and fast.
- **NXDOMAIN/0.0.0.0 Response:** Instantly blocks prohibited domains at the packet level.
- **Multi-Tier Classification Engine:**
  1. High-speed in-memory concurrent cache for zero-latency repeats.
  2. Local Room blocklist & allowlist.
  3. Bundled adult-domain database (`assets/blocklists/adult_domains.txt`).
  4. On-device heuristics detecting adult TLDs (`.xxx`, `.porn`, `.adult`) and keywords with high confidence.
  5. Privacy-preserving upstream queries against Cloudflare Families (`1.1.1.3`) and CleanBrowsing (`185.228.168.168`).
  6. Automatic persistence: Newly discovered adult domains are permanently saved to Room and automatically applied to all subdomains.
- **Configurable Unknown-Domain Policies:**
  - **Strict Mode (Default):** Temporarily blocks unclassified domains until verified.
  - **Balanced Mode:** Allows unknown domains with background classification.

### 2. Social Media Limits & Combined Accounting
- **Instagram Daily Limit (Default: 30 minutes):** Combined accounting tracking both foreground app usage and browser visits (`instagram.com`, `cdninstagram.com`). Once 30 minutes is exhausted, access is blocked across both the app and all Android browsers.
- **X / Twitter & Reddit Restrictions:** Complete platform blocking or selective content restrictions.
- **5-Minute Warning:** Proactive notification before allowances expire.

### 3. Application Monitoring & Protection
- **Foreground Polling:** `UsageMonitoringService` checks foreground application usage every 5 seconds.
- **Essential Apps Whitelist:** Protected communication and financial tools (Microsoft Teams, Outlook, Phone, Messages, Emergency Dialers, UPI/Banking) can never be accidentally blocked.
- **Enforcement Overlay:** Launches `RestrictionActivity` immediately when prohibited apps or limit-exceeded apps are opened.

### 4. Anti-Tamper & Persistence
- **Boot Persistence:** `BootCompletedReceiver` automatically launches filtering and monitoring services on device startup.
- **Dual-Clock Tamper Detection:** Compares system wall-clock time with hardware monotonic uptime (`SystemClock.elapsedRealtime()`) to neutralize clock rollback or jump bypasses.
- **Two Protection Modes:**
  - **Standard Protection:** Works on any ordinary Android device using standard permissions.
  - **Enhanced Protection:** Leverages `DevicePolicyManager` (Device Owner mode) to lock uninstallation during active protection plans.

---

## Architecture Overview

```
                                  +-----------------------------+
                                  |   Jetpack Compose UI        |
                                  | (Dashboard, Onboarding,     |
                                  |  Websites, Apps, Settings)  |
                                  +--------------+--------------+
                                                 |
                                  +--------------v--------------+
                                  |         ViewModels          |
                                  +--------------+--------------+
                                                 |
                   +-----------------------------+-----------------------------+
                   |                                                           |
+------------------v-------------------+                   +-------------------v------------------+
|           Domain Layer               |                   |         Platform Services            |
| - DomainClassification (Model)       |                   | - SupervisionVpnService (UDP 53)     |
| - AppRestriction (Model)             |                   | - DnsInterceptor (Cache + Fallback)  |
| - ProtectionPlan (Model)             |                   | - DomainIntelligenceEngine           |
| - UsageRecord (Model)                |                   | - UsageMonitoringService (5s loop)   |
| - ProtectionStatus (Model)           |                   | - BootCompletedReceiver (Auto-start) |
+------------------+-------------------+                   | - DeviceSecurityManager (DeviceOwner)|
                   |                                       | - TamperDetector & Watchdog          |
+------------------v-------------------+                   +-------------------+------------------+
|            Data Layer                |                                       |
| - Room Database (SupervisionDb)      |                                       |
| - 6 DAOs & Entities                  |<--------------------------------------+
| - Repositories (Domain, App, Plan)   |
| - Bundled Adult Domain Blocklist     |
+--------------------------------------+
```

---

## Verified Hardware Specifications

- **Device:** Motorola Moto G85 5G (`malmo_g`)
- **Target OS:** Android 16 (API 36)
- **Min Supported OS:** Android 12 (API 31)
- **Architecture:** ARM64 / x86_64

---

## Project Documentation
- [`flow.md`](flow.md) — Real-time development execution state, milestones, and hardware status.
- [`decision.md`](decision.md) — Architecture Decision Register (ADR-001 through ADR-009).

