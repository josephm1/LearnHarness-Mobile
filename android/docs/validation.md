# Android 0.6.3 validation — 25 October 2026

Independent Android project on local branch `android-independent`, based on upstream LearnHarness `6cba9c8`. The main regression suite runs on an Android emulator (API 36, Pixel 6). Version 0.6.3 is installed on the connected test device after emulator checks. At the user’s explicit request, two targeted live plan-search probes succeeded there; the live verification record describes their scope. Hands-on phone testing belongs to the user; no broader physical UI tests were run.

## Reproduce

```sh
./android/tools/gradle.sh :app:assembleDebug :app:testDebugUnitTest :app:lintDebug :app:assembleDebugAndroidTest --console=plain
adb -e install -r android/app/build/outputs/apk/debug/app-debug.apk
adb -e install -r android/app/build/outputs/apk/androidTest/debug/app-debug-androidTest.apk
adb -e shell am instrument -w ai.learnharness.android.test/androidx.test.runner.AndroidJUnitRunner
npm run --prefix android/tools/original-core test
```

Final result: **BUILD SUCCESSFUL; 116 JVM tests and 63 ordinary Android instrumentation tests pass, zero failures (two additional live probes are skipped by default); lint has zero errors and 29 warnings (dependency/target updates, UI-string/platform lint and existing PDFBox transitive helper diagnostics).** Generated-core Node checks also pass. Dependencies and runtime requirements are documented in the Android README and original-core README. The ordinary regression suite uses dummy credentials and mocked provider streams. Live probes are opt-in and skipped in that suite. The separately authorized phone checks used the saved ChatGPT plan connection for search and research, without another sign-in or API-key billing.

## Changes verified

- White native Material layout, 16 sp default reading text, categorized Settings and four main navigation destinations. An open lesson hides bottom navigation, scrolls under a collapsing AppBarLayout and keeps the outlined Agora-style composer pinned. Drafts survive navigation. Attach, dictation, send/stop and optional confidence are grouped inside its boundary.
- Untouched confidence stays unused at 50 and is omitted. Deliberately selected confidence is sent once and resets. Structured question UI tests select a native answer option, exercise the original local grading code, persist the tool result and verify no default confidence was included.
- Literal and RE2 document search: text streaming, native PDF page extraction, EPUB spine order, source locations, range validation, bounded snippets and transient-file cleanup. A manual Android Files picker test linked a text file from Downloads, found a known passage and confirmed the records contained only source metadata, not the document text. Scanned PDF OCR and the older Android PDF fallback need wider device coverage.
- Source approval is explicit and bounded. Requests contain approved text only in user/tool input. Tests verify original history is not mutated and source material is removed before tool history is saved for future replay.
- Actual Agora host/renderer assets load in an opaque Websandbox frame. Tests verify the private MessagePort bootstrap, no direct Android file/content access, no same-origin sandbox grant, and atomic template import conflict rejection. A manual slider interaction changed the example graphic from 50% to 82%.
- The unchanged upstream question/plan core runs in AndroidX JavaScriptEngine, not a hidden WebView. Node and Android tests exercise schema validation, invalid-option rejection, deterministic grading, omitted confidence, original Mermaid parsing and cycle rejection.
- The original question declarations/descriptions and system prompt composition are packaged from source. Byte-for-byte prompt composition checks cover teacher, prober and practice assets. Lesson calls use those original role assets with no Android/book system-prompt additions.
- Agent tests verify one-question pause, validation errors returned to the model for repair, the original probe acknowledgment/handoff, automatic teacher planning, plan-node validation, free recall commitments and accumulated usage.
- Provider tests verify streamed tool-call argument assembly, text-only visible previews, completed-only persistence, Responses encrypted-reasoning replay, Anthropic thinking/signature and tool-result replay, Gemini function signatures, native JSON Schema declarations, OpenRouter reasoning-fragment assembly and metadata isolation. Live provider/model tool support remains account-specific and untested in this run.
- Existing OAuth/PKCE/JWT, loopback, account-isolation/refresh/revocation, HTTPS/error redaction, provider settings, backup validation, streaming, Markdown and pagination regression tests pass.

## Direct chat and question submission — 0.6.3

