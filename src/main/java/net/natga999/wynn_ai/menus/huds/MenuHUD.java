package net.natga999.wynn_ai.menus.huds;

import net.natga999.wynn_ai.managers.RenderManager;
import net.natga999.wynn_ai.menus.huds.widgets.ButtonWidget;
import net.natga999.wynn_ai.menus.huds.widgets.MenuWidget;
import net.natga999.wynn_ai.menus.huds.widgets.MenuWidgetFactory;

import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

public class MenuHUD {
    private final List<MenuWidget> widgets = new ArrayList<>();
    private final MenuHUDConfig config;

    public MenuHUD(String menuName) {
        this.config = MenuHUDLoader.getMenuHUDConfig(menuName);

        if (config != null && config.widgets != null) {
            for (Map<String, Object> widgetData : config.widgets) {
                MenuWidget widget = MenuWidgetFactory.createWidget(widgetData);
                if (widget != null) widgets.add(widget);
            }
        }
    }

    public void renderMenuHUD(DrawContext context, MinecraftClient client) {
        if (config == null) return;

        int x = config.x;
        int y = config.y;
        int width = config.windowWidth;
        int height = config.windowHeight;

        context.fill(x, y, x + width, y + height, 0xAA000000);
        context.drawBorder(x, y, width, height, 0xFFFFFFFF);
        context.drawText(client.textRenderer, config.title, x + 8, y + 8, 0xFFFFFF, false);

        for (MenuWidget widget : widgets) {
            widget.render(context, client, x, y);
        }
    }

    public void onMouseClick(double mouseX, double mouseY) {
        MenuHUDConfig config = MenuHUDLoader.getMenuHUDConfig("MainMenu"); // or dynamic name
        if (config == null) return;

        for (Map<String, Object> widgetData : config.widgets) {
            MenuWidget widget = MenuWidgetFactory.createWidget(widgetData);
            if (widget instanceof ButtonWidget button) {
                if (button.isMouseOver(mouseX, mouseY, config.x, config.y)) {
                    handleAction(button.getAction());
                }
            }
        }
    }

    private void handleAction(String action) {
        if ("close".equalsIgnoreCase(action)) {
            RenderManager.setMenuHUDEnabled(false);
        }
        // Add more actions here later
    }
}
