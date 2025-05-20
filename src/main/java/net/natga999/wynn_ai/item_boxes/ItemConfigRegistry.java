package net.natga999.wynn_ai.item_boxes;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.InputStream;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;

public class ItemConfigRegistry {
    private static final Logger LOGGER = LoggerFactory.getLogger(ItemConfigRegistry.class);

    private static final Map<String, ItemConfig> CONFIG_MAP = new HashMap<>();

    // Define the default configuration as a static and reusable instance
    private static final ItemConfig DEFAULT_CONFIG = new ItemConfig(
            0.75, // minYOffset: Half a block above
            0.25, // maxYOffset: 1.5 blocks height
            0.25, // sizeXZOffset: Half a block width/length
            0xFFFFFFFF // Color: Default white
    );

    static {
        try {
            // Load the itemconfig.json file
            InputStream inputStream = ItemConfigRegistry.class.getClassLoader().getResourceAsStream("assets/wynn_ai/itemconfig.json");

            if (inputStream == null) {
                throw new RuntimeException("itemconfig.json file not found in classpath.");
            }

            ObjectMapper objectMapper = new ObjectMapper();

            // Parse JSON into a map of keyword -> ItemConfigDto
            Map<String, ItemConfigDto> configs = objectMapper.readValue(inputStream, new TypeReference<>() {});

            // Convert ItemConfigDto to ItemConfig and populate CONFIG_MAP
            for (Map.Entry<String, ItemConfigDto> entry : configs.entrySet()) {
                CONFIG_MAP.put(entry.getKey(), entry.getValue().toItemConfig());
            }

            for (Map.Entry<String, ItemConfigDto> entry : configs.entrySet()) {
                LOGGER.debug("Loaded configuration for keyword {}: {}", entry.getKey(), entry.getValue());
                System.out.println("Loaded configuration for keyword " + entry.getKey() + ": " + entry.getValue());
                CONFIG_MAP.put(entry.getKey(), entry.getValue().toItemConfig());
            }

            LOGGER.info("Loaded {} item configurations successfully.", CONFIG_MAP.size());
        } catch (Exception e) {
            LOGGER.error("Failed to load item configurations", e);
        }
    }

    public static ItemConfig getConfig(String keyword) {
        return CONFIG_MAP.get(keyword);
    }

    public static ItemConfig getDefaultConfig() {
        return DEFAULT_CONFIG;
    }

    public static Set<String> getRegisteredKeywords() {
        return CONFIG_MAP.keySet();
    }

    // Helper DTO class to parse JSON
    private static class ItemConfigDto {
        public double minYOffset;
        public double maxYOffset;
        public double sizeXZOffset;
        public String color;

        // Convert ItemConfigDto to ItemConfig
        public ItemConfig toItemConfig() {
            return new ItemConfig(minYOffset, maxYOffset, sizeXZOffset, parseColor(color));
        }

        private int parseColor(String color) {
            // Parse the color in HEX format to an integer
            return Integer.parseUnsignedInt(color.replace("#", ""), 16);
        }
    }
}