New learning session now opens directly into a chat with the programmatic greeting “What do you want to learn?” It makes no AI request and adds no assistant/system text to the model history. Sending the first topic saves the exact user message, derives a display title and invokes the tutor; unavailable AI setup is a separate actionable state. There is no title modal or ready/start section. Drafts survive recreation, unsubmitted empty chats are hidden from history, and failed message persistence rolls back without clearing the draft.

A screenshot of the user's water-cycle lesson exposed the previous UI bug: the model had already asked a scope question through a tool call, leaving only one ordinary transcript message. The old message-count condition incorrectly showed Start lesson, whose tutor action correctly refused to bypass the unanswered question. Every revealed lesson question now has Submit answer beneath its options. It commits the selected option and optional typed reasoning through the existing original question contract; composer Send follows that same path.

Emulator checks cover direct entry without a dialog, empty-message validation, first-topic persistence with no programmatic greeting in model input, title derivation, unfinished-draft recreation, Practice form validation and the exact one-transcript-message/pending-scope-question case. The latter selects groundwater/rivers/human use, presses Submit answer and verifies a committed original tool result with the selection and no remaining pending question. All model fixtures use an unconfigured connection to avoid live requests. The original agent prompts are unchanged.

## Additional 0.6 checks

- Original adaptive practice problem/conclude loop, committed private solutions, thinking traces, hints, explicit next-problem pause and proposition-target validation.
- Original extraction/auditor prompts and tools, source-only input, valid passage anchors, assertion/chain merge, original graph read tools, source-fingerprint coverage, per-stage role routing and durable batch checkpoints. A retry skips already covered source passages.
- Original graph deletion semantics, ledger edits and native map/chain reader. Graph ledgers round-trip through learning backups.
- Original strong/cheap/vision slots and per-agent provider/model/thinking resolution; explicit billing and HTTPS route validation.
- Agent read/grep pause before opening files, with metadata-only ls/find. Source approval is still explicit, one-turn and bounded; practice retains the current problem across approval.
- Original researcher instructions and readable article extraction execute on Android with a mocked search/fetch transport, no page-script execution, researcher-role routing and citation results. Native plan search and the full researcher loop were additionally verified live; regression tests cover native tool declarations, visible source links and sparse stream item retention. A second active original-core isolate shares the Android sandbox safely.
- Job records persist independently, remain staged until parent tool history is committed, deduplicate dispatch, expose interruption, and create a fresh job identity for explicit retries. Public web-address guards reject local/private networks. Live Tavily/Brave requests and OS service-timeout behavior still need acceptance checks.
- Manual Android Files folder selection, persisted read grant, on-demand directory browsing and linking a selected text file succeeded. The saved records held folder/file references without the fixture file's body. Folder-reference backup validation is covered by JVM checks.
- Native inline/display formula drawables finish rendering on the emulator without altering stored Markdown. Code fences/spans and escaped dollar signs remain intact. The LaTeX adapter and original prompt preservation have regression checks.

## Historical physical sign-in verification

On the test device, callback processing previously stalled while Chrome remained foregrounded and resumed immediately when LearnHarness became foregrounded. A temporary foreground data-sync service fixed that suspension: real reauthorization completed with Chrome still visible, signed identity/token exchange succeeded, and the service stopped afterward. Existing account registration and learning data survived the update. No tokens, passwords or callback codes were logged. Settings → AI connection → Connection details contains redacted stage/HTTP diagnostics.

## Remaining acceptance work

Full parity has **not** been reached. [The parity ledger](feature-parity.md) remains the scope record. Remaining work includes research PDF tools and the vision PDF specialist, automatic demo/image generation and verification, linked template-folder/skill execution, agent traversal of library trees and image input, full context compaction and cost accounting, Mermaid rendering, additional provider/auth variants, desktop data migration, full job/settings backups and Android update delivery. Research result delivery is durable, but automatic tutor wake currently runs only for the selected open lesson. Native formula presentation does not reproduce every KaTeX extension. No remaining feature has been declared impossible or dropped.

Recheck live inference/tool support with the user's chosen models, device-specific document providers and large books, Android 8–14 PDF fallback, speech recognition, physical e-ink refresh and the latest UI on the phone when reconnected. The APK is a development-signed build, not a Play Store release.
