package net.natga999.wynn_ai.render;

import net.minecraft.client.render.Camera;
import net.minecraft.client.render.VertexConsumerProvider;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.nbt.NbtCompound;

public interface MarkerRenderer {
    void renderMarker(NbtCompound nbt, Camera camera, MatrixStack matrices, VertexConsumerProvider vertexConsumers);
}
