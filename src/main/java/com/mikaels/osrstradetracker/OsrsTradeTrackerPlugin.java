package com.mikaels.osrstradetracker;

import com.google.inject.Provides;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.Instant;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import javax.inject.Inject;
import lombok.extern.slf4j.Slf4j;
import net.runelite.api.Client;
import net.runelite.api.GameState;
import net.runelite.api.GrandExchangeOffer;
import net.runelite.api.WorldType;
import net.runelite.api.events.GameStateChanged;
import net.runelite.api.events.GameTick;
import net.runelite.api.events.GrandExchangeOfferChanged;
import net.runelite.client.config.ConfigManager;
import net.runelite.client.eventbus.Subscribe;
import net.runelite.client.game.ItemManager;
import net.runelite.client.plugins.Plugin;
import net.runelite.client.plugins.PluginDescriptor;

@Slf4j
@PluginDescriptor(
    name = "OSRS Trade Tracker",
    description = "Exports Grand Exchange trade events for the OSRS swing trade scanner.",
    tags = {"grand exchange", "ge", "trade", "tracker", "flipping"}
)
public class OsrsTradeTrackerPlugin extends Plugin
{
    private static final String PLUGIN_VERSION = "0.4.0";
    private static final int CURRENT_OFFERS_REFRESH_TICKS = 5;
    private static final long MODIFICATION_WINDOW_MS = 30_000L;

    private static final Path BASE_OUTPUT_DIR = Paths.get(
        System.getProperty("user.home"),
        ".runelite",
        "osrs-trade-tracker"
    );

    @Inject private Client client;
    @Inject private OsrsTradeTrackerConfig config;
    @Inject private ItemManager itemManager;

    private final Map<Integer, OfferSnapshot> snapshots = new HashMap<>();
    private final Map<Integer, OfferSnapshot> recentlyCancelledOffers = new HashMap<>();
    private final Map<Integer, Long> recentlyCancelledAt = new HashMap<>();

    private EventWriter eventWriter;
    private CurrentOffersWriter currentOffersWriter;
    private PluginLogger pluginLogger;

    private Path outputDir;
    private String activeAccountName;
    private String sessionId;
    private long sequence;
    private int currentOffersRefreshTick;

    @Override
    protected void startUp()
    {
        activeAccountName = null;
        outputDir = null;
        sessionId = null;
        sequence = 0;
        currentOffersRefreshTick = 0;

        eventWriter = null;
        currentOffersWriter = null;
        pluginLogger = null;
        snapshots.clear();
        recentlyCancelledOffers.clear();
        recentlyCancelledAt.clear();

        log.info("OSRS Trade Tracker started. Waiting for a logged-in character.");
    }

    @Override
    protected void shutDown()
    {
        if (pluginLogger != null)
        {
            pluginLogger.write(
                "SHUTDOWN",
                "OSRS Trade Tracker stopped for " + activeAccountName + ". Session " + sessionId
            );
        }

        log.info("OSRS Trade Tracker stopped.");
    }

    /**
     * Refresh the current-offers snapshot every five game ticks.
     *
     * This also detects the logged-in character. Each character receives its
     * own folder under .runelite/osrs-trade-tracker.
     */
    @Subscribe
    public void onGameStateChanged(GameStateChanged event)
    {
        if (event.getGameState() == GameState.LOGGED_IN)
        {
            ensureAccountInitialized();
        }
    }

    @Subscribe
    public void onGameTick(GameTick event)
    {
        // Character-folder setup must not depend on the optional tracking toggle.
        // This guarantees the folder is created as soon as a character is detected.
        if (!ensureAccountInitialized())
        {
            return;
        }

        if (!config.enabled())
        {
            return;
        }

        currentOffersRefreshTick++;

        if (currentOffersRefreshTick < CURRENT_OFFERS_REFRESH_TICKS)
        {
            return;
        }

        currentOffersRefreshTick = 0;
        currentOffersWriter.write();

        // The initial login read can happen before existing GE offers are loaded.
        if (snapshots.isEmpty())
        {
            loadInitialSnapshots();
        }
    }

