package net.natga999.wynn_ai.menus.widgets;

import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.MinecraftClient;

public interface MenuWidget {
    void render(DrawContext context, MinecraftClient client, int parentX, int parentY);
}