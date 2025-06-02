package net.natga999.wynn_ai.commands;

import net.natga999.wynn_ai.path.network.RoadNetworkManager;
import net.natga999.wynn_ai.path.network.RoadNode;
import net.natga999.wynn_ai.ai.BasicPathAI;
import net.natga999.wynn_ai.render.RoadNetworkRenderer;

import net.minecraft.client.network.ClientPlayerEntity;
import net.minecraft.text.Text;
import net.minecraft.util.math.Vec3d;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.Formatting;

import net.fabricmc.fabric.api.client.command.v2.FabricClientCommandSource;

import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import com.mojang.brigadier.arguments.DoubleArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.suggestion.SuggestionsBuilder;
import com.mojang.brigadier.suggestion.Suggestions;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.stream.Collectors;

public class RoadNodeCommands {
    private static final Logger LOGGER = LoggerFactory.getLogger(RoadNodeCommands.class);

    private static String selectedNodeId1 = null;
    private static String selectedNodeId2 = null;

    private static void sendMessage(FabricClientCommandSource source, String message) {
        source.sendFeedback(Text.literal(message));
    }

    // A more robust way to send colored messages piece by piece:
    private static void sendFormattedText(FabricClientCommandSource source, Text text) {
        source.sendFeedback(text);
    }

    private static ClientPlayerEntity getPlayer(FabricClientCommandSource source) throws CommandSyntaxException {
        ClientPlayerEntity player = source.getPlayer(); // MinecraftClient.getInstance().player could also be used directly
        if (player == null) {
            // This should ideally not happen for client commands executed by the player.
            throw new CommandSyntaxException(null, Text.literal("Player not found to execute command."));
        }
        return player;
    }

    private static Vec3d getPlayerPos(FabricClientCommandSource source) throws CommandSyntaxException {
        return getPlayer(source).getPos();
    }

    // --- Command Handler Methods ---

    // Original handleAddNode (implicitly snapToCenter = false, no ID)
    public static int handleAddNode(CommandContext<FabricClientCommandSource> ctx) throws CommandSyntaxException {
        return handleAddNode(ctx, false); // Call the new version with snapToCenter = false
    }

    // Main handler for player-position based node addition (with optional snap and ID)
    public static int handleAddNode(CommandContext<FabricClientCommandSource> ctx, boolean snapToCenter) throws CommandSyntaxException {
        String customId = null;
        try {
            customId = StringArgumentType.getString(ctx, "id");
        } catch (IllegalArgumentException e) { /* ID not provided for this command path */ }

        ClientPlayerEntity player = getPlayer(ctx.getSource());
        Vec3d position;
        String feedbackSuffix = "";

        if (snapToCenter) {
            BlockPos playerBlockPos = player.getBlockPos();
            position = new Vec3d(playerBlockPos.getX() + 0.5, playerBlockPos.getY(), playerBlockPos.getZ() + 0.5);
            feedbackSuffix = " (Position was snapped to block center)";
        } else {
            position = player.getPos();
        }

        String worldId = player.clientWorld.getRegistryKey().getValue().toString();
        return createAndAddNode(ctx, customId, position, worldId, feedbackSuffix);
    }

    public static int handleAddNodeWithCoordinates(CommandContext<FabricClientCommandSource> ctx) throws CommandSyntaxException {
        String nodeId = StringArgumentType.getString(ctx, "id"); // ID is mandatory for this path
        double x = DoubleArgumentType.getDouble(ctx, "x");
        double y = DoubleArgumentType.getDouble(ctx, "y");
        double z = DoubleArgumentType.getDouble(ctx, "z");

        Vec3d position = new Vec3d(x, y, z);
        ClientPlayerEntity player = getPlayer(ctx.getSource()); // Still need player for worldId
        String worldId = player.clientWorld.getRegistryKey().getValue().toString();
        String feedbackSuffix = " (Position specified by coordinates)";

        return createAndAddNode(ctx, nodeId, position, worldId, feedbackSuffix);
    }

