package com.mikaels.osrstradetracker;

import java.io.BufferedWriter;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

final class EventWriter
{
    private final Path eventsFile;

    EventWriter(Path eventsFile)
    {
        this.eventsFile = eventsFile;
    }

    void append(String json) throws Exception
    {
        Files.createDirectories(eventsFile.getParent());
        try (BufferedWriter writer = Files.newBufferedWriter(
            eventsFile,
            StandardCharsets.UTF_8,
            java.nio.file.StandardOpenOption.CREATE,
            java.nio.file.StandardOpenOption.APPEND))
        {
            writer.write(json);
            writer.newLine();
        }
    }
}
