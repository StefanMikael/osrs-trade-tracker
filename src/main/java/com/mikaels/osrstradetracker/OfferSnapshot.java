package com.mikaels.osrstradetracker;

import net.runelite.api.GrandExchangeOffer;

final class OfferSnapshot
{
    final int slot;
    final int itemId;
    final int offerPrice;
    final int totalQuantity;
    final int quantityFilled;
    final int spentGp;
    final String state;

    private OfferSnapshot(int slot, int itemId, int offerPrice, int totalQuantity, int quantityFilled, int spentGp, String state)
    {
        this.slot = slot;
        this.itemId = itemId;
        this.offerPrice = offerPrice;
        this.totalQuantity = totalQuantity;
        this.quantityFilled = quantityFilled;
        this.spentGp = spentGp;
        this.state = state == null ? "" : state;
    }

    static OfferSnapshot from(int slot, GrandExchangeOffer offer)
    {
        if (offer == null)
        {
            return null;
        }

        return new OfferSnapshot(
            slot,
            offer.getItemId(),
            offer.getPrice(),
            offer.getTotalQuantity(),
            offer.getQuantitySold(),
            offer.getSpent(),
            String.valueOf(offer.getState())
        );
    }

    boolean isEmpty()
    {
        return itemId <= 0 || totalQuantity <= 0 || state.toUpperCase().contains("EMPTY");
    }

    boolean isBuy()
    {
        String s = state.toUpperCase();
        return s.contains("BUY") || s.contains("BOUGHT");
    }

    boolean isSell()
    {
        String s = state.toUpperCase();
        return s.contains("SELL") || s.contains("SOLD");
    }

    boolean isComplete()
    {
        String s = state.toUpperCase();
        return totalQuantity > 0
            && quantityFilled >= totalQuantity
            && (s.contains("BOUGHT") || s.contains("SOLD"));
    }

    boolean isCancelled()
    {
        String s = state.toUpperCase();
        return s.contains("CANCEL") || s.contains("ABORT");
    }

    String side()
    {
        if (isBuy()) return "BUY";
        if (isSell()) return "SELL";
        return "UNKNOWN";
    }
}