    // Helper method for common node creation and addition logic
    private static int createAndAddNode(CommandContext<FabricClientCommandSource> ctx,
                                        String proposedId, Vec3d position, String worldId,
                                        String additionalFeedback) {
        RoadNetworkManager rnm = RoadNetworkManager.getInstance();
        final String finalNodeId;

        if (proposedId != null && !proposedId.trim().isEmpty()) {
            finalNodeId = proposedId.trim();
            // Check for existing ID
            if (rnm.getNodeById(finalNodeId) != null) {
                sendMessage(ctx.getSource(), "Error: ID '" + finalNodeId + "' already exists.");
                return 0;
            }
        } else {
            // Generate ID if not proposed (e.g., for /rn add or /rn add center without ID)
            finalNodeId = "node_" + UUID.randomUUID().toString().substring(0, 8);
        }

        // Safeguard: Prevent "nearest" as an ID
        if (finalNodeId.equalsIgnoreCase("nearest")) {
            sendMessage(ctx.getSource(), "Error: Node ID cannot be 'nearest' as it's a reserved keyword. Please choose a different ID.");
            return 0;
        }

        // Safeguard: Prevent "center" as an ID (since it's used as a keyword in the command)
        if (finalNodeId.equalsIgnoreCase("center")) {
            sendMessage(ctx.getSource(), "Error: Node ID cannot be 'center' as it's a reserved keyword for this command. Please choose a different ID.");
            return 0;
        }

        RoadNode newNode = new RoadNode(finalNodeId, position, worldId, new ArrayList<>(), null /* type */);

        if (rnm.addNode(newNode)) {
            sendMessage(ctx.getSource(), String.format("RoadNode '%s' created at X:%.2f Y:%.2f Z:%.2f.%s",
                    finalNodeId, position.getX(), position.getY(), position.getZ(),
                    additionalFeedback != null ? " " + additionalFeedback : ""));
        } else {
            sendMessage(ctx.getSource(), "Error: Failed to add node '" + finalNodeId + "' (unexpected).");
            return 0;
        }
        return 1;
    }

    /**
     * Suggestion provider for the "target_node" argument of the /rn remove command.
     * Suggests "nearest" and all existing node IDs.
     */
    public static CompletableFuture<Suggestions> suggestRemovableNodes(CommandContext<FabricClientCommandSource> context, SuggestionsBuilder builder) {
        String input = builder.getRemaining().toLowerCase(Locale.ROOT);
        RoadNetworkManager rnm = RoadNetworkManager.getInstance();
        FabricClientCommandSource source = context.getSource(); // Get the command source from the context

        ClientPlayerEntity player = source.getPlayer(); // This can return null but doesn't throw CommandSyntaxException

        if (player == null) {
            // This is an unexpected situation for a client-side command typically executed by the player.
            // Log an error and potentially return empty suggestions or all nodes without world filtering.
            LOGGER.warn("Player entity is null in suggestRemovableNodes command context. Cannot filter suggestions by world.");
            return builder.buildFuture();
        }

        // Suggest "nearest"
        if ("nearest".startsWith(input)) {
            builder.suggest("nearest");
        }

        // Suggest existing node IDs relevant to the current world
        if (player.clientWorld != null) {
            String worldId = player.clientWorld.getRegistryKey().getValue().toString();
            rnm.getAllNodes().stream()
                    .filter(node -> node.getWorldId() != null && node.getWorldId().equals(worldId)) // Filter by current world
                    .map(RoadNode::getId)
                    .filter(id -> id.toLowerCase(Locale.ROOT).startsWith(input))
                    .forEach(builder::suggest);
        } else { // Fallback if no world context, suggest all nodes (less ideal)
            rnm.getAllNodes().stream()
                    .map(RoadNode::getId)
                    .filter(id -> id.toLowerCase(Locale.ROOT).startsWith(input))
                    .forEach(builder::suggest);
        }
        return builder.buildFuture();
    }

