package net.natga999.wynn_ai.render;

import net.natga999.wynn_ai.boxes.BoxConfig;
import net.natga999.wynn_ai.boxes.BoxConfigRegistry;

import net.minecraft.client.render.*;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.nbt.NbtDouble;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.Vec3d;
import net.minecraft.util.shape.VoxelShapes;

import java.util.List;

public class BoxMarkerRenderer implements MarkerRenderer {
    @Override
    public void renderMarker(NbtCompound nbt, Camera camera, MatrixStack matrices,
                             VertexConsumerProvider vertexConsumers) {
        Vec3d position = extractPositionFromNbt(nbt);
        if (position == null) return;

        String text = nbt.contains("text") ? nbt.getString("text") : "";
        BoxConfig config = findMatchingConfig(text);

        renderMarker(position, config, camera, matrices, vertexConsumers);
    }

    @Override
    public void renderMarker(Vec3d worldPos, Camera camera,
                             MatrixStack matrices, VertexConsumerProvider vertexConsumers) {
        renderMarker(worldPos, BoxConfigRegistry.getDefaultLoadNodeConfig(), camera, matrices, vertexConsumers);
    }

    private void renderMarker(Vec3d worldPos, BoxConfig config,
                              Camera camera, MatrixStack matrices,
                              VertexConsumerProvider vertexConsumers) {
        if (config == null) config = BoxConfigRegistry.getDefaultConfig();

        Vec3d relativePos = worldPos.subtract(camera.getPos());
        Box box = createBox(relativePos, config);
        drawBoxOutline(matrices, vertexConsumers, box, config.color());
    }

    private BoxConfig findMatchingConfig(String text) {
        return BoxConfigRegistry.getRegisteredKeywords().stream()
                .filter(text::contains)
                .findFirst()
                .map(BoxConfigRegistry::getConfig)
                .orElse(BoxConfigRegistry.getDefaultConfig());
    }

    private Box createBox(Vec3d relativePos, BoxConfig config) {
        return new Box(
                relativePos.x - config.sizeXZ(),
                relativePos.y + config.minY(),
                relativePos.z - config.sizeXZ(),
                relativePos.x + config.sizeXZ(),
                relativePos.y + config.maxY(),
                relativePos.z + config.sizeXZ()
        );
    }

    private void drawBoxOutline(MatrixStack matrices, VertexConsumerProvider consumers,
                                Box box, int color) {
        VertexConsumer lines = consumers.getBuffer(RenderLayer.getLines());
        VertexRendering.drawOutline(matrices, lines, VoxelShapes.cuboid(box), 0, 0, 0, color);
    }

    private Vec3d extractPositionFromNbt(NbtCompound nbt) {
        if (nbt.contains("Pos")) {
            List<Double> posList = nbt.getList("Pos", 6).stream()
                    .map(tag -> ((NbtDouble) tag).doubleValue())
                    .toList();
            return posList.size() == 3 ?
                    new Vec3d(posList.get(0), posList.get(1), posList.get(2)) :
                    null;
        }
        return null;
    }
}