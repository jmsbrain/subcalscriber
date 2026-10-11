# SubcalScriber for LightOS

A calendar viewer for subscribed .ics feeds, kept separate from the phone's own calendar.

This repo starts from [lightphone/light-sdk](https://github.com/lightphone/light-sdk) (its own repo, not a GitHub fork: the account's one fork is `rolodex-light`). The tool lives in `tool/`. Everything else is Light's SDK.

## Before any work

1. Sync with Light's SDK first. Submissions must use the latest SDK.
   ```
   git fetch upstream && git merge upstream/main
   ```
   Conflicts in `tool/` resolve to ours. Anything outside `tool/` resolves to theirs.
2. Re-read `TOOL_GUIDELINES.md`, `CONTRIBUTING.md` (AI/LLM policy) and `docs/tool_metadata/README.md` if they changed in the merge.

## Rules

- Only change `tool/` and this project's own root docs (`CLAUDE.md`, `SUBCALSCRIBER.md`). Never edit Light's SDK, plugin, lint rules, docs or examples.
- Screens extend `LightScreen` / `SimpleLightScreen`, with logic in a `LightViewModel`. Navigate with `navigateTo` / `goBack`.
- Use the SDK UI (`com.thelightphone.sdk.ui`). No Material components, no custom styling where an SDK piece exists.
- Never use `Context`, `Activity`, `Intent`, `LocalContext` or `LocalView`. Use `lightContext` and `callRemoteServiceMethod`. See `BLOCKED_IMPORTS` in `plugin/.../LightSdkPlugin.kt`.
- Permissions and capabilities go only in `tool/lighttool.toml`, and only allowlisted ones (`LightToolPolicy` in `plugin/.../LightToolMetadata.kt`). Libraries only from `allowed-dependencies.txt`.
- Bump `versionCode` (+1) and `versionName` (MAJOR.MINOR.PATCH) for every release. Never change `id`.
- Tag releases `subcalscriber-vX.Y.Z`, never plain `vX.Y.Z`: those tags are Light's SDK versions, which this repo inherits.
- Keep code comments short, in the owner's voice (Light's AI/LLM policy).
- Never post to Light's GitHub (issues, discussions, PRs) for the owner. Drafts are notes for them to rewrite.
- Prefer working offline. Any data leaving the phone must be disclosed (`TOOL_GUIDELINES.md`). No analytics, no ads, no infinite feeds.
- Feeds and events live only inside the tool (`lightContext.filesDir`). Never touch the phone's calendar or sync accounts: keeping clear of them is the point of this tool. The only network traffic is fetching the feed URLs the user adds.

## Check before calling something done

```
./gradlew :tool:assembleDebug :tool:lintDebug
```

## Testing

- Emulator: 1080×1240, API 34, no Google Play. LightOS features: `docs/system_app/README.md`.
- `tool/scripts/emulator-install.sh [--testdata]` builds for the LightOS emulator, installs on `emulator-5554` only and opens SubcalScriber. `--testdata` loads `tool/testdata` (made-up feeds, marked fresh so the fake addresses aren't fetched). It puts `serverPackage` back to `com.lightos` when done.
- Light Phone III: `adb -s <serial> install -r tool/build/outputs/apk/debug/tool-debug.apk` ("Any tools" on).
- Use made-up data for screenshots. Never the owner's real feeds or events.
