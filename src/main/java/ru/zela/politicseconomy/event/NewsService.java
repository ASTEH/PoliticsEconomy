package ru.zela.politicseconomy.event;

import net.minecraft.server.MinecraftServer;

/** Small facade for writing news from any subsystem. */
public final class NewsService {
    private NewsService() {}

    public static void add(
        MinecraftServer server,
        long tick,
        String category,
        String title,
        String body
    ) {
        NewsSavedData.get(server).add(tick, category, title, body);
    }
}
