package net.natga999.wynn_ai.managers;

import net.fabricmc.loader.api.FabricLoader;

import net.minecraft.nbt.NbtCompound;
import net.minecraft.nbt.NbtDouble;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.Vec3d;
import net.minecraft.client.MinecraftClient;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.*;

import com.google.gson.*;

public class ResourceNodeManager {
    private static final Logger LOGGER = LoggerFactory.getLogger(ResourceNodeManager.class);

    // Inner data class
    public static class ResourceNode {
        public final double x, y, z;
        public final String dimension;
        public long lastHarvested;

        public ResourceNode(double x, double y, double z, String dim) {
            this.x = x;
            this.y = y;
            this.z = z;
            this.dimension = dim;
            this.lastHarvested = 0;
        }
    }

    private static final Map<String, List<ResourceNode>> keywordToNodes = new HashMap<>();

    static Path saveFile = FabricLoader.getInstance()
            .getConfigDir()
            .resolve("wynn_ai")
            .resolve("resource_nodes.json");

    private static final Set<String> VALID_RESOURCES = Set.of(
            "Wheat", "Barley", "Oat", "Malt", "Hops", "Rye", "Millet", "Decay Roots", "Rice", "Sorghum", "Hemp", "Dernic Seed"
            // Add more as needed
    );

    public static void scanAndStore(NbtCompound nbt) {
        if (!nbt.contains("text") || !nbt.contains("Pos")) return;

        String text = nbt.getString("text").replaceAll("§.", "").trim();

        // Check for level requirement and resource keyword
        if (!text.contains("Lv. Min")) return;

        // Skip text without a known resource keyword
        String matchedKeyword = VALID_RESOURCES.stream()
                .filter(text::contains)
                .findFirst()
                .orElse(null);
        if (matchedKeyword == null) return;

        Vec3d pos = extractPositionFromNbt(nbt);
        if (pos == null) return;

        String dim = getCurrentDimension();
        // Prevent duplicates (within small distance)
        List<ResourceNode> nodes = keywordToNodes.computeIfAbsent(matchedKeyword, k -> new ArrayList<>());
        if (!isNearExisting(pos, nodes, 0.5)) {
            nodes.add(new ResourceNode(pos.x, pos.y, pos.z, dim));
            saveToFile();
        }
    }

    public static void saveToFile() {
        Path saveFile = getSaveFilePath();
        try {
            Files.createDirectories(saveFile.getParent());

            JsonObject root = new JsonObject();
            for (var entry : keywordToNodes.entrySet()) {
                JsonArray arr = new JsonArray();
                for (ResourceNode n : entry.getValue()) {
                    JsonObject obj = new JsonObject();
                    obj.addProperty("x", n.x);
                    obj.addProperty("y", n.y);
                    obj.addProperty("z", n.z);
                    obj.addProperty("dimension", n.dimension);
                    arr.add(obj);
                }
                root.add(entry.getKey(), arr);
            }

            String json = new GsonBuilder().setPrettyPrinting().create().toJson(root);
            Files.writeString(saveFile, json, StandardOpenOption.CREATE, StandardOpenOption.TRUNCATE_EXISTING);
        } catch (IOException e) {
            LOGGER.error("Failed to save node data", e);
        }
    }

    public static void loadFromFile() {
        Path saveFile = getSaveFilePath();
        if (!Files.exists(saveFile)) return;

        try {
            String json = Files.readString(saveFile);
            if (json.isBlank()) return;

            JsonElement el = JsonParser.parseString(json);
            if (!el.isJsonObject()) return;

            JsonObject root = el.getAsJsonObject();
            keywordToNodes.clear();
            for (String key : root.keySet()) {
                JsonArray arr = root.getAsJsonArray(key);
                List<ResourceNode> list = new ArrayList<>();
                for (JsonElement e : arr) {
                    JsonObject o = e.getAsJsonObject();
                    double x = o.get("x").getAsDouble();
                    double y = o.get("y").getAsDouble();
                    double z = o.get("z").getAsDouble();
                    String d = o.get("dimension").getAsString();
                    list.add(new ResourceNode(x, y, z, d));
                }
                keywordToNodes.put(key, list);
            }
        } catch (IOException | IllegalStateException e) {
            LOGGER.error("Failed to load node data", e);
        }
    }

    private static Path getSaveFilePath() {
        return FabricLoader.getInstance()
                .getConfigDir()
                .resolve("wynn_ai")
                .resolve("resource_nodes.json");
    }

    public static String getCurrentDimension() {
        var world = MinecraftClient.getInstance().world;
        if (world == null) return "unknown";
        Identifier id = world.getRegistryKey().getValue();
        return id.toString();  // e.g. "minecraft:overworld"
    }

    private static boolean isNearExisting(Vec3d pos, List<ResourceNode> existing, double threshold) {
        for (ResourceNode n : existing) {
            if (n.dimension.equals(getCurrentDimension()) &&
                    new Vec3d(n.x, n.y, n.z).distanceTo(pos) < threshold) {
                return true;
            }
        }
        return false;
    }

    public static void clear() {
        keywordToNodes.clear();
    }

    // Helper methods
    private static Vec3d extractPositionFromNbt(NbtCompound nbt) {
        if (nbt.contains("Pos")) { // Check if the NBT has a "Pos" element
            List<Double> posList = nbt.getList("Pos", 6).stream()
                    .map(tag -> ((NbtDouble) tag).doubleValue())
                    .toList(); // Convert the NBTList to a list of doubles
            if (posList.size() == 3) {
                return new Vec3d(posList.get(0), posList.get(1), posList.get(2));
            }
        }
        return null; // Return null if position is not found
    }

    /**
     * Finds and returns the closest resource node position based on the given keyword.
     * @param keyword The keyword associated with the resource nodes.
     * @return The Vec3d position of the closest node, or null if no nodes exist for the given keyword.
     */
    public static ResourceNode getClosestNode(String keyword) {
        // Use the player’s current position as the “from”
        MinecraftClient client = MinecraftClient.getInstance();
        if (client.player == null) return null;
        Vec3d from = client.player.getPos();
        return getClosestNode(keyword, from);
    }

    public static ResourceNode getClosestNode(String keyword, Vec3d fromPos) {
        List<ResourceNode> nodes = getNodes(keyword);
        long now = System.currentTimeMillis();

        if (nodes.isEmpty()) return null;
        return nodes.stream()
                .filter(n -> (now - n.lastHarvested) > 60000) // 60-second cooldown
                .min(Comparator.comparingDouble(n ->
                        new Vec3d(n.x, n.y, n.z).distanceTo(fromPos)
                ))
                .orElse(null);
    }

    public static void clearNodes() {
        keywordToNodes.clear();
    }

    public static Set<String> getTrackedResources() {
        return Collections.unmodifiableSet(keywordToNodes.keySet());
    }

    public static List<ResourceNode> getNodes(String keyword) {
        return Collections.unmodifiableList(
                keywordToNodes.getOrDefault(keyword, Collections.emptyList())
        );
    }

    public static void markHarvested(ResourceNode node) {
        node.lastHarvested = System.currentTimeMillis();
    }
}