    /**
     * New handler for /rn remove <target_node> [radius]
     */
    public static int handleRemoveNodeWithTarget(CommandContext<FabricClientCommandSource> ctx) throws CommandSyntaxException {
        String targetNodeIdentifier = StringArgumentType.getString(ctx, "target_node");
        Double optionalRadius = null;

        try {
            // Radius is only parsed if the "radius" argument node was reached in the command tree
            optionalRadius = DoubleArgumentType.getDouble(ctx, "radius");
        } catch (IllegalArgumentException e) {
            // Radius argument not provided, optionalRadius remains null
        }

        // If radius was provided but target is not "nearest", warn the user.
        if (optionalRadius != null && !targetNodeIdentifier.equalsIgnoreCase("nearest")) {
            sendMessage(ctx.getSource(), "Warning: Radius argument is only applicable when removing the 'nearest' node. It will be ignored for specific node ID '" + targetNodeIdentifier + "'.");
            // We don't null out optionalRadius here, just warn. The logic below will ignore it.
        }


        Vec3d playerPos = getPlayerPos(ctx.getSource());
        String worldId = getPlayer(ctx.getSource()).clientWorld.getRegistryKey().getValue().toString();
        RoadNetworkManager rnm = RoadNetworkManager.getInstance();
        RoadNode nodeToRemove;

        if (targetNodeIdentifier.equalsIgnoreCase("nearest")) {
            double confirmRadius = (optionalRadius != null) ? optionalRadius : 5.0;

            RoadNode closestOverall = rnm.findClosestNode(playerPos, worldId);
            if (closestOverall == null) {
                sendMessage(ctx.getSource(), "Error: No nodes found in the current world to determine 'nearest'.");
                return 0;
            }

            if (playerPos.distanceTo(closestOverall.getPosition()) <= confirmRadius) {
                nodeToRemove = closestOverall;
            } else {
                sendMessage(ctx.getSource(), String.format("Error: Nearest node '%s' (%.1fm away) is too far (> %.1fm). Get closer, increase radius, or specify ID.",
                        closestOverall.getId(), playerPos.distanceTo(closestOverall.getPosition()), confirmRadius));
                return 0;
            }
        } else {
            // Target is a specific node ID
            nodeToRemove = rnm.getNodeById(targetNodeIdentifier);
        }

        if (nodeToRemove == null) {
            sendMessage(ctx.getSource(), "Error: Node '" + targetNodeIdentifier + "' not found.");
            return 0;
        }

        if (rnm.removeNode(nodeToRemove.getId())) {
            if (nodeToRemove.getId().equals(selectedNodeId1)) selectedNodeId1 = null;
            if (nodeToRemove.getId().equals(selectedNodeId2)) selectedNodeId2 = null;
            sendMessage(ctx.getSource(), "RoadNode '" + nodeToRemove.getId() + "' removed.");
        } else {
            sendMessage(ctx.getSource(), "Error: Failed to remove node '" + nodeToRemove.getId() + "' (unexpected).");
            return 0;
        }
        return 1;
    }

    // This overload handles the case "/rn remove" with no arguments after "remove"
    public static int handleRemoveNode(CommandContext<FabricClientCommandSource> ctx) {
        sendMessage(ctx.getSource(), "Error: Missing argument for remove. Usage: /rn remove <id_or_nearest> [radius]");
        return 0;
    }

