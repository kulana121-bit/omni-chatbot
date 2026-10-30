# OmniChat v1.9 — fix report

**Honest verification note:** `./gradlew testDebugUnitTest lintDebug` was **NOT run locally for any
commit** — local Gradle cannot run on this VM (the Gradle daemon's loopback connection is blocked
even with other_tcp=Ask; verified again today with the daemon-test project hanging at the
single-use-daemon fork). Per Ravi's fallback order this is a **source-only delivery**. Every unit
test below is **written, not run locally**. The first CI run (or Ravi's Android Studio build) is
the first real test/lint execution. Static verification actually performed is listed at the end.

Baseline commit: `5fb352c` "chore: OmniChat v1.9 source as received".

## P0 — security

| Finding | Status | Commit | Tests added/changed (written, not run) |
|---|---|---|---|
| P0-1 stream OOM + finishReason | **Fixed** | `79f55d1` | `ReplyTextTest` (300 KB stream: streaming runs 0 full strips, final == old visible(), full-strip count == 1; continuation-policy branches), `ApiClientTest` +3 (length / max_tokens / Gemini MAX_TOKENS → Done.truncated) |
| P0-2 cleartext | **Fixed** | `55bded2` | — (XML/config change; XML well-formedness checked) |
| P0-3 sensitive tools confirm | **Fixed** | `3f3a79a` | `ToolPolicyTest` +2 (both branches: no-untrusted → DATA, untrusted → CONFIRM for all 4 sensitive tools; weather unaffected) |
| P0-4 @mention auto-enable | **Fixed** | `367252d` | `ChatViewModelPromptTest` (3 tests) |

P0-1 details: new `data/ReplyText.kt` — `streamingText()` only tail-scans the last 256 chars for an
unterminated `[[`; `finalText()` is the single full-string pass (tag regex + ToolTagParser.strip +
blank-line collapse) and runs on Done/final message only. `publish()` uses the streaming view.
Provider finish reasons were already mapped for OpenAI `length`, Gemini `MAX_TOKENS`, Anthropic
`max_tokens` in `parseLine`; added OpenAI `max_tokens`, the non-streaming OpenAI path, and Gemini
`length`. Auto-continue ON → existing continuation loop (now via `ContinuationPolicy`); OFF →
`canContinue` state + a **Continue** AssistChip in ChatScreen, backed by
`ChatViewModel.continueReply()` which re-runs generation with the cut reply as base. Streaming now
uses a dedicated `streamHttp` client (read timeout 600 s); the shared client dropped to 60 s.

P0-2 details: base-config `cleartextTrafficPermitted="false"` (system CAs); domain-config allows
cleartext only for `localhost`, `127.0.0.1`, `10.0.2.2`, `::1`. New product flavour **`lan`**
(dimension `net`, alongside `standard`) with `src/lan/AndroidManifest.xml` +
`network_security_config_lan.xml`; the lan **release variant is disabled** in
`app/build.gradle.kts` (debug-only). `InsecureBaseConfiguration` baseline entry deleted in this
commit. EndpointPolicy/NetGuard and the `allowLocalHttp` preference are untouched and still gate
all HTTP. Side effect: CI task names became variant-scoped (`assembleStandardDebug`,
`testStandardDebugUnitTest`, `lintStandardDebug`) — updated in P2-1's commit.

P0-3 details: `ToolPolicy.SENSITIVE = {contacts_search, location, device_info, apps_list}`;
`validate(..., untrustedSeen)` escalates those to `Risk.CONFIRM` (existing PendingAction UI) when
untrusted content (web context, file attachment text, or an earlier app result) appeared in the
same reply; otherwise unchanged DATA behaviour. Confirmation descriptions added for all four;
the data round adds the appNote chip `read contacts (N)` with the result count.
Choice made for P0-4: implemented the **prompt-line variant** ("@contacts is disabled; enable it
in Apps & Skills", generalised per connector via `disabledMentionLines`) instead of asking the
user mid-reply, because it is self-contained; `setConnector(id, true)` is gone from
`effectiveSystemPrompt()` and the "user tagged" line now only names enabled connectors.

## P1 — bugs

| Finding | Status | Commit | Tests (written, not run) |
|---|---|---|---|
| P1-1 load on main thread | **Fixed** | `4b7eea2` | `ChatViewModelLoadingTest` (Robolectric: flag starts true, clears after IO load) |
| P1-2 data-usage API 28 | **Fixed** | `d2db757` | `ConnectorsTest` (API 26/27 → ACTION_SETTINGS, 28+ → data usage; other pages unchanged) |
| P1-3 hardcoded 91 / blank success | **Fixed** | `4abacc2` | `PhoneNumbersTest` (dialling codes, local + international, blank → null) |
| P1-4 Gemini maxTokens | **Fixed** | `76548f5` | `ApiClientTest` +2 MockWebServer (body has maxOutputTokens + thinkingBudget; and maxOutputTokens alone under AUTO) |
| P1-5 usage double count | **Fixed** | `4a24827` | `UsageDeltaTest` (partial flush → resume delta only; never negative; request counted once) |
| P1-6 pagination unbounded | **Fixed** | `3f72a7b` | `ApiClientTest` +2 MockWebServer (repeating token stops in ≤3 requests; 20-page cap = exactly 20 requests) |
| P1-7 readVideo doc vs code | **Fixed** | `b24a3c8` | — (doc aligned to code: 6 keyframes @ JPEG 75; code was already 6/75) |

P1-1 also: load merges by conversation id so a chat created during loading isn't clobbered, and
the sidebar shows "Loading chats…" instead of flashing an empty history.
P1-2 also: `runAction` already converted a throwing `startActivity` to null via `runCatching`;
that is now commented at the settings branch so the behaviour is explicit (no fake "Settings").
P1-3 also: `call`/`sms` return null on a blank resolved number (approveAction then shows
"Couldn't do that"); WhatsApp returns null on blank/unusable numbers.

## VERIFY FIRST — audit only, nothing fixed

Full evidence in `AUDIT-VERIFY.md`, commit `b024945`. Headlines:
- ChatScreen/SettingsScreen: confirmed small main-thread work (attachment bitmap decode in
  composition, usage JSON reads in `remember`), one elapsed-counter reset nit; otherwise clean
  (no non-lifecycle collect exists — UI holds no Flows).
- ToolPolicy: MAX_CALLS_PER_REPLY and MAX_ACTIONS_PER_REPLY **are** enforced; unknown argument
  keys are **dropped, not rejected** (existing test asserts the drop) — Ravi's decision needed.
- ChatStore: atomic write clean (tmp+rename); corrupt-file recovery partial (no quarantine,
  next save overwrites); growth **unbounded** (no caps, whole-file `readText`).
- ProviderErrors: **clean** — auth/quota/TLS/DNS all have retrySameEndpoint=false.
- Prefs/SecretStore migration: **clean** on the half-write risk (write → read-back verify → only
  then remove plaintext; done-flag withheld on any problem); one minor unchecked commit() result
  on plaintext removal.

## P2 — build & tests

| Finding | Status | Commit |
|---|---|---|
| P2-1 CI release build | **Fixed** | `dec9a43` |
| P2-2 lint baseline | **Fixed — baseline deleted** | `58b3a6c` |
| P2-3 LaunchTest / regression suite | **Fixed** | `b831314` |

P2-1: CI now generates an ephemeral keystore (`keytool`, alias `omnichat-ci`, never
`androiddebugkey`, validity 1 day, in `$RUNNER_TEMP`), runs `assembleRelease`, verifies with
`apksigner verify --print-certs` (and fails if the cert says "Android Debug"), and removes the
signing material in an `always()` step. `release.yml` and its secrets were not touched. The
pre-existing CI guard step (verifyReleaseSigning must fail without a keystore) is unchanged and
runs before the keystore is created.

P2-2: **lint-baseline.xml is deleted** (220 lines → 0) and the `baseline =` setting removed.
Every entry was resolved at the source: InsecureBaseConfiguration (P0-2), InlinedApi (P1-2; the
action is now the literal string so lint needn't prove a parameter guard), DefaultLocale
(Locale.US ×3 in Usage.kt), AutoboxingStateCreation (mutableIntStateOf ×6, including the
AppsSkillsScreen tab line the ObsoleteSdkInt entry pointed at), ApplySharedPref (migration
commits → apply() with the idempotency argument written in code and in the commit body;
UsageStore.wipe → apply(); the three load-bearing commits — Prefs.wipeAll, SecretStore.put /
remove / clear — kept and suppressed at the site with the reason in a comment).
`warningsAsErrors = true` is now set.
**warningsAsErrors blocker list (cannot be confirmed without running lint — first CI run decides):**
1. Other default-locale `.format(...)` calls exist outside Usage.kt (e.g. ToolPolicy.describe,
   Connectors currency/crypto, ChatViewModel alarm text). The baseline only ever listed Usage.kt,
   but if lint flags the others they will now fail the build; each is a one-line Locale.US fix.
2. The ObsoleteSdkInt baseline entry pointed at a `rememberSaveable` line, not an SDK check; the
   state was converted to mutableIntStateOf, but if the entry was really about something else in
   AppsSkillsScreen it may resurface.
3. Any lint check whose baseline entry was masking a second occurrence at a different line
   (baseline entries are line-scoped).
4. `mutableIntStateOf` in `rememberSaveable` (MainActivity, AppsSkillsScreen) relies on the
   built-in saver for MutableIntState in the Compose version in use (2024.09.03 BOM — supported).

P2-3: LaunchTest.kt was not literally empty (it had the Robolectric launch test) — the requested
regression cases were added across the suite, mostly inside their own findings' commits:
ToolTagParser nested braces / unicode / 100 KB junk / unterminated `[[` (`ToolTagParserTest`,
this commit), long-stream regression (P0-1), number resolution (P1-3), Gemini body (P1-4),
usage dedupe (P1-5), pagination cap (P1-6).

## Static verification actually run (in place of Gradle)

- Brace/paren/bracket balance (string/comment-stripped) for **every** Kotlin file, compared
  against the baseline commit with the same checker: **zero delta on every edited file**; every
  new file balances exactly (0,0,0). (Absolute counts differ from zero on some untouched files
  in both versions — regex-literal artifacts of the stripper, identical before/after.)
- XML well-formedness: all 13 XML files parsed (incl. both network-security configs, the lan
  manifest, and the baseline while it existed). CI/release YAML parsed.
- Grep proofs: no `setConnector(..., true)` auto-enable remains; no `"91$…"` hardcode remains;
  main `network_security_config.xml` base-config is `cleartextTrafficPermitted="false"`;
  `verifyReleaseSigning` guard intact (2 references) and still fails without
  keystore.properties; ToolPolicy allowlist and `<untrusted_content>` wrapping untouched;
  no new lint-baseline entries (file deleted).
- Every edited file was re-read after editing; the full `git diff` vs baseline was reviewed.

## Pre-existing item flagged, not changed

`data/WebSearch.kt:29` already had `.followRedirects(true)` in the received source (its PUBLIC
NetGuard network interceptor blocks HTTP downgrade hops, and no API key is sent on those
requests). It was left as-is: removing it is a behaviour change to web search that none of the
findings asked for. Ravi may want a separate decision on it.

## Residual risks for the first real build

- Kotlin smart-cast / AGP DSL details that only a compiler can fully prove — chiefly the
  `androidComponents.beforeVariants(selector().withFlavor("net" to "lan"))` block (P0-2) and
  the flavour-scoped Gradle task names in CI (P2-1).
- `ChatViewModelPromptTest` loads the `ChatViewModel` companion in a plain JUnit test (no
  Robolectric); if class-loading the AndroidViewModel subclass misbehaves on the JVM, move it
  under Robolectric like `ChatViewModelLoadingTest`.
