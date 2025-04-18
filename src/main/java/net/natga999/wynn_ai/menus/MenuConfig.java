package net.natga999.wynn_ai.menus;

import java.util.List;

public class MenuConfig {
    private int windowWidth;
    private int windowHeight;
    private List<WidgetConfig> widgets;

    // Getters and Setters
    public int getWindowWidth() {
        return windowWidth;
    }

    public void setWindowWidth(int windowWidth) {
        this.windowWidth = windowWidth;
    }

    public int getWindowHeight() {
        return windowHeight;
    }

    public void setWindowHeight(int windowHeight) {
        this.windowHeight = windowHeight;
    }

    public List<WidgetConfig> getWidgets() {
        return widgets;
    }

    public void setWidgets(List<WidgetConfig> widgets) {
        this.widgets = widgets;
    }
}