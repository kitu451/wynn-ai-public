package net.natga999.wynn_ai.strategies;

import net.natga999.wynn_ai.ai.BasicPathAI;

import net.minecraft.client.MinecraftClient;

public interface MovementStrategy {
    /** Called every tick, once per frame. */
    void tick(BasicPathAI ai);

    /** Returns true once the strategy considers its path “done.” */
    boolean isComplete(BasicPathAI ai);

    /** Optional cleanup when path finishes or is cancelled. */
    default void onStop(BasicPathAI ai) {}

    void handleCameraRotation(BasicPathAI ai, MinecraftClient client);
}