    public static int handleSelectNode(CommandContext<FabricClientCommandSource> ctx, int slot) throws CommandSyntaxException {
        String idOrNearest = StringArgumentType.getString(ctx, "id_or_nearest");
        Vec3d playerPos = getPlayerPos(ctx.getSource());
        RoadNetworkManager rnm = RoadNetworkManager.getInstance();
        RoadNode nodeToSelect;

        if (idOrNearest.equalsIgnoreCase("nearest")) {
            String worldId = getPlayer(ctx.getSource()).clientWorld.getRegistryKey().getValue().toString();
            nodeToSelect = rnm.findClosestNode(playerPos, worldId, Double.MAX_VALUE);
        } else {
            nodeToSelect = rnm.getNodeById(idOrNearest);
        }

        if (nodeToSelect == null) {
            sendMessage(ctx.getSource(), "Error: Node '" + idOrNearest + "' not found for selection.");
            return 0;
        }

        if (slot == 1) {
            selectedNodeId1 = nodeToSelect.getId();
            sendMessage(ctx.getSource(), "Node '" + nodeToSelect.getId() + "' selected as primary.");
        } else if (slot == 2) {
            selectedNodeId2 = nodeToSelect.getId();
            sendMessage(ctx.getSource(), "Node '" + nodeToSelect.getId() + "' selected as secondary.");
        } else {
            // This case should not be reachable if command registration is correct
            sendMessage(ctx.getSource(), "Error: Invalid selection slot.");
            return 0;
        }
        return 1;
    }

    public static int handleConnectNodes(CommandContext<FabricClientCommandSource> ctx) {
        if (selectedNodeId1 == null || selectedNodeId2 == null) {
            sendMessage(ctx.getSource(), "Error: Please select two nodes using /rn select1 and /rn select2 first.");
            return 0;
        }
        if (selectedNodeId1.equals(selectedNodeId2)) {
            sendMessage(ctx.getSource(), "Error: Cannot connect a node to itself.");
            return 0;
        }

        RoadNetworkManager rnm = RoadNetworkManager.getInstance();
        if (rnm.addConnection(selectedNodeId1, selectedNodeId2)) {
            sendMessage(ctx.getSource(), "Connected RoadNode '" + selectedNodeId1 + "' and '" + selectedNodeId2 + "'.");
        } else {
            sendMessage(ctx.getSource(), "Error: Failed to connect nodes (e.g., already connected, or one/both not found).");
        }
        return 1;
    }

    public static int handleConnectLastNodes(CommandContext<FabricClientCommandSource> ctx) {
        RoadNetworkManager rnm = RoadNetworkManager.getInstance();
        LinkedList<String> recentNodes = rnm.getTwoMostRecentNodes();

        if (recentNodes.size() < 2) {
            sendMessage(ctx.getSource(), "Error: Fewer than two nodes have been created recently in this session. Cannot connect 'last'.");
            return 0;
        }

        // The most recent is at index 0, the one before it is at index 1.
        String nodeIdLast = recentNodes.get(0);
        String nodeIdSecondLast = recentNodes.get(1);

        // Sanity check, though IDs should always be different if logic is correct
        if (nodeIdLast.equals(nodeIdSecondLast)) {
            sendMessage(ctx.getSource(), "Error: Last two recorded nodes are the same. This shouldn't happen.");
            LOGGER.warn("Attempted to connect last two nodes, but they were the same: {}", nodeIdLast);
            return 0;
        }

        sendMessage(ctx.getSource(), "Attempting to connect last two created nodes: '" + nodeIdLast + "' and '" + nodeIdSecondLast + "'.");

        if (rnm.addConnection(nodeIdLast, nodeIdSecondLast)) {
            sendMessage(ctx.getSource(), "Connected RoadNode '" + nodeIdLast + "' and '" + nodeIdSecondLast + "'.");
        } else {
            // This could happen if they are already connected, or if one of the nodes was removed
            // after being added to the recent list but before this command was run.
            sendMessage(ctx.getSource(), "Error: Failed to connect last two nodes (e.g., already connected, or one/both no longer exist).");
            return 0; // Ensure failure is returned
        }
        return 1;
    }

