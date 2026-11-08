# Architecture Decision Register (decision.md)

### Decision ID: ADR-001
**Title:** Clean Architecture with MVVM and Jetpack Compose for Android App
- **Status:** Accepted
- **Context:** Project Supervision requires robust, testable, and maintainable local-first enforcement on Android 12 to 16.
- **Decision:** Implement Clean Architecture separating Presentation (Compose + ViewModels), Domain (Models, Rules), Data (Room DAOs, Repositories), and Platform Services (VPNService, UsageStatsManager).
- **Consequences:** Ensures complete separation of UI from background enforcement services and enables modular testing without device dependencies.

---

### Decision ID: ADR-002
**Title:** Local VPNService for DNS-Level Content Interception
- **Status:** Accepted
- **Context:** Adult website filtering must operate reliably without rooting or relying on browser extensions across Chrome, Firefox, Brave, and other Android browsers.
- **Decision:** Use Android `VpnService` to capture UDP port 53 DNS queries locally. Respond with `0.0.0.0` for prohibited domains while passing through approved queries to upstream DNS (8.8.8.8) using protected sockets.
- **Consequences:** Provides system-wide domain filtering without proxying HTTPS payloads (maintaining user privacy and avoiding MITM certificate complexities). Limitation: Encrypted DNS (DoH/DoT) must be handled via fallback mitigation.

---

### Decision ID: ADR-003
**Title:** Multi-Tiered Dynamic Domain Classification Architecture
- **Status:** Accepted
- **Context:** Static blocklists quickly become outdated as new adult platforms and mirror domains emerge.
- **Decision:** Multi-layered classification:
  1. In-memory concurrent cache for zero-latency repeats.
  2. Local Room blocklist & allowlist.
  3. Bundled & downloaded curated blocklists.
  4. Heuristic on-device analysis (TLDs, high-confidence keywords).
  5. Fallback policy (Strict mode blocks unclassified domains; Balanced allows with background scan).
- **Consequences:** Eliminates external latency on known domains, avoids leaking full browsing history, and supports resilient offline filtering.

---

### Decision ID: ADR-004
**Title:** UsageStatsManager with Foreground Activity Event Polling
- **Status:** Accepted
- **Context:** App-level restrictions (e.g. 30-minute Instagram daily allowance) require tracking active usage without prohibitive battery drain.
- **Decision:** Foreground service query every 5 seconds against `UsageEvents.Event.ACTIVITY_RESUMED` combined with daily cumulative query from `UsageStatsManager`.
- **Consequences:** Accurate minute-level accounting that survives app termination; launches `RestrictionActivity` overlay when limits are exceeded.

---

### Decision ID: ADR-005
**Title:** Two-Tier Protection Modes: Standard vs Enhanced (Device Owner)
- **Status:** Accepted
- **Context:** Tamper protection and uninstall resistance without violating Android security guidelines.
- **Decision:** Provide Standard Protection (VPN + Usage Stats + Foreground Service monitoring) for any ordinary device, and Enhanced Protection utilizing `DevicePolicyManager` (Device Owner mode) for enforced policy locking where provisioned.
- **Consequences:** Clearly communicates capabilities to user without making false claims about absolute uninstallation prevention on non-provisioned devices.

---

### Decision ID: ADR-006
**Title:** Offline Enforcement and Boot-Completed Persistence
- **Status:** Accepted
- **Context:** Restrictions must not depend on an internet connection or reset upon device restart.
- **Decision:** Persist all active policies and discovered blocklist rules in Room SQLite locally. On device startup, `BootCompletedReceiver` queries active plans and immediately launches foreground services (`SupervisionVpnService` and `UsageMonitoringService`).
- **Consequences:** Fully autonomous enforcement without requiring cloud servers, surviving network outages and device reboots.

---

### Decision ID: ADR-007
**Title:** Safeguarded Essential Applications Whitelist
- **Status:** Accepted
- **Context:** Self-control restrictions must never compromise critical communication, safety, or banking capabilities.
- **Decision:** Hardcode an unmodifiable `isEssential = true` attribute for emergency dialer, phone, SMS, banking apps, and work platforms (Microsoft Teams, Outlook). Prevent user from blocking these apps even under active restriction plans.
- **Consequences:** Prevents lockout from critical emergency services, financial tools, and enterprise communication channels.

---

### Decision ID: ADR-008
**Title:** Strict Mode as Default for Unknown Domains
- **Status:** Accepted
- **Context:** Users frequently bypass standard DNS filters by visiting newly generated adult mirror domains or unlisted proxies.
- **Decision:** Default to Strict Mode where unclassified domains are temporarily blocked while dynamic classification takes place.
- **Consequences:** Closes the unknown-domain loophole while allowing legitimate domains to be permanently cached as safe once verified.

---

### Decision ID: ADR-009
**Title:** Dual-Clock Monotonic Anti-Tamper Verification
- **Status:** Accepted
- **Context:** Users often manipulate system Date & Time to fast-forward past daily restriction limits or roll back time to revive expired allowances.
- **Decision:** Compare `System.currentTimeMillis()` against hardware monotonic uptime `SystemClock.elapsedRealtime()`. If wall-clock time regresses backwards (>60s) or jumps forward without corresponding hardware elapsed uptime, trigger a `TAMPER_DETECTED` event with `HIGH` severity and preserve the active restriction state.
- **Consequences:** Neutralizes system clock bypasses while ensuring accurate usage tracking across device sleep and wake states.



