package net.natga999.wynn_ai.render;

import net.natga999.wynn_ai.path.network.RoadNetworkManager;
import net.natga999.wynn_ai.path.network.RoadNode;

import net.minecraft.client.MinecraftClient;
import net.minecraft.client.render.*;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.util.math.Vec3d;

import com.mojang.blaze3d.systems.RenderSystem;

import org.joml.Matrix4f;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Collection;
import java.util.ArrayList;
import java.util.List;

public class RoadNetworkRenderer {
    private static final Logger LOGGER = LoggerFactory.getLogger(RoadNetworkRenderer.class);

    // Colors and styles
    private static final int NODE_CONNECTION_COLOR = 0xFF0000FF; // Blue ARGB
    private static final float NODE_LINE_WIDTH = 1.5f;
    private static final int NODE_MARKER_COLOR = 0x80FFFF00;   // Yellow with 50% alpha (ARGB)
    private static final double MARKER_SIZE = 0.25;           // Half-size of the cube marker
    private static final double Y_OFFSET = 0.5;               // Common Y offset for lines and markers
    private static final int TEST_HIGHWAY_PATH_COLOR = 0xFFFFA500; // Orange ARGB
    private static final float TEST_HIGHWAY_LINE_WIDTH = 3.0f;

    // Toggles
    public static boolean renderNetwork = true;
    public static boolean renderNodeMarkers = true;
    public static boolean renderTestHighwayPath = false; // Toggle for this specific path

    private static List<Vec3d> currentTestHighwayPath = null;

