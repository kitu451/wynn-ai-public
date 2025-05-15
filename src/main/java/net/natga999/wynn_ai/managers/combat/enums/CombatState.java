package net.natga999.wynn_ai.managers.combat.enums;

public enum CombatState {
    AWAIT,
    SEARCH,    // No target found
    APPROACH,  // Moving toward target
    ATTACK,    // In range, attacking
    EVADE      // Dodging projectiles/attacks
}