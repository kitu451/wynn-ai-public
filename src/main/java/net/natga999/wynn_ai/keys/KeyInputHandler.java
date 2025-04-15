package net.natga999.wynn_ai.keys;

import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.keybinding.v1.KeyBindingHelper;
import net.minecraft.client.option.KeyBinding;
import net.minecraft.client.util.InputUtil;
import net.minecraft.text.Text;
import net.natga999.wynn_ai.render.EntityOutliner;
import org.lwjgl.glfw.GLFW;
import net.natga999.wynn_ai.CustomMenuScreen;
import net.natga999.wynn_ai.TestRender;

public class KeyInputHandler {
    private static KeyBinding toggleHudKey;
    private static KeyBinding toggleMenuKey;

    private static final KeyBinding TOGGLE_OUTLINE_KEY = KeyBindingHelper.registerKeyBinding(
            new KeyBinding(
                    "key.wynn_ai.toggle_outline",
                    InputUtil.Type.KEYSYM,
                    GLFW.GLFW_KEY_O, // Use any key you prefer
                    "category.wynn_ai.keys"
            )
    );

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
                    client.setScreen(new CustomMenuScreen(Text.of("WYNN AI Menu"),toggleMenuKey));
                } else if (client.currentScreen instanceof CustomMenuScreen) {
                    client.setScreen(null);
                }
            }

            if (TOGGLE_OUTLINE_KEY.wasPressed()) {
                boolean newState = EntityOutliner.toggleOutlining();
                assert client.player != null;
                client.player.sendMessage(Text.literal("Entity outlining: " + (newState ? "ON" : "OFF")), true);
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