    public static void render(MatrixStack matrices, Camera camera) {
        if (!renderNetwork && !renderNodeMarkers) {
            return;
        }

        RoadNetworkManager rnm = RoadNetworkManager.getInstance();
        Collection<RoadNode> allNodes = rnm.getAllNodes();

        if (allNodes == null || allNodes.isEmpty()) {
            return;
        }

        Vec3d cameraPos = camera.getPos();
        String playerWorldId = MinecraftClient.getInstance().world != null ?
                MinecraftClient.getInstance().world.getRegistryKey().getValue().toString() : null;

        MatrixStack.Entry matrixEntry = matrices.peek();
        Matrix4f positionMatrix = matrixEntry.getPositionMatrix();

        // --- Render Test Highway Path ---
        if (renderTestHighwayPath && currentTestHighwayPath != null && currentTestHighwayPath.size() >= 2) {
            // Similar rendering logic to other lines, but using TEST_HIGHWAY_PATH_COLOR
            // and currentTestHighwayPath
            RenderSystem.enableBlend();
            RenderSystem.defaultBlendFunc();
            RenderSystem.lineWidth(TEST_HIGHWAY_LINE_WIDTH);
            RenderSystem.disableCull();
            RenderSystem.disableDepthTest(); // Overlay
            RenderSystem.setShader(GameRenderer::getPositionColorProgram);

            VertexConsumerProvider.Immediate pathVertexConsumerProvider = MinecraftClient.getInstance().getBufferBuilders().getEntityVertexConsumers();
            VertexConsumer pathBuffer = pathVertexConsumerProvider.getBuffer(RenderLayer.getLines());

            float pathAlpha = ((TEST_HIGHWAY_PATH_COLOR >> 24) & 0xFF) / 255.0f;
            float pathRed   = ((TEST_HIGHWAY_PATH_COLOR >> 16) & 0xFF) / 255.0f;
            float pathGreen = ((TEST_HIGHWAY_PATH_COLOR >>  8) & 0xFF) / 255.0f;
            float pathBlue  = (TEST_HIGHWAY_PATH_COLOR & 0xFF) / 255.0f;

            for (int i = 0; i < currentTestHighwayPath.size() - 1; i++) {
                // Path points are already world coordinates with Y_OFFSET + 0.1
                Vec3d start = currentTestHighwayPath.get(i).subtract(cameraPos);
                Vec3d end = currentTestHighwayPath.get(i + 1).subtract(cameraPos);
                Vec3d lineVector = end.subtract(start);
                if (lineVector.lengthSquared() == 0) continue;
                Vec3d normalized = lineVector.normalize();

                pathBuffer.vertex(matrixEntry.getPositionMatrix(), (float)start.x, (float)start.y, (float)start.z)
                        .color(pathRed, pathGreen, pathBlue, pathAlpha)
                        .normal(matrixEntry, (float)normalized.x, (float)normalized.y, (float)normalized.z);
                pathBuffer.vertex(matrixEntry.getPositionMatrix(), (float)end.x, (float)end.y, (float)end.z)
                        .color(pathRed, pathGreen, pathBlue, pathAlpha)
                        .normal(matrixEntry, (float)normalized.x, (float)normalized.y, (float)normalized.z);
            }
            pathVertexConsumerProvider.draw(RenderLayer.getLines());
            // No need to restore all states here if other rendering parts follow
        }

        // --- Render Connections (Lines) - Using VertexConsumerProvider ---
        if (renderNetwork) {
            // Setup for lines
            RenderSystem.enableBlend(); // In case lines also have alpha
            RenderSystem.defaultBlendFunc();
            RenderSystem.lineWidth(NODE_LINE_WIDTH);
            RenderSystem.disableCull();
            RenderSystem.disableDepthTest(); // Lines as overlay
            RenderSystem.setShader(GameRenderer::getPositionColorProgram);

            VertexConsumerProvider.Immediate lineVertexConsumerProvider = MinecraftClient.getInstance().getBufferBuilders().getEntityVertexConsumers();
            VertexConsumer lineBuffer = lineVertexConsumerProvider.getBuffer(RenderLayer.getLines());

            float lineAlpha = ((NODE_CONNECTION_COLOR >> 24) & 0xFF) / 255.0f;
            float lineRed   = ((NODE_CONNECTION_COLOR >> 16) & 0xFF) / 255.0f;
            float lineGreen = ((NODE_CONNECTION_COLOR >>  8) & 0xFF) / 255.0f;
            float lineBlue  = (NODE_CONNECTION_COLOR & 0xFF) / 255.0f;

            for (RoadNode node : allNodes) {
                if (node.getPosition() == null) continue;
                if (playerWorldId != null && node.getWorldId() != null && !node.getWorldId().equals(playerWorldId)) continue;
                // ... (rest of line rendering logic as before) ...
                Vec3d nodePosWorldBase = node.getPosition();
                for (String connectedNodeId : node.getConnections()) {
                    RoadNode connectedNode = rnm.getNodeById(connectedNodeId);
                    if (connectedNode == null || connectedNode.getPosition() == null) continue;
                    if (playerWorldId != null && connectedNode.getWorldId() != null && !connectedNode.getWorldId().equals(playerWorldId)) continue;

                    if (node.getId().compareTo(connectedNodeId) < 0) {
                        Vec3d connectedNodePosWorldBase = connectedNode.getPosition();
                        Vec3d nodePosWorldRender = nodePosWorldBase.add(0, Y_OFFSET, 0);
                        Vec3d connectedNodePosWorldRender = connectedNodePosWorldBase.add(0, Y_OFFSET, 0);

                        Vec3d start = nodePosWorldRender.subtract(cameraPos);
                        Vec3d end = connectedNodePosWorldRender.subtract(cameraPos);
                        Vec3d lineVector = end.subtract(start);
                        if (lineVector.lengthSquared() == 0) continue;
                        Vec3d normalized = lineVector.normalize();

                        lineBuffer.vertex(positionMatrix, (float)start.x, (float)start.y, (float)start.z)
                                .color(lineRed, lineGreen, lineBlue, lineAlpha)
                                .normal(matrixEntry, (float)normalized.x, (float)normalized.y, (float)normalized.z);
                        lineBuffer.vertex(positionMatrix, (float)end.x, (float)end.y, (float)end.z)
                                .color(lineRed, lineGreen, lineBlue, lineAlpha)
                                .normal(matrixEntry, (float)normalized.x, (float)normalized.y, (float)normalized.z);
                    }
                }
            }
            lineVertexConsumerProvider.draw(RenderLayer.getLines()); // Draw batched lines
        }

        // --- Render Node Markers (Boxes using Tessellator) ---
        if (renderNodeMarkers) {
            // Setup for Tessellator drawing
            RenderSystem.enableBlend(); // For alpha in marker color
            RenderSystem.defaultBlendFunc(); // GL_SRC_ALPHA, GL_ONE_MINUS_SRC_ALPHA
            RenderSystem.disableCull();      // To see all faces, or enable if you want culling
            RenderSystem.enableDepthTest(); // To draw as an overlay
            RenderSystem.setShader(GameRenderer::getPositionColorProgram); // Standard shader for POSITION_COLOR
            // RenderSystem.setShaderColor(1.0F, 1.0F, 1.0F, 1.0F); // Usually not needed if colors are per-vertex

            Tessellator tessellator = Tessellator.getInstance();
            BufferBuilder bufferBuilder = tessellator.begin(VertexFormat.DrawMode.QUADS, VertexFormats.POSITION_COLOR);

            float markerAlpha = ((NODE_MARKER_COLOR >> 24) & 0xFF) / 255.0f;
            float markerRed   = ((NODE_MARKER_COLOR >> 16) & 0xFF) / 255.0f;
            float markerGreen = ((NODE_MARKER_COLOR >>  8) & 0xFF) / 255.0f;
            float markerBlue  = (NODE_MARKER_COLOR & 0xFF) / 255.0f;

            for (RoadNode node : allNodes) {
                if (node.getPosition() == null) continue;
                if (playerWorldId != null && node.getWorldId() != null && !node.getWorldId().equals(playerWorldId)) continue;

                Vec3d nodeCenterWorld = node.getPosition().add(0, Y_OFFSET, 0);
                Vec3d nodeCenterCameraRelative = nodeCenterWorld.subtract(cameraPos);

                float x1 = (float)(nodeCenterCameraRelative.x - MARKER_SIZE);
                float y1 = (float)(nodeCenterCameraRelative.y - MARKER_SIZE);
                float z1 = (float)(nodeCenterCameraRelative.z - MARKER_SIZE);
                float x2 = (float)(nodeCenterCameraRelative.x + MARKER_SIZE);
                float y2 = (float)(nodeCenterCameraRelative.y + MARKER_SIZE);
                float z2 = (float)(nodeCenterCameraRelative.z + MARKER_SIZE);

                // Use the same helper, but it now directly uses BufferBuilder
                drawSolidColorBoxQuadsWithBufferBuilder(bufferBuilder, positionMatrix, x1, y1, z1, x2, y2, z2, markerRed, markerGreen, markerBlue, markerAlpha);
            }

            // Finish building and draw
            // In modern Fabric/Minecraft, after bufferBuilder.begin, you use BuiltBuffer and tessellator.draw
            // For older versions or direct BufferBuilder usage that matches your example:
            BufferRenderer.drawWithGlobalProgram(bufferBuilder.end()); // As per your example
            // OR, more commonly for world rendering with Tessellator:
            //tessellator.draw(); // This implicitly calls bufferBuilder.end() and draws the buffer.

        }

        // Restore default RenderSystem states after ALL rendering is done for this renderer
        if (renderNetwork || renderNodeMarkers || renderTestHighwayPath) {
            RenderSystem.enableDepthTest();
            RenderSystem.enableCull();
            RenderSystem.disableBlend();
            RenderSystem.lineWidth(1.0f);
        }
    }

