# OSRS Trade Tracker

A RuneLite external plugin that records Grand Exchange offer activity to local
JSON files for personal trade analysis and swing-trading tools.

## Features

- Tracks offers created, filled, completed, cancelled, and modified
- Keeps a live snapshot of current Grand Exchange offers
- Stores each RuneScape character in a separate folder
- Records actual fill prices, quantities, worlds, and timestamps
- Writes data locally only; the plugin does not upload trading data

## Local data

Files are written to:

```text
~/.runelite/osrs-trade-tracker/<Character Name>/
```

On Windows:

```text
%USERPROFILE%\.runelite\osrs-trade-tracker\<Character Name>\
```

Each character folder contains:

- `events.jsonl` — historical Grand Exchange events
- `current_offers.json` — current open offers
- `plugin.log` — diagnostic log
- `version.json` — plugin and schema information

These files may contain the character name and trading history. They are not
part of this repository and are excluded by `.gitignore`.

## Development

The project follows the RuneLite external-plugin template and targets Java 11.
A newer JDK, including JDK 17, can also compile it.

Run the development client with:

```text
gradlew.bat run
```

On macOS or Linux:

```text
./gradlew run
```

Assertions are enabled by the Gradle run task.

## Privacy

The plugin stores data only in the local RuneLite directory. It does not send
trade information to a remote server.

## License

BSD 2-Clause License. See `LICENSE`.
