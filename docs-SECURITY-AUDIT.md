# OmniChat v1.9 — Security & reliability audit (from v1.8)

## Findings (prioritised) and status

| # | Sev | Area / file | Root cause | Fix | Verification |
|---|-----|-------------|-----------|-----|--------------|
| 1 | Critical | Tools — ChatViewModel.kt, Connectors.kt | Any `[[app:…]]` tag in model output ran immediately (SMS/WhatsApp/email composers, timer with SKIP_UI, clipboard overwrite, open any URL). Prompt-injected web/file content could drive actions. | `security/ToolPolicy.kt`: strict balanced-brace parser, allowlist, per-tool arg schema (types, ranges, lengths, patterns), unknown keys dropped, per-reply call/action caps, dedupe. CONFIRM-risk tools (call, sms, whatsapp, email, alarm, timer, open_url, copy) wait for an Allow/Don't allow card. Data tools: 20 s timeout, 8 KB output cap. | ToolPolicyTest (12): forged tags, nested braces, invalid args, disabled/unknown tools, repeat caps, JS/intent URLs |
| 2 | High | Secrets — Prefs.kt | API keys, fallback keys, GitHub token in plaintext SharedPreferences | `security/SecretStore.kt`: AES-256-GCM, key in Android Keystore, separate prefs file, verified write, in-memory cache, decryption failure → empty + "re-enter" warning (no crash). One-time migration that removes plaintext only after verified encrypt; failure keeps original. | SecretStoreTest (4), PrefsMigrationTest (6) |
| 3 | High | Network — Manifest, all OkHttp clients | `usesCleartextTraffic="true"` app-wide; redirects followed (x-api-key / x-goog-api-key headers are not stripped by OkHttp on cross-host redirects); unbounded response bodies | `security/NetGuard.kt` on every client: AI = HTTPS only, HTTP only to private/loopback hosts with explicit opt-in; public (search/connectors) = HTTP upgraded to HTTPS, LAN addresses refused, HTTP redirect hops blocked. AI client: redirects not followed, call timeout, bounded line/body reads. `EndpointPolicy` validates scheme/host/port/userinfo/keys-in-query before any request. | EndpointPolicyTest (6), NetGuardTest (5), ApiClientTest redirect/cleartext/local opt-in |
| 4 | High | Release — app/build.gradle.kts | Release silently signed with debug key when keystore missing; keystore path hardcoded | Signing only from `OMNICHAT_KEYSTORE_PROPERTIES` or git-ignored `keystore.properties`; no debug fallback; `verifyReleaseSigning` task (runs before assemble/package/bundle) fails on missing config, `androiddebugkey`, `debug.keystore`, or `CN=Android Debug` cert | Ran negative check (see report) |
| 5 | Medium | Attachments — ChatViewModel.kt | `readBytes()` trusted metadata size; ZIP reading had no total-inflate/entry/time limits; no content checks | `security/SafeIO.kt`: limits enforced while reading, prefix reads for text, PDF magic + text sniffing, cumulative pending limit, ZIP: entry count, total inflated bytes (incl. skipped entries), ratio, time, traversal names ignored, cancellation | SafeIOTest (11): zero-byte, endless stream, bomb, traversal, corrupted, too many entries, timeout, cancel |
| 6 | Medium | Permissions — Manifest, MainActivity | READ_MEDIA_IMAGES/VIDEO/VISUAL_USER_SELECTED + legacy storage requested at startup though the app only uses Photo Picker / SAF | Removed those 5 permissions and the startup request. Calendar/Contacts still requested only when the user enables that app. | Manifest diff; LaunchTest |
| 7 | Medium | Backup/privacy | `allowBackup=false` but no rules for Android 12+ device transfer | `dataExtractionRules` excludes everything; `fullBackupContent=false`; "Delete all data" wipes chats, memories, skills, usage, settings, secrets and Keystore key; privacy text says what goes to which provider | ChatStoreTest wipe |
| 8 | Medium | Logging — OmniApp.kt, error texts | Crash report & provider error bodies could contain keys | `Redactor` (key patterns + every stored secret) on crash file, provider errors, toasts; crash file capped 12 KB, kept ≤7 days, no device model | RedactorTest (4), ApiClientTest auth redaction |
| 9 | Medium | Prompt injection | Web results, files and tool results inserted as plain user text | Wrapped in `<untrusted_content>` + system SAFETY_GUIDE; actions still need a tap regardless | ApiClientTest delimiter test |
| 10 | Medium | Reliability — ChatViewModel.kt | Fixed 1.2 s retry on any IOException; no error taxonomy; concurrent `saveAsync` could write an older snapshot last | `ProviderErrors`: AUTH/QUOTA/RATE_LIMIT/TIMEOUT/DNS/TLS/NETWORK/SERVER/REDIRECT/CONFIG; only TIMEOUT/NETWORK retried (max 2, exponential + jitter); reply size cap; saves serialized, only newest snapshot written | ProviderErrorsTest (5), ApiClientTest (11) |
| 11 | Low | Supply chain | Gradle distribution not checksum-pinned | `distributionSha256Sum` (official value), CI wrapper-validation, dependency-review on PRs | CI config (not run here) |

