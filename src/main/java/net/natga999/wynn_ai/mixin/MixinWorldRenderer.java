package net.natga999.wynn_ai.mixin;

import net.natga999.wynn_ai.managers.EntityOutlinerManager;

import net.minecraft.client.render.OutlineVertexConsumerProvider;
import net.minecraft.client.render.VertexConsumerProvider;
import net.minecraft.client.render.WorldRenderer;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityType;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.scoreboard.AbstractTeam;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(WorldRenderer.class)
public abstract class MixinWorldRenderer {

    @Inject(method = "renderEntity", at = @At("HEAD"))
    private void renderEntity(Entity entity, double cameraX, double cameraY, double cameraZ,
                              float tickDelta, MatrixStack matrices,
                              VertexConsumerProvider vertexConsumers, CallbackInfo ci) {
        if (vertexConsumers instanceof OutlineVertexConsumerProvider outlineVertexConsumers) {

            // Check if we should outline this entity
            if (EntityOutlinerManager.shouldOutline(entity)) {
                // Get the outline color from the manager
                int color = EntityOutlinerManager.getOutlineColor(entity);

                // Extract RGBA components
                int alpha = 255; // Always fully opaque for outlines
                int red = (color >> 16) & 0xFF;
                int green = (color >> 8) & 0xFF;
                int blue = color & 0xFF;

                // Set the outline color
                outlineVertexConsumers.setColor(red, green, blue, alpha);

                // Special handling for players - preserve team colors if needed
                if (entity.getType() == EntityType.PLAYER) {
                    PlayerEntity player = (PlayerEntity) entity;
                    AbstractTeam team = player.getScoreboardTeam();
                    if (team != null && team.getColor().getColorValue() != null) {
                        int teamColor = team.getColor().getColorValue();
                        int teamAlpha = (teamColor >> 24) & 0xFF;
                        int teamRed = (teamColor >> 16) & 0xFF;
                        int teamGreen = (teamColor >> 8) & 0xFF;
                        int teamBlue = teamColor & 0xFF;
                        outlineVertexConsumers.setColor(teamRed, teamGreen, teamBlue,
                                teamAlpha > 0 ? teamAlpha : 255);
                    }
                }
            }
        }
    }
}