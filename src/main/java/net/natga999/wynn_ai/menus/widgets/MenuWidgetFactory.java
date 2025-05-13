package net.natga999.wynn_ai.menus.widgets;

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
        if ("checkbox".equalsIgnoreCase(type)) {
            int x = (int) data.get("x");
            int y = (int) data.get("y");
            int width = (int) data.get("width");
            int height = (int) data.get("height");
            String text = (String) data.get("text");
            String action = (String) data.get("action");
            Boolean checked = (Boolean) data.get("checked");
            return new CheckBoxWidget(x, y, width, height, text, action, checked);
        }
        if ("slider".equalsIgnoreCase(type)) {
            int x = (int) data.get("x");
            int y = (int) data.get("y");
            int width = (int) data.get("width");
            int height = (int) data.get("height");
            String text = (String) data.get("text");
            String action = (String) data.get("action");
            float value = ((Number) data.get("value")).floatValue();
            float min = ((Number) data.get("min")).floatValue();
            float max = ((Number) data.get("max")).floatValue();
            float step = ((Number) data.get("step")).floatValue();
            return new SliderWidget(x, y, width, height, text, action, value, min, max, step);
        }
        if ("mouse_button".equalsIgnoreCase(type)) {
            return new MouseButtonSwitchWidget(
                    (int) data.get("x"),
                    (int) data.get("y"),
                    (int) data.get("width"),
                    (int) data.get("height"),
                    (String) data.get("action"),
                    MouseButtonSwitchWidget.MouseButton.valueOf(
                            ((String) data.get("defaultButton")).toUpperCase())
            );
        }
        if ("resource_list".equals(type)) {
            return new ResourceSelectorWidget(
                    (int) data.get("x"),
                    (int) data.get("y"),
                    (int) data.get("width"),
                    (int) data.get("height")
            );
        }
        return null;
    }
}