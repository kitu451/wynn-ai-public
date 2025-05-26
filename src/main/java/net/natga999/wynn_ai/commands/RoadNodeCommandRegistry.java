package net.natga999.wynn_ai.commands;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.DoubleArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.tree.LiteralCommandNode;

import net.fabricmc.fabric.api.client.command.v2.FabricClientCommandSource;

import static net.fabricmc.fabric.api.client.command.v2.ClientCommandManager.literal;
import static net.fabricmc.fabric.api.client.command.v2.ClientCommandManager.argument;

public class RoadNodeCommandRegistry {

    public static void register(CommandDispatcher<FabricClientCommandSource> dispatcher) {
        // Main literal for all road node commands, alias 'rn'
        // First, define the structure using the builder
        var rnNodeBuilder = literal("rn")
                // /rn add [id]
                .then(literal("add")
                        // /rn add
                        .executes(RoadNodeCommands::handleAddNode) // Default: player's exact Vec3d, no ID
                        // /rn add center
                        .then(literal("center") // New subcommand for snapping
                                .executes(ctx -> RoadNodeCommands.handleAddNode(ctx, true)) // Snap = true, no ID
                                // /rn add center <id>
                                .then(argument("id", StringArgumentType.word())
                                        .executes(ctx -> RoadNodeCommands.handleAddNode(ctx, true)))) // Snap = true, with ID
                        // /rn add <id>
                        .then(argument("id", StringArgumentType.word())
                                .executes(RoadNodeCommands::handleAddNode) // Default: player's exact Vec3d, with ID
                                // /rn add <id> center
                                .then(literal("center")
                                        .executes(ctx -> RoadNodeCommands.handleAddNode(ctx, true))))) // Snap = true, with ID
                // ... (all your other .then() clauses for subcommands) ...
                // /rn remove <id_or_nearest> [radius]
                .then(literal("remove")
                        .executes(RoadNodeCommands::handleRemoveNode) // Calls the (CommandContext) overload -> (ctx, null, null)
                        .then(argument("target_node", StringArgumentType.word()) // New single argument
                                .suggests(RoadNodeCommands::suggestRemovableNodes) // Our custom suggestion provider
                                .executes(RoadNodeCommands::handleRemoveNodeWithTarget) // New handler for this structure
                                .then(argument("radius", DoubleArgumentType.doubleArg(0.1))
                                        .executes(RoadNodeCommands::handleRemoveNodeWithTarget) // Same handler, it will check for radius
                                )
                        )
                )
                // /rn select1 <id_or_nearest>
                .then(literal("select1")
                        .then(argument("id_or_nearest", StringArgumentType.word())
                                .executes(ctx -> RoadNodeCommands.handleSelectNode(ctx, 1))))
                // /rn select2 <id_or_nearest>
                .then(literal("select2")
                        .then(argument("id_or_nearest", StringArgumentType.word())
                                .executes(ctx -> RoadNodeCommands.handleSelectNode(ctx, 2))))
                // /rn connect
                .then(literal("connect")
                        .executes(RoadNodeCommands::handleConnectNodes))
                // /rn disconnect
                .then(literal("disconnect")
                        .executes(RoadNodeCommands::handleDisconnectNodes))
                // /rn info [id_or_nearest]
                .then(literal("info")
                        .executes(RoadNodeCommands::handleNodeInfo) // No ID, implies nearest
                        .then(argument("id_or_nearest", StringArgumentType.word())
                                .executes(RoadNodeCommands::handleNodeInfo)))
                // /rn list [radius]
                .then(literal("list")
                        .executes(RoadNodeCommands::handleListNodes) // No radius, lists all
                        .then(argument("radius", DoubleArgumentType.doubleArg(0.1))
                                .executes(RoadNodeCommands::handleListNodes)))
                // /rn settype <id_or_nearest> <type_name>
                .then(literal("settype")
                        .then(argument("id_or_nearest", StringArgumentType.word())
                                .then(argument("type_name", StringArgumentType.greedyString()) // Greedy for multi-word types
                                        .executes(RoadNodeCommands::handleSetNodeType))))
                // /rn save
                .then(literal("save")
                        .executes(RoadNodeCommands::handleSaveNetwork))
                // /rn reload
                .then(literal("reload")
                        .executes(RoadNodeCommands::handleReloadNetwork))
                // /rn help
                .then(literal("help")
                        .executes(RoadNodeCommands::handleHelp))
                // Default action for /rn (could be help or a status)
                .executes(RoadNodeCommands::handleHelp); // Shows help if just /rn is typed

        // Build the actual command node from the builder
        LiteralCommandNode<FabricClientCommandSource> rnActualNode = rnNodeBuilder.build();

        // Register the main command node
        dispatcher.register(literal(rnActualNode.getLiteral()).redirect(rnActualNode).executes(rnActualNode.getCommand())); // This line is a bit redundant now
        // A simpler registration after building rnActualNode:
        dispatcher.getRoot().addChild(rnActualNode); // Add the built node to the dispatcher's root

        // Register the alias redirecting to the *built* node
        dispatcher.register(literal("roadnode").redirect(rnActualNode));
    }
}