    @Subscribe
    public void onGrandExchangeOfferChanged(GrandExchangeOfferChanged event)
    {
        if (!ensureAccountInitialized())
        {
            return;
        }

        if (!config.enabled())
        {
            return;
        }

        int slot = event.getSlot();
        GrandExchangeOffer offer = event.getOffer();

        OfferSnapshot previous = snapshots.get(slot);
        OfferSnapshot current = OfferSnapshot.from(slot, offer);

        if (current == null || current.isEmpty())
        {
            if (previous != null
                && !previous.isEmpty()
                && !previous.isComplete()
                && !previous.isCancelled())
            {
                writeEvent("OFFER_ABORTED", slot, previous, previous, 0, 0, 0, false, false);
            }

            snapshots.remove(slot);
            currentOffersWriter.write();
            return;
        }

        OfferSnapshot modificationSource = findRecentModificationSource(slot, current);

        if (previous == null || previous.isEmpty())
        {
            if (modificationSource != null)
            {
                writeEvent("OFFER_MODIFIED", slot, modificationSource, current, 0, 0, 0, false, false);
                clearRecentCancellation(slot);
            }
            else
            {
                writeEvent("OFFER_CREATED", slot, current, current, 0, 0, 0, false, false);
                clearRecentCancellation(slot);
            }
        }

        boolean sameActiveOffer = isSameActiveOffer(previous, current);
        boolean priceChanged = sameActiveOffer && previous.offerPrice != current.offerPrice;
        boolean quantityChanged = sameActiveOffer && previous.totalQuantity != current.totalQuantity;

        if (priceChanged || quantityChanged)
        {
            writeEvent("OFFER_MODIFIED", slot, previous, current, 0, 0, 0, false, false);
        }

        int deltaQty = previous == null
            ? current.quantityFilled
            : current.quantityFilled - previous.quantityFilled;

        int deltaGp = previous == null
            ? current.spentGp
            : current.spentGp - previous.spentGp;

        boolean hasFill = deltaQty > 0 && deltaGp >= 0;
        boolean completeNow = current.isComplete();
        boolean wasComplete = previous != null && previous.isComplete();
        boolean becameComplete = completeNow && !wasComplete;
        boolean partialFill = hasFill && !completeNow;

        if (hasFill)
        {
            int actualPrice = deltaQty == 0 ? 0 : deltaGp / deltaQty;
            writeEvent(
                fillType(current),
                slot,
                previous,
                current,
                deltaQty,
                deltaGp,
                actualPrice,
                partialFill,
                becameComplete
            );
        }

        if (becameComplete)
        {
            writeEvent("OFFER_COMPLETED", slot, previous, current, 0, 0, 0, false, true);
        }

        if (current.isCancelled() && (previous == null || !previous.isCancelled()))
        {
            writeEvent("OFFER_CANCELLED", slot, previous, current, 0, 0, 0, false, false);
            recentlyCancelledOffers.put(slot, current);
            recentlyCancelledAt.put(slot, System.currentTimeMillis());
        }

        snapshots.put(slot, current);
        currentOffersWriter.write();
    }

    /**
     * Initializes the writers for the logged-in character and automatically
     * switches folders if another character is logged into the same client.
     */
    private boolean ensureAccountInitialized()
    {
        String detectedAccountName = accountName();

        if (detectedAccountName.isEmpty())
        {
            return false;
        }

        if (detectedAccountName.equals(activeAccountName)
            && eventWriter != null
            && currentOffersWriter != null
            && pluginLogger != null)
        {
            return true;
        }

        try
        {
            initializeForAccount(detectedAccountName);
            return true;
        }
        catch (Exception e)
        {
            log.warn("Could not initialize trade tracking for {}", detectedAccountName, e);
            return false;
        }
    }

