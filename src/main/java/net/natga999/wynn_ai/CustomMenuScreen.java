package net.natga999.wynn_ai;

import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.widget.ButtonWidget;
import net.minecraft.client.gui.widget.CheckboxWidget;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.option.KeyBinding;
import net.minecraft.text.Text;

public class CustomMenuScreen extends Screen {

    private final KeyBinding toggleKey;

    public CustomMenuScreen(Text title, KeyBinding toggleKey) {
        super(title);
        this.toggleKey = toggleKey;
    }

    @Override
    protected void init() {
        // Center of the screen
        int centerX = this.width / 2;
        int centerY = this.height / 2;

        // Add a toggleable feature checkbox
        CheckboxWidget hudToggleCheckbox = CheckboxWidget.builder(Text.of("Show HUD"), textRenderer)
                .pos(centerX - 75, centerY - 70)
                .maxWidth(150)
                .checked(TestRender.isHudEnabled())
                .callback((widget, isChecked) -> {
                    TestRender.setHudEnabled(isChecked);
                })
                .build();

        this.addDrawableChild(hudToggleCheckbox);

        // Add a button to close the menu
        this.addDrawableChild(ButtonWidget.builder(Text.of("Close Menu"), button -> {
            this.close();
        }).dimensions(centerX - 50, centerY + 20, 100, 20).build());
    }

    @Override
    public void render(DrawContext context, int mouseX, int mouseY, float delta) {
        this.renderBackground(context, mouseX, mouseY, delta);
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
    public void close() {
        assert this.client != null;
        this.client.setScreen(null);
    }
}