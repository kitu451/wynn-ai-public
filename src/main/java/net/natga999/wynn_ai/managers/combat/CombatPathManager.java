package net.natga999.wynn_ai.managers.combat;

import net.minecraft.util.math.Vec3d;

public class CombatPathManager {
    public void setGoal(Vec3d targetPos) {
        // Use your existing pathfinding, but with combat-specific tweaks:
        // - Faster recalculations
        // - Prefer open spaces
    }

    public void evade() {
        // Move perpendicular to the target to dodge
    }
}