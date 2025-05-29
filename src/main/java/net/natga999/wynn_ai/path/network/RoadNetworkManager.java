package net.natga999.wynn_ai.path.network;

import net.minecraft.util.math.Vec3d;

import net.fabricmc.loader.api.FabricLoader;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.reflect.TypeToken;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;
import java.lang.reflect.Type;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;
import java.util.stream.Collectors;

public class RoadNetworkManager {
    private static final Logger LOGGER = LoggerFactory.getLogger(RoadNetworkManager.class);

    private static final RoadNetworkManager INSTANCE = new RoadNetworkManager();
    private final Map<String, RoadNode> nodes = new HashMap<>();
    private final Path networkFilePath;
    private final Gson gson;
    private final LinkedList<String> recentlyAddedNodeIds = new LinkedList<>();
    private static final int MAX_RECENT_NODES_TO_TRACK = 2;
    private static final double TUNNEL_TRAVEL_COST = 1.0;

    private RoadNetworkManager() {
        this.networkFilePath = FabricLoader.getInstance()
                .getConfigDir()
                .resolve("wynn_ai")
                .resolve("road_network.json");
        this.gson = new GsonBuilder().setPrettyPrinting().disableHtmlEscaping().create();
    }

    public static RoadNetworkManager getInstance() {
        return INSTANCE;
    }

    public boolean loadNetwork() {
        nodes.clear();
        recentlyAddedNodeIds.clear();
        if (!Files.exists(networkFilePath)) {
            LOGGER.warn("Road network file not found: {}", networkFilePath);
            return true; // Or false if not found is an error for reload specifically
        }

        try (FileReader reader = new FileReader(networkFilePath.toFile())) {
            Type roadNodeListType = new TypeToken<NodesWrapper>() {}.getType();
            NodesWrapper wrapper = gson.fromJson(reader, roadNodeListType);
            if (wrapper != null && wrapper.nodes != null) {
                for (RoadNode node : wrapper.nodes) {
                    node.initializeTransientFields();
                    nodes.put(node.getId(), node);
                }
                LOGGER.info("Loaded {} road nodes from {}", nodes.size(), networkFilePath);
            } else {
                LOGGER.warn("Road network file is empty or malformed: {}", networkFilePath);
            }
            return true;
        } catch (IOException | com.google.gson.JsonSyntaxException e) {
            LOGGER.warn("Failed to load/parse road network from {}:", networkFilePath, e);
            return false;
        }
    }

    public boolean saveNetwork() {
        try {
            Files.createDirectories(networkFilePath.getParent());
            try (FileWriter writer = new FileWriter(networkFilePath.toFile())) {
                NodesWrapper wrapper = new NodesWrapper();
                wrapper.nodes = new ArrayList<>(this.nodes.values()); // Ensure all transient fields are up-to-date if needed before serialization
                gson.toJson(wrapper, writer);
                LOGGER.info("Saved {} road nodes to {}", this.nodes.size(), networkFilePath);
                return true;
            }
        } catch (IOException e) {
            LOGGER.warn("Failed to save road network to {}:", networkFilePath, e);
            return false;
        }
    }

    public boolean addNode(RoadNode node) {
        if (node == null || nodes.containsKey(node.getId())) {
            LOGGER.warn("Attempted to add null node or node with duplicate ID: {}", (node != null ? node.getId() : "null"));
            return false;
        }
        nodes.put(node.getId(), node);
        LOGGER.info("Added node: {}", node.getId());
        // Track recently added node
        addRecentNode(node.getId());
        return true;
    }

    private void addRecentNode(String nodeId) {
        // Remove if already present to move it to the front (most recent)
        recentlyAddedNodeIds.remove(nodeId);
        recentlyAddedNodeIds.addFirst(nodeId); // Add to the beginning

        // Keep the list size limited
        while (recentlyAddedNodeIds.size() > MAX_RECENT_NODES_TO_TRACK) {
            recentlyAddedNodeIds.removeLast();
        }
        LOGGER.debug("Recently added nodes: {}", recentlyAddedNodeIds);
    }

    /**
     * Gets the IDs of the two most recently added nodes in this session.
     * The first element is the most recent, the second is the one before it.
     * Returns a list with 0, 1, or 2 elements.
     */
    public LinkedList<String> getTwoMostRecentNodes() {
        // Return a copy to prevent external modification of the internal list directly
        return new LinkedList<>(recentlyAddedNodeIds);
    }

