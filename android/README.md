# LearnHarness for Android

An independent native Android adaptation of [LearnHarness](https://github.com/amosblomqvist/learnharness) by Amos Blomqvist. The upstream learning-agent and core source is retained under `src/`; this Gradle project is separate and does not need Node.js, Electron, a computer/server, or a hosted website to run.

## Install and start

Download the signed APK from [GitHub Releases](https://github.com/josephm1/LearnHarness-Mobile/releases). Debug builds are also available locally at `app/build/outputs/apk/debug/app-debug.apk`.

1. Install the APK on Android 8.0 (API 26) or later. Version **0.6.3** includes ChatGPT sign-in, Material You navigation and the Android browser-handoff fix.
2. Open **Settings → AI connection → Continue with ChatGPT**. Sign in through the system browser and approve plan usage. Use **Return to LearnHarness** in the browser when done.
3. In Settings, choose an available **Model**, then enable **Use ChatGPT plan**. If model loading failed, use **Manage account → Refresh models**. No API key is needed on this route.
4. In **Learn**, create a goal. The tutor probes understanding, proposes a dependency plan in the lesson, and teaches through questions and reasoning.
5. Use **Extract knowledge from this lesson** to save claims and their premises to **Library**. Review/correct extracted claims there.
6. Create practice from a topic or the library. Questions, hints, model answers, self-review and written answers are available offline after generation.

Alternatively, configure an API provider in Settings and explicitly choose **Use saved API provider instead**. Choose its HTTPS API base URL, model ID and API key, then press **Save provider settings**. API-key billing is separate from your ChatGPT subscription. Requests never fall back automatically from ChatGPT plan usage to API billing.

## Providers, models and thinking

Under **Settings → AI connection**, **Active connection** chooses ChatGPT plan or the saved API provider. Other subscriptions cannot be used as API credits; this app currently supports ChatGPT account authorization and API keys for other providers.

For APIs, open **API provider settings**, choose a provider from the Material dropdown, and enter its key. **Browse provider models** reads that provider’s catalog without making a generation request; you can also enter an ID manually. Save the provider/model before configuring **Thinking mode**. Endpoints and models are remembered separately for each provider; keys remain encrypted.

**Thinking mode** is saved separately for each connection and model, including each ChatGPT registration. **Model default** omits overrides. OpenAI/ChatGPT and OpenRouter expose effort choices; DeepSeek exposes Off and effort; Anthropic exposes adaptive effort and explicit extended-thinking budgets; Gemini 2.5 exposes budgets and newer Gemini models expose levels. Availability still varies within each provider. An unsupported override returns an error; choose Model default rather than silently changing model or billing. Higher settings can take longer and consume more allowance. Changing these controls does not start an inference request.

Protocol references: [OpenAI reasoning](https://developers.openai.com/api/docs/guides/reasoning), [Anthropic effort](https://platform.claude.com/docs/en/build-with-claude/effort), [Gemini thinking](https://ai.google.dev/gemini-api/docs/generate-content/thinking), [DeepSeek thinking](https://api-docs.deepseek.com/guides/thinking_mode/), [OpenRouter reasoning](https://openrouter.ai/docs/guides/best-practices/reasoning-tokens).

## ChatGPT account sign-in

This implements OpenAI’s documented [Sign in with ChatGPT plan usage for open-source/local apps](https://developers.openai.com/siwc/token-sharing-open-source). It uses the system browser, a local `127.0.0.1` callback, fresh OAuth state, PKCE and an OIDC nonce. Account identity is validated with OpenAI’s signing keys before activating it. The app never asks for or stores your ChatGPT password.

The account’s OAuth access/refresh/ID tokens, models and registration are encrypted with the Android Keystore in private storage and excluded from learning exports and device backups. A stable opaque host ID is saved once per installation. A temporary foreground sign-in service keeps the callback listener responsive while the system browser is in front; it stops when sign-in completes, fails or is cancelled. The callback listener times out after five minutes; **Cancel sign-in** closes it. If Android kills the process during browser sign-in, start sign-in again. A failed code exchange retains its issued client ID under **Retry ChatGPT sign-in**, without activating an unverified account.

Available models come from the signed-in account, with no hardcoded model ID. Saved account registrations stay separate, including registrations with identical email addresses. The account picker shows a stable registration suffix. Access tokens refresh serially near expiry, with both replacement tokens saved together. Terminal refresh failures clear unusable credentials while retaining the registration; temporary failures retain credentials. Sign-out attempts remote session revocation, then clears local tokens. If revocation cannot be confirmed, disconnect the app in ChatGPT settings as instructed.

Eligible tutoring, practice and knowledge extraction requests use `https://api.openai.com/v1/responses` with `store:false`, `stream:true`, full required input history and developer instructions. Normal mode shows live Markdown previews for tutoring and practice feedback, coalesced at 100 ms to avoid rebuilding the screen. E-ink mode buffers the stream and updates only after completion. Partial/interrupted/incomplete/failed replies are not saved as completed learning records.

Eligibility, available models and limits depend on the account, workspace and OpenAI’s preview availability. ChatGPT Plus usage is shared across apps; this app does not receive its own allowance. Use **Usage and limits** to open [ChatGPT Settings → Usage](https://chatgpt.com/settings/usage). Plan/app limits stop requests and never switch billing automatically. This login does not import your ChatGPT conversation history.

“Independent” means the app runs its own storage and orchestration on Android. AI generation still requires internet access and either an eligible ChatGPT sign-in or an API provider account/key; this release does not bundle an offline language model. Keep the app foregrounded for a request. Android may terminate a background process; submitted learner turns remain saved, and **Ask tutor to continue / retry** can resume. Requests time out after three minutes without a response. Cancellation prevents local result delivery; it does not guarantee the provider stops billing.

## Material You interface

Normal mode uses Material 3 controls, dynamic accent colours where supported, and plain white backgrounds throughout. Reading text defaults to 16 sp; the toolbar and navigation labels are compact. A native navigation bar provides icon-and-label destinations on the main screens. Open lessons hide this bar and use a single-line toolbar so reading gets most of the screen; Back returns to the main destinations. The lesson toolbar collapses during scrolling. An outlined white composer groups attachments, dictation, icon-only Send/Stop and an optional confidence slider. It stays below the reading area while the keyboard is open. The midpoint is only a placeholder: untouched confidence is omitted from the request. Moving the slider includes its percentage in that answer, then resets to optional after sending. Stage, connection/usage, extraction, export, rename and deletion live in the toolbar overflow. Settings has separate AI connection, Appearance, Data & backup and About pages. Optional API credentials are on their own page.

New learning session opens directly into a chat with the local greeting “What do you want to learn?” There is no lesson-title modal or Ready/Start section. Send submits the first topic, derives the lesson title and continues into the tutor. The greeting never enters model history. Drafts survive Activity recreation; unsubmitted empty chats are hidden from the lesson list. Questions have a clearly labelled Submit answer button beneath their options, and the composer Send action submits the same selection. AI setup appears only when a saved message cannot be sent without configuring a connection. Practice retains its separate validated topic/library form.


## E-ink mode

Enable **Settings → Appearance → E-ink compatible mode**. It is saved on the device and switches the application theme, including dialogs, to black text and outlines on white paper. It removes decorative colour, grey surface fills, rounded cards, elevation, ripples and window transitions. Lesson transcripts show one text page with explicit Previous/Next controls. Replies appear in complete blocks; there is no token animation, animated graph, activity spinner or auto-scroll.

Markdown renders headings, emphasis, lists, links, code and tables using native Android spans. Reading size is adjustable from 16–28 sp. Page length has 650/1100/1800-character upper limits, with additional page breaks based on screen size and reading text size; pages break at paragraphs or words, keeping Unicode characters intact. A page can still scroll when large text or a small display requires it. Library and practice use static outlined cards with explicit actions. Text is selectable.

This follows the visual rules inspected in `Kalamos-Notes/features/writing/workspace/src/vanilla/eink.css`. Vendor-specific Bigme/Boox waveform or full-panel refresh APIs are deliberately not assumed. Physical ghosting and refresh behaviour still need testing on the target e-ink device.

## Feature coverage

| Area | Android implementation |
| --- | --- |
| Adaptive classroom | Original role prompts and question schemas; streamed tool calls; probe/quiz/recall/explain/ask cards, native grading, optional confidence, automatic probe handoff, original plan validation and nodeId checks; persistent history and retry |
| Knowledge | Original extract/audit/merge pipeline, fingerprint coverage, assertion ledgers, joint-premise chains, graph map and source anchors; manual notes/editing and review |
| Practice | Original adaptive problem/conclude loop, proposition targets, thinking traces, hints, committed solutions, explicit next problem; legacy sets retained |
| Providers | ChatGPT account/plan sign-in through Responses, plus OpenAI-compatible Chat Completions, Anthropic Messages, Gemini generateContent; presets for OpenRouter and DeepSeek; editable endpoints/models |
| Privacy/storage | Private atomic JSON records; Android Keystore AES-GCM API-key and OAuth-credential encryption; no bundled keys; cloud/device backups excluded |
| Documents | Link original PDF/EPUB/text files or browse selected library folders through Android Files; bounded on-demand reading and literal or RE2 grep; preview and approve excerpts before one AI turn; no full-book text import/index |
| Interactive templates | Agora OpenIntelligentUI renderer and template JSON format; import/export, local shortcuts, sandbox preview, native follow-up approval and e-ink fallbacks |
| Import/export | UTF-8 text/Markdown notes; session text export; Android learning backup including source references; separate interactive template catalog export |
| Dictation | System speech-recognition app, with offline recognition requested when supported; typing remains available |
| Usage | Provider-reported input/output token totals for completed requests; no guessed prices |
| E-ink | Saved monochrome theme, outlined page layouts, static status and block responses, configurable reading/page size |

Full parity remains the target. [The parity ledger](docs/feature-parity.md) records every remaining feature. The original adaptive practice loop, three-stage extraction pipeline, assertion/chain graph and coverage rules now run on Android. Original agent file tools request explicit passage approval, and local session search and plan-progress derivation reuse upstream code. Graph ledgers are included in Android learning backups; desktop backups are not yet interchangeable.

**Models by role** supports the original strong/cheap/vision slots and individual agent overrides, each with explicit plan/API billing and thinking controls. **Web research** supports built-in **ChatGPT plan search**, with no separate search API key; this is the default for a plan connection when no search preference has been saved. Choose it explicitly in Settings → Web research if search was previously disabled. The Researcher role must use a ChatGPT plan connection. Optional Tavily/Brave providers remain available under Other search providers. Built-in search availability depends on the selected model and account/workspace policy; an error never changes billing or search provider. A live gpt-6.1-sol request and the original researcher loop both succeeded through the saved plan connection; see the [validation report](docs/validation.md). Research runs with the original researcher instructions in an isolated context; only the completed brief reaches the tutor. **Background jobs** shows status, usage, cancellation and explicit retry. Up to three research jobs run concurrently in a foreground service. Android interruption is recorded rather than silently retried. Completed results are durably delivered; automatic tutor continuation currently runs for the selected open lesson. Research PDF tools, other job kinds, job backups and complete background tutor orchestration remain in scope.

The research transport permits public HTTP pages to preserve upstream web-fetch behavior, follows bounded redirects and rejects local/private destinations through its DNS resolver. Model endpoints, authentication and search-provider credentials still require HTTPS. ChatGPT built-in search uses the authenticated OpenAI Responses route; URL citations become clickable Markdown links while original provider output is retained for replay. Page scripts are never executed. Web retrieval is capped at 5 MB and model-visible article text at 40,000 characters. Local library files retain the separate explicit one-turn passage-approval gate. Library folder browsing uses Android’s [document-tree APIs](https://developer.android.com/reference/android/provider/DocumentsContract); it reads one directory’s metadata on demand and links only the files you select. There is no recursive text import or index. Folder grants remain local to the device and may need reauthorization after restoring a backup.


## Build

Requires JDK 17, Android SDK platform 36 and accepted Android SDK licences. The wrapper uses Gradle 8.14.5; AGP is pinned to 8.7.3, AppCompat to 1.7.0 and Material Components to 1.13.0. `compileSdk=36`, `targetSdk=35`, `minSdk=26`.

Set `ANDROID_HOME` to your SDK directory, or create an untracked `local.properties` containing `sdk.dir=/absolute/path/to/Android/Sdk`.

```sh
cd android
./gradlew :app:assembleDebug :app:testDebugUnitTest :app:lintDebug
adb install -r app/build/outputs/apk/debug/app-debug.apk
```

On this workstation, `./tools/gradle.sh` runs the wrapper with Android Studio Flatpak's JDK 17 extension, avoiding the host's Java 25. Without that Flatpak installation it uses the current Java runtime. No sibling project dependency is required.

## Code map

- `MainActivity.java`: Material screens, lesson orchestration, practice, library, settings and Android document/speech integration.
- `ProviderClient.java`: native HTTPS provider adapters, model catalogs, bounded input and response parsing, cancellation.
- `Thinking.java`: per-provider thinking request formats and chooser options.
- `Records.java`: versioned local records, source metadata, backup validation and atomic writes.
- `LessonAgent.java` / `ToolProtocol.java`: original question/plan tool loop and cross-provider tool/signature history.
- `OriginalCore.java`: AndroidX JavaScript sandbox for the unchanged upstream validation, grading and plan parser.
- `BookDocuments.java` / `SourceInput.java`: on-demand document search and explicit one-turn source approval.
- `InteractiveActivity.java` / `InteractiveTemplates.java`: adapted Agora renderer, sandbox bridge and template catalog.
- `SecretStore.java`: Keystore encryption for provider keys and OAuth account records.
- `ChatGptAccounts.java`: account isolation, browser sign-in orchestration, model catalogs, refresh and revocation.
- `OAuthProtocol.java` / `OAuthLoopback.java`: PKCE, callback and signed identity validation.
- `OpenAiHttp.java` / `ResponseStream.java`: official OAuth/Responses HTTPS routes, preview callbacks and completed-stream validation.
- `ProviderStream.java`: bounded SSE parsers for Chat Completions, Anthropic and Gemini; completed-only persistence.
- `LearningContract.java`: validate structured extraction/practice before mutating local records.
- `Pages.java`: deterministic e-ink page splitting.

Records live in private `files/learning.json`. Provider settings and encrypted secrets use separate private SharedPreferences. All ChatGPT account credentials and catalogs are saved together as one encrypted record; the non-secret installation host ID and first-sign-in welcome state are stored separately. Learning exports contain neither API keys nor provider settings. Lesson requests use the session and original role prompt; library notes are not automatically appended to that system prompt. Legacy practice generation can use up to approximately 18,000 characters of reviewed notes. Book files remain references: only explicitly approved bounded excerpts enter the current user/tool input, then they are removed from replay history before saving. Unsent approval previews stay local. Source URI permissions are device-specific; restoring a backup may require re-linking files through Files. Invalid structured responses are rejected without committing partial results. Restore validates the entire backup, requests confirmation, and uses an atomic replacement; export first to preserve current records.

## Streaming and Markdown

Normal tutoring and practice feedback display live Markdown without replacing the reply editor. Updates follow the bottom only while you are already at the bottom, so scrolling up to read stays under your control. Structured knowledge/practice generation remains buffered until its JSON is validated. Interrupted/cancelled/failed replies are not saved as successful tutor turns. E-ink keeps static pages with rendered Markdown spans and explicit page turns.

Markdown supports CommonMark, tables, strikethrough and native inline/display LaTeX through Markwon 4.6.2 (Apache-2.0). Links open only HTTP/HTTPS destinations after a tap. Remote image fetching and executable HTML are not enabled. The upstream `$inline$` syntax is adapted only during display to the [Markwon LaTeX extension](https://noties.io/Markwon/docs/v4/ext-latex/); original stored messages, prompt text and code spans/fences remain unchanged. Formula rendering uses JLatexMath-Android 0.2.0; its original GPL-2.0-or-later/linking-exception and font notices are retained.

Agora’s streaming Markdown files were inspected for in-place, rate-limited update behavior. Its Compose Markdown renderer is not a dependency of this native Views app. Agora’s interactive web assets are copied unchanged and its native MessagePort host and template catalog are adapted in Java, with attribution and licences included. [Markwon](https://github.com/noties/Markwon) provides native span rendering here.

## Documents and interactive templates

Use the composer attachment button or **Library → Add to library → Link book or document**. Linking stores only a title, type and Android document URI. Search is literal by default; a RE2 switch enables bounded regular expressions. An optional page/chapter/section range limits work. PDF search reads native page text on Android 15+ and uses PDFBox on older versions; EPUB follows reading order one chapter at a time; text is streamed. Scanned PDFs require OCR, which is not yet implemented. EPUB/older PDF paths can make a transient raw-file working copy; no extracted whole-book text or search index is retained.

Search results are local. Choose a passage, review/edit the bounded excerpt, and explicitly approve it for the next reply. A book-related question with attached sources offers a deterministic approval/search gate; no prompt wording was added for this. Books are not automatically searched or sent. Agent `read/grep/find/ls` integration remains in the ledger.

**Library → Interactive templates** imports an Agora template or catalog JSON, adds a built-in example, and supports rename/shortcut/export/delete. An exact `/shortcut` in the reply field opens its template locally. The generated UI runs in Agora’s opaque-origin Websandbox iframe. It has no direct Android file access, native JS interface, account credentials or model key. Follow-ups require native approval before placement in the reply field. Reference-folder/linked-skill execution is not yet ported; metadata round-trips without granting access. E-ink-aware templates use Agora’s profile; others offer a readable fallback.

Original deterministic code and tool descriptions are packaged by [`tools/original-core`](tools/original-core/README.md). Normal APK builds do not need npm; only regeneration does. Android System WebView must support Jetpack JavaScriptSandbox for original question/plan execution. The original system prompt assets are generated from upstream Markdown and checked against its exact composition.

## Verification


Run the Gradle command above for APK assembly, JVM tests and Android lint. Tests exercise pagination/Unicode preservation, full structured-output validation, backup format/duplicate-ID rejection, three provider request/response protocols, authentication/error redaction, HTTPS-only endpoints and bounded reads. Provider tests use in-memory HTTPS connections and make no billable API calls.

Real ChatGPT sign-in and reauthorization were verified on the test device, including completion while Chrome remained foregrounded. Live model quality, provider-account compatibility, system dictation and physical e-ink refresh still require acceptance testing. Android instrumentation uses isolated dummy credentials and mocked HTTPS; it does not sign in to a real ChatGPT account. This is an independent APK distribution, not a Play Store release.

## Licence and attribution

AGPL-3.0-only, matching the upstream repository's [LICENSE](../LICENSE). Original LearnHarness by Amos Blomqvist; Android adaptation in this folder. Agora adaptations preserve its GPL-3.0 attribution; bundled OpenIntelligentUI and Websandbox retain their MIT/Apache-2.0 notices. Bundled core runtime licences and attribution are under `assets/core/licenses/`; native formula-library notices are under `assets/licenses/jlatexmath-android/`. Preserve the licence and provide corresponding source when distributing modified versions under its terms.

ChatGPT protocol references: [sign-in](https://developers.openai.com/siwc/token-sharing-open-source/sign-in), [account sessions](https://developers.openai.com/siwc/token-sharing-open-source/profiles-and-sessions), [models and inference](https://developers.openai.com/siwc/token-sharing-open-source/models-and-inference), [preview limitations](https://developers.openai.com/siwc/token-sharing-open-source/preview-limitations).

Provider protocol references: [Anthropic Messages](https://platform.claude.com/docs/en/api/messages/create), [Gemini generateContent](https://ai.google.dev/api/generate-content). UI uses [Material Components for Android](https://github.com/material-components/material-components-android/blob/master/docs/getting-started.md).
