package net.natga999.wynn_ai.managers;

import net.natga999.wynn_ai.ai.BasicPathAI;
import net.natga999.wynn_ai.path.LongDistancePathPlanner;
import net.natga999.wynn_ai.path.network.RoadNetworkManager;
import net.natga999.wynn_ai.path.network.RoadNode;

import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.screen.Screen; // For screen checks
// import some.package.from.wynncraft.WynncraftRepairMenuScreen; // <<< YOU NEED TO FIND THIS CLASS
import net.minecraft.client.network.ClientPlayerEntity;
import net.minecraft.entity.Entity;
import net.minecraft.entity.LivingEntity;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items; // For comparing with Items.POTION
import net.minecraft.screen.ScreenHandler;
import net.minecraft.screen.slot.SlotActionType;
import net.minecraft.text.Text;
import net.minecraft.util.Hand;
import net.minecraft.util.math.Vec3d;
import net.minecraft.component.type.LoreComponent;
import net.minecraft.component.DataComponentTypes;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.function.Predicate;
import java.util.regex.Pattern;
import java.util.regex.Matcher;

//TODO clean this class, rewrite loggers
public class RepairStateManager {
    private static final Logger LOGGER = LoggerFactory.getLogger(RepairStateManager.class);
    private static final RepairStateManager INSTANCE = new RepairStateManager();

    private final BasicPathAI basicPathAI;
    private final LongDistancePathPlanner longDistancePathPlanner;
    private final RoadNetworkManager roadNetworkManager;

    // Configuration
    private int toolDurabilityThreshold = 5;
    private List<Vec3d> knownRepairStationCoordinates = new ArrayList<>();
    private String repairStationRoadNodeType = "REPAIR_STATION_ADJACENT";
    private String repairNpcNameOrType = "Armorer"; // Example: Name to look for
    private double repairNpcInteractionReach = 4.5;
    private String repairInitiatorItemName = "Repair Items"; // Text on the "potion" like item
    private int repairCheckIntervalTicks = 20 * 10;
    private int mainStateTimeoutTicks = 20 * 60; // Timeout for main states like PATHING_TO_REPAIR
    private int postRepairCooldownTicks = 20 * 5;
    private static final int SUB_STATE_TIMEOUT_TICKS = 20 * 5; // 5 seconds timeout for each GUI sub-step

    private RepairState currentState = RepairState.IDLE;
    private boolean needsRepairFlag = false;
    private Vec3d previousActivityLocation = null;
    private boolean wasHarvestingActive = false;
    private int currentTickCounter = 0; // For main state timeouts/intervals

    private static final Pattern DURABILITY_PATTERN = Pattern.compile(".*?§8\\[(\\d+)/(\\d+) Durability\\].*");

    private enum RepairState {
        IDLE,
        PATHING_TO_REPAIR,
        AT_REPAIR_STATION,      // Reached location, initiating interaction with NPC
        INTERACTING_REPAIR_GUI, // NPC interaction successful, menu is open, handling GUI steps
        PATHING_TO_WORK,
        COOLDOWN_ERROR,
        COOLDOWN_SUCCESS
    }

    private enum RepairInteractionSubState {
        IDLE, // Initial state or after completion/error
        LOCATING_NPC,
        CLICKED_NPC_WAITING_FOR_MENU,
        LOCATING_REPAIR_INITIATOR_ITEM, // e.g., "Repair Items" potion
        CLICKING_REPAIR_INITIATOR_ITEM,
        LOCATING_TOOL_IN_MENU,
        CLICKING_TOOL_TO_REPAIR,
        VERIFYING_REPAIR_COMPLETION,
        CLOSING_MENU
    }
    private RepairInteractionSubState interactionSubState = RepairInteractionSubState.IDLE;
    private int interactionTickCounter = 0; // For sub-state timeouts

    private ItemStack toolToRepairLastAttempt = ItemStack.EMPTY;
    private Vec3d currentTargetRepairStationVec3d = null; // Store the specific repair station we are pathing to

    private RepairStateManager() {
        this.basicPathAI = BasicPathAI.getInstance();
        this.longDistancePathPlanner = LongDistancePathPlanner.getInstance();
        this.roadNetworkManager = RoadNetworkManager.getInstance();
    }

    public static RepairStateManager getInstance() {
        return INSTANCE;
    }