    private void initializeForAccount(String accountName) throws Exception
    {
        if (pluginLogger != null && activeAccountName != null)
        {
            pluginLogger.write(
                "ACCOUNT_SWITCH",
                "Stopped writing for " + activeAccountName + " because " + accountName + " is now logged in."
            );
        }

        String folderName = safeFolderName(accountName);
        Path newOutputDir = BASE_OUTPUT_DIR.resolve(folderName);
        Files.createDirectories(newOutputDir);

        String newSessionId = UUID.randomUUID().toString();
        EventWriter newEventWriter = new EventWriter(newOutputDir.resolve("events.jsonl"));
        CurrentOffersWriter newCurrentOffersWriter = new CurrentOffersWriter(
            client,
            itemManager,
            newOutputDir.resolve("current_offers.json"),
            newSessionId
        );
        PluginLogger newPluginLogger = new PluginLogger(newOutputDir.resolve("plugin.log"));

        activeAccountName = accountName;
        outputDir = newOutputDir;
        sessionId = newSessionId;
        sequence = 0;
        currentOffersRefreshTick = 0;

        eventWriter = newEventWriter;
        currentOffersWriter = newCurrentOffersWriter;
        pluginLogger = newPluginLogger;

        snapshots.clear();
        recentlyCancelledOffers.clear();
        recentlyCancelledAt.clear();
        loadInitialSnapshots();

        VersionWriter.write(
            outputDir.resolve("version.json"),
            sessionId,
            outputDir.toString(),
            PLUGIN_VERSION
        );

        currentOffersWriter.write();
        pluginLogger.write(
            "STARTUP",
            "OSRS Trade Tracker started for " + activeAccountName
                + ". Session " + sessionId
                + ". Folder " + outputDir
        );

        log.info(
            "OSRS Trade Tracker is writing character {} to {}",
            activeAccountName,
            outputDir
        );
    }

    private String safeFolderName(String accountName)
    {
        String safe = accountName
            .trim()
            .replaceAll("[\\\\/:*?\"<>|]", "_")
            .replaceAll("[. ]+$", "");

        return safe.isEmpty() ? "Unknown Character" : safe;
    }

    private void loadInitialSnapshots()
    {
        try
        {
            GrandExchangeOffer[] offers = client.getGrandExchangeOffers();

            if (offers == null)
            {
                return;
            }

            for (int i = 0; i < offers.length; i++)
            {
                OfferSnapshot snapshot = OfferSnapshot.from(i, offers[i]);

                if (snapshot != null && !snapshot.isEmpty())
                {
                    snapshots.put(i, snapshot);
                }
            }
        }
        catch (Exception e)
        {
            if (pluginLogger != null)
            {
                pluginLogger.write(
                    "ERROR",
                    "Could not load initial GE snapshots: " + e.getMessage()
                );
            }
        }
    }

    private OfferSnapshot findRecentModificationSource(int slot, OfferSnapshot current)
    {
        OfferSnapshot cancelled = recentlyCancelledOffers.get(slot);
        Long cancelledAt = recentlyCancelledAt.get(slot);

        if (cancelled == null || cancelledAt == null)
        {
            return null;
        }

        if (System.currentTimeMillis() - cancelledAt > MODIFICATION_WINDOW_MS)
        {
            clearRecentCancellation(slot);
            return null;
        }

        if (cancelled.itemId != current.itemId || !cancelled.side().equals(current.side()))
        {
            return null;
        }

        boolean priceChanged = cancelled.offerPrice != current.offerPrice;
        boolean quantityChanged = cancelled.totalQuantity != current.totalQuantity;

        return priceChanged || quantityChanged ? cancelled : null;
    }

    private void clearRecentCancellation(int slot)
    {
        recentlyCancelledOffers.remove(slot);
        recentlyCancelledAt.remove(slot);
    }

    private boolean isSameActiveOffer(OfferSnapshot previous, OfferSnapshot current)
    {
        return previous != null
            && current != null
            && !previous.isEmpty()
            && !previous.isComplete()
            && !previous.isCancelled()
            && previous.itemId == current.itemId
            && previous.side().equals(current.side());
    }

    private String fillType(OfferSnapshot snapshot)
    {
        if (snapshot.isBuy())
        {
            return "BUY_FILL";
        }

        if (snapshot.isSell())
        {
            return "SELL_FILL";
        }

        return "GE_FILL";
    }

