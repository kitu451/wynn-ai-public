package net.natga999.wynn_ai.tasks;

import net.minecraft.client.MinecraftClient;

public class IdleTask implements Task {
    public int getPriority() { return 10; }
    public void start(MinecraftClient c) {}
    public void tick(MinecraftClient c) {
        // maybe look around or park
    }
    public void stop(MinecraftClient c) {}
    public boolean isActive() { return true; }
}