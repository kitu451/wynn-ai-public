package net.natga999.wynn_ai.mixin;

import net.minecraft.client.MinecraftClient;
import net.minecraft.entity.Entity;
import net.natga999.wynn_ai.managers.EntityOutlinerManager;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(MinecraftClient.class)
public abstract class MixinMinecraftClient {

    @Inject(method = "hasOutline", at = @At("HEAD"), cancellable = true)
    private void outlineEntities(Entity entity, CallbackInfoReturnable<Boolean> ci) {
        if (EntityOutlinerManager.shouldOutline(entity)) {
            ci.setReturnValue(true);
        }
    }
}