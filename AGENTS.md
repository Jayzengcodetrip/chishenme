# Development guide

Read `README.md` and `design-system/chishenme/MASTER.md` before making changes. Page-specific design rules are in `design-system/chishenme/pages/`.

## Current baseline

- v1.4.5 / versionCode 14 is the first public release. Android package: `com.mealpick.app`; Room schema: 4; minimum Android: 8.0 (API 26).
- Kotlin + Jetpack Compose + Room. Keep the existing white/warm-gray interface, persimmon-red accent, and vector launcher icon unless a change is explicitly requested.
- Home ↔ meal-choice navigation uses a stationary fade of about 160ms. Each animated page owns its toolbar and insets; do not remeasure an outgoing page using the destination's toolbar.
- Keep navigation mounted while a meal snapshot loads. Home and meal-choice pages do not depend on meal data; data pages must wait for the selected meal rather than show another meal's rows.

## Data invariants

- Breakfast, lunch, dinner, afternoon tea, and late-night snack have independent editable data, counts, ordering, shopping carts, and cooking menus. Only language is global.
- Capture the originating meal before asynchronous writes. Validate every item/group against that meal. See `docs/meal-isolation.md`.
- Seed data imports once per meal in a transaction. Existing initialization markers prevent reimport even after all records are deleted. Never overwrite user data, restore deleted seeds, or use destructive Room migration.
- The nutrition wheel starts with five options, listed in `docs/nutrition-data-reference.md`. Do not invent child dishes or counts for those groups.
- Same-name items in different locations count independently. Deletion removes the associated counts. Shopping and cooking-menu toggles are independent; only completing a meal increments cooking totals.
- UI language changes fixed interface text only. Preserve editable names and notes verbatim.

## Verification and releases

- Use `./gradlew :app:testDebugUnitTest :app:assembleDebug` for local checks and `:app:connectedDebugAndroidTest` with a dedicated Android test device for UI/data regression. Instrumentation tests reset the debug app's database.
- Add focused regression coverage for meaningful behavioral fixes; do not change unrelated features.
- Keep application identity and the maintainer's signing certificate consistent for official updates. Increment versionCode for any new APK release. Never commit `.signing/`, keystores, passwords, `local.properties`, device databases, private reference photos, or build caches.
- Source-only documentation updates do not imply a rebuilt APK. Report exactly what was built, tested, and published; verify the published package metadata and SHA-256.
- Original Word files are not required to build or test. `tools/verify_seed.py` is an optional maintainer comparison tool for those private source documents.
