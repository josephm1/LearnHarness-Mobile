# Interactive viewer assets

- Open Intelligent UI: CopilotKit/OpenIntelligentUI revision
  `f6e4388b26a64b9a0714943b08a1ce622b924eec` (MIT).
  `design-system.js` contains its exported theme, SVG/form CSS and library import map.
- `websandbox.js`: published `@jetbrains/websandbox` 1.2.1 UMD distribution (Apache-2.0),
  with its source-map reference removed and blank Webpack comment banners normalized.
- `host.js`: Agora's plain-JavaScript Android host, adapted from upstream final-frame
  assembly and validated `sendPrompt`/`openLink` patterns. No React/Next.js runtime.
- License copies are packaged alongside these files. Keep them when updating assets.

The original Agora integration packages these assets using its `scripts/package-interactive-assets.py` script; that script is not part of this repository.
Python and Node are packaging-time tools; Gradle and the installed application use the
committed assets without npm or Node. The command fetches the exact upstream revision
and package version and does not update maintained application code.

The opaque-origin iframe keeps the upstream sandbox boundary. The enclosing trusted
host must permit the same script sources because srcdoc inherits its parent's CSP.
Only the trusted document receives Android's private MessagePort; neither an Android
JavaScript interface nor same-origin iframe access is enabled. Native confirmation
is required before a follow-up is sent. Generated artifacts are never host markup.

LearnHarness Android reuses these assets from the Agora Android integration.
The native Java host is adapted for this application's Activities, confirmation flow
and private template catalogue. Agora's GPL-3.0 license is retained alongside the
MIT and Apache-2.0 notices. The enclosing LearnHarness project is AGPL-3.0.
