package net.natga999.wynn_ai.managers;

import net.natga999.wynn_ai.ai.BasicPathAI;
import net.natga999.wynn_ai.path.LongDistancePathPlanner;
import net.natga999.wynn_ai.path.network.RoadNetworkManager;
import net.natga999.wynn_ai.path.network.RoadNode;

import net.minecraft.client.MinecraftClient;
import net.minecraft.client.network.ClientPlayerEntity;
import net.minecraft.item.ItemStack;
import net.minecraft.item.ToolItem;
import net.minecraft.text.Text;
import net.minecraft.util.math.Vec3d;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

public class RepairStateManager {
    private static final Logger LOGGER = LoggerFactory.getLogger(RepairStateManager.class);
    private static final RepairStateManager INSTANCE = new RepairStateManager();

    private final BasicPathAI basicPathAI;
    private final LongDistancePathPlanner longDistancePathPlanner;
    private final RoadNetworkManager roadNetworkManager;


    // Configuration - TODO: Load from a config file
    private int toolDurabilityThreshold = 30; // e.g., repair if durability < 30
    private List<Vec3d> knownRepairStationCoordinates = new ArrayList<>(); // Or use RoadNode type
    private String repairStationRoadNodeType = "REPAIR_STATION_ADJACENT";
    private int repairCheckIntervalTicks = 20 * 10; // Check every 10 seconds
    private int repairInteractionTimeoutTicks = 20 * 15; // Max time to wait for repair interaction
    private int postRepairCooldownTicks = 20 * 5; // Cooldown after returning from repair

    private RepairState currentState = RepairState.IDLE;
    private boolean needsRepairFlag = false;
    private Vec3d previousActivityLocation = null;
    private boolean wasHarvestingActive = false;
    private int currentTickCounter = 0;

    private enum RepairState {
        IDLE,
        CHECKING_TOOLS, // Optional explicit state, or do it in IDLE
        PATHING_TO_REPAIR,
        AT_REPAIR_STATION, // Reached location, about to interact
        INTERACTING_REPAIR, // Clicked repair, waiting for it to finish (e.g. NPC dialog, GUI)
        PATHING_TO_WORK,
        COOLDOWN_ERROR, // If something went wrong, wait before retry
        COOLDOWN_SUCCESS // After successful repair cycle, wait before checking again
    }

    private RepairStateManager() {
        this.basicPathAI = BasicPathAI.getInstance();
        this.longDistancePathPlanner = LongDistancePathPlanner.getInstance();
        this.roadNetworkManager = RoadNetworkManager.getInstance();
        // TODO: Load knownRepairStationCoordinates from config if not using RoadNode type
    }

    public static RepairStateManager getInstance() {
        return INSTANCE;
    }

    public void tick(MinecraftClient client) {
        if (client.player == null || client.world == null) {
            if (currentState != RepairState.IDLE) {
                LOGGER.warn("Player or world is null, resetting RepairState to IDLE.");
                resetToIdle();
            }
            return;
        }
        currentTickCounter++;

        switch (currentState) {
            case IDLE:
                if (currentTickCounter >= repairCheckIntervalTicks) {
                    checkForRepairNeeds(client.player);
                    if (needsRepairFlag) {
                        LOGGER.info("Tools need repair. Initiating repair cycle.");
                        // Store current activity state
                        previousActivityLocation = client.player.getPos();
                        wasHarvestingActive = HarvestPathManager.getInstance().isActive(); // Assumes isActive() getter

                        // Pause other activities
                        if (wasHarvestingActive) {
                            HarvestPathManager.getInstance().setActive(false); // Assumes setActive(false)
                        }
                        basicPathAI.stop(); // Stop any current AI movement

                        transitionToState(RepairState.PATHING_TO_REPAIR);
                    } else {
                        currentTickCounter = 0; // Reset counter if no repair needed
                    }
                }
                break;

            case PATHING_TO_REPAIR:
                // This state is entered once, path is planned, BasicPathAI takes over.
                // We will transition out when BasicPathAI completes.
                // Actual path planning should happen in transition *into* this state.
                if (basicPathAI.getStrategy() == null || basicPathAI.getStrategy().isComplete(basicPathAI)) {
                    LOGGER.info("Arrived at repair station vicinity.");
                    transitionToState(RepairState.AT_REPAIR_STATION);
                }
                break;

            case AT_REPAIR_STATION:
                LOGGER.info("Attempting to interact with repair station.");
                // TODO: Implement actual interaction logic (e.g., find NPC, click block/GUI)
                // For now, simulate with a delay
                boolean interactionSuccess = simulateRepairInteraction(client);
                if (interactionSuccess) {
                    transitionToState(RepairState.INTERACTING_REPAIR);
                } else {
                    LOGGER.warn("Failed to interact with repair station. Cooling down.");
                    transitionToState(RepairState.COOLDOWN_ERROR);
                }
                break;

            case INTERACTING_REPAIR:
                // TODO: Check if repair is actually complete (e.g. inventory change, GUI closed)
                if (currentTickCounter >= repairInteractionTimeoutTicks) { // Or some other condition
                    LOGGER.info("Repair assumed complete. Pathing back to work location.");
                    needsRepairFlag = false; // Assume tools are repaired
                    transitionToState(RepairState.PATHING_TO_WORK);
                }
                break;

            case PATHING_TO_WORK:
                // Similar to PATHING_TO_REPAIR, transition out when BasicPathAI completes.
                if (basicPathAI.getStrategy() == null || basicPathAI.getStrategy().isComplete(basicPathAI)) {
                    LOGGER.info("Arrived back at previous work location.");
                    if (wasHarvestingActive) {
                        HarvestPathManager.getInstance().setActive(true);
                    }
                    transitionToState(RepairState.COOLDOWN_SUCCESS);
                }
                break;

            case COOLDOWN_ERROR:
            case COOLDOWN_SUCCESS:
                if (currentTickCounter >= postRepairCooldownTicks) {
                    resetToIdle();
                }
                break;
        }
    }

