package net.natga999.wynn_ai.mixin;

import net.natga999.wynn_ai.input.KeyInputHandler;
import net.natga999.wynn_ai.managers.RenderManager;
import net.natga999.wynn_ai.managers.MenuHUDManager;

import net.minecraft.client.Mouse;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import static org.lwjgl.glfw.GLFW.glfwGetCursorPos;

@Mixin(Mouse.class)
public class MouseMixin {

    @Inject(
            method = "onMouseButton",
            at = @At("HEAD"),
            cancellable = true
    )
    private void onMouseButtonWindow(long window, int button, int action, int mods, CallbackInfo ci) {
        if (!RenderManager.isInteractionMode()) return;

        // Only cancel if clicking on a menu
        double[] xPos = new double[1];
        double[] yPos = new double[1];
        glfwGetCursorPos(window, xPos, yPos);

        double scale = net.minecraft.client.MinecraftClient.getInstance().getWindow().getScaleFactor();
        double mouseX = xPos[0] / scale;
        double mouseY = yPos[0] / scale;

        // If not clicking on any menu, let Minecraft handle it
        if (!MenuHUDManager.wasClickOnMenu() && !MenuHUDManager.isClickInsideAnyMenu(mouseX, mouseY) && !KeyInputHandler.isHoldInteractionOn()) {
            KeyInputHandler.setToggleInteractionOn(false);
            RenderManager.toggleInteractionMode();
            return;
        }

        // Prevent default behavior like locking the cursor
        ci.cancel();
    }
}