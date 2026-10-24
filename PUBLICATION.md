# Public distribution checks — 25 October 2026

This repository starts with a clean source snapshot, without local development Git history. It credits the upstream project and preserves its AGPL license and third-party license notices.

Excluded: local SDK settings, build caches, dependencies, APKs in Git, user records, account storage, credentials, signing keys, device screenshots, device identifiers and development logs. APKs are distributed as GitHub Release assets. Commit metadata uses a generic project identity.

The source snapshot and uncompressed APK contents were checked for private-key markers, provider/GitHub tokens, local home-directory paths and known development identifiers. Gitleaks additionally scanned the source: its five generic-key findings were reviewed and were public model catalog IDs in the unchanged upstream `src/core/prices.ts`, rather than credentials. Test credentials and document URIs are synthetic fixtures.

The release APK is non-debuggable and uses a dedicated RSA signing certificate with the generic subject `CN=LearnHarness Mobile`. Its signature is verified with Android SDK apksigner. Private signing material is held outside the repository.

The clean source snapshot builds the release APK and passes 116 JVM tests and Android release lint. Prior emulator instrumentation and UI checks are recorded in the validation report. No assertion of complete upstream feature parity is made.
