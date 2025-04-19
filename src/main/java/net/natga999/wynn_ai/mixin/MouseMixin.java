package net.natga999.wynn_ai.mixin;

import net.minecraft.client.Mouse;
import net.natga999.wynn_ai.managers.RenderManager;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(Mouse.class)
public class MouseMixin {
    @Inject(
            method = "onMouseButton",
            at = @At("HEAD"),
            cancellable = true
    )
    private void onMouseButtonWindow(long window, int button, int action, int mods, CallbackInfo ci) {
        if (RenderManager.isInteractionMode()) {
            // Prevent default behavior like locking the cursor
            ci.cancel();
        }
    }
}
