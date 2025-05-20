package net.natga999.wynn_ai.tasks;

import net.natga999.wynn_ai.ai.BasicPathAI;
import net.natga999.wynn_ai.managers.combat.CombatManager;

import net.minecraft.client.MinecraftClient;
import net.minecraft.util.math.Vec3d;

import java.util.List;

public class CombatTask implements Task {
    private final List<Vec3d> path;
    private boolean active = false;

    public CombatTask(List<Vec3d> path) { this.path = path; }

    public int getPriority() { return 3; }  // higher than harvest

    public void start(MinecraftClient client) {
        //call basicPathAI
        //CombatManager.getInstance().startChase(target);
        BasicPathAI.getInstance().goAlongPath(path);
        active = true;
    }

    public void tick(MinecraftClient client) {
        if (!CombatManager.getInstance().isInCombat()) {
            active = false;
        }
    }

    public void stop(MinecraftClient client) {
        //call basicPathAI
        //CombatManager.getInstance().stop();
        BasicPathAI.getInstance().stop();
    }

    public boolean isActive() { return active; }
}