    public static int handleDisconnectNodes(CommandContext<FabricClientCommandSource> ctx) {
        if (selectedNodeId1 == null || selectedNodeId2 == null) {
            sendMessage(ctx.getSource(), "Error: Please select two nodes using /rn select1 and /rn select2 first.");
            return 0;
        }

        RoadNetworkManager rnm = RoadNetworkManager.getInstance();
        if (rnm.removeConnection(selectedNodeId1, selectedNodeId2)) {
            sendMessage(ctx.getSource(), "Disconnected RoadNode '" + selectedNodeId1 + "' and '" + selectedNodeId2 + "'.");
        } else {
            sendMessage(ctx.getSource(), "Error: Failed to disconnect nodes (e.g., not connected, or one/both not found).");
        }
        return 1;
    }

    public static int handleNodeInfo(CommandContext<FabricClientCommandSource> ctx) throws CommandSyntaxException {
        String idOrNearest = null;
        try {
            idOrNearest = StringArgumentType.getString(ctx, "id_or_nearest");
        } catch (IllegalArgumentException e) {
            // "id_or_nearest" is optional, defaults to "nearest" logic if not provided
        }

        Vec3d playerPos = getPlayerPos(ctx.getSource());
        RoadNetworkManager rnm = RoadNetworkManager.getInstance();
        RoadNode nodeToShow;

        if (idOrNearest == null || idOrNearest.equalsIgnoreCase("nearest")) {
            String worldId = getPlayer(ctx.getSource()).clientWorld.getRegistryKey().getValue().toString();
            nodeToShow = rnm.findClosestNode(playerPos, worldId, Double.MAX_VALUE);
        } else {
            nodeToShow = rnm.getNodeById(idOrNearest);
        }

        if (nodeToShow == null) {
            sendMessage(ctx.getSource(), "Error: Node not found.");
            return 0;
        }

        sendMessage(ctx.getSource(), "--- Node Info: " + nodeToShow.getId() + " ---");
        Vec3d pos = nodeToShow.getPosition();
        sendMessage(ctx.getSource(), String.format("Position: X:%.0f Y:%.0f Z:%.0f", pos.getX(), pos.getY(), pos.getZ()));
        sendMessage(ctx.getSource(), "Type: " + (nodeToShow.getType() != null ? nodeToShow.getType() : "N/A"));
        String connectionsStr = String.join(", ", nodeToShow.getConnections());
        sendMessage(ctx.getSource(), "Connections: " + (connectionsStr.isEmpty() ? "None" : connectionsStr));
        return 1;
    }

    public static int handleListNodes(CommandContext<FabricClientCommandSource> ctx) throws CommandSyntaxException {
        Double radiusParam = null;
        try {
            radiusParam = DoubleArgumentType.getDouble(ctx, "radius");
        } catch (IllegalArgumentException e) {
            // "radius" argument is optional
        }

        Vec3d playerPos = null; // Only needed if radius is specified
        if (radiusParam != null) {
            playerPos = getPlayerPos(ctx.getSource());
        }

        RoadNetworkManager rnm = RoadNetworkManager.getInstance();
        Collection<RoadNode> allNodes = rnm.getAllNodes();
        List<RoadNode> nodesToList = new ArrayList<>();

        if (radiusParam != null && playerPos != null) {
            for (RoadNode node : allNodes) {
                if (playerPos.distanceTo(node.getPosition()) <= radiusParam) {
                    nodesToList.add(node);
                }
            }
        } else {
            nodesToList.addAll(allNodes);
        }

        if (nodesToList.isEmpty()) {
            sendMessage(ctx.getSource(), "No road nodes found" + (radiusParam != null ? " within " + String.format("%.1f", radiusParam) + " meters." : "."));
            return 0;
        }

        sendMessage(ctx.getSource(), "--- Road Nodes (" + nodesToList.size() + ") ---");
        int count = 0;
        for (RoadNode node : nodesToList) {
            if (count++ >= 20) { // Limit chat spam
                sendMessage(ctx.getSource(), "...and " + (nodesToList.size() - 20) + " more.");
                break;
            }
            Vec3d pos = node.getPosition();
            sendMessage(ctx.getSource(), String.format("%s: X:%.0f Y:%.0f Z:%.0f (Connections: %d, Type: %s)",
                    node.getId(), pos.getX(), pos.getY(), pos.getZ(),
                    node.getConnections().size(), (node.getType() != null ? node.getType() : "N/A")));
        }
        return 1;
    }

