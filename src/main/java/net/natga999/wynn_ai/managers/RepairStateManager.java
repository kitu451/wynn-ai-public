package net.natga999.wynn_ai.managers;

import net.natga999.wynn_ai.ai.BasicPathAI;
import net.natga999.wynn_ai.path.network.RoadNetworkManager;
import net.natga999.wynn_ai.path.network.RoadNode;
import net.natga999.wynn_ai.services.NavigationService;

import net.minecraft.client.MinecraftClient;
import net.minecraft.client.network.ClientPlayerEntity;
import net.minecraft.entity.Entity;
import net.minecraft.entity.LivingEntity;
import net.minecraft.item.ItemStack;
import net.minecraft.screen.ScreenHandler;
import net.minecraft.screen.slot.SlotActionType;
import net.minecraft.text.Text;
import net.minecraft.util.Hand;
import net.minecraft.util.math.Vec3d;
import net.minecraft.component.type.LoreComponent;
import net.minecraft.component.DataComponentTypes;
import net.minecraft.client.gui.screen.ingame.GenericContainerScreen;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Comparator;
import java.util.List;
import java.util.Objects;
import java.util.function.Predicate;
import java.util.regex.Pattern;
import java.util.regex.Matcher;

public class RepairStateManager {
    private static final Logger LOGGER = LoggerFactory.getLogger(RepairStateManager.class);
    private static final RepairStateManager INSTANCE = new RepairStateManager();

    private final BasicPathAI basicPathAI;
    private final RoadNetworkManager roadNetworkManager;

    private final int toolDurabilityThreshold = 59;
    private final String repairStationRoadNodeType = "REPAIR_STATION_ADJACENT";
    private final String repairNpcNameOrType = "Armorer"; // Example: Name to look for
    private final String repairInitiatorItemName = "Repair Items"; // Text on the "potion" like item
    private final int repairCheckIntervalTicks = 20 * 10;
    private final int mainStateTimeoutTicks = 20 * 60; // Timeout for main states like PATHING_TO_REPAIR
    private final int postRepairCooldownTicks = 20 * 5;
    private static final int SUB_STATE_TIMEOUT_TICKS = 20 * 5; // 5 seconds timeout for each GUI sub-step

    private final NavigationService navigationService;
    private RepairState currentState = RepairState.IDLE;
    private boolean needsRepairFlag = false;
    private Vec3d previousActivityLocation = null;
    private boolean wasHarvestingActive = false;
    private int currentTickCounter = 0; // For main state timeouts/intervals

    private static final Pattern DURABILITY_PATTERN = Pattern.compile(".*?§8\\[(\\d+)/(\\d+) Durability].*");

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
    private int interactionTickCounter = 0; // For substate timeouts

    private ItemStack toolToRepairLastAttempt = ItemStack.EMPTY;
    private Vec3d currentTargetRepairStationVec3d = null; // Store the specific repair station we are pathing to

