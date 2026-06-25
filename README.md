# OSRS Trade Tracker

A RuneLite external plugin project for exporting Grand Exchange events to the OSRS swing trade scanner.

## Open in IntelliJ

Open this folder directly:

`osrs-trade-tracker-full-project`

Set Gradle JVM to Temurin 17.

Run:

`src/test/java/com/mikaels/osrstradetracker/OsrsTradeTrackerPluginTest.java`

Add VM option:

`-ea`

## Output folder

`%USERPROFILE%\.runelite\osrs-trade-tracker`

Files:

- `events.jsonl`
- `current_offers.json`
- `plugin.log`
- `version.json`

## Gradle wrapper

This project includes the Gradle wrapper. On Windows, commands can be run with `gradlew.bat`; on macOS or Linux, use `./gradlew`.