    public static int handleSetNodeType(CommandContext<FabricClientCommandSource> ctx) throws CommandSyntaxException {
        String idOrNearest = StringArgumentType.getString(ctx, "id_or_nearest");
        String typeName = StringArgumentType.getString(ctx, "type_name");

        Vec3d playerPos = getPlayerPos(ctx.getSource());
        RoadNetworkManager rnm = RoadNetworkManager.getInstance();
        RoadNode nodeToModify;

        if (idOrNearest.equalsIgnoreCase("nearest")) {
            String worldId = getPlayer(ctx.getSource()).clientWorld.getRegistryKey().getValue().toString();
            nodeToModify = rnm.findClosestNode(playerPos, worldId, Double.MAX_VALUE);
        } else {
            nodeToModify = rnm.getNodeById(idOrNearest);
        }

        if (nodeToModify == null) {
            sendMessage(ctx.getSource(), "Error: Node '" + idOrNearest + "' not found.");
            return 0;
        }

        rnm.updateNodeType(nodeToModify.getId(), typeName); // Assumes RCM handles updating the node
        sendMessage(ctx.getSource(), "Node '" + nodeToModify.getId() + "' type set to '" + typeName + "'. Remember to /rn save.");
        return 1;
    }

    public static int handleSaveNetwork(CommandContext<FabricClientCommandSource> ctx) {
        if (RoadNetworkManager.getInstance().saveNetwork()) {
            sendMessage(ctx.getSource(), "Road network saved successfully.");
        } else {
            sendMessage(ctx.getSource(), "Error: Failed to save road network. Check console/logs.");
        }
        return 1;
    }

    public static int handleReloadNetwork(CommandContext<FabricClientCommandSource> ctx) {
        if (RoadNetworkManager.getInstance().loadNetwork()) {
            sendMessage(ctx.getSource(), "Road network reloaded successfully.");
            selectedNodeId1 = null; // Clear selections as node IDs/existence might have changed
            selectedNodeId2 = null;
        } else {
            sendMessage(ctx.getSource(), "Error: Failed to reload road network. Check console/logs or file integrity.");
        }
        return 1;
    }

    public static int handleTestHighwayPath(CommandContext<FabricClientCommandSource> ctx) throws CommandSyntaxException {
        return findAndVisualizeHighwayPath(ctx, false); // Call helper, DO NOT drive
    }

    public static int handleTestAndDriveHighwayPath(CommandContext<FabricClientCommandSource> ctx) throws CommandSyntaxException {
        return findAndVisualizeHighwayPath(ctx, true); // Call helper, DO drive
    }

