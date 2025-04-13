package net.natga999.wynn_ai.boxes;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.io.InputStream;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class BoxConfigRegistry {

    private static final Logger LOGGER = LoggerFactory.getLogger(BoxConfigRegistry.class); // Logger instance

    private static final Map<String, BoxConfig> CONFIG_MAP = new HashMap<>();

    static {
        try {
            // Load the boxconfig.json file
            InputStream inputStream = BoxConfigRegistry.class.getClassLoader().getResourceAsStream("boxconfig.json");

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
            LOGGER.error("Failed to load box configurations", e);
        }
    }

    public static BoxConfig getConfig(String keyword) {
        return CONFIG_MAP.get(keyword);
    }

    public static Set<String> getRegisteredKeywords() {
        return CONFIG_MAP.keySet();
    }

    // Helper DTO Class to Parse JSON
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
            return Integer.parseInt(color.replace("#", ""), 16);
        }
    }
}