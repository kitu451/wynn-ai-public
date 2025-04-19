package net.natga999.wynn_ai.menus.huds;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import java.io.IOException;
import java.io.InputStream;
import java.util.HashMap;
import java.util.Map;

public class MenuHUDLoader {
    private static final Logger LOGGER = LoggerFactory.getLogger(MenuHUDLoader.class);
    private static final Map<String, MenuHUDConfig> loadedHUDMenus = new HashMap<>();

    // Tracks current state of checkboxes (action → checked)
    private static final Map<String, Boolean> checkboxStates = new HashMap<>();

    static {
        try (InputStream inputStream = MenuHUDLoader.class.getClassLoader().getResourceAsStream("assets/wynn_ai/menuhudconfig.json")) {
            if (inputStream == null) {
                throw new IOException("menuhudconfig.json not found in resources.");
            }

            ObjectMapper objectMapper = new ObjectMapper();
            MenuHUDRootConfig rootConfig = objectMapper.readValue(inputStream, MenuHUDRootConfig.class);

            if (rootConfig.menus != null) {
                loadedHUDMenus.putAll(rootConfig.menus);
                System.out.println("Loaded menu configs: " + loadedHUDMenus.keySet());
            } else {
                throw new IOException("Root JSON does not contain 'menus' key.");
            }
        } catch (Exception e) {
            LOGGER.error("Failed to load menu layouts", e);
        }
    }

    public static boolean getCheckboxState(String action) {
        return checkboxStates.getOrDefault(action, false);
    }

    public static void setCheckboxState(String action, boolean checked) {
        checkboxStates.put(action, checked);
    }

    public static MenuHUDConfig getMenuHUDConfig(String menuName) {
        return loadedHUDMenus.get(menuName);
    }
}
