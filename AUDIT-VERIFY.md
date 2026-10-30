# VERIFY-FIRST audit — report only, nothing here was fixed

Audited 2026-10-30 against the v1.9 source as received (plus the P0/P1 fixes; line numbers are
from the tree at audit time and may drift by a few lines). Each item: CONFIRMED bug, MINOR
observation, or CLEAN, with evidence.

## ui/ChatScreen.kt + ui/SettingsScreen.kt

- CONFIRMED (main-thread work, small): `ui/ChatScreen.kt:428` — `AttachmentPreview` decodes a
  base64 image with `BitmapFactory.decodeByteArray` inside `remember(a)` during composition,
  i.e. on the main thread. Bounded (attachment thumbnails) but a decode jank source in lists.
- CONFIRMED (main-thread work, small): `ui/SettingsScreen.kt:748` `remember(tick, clock) {
  vm.usage.todayCounts() }` and `:886` `remember(scope, vm.usageTick) { vm.usage.history(scope, 7) }`
  read SharedPreferences + parse JSON on the main thread on every tick/clock recomposition.
  Data is tiny (≤30 days of counters), so impact is low, not zero.
- MINOR: `MainActivity.kt:95` reads the crash-report file with `file.readText()` inside
  `remember` during composition (main thread, only when a crash report exists).
- MINOR: `ui/ChatScreen.kt` `ThinkingBlock` — `LaunchedEffect(active)` starts a 1 s counter
  but `elapsed` is never reset when `active` flips back to true, so a reused instance can
  continue counting from the previous session's value.
- CLEAN: all other `LaunchedEffect`s are keyed (toast, scroll-follow, model refresh) or are the
  intended periodic tickers (SettingsScreen.kt:747 30 s clock). `remember` usage is keyed where
  the underlying value changes (message id, provider id, `vm.input` token). No raw Flow
  collection in the UI at all — state is Compose snapshot state held in the ViewModel — so
  there is no non-lifecycle-aware collect.

## security/ToolPolicy.kt — limits and unknown keys

- MAX_CALLS_PER_REPLY: ENFORCED, twice. `ToolTagParser.MAX_TAGS = 12` caps parsing
  (ToolPolicy.kt:14,20); ChatViewModel.kt:906 takes only `MAX_CALLS_PER_REPLY = 6` verdicts
  and notes the skip in appNotes (:908).
- MAX_ACTIONS_PER_REPLY (=4): ENFORCED for OPEN and CONFIRM verdicts
  (ChatViewModel.kt:922,924, both gated on `doneActions.size < MAX_ACTIONS_PER_REPLY`).
  DATA calls are not counted as actions (by design).
- CONFIRMED behaviour (differs from the audit's suspicion): unknown argument keys are NOT
  rejected. `validate()` builds the output JSONObject only from the spec's fields, so extra
  keys are silently dropped — asserted by the existing test `unknownArgumentKeysDropped`.
  This is a deliberate-looking design (a model adding junk keys doesn't kill the call), but
  it means a typo'd key name fails silently instead of surfacing an error. Not fixed — needs
  Ravi's call on reject-vs-drop.

## data/ChatStore.kt (inside data/Prefs.kt, ~line 200)

- Atomic write: CLEAN. `save()` (Prefs.kt:246) writes `chats.json.tmp` then `renameTo(file)`
  (:268, with delete+rename fallback). A kill mid-save cannot leave a half-written main file.
- Corrupt-file recovery: PARTIAL. `load()` (Prefs.kt:203) is `runCatching` and each
  conversation is parsed under its own `runCatching`/`mapNotNull`, so one bad conversation
  drops only itself. BUT whole-file corruption returns `emptyList()` with no quarantine/backup
  of the corrupt file, and the next `save()` overwrites it — the original data is then
  unrecoverable. No `.bak`/`.corrupt` copy is kept.
- CONFIRMED unbounded growth: no cap anywhere on conversation count, total file size, or
  per-conversation size in `save()`/`load()`; `load()` also does `file.readText()` (Prefs.kt:205),
  loading the whole file into memory at once. Chats only ever accumulate.

## data/ProviderErrors.kt — retry classification

- CLEAN. `ErrorKind` (ProviderErrors.kt:14-27): AUTH retrySameEndpoint=false (:16),
  QUOTA=false (:17), TLS=false (:21), DNS=false (:20). Only TIMEOUT (:19) and NETWORK have
  retrySameEndpoint=true. `kindForStatus` maps 401/403→AUTH, 402 and quota-429→QUOTA,
  429→RATE_LIMIT (also retry=false). Auth/quota/TLS failures therefore never retry the same
  endpoint; they may still try the next fallback key (tryFallback=true), which is the intended
  chain behaviour in ChatViewModel.

## data/Prefs.kt + security/SecretStore.kt — migration half-writes

- CLEAN (main risk): `migrateSecrets()` writes each secret via `SecretStore.put`, reads it
  back and compares (`secrets.get(n) == v`) before removing the plaintext copy; on any failure
  the name lands in `migrationProblems`, the plaintext stays, and `secrets_v1_done` is NOT set,
  so the migration retries on the next launch. Fallback keys inside the fallbacks JSON follow
  the same write-verify-then-blank pattern. `SecretStore.put` itself verifies an
  encrypt→decrypt round-trip before committing.
- MINOR: the plaintext-removal `sp.edit().remove(n).commit()` results in `migrateSecrets`
  are not checked. If such a commit failed while `migrationProblems` stayed empty,
  `secrets_v1_done` would still be set and a duplicate plaintext copy would remain on disk
  (availability safe — the secret keeps working — but the plaintext copy the migration meant
  to erase would persist).
- BY DESIGN (noted, not a bug): when encrypted storage is unavailable/fails, secrets fall
  back to plaintext storage (`p1:` in SecretStore, plain prefs in `Prefs.setSecret`) so a key
  is never lost; the UI surfaces this via `unreadableSecrets()`/the plaintext warning.

## Summary for Ravi (decisions needed, nothing fixed here)

1. ToolPolicy: reject unknown argument keys, or keep dropping them silently?
2. ChatStore: add a corrupt-file quarantine (`chats.json.corrupt`) + a size/count cap?
3. Small main-thread reads (image decode, usage counts, crash file): move to IO, or accept?
