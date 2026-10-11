# SubcalScriber

A calendar viewer for LightOS. Add the web address of an .ics calendar feed and SubcalScriber shows its events. It keeps its own copy of the feeds, separate from the phone's built-in calendar and sync apps like DAVx5.

The tool is in `tool/`. Everything else is Light's SDK ([lightphone/light-sdk](https://github.com/lightphone/light-sdk)).

## Privacy

SubcalScriber only connects to the feed addresses you add. Nothing else leaves the phone.

## Build

```
./gradlew :tool:assembleDebug :tool:lintDebug
```

The APK ends up in `tool/build/outputs/apk/debug/tool-debug.apk`.

## Using it

- **Easiest:** open Tool Manager on your computer, go to SubcalScriber → **Add feeds**, and upload a plain text file with your calendar links, one per line (`https://…` or `webcal://…`). Put a name first to choose it (`Work: https://…`); otherwise it uses the calendar's own name. Open SubcalScriber and they're added; the file is deleted once read.
- **On the phone:** tap the gear, then **ADD FEED**, and type the name and address. (Light's keyboard can't paste.) It downloads right away so a bad address shows up immediately.
- Each feed gets a one-letter tag, shown next to its events. It defaults to a letter of the name that no other feed uses; tap **LETTER** to change it.
- The agenda shows the next 60 days. **SHOW** cycles through all feeds, then one feed at a time.
- Feeds update when the app opens if they're more than an hour old, or when you tap **REFRESH**. Nothing runs in the background.

## Testing on the emulator

`tool/scripts/emulator-start.sh` boots the `Light_Phone` emulator. `tool/scripts/emulator-install.sh` builds, installs and opens SubcalScriber there; add `--testdata` to load the made-up feeds in `tool/testdata`.
