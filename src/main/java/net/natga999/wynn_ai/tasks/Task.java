package net.natga999.wynn_ai.tasks;

import net.minecraft.client.MinecraftClient;

public interface Task {
    /** True if this task is still running (not yet succeeded/failed). */
    boolean isActive();

    /** Called once when the task is first started. */
    void start(MinecraftClient client);

    /** Called every tick until isActive() returns false. */
    void tick(MinecraftClient client);

    /** Called when the task exits (either succeeded or aborted). */
    void stop(MinecraftClient client);

    /** Priority: lower = more important (0 highest). */
    int getPriority();
}