    public void tick(MinecraftClient client) {
        if (client.player == null || client.world == null) {
            if (currentState != RepairState.IDLE) {
                LOGGER.error("Player or world is null, resetting RepairState to IDLE.");
                resetToIdle();
            }
            return;
        }
        currentTickCounter++;
        interactionTickCounter++; // This counter is for sub-states, might also be used for main state INTERACTING_REPAIR_GUI

        switch (currentState) {
            case IDLE:
                if (currentTickCounter >= repairCheckIntervalTicks) {
                    publicCheckForRepairNeeds(client.player, false); // Internal check, no chat spam
                    if (needsRepairFlag) {
                        LOGGER.error("Tools need repair. Initiating repair cycle.");
                        previousActivityLocation = client.player.getPos();
                        wasHarvestingActive = HarvestPathManager.getInstance().isActive();
                        if (wasHarvestingActive) HarvestPathManager.getInstance().setActive(false);
                        basicPathAI.stop();
                        transitionToState(RepairState.PATHING_TO_REPAIR);
                    } else {
                        currentTickCounter = 0;
                    }
                }
                break;

            case PATHING_TO_REPAIR:
                if (basicPathAI.getStrategy() == null || basicPathAI.getStrategy().isComplete(basicPathAI)) {
                    LOGGER.error("Arrived at repair station vicinity: {}", currentTargetRepairStationVec3d);
                    transitionToState(RepairState.AT_REPAIR_STATION);
                    interactionSubState = RepairInteractionSubState.LOCATING_NPC; // Start NPC location process
                } else if (currentTickCounter > mainStateTimeoutTicks) {
                    LOGGER.error("Timeout while pathing to repair station. Aborting.");
                    basicPathAI.stop();
                    transitionToState(RepairState.COOLDOWN_ERROR);
                }
                break;

            case AT_REPAIR_STATION:
                handleAtRepairStationState(client);
                break;

            case INTERACTING_REPAIR_GUI:
                handleInteractingRepairGuiState(client);
                break;

            case PATHING_TO_WORK:
                if (basicPathAI.getStrategy() == null || basicPathAI.getStrategy().isComplete(basicPathAI)) {
                    LOGGER.error("Arrived back at previous work location.");
                    if (wasHarvestingActive) HarvestPathManager.getInstance().setActive(true);
                    transitionToState(RepairState.COOLDOWN_SUCCESS);
                } else if (currentTickCounter > mainStateTimeoutTicks) {
                    LOGGER.error("Timeout while pathing back to work. Aborting.");
                    basicPathAI.stop();
                    transitionToState(RepairState.COOLDOWN_ERROR); // Or just IDLE
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

    private void handleAtRepairStationState(MinecraftClient client) {
        switch (interactionSubState) {
            case LOCATING_NPC:
                Entity npc = findRepairNpc(client, repairNpcNameOrType, repairNpcInteractionReach);
                if (npc != null) {
                    LOGGER.error("Found repair NPC: {}. Attempting interaction.", npc.getDisplayName().getString());
                    BasicPathAI.rotateCameraToward(npc.getEyePos(), client, true);
                    client.interactionManager.interactEntity(client.player, npc, Hand.MAIN_HAND);
                    interactionSubState = RepairInteractionSubState.CLICKED_NPC_WAITING_FOR_MENU;
                    interactionTickCounter = 0;
                } else if (interactionTickCounter > SUB_STATE_TIMEOUT_TICKS) {
                    LOGGER.error("Failed to find repair NPC '{}' near {} after timeout. Aborting repair.", repairNpcNameOrType, client.player.getPos());
                    transitionToState(RepairState.COOLDOWN_ERROR);
                }
                break;

            case CLICKED_NPC_WAITING_FOR_MENU:
                if (isExpectedRepairMenuOpen(client)) {
                    LOGGER.error("Repair menu detected. Proceeding to GUI interaction.");
                    transitionToState(RepairState.INTERACTING_REPAIR_GUI); // Move to main state for GUI
                    interactionSubState = RepairInteractionSubState.LOCATING_REPAIR_INITIATOR_ITEM; // Set sub-state for next phase
                    interactionTickCounter = 0;
                } else if (interactionTickCounter > SUB_STATE_TIMEOUT_TICKS) {
                    LOGGER.error("Repair menu did not open after interacting with NPC. Timeout.");
                    transitionToState(RepairState.COOLDOWN_ERROR);
                }
                break;

            default:
                LOGGER.error("Unexpected interactionSubState {} in AT_REPAIR_STATION. Resetting.", interactionSubState);
                interactionSubState = RepairInteractionSubState.LOCATING_NPC; // Restart process for this station
                interactionTickCounter = 0;
                break;
        }
    }

    private void handleInteractingRepairGuiState(MinecraftClient client) {
        if (client.player == null || client.currentScreen == null || !(client.currentScreen instanceof net.minecraft.client.gui.screen.ingame.GenericContainerScreen)) {
            LOGGER.error("Repair menu (GenericContainerScreen) closed or invalid state. Aborting.");
            if (client.player != null && client.currentScreen != null) client.player.closeHandledScreen();
            transitionToState(RepairState.PATHING_TO_WORK);
            return;
        }

        // If the screen is closed, BUT we are NOT in the process of intentionally closing it,
        // then it's an unexpected closure (e.g., player pressed Esc, or server closed it).
        if (client.currentScreen == null || !(client.currentScreen instanceof net.minecraft.client.gui.screen.ingame.GenericContainerScreen)) {
            if (interactionSubState != RepairInteractionSubState.CLOSING_MENU) {
                // Screen closed unexpectedly, or changed to a non-repair screen
                LOGGER.error("Repair menu (GenericContainerScreen) closed or changed unexpectedly. SubState: {}. Aborting.", interactionSubState);
                // No need to call closeHandledScreen() if client.currentScreen is already null
                transitionToState(RepairState.COOLDOWN_ERROR);
                return;
            }
            // If we are in CLOSING_MENU and the screen is null, that's expected.
            // The CLOSING_MENU logic below will handle the transition.
        }

        ScreenHandler screenHandler = client.player.currentScreenHandler;
        // It's also useful to know the title of the screen for context/logging
        // String screenTitle = client.currentScreen.getTitle().getString();

        switch (interactionSubState) {
            case LOCATING_REPAIR_INITIATOR_ITEM:
                // Find the "Repair Items" initiator IN THE MENU SLOTS
                // You need to know its name or some unique property as it appears in the menu.
                int initiatorSlot = findItemSlotInMenu(screenHandler, stack ->
                                !stack.isEmpty() && // Ensure it's not an empty slot
                                        // stack.isOf(Items.POTION) && // If you know its base item type
                                        stack.getName().getString().contains(repairInitiatorItemName) // Check by name
                        // Potentially add more checks: specific lore, enchantments if it's unique
                );

                if (initiatorSlot != -1) {
                    LOGGER.info("'{}' item found in repair menu at slot {}. Clicking.", repairInitiatorItemName, initiatorSlot);
                    clickMenuSlot(client, screenHandler.syncId, initiatorSlot);
                    interactionSubState = RepairInteractionSubState.CLICKING_REPAIR_INITIATOR_ITEM;
                    interactionTickCounter = 0;
                } else if (interactionTickCounter > SUB_STATE_TIMEOUT_TICKS) {
                    LOGGER.error("'{}' item not found in repair menu. Timeout.", repairInitiatorItemName);
                    client.player.closeHandledScreen();
                    transitionToState(RepairState.COOLDOWN_ERROR);
                }
                break;

            case CLICKING_REPAIR_INITIATOR_ITEM:
                if(interactionTickCounter > 5) { // Small delay
                    LOGGER.info("Clicked '{}'. Now locating tool to repair from player's inventory to identify it.", repairInitiatorItemName);
                    // First, identify WHICH tool from the player's inventory needs repair.
                    storeToolThatNeedsRepair(client.player); // This sets toolToRepairLastAttempt from inventory
                    if (toolToRepairLastAttempt.isEmpty()) {
                        LOGGER.error("No tool identified in player inventory as needing repair. Aborting.");
                        client.player.closeHandledScreen();
                        transitionToState(RepairState.COOLDOWN_ERROR);
                        break;
                    }
                    interactionSubState = RepairInteractionSubState.LOCATING_TOOL_IN_MENU;
                    interactionTickCounter = 0;
                }
                break;

            case LOCATING_TOOL_IN_MENU:
                // Now, find the REPRESENTATION of toolToRepairLastAttempt in the MENU's slots.
                // The item in the menu might not be ItemStack.areItemsAndComponentsEqual to the inventory one.
                // You might need to match by item type and name, or other distinct properties.
                if (toolToRepairLastAttempt.isEmpty()) { // Should have been set in previous state
                    LOGGER.error("toolToRepairLastAttempt is empty in LOCATING_TOOL_IN_MENU. This shouldn't happen.");
                    client.player.closeHandledScreen();
                    transitionToState(RepairState.COOLDOWN_ERROR);
                    break;
                }

                LOGGER.info("Attempting to locate representation of '{}' in the repair menu.", toolToRepairLastAttempt.getName().getString());
                int toolSlotInMenu = findItemSlotInMenu(screenHandler, menuItemStack -> {
                    if (menuItemStack.isEmpty()) return false;
                    // How to match:
                    // 1. Is it the same item type?
                    boolean itemTypeMatch = menuItemStack.isOf(toolToRepairLastAttempt.getItem());
                    // 2. Does the name match? (Wynncraft names can be complex with colors)
                    boolean nameMatch = menuItemStack.getName().getString().equals(toolToRepairLastAttempt.getName().getString());
                    // 3. Does it have durability lore showing it's damaged? (Optional, but good)
                    boolean isDamagedInMenu = false;
                    LoreComponent lore = menuItemStack.get(DataComponentTypes.LORE);
                    if (lore != null) {
                        for (Text line : lore.lines()) {
                            Matcher matcher = DURABILITY_PATTERN.matcher(line.getString());
                            if (matcher.matches()) {
                                // Optional: check if its current durability in menu implies it's the one.
                                // int menuDurability = Integer.parseInt(matcher.group(1));
                                isDamagedInMenu = true; // Found a durability line
                                break;
                            }
                        }
                    }
                    // Add more specific checks if needed based on Wynncraft item properties
                    return itemTypeMatch && nameMatch && isDamagedInMenu; // Example matching criteria
                });

                if (toolSlotInMenu != -1) {
                    LOGGER.info("Representation of tool {} found in repair menu at slot {}. Clicking.", toolToRepairLastAttempt.getName().getString(), toolSlotInMenu);
                    clickMenuSlot(client, screenHandler.syncId, toolSlotInMenu); // Click THIS slot
                    interactionSubState = RepairInteractionSubState.CLICKING_TOOL_TO_REPAIR;
                    interactionTickCounter = 0;
                    // currentToolRepairRetryCount should be reset here because we're starting a new tool interaction
                    //currentToolRepairRetryCount = 0;
                } else if (interactionTickCounter > SUB_STATE_TIMEOUT_TICKS) {
                    LOGGER.error("Representation of tool {} not found in repair menu after clicking initiator. Timeout.", toolToRepairLastAttempt.getName().getString());
                    client.player.closeHandledScreen();
                    transitionToState(RepairState.COOLDOWN_ERROR);
                }
                break;

            case CLICKING_TOOL_TO_REPAIR:
                if (interactionTickCounter > 10) { // Wait 0.5s for repair to (hopefully) process
                    LOGGER.error("Clicked tool in menu. Verifying repair.");
                    interactionSubState = RepairInteractionSubState.VERIFYING_REPAIR_COMPLETION;
                    interactionTickCounter = 0;
                }
                break;

            case VERIFYING_REPAIR_COMPLETION:
                boolean repaired = checkSpecificToolRepair(client.player, toolToRepairLastAttempt);
                if (repaired) {
                    LOGGER.error("Tool {} successfully repaired!", toolToRepairLastAttempt.getName().getString());
                    // Check if other tools still need repair, or if this was the only one.
                    publicCheckForRepairNeeds(client.player, false); // Re-check all tools
                    if (!this.needsRepairFlag) {
                        LOGGER.error("All tools now repaired or above threshold.");
                        interactionSubState = RepairInteractionSubState.CLOSING_MENU;
                    } else {
                        LOGGER.error("Other tools still need repair. Restarting item location in menu.");
                        // This will loop back to find the next tool needing repair.
                        // Ensure toolToRepairLastAttempt is updated by storeToolThatNeedsRepair.
                        storeToolThatNeedsRepair(client.player); // Find next tool
                        if (toolToRepairLastAttempt.isEmpty()) { // No more tools found by that method
                            LOGGER.error("No more repairable tools found by storeToolThatNeedsRepair. Closing menu.");
                            interactionSubState = RepairInteractionSubState.CLOSING_MENU;
                        } else {
                            interactionSubState = RepairInteractionSubState.LOCATING_TOOL_IN_MENU; // Find next tool
                        }
                    }
                    interactionTickCounter = 0;
                } else if (interactionTickCounter > SUB_STATE_TIMEOUT_TICKS) {
                    LOGGER.error("Tool {} repair could not be verified. Timeout.", toolToRepairLastAttempt.getName().getString());
                    interactionSubState = RepairInteractionSubState.CLOSING_MENU; // Close menu even on failure
                }
                break;

            case CLOSING_MENU:
                // This sub-state is responsible for handling the screen closure and transitioning.
                // It might have already called client.player.closeHandledScreen() on a previous tick.
                LOGGER.error("In CLOSING_MENU sub-state. Current screen is: {}", client.currentScreen != null ? client.currentScreen.getClass().getName() : "null");

                // Attempt to close the screen if it's not already null and if this is the first effective tick in this state
                if (client.currentScreen != null && interactionTickCounter <= 1) { // Only try to close once or twice at the start of this state
                    LOGGER.error("Attempting to close repair menu (tick: {}).", interactionTickCounter);
                    client.player.closeHandledScreen();
                }

                // Ensure screen is actually closed before transitioning.
                // Wait a few ticks for the screen to fully disappear.
                if (client.currentScreen == null) {
                    if (interactionTickCounter > 5) { // Wait a brief moment after screen becomes null
                        LOGGER.error("Screen confirmed closed. NeedsRepairFlag: {}", this.needsRepairFlag);
                        if (this.needsRepairFlag) { // If, after attempting all, some still need repair
                            LOGGER.error("Some tools still need repair after attempting. Transitioning to COOLDOWN_ERROR.");
                            transitionToState(RepairState.COOLDOWN_ERROR);
                        } else {
                            LOGGER.error("All necessary repairs complete. Transitioning to PATHING_TO_WORK.");
                            transitionToState(RepairState.PATHING_TO_WORK); // Success
                        }
                        // Return here to prevent falling through to the default case if something unexpected happens next tick
                        return;
                    }
                    // else, screen is null, but we're still in the short wait period.
                } else {
                    // Screen isn't closed yet, try to close it again or just wait.
                    // This could happen if closeHandledScreen() was called but didn't take effect immediately,
                    // or if this is the first tick of CLOSING_MENU.
                    if (interactionTickCounter == 0) { // First tick in this state
                        LOGGER.error("Attempting to close repair menu.");
                        client.player.closeHandledScreen();
                    } else if (interactionTickCounter > SUB_STATE_TIMEOUT_TICKS) {
                        LOGGER.error("Screen did not close within timeout. Forcing transition based on needsRepairFlag.");
                        if (this.needsRepairFlag) transitionToState(RepairState.COOLDOWN_ERROR);
                        else transitionToState(RepairState.PATHING_TO_WORK);
                        return; // Prevent fall-through
                    }
                    // else, still waiting for screen to close or timeout.
                }
                break; // End of CLOSING_MENU

            default:
                // All other sub-states assume the screen is open and valid.
                // If by some logic error we get here and the screen is null, that's an issue.
                if (client.currentScreen == null) {
                    LOGGER.error("CRITICAL: Reached default in GUI interaction but screen is null. SubState: {}. Aborting.", interactionSubState);
                    transitionToState(RepairState.COOLDOWN_ERROR);
                    return;
                }
                // ... (original default case for unexpected interactionSubState when screen IS open)
                LOGGER.error("Unexpected interactionSubState {} in INTERACTING_REPAIR_GUI (screen open). Resetting.", interactionSubState);
                if (client.player != null && client.currentScreen != null) client.player.closeHandledScreen();
                transitionToState(RepairState.COOLDOWN_ERROR);
                break;
        }
    }


    private void transitionToState(RepairState newState) {
        LOGGER.error("RepairState transitioning from {} to {}", currentState, newState);
        currentState = newState;
        currentTickCounter = 0; // Reset main state tick counter

        // Reset interaction sub-state machine whenever we leave an interaction phase
        if (newState != RepairState.AT_REPAIR_STATION && newState != RepairState.INTERACTING_REPAIR_GUI) {
            interactionSubState = RepairInteractionSubState.IDLE;
            toolToRepairLastAttempt = ItemStack.EMPTY;
        }
        interactionTickCounter = 0; // Always reset sub-state counter on main state change

        MinecraftClient client = MinecraftClient.getInstance();
        if (client.player == null || client.world == null) return;

        switch (newState) {
            case PATHING_TO_REPAIR:
                currentTargetRepairStationVec3d = findClosestRepairLocation(client.player.getPos(), client.player.getWorld().getRegistryKey().getValue().toString());
                if (currentTargetRepairStationVec3d == null) {
                    LOGGER.error("No repair station found! Cannot path to repair.");
                    transitionToState(RepairState.COOLDOWN_ERROR);
                    return;
                }
                List<Vec3d> pathToRepair = longDistancePathPlanner.planPathToGoal(client.player.getPos(), currentTargetRepairStationVec3d, client.world);
                if (pathToRepair != null && !pathToRepair.isEmpty()) {
                    basicPathAI.startGeneralPath(pathToRepair);
                } else {
                    LOGGER.error("Failed to plan path to repair station {}.", currentTargetRepairStationVec3d);
                    client.player.sendMessage(Text.literal("Failed to path to repair station!"), false);
                    transitionToState(RepairState.COOLDOWN_ERROR);
                }
                break;
            case PATHING_TO_WORK:
                if (previousActivityLocation == null) {
                    LOGGER.error("No previous activity location to return to. Idling.");
                    resetToIdle(); return;
                }
                List<Vec3d> pathToWork = longDistancePathPlanner.planPathToGoal(client.player.getPos(), previousActivityLocation, client.world);
                if (pathToWork != null && !pathToWork.isEmpty()) {
                    basicPathAI.startGeneralPath(pathToWork);
                } else {
                    //TODO sometimes failing find, add retry to go, maybe just restart harvest strategy
                    LOGGER.error("Failed to plan path back to work location {}.", previousActivityLocation);
                    client.player.sendMessage(Text.literal("Failed to path back to work!"), false);
                    transitionToState(RepairState.COOLDOWN_SUCCESS);
                }
                break;
            case AT_REPAIR_STATION:
                interactionSubState = RepairInteractionSubState.LOCATING_NPC; // Set initial sub-state
                interactionTickCounter = 0;
                break;
        }
    }

    private void resetToIdle() {
        LOGGER.info("RepairStateManager resetting to IDLE state.");
        currentState = RepairState.IDLE;
        interactionSubState = RepairInteractionSubState.IDLE;
        currentTickCounter = 0;
        interactionTickCounter = 0;
        needsRepairFlag = false;
        previousActivityLocation = null;
        toolToRepairLastAttempt = ItemStack.EMPTY;
        currentTargetRepairStationVec3d = null;
        if (basicPathAI.getStrategy() != null) { // Stop AI if it was doing anything related to us
            //basicPathAI.stop();
        }
    }

    public void publicCheckForRepairNeeds(ClientPlayerEntity player, boolean sendFeedbackToPlayer) {
        // ... (implementation from previous message) ...
        // This implementation needs to be robust as per our last discussion.
        // For brevity, I'm not repeating it here, but assume it's correctly implemented.
        // It sets this.needsRepairFlag.
        if (player == null) {
            if (sendFeedbackToPlayer && MinecraftClient.getInstance().player != null) {
                MinecraftClient.getInstance().player.sendMessage(Text.literal("Error: Player entity is null for repair check."), false);
            }
            LOGGER.error("Player entity is null when calling publicCheckForRepairNeeds.");
            return;
        }
        this.needsRepairFlag = false; // Reset before check
        int itemsFoundNeedingRepair = 0;
        StringBuilder feedback = sendFeedbackToPlayer ? new StringBuilder("Repair Check (Threshold: <" + toolDurabilityThreshold + "):\n") : null;

        for (int i = 0; i < player.getInventory().size(); i++) {
            ItemStack stack = player.getInventory().getStack(i);
            if (stack.isEmpty()) continue;

            LoreComponent loreComponent = stack.get(DataComponentTypes.LORE);
            if (loreComponent == null || loreComponent.lines().isEmpty()) continue;

            for (Text loreLineText : loreComponent.lines()) {
                String loreLine = loreLineText.getString();
                Matcher matcher = DURABILITY_PATTERN.matcher(loreLine);
                if (matcher.matches()) {
                    try {
                        int currentDurability = Integer.parseInt(matcher.group(1));
                        int maxDurability = Integer.parseInt(matcher.group(2));
                        if (currentDurability < toolDurabilityThreshold) {
                            this.needsRepairFlag = true;
                            itemsFoundNeedingRepair++;
                            if (sendFeedbackToPlayer) feedback.append(String.format("NEEDS REPAIR: %s (Slot %d) - %d/%d\n", stack.getName().getString(), i, currentDurability, maxDurability));
                            // No early return if collecting all feedback
                        } else if (sendFeedbackToPlayer) {
                            feedback.append(String.format("OK: %s (Slot %d) - %d/%d\n", stack.getName().getString(), i, currentDurability, maxDurability));
                        }
                        break;
                    } catch (NumberFormatException e) {LOGGER.error("Parse error in lore for {}: {}", stack.getName().getString(), e.getMessage());}
                }
            }
        }
        if (sendFeedbackToPlayer) {
            if (itemsFoundNeedingRepair == 0) feedback.append("No tools found needing repair.\n");
            feedback.append("Overall needsRepairFlag: ").append(this.needsRepairFlag);
            for(String line : feedback.toString().split("\n")) player.sendMessage(Text.literal(line), false);
        }
    }

    // --- Helper Methods for Interaction ---

    private Entity findRepairNpc(MinecraftClient client, String nameHint, double radius) {
        if (client.player == null || client.world == null) return null;
        Vec3d searchCenter = client.player.getPos(); // NPC should be near the player now

        return client.world.getEntitiesByClass(LivingEntity.class, // More specific than Entity
                        client.player.getBoundingBox().expand(radius),
                        entity -> !entity.isRemoved() && entity.getDisplayName().getString().contains(nameHint) // Basic name check
                ).stream()
                .min(Comparator.comparingDouble(e -> e.getPos().squaredDistanceTo(searchCenter)))
                .orElse(null);
    }

    private boolean isExpectedRepairMenuOpen(MinecraftClient client) {
        if (client.currentScreen == null) return false;
        // *** Replace with actual Wynncraft repair menu screen class name ***
        // Example: return client.currentScreen instanceof com.wynncraft.client.gui.RepairScreen;
        LOGGER.error("Current screen: {}", client.currentScreen.getClass().getName());
        // For now, let's assume any non-null screen after interaction is a step forward.
        // This is a placeholder and needs to be specific.
        // A common approach is to check against a known title or class.
        // If you don't know the class, you can check the screen title:
        // return client.currentScreen.getTitle().getString().contains("Repair"); // Example title check
        return true; // Placeholder - VERY IMPORTANT TO REPLACE
    }

    private void storeToolThatNeedsRepair(ClientPlayerEntity player) {
        this.toolToRepairLastAttempt = ItemStack.EMPTY; // Reset
        for (int i = 0; i < player.getInventory().size(); i++) {
            ItemStack stack = player.getInventory().getStack(i);
            if (stack.isEmpty()) continue;

            LoreComponent loreComponent = stack.get(DataComponentTypes.LORE);
            if (loreComponent != null && !loreComponent.lines().isEmpty()) {
                for (Text loreLineText : loreComponent.lines()) {
                    Matcher matcher = DURABILITY_PATTERN.matcher(loreLineText.getString());
                    if (matcher.matches()) {
                        try {
                            int currentDurability = Integer.parseInt(matcher.group(1));
                            if (currentDurability < toolDurabilityThreshold) {
                                this.toolToRepairLastAttempt = stack.copy(); // Store a copy
                                LOGGER.error("Identified {} as the tool to attempt repairing.", this.toolToRepairLastAttempt.getName().getString());
                                return; // Found the first tool needing repair
                            }
                            break;
                        } catch (NumberFormatException e) { /* Ignore */ }
                    }
                }
            }
        }
        LOGGER.error("No specific tool needing repair found in inventory to target in menu.");
    }

    private int findItemSlotInMenu(ScreenHandler handler, Predicate<ItemStack> itemPredicate) {
        if (handler == null) return -1;
        for (net.minecraft.screen.slot.Slot slot : handler.slots) {
            ItemStack stackInSlot = slot.getStack();
            if (!stackInSlot.isEmpty() && itemPredicate.test(stackInSlot)) {
                return slot.id; // slot.id is usually the index used for clickSlot
            }
        }
        return -1;
    }

    private void clickMenuSlot(MinecraftClient client, int syncId, int slotId) {
        if (client.player == null) return;
        LOGGER.error("Clicking slot {} in screen with syncId {}", slotId, syncId);
        client.interactionManager.clickSlot(syncId, slotId, 0, SlotActionType.PICKUP, client.player);
        // May need SlotActionType.QUICK_MOVE or others depending on menu behavior
    }

    private boolean checkSpecificToolRepair(ClientPlayerEntity player, ItemStack toolWeTriedToRepair) {
        if (toolWeTriedToRepair.isEmpty()) {
            LOGGER.error("checkSpecificToolRepair called with an empty toolWeTriedToRepair stack.");
            return false;
        }

        LOGGER.error("Verifying repair for tool: {}", toolWeTriedToRepair.getName().getString());

        for (int i = 0; i < player.getInventory().size(); i++) {
            ItemStack currentStackInInventory = player.getInventory().getStack(i);
            if (currentStackInInventory.isEmpty()) continue;

            // Match by item type and name
            if (currentStackInInventory.isOf(toolWeTriedToRepair.getItem()) &&
                    currentStackInInventory.getName().getString().equals(toolWeTriedToRepair.getName().getString())) {

                LOGGER.error("Found candidate item {} in player inventory slot {}. Checking its lore for updated durability.",
                        currentStackInInventory.getName().getString(), i);

                LoreComponent loreComponent = currentStackInInventory.get(DataComponentTypes.LORE);
                if (loreComponent != null && !loreComponent.lines().isEmpty()) {
                    boolean durabilityLineFoundThisItem = false;
                    for (Text loreLineText : loreComponent.lines()) {
                        String loreLine = loreLineText.getString();
                        Matcher matcher = DURABILITY_PATTERN.matcher(loreLine); // Your existing pattern

                        if (matcher.matches()) { // Or matcher.find() if it's not the whole line
                            durabilityLineFoundThisItem = true;
                            try {
                                int newDurability = Integer.parseInt(matcher.group(1));
                                int maxDurability = Integer.parseInt(matcher.group(2));

                                LOGGER.error("Found durability lore on item {} (slot {}): {}/{}",
                                        currentStackInInventory.getName().getString(), i, newDurability, maxDurability);

                                // How does Wynncraft repair work? Fully or partially?
                                // Let's assume "repaired" means it's no longer below our *initial* threshold,
                                // or ideally, it's at max durability.
                                if (newDurability >= maxDurability) { // Fully repaired
                                    LOGGER.error("Tool {} is now fully repaired ({}). Verification SUCCESS.",
                                            currentStackInInventory.getName().getString(), newDurability);
                                    return true;
                                } else if (newDurability >= toolDurabilityThreshold) {
                                    LOGGER.error("Tool {} now has durability {} (above threshold {}). Considered sufficiently repaired. Verification SUCCESS.",
                                            currentStackInInventory.getName().getString(), newDurability, toolDurabilityThreshold);
                                    return true;
                                } else {
                                    // It had a durability line, but it's still below the threshold.
                                    LOGGER.error("Tool {} has durability {} which is still below threshold {} after repair attempt. Verification FAILED for this item.",
                                            currentStackInInventory.getName().getString(), newDurability, toolDurabilityThreshold);
                                    return false; // This specific item instance is confirmed to be not repaired enough.
                                }
                            } catch (NumberFormatException e) {
                                LOGGER.error("Could not parse durability from lore line '{}' for item {} in checkSpecificToolRepair: {}",
                                        loreLine, currentStackInInventory.getName().getString(), e.getMessage());
                                // Continue to check other lore lines on this item, as this one might be malformed.
                            }
                        }
                    }
                    // If we iterated all lore lines for this specific item and found no matching durability string
                    if (!durabilityLineFoundThisItem) {
                        LOGGER.error("No valid durability lore line found on candidate tool {} (slot {}) after repair attempt.",
                                currentStackInInventory.getName().getString(), i);
                        return false; // This item instance doesn't have the expected durability lore anymore.
                    }
                } else { // No lore component on the item in inventory
                    LOGGER.error("Candidate tool {} (slot {}) in inventory has no lore component after repair attempt.",
                            currentStackInInventory.getName().getString(), i);
                    return false;
                }
            }
        }

        // If we looped through the entire inventory and didn't find the tool OR
        // found it but couldn't confirm its repair (e.g. it was still damaged, or lore was missing/changed).
        LOGGER.error("Could not find or verify repair for tool instance matching '{}' in inventory.",
                toolWeTriedToRepair.getName().getString());
        return false;
    }

    private Vec3d findClosestRepairLocation(Vec3d currentPos, String worldId) {
        // ... (implementation from previous message, ensure currentTargetRepairStationVec3d is set here if used for NPC search) ...
        List<RoadNode> repairNodes = roadNetworkManager.getNodesByType(repairStationRoadNodeType);
        if (!repairNodes.isEmpty()) {
            return repairNodes.stream()
                    .filter(node -> node.getWorldId().equals(worldId) && node.getPosition() != null)
                    .min(Comparator.comparingDouble(node -> node.getPosition().distanceTo(currentPos)))
                    .map(RoadNode::getPosition)
                    .orElse(null);
        }
        if (!knownRepairStationCoordinates.isEmpty()) {
            return knownRepairStationCoordinates.stream()
                    .min(Comparator.comparingDouble(vec -> vec.distanceTo(currentPos)))
                    .orElse(null);
        }
        LOGGER.error("No repair stations defined (neither by RoadNode type '{}' nor by manual coordinates).", repairStationRoadNodeType);
        return null;
    }

    public boolean isRepairCycleActive() {
        return currentState != RepairState.IDLE && currentState != RepairState.COOLDOWN_SUCCESS;
    }

    // Getter for test command
    public boolean getNeedsRepairFlag() {
        return needsRepairFlag;
    }
}