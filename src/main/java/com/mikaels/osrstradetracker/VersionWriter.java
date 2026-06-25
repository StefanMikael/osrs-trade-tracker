package com.mikaels.osrstradetracker;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;

final class VersionWriter
{
    static void write(Path versionFile, String sessionId, String outputDir, String version)
    {
        try
        {
            Files.createDirectories(versionFile.getParent());
            String json = "{"
                + "\"plugin\":\"OSRS Trade Tracker\","
                + "\"version\":" + JsonUtil.q(version) + ","
                + "\"sessionId\":" + JsonUtil.q(sessionId) + ","
                + "\"startedAt\":" + JsonUtil.q(Instant.now().toString()) + ","
                + "\"outputDir\":" + JsonUtil.q(outputDir)
                + "}";

            Files.write(
                versionFile,
                json.getBytes(StandardCharsets.UTF_8),
                java.nio.file.StandardOpenOption.CREATE,
                java.nio.file.StandardOpenOption.TRUNCATE_EXISTING
            );
        }
        catch (Exception ignored)
        {
        }
    }
}
