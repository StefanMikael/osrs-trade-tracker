package com.mikaels.osrstradetracker;

import java.io.BufferedWriter;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;

final class PluginLogger
{
    private final Path logFile;

    PluginLogger(Path logFile)
    {
        this.logFile = logFile;
    }

    void write(String level, String message)
    {
        try
        {
            Files.createDirectories(logFile.getParent());
            try (BufferedWriter writer = Files.newBufferedWriter(
                logFile,
                StandardCharsets.UTF_8,
                java.nio.file.StandardOpenOption.CREATE,
                java.nio.file.StandardOpenOption.APPEND))
            {
                writer.write(Instant.now() + " [" + level + "] " + message);
                writer.newLine();
            }
        }
        catch (Exception ignored)
        {
        }
    }
}