    private RepairStateManager() {
        this.basicPathAI = BasicPathAI.getInstance();
        this.navigationService = NavigationService.getInstance();
        this.roadNetworkManager = RoadNetworkManager.getInstance();
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
        interactionTickCounter++; // This counter is for substates, might also be used for main state INTERACTING_REPAIR_GUI

        switch (currentState) {
            case IDLE:
                if (currentTickCounter >= repairCheckIntervalTicks) {
                    publicCheckForRepairNeeds(client.player, false); // Internal check, no chat spam
                    if (needsRepairFlag) {
                        LOGGER.info("Tools need repair. Initiating repair cycle.");
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
                    LOGGER.info("Arrived at repair station vicinity: {}", currentTargetRepairStationVec3d);
                    transitionToState(RepairState.AT_REPAIR_STATION);
                    interactionSubState = RepairInteractionSubState.LOCATING_NPC; // Start NPC location process
                } else if (currentTickCounter > mainStateTimeoutTicks) {
                    LOGGER.warn("Timeout while pathing to repair station. Aborting.");
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
                    LOGGER.info("Arrived back at previous work location.");
                    if (wasHarvestingActive) HarvestPathManager.getInstance().setActive(true);
                    transitionToState(RepairState.COOLDOWN_SUCCESS);
                } else if (currentTickCounter > mainStateTimeoutTicks) {
                    LOGGER.warn("Timeout while pathing back to work. Aborting.");
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
                Entity npc = findRepairNpc(client);
                if (npc != null) {
                    LOGGER.info("Found repair NPC: {}. Attempting interaction.", Objects.requireNonNull(npc.getDisplayName()).getString());
                    //BasicPathAI.rotateCameraToward(npc.getEyePos(), client, true);
                    BasicPathAI.getInstance().rotateCameraToward(client, true);
                    assert client.interactionManager != null;
                    client.interactionManager.interactEntity(client.player, npc, Hand.MAIN_HAND);
                    interactionSubState = RepairInteractionSubState.CLICKED_NPC_WAITING_FOR_MENU;
                    interactionTickCounter = 0;
                } else if (interactionTickCounter > SUB_STATE_TIMEOUT_TICKS) {
                    assert client.player != null;
                    LOGGER.warn("Failed to find repair NPC '{}' near {} after timeout. Aborting repair.", repairNpcNameOrType, client.player.getPos());
                    transitionToState(RepairState.COOLDOWN_ERROR);
                }
                break;

            case CLICKED_NPC_WAITING_FOR_MENU:
                if (isExpectedRepairMenuOpen(client)) {
                    LOGGER.info("Repair menu detected. Proceeding to GUI interaction.");
                    transitionToState(RepairState.INTERACTING_REPAIR_GUI); // Move to main state for GUI
                    interactionSubState = RepairInteractionSubState.LOCATING_REPAIR_INITIATOR_ITEM; // Set substate for next phase
                    interactionTickCounter = 0;
                } else if (interactionTickCounter > SUB_STATE_TIMEOUT_TICKS) {
                    LOGGER.warn("Repair menu did not open after interacting with NPC. Timeout.");
                    transitionToState(RepairState.COOLDOWN_ERROR);
                }
                break;

            default:
                LOGGER.warn("Unexpected interactionSubState {} in AT_REPAIR_STATION. Resetting.", interactionSubState);
                interactionSubState = RepairInteractionSubState.LOCATING_NPC; // Restart process for this station
                interactionTickCounter = 0;
                break;
        }
    }

    private void handleInteractingRepairGuiState(MinecraftClient client) {
        // --- Initial Sanity and State Checks ---

        // 1. Critical: Player and World must exist.
        if (client.player == null || client.world == null) {
            LOGGER.warn("RepairStateManager: Player or world is null during GUI interaction. Aborting to COOLDOWN_ERROR.");
            // Attempt to close screen if player is somehow non-null but world is, and screen is open.
            if (client.player != null && client.currentScreen != null) {
                client.player.closeHandledScreen();
            }
            transitionToState(RepairState.COOLDOWN_ERROR);
            return;
        }

        // 2. Check the current screen state.
        //    - If it's the CLOSING_MENU substate, it has its own logic for handling null screens.
        //    - Otherwise (for all other GUI interaction substates), the repair screen MUST be open.
        if (interactionSubState != RepairInteractionSubState.CLOSING_MENU) {
            // For any substate EXCEPT CLOSING_MENU, we expect the repair screen to be open.
            if (!(client.currentScreen instanceof net.minecraft.client.gui.screen.ingame.GenericContainerScreen)) {
                // Screen is either null or not the correct type. This is an unexpected state.
                LOGGER.warn("RepairStateManager: Expected repair menu (GenericContainerScreen) to be open, but it's not (or is wrong type). Current screen: {}. SubState: {}. Aborting to COOLDOWN_ERROR.",
                        client.currentScreen != null ? client.currentScreen.getClass().getName() : "null",
                        interactionSubState);
                // If screen is non-null but wrong type, try to close it.
                if (client.currentScreen != null) {
                    client.player.closeHandledScreen();
                }
                transitionToState(RepairState.COOLDOWN_ERROR);
                return;
            }
            // If we are here, screen IS the GenericContainerScreen, and we are NOT in CLOSING_MENU.
        }
        // If interactionSubState IS CLOSING_MENU, we don't do the screen check here;
        // that substate's logic will handle whether the screen is null (expected) or not.

        // --- Proceed with Sub-State Logic ---
        // At this point, if not in CLOSING_MENU, client.currentScreen is a GenericContainerScreen.
        // If in CLOSING_MENU, client.currentScreen might be null or the GenericContainerScreen.
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
                    LOGGER.warn("'{}' item not found in repair menu. Timeout.", repairInitiatorItemName);
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
                        LOGGER.info("No tool identified in player inventory as needing repair. Aborting.");
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
                    LOGGER.warn("toolToRepairLastAttempt is empty in LOCATING_TOOL_IN_MENU. This shouldn't happen.");
                    client.player.closeHandledScreen();
                    transitionToState(RepairState.COOLDOWN_ERROR);
                    break;
                }

                LOGGER.debug("Attempting to locate representation of '{}' in the repair menu.", toolToRepairLastAttempt.getName().getString());
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
                    //currentToolRepairRetryCount should be reset here because we're starting a new tool interaction
                    //currentToolRepairRetryCount = 0;
                } else if (interactionTickCounter > SUB_STATE_TIMEOUT_TICKS) {
                    LOGGER.warn("Representation of tool {} not found in repair menu after clicking initiator. Timeout.", toolToRepairLastAttempt.getName().getString());
                    client.player.closeHandledScreen();
                    transitionToState(RepairState.COOLDOWN_ERROR);
                }
                break;

            case CLICKING_TOOL_TO_REPAIR:
                if (interactionTickCounter > 10) { // Wait 0.5s for repair to (hopefully) process
                    LOGGER.debug("Clicked tool in menu. Verifying repair.");
                    interactionSubState = RepairInteractionSubState.VERIFYING_REPAIR_COMPLETION;
                    interactionTickCounter = 0;
                }
                break;

            case VERIFYING_REPAIR_COMPLETION:
                boolean repaired = checkSpecificToolRepair(client.player, toolToRepairLastAttempt);
                if (repaired) {
                    LOGGER.info("Tool {} successfully repaired!", toolToRepairLastAttempt.getName().getString());
                    // Check if other tools still need repair, or if this was the only one.
                    publicCheckForRepairNeeds(client.player, false); // Re-check all tools
                    if (!this.needsRepairFlag) {
                        LOGGER.info("All tools now repaired or above threshold.");
                        interactionSubState = RepairInteractionSubState.CLOSING_MENU;
                    } else {
                        LOGGER.info("Other tools still need repair. Restarting item location in menu.");
                        // This will loop back to find the next tool needing repair.
                        // Ensure toolToRepairLastAttempt is updated by storeToolThatNeedsRepair.
                        storeToolThatNeedsRepair(client.player); // Find next tool
                        if (toolToRepairLastAttempt.isEmpty()) { // No more tools found by that method
                            LOGGER.info("No more repairable tools found by storeToolThatNeedsRepair. Closing menu.");
                            interactionSubState = RepairInteractionSubState.CLOSING_MENU;
                        } else {
                            interactionSubState = RepairInteractionSubState.LOCATING_TOOL_IN_MENU; // Find next tool
                        }
                    }
                    interactionTickCounter = 0;
                } else if (interactionTickCounter > SUB_STATE_TIMEOUT_TICKS) {
                    LOGGER.warn("Tool {} repair could not be verified. Timeout.", toolToRepairLastAttempt.getName().getString());
                    interactionSubState = RepairInteractionSubState.CLOSING_MENU; // Close menu even on failure
                }
                break;

            case CLOSING_MENU:
                // Log the current screen state carefully, handling null
                LOGGER.info("In CLOSING_MENU sub-state. Tick: {}. Current screen: {}",
                        interactionTickCounter,
                        (client.currentScreen != null ? client.currentScreen.getClass().getName() : "null")); // Safely log screen state

                // Attempt to close the screen if it's not already null AND
                // if this is an early tick in this substate (e.g., the first time we enter CLOSING_MENU)
                if (client.currentScreen != null && interactionTickCounter <= 1) {
                    LOGGER.info("Attempting to close repair menu (interactionTick: {}).", interactionTickCounter);
                    client.player.closeHandledScreen();
                    // After calling close, it might not be null until the next tick.
                    // So we typically wait for the next tick to check client.currentScreen == null.
                }

                // Check if the screen has actually closed
                if (client.currentScreen == null) {
                    // Screen is now confirmed to be closed
                    if (interactionTickCounter > 5) { // Wait a few more ticks for things to settle client-side
                        LOGGER.info("Screen confirmed closed. NeedsRepairFlag: {}", this.needsRepairFlag);
                        if (this.needsRepairFlag) { // Check if any tools still need repair after all attempts
                            LOGGER.warn("Some tools still need repair after attempting closure. Transitioning to COOLDOWN_ERROR.");
                            transitionToState(RepairState.COOLDOWN_ERROR);
                        } else {
                            LOGGER.info("All necessary repairs complete and menu closed. Transitioning to PATHING_TO_WORK.");
                            transitionToState(RepairState.PATHING_TO_WORK);
                        }
                        return; // Exit handleInteractingRepairGuiState as we've transitioned main state
                    }
                    // else, screen is null, but we're still in the short post-closure grace period.
                    // interactionTickCounter will increment, and this block will be re-evaluated.
                } else if (interactionTickCounter > SUB_STATE_TIMEOUT_TICKS) {
                    // Screen is NOT null, and we've timed out waiting for it to close.
                    LOGGER.warn("Screen did not close within timeout ({} ticks) despite being in CLOSING_MENU. Forcing transition based on needsRepairFlag.", SUB_STATE_TIMEOUT_TICKS);
                    if (this.needsRepairFlag) {
                        transitionToState(RepairState.COOLDOWN_ERROR);
                    } else {
                        transitionToState(RepairState.PATHING_TO_WORK);
                    }
                    return; // Exit handleInteractingRepairGuiState
                }
                // else, screen is not yet null (and not timed out), or it is null but in grace period.
                // The loop continues, interactionTickCounter increments.
                break; // End of CLOSING_MENU

            default:
                // All other substates assume the screen is open and valid.
                // If by some logic error we get here and the screen is null, that's an issue.
                // ... (original default case for unexpected interactionSubState when screen IS open)
                LOGGER.warn("Unexpected interactionSubState {} in INTERACTING_REPAIR_GUI (screen open). Resetting.", interactionSubState);
                if (client.player != null && client.currentScreen != null) client.player.closeHandledScreen();
                transitionToState(RepairState.COOLDOWN_ERROR);
                break;
        }
    }


    private void transitionToState(RepairState newState) {
        LOGGER.info("RepairState transitioning from {} to {}", currentState, newState);
        currentState = newState;
        currentTickCounter = 0; // Reset main state tick counter

        // Reset interaction substate machine whenever we leave an interaction phase
        if (newState != RepairState.AT_REPAIR_STATION && newState != RepairState.INTERACTING_REPAIR_GUI) {
            interactionSubState = RepairInteractionSubState.IDLE;
            toolToRepairLastAttempt = ItemStack.EMPTY;
        }
        interactionTickCounter = 0; // Always reset substate counter on main state change

        MinecraftClient client = MinecraftClient.getInstance();
        if (client.player == null || client.world == null) return;

        switch (newState) {
            case PATHING_TO_REPAIR:
                currentTargetRepairStationVec3d = findClosestRepairLocation(client.player.getPos(), client.player.getWorld().getRegistryKey().getValue().toString());
                if (currentTargetRepairStationVec3d == null) {
                    LOGGER.warn("No repair station found! Cannot path to repair.");
                    transitionToState(RepairState.COOLDOWN_ERROR);
                    return;
                }

                LOGGER.info("Attempting to navigate to repair station: {}", currentTargetRepairStationVec3d);
                NavigationService.TravelPlan planToRepair = navigationService.planJourneyTo(currentTargetRepairStationVec3d);

                if (planToRepair.planSuccess) {
                    //TODO change to startHighwaySplinePath, but clear waypoints between tunnel nodes
                    basicPathAI.startGeneralPath(planToRepair.waypoints);
                } else {
                    LOGGER.warn("Failed to plan path to repair station {}.", currentTargetRepairStationVec3d);
                    client.player.sendMessage(Text.literal("Failed to path to repair station!"), false);
                    transitionToState(RepairState.COOLDOWN_ERROR);
                }
                break;
            case PATHING_TO_WORK:
                if (previousActivityLocation == null) {
                    LOGGER.info("No previous activity location to return to. Idling.");
                    resetToIdle();
                    return;
                }

                LOGGER.info("Attempting to navigate back to work location: {}", previousActivityLocation);
                NavigationService.TravelPlan planToWork = navigationService.planJourneyTo(previousActivityLocation);

                if (planToWork.planSuccess) {
                    //TODO change to startHighwaySplinePath, but clear waypoints between tunnel nodes
                    basicPathAI.startGeneralPath(planToWork.waypoints);
                } else {
                    //TODO sometimes failing find, add retry to go, maybe just restart harvest strategy
                    LOGGER.warn("Failed to plan path back to work location {}.", previousActivityLocation);
                    client.player.sendMessage(Text.literal("Failed to path back to work!"), false);
                    transitionToState(RepairState.COOLDOWN_SUCCESS);
                }
                break;
            case AT_REPAIR_STATION:
                interactionSubState = RepairInteractionSubState.LOCATING_NPC; // Set initial substate
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
        /*
        if (basicPathAI.getStrategy() != null) { // Stop AI if it was doing anything related to us
            //basicPathAI.stop();
        }
        **/
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
            LOGGER.warn("Player entity is null when calling publicCheckForRepairNeeds.");
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
                            feedback.append(String.format("OK:   %s (Slot %d) - %d/%d\n", stack.getName().getString(), i, currentDurability, maxDurability));
                        }
                        break;
                    } catch (NumberFormatException e) {LOGGER.warn("Parse error in lore for {}: {}", stack.getName().getString(), e.getMessage());}
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

    private Entity findRepairNpc(MinecraftClient client) {
        if (client.player == null || client.world == null) return null;
        Vec3d searchCenter = client.player.getPos(); // NPC should be near the player now

        return client.world.getEntitiesByClass(LivingEntity.class,
                        client.player.getBoundingBox().expand(4.5),
                        entity -> !entity.isRemoved() && Objects.requireNonNull(entity.getDisplayName()).getString().contains("Armorer")
                ).stream()
                .min(Comparator.comparingDouble(e -> e.getPos().squaredDistanceTo(searchCenter)))
                .orElse(null);
    }

    private boolean isExpectedRepairMenuOpen(MinecraftClient client) {
        if (client.currentScreen == null) {
            return false;
        }

        // Check if it's the correct class instance
        if (client.currentScreen instanceof GenericContainerScreen) {
            // OPTIONAL: Add a title check for more specificity if GenericContainerScreen
            // is used for many different things in Wynncraft.
            // You'll need to manually open the repair menu and see its exact title.
            Text screenTitle = client.currentScreen.getTitle();
            if (screenTitle != null) {
                String titleString = screenTitle.getString();
                LOGGER.info("GenericContainerScreen open with title: {} - should be \uDAFF\uDFF8\uE016 for (Blacksmith)", titleString); // Log title for first-time check
                // Example: Replace "󏿸" with the actual title of the Wynncraft repair menu
                if (titleString.contains("\uDAFF\uDFF8\uE016")) {
                    return true; // It's a GenericContainerScreen AND has the expected title
                } else {
                    // It's a GenericContainerScreen, but not the one we expect for repair.
                    // This might happen if another mod uses GenericContainerScreen or if Wynncraft
                    // uses it for other UIs that might coincidentally open.
                    // For now, if you are confident this is the *only* relevant GenericContainerScreen
                    // that opens after NPC interaction, you might omit the title check or make it less strict.
                    // However, for robustness, a title check is good.
                    LOGGER.warn("GenericContainerScreen is open, but title '{}' does not match expected repair screen title.", titleString);
                    // Depending on strictness, you could return false here if title doesn't match.
                    return false; // Assuming class match is sufficient for now. REFINE THIS.
                }
            } else {
                // GenericContainerScreen with no title? Unlikely for a proper UI.
                LOGGER.warn("GenericContainerScreen is open, but has no title.");
                return true; // Or false, depending on how strict you need to be.
            }
        }
        // If it's not a GenericContainerScreen at all
        return false;
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
                                LOGGER.info("Identified {} as the tool to attempt repairing.", this.toolToRepairLastAttempt.getName().getString());
                                return; // Found the first tool needing repair
                            }
                            break;
                        } catch (NumberFormatException e) { /* Ignore */ }
                    }
                }
            }
        }
        LOGGER.info("No specific tool needing repair found in inventory to target in menu.");
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
        LOGGER.info("Clicking slot {} in screen with syncId {}", slotId, syncId);
        assert client.interactionManager != null;
        client.interactionManager.clickSlot(syncId, slotId, 0, SlotActionType.PICKUP, client.player);
    }

    private boolean checkSpecificToolRepair(ClientPlayerEntity player, ItemStack toolWeTriedToRepair) {
        if (toolWeTriedToRepair.isEmpty()) {
            LOGGER.debug("checkSpecificToolRepair called with an empty toolWeTriedToRepair stack.");
            return false;
        }

        LOGGER.info("Verifying repair for tool: {}", toolWeTriedToRepair.getName().getString());

        for (int i = 0; i < player.getInventory().size(); i++) {
            ItemStack currentStackInInventory = player.getInventory().getStack(i);
            if (currentStackInInventory.isEmpty()) continue;

            // Match by item type and name
            if (currentStackInInventory.isOf(toolWeTriedToRepair.getItem()) &&
                    currentStackInInventory.getName().getString().equals(toolWeTriedToRepair.getName().getString())) {

                LOGGER.info("Found candidate item {} in player inventory slot {}. Checking its lore for updated durability.",
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

                                LOGGER.info("Found durability lore on item {} (slot {}): {}/{}",
                                        currentStackInInventory.getName().getString(), i, newDurability, maxDurability);

                                // How does Wynncraft repair work? Fully or partially?
                                // Let's assume "repaired" means it's no longer below our *initial* threshold,
                                // or ideally, it's at max durability.
                                if (newDurability >= maxDurability) { // Fully repaired
                                    LOGGER.info("Tool {} is now fully repaired ({}). Verification SUCCESS.",
                                            currentStackInInventory.getName().getString(), newDurability);
                                    return true;
                                } else if (newDurability >= toolDurabilityThreshold) {
                                    LOGGER.info("Tool {} now has durability {} (above threshold {}). Considered sufficiently repaired. Verification SUCCESS.",
                                            currentStackInInventory.getName().getString(), newDurability, toolDurabilityThreshold);
                                    return true;
                                } else {
                                    // It had a durability line, but it's still below the threshold.
                                    LOGGER.warn("Tool {} has durability {} which is still below threshold {} after repair attempt. Verification FAILED for this item.",
                                            currentStackInInventory.getName().getString(), newDurability, toolDurabilityThreshold);
                                    return false; // This specific item instance is confirmed to be not repaired enough.
                                }
                            } catch (NumberFormatException e) {
                                LOGGER.warn("Could not parse durability from lore line '{}' for item {} in checkSpecificToolRepair: {}",
                                        loreLine, currentStackInInventory.getName().getString(), e.getMessage());
                                // Continue to check other lore lines on this item, as this one might be malformed.
                            }
                        }
                    }
                    // If we iterated all lore lines for this specific item and found no matching durability string
                    if (!durabilityLineFoundThisItem) {
                        LOGGER.warn("No valid durability lore line found on candidate tool {} (slot {}) after repair attempt.",
                                currentStackInInventory.getName().getString(), i);
                        return false; // This item instance doesn't have the expected durability lore anymore.
                    }
                } else { // No lore component on the item in inventory
                    LOGGER.warn("Candidate tool {} (slot {}) in inventory has no lore component after repair attempt.",
                            currentStackInInventory.getName().getString(), i);
                    return false;
                }
            }
        }

        // If we looped through the entire inventory and didn't find the tool OR
        // found it but couldn't confirm its repair (e.g. it was still damaged, or lore was missing/changed).
        LOGGER.warn("Could not find or verify repair for tool instance matching '{}' in inventory.",
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
        LOGGER.warn("No repair stations defined (neither by RoadNode type '{}' nor by manual coordinates).", repairStationRoadNodeType);
        return null;
    }
}