    // Helper method to find, visualize, and optionally drive the highway path
    private static int findAndVisualizeHighwayPath(CommandContext<FabricClientCommandSource> ctx, boolean drivePath) throws CommandSyntaxException {
        ClientPlayerEntity player = getPlayer(ctx.getSource());
        Vec3d playerStartPos = player.getPos(); // Player's actual start for the whole journey
        String worldId = player.clientWorld.getRegistryKey().getValue().toString();

        double goalX = DoubleArgumentType.getDouble(ctx, "x");
        double goalY = DoubleArgumentType.getDouble(ctx, "y");
        double goalZ = DoubleArgumentType.getDouble(ctx, "z");
        Vec3d finalGoalPos = new Vec3d(goalX, goalY, goalZ);

        sendMessage(ctx.getSource(), String.format("Processing highway path from player (%.1f, %.1f, %.1f) to (%.1f, %.1f, %.1f) in world '%s'. Drive: %s",
                playerStartPos.getX(), playerStartPos.getY(), playerStartPos.getZ(), goalX, goalY, goalZ, worldId, drivePath));

        RoadNetworkManager rnm = RoadNetworkManager.getInstance();
        BasicPathAI pathAI = BasicPathAI.getInstance(); // Get AI instance

        // 1. Find the closest road node to player (Start_RN)
        RoadNode startRN = rnm.findClosestNode(playerStartPos, worldId);
        if (startRN == null) {
            sendMessage(ctx.getSource(), "Error: No road node found near your current position in this world.");
            RoadNetworkRenderer.clearTestHighwayPath();
            pathAI.stop(); // Stop any current AI movement
            return 0;
        }
        sendMessage(ctx.getSource(), "Nearest start road node (Start_RN): " + startRN.getId() + " at " + formatVec3d(startRN.getPosition()));

        // 2. Find the closest road node to goal (Goal_RN)
        RoadNode goalRN = rnm.findClosestNode(finalGoalPos, worldId);
        if (goalRN == null) {
            sendMessage(ctx.getSource(), "Error: No road node found near the goal position in this world.");
            RoadNetworkRenderer.clearTestHighwayPath();
            pathAI.stop();
            return 0;
        }
        sendMessage(ctx.getSource(), "Nearest goal road node (Goal_RN): " + goalRN.getId() + " at " + formatVec3d(goalRN.getPosition()));

        // 3. A* on Road Network between Start_RN and Goal_RN
        List<RoadNode> highwayNodes;
        if (startRN.getId().equals(goalRN.getId())) {
            sendMessage(ctx.getSource(), "Player and goal are closest to the same road node (" + startRN.getId() + "). Highway segment is trivial.");
            // We still need a list containing this single node for the logic below if driving.
            highwayNodes = List.of(startRN);
            //TODO it didn't visualize single node
            RoadNetworkRenderer.setTestHighwayPath(highwayNodes); // Visualize this single point or segment
        } else {
            highwayNodes = rnm.findPathOnRoadNetwork(startRN.getId(), goalRN.getId());
            if (highwayNodes == null || highwayNodes.isEmpty()) {
                sendMessage(ctx.getSource(), "Failed to find a highway path between " + startRN.getId() + " and " + goalRN.getId() + ".");
                RoadNetworkRenderer.clearTestHighwayPath();
                pathAI.stop();
                return 0;
            }
            sendMessage(ctx.getSource(), "Highway path found with " + highwayNodes.size() + " nodes: " +
                    highwayNodes.stream().map(RoadNode::getId).collect(Collectors.joining(" -> ")));
            RoadNetworkRenderer.setTestHighwayPath(highwayNodes);
        }
        RoadNetworkRenderer.renderTestHighwayPath = true;


        // 4. If "drivePath" is true, initiate movement
        if (drivePath) {
            // For this test, we'll simplify:
            // - Path from player to Start_RN.position using GeneralPurposeTravelStrategy
            // - Then path along highwayNodes using HighwaySplineStrategy
            // - Then path from Goal_RN.position to finalGoalPos using GeneralPurposeTravelStrategy
            // This would require a more complex orchestrator.

            // *** SIMPLIFIED TEST: Drive ONLY the highway segment ***
            // This means the player should manually position themselves at startRN first for this simple test.
            // Or, we can just directly tell the AI to start the highway spline path.
            // The HighwaySplineStrategy will then generate splines from these highwayNodes.

            if (highwayNodes.size() < 2) {
                sendMessage(ctx.getSource(), "Highway path has fewer than 2 nodes. Cannot use HighwaySplineStrategy. Will attempt direct path or stop.");
                // If player is already at startRN and startRN == goalRN, and goal is close to startRN,
                // you might want a direct GeneralPurpose path to finalGoalPos.
                // For this test, let's just stop if the highway itself is too short.
                pathAI.stop();
                // A more complete orchestrator would handle this: pathAI.startGeneralPath(List.of(playerStartPos, finalGoalPos));
                return 1; // Path visualized, but not driven with spline.
            }

            sendMessage(ctx.getSource(), "Initiating drive along the highway segment using HighwaySplineStrategy.");
            pathAI.startHighwaySplinePath(highwayNodes);
            // The HighwaySplineStrategy will now take over in BasicPathAI's tick loop.
            // For a full journey, an orchestrator would:
            // 1. Path locally to startRN.getPosition()
            // 2. On completion, call pathAI.startHighwaySplinePath(highwayNodes)
            // 3. On completion, path locally from goalRN.getPosition() to finalGoalPos
        }
        return 1;
    }