    private void writeEvent(
        String eventType,
        int slot,
        OfferSnapshot previous,
        OfferSnapshot current,
        int deltaQty,
        int deltaGp,
        int actualPrice,
        boolean partialFill,
        boolean completedByThisEvent)
    {
        if (current == null || eventWriter == null || pluginLogger == null)
        {
            return;
        }

        try
        {
            long seq = ++sequence;
            String eventId = UUID.randomUUID().toString();
            String now = Instant.now().toString();

            String json = "{"
                + "\"eventId\":" + JsonUtil.q(eventId) + ","
                + "\"sessionId\":" + JsonUtil.q(sessionId) + ","
                + "\"sequence\":" + seq + ","
                + "\"time\":" + JsonUtil.q(now) + ","
                + "\"event\":" + JsonUtil.q(eventType) + ","
                + "\"account\":" + JsonUtil.q(activeAccountName) + ","
                + "\"world\":" + client.getWorld() + ","
                + "\"membersWorld\":" + client.getWorldType().contains(WorldType.MEMBERS) + ","
                + "\"slot\":" + slot + ","
                + "\"side\":" + JsonUtil.q(current.side()) + ","
                + "\"itemId\":" + current.itemId + ","
                + "\"itemName\":" + JsonUtil.q(itemName(current.itemId)) + ","
                + "\"deltaQty\":" + deltaQty + ","
                + "\"deltaGp\":" + deltaGp + ","
                + "\"actualPrice\":" + actualPrice + ","
                + "\"offerPrice\":" + current.offerPrice + ","
                + "\"offerState\":" + JsonUtil.q(current.state) + ","
                + "\"partialFill\":" + partialFill + ","
                + "\"completedByThisEvent\":" + completedByThisEvent + ","
                + "\"offerComplete\":" + current.isComplete() + ","
                + "\"offerCancelled\":" + current.isCancelled() + ","
                + "\"totalFilled\":" + current.quantityFilled + ","
                + "\"totalQuantity\":" + current.totalQuantity + ","
                + "\"spentGp\":" + current.spentGp + ","
                + "\"previousFilled\":" + (previous == null ? 0 : previous.quantityFilled) + ","
                + "\"previousSpentGp\":" + (previous == null ? 0 : previous.spentGp) + ","
                + "\"previousOfferPrice\":" + (previous == null ? 0 : previous.offerPrice) + ","
                + "\"previousTotalQuantity\":" + (previous == null ? 0 : previous.totalQuantity) + ","
                + "\"priceChanged\":" + (previous != null && previous.offerPrice != current.offerPrice) + ","
                + "\"quantityChanged\":" + (previous != null && previous.totalQuantity != current.totalQuantity) + ","
                + "\"priceDelta\":" + (previous == null ? 0 : current.offerPrice - previous.offerPrice) + ","
                + "\"quantityDelta\":" + (previous == null ? 0 : current.totalQuantity - previous.totalQuantity)
                + "}";

            eventWriter.append(json);
            pluginLogger.write("EVENT", json);
            log.info("Recorded GE event: {}", json);
        }
        catch (Exception e)
        {
            log.warn("Failed to write GE event", e);

            if (pluginLogger != null)
            {
                pluginLogger.write(
                    "ERROR",
                    "Failed to write GE event: " + e.getMessage()
                );
            }
        }
    }

    private String accountName()
    {
        try
        {
            return client.getLocalPlayer() == null
                ? ""
                : client.getLocalPlayer().getName();
        }
        catch (Exception e)
        {
            return "";
        }
    }

    private String itemName(int itemId)
    {
        if (itemId <= 0)
        {
            return "";
        }

        try
        {
            return itemManager.getItemComposition(itemId).getName();
        }
        catch (Exception e)
        {
            return "Item " + itemId;
        }
    }

    @Provides
    OsrsTradeTrackerConfig provideConfig(ConfigManager configManager)
    {
        return configManager.getConfig(OsrsTradeTrackerConfig.class);
    }
}
