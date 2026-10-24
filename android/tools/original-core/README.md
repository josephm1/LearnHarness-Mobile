# Original core on Android

The bundle reuses unchanged upstream question grading, plan parsing/status derivation, argument healing, local session search, graph assertions/chains/coverage/deletion/layout and graph read tools. Build adapters extract the original practice, extraction/auditor and researcher instructions, schemas and tool descriptions directly from source. Gradle separately packages the original composed role prompts. No Android or book instructions are appended.

`entry.ts` adapts JSON requests to these original functions inside AndroidX JavaScriptEngine 1.1.1. Each active agent gets an isolate; concurrent agents share one sandbox connection, as required by Android. No network or filesystem APIs are exposed. Web articles use an in-memory LinkeDOM DOM with original Readability and Turndown; page scripts are never evaluated. Only the local bundled program is evaluated, and all request values are JSON encoded.

Platform adapters provide standard UTF-8/base64 APIs, SHA-256, Android SecureRandom entropy for original graph IDs, and manually stepped stopped D3 layout timers. Graph merges and extraction use Android persistence/transport adapters while preserving original domain decisions. A system WebView supporting JavaScriptSandbox is required; unsupported devices report the requirement.

Generated assets are retained so ordinary APK builds need only Gradle, JDK and the Android SDK. Regenerate after upstream changes:

```sh
npm ci --prefix android/tools/original-core
npm run --prefix android/tools/original-core build
npm run --prefix android/tools/original-core test
```

Exact dependency versions are pinned in package-lock.json. Build regeneration copies runtime package licences and attribution to `app/src/main/assets/core/licenses/`, including Readability (MPL-2.0), LinkeDOM (ISC), Turndown, D3, TypeBox and noble hashes (MIT), and fast-text-encoding (Apache-2.0). Esbuild is build-time only. LearnHarness remains AGPL-3.0-only.

This is not a claim of full parity. Native transport/orchestration limitations, unported job tools and acceptance work remain tracked in `android/docs/feature-parity.md`.
