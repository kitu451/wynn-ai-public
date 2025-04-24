package net.natga999.wynn_ai.input;

import net.natga999.wynn_ai.managers.EntityOutlinerManager;
import net.natga999.wynn_ai.managers.MenuHUDManager;
import net.natga999.wynn_ai.managers.PathingManager;
import net.natga999.wynn_ai.managers.RenderManager;
import net.natga999.wynn_ai.menus.MenuHUD;

import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.keybinding.v1.KeyBindingHelper;

import net.minecraft.client.option.KeyBinding;
import net.minecraft.client.util.InputUtil;
import net.minecraft.entity.EntityType;
import net.minecraft.text.Text;

import org.lwjgl.glfw.GLFW;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import static net.natga999.wynn_ai.menus.MenuHUDLoader.setCheckboxState;

public class KeyInputHandler {
    private static final Logger LOGGER = LoggerFactory.getLogger(KeyInputHandler.class);

    private static boolean toggleInteractionOn = false;
    private static boolean holdInteractionOn = false;

    private static KeyBinding toggleMenuHUDKey;
    private static KeyBinding toggleInteractionModeKey;
    private static KeyBinding holdInteractionModeKey;
    private static KeyBinding toggleBoxesKey;
    private static KeyBinding toggleOutlineKey;
    private static KeyBinding toggleHudKey;
    private static KeyBinding togglePathKey;

    public static void setToggleInteractionOn(boolean toggleInteractionOn) {
        KeyInputHandler.toggleInteractionOn = toggleInteractionOn;
    }

    public static boolean isHoldInteractionOn() {
        return holdInteractionOn;
    }

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

        holdInteractionModeKey = KeyBindingHelper.registerKeyBinding(new KeyBinding(
                "key.wynn_ai.hold_interaction_mode",
                InputUtil.Type.KEYSYM,
                GLFW.GLFW_KEY_GRAVE_ACCENT,
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

        togglePathKey = KeyBindingHelper.registerKeyBinding(new KeyBinding(
                "key.wynn_ai.toggle_path",
                InputUtil.Type.KEYSYM,
                GLFW.GLFW_KEY_KP_0,
                "category.wynn_ai.keys"
        ));

        ClientTickEvents.END_CLIENT_TICK.register(client -> {
            if (toggleMenuHUDKey.wasPressed()) {
                if (MenuHUDManager.hasNoMenus()) {
                    if (RenderManager.isMenuHUDEnabled()) {
                        RenderManager.getInstance().toggleMenuHUD();
                    }
                    MenuHUD menu = new MenuHUD("MainMenu");
                    if (!menu.getTitle().equals("Unknown")) {
                        MenuHUD newMenu = MenuHUD.createNewInstance("MainMenu"); // base name
                        LOGGER.debug("[MenuHUDManager] Default menu created: MainMenu{}", newMenu.getMenuId());
                        MenuHUDManager.registerMenu(newMenu);
                    }
                }

                RenderManager.getInstance().toggleMenuHUD();
                assert client.player != null;
                client.player.sendMessage(Text.literal("Menu HUD: " + (RenderManager.isMenuHUDEnabled() ? "ON" : "OFF")), true);
            }

            // 1) Flip the persistent toggle when its key is pressed:
            if (toggleInteractionModeKey.wasPressed()) {
                toggleInteractionOn = !toggleInteractionOn;
                LOGGER.debug("Mouse interaction (toggled)");
                assert client.player != null;
                client.player.sendMessage(
                        Text.literal("Mouse interaction (toggled): " + (toggleInteractionOn ? "ON" : "OFF")),
                        true
                );
            }

            // 2) Compute the actual interaction mode:
            holdInteractionOn = holdInteractionModeKey.isPressed();
            boolean actualInteraction = toggleInteractionOn || holdInteractionOn;

            // 3) If it changed since last tick, update RenderManager & notify
            if (actualInteraction != RenderManager.isInteractionMode()) {
                RenderManager.setInteractionMode(actualInteraction);
                if (!holdInteractionOn) { // only notify on toggle or hold-release
                    LOGGER.debug("Mouse interaction");
                    assert client.player != null;
                    client.player.sendMessage(
                            Text.literal("Mouse interaction: " + (actualInteraction ? "ON" : "OFF")),
                            true
                    );
                }
            }

//            if (toggleInteractionModeKey.isPressed()) {
//                if (!RenderManager.isInteractionMode()) {
//                    RenderManager.setInteractionMode(true);
//                    LOGGER.debug("Interaction mode enabled");
//                    assert client.player != null;
//                    client.player.sendMessage(Text.literal("Mouse interaction: " + (RenderManager.isInteractionMode() ? "ON" : "OFF")), true);
//                }
//            } else {
//                if (RenderManager.isInteractionMode()) {
//                    RenderManager.setInteractionMode(false);
//                    LOGGER.debug("Interaction mode disabled");
//                    assert client.player != null;
//                    client.player.sendMessage(Text.literal("Mouse interaction: " + (RenderManager.isInteractionMode() ? "ON" : "OFF")), true);
//                }
//            }

            if (toggleBoxesKey.wasPressed()) {
                RenderManager.getInstance().toggleBox();
                boolean newState = RenderManager.isBoxEnabled();

                setCheckboxState("showBoxes", newState);

                for (MenuHUD menu : MenuHUDManager.getMenus()) {
                    menu.toggleCheckbox("showBoxes");
                }
                LOGGER.debug("Entity boxes: {}", newState ? "ON" : "OFF");
                assert client.player != null;
                client.player.sendMessage(Text.literal("Entity boxes: " + (newState ? "ON" : "OFF")), true);
            }

            if (toggleOutlineKey.wasPressed()) {
                //to-do: replace logic to json
                EntityOutlinerManager.outlinedEntityTypes.put(EntityType.ZOMBIE, 0xFF0000);
                EntityOutlinerManager.toggleOutlining();
                boolean newState = EntityOutlinerManager.isOutliningEnabled();

                setCheckboxState("showOutlines", newState);

                for (MenuHUD menu : MenuHUDManager.getMenus()) {
                    menu.toggleCheckbox("showOutlines");
                }
                LOGGER.debug("Entity outlining: {}", newState ? "ON" : "OFF");
                assert client.player != null;
                client.player.sendMessage(Text.literal("Entity outlining: " + (newState ? "ON" : "OFF")), true);
            }

            if (toggleHudKey.wasPressed()) {
                RenderManager.getInstance().toggleHud();
                boolean newState = RenderManager.isHudEnabled();

                setCheckboxState("showHUD", newState);

                for (MenuHUD menu : MenuHUDManager.getMenus()) {
                    menu.toggleCheckbox("showHUD");
                }
                LOGGER.debug("HUD: {}", newState ? "ON" : "OFF");
                assert client.player != null;
                client.player.sendMessage(Text.literal("HUD: " + (newState ? "ON" : "OFF")), true);
            }

            if (togglePathKey.wasPressed()) {
                PathingManager.getInstance().togglePathing();
            }
        });
    }
}