package com.mikaels.osrstradetracker;

import net.runelite.client.config.Config;
import net.runelite.client.config.ConfigGroup;
import net.runelite.client.config.ConfigItem;

@ConfigGroup("osrstradetracker")
public interface OsrsTradeTrackerConfig extends Config
{
    @ConfigItem(
        keyName = "enabled",
        name = "Enable tracking",
        description = "Track Grand Exchange events and export them to local files."
    )
    default boolean enabled()
    {
        return true;
    }
}
