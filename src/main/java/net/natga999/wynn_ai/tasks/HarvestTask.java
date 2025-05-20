package net.natga999.wynn_ai.tasks;

import net.natga999.wynn_ai.ai.BasicPathAI;
import net.natga999.wynn_ai.managers.HarvestPathManager;

import net.minecraft.client.MinecraftClient;
import net.minecraft.util.math.Vec3d;

import java.util.List;

public class HarvestTask implements Task {
    private final List<Vec3d> path;
    private boolean active = false;

    public HarvestTask(List<Vec3d> path) { this.path = path; }

    public int getPriority() { return 5; }  // medium priority

    public void start(MinecraftClient client) {
        BasicPathAI.getInstance().goAlongPath(path);
        active = true;
    }

    public void tick(MinecraftClient client) {
        if (HarvestPathManager.getInstance().isPathComplete()) {
            active = false;
        }
    }

    public void stop(MinecraftClient client) {
        BasicPathAI.getInstance().stop();
    }

    public boolean isActive() { return active; }
}