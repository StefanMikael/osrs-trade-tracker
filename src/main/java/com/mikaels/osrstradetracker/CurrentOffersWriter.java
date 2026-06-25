package com.mikaels.osrstradetracker;

import net.runelite.api.WorldType;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import net.runelite.api.Client;
import net.runelite.api.GrandExchangeOffer;
import net.runelite.client.game.ItemManager;

final class CurrentOffersWriter
{
    private final Client client;
    private final ItemManager itemManager;
    private final Path currentOffersFile;
    private final String sessionId;

    CurrentOffersWriter(Client client, ItemManager itemManager, Path currentOffersFile, String sessionId)
    {
        this.client = client;
        this.itemManager = itemManager;
        this.currentOffersFile = currentOffersFile;
        this.sessionId = sessionId;
    }

    void write()
    {
        try
        {
            Files.createDirectories(currentOffersFile.getParent());
            GrandExchangeOffer[] offers = client.getGrandExchangeOffers();

            StringBuilder sb = new StringBuilder();
            sb.append("{")
                .append("\"time\":").append(JsonUtil.q(Instant.now().toString())).append(",")
                .append("\"sessionId\":").append(JsonUtil.q(sessionId)).append(",")
                .append("\"account\":").append(JsonUtil.q(accountName())).append(",")
                .append("\"world\":").append(client.getWorld()).append(",")
                .append("\"membersWorld\":").append(client.getWorldType().contains(WorldType.MEMBERS)).append(",")
                .append("\"offers\":[");

            if (offers != null)
            {
                for (int i = 0; i < offers.length; i++)
                {
                    if (i > 0) sb.append(",");
                    OfferSnapshot snapshot = OfferSnapshot.from(i, offers[i]);

                    if (snapshot == null || snapshot.isEmpty())
                    {
                        sb.append("{\"slot\":").append(i).append(",\"empty\":true}");
                        continue;
                    }

                    sb.append("{")
                        .append("\"slot\":").append(i).append(",")
                        .append("\"empty\":false,")
                        .append("\"side\":").append(JsonUtil.q(snapshot.side())).append(",")
                        .append("\"itemId\":").append(snapshot.itemId).append(",")
                        .append("\"itemName\":").append(JsonUtil.q(itemName(snapshot.itemId))).append(",")
                        .append("\"offerPrice\":").append(snapshot.offerPrice).append(",")
                        .append("\"totalQuantity\":").append(snapshot.totalQuantity).append(",")
                        .append("\"filledQuantity\":").append(snapshot.quantityFilled).append(",")
                        .append("\"spentGp\":").append(snapshot.spentGp).append(",")
                        .append("\"state\":").append(JsonUtil.q(snapshot.state)).append(",")
                        .append("\"complete\":").append(snapshot.isComplete()).append(",")
                        .append("\"cancelled\":").append(snapshot.isCancelled())
                        .append("}");
                }
            }

            sb.append("]}");

            Files.write(
                currentOffersFile,
                sb.toString().getBytes(StandardCharsets.UTF_8),
                java.nio.file.StandardOpenOption.CREATE,
                java.nio.file.StandardOpenOption.TRUNCATE_EXISTING
            );
        }
        catch (Exception ignored)
        {
        }
    }

    private String accountName()
    {
        try
        {
            return client.getLocalPlayer() == null ? "" : client.getLocalPlayer().getName();
        }
        catch (Exception e)
        {
            return "";
        }
    }

    private String itemName(int itemId)
    {
        if (itemId <= 0) return "";

        try
        {
            return itemManager.getItemComposition(itemId).getName();
        }
        catch (Exception e)
        {
            return "Item " + itemId;
        }
    }
}