    private void transitionToState(RepairState newState) {
        LOGGER.debug("RepairState transitioning from {} to {}", currentState, newState);
        currentState = newState;
        currentTickCounter = 0; // Reset tick counter for the new state

        MinecraftClient client = MinecraftClient.getInstance(); // Needed for path planning

        switch (newState) {
            case PATHING_TO_REPAIR:
                Vec3d repairStationPos = findClosestRepairLocation(client.player.getPos(), client.player.getWorld().getRegistryKey().getValue().toString());
                if (repairStationPos == null) {
                    LOGGER.error("No repair station found! Cannot path to repair.");
                    transitionToState(RepairState.COOLDOWN_ERROR);
                    return;
                }
                List<Vec3d> pathToRepair = longDistancePathPlanner.planPathToGoal(client.player.getPos(), repairStationPos, client.world);
                if (pathToRepair != null && !pathToRepair.isEmpty()) {
                    basicPathAI.startGeneralPath(pathToRepair);
                } else {
                    LOGGER.error("Failed to plan path to repair station {}.", repairStationPos);
                    client.player.sendMessage(Text.literal("Failed to path to repair station!"), false);
                    transitionToState(RepairState.COOLDOWN_ERROR);
                }
                break;
            case PATHING_TO_WORK:
                if (previousActivityLocation == null) {
                    LOGGER.warn("No previous activity location to return to. Idling.");
                    resetToIdle(); // Or go to a default safe spot
                    return;
                }
                List<Vec3d> pathToWork = longDistancePathPlanner.planPathToGoal(client.player.getPos(), previousActivityLocation, client.world);
                if (pathToWork != null && !pathToWork.isEmpty()) {
                    basicPathAI.startGeneralPath(pathToWork);
                } else {
                    LOGGER.error("Failed to plan path back to work location {}.", previousActivityLocation);
                    client.player.sendMessage(Text.literal("Failed to path back to work!"), false);
                    transitionToState(RepairState.COOLDOWN_ERROR); // Or just idle if stuck
                }
                break;
            // Other states don't need immediate action on transition in this example
        }
    }

    private void resetToIdle() {
        LOGGER.info("RepairStateManager resetting to IDLE state.");
        currentState = RepairState.IDLE;
        currentTickCounter = 0;
        needsRepairFlag = false;
        previousActivityLocation = null;
        // Ensure AI movement is stopped if it was part of repair cycle
        if (basicPathAI.getStrategy() instanceof net.natga999.wynn_ai.strategies.GeneralPurposeTravelStrategy) {
            basicPathAI.stop();
        }
    }


    private void checkForRepairNeeds(ClientPlayerEntity player) {
        needsRepairFlag = false;
        for (int i = 0; i < player.getInventory().size(); i++) {
            ItemStack stack = player.getInventory().getStack(i);
            if (!stack.isEmpty() && stack.getItem() instanceof ToolItem) { // Check for ToolItem or specific custom tool base
                if (stack.isDamageable() && (stack.getMaxDamage() - stack.getDamage()) < toolDurabilityThreshold) {
                    LOGGER.info("Tool {} needs repair. Durability: {}/{}", stack.getName().getString(), stack.getMaxDamage() - stack.getDamage(), stack.getMaxDamage());
                    needsRepairFlag = true;
                    return; // Found one tool, no need to check further
                }
            }
        }
    }

    private Vec3d findClosestRepairLocation(Vec3d currentPos, String worldId) {
        // Option 1: Use RoadNodes of a specific type
        List<RoadNode> repairNodes = roadNetworkManager.getNodesByType(repairStationRoadNodeType);
        if (!repairNodes.isEmpty()) {
            return repairNodes.stream()
                    .filter(node -> node.getWorldId().equals(worldId) && node.getPosition() != null)
                    .min(Comparator.comparingDouble(node -> node.getPosition().distanceTo(currentPos)))
                    .map(RoadNode::getPosition)
                    .orElse(null);
        }

        // Option 2: Fallback to manually configured coordinates (if any)
        if (!knownRepairStationCoordinates.isEmpty()) {
            return knownRepairStationCoordinates.stream()
                    .min(Comparator.comparingDouble(vec -> vec.distanceTo(currentPos)))
                    .orElse(null);
        }

        LOGGER.warn("No repair stations defined (neither by RoadNode type '{}' nor by manual coordinates).", repairStationRoadNodeType);
        return null;
    }

    private boolean simulateRepairInteraction(MinecraftClient client) {
        // Placeholder: In a real scenario, this would involve:
        // - Raycasting for an NPC/block
        // - Sending interaction packets (e.g., client.interactionManager.interactEntity)
        // - Opening and navigating a GUI
        client.player.sendMessage(Text.literal("Simulating repair interaction..."), false);
        LOGGER.info("Simulating repair interaction at {}. Waiting...", client.player.getBlockPos());
        // For now, just return true and let the INTERACTING_REPAIR state handle the delay
        return true;
    }

    public boolean isRepairCycleActive() {
        return currentState != RepairState.IDLE && currentState != RepairState.COOLDOWN_SUCCESS;
    }
}