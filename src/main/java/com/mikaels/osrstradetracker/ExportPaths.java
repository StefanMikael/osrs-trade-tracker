package com.mikaels.osrstradetracker;

import java.nio.file.Path;
import java.nio.file.Paths;

final class ExportPaths
{
    final Path outputDir;
    final Path eventsFile;
    final Path currentOffersFile;
    final Path pluginLogFile;
    final Path versionFile;

    private ExportPaths(Path outputDir)
    {
        this.outputDir = outputDir;
        this.eventsFile = outputDir.resolve("events.jsonl");
        this.currentOffersFile = outputDir.resolve("current_offers.json");
        this.pluginLogFile = outputDir.resolve("plugin.log");
        this.versionFile = outputDir.resolve("version.json");
    }

    static ExportPaths defaults()
    {
        Path dir = Paths.get(System.getProperty("user.home"), ".runelite", "osrs-trade-tracker").toAbsolutePath();
        return new ExportPaths(dir);
    }
}
