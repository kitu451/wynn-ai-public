package net.natga999.wynn_ai.menus.widgets;

import net.natga999.wynn_ai.managers.HarvestingManager;

import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;

public class MouseButtonSwitchWidget implements MenuWidget {
    public enum MouseButton {
        LEFT("LMC", 0xFF0000FF),  // Blue for left
        RIGHT("RMC", 0xFFFF0000); // Red for right

        public final String displayText;
        public final int color;

        MouseButton(String displayText, int color) {
            this.displayText = displayText;
            this.color = color;
        }
    }

    private final int x, y, width, height;
    private final String action;
    private MouseButton selectedButton;

    public MouseButtonSwitchWidget(int x, int y, int width, int height, String action, MouseButton defaultButton) {
        this.x = x;
        this.y = y;
        this.width = width;
        this.height = height;
        this.action = action;
        this.selectedButton = HarvestingManager.getMouseButtonState(action, defaultButton);
        HarvestingManager.addButtonChangeListener(action, this::updateButtonState);
    }

    private void updateButtonState() {
        this.selectedButton = HarvestingManager.getMouseButtonState(action, selectedButton);
    }

    @Override
    public void render(DrawContext context, MinecraftClient client, int parentX, int parentY) {
        updateButtonState(); // Ensure latest state before rendering
        int drawX = parentX + x;
        int drawY = parentY + y;

        // Background and border
        context.fill(drawX, drawY, drawX + width, drawY + height, 0x80000000);
        context.drawBorder(drawX, drawY, width, height, 0xFFFFFFFF);

        // Text rendering
        String displayText = selectedButton.displayText;
        int textWidth = client.textRenderer.getWidth(displayText);
        int textX = drawX + (width - textWidth + 1) / 2;
        int textY = drawY + (height - client.textRenderer.fontHeight + 1) / 2;

        // Colored background behind text
        context.fill(drawX + 2, drawY + 2, drawX + width - 2, drawY + height - 2, selectedButton.color);
        context.drawText(client.textRenderer, displayText, textX, textY, 0xFFFFFF, false);
    }

    public void toggle() {
        MouseButton newState = (selectedButton == MouseButton.LEFT) ? MouseButton.RIGHT : MouseButton.LEFT;
        HarvestingManager.setMouseButtonState(action, newState);
    }

    public MouseButton getSelectedButton() {
        return selectedButton;
    }

    public boolean isMouseOver(double mouseX, double mouseY, int menuX, int menuY) {
        return mouseX >= menuX + x && mouseX <= menuX + x + width &&
                mouseY >= menuY + y && mouseY <= menuY + y + height;
    }

    public String getAction() {
        return action;
    }
}