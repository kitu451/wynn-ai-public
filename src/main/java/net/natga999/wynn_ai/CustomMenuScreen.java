package net.natga999.wynn_ai;

import net.natga999.wynn_ai.managers.EntityOutlinerManager;
import net.natga999.wynn_ai.managers.RenderManager;

import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.widget.ButtonWidget;
import net.minecraft.client.gui.widget.CheckboxWidget;
import net.minecraft.client.gui.widget.SliderWidget;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.option.KeyBinding;
import net.minecraft.text.Text;

public class CustomMenuScreen extends Screen {

    private final KeyBinding toggleKey;

    // Define the dimensions of the "window" inside the game screen
    private int windowWidth;
    private int windowHeight;

    public CustomMenuScreen(Text title, KeyBinding toggleKey) {
        super(title);
        this.toggleKey = toggleKey;
    }

    @Override
    protected void init() {

        this.windowWidth = (int) (this.width * 0.5); // 50% of screen width
        this.windowHeight = (int) (this.height * 0.5);

        // Calculate the window's top-left corner
        int windowX = this.width / 2 - windowWidth / 2;
        int windowY = this.height / 2 - windowHeight / 2;

        // Add a toggleable feature checkbox
        CheckboxWidget hudToggleCheckbox = CheckboxWidget.builder(Text.of("Show HUD"), textRenderer)
                .pos(windowX + windowWidth / 4 + 15, windowY + 10)
                .maxWidth(150)
                .checked(RenderManager.isHudEnabled())
                .callback((widget, isChecked) -> {
                    RenderManager.setHudEnabled(isChecked);
                })
                .build();

        this.addDrawableChild(hudToggleCheckbox);

        // Create a slider for detection radius (min 16, max 512, current value from TestRender)
        int currentRadius = TestRender.getDetectionRadius();
        double sliderValue = normalizeRadius(currentRadius);

        // Create a custom slider for detection radius
        DetectionRadiusSlider radiusSlider = new DetectionRadiusSlider(
                windowX + 10,
                windowY + 10,
                windowWidth / 4,
                17,
                Text.of("Detection Radius: " + currentRadius),
                sliderValue);

        this.addDrawableChild(radiusSlider);

        // Add box rendering toggle checkbox
        CheckboxWidget boxToggle = CheckboxWidget.builder(Text.of("Show Entity Boxes"), this.textRenderer)
                .pos(windowX + 10, windowY + 32) // Position below the HUD checkbox
                .checked(RenderManager.isBoxEnabled())
                .callback((checkbox, checked) -> RenderManager.setBoxEnabled(checked))
                .build();
        this.addDrawableChild(boxToggle);

        // Add box rendering toggle checkbox
        CheckboxWidget outlineToggle = CheckboxWidget.builder(Text.of("Show Outlines"), this.textRenderer)
                .pos(windowX + windowWidth / 4 + 15, windowY + 32) // Position below the HUD checkbox
                .checked(RenderManager.isBoxEnabled())
                .callback((checkbox, checked) -> EntityOutlinerManager.setOutliningEnabled(checked))
                .build();
        this.addDrawableChild(outlineToggle);

        // Add a button to close the menu
        this.addDrawableChild(ButtonWidget.builder(Text.of("Close Menu"), button -> {
            this.close();
        }).dimensions(windowX + windowWidth / 2 - 50, windowY + windowHeight - 30, 100, 20).build());
    }

    // Helper method to normalize radius to slider value (0.0 to 1.0)
    private double normalizeRadius(int radius) {
        // Convert radius value to slider position (0.0 to 1.0)
        return (radius - 1) / (512.0 - 1.0);
    }

    // Helper method to convert slider value to radius
    private int denormalizeRadius(double value) {
        // Convert slider position (0.0 to 1.0) to radius value
        return (int) Math.round(1 + value * (512 - 1));
    }

    // Custom slider class for detection radius
    private class DetectionRadiusSlider extends SliderWidget {

        public DetectionRadiusSlider(int x, int y, int width, int height, Text text, double value) {
            super(x, y, width, height, text, value);
        }

        @Override
        protected void updateMessage() {
            int radius = denormalizeRadius(this.value);
            this.setMessage(Text.of("Detection Radius: " + radius));
        }

        @Override
        protected void applyValue() {
            int radius = denormalizeRadius(this.value);
            TestRender.setDetectionRadius(radius);
        }
    }

    @Override
    public void render(DrawContext context, int mouseX, int mouseY, float delta) {

        // Calculate the window's position
        int windowX = this.width / 2 - windowWidth / 2;
        int windowY = this.height / 2 - windowHeight / 2;
        int borderThickness = 1;

        // Define the border color (use any color in ARGB format)
        int borderColor = 0xFF000000; // Solid black with full opacity

        // Render the border (solid color)
        // Render the left border
        context.fill(
                windowX - borderThickness,              // Left (extended by border thickness)
                windowY - borderThickness,              // Top (extended by border thickness)
                windowX, // Right (extended by border thickness)
                windowY + windowHeight + borderThickness, // Bottom (extended by border thickness)
                borderColor
        );
        // Render the bottom border
        context.fill(
                windowX - borderThickness,
                windowY + windowHeight,
                windowX + windowWidth + borderThickness,
                windowY + windowHeight + borderThickness,
                borderColor
        );
        // Render the right border
        context.fill(
                windowX + windowWidth,
                windowY - borderThickness,
                windowX + windowWidth + borderThickness,
                windowY + windowHeight + borderThickness,
                borderColor
        );

        // Render the top border
        context.fill(
                windowX - borderThickness,
                windowY - borderThickness,
                windowX + windowWidth + borderThickness,
                windowY,
                borderColor
        );

        // Render the "window" background
        context.fill(windowX, windowY, windowX + windowWidth, windowY + windowHeight, 0x80000000); // Semi-transparent black

        super.render(context, mouseX, mouseY, delta); // Render widgets
        context.drawCenteredTextWithShadow(this.textRenderer, this.title, this.width / 2, 20, 0xFFFFFF); // Title
    }

    @Override
    public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
        // Close if the bound key is pressed
        if (toggleKey.matchesKey(keyCode, scanCode)) {
            this.close();
            return true;
        }
        return super.keyPressed(keyCode, scanCode, modifiers);
    }

    @Override
    public boolean shouldPause() {
        return false; // Keep the game running while the menu is open
    }

    @Override
    public void renderBackground(DrawContext context, int mouseX, int mouseY, float delta) {
        // Do nothing to prevent Minecraft's default blurred background rendering
    }

    @Override
    public void close() {
        assert this.client != null;
        this.client.setScreen(null);
    }
}