## Not done / remaining risks (honest list)
- **Cleartext at OS level:** network_security_config still permits cleartext because Android can't express "private IP ranges" and the existing Ollama provider uses `http://192.168.x.x`. Enforcement is in-app (NetGuard on every OkHttp client). Any future HTTP stack (WebView, MediaPlayer URLs) would bypass it.
- **Keystore limits:** encryption does not protect keys on a rooted/compromised device or from code running inside the app process. Keys are never sent anywhere except the provider endpoint the user configured.
- **Fallback providers:** when "auto fallback" is on, a failed request is re-sent (with the conversation) to the user's other configured providers. That is user-configured consent, but it is a different company receiving the data.
- **Memory tags** (`[[remember:]]`) are still model-controlled; injection could plant a memory. Mitigated only by the safety prompt; memories are visible/deletable in Settings.
- **Data tools** (contacts, calendar, location) send results to the AI provider without a per-call prompt once the user enabled that app + granted the OS permission.
- **Gradle dependency verification metadata** (`verification-metadata.xml`) not generated; vulnerability scan relies on GitHub dependency-review in CI. No known advisories found for OkHttp 4.12.0 / Okio 3.6.0 in the versions used; dependencies were intentionally not bulk-upgraded.
- **Conversation storage** remains a single JSON file (atomic write, serialized saves). No Room DB; fine for current size, not designed for thousands of long chats.
- **Not tested here:** real devices, real provider keys, Compose UI tests (no emulator), permission revocation flows on device, Android Keystore on real hardware (tests use the same AES-GCM code with an in-memory key).

## Build & sign a release
1. Create a keystore once: `keytool -genkeypair -v -keystore omnichat-release.jks -alias omnichat -keyalg RSA -keysize 4096 -validity 10000`
2. Create a `keystore.properties` **outside git** with `storeFile=…`, `storePassword=…`, `keyAlias=…`, `keyPassword=…`
3. `export OMNICHAT_KEYSTORE_PROPERTIES=/path/to/keystore.properties`
4. `./gradlew testDebugUnitTest lintDebug assembleRelease` → `app/build/outputs/apk/release/`
Without step 3 (or a root `keystore.properties`), release tasks fail on purpose. Debug builds need nothing (`./gradlew assembleDebug`, installs as `com.ravi.omnichat.debug`).
CI: `.github/workflows/ci.yml` (PRs: build, tests, lint, signing-guard check, dependency review); `release.yml` (manual, protected `release` environment, keystore from secrets).

## Local checks
`./gradlew testDebugUnitTest` · `./gradlew lintDebug` · `./gradlew verifyReleaseSigning`