    public boolean removeNode(String nodeId) {
        if (nodeId == null || !nodes.containsKey(nodeId)) {
            LOGGER.warn("Attempted to remove non-existent node: {}", nodeId);
            return false;
        }
        nodes.remove(nodeId);
        recentlyAddedNodeIds.remove(nodeId); // Also remove from recent list
        // Also remove this node from all other nodes' connection lists
        for (RoadNode otherNode : nodes.values()) {
            otherNode.getConnections().remove(nodeId);
        }
        LOGGER.info("Removed node: {}", nodeId);
        return true;
    }

    public boolean addConnection(String nodeId1, String nodeId2) {
        RoadNode node1 = nodes.get(nodeId1);
        RoadNode node2 = nodes.get(nodeId2);

        if (node1 == null || node2 == null) {
            LOGGER.warn("Add connection: One or both nodes not found ({} -> {})", nodeId1, nodeId2);
            return false;
        }
        if (nodeId1.equals(nodeId2)) {
            LOGGER.warn("Add connection: Cannot connect a node to itself ({})", nodeId1);
            return false;
        }

        // Add connection bidirectionally
        boolean changed1 = false;
        if (!node1.getConnections().contains(nodeId2)) {
            node1.getConnections().add(nodeId2);
            changed1 = true;
        }
        boolean changed2 = false;
        if (!node2.getConnections().contains(nodeId1)) {
            node2.getConnections().add(nodeId1);
            changed2 = true;
        }

        if (changed1 || changed2) {
            LOGGER.info("Added connection between {} and {}", nodeId1, nodeId2);
            return true;
        } else {
            LOGGER.info("Connection between {} and {} already exists.", nodeId1, nodeId2);
            return false; // Or true if "already connected" is considered success
        }
    }

    public boolean removeConnection(String nodeId1, String nodeId2) {
        RoadNode node1 = nodes.get(nodeId1);
        RoadNode node2 = nodes.get(nodeId2);

        if (node1 == null || node2 == null) {
            LOGGER.warn("Remove connection: One or both nodes not found ({} -> {})", nodeId1, nodeId2);
            return false;
        }

        // Remove connection bidirectionally
        boolean removed1 = node1.getConnections().remove(nodeId2);
        boolean removed2 = node2.getConnections().remove(nodeId1);

        if (removed1 || removed2) {
            LOGGER.info("Removed connection between {} and {}", nodeId1, nodeId2);
            return true;
        } else {
            LOGGER.info("No connection found between {} and {} to remove.", nodeId1, nodeId2);
            return false;
        }
    }

    public void updateNodeType(String nodeId, String typeName) {
        RoadNode node = nodes.get(nodeId);
        if (node == null) {
            LOGGER.warn("Set node type: Node not found ({})", nodeId);
            return;
        }
        // Assuming RoadNode has a setType method
        // If typeName is null or empty, maybe set type to null.
        String newType = (typeName == null || typeName.trim().isEmpty()) ? null : typeName.trim();
        node.setType(newType); // Requires setType in RoadNode
        LOGGER.info("Node {} type set to '{}'", nodeId, newType);
    }

    public RoadNode getNodeById(String id) {
        return nodes.get(id);
    }

    public List<RoadNode> getNodesByType(String type) {
        return nodes.values().stream()
                .filter(node -> type.equalsIgnoreCase(node.getType()))
                .collect(Collectors.toList());
    }

    public Collection<RoadNode> getAllNodes() {
        // The 'nodes == null' check is indeed redundant here.
        return Collections.unmodifiableCollection(nodes.values());
    }

    public RoadNode findClosestNode(Vec3d targetPosition, String worldId, double maxDistance) {
        return nodes.values().stream()
                .filter(node -> node.getWorldId().equals(worldId) && node.getPosition() != null)
                .filter(node -> node.getPosition().distanceTo(targetPosition) <= maxDistance)
                .min(Comparator.comparingDouble(node -> node.getPosition().distanceTo(targetPosition)))
                .orElse(null);
    }

    public RoadNode findClosestNode(Vec3d targetPosition, String worldId) {
        return findClosestNode(targetPosition, worldId, Double.MAX_VALUE);
    }

