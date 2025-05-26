package net.natga999.wynn_ai.render;

import net.natga999.wynn_ai.path.network.RoadNetworkManager;
import net.natga999.wynn_ai.path.network.RoadNode;

import net.minecraft.client.MinecraftClient;
import net.minecraft.client.render.*;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.util.math.Vec3d;

import com.mojang.blaze3d.systems.RenderSystem;

import java.util.Collection;

public class RoadNetworkRenderer {
    private static final int NODE_CONNECTION_COLOR = 0xFF0000FF; // Blue ARGB for network lines
    private static final float NODE_LINE_WIDTH = 1.5f;
    private static final double Y_OFFSET = 0.5; // Offset to render lines higher

    // Configuration option
    public static boolean renderNetwork = true;

    public static void render(MatrixStack matrices, Camera camera) {
        if (!renderNetwork) {
            return;
        }

        RoadNetworkManager rnm = RoadNetworkManager.getInstance();
        Collection<RoadNode> allNodes = rnm.getAllNodes();

        if (allNodes == null || allNodes.isEmpty()) {
            return;
        }

        RenderSystem.enableBlend();
        RenderSystem.defaultBlendFunc();
        RenderSystem.disableCull();
        RenderSystem.disableDepthTest(); // Or enable for occlusion
        RenderSystem.setShader(GameRenderer::getPositionColorProgram);
        RenderSystem.lineWidth(NODE_LINE_WIDTH);

        VertexConsumerProvider.Immediate vertexConsumerProvider =
                MinecraftClient.getInstance().getBufferBuilders().getEntityVertexConsumers();
        RenderLayer layer = RenderLayer.getLines();
        VertexConsumer buffer = vertexConsumerProvider.getBuffer(layer);

        float alpha = ((NODE_CONNECTION_COLOR >> 24) & 0xFF) / 255.0f;
        float red   = ((NODE_CONNECTION_COLOR >> 16) & 0xFF) / 255.0f;
        float green = ((NODE_CONNECTION_COLOR >>  8) & 0xFF) / 255.0f;
        float blue  = (NODE_CONNECTION_COLOR & 0xFF) / 255.0f;

        MatrixStack.Entry entry = matrices.peek();
        Vec3d cameraPos = camera.getPos();
        String playerWorldId = MinecraftClient.getInstance().world != null ?
                MinecraftClient.getInstance().world.getRegistryKey().getValue().toString() : null;

        for (RoadNode node : allNodes) {
            if (node.getPosition() == null) continue;

            if (playerWorldId != null && node.getWorldId() != null && !node.getWorldId().equals(playerWorldId)) {
                continue;
            }

            Vec3d nodePosWorldBase = node.getPosition(); // Original node position

            for (String connectedNodeId : node.getConnections()) {
                RoadNode connectedNode = rnm.getNodeById(connectedNodeId);
                if (connectedNode == null || connectedNode.getPosition() == null) continue;

                if (playerWorldId != null && connectedNode.getWorldId() != null && !connectedNode.getWorldId().equals(playerWorldId)) {
                    continue;
                }

                if (node.getId().compareTo(connectedNodeId) < 0) {
                    Vec3d connectedNodePosWorldBase = connectedNode.getPosition(); // Original connected node position

                    // Apply Y-offset for rendering
                    Vec3d nodePosWorldRender = nodePosWorldBase.add(0, Y_OFFSET, 0);
                    Vec3d connectedNodePosWorldRender = connectedNodePosWorldBase.add(0, Y_OFFSET, 0);

                    // Adjust positions relative to camera
                    Vec3d start = nodePosWorldRender.subtract(cameraPos);
                    Vec3d end = connectedNodePosWorldRender.subtract(cameraPos);

                    Vec3d lineVector = end.subtract(start);
                    if (lineVector.lengthSquared() == 0) continue;
                    Vec3d normalized = lineVector.normalize();

                    // Draw lines using your established API (no .next())
                    buffer.vertex(entry.getPositionMatrix(), (float)start.x, (float)start.y, (float)start.z)
                            .color(red, green, blue, alpha)
                            // For normal, pass the NormalMatrix from the entry
                            .normal(entry, (float)normalized.x, (float)normalized.y, (float)normalized.z);

                    buffer.vertex(entry.getPositionMatrix(), (float)end.x, (float)end.y, (float)end.z)
                            .color(red, green, blue, alpha)
                            .normal(entry, (float)normalized.x, (float)normalized.y, (float)normalized.z);
                }
            }
        }

        vertexConsumerProvider.draw(layer);

        RenderSystem.enableCull();
        RenderSystem.enableDepthTest();
        RenderSystem.disableBlend();
        RenderSystem.lineWidth(1.0f);
    }
}