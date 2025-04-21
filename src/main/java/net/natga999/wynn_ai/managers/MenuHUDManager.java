package net.natga999.wynn_ai.managers;

import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;
import net.natga999.wynn_ai.menus.huds.MenuHUD;

import java.util.ArrayList;
import java.util.List;

public class MenuHUDManager {
    private static final List<MenuHUD> menus = new ArrayList<>();

    public static void registerMenu(MenuHUD menu) {
        menus.add(menu);
    }

    public static void removeMenu(MenuHUD menu) {
        menus.remove(menu);
    }

    public static void clearMenus() {
        menus.clear();
    }

    public static void renderAll(DrawContext context, MinecraftClient client) {
        for (MenuHUD menu : menus) {
            menu.renderMenuHUD(context, client);
        }
    }

    public static boolean menuExists(String title) {
        for (MenuHUD menu : menus) {
            if (menu.getTitle().equals(title)) { // You handle null safely inside MenuHUD
                return true;
            }
        }
        return false;
    }

    public static void handleClick(double mouseX, double mouseY) {
        for (int i = menus.size() - 1; i >= 0; i--) {
            MenuHUD menu = menus.get(i);
            if (menu == null) continue;
            if (menu.isMouseOver(mouseX, mouseY)) {
                menu.onMouseClick(mouseX, mouseY);

                // Bring to front
                menus.remove(i);
                menus.add(menu);
                break;
            }
        }
    }

    public static void handleDrag(double mouseX, double mouseY) {
        for (int i = menus.size() - 1; i >= 0; i--) {
            MenuHUD menu = menus.get(i);
            if (menu == null) continue;
            if (menu.isDragging()) {
                menu.onMouseHold(mouseX, mouseY);
                break;
            }
        }
    }

    public static void handleRelease() {
        for (MenuHUD menu : menus) {
            if (menu.isDragging()) {
                menu.onMouseRelease();
            }
        }
    }

    public static List<MenuHUD> getMenus() {
        return menus;
    }
}