    public List<RoadNode> findPathOnRoadNetwork(String startNodeId, String goalNodeId) {
        RoadNode startNode = nodes.get(startNodeId);
        RoadNode goalNode = nodes.get(goalNodeId);

        if (startNode == null || goalNode == null) {
            LOGGER.warn("A* pathfinding: Start or goal node not found ({} -> {})", startNodeId, goalNodeId);
            return null;
        }

        PriorityQueue<PathStep> openSet = new PriorityQueue<>(Comparator.comparingDouble(step -> step.fScore));
        Map<RoadNode, RoadNode> cameFrom = new HashMap<>();
        Map<RoadNode, Double> gScore = new HashMap<>(); // Cost from start to node

        nodes.values().forEach(node -> gScore.put(node, Double.POSITIVE_INFINITY));
        gScore.put(startNode, 0.0);

        openSet.add(new PathStep(startNode, heuristic(startNode, goalNode)));

        while (!openSet.isEmpty()) {
            RoadNode current = openSet.poll().node;

            if (current.equals(goalNode)) {
                return reconstructPath(cameFrom, current);
            }

            for (String neighborId : current.getConnections()) {
                RoadNode neighbor = nodes.get(neighborId);
                if (neighbor == null || neighbor.getPosition() == null) continue; // Skip invalid connections

                double tentativeGScore = gScore.get(current) + current.getPosition().distanceTo(neighbor.getPosition());

                if (tentativeGScore < gScore.getOrDefault(neighbor, Double.POSITIVE_INFINITY)) {
                    cameFrom.put(neighbor, current);
                    gScore.put(neighbor, tentativeGScore);
                    double fScore = tentativeGScore + heuristic(neighbor, goalNode);
                    // Remove if neighbor is already in openSet with higher fScore, then add new
                    openSet.removeIf(step -> step.node.equals(neighbor));
                    openSet.add(new PathStep(neighbor, fScore));
                }
            }

            // *** Explore tunnel connection if applicable ***
            if ("TUNNEL_ENTRANCE".equalsIgnoreCase(current.getType()) && current.getTargetTunnelExitNodeId() != null) {
                RoadNode tunnelExitNeighbor = nodes.get(current.getTargetTunnelExitNodeId());
                if (tunnelExitNeighbor != null && tunnelExitNeighbor.getPosition() != null) {
                    // Ensure the tunnel exit is in the same world, or handle cross-world tunnels if your system supports it
                    if (current.getWorldId().equals(tunnelExitNeighbor.getWorldId())) {
                        double tentativeGScore = gScore.get(current) + TUNNEL_TRAVEL_COST; // Special low cost for tunnel travel

                        if (tentativeGScore < gScore.getOrDefault(tunnelExitNeighbor, Double.POSITIVE_INFINITY)) {
                            cameFrom.put(tunnelExitNeighbor, current); // Traveled from current (entrance) to exit
                            gScore.put(tunnelExitNeighbor, tentativeGScore);
                            double fScore = tentativeGScore + heuristic(tunnelExitNeighbor, goalNode);
                            openSet.removeIf(step -> step.node.equals(tunnelExitNeighbor));
                            openSet.add(new PathStep(tunnelExitNeighbor, fScore));
                            LOGGER.debug("A*: Considered tunnel from {} to {} with cost {}", current.getId(), tunnelExitNeighbor.getId(), TUNNEL_TRAVEL_COST);
                        }
                    } else {
                        LOGGER.warn("A*: Tunnel from {} to {} crosses worlds ({} -> {}). Cross-world A* not yet supported here.",
                                current.getId(), tunnelExitNeighbor.getId(), current.getWorldId(), tunnelExitNeighbor.getWorldId());
                    }
                } else {
                    LOGGER.warn("A*: Tunnel entrance {} has invalid target exit ID {} or exit node has no position.", current.getId(), current.getTargetTunnelExitNodeId());
                }
            }
        }
        LOGGER.warn("A* pathfinding failed to find a path from {} to {}", startNodeId, goalNodeId);
        return null; // Path not found
    }

    private double heuristic(RoadNode a, RoadNode b) {
        if (a.getPosition() == null || b.getPosition() == null) return Double.POSITIVE_INFINITY;
        return a.getPosition().distanceTo(b.getPosition());
    }

    private List<RoadNode> reconstructPath(Map<RoadNode, RoadNode> cameFrom, RoadNode current) {
        List<RoadNode> totalPath = new ArrayList<>();
        totalPath.add(current);
        while (cameFrom.containsKey(current)) {
            current = cameFrom.get(current);
            totalPath.addFirst(current); // Add to the beginning to reverse
        }
        return totalPath;
    }

    // Helper class for A* priority queue
    private static class PathStep {
        RoadNode node;
        double fScore; // gScore + hScore

        PathStep(RoadNode node, double fScore) {
            this.node = node;
            this.fScore = fScore;
        }
    }

    // Helper class for GSON deserialization of the top-level "nodes" array
    private static class NodesWrapper {
        List<RoadNode> nodes;
    }
}