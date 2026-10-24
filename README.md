# LearnHarness Mobile

An independent native Android adaptation of [LearnHarness](https://github.com/amosblomqvist/learnharness) by Amos Blomqvist, based on upstream revision `6cba9c8`. This project is not the official upstream Android release.

[Download the APK](https://github.com/josephm1/LearnHarness-Mobile/releases/latest) · [Android documentation](android/README.md) · [Feature parity](android/docs/feature-parity.md)

Native Material You learning and practice chats, streamed Markdown, a knowledge graph, local document references with bounded read/grep and explicit source approval, provider/model/thinking controls, and an optional high-contrast e-ink mode. New lessons open directly into chat; structured questions have an explicit Submit answer action.

Android 8.0 (API 26) or newer is required. AI requests need internet and a compatible provider account. ChatGPT sign-in availability, models and plan usage depend on the account and service policy; API-key billing is separate. No credentials or user learning records are bundled.

Full upstream feature parity is still in progress. See the [parity ledger](android/docs/feature-parity.md) for implemented features and remaining work.

## Build

Install JDK 17 and the Android SDK (platform 36). Set `ANDROID_HOME`, or create an untracked `android/local.properties` containing `sdk.dir` for your SDK.

```sh
cd android
./gradlew :app:assembleDebug
./gradlew :app:testDebugUnitTest :app:lintDebug
```

The Gradle wrapper downloads Gradle as needed. Prebundled agent assets are included; building the Android app does not require Node.js. To regenerate those assets, install Node.js, then run:

```sh
npm ci --prefix android/tools/original-core
npm run --prefix android/tools/original-core build
npm run --prefix android/tools/original-core test
```

The original learning/core sources are retained in `src/` to reproduce the agent bundle and verbatim prompt assets. The desktop/web app is maintained in the original repository.

## APK signing

The release APK is signed with a dedicated distribution key. Signing keys are stored outside this repository and are never included in Git or releases. `:app:assembleRelease` produces an unsigned APK; sign your own distribution using Android SDK `apksigner`. Locally generated debug builds use your own debug key and cannot update the published APK without uninstalling first.

## Credits and license

Derived from [amosblomqvist/learnharness](https://github.com/amosblomqvist/learnharness), licensed under [AGPL-3.0](LICENSE). The original agent prompts remain unchanged. The Android adaptation was modified in October 2026.

Interactive rendering uses the Agora Android integration of [OpenIntelligentUI](https://github.com/CopilotKit/OpenIntelligentUI) (MIT) and JetBrains Websandbox (Apache-2.0). Agora GPL-3.0 and other third-party notices are retained in the [bundled license/provenance files](android/app/src/main/assets/interactive/PROVENANCE.md) and dependency license directories.
