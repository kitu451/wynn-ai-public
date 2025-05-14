package net.natga999.wynn_ai.render;

import net.natga999.wynn_ai.boxes.BoxConfig;
import net.natga999.wynn_ai.boxes.BoxConfigRegistry;
import net.minecraft.client.render.*;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.nbt.NbtDouble;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.Vec3d;
import java.util.List;

public class BoxMarkerRenderer implements MarkerRenderer {
    @Override
    public void renderMarker(NbtCompound nbt, Camera camera, MatrixStack matrices,
                             VertexConsumerProvider vertexConsumers) {
        Vec3d position = extractPositionFromNbt(nbt);
        if (position == null) return;

        String text = nbt.contains("text") ? nbt.getString("text") : "";
        BoxConfig config = findMatchingConfig(text);

        renderMarker(position, config, config.color(), camera, matrices, vertexConsumers);
    }

    @Override
    public void renderMarker(Vec3d worldPos, int color, Camera camera,
                             MatrixStack matrices, VertexConsumerProvider vertexConsumers) {
        renderMarker(worldPos, BoxConfigRegistry.getDefaultLoadNodeConfig(), color, camera, matrices, vertexConsumers);
    }

    private void renderMarker(Vec3d worldPos, BoxConfig config, int color,
                              Camera camera, MatrixStack matrices,
                              VertexConsumerProvider vertexConsumers) {
        if (config == null) config = BoxConfigRegistry.getDefaultConfig();

        Vec3d relativePos = worldPos.subtract(camera.getPos());
        Box box = createBox(relativePos, config);
        drawBoxOutline(matrices, vertexConsumers, box, color);
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
        WorldRenderer.drawBox(
                matrices,
                lines,
                box.minX, box.minY, box.minZ,
                box.maxX, box.maxY, box.maxZ,
                (color >> 16 & 0xFF) / 255f,
                (color >> 8 & 0xFF) / 255f,
                (color & 0xFF) / 255f,
                1.0f
        );
    }

    // Existing helper
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