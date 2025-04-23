package net.natga999.wynn_ai.menus.widgets;

import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.MinecraftClient;

public class ButtonWidget implements MenuWidget {
    private final int x, y, width, height;
    private final String text;
    private final String action;

    public ButtonWidget(int x, int y, int width, int height, String text, String action) {
        this.x = x;
        this.y = y;
        this.width = width;
        this.height = height;
        this.text = text;
        this.action = action;
    }

    @Override
    public void render(DrawContext context, MinecraftClient client, int parentX, int parentY) {
        int drawX = parentX + x;
        int drawY = parentY + y;
        context.drawBorder(drawX, drawY, width, height, 0xFFFFFFFF);
        context.fill(drawX, drawY, drawX + width, drawY + height, 0x80000000); // Semi-transparent box
        context.drawText(client.textRenderer, text, drawX + (width - client.textRenderer.getWidth(text)
        ) / 2, drawY + (height - client.textRenderer.fontHeight + 1) / 2, 0xFFFFFF, false);
    }

    public boolean isMouseOver(double mouseX, double mouseY, int menuX, int menuY) {
        return mouseX >= menuX + x && mouseX <= menuX + x + width &&
                mouseY >= menuY + y && mouseY <= menuY + y + height;
    }

    public String getAction() {
        return action;
    }
}