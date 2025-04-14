package net.natga999.wynn_ai.keys;

import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.keybinding.v1.KeyBindingHelper;
import net.minecraft.client.option.KeyBinding;
import net.minecraft.client.util.InputUtil;
import net.minecraft.text.Text;
import org.lwjgl.glfw.GLFW;
import net.natga999.wynn_ai.CustomMenuScreen;
import net.natga999.wynn_ai.TestRender;

public class KeyInputHandler {
    private static KeyBinding toggleHudKey;
    private static KeyBinding toggleMenuKey;

    public static void register() {
        toggleHudKey = KeyBindingHelper.registerKeyBinding(new KeyBinding(
                "key.wynn_ai.toggle_hud",
                InputUtil.Type.KEYSYM,
                GLFW.GLFW_KEY_H,
                "category.wynn_ai"
        ));

        toggleMenuKey = KeyBindingHelper.registerKeyBinding(new KeyBinding(
                "key.custommenu.togglemenu",
                InputUtil.Type.KEYSYM,
                GLFW.GLFW_KEY_M,
                "category.custommenu"
        ));

        ClientTickEvents.END_CLIENT_TICK.register(client -> {
            if (toggleHudKey.wasPressed()) {
                TestRender.setHudEnabled(!TestRender.isHudEnabled());
            }

            if (toggleMenuKey.wasPressed()) {
                if (client.currentScreen == null) {
                    client.setScreen(new CustomMenuScreen(Text.of("Custom Settings Menu"),toggleMenuKey));
                } else if (client.currentScreen instanceof CustomMenuScreen) {
                    client.setScreen(null);
                }
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
