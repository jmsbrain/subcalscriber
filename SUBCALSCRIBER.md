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
