package net.natga999.wynn_ai.keys;

import net.natga999.wynn_ai.managers.EntityOutlinerManager;
import net.natga999.wynn_ai.managers.MenuHUDManager;
import net.natga999.wynn_ai.managers.RenderManager;

import net.minecraft.client.option.KeyBinding;
import net.minecraft.client.util.InputUtil;
import net.minecraft.entity.EntityType;
import net.minecraft.text.Text;

import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.keybinding.v1.KeyBindingHelper;
import net.natga999.wynn_ai.menus.MainMenuScreen;
import net.natga999.wynn_ai.menus.huds.MenuHUD;
import org.lwjgl.glfw.GLFW;

public class KeyInputHandler {
    private static KeyBinding toggleInteractionModeKey;
    private static KeyBinding togglePersistentMenuKey;
    private static KeyBinding toggleMenuHUDKey;
    private static KeyBinding toggleBoxesKey;
    private static KeyBinding toggleOutlineKey;
    private static KeyBinding toggleHudKey;

    public static void register() {
        toggleMenuHUDKey = KeyBindingHelper.registerKeyBinding(new KeyBinding(
                "key.wynn_ai.toggle_HUD_menu",
                InputUtil.Type.KEYSYM,
                GLFW.GLFW_KEY_KP_7,
                "category.wynn_ai.keys"
        ));

        toggleInteractionModeKey = KeyBindingHelper.registerKeyBinding(new KeyBinding(
                "key.wynn_ai.toggle_interaction_mode",
                InputUtil.Type.KEYSYM,
                GLFW.GLFW_KEY_KP_8,
                "category.wynn_ai.keys"
        ));

        togglePersistentMenuKey = KeyBindingHelper.registerKeyBinding(new KeyBinding(
                "key.wynn_ai.toggle_persistent_menu",
                InputUtil.Type.KEYSYM,
                GLFW.GLFW_KEY_KP_9,
                "category.wynn_ai.keys"
        ));

        toggleBoxesKey = KeyBindingHelper.registerKeyBinding(new KeyBinding(
                "key.wynn_ai.toggle_boxes",
                InputUtil.Type.KEYSYM,
                GLFW.GLFW_KEY_KP_4,
                "category.wynn_ai.keys"
        ));

        toggleOutlineKey = KeyBindingHelper.registerKeyBinding(new KeyBinding(
                "key.wynn_ai.toggle_outline",
                InputUtil.Type.KEYSYM,
                GLFW.GLFW_KEY_KP_5,
                "category.wynn_ai.keys"
        ));

        toggleHudKey = KeyBindingHelper.registerKeyBinding(new KeyBinding(
                "key.wynn_ai.toggle_hud",
                InputUtil.Type.KEYSYM,
                GLFW.GLFW_KEY_KP_6,
                "category.wynn_ai.keys"
        ));

        ClientTickEvents.END_CLIENT_TICK.register(client -> {
            if (toggleMenuHUDKey.wasPressed()) {
                if (!MenuHUDManager.menuExists("Main Menu")) {
                    MenuHUD menu = new MenuHUD("MainMenu");
                    if (!menu.getTitle().equals("Unknown")) {
                        MenuHUDManager.registerMenu(menu);
                    }
                }

                RenderManager.getInstance().toggleMenuHUD();
                assert client.player != null;
                client.player.sendMessage(Text.literal("Menu HUD: " + (RenderManager.isMenuHUDEnabled() ? "ON" : "OFF")), true);
            }

            if (togglePersistentMenuKey.wasPressed()) {
                RenderManager.toggleMenuVisible();
                assert client.player != null;
                if (client.currentScreen == null) {
                    client.setScreen(new MainMenuScreen(Text.of("WYNN AI Menu"),togglePersistentMenuKey, "MainMenu"));
                } else if (client.currentScreen instanceof MainMenuScreen) {
                    client.setScreen(null);
                }
                client.player.sendMessage(Text.literal("Persistent menu: " + (RenderManager.isMenuVisible() ? "ON" : "OFF")), true);
            }

            if (toggleInteractionModeKey.wasPressed()) {
                RenderManager.toggleInteractionMode();
                assert client.player != null;
                client.player.sendMessage(Text.literal("Mouse interaction: " + (RenderManager.isInteractionMode() ? "ON" : "OFF")), true);
            }

            if (toggleBoxesKey.wasPressed()) {
                RenderManager.getInstance().toggleBox();
                for (MenuHUD menu : MenuHUDManager.getMenus()) {
                    menu.toggleCheckbox("showBoxes");
                }
                assert client.player != null;
                client.player.sendMessage(Text.literal("Entity boxes: " + (RenderManager.isBoxEnabled() ? "ON" : "OFF")), true);
            }

            if (toggleOutlineKey.wasPressed()) {
                //to-do: replace logic to json
                EntityOutlinerManager.outlinedEntityTypes.put(EntityType.ZOMBIE, 0xFF0000);
                boolean newState = EntityOutlinerManager.toggleOutlining();
                for (MenuHUD menu : MenuHUDManager.getMenus()) {
                    menu.toggleCheckbox("showOutlines");
                }
                assert client.player != null;
                client.player.sendMessage(Text.literal("Entity outlining: " + (newState ? "ON" : "OFF")), true);
            }

            if (toggleHudKey.wasPressed()) {
                RenderManager.getInstance().toggleHud();
                for (MenuHUD menu : MenuHUDManager.getMenus()) {
                    menu.toggleCheckbox("showHUD");
                }
                assert client.player != null;
                client.player.sendMessage(Text.literal("HUD: " + (RenderManager.isHudEnabled() ? "ON" : "OFF")), true);
            }
        });
    }
}