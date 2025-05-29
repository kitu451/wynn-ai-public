package net.natga999.wynn_ai.boxes;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.InputStream;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;

public class BoxConfigRegistry {
    private static final Logger LOGGER = LoggerFactory.getLogger(BoxConfigRegistry.class);

    private static final Map<String, BoxConfig> CONFIG_MAP = new HashMap<>();

    // Define the default configuration as a static and reusable instance
    private static final BoxConfig DEFAULT_CONFIG = new BoxConfig(
            0.0, // minYOffset
            -1.9,  // maxYOffset
            0.5,  // sizeXZOffset
            0xFFFFFFFF // Color: Default white
    );

    private static final BoxConfig DEFAULT_LOAD_NODE_CONFIG = new BoxConfig(
            1.0,    // minY
            -3.5,    // maxY
            0.25f,   // sizeXZ
            0xFF00FF00 // Green color for stored nodes
    );

    static {
        try {
            // Load the boxconfig.json file
            InputStream inputStream = BoxConfigRegistry.class.getClassLoader().getResourceAsStream("assets/wynn_ai/boxconfig.json");

            if (inputStream == null) {
                throw new RuntimeException("boxconfig.json file not found in classpath.");
            }

            ObjectMapper objectMapper = new ObjectMapper();

            // Parse JSON into a map of keyword -> BoxConfigDto
            Map<String, BoxConfigDto> configs = objectMapper.readValue(inputStream, new TypeReference<>() {});

            // Convert BoxConfigDto to BoxConfig and populate CONFIG_MAP
            for (Map.Entry<String, BoxConfigDto> entry : configs.entrySet()) {
                CONFIG_MAP.put(entry.getKey(), entry.getValue().toBoxConfig());
            }

            for (Map.Entry<String, BoxConfigDto> entry : configs.entrySet()) {
                LOGGER.debug("Loaded configuration for keyword {}: {}", entry.getKey(), entry.getValue());
                System.out.println("Loaded configuration for keyword " + entry.getKey() + ": " + entry.getValue());
                CONFIG_MAP.put(entry.getKey(), entry.getValue().toBoxConfig());
            }

            LOGGER.info("Loaded {} configurations successfully.", CONFIG_MAP.size());
        } catch (Exception e) {
            LOGGER.warn("Failed to load box configurations", e);
        }
    }

    public static BoxConfig getConfig(String keyword) {
        return CONFIG_MAP.get(keyword);
    }

    public static BoxConfig getDefaultConfig() {
        return DEFAULT_CONFIG;
    }

    public static BoxConfig getDefaultLoadNodeConfig() {
        return DEFAULT_LOAD_NODE_CONFIG;
    }

    public static Set<String> getRegisteredKeywords() {
        return CONFIG_MAP.keySet();
    }

    // Helper DTO class to parse JSON
    private static class BoxConfigDto {
        public double minYOffset;
        public double maxYOffset;
        public double sizeXZOffset;
        public String color; // e.g., "#FFAA00"

        // Convert BoxConfigDto to BoxConfig
        public BoxConfig toBoxConfig() {
            return new BoxConfig(minYOffset, maxYOffset, sizeXZOffset, parseColor(color));
        }

        private int parseColor(String color) {
            // Parse the color in HEX format to an integer
            return Integer.parseUnsignedInt(color.replace("#", ""), 16);
        }
    }
}