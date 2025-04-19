package net.natga999.wynn_ai.input;

import net.natga999.wynn_ai.managers.RenderManager;
import net.natga999.wynn_ai.menus.huds.MenuHUD;

import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import static org.lwjgl.glfw.GLFW.*;

public class MouseInputHandler {
    private static final Logger LOGGER = LoggerFactory.getLogger(MouseInputHandler.class);

    private final String currentMenu = "MainMenu"; // Replace dynamically if needed
    private final MenuHUD menuHUD = new MenuHUD(currentMenu);
    private boolean wasMousePressedLastTick = false;

    public MouseInputHandler() {
        registerMouseEvent();
    }

    private void registerMouseEvent() {
        ClientTickEvents.END_CLIENT_TICK.register(client -> {
            if (!RenderManager.isMenuHUDEnabled()) return;

            long windowHandle = client.getWindow().getHandle();

            // Get raw mouse position
            double[] xPos = new double[1];
            double[] yPos = new double[1];
            glfwGetCursorPos(windowHandle, xPos, yPos);

            double scale = client.getWindow().getScaleFactor();
            double mouseX = xPos[0] / scale;
            double mouseY = yPos[0] / scale;

            // Check if left mouse button is pressed
            boolean leftPressed = glfwGetMouseButton(windowHandle, GLFW_MOUSE_BUTTON_LEFT) == GLFW_PRESS;

            if (leftPressed && !wasMousePressedLastTick) {
                LOGGER.info("Mouse clicked at: x={} y={}", mouseX, mouseY);
                menuHUD.onMouseClick(mouseX, mouseY);
            }

            wasMousePressedLastTick = leftPressed;
        });
    }
}