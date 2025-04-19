package net.natga999.wynn_ai.input;

import net.natga999.wynn_ai.managers.RenderManager;
import net.natga999.wynn_ai.menus.huds.MenuHUD;

import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.minecraft.client.MinecraftClient;

public class MouseInputHandler {

    private final MinecraftClient client = MinecraftClient.getInstance();
    private final String currentMenu = "MainMenu"; // Replace dynamically if needed
    private final MenuHUD menuHUD = new MenuHUD(currentMenu);

    public MouseInputHandler() {
        registerMouseEvent();
    }

    private void registerMouseEvent() {
        // Check mouse interactions during each tick
        ClientTickEvents.END_CLIENT_TICK.register(client -> {
            if (client.mouse.wasLeftButtonClicked()) {
                handleMouseClick();
            }
        });
    }

    private void handleMouseClick() {
        if (!RenderManager.isMenuHUDEnabled()) return;

        double mouseX = client.mouse.getX() / client.getWindow().getScaleFactor();
        double mouseY = client.mouse.getY() / client.getWindow().getScaleFactor();

        // Call the MenuHUD's onMouseClick method
        menuHUD.onMouseClick(mouseX, mouseY);
    }
}