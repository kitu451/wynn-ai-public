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
                        // Existing paths:
                        // /rn add (player pos, generated ID, no snap)
                        .executes(RoadNodeCommands::handleAddNode)
                        // /rn add center (player pos, generated ID, snapped)
                        .then(literal("center")
                                .executes(ctx -> RoadNodeCommands.handleAddNode(ctx, true)) // Snap = true, no ID
                                // /rn add center <id> (player pos, custom ID, snapped)
                                .then(argument("id", StringArgumentType.word())
                                        .executes(ctx -> RoadNodeCommands.handleAddNode(ctx, true)))) // Snap = true, with ID
                        // Path for ID first, then optional "center" or XYZ
                        .then(argument("id", StringArgumentType.word())
                                // /rn add <id> (player pos, custom ID, no snap)
                                .executes(RoadNodeCommands::handleAddNode) // Defaults to snap=false, gets ID from context
                                // /rn add <id> center (player pos, custom ID, snapped)
                                .then(literal("center")
                                        .executes(ctx -> RoadNodeCommands.handleAddNode(ctx, true))) // Snap = true, gets ID from context
                                // *** NEW PATH: /rn add <id> <x> <y> <z> ***
                                .then(argument("x", DoubleArgumentType.doubleArg())
                                        .then(argument("y", DoubleArgumentType.doubleArg())
                                                .then(argument("z", DoubleArgumentType.doubleArg())
                                                        .executes(RoadNodeCommands::handleAddNodeWithCoordinates) // New handler method
                                                )
                                        )
                                )
                        )
                )
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
                        .executes(RoadNodeCommands::handleConnectNodes) // Existing: Connects selectedNodeId1 and selectedNodeId2
                        // *** NEW SUBCOMMAND ***
                        .then(literal("last")
                                .executes(RoadNodeCommands::handleConnectLastNodes) // New handler
                        )
                )
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
                // /rn testpath
                .then(literal("testpath")
                        // Existing: /rn testpath <x> <y> <z> (just visualizes)
                        .then(argument("x", DoubleArgumentType.doubleArg())
                                .then(argument("y", DoubleArgumentType.doubleArg())
                                        .then(argument("z", DoubleArgumentType.doubleArg())
                                                .executes(RoadNodeCommands::handleTestHighwayPath) // Just shows the path
                                                // New: /rn testpath <x> <y> <z> drive (visualizes AND drives)
                                                .then(literal("drive")
                                                        .executes(RoadNodeCommands::handleTestAndDriveHighwayPath) // New handler
                                                )
                                        )
                                )
                        )
                        .then(literal("clearpath")
                                .executes(RoadNodeCommands::handleClearTestHighwayPath)
                        )
                )
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