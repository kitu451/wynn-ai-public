package net.natga999.wynn_ai.render;

import net.natga999.wynn_ai.managers.HarvestPathManager;

import com.mojang.blaze3d.systems.RenderSystem;
import net.minecraft.client.render.*;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.util.math.Vec3d;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.List;

public class PathRenderer {
    private static final Logger LOGGER = LoggerFactory.getLogger(PathRenderer.class);

    private static final int LINE_COLOR = 0xFFFF0000; // Red ARGB
    private static final int SPLINE_COLOR = 0xFF00FF00; // Green ARGB
    private static final float LINE_WIDTH = 2.0f;

    public static void renderSplinePath(MatrixStack matrices, Vec3d cameraPos, List<Vec3d> splinePath) {
        if (splinePath == null || splinePath.size() < 2) {
            return;
        }

        LOGGER.debug("Rendering spline path with {} points", splinePath.size());

        RenderSystem.enableBlend();
        RenderSystem.defaultBlendFunc();
        RenderSystem.disableCull();
        RenderSystem.disableDepthTest();
        RenderSystem.setShader(GameRenderer::getPositionColorProgram);
        RenderSystem.lineWidth(LINE_WIDTH);

        // Get vertex consumers from the buffer builders
        VertexConsumerProvider.Immediate vertexConsumerProvider =
                net.minecraft.client.MinecraftClient.getInstance().getBufferBuilders().getEntityVertexConsumers();
        RenderLayer layer = RenderLayer.getLines();
        VertexConsumer buffer = vertexConsumerProvider.getBuffer(layer);

        float alpha = ((SPLINE_COLOR >> 24) & 0xFF) / 255.0f;
        float red = ((SPLINE_COLOR >> 16) & 0xFF) / 255.0f;
        float green = ((SPLINE_COLOR >> 8) & 0xFF) / 255.0f;
        float blue = (SPLINE_COLOR & 0xFF) / 255.0f;

        // Create the position matrix
        MatrixStack.Entry entry = matrices.peek();

        for (int i = 0; i < splinePath.size() - 1; i++) {
            Vec3d start = splinePath.get(i).subtract(cameraPos);
            Vec3d end = splinePath.get(i + 1).subtract(cameraPos);
            Vec3d normalized = new Vec3d(end.x - start.x, end.y - start.y, end.z - start.z).normalize();

            // Draw lines using modern approach
            buffer.vertex(entry.getPositionMatrix(), (float)start.x, (float)start.y, (float)start.z)
                    .color(red, green, blue, alpha)
                    .normal(entry, (float)normalized.x, (float)normalized.y, (float)normalized.z);

            buffer.vertex(entry.getPositionMatrix(), (float)end.x, (float)end.y, (float)end.z)
                    .color(red, green, blue, alpha)
                    .normal(entry, (float)normalized.x, (float)normalized.y, (float)normalized.z);
        }

        // Draw and end
        vertexConsumerProvider.draw(layer);

        // Restore state
        RenderSystem.enableCull();
        RenderSystem.enableDepthTest();
        RenderSystem.disableBlend();
    }

    public static void renderPath(MatrixStack matrices, Vec3d cameraPos, List<Vec3d> path) {
        // Only render when in MOVING_TO_NODE state
        if (path == null || path.size() < 2) {
            return;
        }

        RenderSystem.enableBlend();
        RenderSystem.defaultBlendFunc();
        RenderSystem.disableCull();
        RenderSystem.disableDepthTest();
        RenderSystem.setShader(GameRenderer::getPositionColorProgram);
        RenderSystem.lineWidth(LINE_WIDTH);

        // Get vertex consumers from the buffer builders
        VertexConsumerProvider.Immediate vertexConsumerProvider =
                net.minecraft.client.MinecraftClient.getInstance().getBufferBuilders().getEntityVertexConsumers();
        RenderLayer layer = RenderLayer.getLines();
        VertexConsumer buffer = vertexConsumerProvider.getBuffer(layer);

        float alpha = ((LINE_COLOR >> 24) & 0xFF) / 255.0f;
        float red = ((LINE_COLOR >> 16) & 0xFF) / 255.0f;
        float green = ((LINE_COLOR >> 8) & 0xFF) / 255.0f;
        float blue = (LINE_COLOR & 0xFF) / 255.0f;

        // Create the position matrix
        MatrixStack.Entry entry = matrices.peek();

        for (int i = 0; i < path.size() - 1; i++) {
            Vec3d start = path.get(i).subtract(cameraPos);
            Vec3d end = path.get(i + 1).subtract(cameraPos);
            Vec3d normalized = new Vec3d(end.x - start.x, end.y - start.y, end.z - start.z).normalize();

            // Draw lines using modern approach
            buffer.vertex(entry.getPositionMatrix(), (float)start.x, (float)start.y, (float)start.z)
                    .color(red, green, blue, alpha)
                    .normal(entry, (float)normalized.x, (float)normalized.y, (float)normalized.z);

            buffer.vertex(entry.getPositionMatrix(), (float)end.x, (float)end.y, (float)end.z)
                    .color(red, green, blue, alpha)
                    .normal(entry, (float)normalized.x, (float)normalized.y, (float)normalized.z);
        }

        // Draw and end
        vertexConsumerProvider.draw(layer);

        // Restore state
        RenderSystem.enableCull();
        RenderSystem.enableDepthTest();
        RenderSystem.disableBlend();

        // Additionally, render the spline path if available
        List<Vec3d> splinePath = HarvestPathManager.getInstance().getSplinePath();
        if (splinePath != null && !splinePath.isEmpty()) {
            LOGGER.debug("Spline path exists with {} points", splinePath.size());
            renderSplinePath(matrices, cameraPos, splinePath);
        }
    }
}