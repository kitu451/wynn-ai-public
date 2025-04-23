package net.natga999.wynn_ai.managers;

import net.natga999.wynn_ai.menus.MenuHUD;

import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;

import java.util.ArrayList;
import java.util.List;

public class MenuHUDManager {
    private static final List<MenuHUD> menus = new ArrayList<>();
    private static boolean wasClickOnMenuThisTick = false;

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
            if (menu.getTitle().equals(title)) {
                return true;
            }
        }
        return false;
    }

    public static boolean hasNoMenus() {
        return menus.isEmpty();
    }

    public static void handleClick(double mouseX, double mouseY) {
        wasClickOnMenuThisTick = false;
        if (!RenderManager.isInteractionMode()) {
            return;
        }
        MenuHUD toFront = null;
        for (int i = menus.size() - 1; i >= 0; i--) {
            MenuHUD menu = menus.get(i);
            if (menu == null) continue;
            if (menu.isMouseOver(mouseX, mouseY)) {
                toFront = menu; // Mark for reordering
                break;
            }
        }

        if (toFront != null) {
            bringToFront(toFront);
            wasClickOnMenuThisTick = true;
            toFront.onMouseClick(mouseX, mouseY);
        }
    }

    public static void handleDrag(double mouseX, double mouseY) {
        if (!RenderManager.isInteractionMode()) {
            return;
        }
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
        if (!RenderManager.isInteractionMode()) {
            return;
        }
        for (MenuHUD menu : menus) {
            if (menu.isDragging()) {
                menu.onMouseRelease();
            }
        }
    }

    public static boolean wasClickOnMenu() {
        return wasClickOnMenuThisTick;
    }

    public static boolean isClickInsideAnyMenu(double mouseX, double mouseY) {
        for (MenuHUD menu : getMenus()) {
            if (menu.isMouseOver(mouseX, mouseY)) {
                return true;
            }
        }
        return false;
    }

    public static void bringToFront(MenuHUD menu) {
        if (menus.remove(menu)) {
            menus.add(menu);
        }
    }

    public static List<MenuHUD> getMenus() {
        return menus;
    }
}