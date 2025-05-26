package net.natga999.wynn_ai.commands;

import net.natga999.wynn_ai.path.network.RoadNetworkManager;
import net.natga999.wynn_ai.path.network.RoadNode;

import net.minecraft.client.network.ClientPlayerEntity;
import net.minecraft.text.Text;
import net.minecraft.util.math.Vec3d;

import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import com.mojang.brigadier.arguments.DoubleArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;

import net.fabricmc.fabric.api.client.command.v2.FabricClientCommandSource;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.UUID;

public class RoadNodeCommands {

    private static String selectedNodeId1 = null;
    private static String selectedNodeId2 = null;

    private static void sendMessage(FabricClientCommandSource source, String message) {
        source.sendFeedback(Text.literal(message));
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

    public static int handleAddNode(CommandContext<FabricClientCommandSource> ctx) throws CommandSyntaxException {
        String customId = null;
        try {
            customId = StringArgumentType.getString(ctx, "id");
        } catch (IllegalArgumentException e) {
            // "id" argument is optional, this means it wasn't provided
        }

        Vec3d playerPos = getPlayerPos(ctx.getSource());
        RoadNetworkManager rnm = RoadNetworkManager.getInstance();

        final String nodeId;
        if (customId != null && !customId.trim().isEmpty()) {
            if (rnm.getNodeById(customId) != null) {
                sendMessage(ctx.getSource(), "Error: ID '" + customId + "' already exists.");
                return 0; // Indicate failure
            }
            nodeId = customId;
        } else {
            nodeId = "node_" + UUID.randomUUID().toString().substring(0, 8); // Generate a short unique ID
        }

        // Assuming RoadNode constructor: RoadNode(String id, Vec3d position, List<String> connections, String type)
        // Or that type is set separately or defaults to null.
        String worldId = getPlayer(ctx.getSource()).clientWorld.getRegistryKey().getValue().toString();
        RoadNode newNode = new RoadNode(nodeId, playerPos, worldId, new ArrayList<>(), null /* type */);
        if (rnm.addNode(newNode)) {
            sendMessage(ctx.getSource(), String.format("RoadNode '%s' created at X:%.0f Y:%.0f Z:%.0f.",
                    nodeId, playerPos.getX(), playerPos.getY(), playerPos.getZ()));
        } else {
            sendMessage(ctx.getSource(), "Error: Failed to add node (unexpected).");
        }
        return 1; // Indicate success
    }

    public static int handleRemoveNode(CommandContext<FabricClientCommandSource> ctx) throws CommandSyntaxException {
        String idOrNearest = StringArgumentType.getString(ctx, "id_or_nearest");
        double confirmRadius = 5.0; // Default confirmation radius for "nearest"
        try {
            confirmRadius = DoubleArgumentType.getDouble(ctx, "radius");
        } catch (IllegalArgumentException e) {
            // "radius" argument is optional
        }

        Vec3d playerPos = getPlayerPos(ctx.getSource());
        RoadNetworkManager rnm = RoadNetworkManager.getInstance();
        RoadNode nodeToRemove;

        if (idOrNearest.equalsIgnoreCase("nearest")) {
            String worldId = getPlayer(ctx.getSource()).clientWorld.getRegistryKey().getValue().toString();
            nodeToRemove = rnm.findClosestNode(playerPos, worldId, Double.MAX_VALUE); // find closest without initial distance limit
            if (nodeToRemove != null && playerPos.distanceTo(nodeToRemove.getPosition()) > confirmRadius) {
                sendMessage(ctx.getSource(), String.format("Error: Nearest node '%s' (%.1fm away) is too far (> %.1fm). Get closer or specify ID.",
                        nodeToRemove.getId(), playerPos.distanceTo(nodeToRemove.getPosition()), confirmRadius));
                return 0;
            }
        } else {
            nodeToRemove = rnm.getNodeById(idOrNearest);
        }

        if (nodeToRemove == null) {
            sendMessage(ctx.getSource(), "Error: Node '" + idOrNearest + "' not found.");
            return 0;
        }

        if (rnm.removeNode(nodeToRemove.getId())) {
            // Clear selections if the removed node was selected
            if (nodeToRemove.getId().equals(selectedNodeId1)) selectedNodeId1 = null;
            if (nodeToRemove.getId().equals(selectedNodeId2)) selectedNodeId2 = null;
            sendMessage(ctx.getSource(), "RoadNode '" + nodeToRemove.getId() + "' removed.");
        } else {
            sendMessage(ctx.getSource(), "Error: Failed to remove node '" + nodeToRemove.getId() + "'.");
        }
        return 1;
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

    public static int handleHelp(CommandContext<FabricClientCommandSource> ctx) {
        FabricClientCommandSource source = ctx.getSource();
        sendMessage(source, "--- WynnAI RoadNode Commands (/rn) ---");
        sendMessage(source, "/rn add [id] - Adds node at current pos. Optional custom ID.");
        sendMessage(source, "/rn remove <id|\"nearest\"> [radius] - Removes node. Optional confirm radius for 'nearest'.");
        sendMessage(source, "/rn select1 <id|\"nearest\"> - Selects first node for connect/disconnect.");
        sendMessage(source, "/rn select2 <id|\"nearest\"> - Selects second node.");
        sendMessage(source, "/rn connect - Connects selected nodes.");
        sendMessage(source, "/rn disconnect - Disconnects selected nodes.");
        sendMessage(source, "/rn info [id|\"nearest\"] - Shows info. Defaults to nearest if no ID.");
        sendMessage(source, "/rn list [radius] - Lists nodes, optionally within radius.");
        sendMessage(source, "/rn settype <id|\"nearest\"> <type_name> - Sets the type of a node.");
        sendMessage(source, "/rn save - Saves the current road network to file.");
        sendMessage(source, "/rn reload - Reloads network from file (clears selections).");
        sendMessage(source, "/rn help - Shows this help message.");
        return 1;
    }
}