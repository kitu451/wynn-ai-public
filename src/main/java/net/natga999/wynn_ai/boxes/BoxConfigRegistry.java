package net.natga999.wynn_ai.boxes;

import java.util.HashMap;
import java.util.Map;
import java.util.Set;

public class BoxConfigRegistry {
    // Map to store keyword -> BoxConfig
    private static final Map<String, BoxConfig> CONFIG_MAP = new HashMap<>();

    static {
        // Register configurations
        // Fishing

        // Woodcutting
        CONFIG_MAP.put("Oak", new BoxConfig(-1.0, 1.5, 0.5, 0xFFAA00));

        // Mining

        // Farming
        CONFIG_MAP.put("Wheat", new BoxConfig(-2.5, 0.0, 1.0, 0xFFFF00));
        CONFIG_MAP.put("Barley", new BoxConfig(-2.5, 0.0, 1.0, 0xFFFF00));

        // Mobs
        CONFIG_MAP.put("Zombie Raider", new BoxConfig(-2.0, 0.0, 0.5, 0xFFAA00));

        //CONFIG_MAP.put("Corn", new BoxConfig(-0.5, 0.5, 1.0, 0xFFAA00)); // Orange box, standard size
        // Add more configurations here
    }

    /**
     * Retrieves a BoxConfig for the given keyword.
     *
     * @param keyword The string keyword to search for.
     * @return The corresponding BoxConfig, or null if not found.
     */
    public static BoxConfig getConfig(String keyword) {
        return CONFIG_MAP.get(keyword);
    }

    /**
     * Retrieves all registered keywords.
     *
     * @return A Set of all keywords in the map.
     */
    public static Set<String> getRegisteredKeywords() {
        return CONFIG_MAP.keySet();
    }
}