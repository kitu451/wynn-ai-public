package net.natga999.wynn_ai.keys;

import net.natga999.wynn_ai.managers.EntityOutlinerManager;
import net.natga999.wynn_ai.managers.RenderManager;
import net.natga999.wynn_ai.CustomMenuScreen;

import net.minecraft.client.option.KeyBinding;
import net.minecraft.client.util.InputUtil;
import net.minecraft.entity.EntityType;
import net.minecraft.text.Text;

import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.keybinding.v1.KeyBindingHelper;
import org.lwjgl.glfw.GLFW;

public class KeyInputHandler {
    private static KeyBinding toggleMenuKey;
    private static KeyBinding toggleBoxesKey;
    private static KeyBinding toggleOutlineKey;
    private static KeyBinding toggleHudKey;

    public static void register() {
        toggleMenuKey = KeyBindingHelper.registerKeyBinding(new KeyBinding(
                "key.wynn_ai.togglemenu",
                InputUtil.Type.KEYSYM,
                GLFW.GLFW_KEY_KP_8,
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
            if (toggleMenuKey.wasPressed()) {
                if (client.currentScreen == null) {
                    client.setScreen(new CustomMenuScreen(Text.of("WYNN AI Menu"),toggleMenuKey));
                } else if (client.currentScreen instanceof CustomMenuScreen) {
                    client.setScreen(null);
                }
            }

            if (toggleBoxesKey.wasPressed()) {
                RenderManager.getInstance().toggleBox();
                assert client.player != null;
                client.player.sendMessage(Text.literal("Entity boxes: " + (RenderManager.isBoxEnabled() ? "ON" : "OFF")), true);
            }

            if (toggleOutlineKey.wasPressed()) {
                EntityOutlinerManager.outlinedEntityTypes.put(EntityType.ZOMBIE, 0xFF0000);
                boolean newState = EntityOutlinerManager.toggleOutlining();
                assert client.player != null;
                client.player.sendMessage(Text.literal("Entity outlining: " + (newState ? "ON" : "OFF")), true);
            }

            if (toggleHudKey.wasPressed()) {
                RenderManager.getInstance().toggleHud();
                assert client.player != null;
                client.player.sendMessage(Text.literal("HUD: " + (RenderManager.isHudEnabled() ? "ON" : "OFF")), true);
            }
        });
    }

    public static boolean isHudToggleKey(int keyCode) {
        return toggleHudKey.matchesKey(keyCode, 0);
    }

    public static boolean isMenuToggleKey(int keyCode) {
        return toggleMenuKey.matchesKey(keyCode, 0);
    }
}
