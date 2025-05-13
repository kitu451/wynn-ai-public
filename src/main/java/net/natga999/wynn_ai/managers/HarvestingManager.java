package net.natga999.wynn_ai.managers;

import net.natga999.wynn_ai.menus.MenuHUDLoader;
import net.natga999.wynn_ai.menus.widgets.MouseButtonSwitchWidget;

import java.util.*;

public class HarvestingManager {
    private static String activeResource = "Wheat"; // Default
    private static final List<Runnable> changeListeners = new ArrayList<>();

    private static final Map<String, MouseButtonSwitchWidget.MouseButton> buttonStates = new HashMap<>();
    private static final Map<String, List<Runnable>> buttonChangeListeners = new HashMap<>();

    public static void setActiveResource(String resource) {
        if (ResourceNodeManager.hasResourceConfig(resource)) {
            activeResource = resource;
            saveToConfig();
            notifyListeners();
        }
    }

    public static void addChangeListener(Runnable listener) {
        changeListeners.add(listener);
    }

    private static void notifyListeners() {
        for (Runnable listener : changeListeners) {
            listener.run();
        }
    }

    public static String getActiveResource() {
        return activeResource;
    }

    public static void setMouseButtonState(String action, MouseButtonSwitchWidget.MouseButton state) {
        MouseButtonSwitchWidget.MouseButton old = buttonStates.put(action, state);
        if (old != state) {
            notifyButtonListeners(action);
        }
        MenuHUDLoader.setMouseButtonState(action, state); // Preserve existing save logic
    }

    public static MouseButtonSwitchWidget.MouseButton getMouseButtonState(String action,
                                                                          MouseButtonSwitchWidget.MouseButton defaultVal) {
        return buttonStates.getOrDefault(action,
                MenuHUDLoader.getMouseButtonState(action, defaultVal));
    }

    public static void addButtonChangeListener(String action, Runnable listener) {
        buttonChangeListeners.computeIfAbsent(action, k -> new ArrayList<>()).add(listener);
    }

    private static void notifyButtonListeners(String action) {
        for (Runnable listener : buttonChangeListeners.getOrDefault(action, Collections.emptyList())) {
            listener.run();
        }
    }

    private static void saveToConfig() {
        // Save to mod config file
    }
}