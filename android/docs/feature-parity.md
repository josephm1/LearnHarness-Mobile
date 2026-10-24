# Android parity ledger

Target: full functional parity with upstream LearnHarness `6cba9c8` (package 0.3.4), with native Material UI and optional e-ink presentation. No missing feature below is excluded from scope. This is an implementation ledger, not a claim that parity has been reached.

## Constraints agreed with the user

- Standalone Android app; no required desktop or hosted backend.
- Preserve original prompt text and composition. Android packages the upstream base, numeric role fragments, then shared fragments using the upstream separators. Do not append Android or book instructions to the system prompt.
- Source documents remain references in Android Files. No whole-book text imports or full-text indexes.
- Native grep/read acts on demand. Show a source preview and obtain explicit approval before providing any passage to a model. Approval is for one reply, with at most 6,000 characters. File attachment alone grants no model access.
- Unused confidence is omitted; a user-selected percentage applies to one answer.
- No silent change from ChatGPT plan billing to paid API billing.

## Audited implementation map

| Original capability and implementation | Android status | Remaining implementation |
| --- | --- | --- |
| Base/role/shared prompt composition: `src/agent/prompt-segments.ts`, `teacher-agent.ts` | Upstream text packaged and checked byte-for-byte after the same trim/join; probing/teaching requests use these prompts | Original adaptive practice and extraction/audit prompts are now connected; editable overrides remain |
| Probe/teach agent loop and question stop rules: `teacher-agent.ts`, `tools.ts`, `core/question-core.ts` | Streaming tool calls across supported protocols; original question validation/cards, one-question pause and bounded repair loop | Generic encoded-argument healing is reused; demo/visual/PDF job tools remain |
| Probe questions, free recall, explain, multiple choice, confidence, dontKnow: `tools.ts`, `tools/practice.ts` | Original probe/quiz/recall/explain/ask contracts, grading, multi-select, optional confidence, reading/reasoning channels and reveal gate | Rich original answer-review presentation remains |
| Probe completion and automatic phase transition: `tools/probe.ts`, `core/phases.ts` | Original probe_done validation and acknowledgment, assessment transcript and automatic teacher handoff | Original coverage and graph tracking implemented; additional presentation parity remains |
| Plan tree: `tools/plan.ts`, `core/plan-tree.ts`, `plan-blocks.ts` | Original Mermaid parser/createTree, native plan reader and exact nodeId validation | Original derived current/visited/upcoming status implemented; full navigation presentation remains |
| Learner graph/chains/evidence: `store/graph.ts`, `core/graph.ts`, `chains.ts` | Original assertion ledgers, deterministic merge/derivation/coverage, native map with original settled layout, joint-premise chains, source anchors and deletion semantics | Broader upstream graph navigation/cluster presentation |
| Three-stage extraction: `subagents/extraction.ts` | Original extract/audit/deterministic-merge pipeline, bounded batching, durable checkpoints, cancellation, per-stage usage and source-fingerprint coverage | Wider real-model and large-history acceptance testing |
| Practice agent: `tools/practice.ts`, role prompt, practice contracts | Original ongoing adaptive problem/conclude loop, proposition targeting, private solutions, thinking traces, hints and explicit next-problem action; legacy sets retained | Original rich practice review presentation |
| Local files: `tools/read.ts`, `ls.ts`, `find.ts`, `grep.ts` | Explicit PDF/EPUB/text file references; bounded on-demand reading and literal/RE2 grep | Original ls/find metadata and approval-gated read/grep contracts connected; user-selected directory browsing and individual file linking implemented; agent traversal of trees and image input remain |
| PDF specialist: `subagents/pdf-reader.ts`, `pdf-ops.ts` | Native page text reading/search with page locations | Original triage/size strategy, agent loop, image/math-heavy page rendering and vision results |
| Research: `subagents/researcher.ts`, `tools/web.ts` | Original researcher prompt, native ChatGPT plan search (live verified), optional Tavily/Brave settings, bounded public-page fetch, original Readability/Turndown, citations, isolated research jobs and durable result delivery | Research PDF download/triage/read tools; live optional Tavily/Brave acceptance |
| Interactive demos: `subagents/demo-builder.ts`, `headless.ts` | Agora renderer/templates, opaque sandbox and e-ink fallback implemented | Original generator, artifact verification and tool integration |
| Visuals/images/SVG: `subagents/visual-maker.ts`, `headless.ts` | Not yet ported | Original generation contracts, compatible image-capable provider, render/verify and native artifact presentation |
| Jobs: `server/jobs.ts`, subagent runner/semaphore | Durable research jobs, three-worker foreground service, notification cancellation, per-job usage/status, explicit interruption/retry and original result/abort wake text | Demo/visual/PDF jobs, full activity dock, background tutor orchestration and job backup; only the selected open lesson wakes automatically |
| Provider catalog and per-role models: `core/settings-resolution.ts`, `server/model-catalog.ts` | Five API protocol/preset choices plus ChatGPT, model browsing and thinking controls | Original strong/cheap/vision slot and eight role overrides implemented; additional adapters/auth variants/capability filtering remain |
| Prices and usage: `core/prices.ts`, `cost-summary.ts` | Completed token totals | Original live/model/job cost accounting and price-catalog updates |
| Context management: `server/compaction.ts`, `core/compaction.ts` | Bounded recent notes and explicit long-session failure | Original compaction, context budget views and retained tool history |
| Markdown/math/demos: original Streamdown/plugins, KaTeX, Mermaid | Native Markdown, code, tables and inline/display LaTeX in lesson/question/option/library views | Mermaid and original artifact generation; Agora interactive renderer/templates available |
| Session navigation/history: `core/session-find.ts`, clusters, native messages | Session list, rename/delete/export, draft preservation | Original local find-in-session and graph source anchors implemented; cross-session history search, branching/cluster views remain |
| Data/settings/prompts: `store/*`, settings/prompt override stores | Android learning JSON backup, encrypted credentials and source-reference metadata | Desktop schema import/export/migration, settings switches, editable prompt overrides and job backups (original graph ledgers are included) |
| Dictation: original provider-specific integrations | Android system dictation | Original provider options, audio input and transcription settings |
| Application updates: Electron updater | Development APK installed through ADB | Android package update/download flow with OS-required user install confirmation |

