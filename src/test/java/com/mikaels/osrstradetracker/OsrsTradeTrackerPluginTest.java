package com.mikaels.osrstradetracker;
import net.runelite.api.WorldType;
import net.runelite.client.RuneLite;
import net.runelite.client.externalplugins.ExternalPluginManager;

public class OsrsTradeTrackerPluginTest
{
    public static void main(String[] args) throws Exception
    {
        ExternalPluginManager.loadBuiltin(OsrsTradeTrackerPlugin.class);
        RuneLite.main(args);
    }
}
