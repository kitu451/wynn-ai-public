package net.natga999.wynn_ai.render;

import net.minecraft.client.render.Camera;
import net.minecraft.client.render.VertexConsumerProvider;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.util.math.Vec3d;

public interface MarkerRenderer {
    // NBT-based rendering
    void renderMarker(NbtCompound nbt, Camera camera, MatrixStack matrices, VertexConsumerProvider vertexConsumers);

    // position-based rendering
    void renderMarker(Vec3d position, Camera camera, MatrixStack matrices, VertexConsumerProvider vertexConsumers);
}