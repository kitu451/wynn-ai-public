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
import java.util.stream.Collectors;

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

    //todo add handle Dernic ore and wood
    private static final LinkedHashSet<String> VALID_RESOURCES = new LinkedHashSet<>(Arrays.asList(
            "Wheat", "Barley", "Oat", "Malt", "Hops", "Rye", "Millet", "Decay Roots", "Rice", "Sorghum", "Hemp", "Dernic Seed", "Red Mushroom", "Brown Mushroom", "Voidgloom", //15
            "Copper", "Granite", "Gold", "Sandstone", "Iron", "Silver", "Cobalt", "Kanderstone", "Diamond", "Molten", "Voidstone", "Dernic Ore", "Foul Larbonic Shell", //13
            "Oak", "Birch", "Willow", "Acacia", "Spruce", "Jungle", "Dark", "Light", "Pine", "Avo", "Sky", "Dernic Wood", "Bamboo", "Flerisi Tree", //14
            "Gudgeon", "Trout", "Salmon", "Carp", "Icefish", "Piranha", "Koi", "Gylia Fish", "Bass", "Molten Eel", "Starfish", "Dernic Fish", "Abyssal Matter" //13
    ));

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

            JsonObject root = getJsonObject();

            String json = new GsonBuilder().setPrettyPrinting().create().toJson(root);
            Files.writeString(saveFile, json, StandardOpenOption.CREATE, StandardOpenOption.TRUNCATE_EXISTING);
        } catch (IOException e) {
            LOGGER.warn("Failed to save node data", e);
        }
    }

    private static JsonObject getJsonObject() {
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
        return root;
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
            LOGGER.warn("Failed to load node data", e);
        }

        // Preserve valid resources even if empty in JSON
        VALID_RESOURCES.forEach(resource ->
                keywordToNodes.putIfAbsent(resource, new ArrayList<>())
        );
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
                .filter(n -> (now - n.lastHarvested) > 58000) // 58-second cooldown
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

    public static Set<String> getRegisteredResources() {
        // Combine tracked resources with valid configs
        Set<String> resources = new HashSet<>(keywordToNodes.keySet());
        resources.addAll(VALID_RESOURCES);
        return Collections.unmodifiableSet(resources);
    }

    public static List<String> getAvailableResources() {
        // Returns resources that have both config and nodes
        return keywordToNodes.keySet().stream()
                .filter(VALID_RESOURCES::contains)
                .sorted()
                .collect(Collectors.toList());
    }

    public static void markHarvested(ResourceNode node) {
        node.lastHarvested = System.currentTimeMillis();
    }

    public static List<String> getMenuResources() {
        return VALID_RESOURCES.stream()
                .sorted(String.CASE_INSENSITIVE_ORDER)
                .collect(Collectors.toList());
    }

    // Modified validation check
    public static boolean isValidResource(String text) {
        return VALID_RESOURCES.stream()
                .anyMatch(text::contains);
    }

    public static boolean hasResourceConfig(String resource) {
        return VALID_RESOURCES.contains(resource) || keywordToNodes.containsKey(resource);
    }

    public static List<String> getSortedResources() {
        return new ArrayList<>(VALID_RESOURCES);
    }

    public static void registerResource(String resource) {
        if (!VALID_RESOURCES.contains(resource)) {
            VALID_RESOURCES.add(resource);
            // Optionally create empty node list if needed
            keywordToNodes.putIfAbsent(resource, new ArrayList<>());
        }
    }
}