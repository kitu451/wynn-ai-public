package net.natga999.wynn_ai.commands;

import net.minecraft.client.network.ClientPlayerEntity;
import net.minecraft.component.DataComponentTypes;
import net.minecraft.component.type.NbtComponent;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.nbt.NbtElement;
import net.minecraft.registry.RegistryWrapper;
import net.minecraft.text.Text;

import net.fabricmc.fabric.api.client.command.v2.FabricClientCommandSource;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.context.CommandContext;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import static net.fabricmc.fabric.api.client.command.v2.ClientCommandManager.literal;
import static net.fabricmc.fabric.api.client.command.v2.ClientCommandManager.argument;

public class NbtInfoCommand {
    private static final Logger LOGGER = LoggerFactory.getLogger(NbtInfoCommand.class);

    /**
     * Registers the /nbtinfo command and its subcommands.
     * @param dispatcher The command dispatcher to register with.
     */
    public static void register(CommandDispatcher<FabricClientCommandSource> dispatcher) {
        dispatcher.register(literal("nbtinfo")
                .then(literal("hand")
                        .executes(NbtInfoCommand::showHandItemNbt)
                )
                .then(literal("slot")
                        .then(argument("slot_index", IntegerArgumentType.integer(0, 40)) // Player main inv (0-35), armor (36-39), offhand (40)
                                .executes(NbtInfoCommand::showSlotItemNbt)
                        )
                )
                .executes(ctx -> {
                    ctx.getSource().sendFeedback(Text.literal("Usage: /nbtinfo <hand | slot <slot_index> | checkrepair>"));
                    return 1;
                })
        );
    }

    private static int showHandItemNbt(CommandContext<FabricClientCommandSource> ctx) {
        ClientPlayerEntity player = ctx.getSource().getPlayer();
        // getPlayer() in FabricClientCommandSource doesn't throw CommandSyntaxException, but good practice for other contexts
        if (player == null) {
            ctx.getSource().sendError(Text.literal("Player not found."));
            return 0;
        }
        ItemStack stack = player.getMainHandStack();
        displayNbtInfo(ctx.getSource(), stack, "Main Hand");
        return 1;
    }

    private static int showSlotItemNbt(CommandContext<FabricClientCommandSource> ctx) {
        ClientPlayerEntity player = ctx.getSource().getPlayer();
        if (player == null) {
            ctx.getSource().sendError(Text.literal("Player not found."));
            return 0;
        }
        int slotIndex = IntegerArgumentType.getInteger(ctx, "slot_index");

        // Validate slot index based on typical inventory layout
        // Main inventory: 0-35
        // Armor slots: Typically 36 (feet) to 39 (head) - ItemStack.ARMOR_SLOT_IDS can map these
        // Offhand: Typically 40 (ItemStack.OFFHAND_SLOT_INDEX)
        // Hotbar slots are 0-8. Full inventory including hotbar is 0-35.
        if (slotIndex < 0 || slotIndex >= player.getInventory().size()) {
            // player.getInventory().size() includes main, armor, offhand.
            // For just mainInv + hotbar: player.getInventory().main.size() which is 36.
            ctx.getSource().sendError(Text.literal(String.format("Invalid slot index %d. Max vanilla slots: %d.", slotIndex, player.getInventory().size() -1 )));
            return 0;
        }
        ItemStack stack = player.getInventory().getStack(slotIndex);
        displayNbtInfo(ctx.getSource(), stack, "Slot " + slotIndex);
        return 1;
    }

    private static void displayNbtInfo(FabricClientCommandSource source, ItemStack stack, String itemLocation) {
        if (stack.isEmpty()) {
            source.sendFeedback(Text.literal(itemLocation + " is empty."));
            LOGGER.info("{} is empty.", itemLocation);
            return;
        }

        source.sendFeedback(Text.literal("--- Info for " + stack.getName().getString() + " (" + itemLocation + ") ---"));
        LOGGER.info("--- Info for {} ({}) ---", stack.getName().getString(), itemLocation);

        // Display Standard Component Durability
        Integer maxDamage = stack.get(DataComponentTypes.MAX_DAMAGE);
        if (maxDamage != null && maxDamage > 0) {
            Integer damageTaken = stack.get(DataComponentTypes.DAMAGE);
            if (damageTaken == null) damageTaken = 0;
            int currentDurability = maxDamage - damageTaken;
            String durabilityInfo = String.format("Component Durability: %d/%d (Damage component: %d)", currentDurability, maxDamage, damageTaken);
            source.sendFeedback(Text.literal(durabilityInfo));
            LOGGER.info(durabilityInfo);
        } else {
            source.sendFeedback(Text.literal("Item does not have MAX_DAMAGE component (or it's not positive)."));
            LOGGER.info("Item does not have MAX_DAMAGE component (or it's not positive).");
        }

        // Display CUSTOM_DATA component
        NbtComponent customDataComponent = stack.get(DataComponentTypes.CUSTOM_DATA);
        if (customDataComponent != null && !customDataComponent.isEmpty()) {
            NbtCompound customNbt = customDataComponent.copyNbt(); // Get a mutable copy
            source.sendFeedback(Text.literal("Custom Data Component (custom_data): (see logs)"));
            LOGGER.info("Custom Data Component (custom_data): {}", customNbt.toString());
        } else {
            source.sendFeedback(Text.literal("Item has no 'custom_data' component or it's empty."));
            LOGGER.info("Item has no 'custom_data' component or it's empty.");
        }

        // Display Full Encoded NBT (includes all components)
        try {
            RegistryWrapper.WrapperLookup registries = source.getWorld().getRegistryManager();
            if (registries == null) {
                LOGGER.warn("RegistryWrapper.WrapperLookup is null, cannot encode full NBT.");
                source.sendError(Text.literal("Error: Could not get registry manager to encode NBT."));
                return; // Added return
            }

            NbtElement fullNbtElement = stack.encode(registries); // This is the one that gives the full picture
            if (fullNbtElement instanceof NbtCompound) {
                String fullNbtString = fullNbtElement.toString();
                source.sendFeedback(Text.literal("Full Encoded NBT: (see logs for potentially long string)"));
                LOGGER.info("Full Encoded NBT of {}: {}", stack.getName().getString(), fullNbtString);
            } else {
                LOGGER.warn("Encoded ItemStack for {} resulted in non-compound NBT: {}", stack.getName().getString(), fullNbtElement.toString());
                source.sendFeedback(Text.literal("Full Encoded NBT was not a compound: " + fullNbtElement.getNbtType().getCommandFeedbackName()));
            }
        } catch (Exception e) {
            LOGGER.warn("Error encoding full NBT for {}: {}", stack.getName().getString(), e.getMessage(), e);
            source.sendError(Text.literal("Error encoding full NBT: " + e.getMessage()));
        }

        source.sendFeedback(Text.literal("--- End Info ---"));
        LOGGER.info("--- End Info ---");
    }
}