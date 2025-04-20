package net.natga999.wynn_ai.menus.huds.widgets;

import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;

import static net.natga999.wynn_ai.menus.huds.MenuHUDLoader.getSliderValueOrDefault;
import static net.natga999.wynn_ai.menus.huds.MenuHUDLoader.setSliderValue;

public class SliderWidget implements MenuWidget {
    private final int x, y, width, height;
    private final String text;
    private final String action;
    private float value;
    private final float min;
    private final float max;
    private final float step;
    private boolean isDragging = false;

    public SliderWidget(int x, int y, int width, int height, String text, String action, float defaultValue, float min, float max, float step) {
        this.x = x;
        this.y = y;
        this.width = width;
        this.height = height;
        this.text = text;
        this.action = action;
        this.value = getSliderValueOrDefault(action, defaultValue);
        this.min = min;
        this.max = max;
        this.step = step;
    }

    @Override
    public void render(DrawContext context, MinecraftClient client, int parentX, int parentY) {
        int drawX = parentX + x;
        int drawY = parentY + y;
        context.drawBorder(drawX, drawY, width, height, 0xFFFFFFFF);
        context.fill(drawX, drawY, drawX + width, drawY + height, 0x80000000); // Semi-transparent box

        // Calculate knob position
        float percentage = (value - min) / (max - min);
        int knobPosition = drawX + Math.round(percentage * (width - 10));

        // Draw slider knob
        context.fill(knobPosition, drawY, knobPosition + 10, drawY + height, 0xFFAAAAAA);
        context.drawBorder(knobPosition, drawY, 10, height, 0xFFFFFFFF);

        // Format the display text with the value
        String displayText = text + ": " + (int) value;

        context.drawText(client.textRenderer, displayText, drawX + (width - client.textRenderer.getWidth(displayText)
        ) / 2, drawY + (height - client.textRenderer.fontHeight + 1) / 2, 0xFFFFFF, false);
    }

    public void onDrag(double mouseX, int menuX) {
        // Calculate the slider position as a percentage
        float relativeX = (float) (mouseX - (menuX + x));
        relativeX = Math.max(0, Math.min(width, relativeX));
        float percentage = relativeX / width;

        // Calculate the new value based on the percentage
        float newValue = min + percentage * (max - min);

        // Apply stepping if needed
        if (step > 0) {
            newValue = Math.round(newValue / step) * step;
        }

        // Set the new value
        newValue = Math.max(min, Math.min(max, newValue));
        setValue(newValue);
    }

    public float getValue() {
        return value;
    }

    public void setValue(float value) {
        setSliderValue(action, value);
        this.value = Math.max(min, Math.min(max, value));
    }

    public boolean isMouseOver(double mouseX, double mouseY, int menuX, int menuY) {
        return mouseX >= menuX + x && mouseX <= menuX + x + width &&
                mouseY >= menuY + y && mouseY <= menuY + y + height;
    }

    public String getAction() {
        return action;
    }

    public void setDragging(boolean dragging) {
        this.isDragging = dragging;
    }

    public boolean isDragging() {
        return isDragging;
    }
}
