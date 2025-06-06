package net.natga999.wynn_ai.menus;

import net.natga999.wynn_ai.menus.widgets.MouseButtonSwitchWidget;

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

    // Tracks current state of checkboxes (action → checked) and sliders (action → value)
    private static final Map<String, Boolean> checkboxStates = new HashMap<>();
    private static final Map<String, Float> sliderValues = new HashMap<>();
    private static final Map<String, MouseButtonSwitchWidget.MouseButton> mouseButtonStates = new HashMap<>();

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
            LOGGER.warn("Failed to load menu layouts", e);
        }
    }

    public static float getSliderValueOrDefault(String action, float defaultValue) {
        return sliderValues.getOrDefault(action, defaultValue);
    }

    public static void setSliderValue(String action, float value) {
        sliderValues.put(action, value);
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

    public static MouseButtonSwitchWidget.MouseButton getMouseButtonState(String action, MouseButtonSwitchWidget.MouseButton defaultVal) {
        return mouseButtonStates.getOrDefault(action, defaultVal);
    }

    public static void setMouseButtonState(String action, MouseButtonSwitchWidget.MouseButton state) {
        mouseButtonStates.put(action, state);
    }
}