## Android-specific architectural decisions still open

1. **Interactive renderer reuse: decided.** The user selected Agora’s integrated OpenIntelligentUI renderer and template system. Its bundled web assets and sandbox are reused; the native Java host uses the same private MessagePort/bootstrap design. Template JSON import/export, preview, shortcuts, readable fallbacks and e-ink presentation are implemented. Agora linked skills/source-folder workflows are retained as metadata but their execution remains to be ported; they must never bypass source approval or alter the original system prompts. The original LearnHarness generator/tool loop still needs to be connected.
2. **Read-only filesystem scope.** Desktop tools search a home directory. Android now exposes user-selected folders through Storage Access Framework for on-demand metadata browsing and selected file linking. Agent traversal of these trees remains; arbitrary cross-app private storage is not available. A broader personal-APK storage permission is a separate option, not assumed.
3. **Long-running jobs.** Android may stop an ordinary background process. Research now uses a foreground data-sync service with durable state and cancellation. Android process/service interruption is reported and needs an explicit retry. No promise of unlimited always-on background execution is made.
4. **Service capability/billing.** ChatGPT built-in web search has been verified on the plan route, without a separate search-provider key. Availability remains model/account/workspace dependent. Other research providers and unsupported image/vision operations may need separately configured capabilities. The app must stop and request explicit configuration rather than switch billing.

No item has been declared impossible or removed. Resolve an architectural constraint with the user before replacing or dropping its original behavior.