    public static int handleClearTestHighwayPath(CommandContext<FabricClientCommandSource> ctx) {
        RoadNetworkRenderer.clearTestHighwayPath();
        sendMessage(ctx.getSource(), "Cleared displayed test highway path.");
        return 1;
    }

    // Helper to format Vec3d for messages
    private static String formatVec3d(Vec3d vec) {
        if (vec == null) return "null";
        return String.format("%.1f, %.1f, %.1f", vec.getX(), vec.getY(), vec.getZ());
    }

    public static int handleHelp(CommandContext<FabricClientCommandSource> ctx) {
        FabricClientCommandSource source = ctx.getSource();

        // Using direct § codes.
        // §e = Yellow, §b = Aqua, §a = Green, §c = Red, §7 = Gray, §f = White, §6 = Gold
        // Command names in Aqua, arguments in Yellow, descriptions in Gray/White.

        sendFormattedText(source, Text.literal("--- §6WynnAI RoadNode Commands (/rn)§r ---").formatted(Formatting.GOLD)); // §r resets color

        sendFormattedText(source, Text.literal("§b/rn add §e[id] ['center']§f - Add node. Options: custom ID, snap to player block center.")
                .append(Text.literal("\n    §b/rn add §e[id] <x> <y> <z>§f - Add node at specific coordinates.").formatted(Formatting.AQUA)));

        sendFormattedText(source, Text.literal("§b/rn remove §e<id | nearest> [radius]§f - Removes node. Optional confirm radius for 'nearest'.")
                .append(Text.literal(" Suggests 'nearest' and existing node IDs.").formatted(Formatting.GRAY)));

        sendFormattedText(source, Text.literal("§b/rn select1 §e<id | nearest>§f - Selects first node for connect/disconnect."));
        sendFormattedText(source, Text.literal("§b/rn select2 §e<id | nearest>§f - Selects second node."));

        sendFormattedText(source, Text.literal("§b/rn connect§f - Connects selected nodes.")
                .append(Text.literal("\n    §b/rn connect last§f - Connects last two created nodes.").formatted(Formatting.AQUA)));

        sendFormattedText(source, Text.literal("§b/rn disconnect§f - Disconnects selected nodes."));

        sendFormattedText(source, Text.literal("§b/rn info §e[id | nearest]§f - Shows info for a node. Defaults to nearest if no ID."));
        sendFormattedText(source, Text.literal("§b/rn list §e[radius]§f - Lists nodes, optionally within player radius."));
        sendFormattedText(source, Text.literal("§b/rn settype §e<id | nearest> <type_name>§f - Sets the type of a node."));
        sendFormattedText(source, Text.literal("§b/rn save§f - Saves the current road network to file."));
        sendFormattedText(source, Text.literal("§b/rn reload§f - Reloads network from file (clears selections)."));

        sendFormattedText(source, Text.literal("§b/rn testpath §e<x> <y> <z> ['drive']§f - Visualizes A* highway path to coords. 'drive' makes bot follow it.")
                .append(Text.literal("\n    §b/rn testpath clearpath§f - Clears visualized test path & stops AI.").formatted(Formatting.AQUA)));

        sendFormattedText(source, Text.literal("§b/rn help§f - Shows this help message."));

        return 1;
    }
}