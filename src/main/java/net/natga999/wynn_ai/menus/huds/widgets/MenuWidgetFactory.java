package net.natga999.wynn_ai.menus.huds.widgets;

import java.util.Map;

public class MenuWidgetFactory {
    public static MenuWidget createWidget(Map<String, Object> data) {
        String type = (String) data.get("type");
        if ("button".equalsIgnoreCase(type)) {
            int x = (int) data.get("x");
            int y = (int) data.get("y");
            int width = (int) data.get("width");
            int height = (int) data.get("height");
            String text = (String) data.get("text");
            String action = (String) data.get("action");
            return new ButtonWidget(x, y, width, height, text, action);
        }
        return null; // or throw exception
    }
}
