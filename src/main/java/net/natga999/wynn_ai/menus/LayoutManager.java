package net.natga999.wynn_ai.menus;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.util.HashMap;
import java.util.Map;

public class LayoutManager {
    private static final Logger LOGGER = LoggerFactory.getLogger(LayoutManager.class);

    private static final Map<String, MenuConfig> menuConfigs = new HashMap<>();

    public static void loadLayouts(String filePath) throws IOException {

        try (InputStream inputStream = LayoutManager.class.getClassLoader().getResourceAsStream("assets/wynn_ai/menuconfig.json")) {
            if (inputStream == null) {
                throw new IOException("menuconfig.json not found in resources.");
            }

            ObjectMapper objectMapper = new ObjectMapper();
            Map<String, Object> root = objectMapper.readValue(
                    inputStream,
                    objectMapper.getTypeFactory().constructMapType(Map.class, String.class, Object.class)
            );

            if (root.containsKey("menus")) {
                Map<String, MenuConfig> menus = objectMapper.convertValue(
                        root.get("menus"),
                        objectMapper.getTypeFactory().constructMapType(Map.class, String.class, MenuConfig.class)
                );
                menuConfigs.putAll(menus);
            } else {
                throw new IOException("Root JSON does not contain 'menus' key");
            }

            LOGGER.info("Menu layouts loaded successfully.");
        } catch (IOException e) {
            LOGGER.error("Failed to load menu layouts", e);
        }

        // Use Jackson to parse the layout JSON file
        ObjectMapper objectMapper = new ObjectMapper();

        // Parse the root JSON
        Map<String, Object> root = objectMapper.readValue(
                new File(filePath),
                objectMapper.getTypeFactory().constructMapType(Map.class, String.class, Object.class)
        );

        // Extract the "menus" key and parse it into menuConfigs
        if (root.containsKey("menus")) {
            // Map the "menus" object to menuConfigs
            Map<String, MenuConfig> menus = objectMapper.convertValue(
                    root.get("menus"),
                    objectMapper.getTypeFactory().constructMapType(Map.class, String.class, MenuConfig.class)
            );

            menuConfigs.putAll(menus);
        } else {
            throw new IOException("Root JSON does not contain 'menus' key");
        }
    }

    public static MenuConfig getMenuConfig(String menuName) {
        return menuConfigs.get(menuName);
    }
}