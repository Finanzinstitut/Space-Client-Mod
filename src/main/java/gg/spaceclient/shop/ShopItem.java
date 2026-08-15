package gg.spaceclient.shop;

/** One entry in the catalogue, as the server describes it. */
public record ShopItem(String id, String name, String type, int price, boolean owned) {}
