package net.natga999.wynn_ai.managers;

import net.minecraft.nbt.NbtCompound;
import net.minecraft.nbt.NbtDouble;
import net.minecraft.util.math.Vec3d;
import net.minecraft.client.MinecraftClient;

import java.util.*;

public class ResourceNodeManager {
    private static final Map<String, List<Vec3d>> keywordToNodes = new HashMap<>();

    public static void scanAndStore(NbtCompound nbt, String keyword) {
        if (!nbt.contains("text")) return;

        String text = nbt.getString("text");
        if (text.contains(keyword) && text.contains("Lv. Min:")) {
            Vec3d pos = extractPositionFromNbt(nbt);
            if (pos != null) {
                keywordToNodes.computeIfAbsent(keyword, k -> new ArrayList<>()).add(pos);
            }
        }
    }

    public static List<Vec3d> getNodes(String keyword) {
        return keywordToNodes.getOrDefault(keyword, Collections.emptyList());
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
    public static Vec3d getClosestNode(String keyword) {
        // Use the player’s current position as the “from”
        MinecraftClient client = MinecraftClient.getInstance();
        if (client.player == null) return null;
        Vec3d from = client.player.getPos();
        return getClosestNode(keyword, from);
    }

    // existing two-arg version:
    public static Vec3d getClosestNode(String keyword, Vec3d fromPos) {
        List<Vec3d> nodes = getNodes(keyword);
        if (nodes.isEmpty()) return null;
        return nodes.stream()
                .min(Comparator.comparingDouble(n -> n.distanceTo(fromPos)))
                .orElse(null);
    }

    public static void clearNodes() {
        keywordToNodes.clear();
    }
}