    /**
     * Helper method to draw the 6 faces of a box as QUADS using BufferBuilder,
     * suitable for VertexFormat.POSITION_COLOR and DrawMode.QUADS.
     * No .normal() or .next() calls, as per API.
     */
    private static void drawSolidColorBoxQuadsWithBufferBuilder(BufferBuilder buffer, Matrix4f matrix,
                                                                float x1, float y1, float z1, float x2, float y2, float z2,
                                                                float r, float g, float b, float a) {
        // Bottom face (Y-)
        buffer.vertex(matrix, x1, y1, z1).color(r, g, b, a); // Use the correct endVertex equivalent
        buffer.vertex(matrix, x2, y1, z1).color(r, g, b, a);
        buffer.vertex(matrix, x2, y1, z2).color(r, g, b, a);
        buffer.vertex(matrix, x1, y1, z2).color(r, g, b, a);

        // Top face (Y+)
        buffer.vertex(matrix, x1, y2, z2).color(r, g, b, a);
        buffer.vertex(matrix, x2, y2, z2).color(r, g, b, a);
        buffer.vertex(matrix, x2, y2, z1).color(r, g, b, a);
        buffer.vertex(matrix, x1, y2, z1).color(r, g, b, a);

        // Front face (Z-)
        buffer.vertex(matrix, x1, y1, z1).color(r, g, b, a);
        buffer.vertex(matrix, x2, y1, z1).color(r, g, b, a);
        buffer.vertex(matrix, x2, y2, z1).color(r, g, b, a);
        buffer.vertex(matrix, x1, y2, z1).color(r, g, b, a);

        // Back face (Z+)
        buffer.vertex(matrix, x2, y1, z2).color(r, g, b, a);
        buffer.vertex(matrix, x1, y1, z2).color(r, g, b, a);
        buffer.vertex(matrix, x1, y2, z2).color(r, g, b, a);
        buffer.vertex(matrix, x2, y2, z2).color(r, g, b, a);

        // Left face (X-)
        buffer.vertex(matrix, x1, y1, z2).color(r, g, b, a);
        buffer.vertex(matrix, x1, y1, z1).color(r, g, b, a);
        buffer.vertex(matrix, x1, y2, z1).color(r, g, b, a);
        buffer.vertex(matrix, x1, y2, z2).color(r, g, b, a);

        // Right face (X+)
        buffer.vertex(matrix, x2, y1, z1).color(r, g, b, a);
        buffer.vertex(matrix, x2, y1, z2).color(r, g, b, a);
        buffer.vertex(matrix, x2, y2, z2).color(r, g, b, a);
        buffer.vertex(matrix, x2, y2, z1).color(r, g, b, a);
    }

    public static void setTestHighwayPath(List<RoadNode> nodePath) {
        if (nodePath == null || nodePath.isEmpty()) {
            currentTestHighwayPath = null;
            renderTestHighwayPath = false;
            LOGGER.info("Cleared test highway path.");
        } else {
            currentTestHighwayPath = new ArrayList<>();
            for (RoadNode node : nodePath) {
                if (node.getPosition() != null) {
                    currentTestHighwayPath.add(node.getPosition().add(0, Y_OFFSET + 0.1, 0)); // Slightly above other lines
                }
            }
            renderTestHighwayPath = true;
            LOGGER.info("Set test highway path with {} nodes.", currentTestHighwayPath.size());
        }
    }

    public static void clearTestHighwayPath() {
        setTestHighwayPath(null);
    }
}