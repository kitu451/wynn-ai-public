package net.natga999.wynn_ai.menus;

import net.natga999.wynn_ai.WynnAIClient;
import net.natga999.wynn_ai.managers.*;
import net.natga999.wynn_ai.menus.widgets.*;

import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

public class MenuHUD {
    private static final Logger LOGGER = LoggerFactory.getLogger(MenuHUD.class);

    private final List<MenuWidget> widgets = new ArrayList<>();
    private final String menuId;
    private final String baseMenuName;
    private final MenuHUDConfig config;
    private SliderWidget activeSlider = null;

    private boolean dragging = false;
    private int dragOffsetX = 0;
    private int dragOffsetY = 0;
    private double dragStartX = 0;
    private double dragStartY = 0;
    private boolean movedFarEnoughToDrag = false;

    public MenuHUD(String menuId) {
        this.menuId = menuId;
        this.baseMenuName = menuId.contains("#") ? menuId.substring(0, menuId.indexOf('#')) : menuId;

        // Clone the config to allow independent x/y etc
        MenuHUDConfig baseConfig = MenuHUDLoader.getMenuHUDConfig(baseMenuName);
        if (baseConfig != null) {
            this.config = cloneConfig(baseConfig); // deep copy
        } else {
            this.config = null;
        }

        if (this.config == null) {
            LOGGER.warn("[MenuHUD] Failed to load config for: {}", menuId);
            return;
        }

        if (config.widgets != null) {
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
        context.drawText(client.textRenderer, config.title, x + 5, y + 5, 0xFFFFFF, false);

        for (MenuWidget widget : widgets) {
            widget.render(context, client, x, y);
        }
    }

    public boolean isMouseOver(double mouseX, double mouseY) {
        if (config == null) return false; // prevent NPE
        return mouseX >= config.x && mouseX <= config.x + config.windowWidth &&
                mouseY >= config.y && mouseY <= config.y + config.windowHeight;
    }

    public void onMouseClick(double mouseX, double mouseY) {
        if (config == null) return; // prevent NPE
        dragStartX = mouseX;
        dragStartY = mouseY;
        dragOffsetX = (int) (mouseX - config.x);
        dragOffsetY = (int) (mouseY - config.y);
        dragging = true;
        movedFarEnoughToDrag = false;

        int baseX = config.x;
        int baseY = config.y;

        activeSlider = null;

        for (MenuWidget widget : widgets) {
            if (widget instanceof ButtonWidget button) {
                if (button.isMouseOver(mouseX, mouseY, baseX, baseY)) {
                    handleAction(button.getAction());
                }
            }
            else if (widget instanceof CheckBoxWidget checkBox) {
                if (checkBox.isMouseOver(mouseX, mouseY, baseX, baseY)) {
                    checkBox.setChecked(!checkBox.isChecked());
                    handleAction(checkBox.getAction());
                }
            }
            else if (widget instanceof SliderWidget slider) {
                if (slider.isMouseOver(mouseX, mouseY, baseX, baseY)) {
                    activeSlider = slider;
                    slider.onDrag(mouseX, baseX);
                    handleSliderAction(slider.getAction(), slider.getValue());
                }
            }
            else if (widget instanceof MouseButtonSwitchWidget buttonSwitch) {
                if (buttonSwitch.isMouseOver(mouseX, mouseY, baseX, baseY)) {
                    buttonSwitch.toggle();
                    handleAction(buttonSwitch.getAction());
                }
            }
            else if (widget instanceof ResourceSelectorWidget selector) {
                if (selector.isMouseOver(mouseX, mouseY, baseX, baseY)) {
                    // Pass mouse cords relative to menu
                    boolean handled = selector.mouseClicked(
                            mouseX - baseX,  // Convert to widget-relative X
                            mouseY - baseY  // Convert to widget-relative Y
                    );

                    if (handled) {
                        handleAction("resourceSelected:" + HarvestingManager.getActiveResource());
                    }
                }
            }
        }
    }

    public void onMouseHold(double mouseX, double mouseY) {
        if (config == null) return;

        if (dragging && activeSlider == null) {
            double dx = Math.abs(mouseX - dragStartX);
            double dy = Math.abs(mouseY - dragStartY);
            if (!movedFarEnoughToDrag && (dx > 5 || dy > 5)) {
                movedFarEnoughToDrag = true;
            }

            if (movedFarEnoughToDrag) {
                config.x = (int) mouseX - dragOffsetX;
                config.y = (int) mouseY - dragOffsetY;
            }
        }

        if (activeSlider != null) {
            activeSlider.onDrag(mouseX, config.x);
            handleSliderAction(activeSlider.getAction(), activeSlider.getValue());
        }
    }

    public void onMouseRelease() {
        dragging = false;
        movedFarEnoughToDrag = false;
        if (activeSlider != null) {
            activeSlider = null;
        }
    }

    public boolean isDragging() {
        return dragging;
    }

    private void handleAction(String action) {
        if ("showHUD".equalsIgnoreCase(action)) {
            RenderManager.getInstance().setHudEnabled(!RenderManager.getInstance().isHudEnabled());
        }
        if ("showBoxes".equalsIgnoreCase(action)) {
            RenderManager.getInstance().setBoxEnabled(!RenderManager.getInstance().isBoxEnabled());
        }
        if ("showOutlines".equalsIgnoreCase(action)) {
            EntityOutlinerManager.getInstance().toggleOutlining();
        }
        if ("EntityListMain".equalsIgnoreCase(action) || "ResourceManager".equalsIgnoreCase(action)) {
            MenuHUD newMenu = MenuHUD.createNewInstance(action); // base name
            //newMenu.getConfig().x = config.x + 10; newMenu.getConfig().y = config.y + 10; // Offset slightly to avoid overlapping
            MenuHUDManager.registerMenu(newMenu);
            MenuHUDManager.bringToFront(newMenu);
        }
        if ("close".equalsIgnoreCase(action)) {
            MenuHUDManager.removeMenu(this);
        }
        if ("toggleHarvestButton".equalsIgnoreCase(action)) {
            boolean isRightClick = checkMouseButtonState(action);
            updateHarvestBehavior(isRightClick);
        }
        if ("open_resource_selector".equalsIgnoreCase(action)) {
            MenuHUD newMenu = MenuHUD.createNewInstance("ResourceSelector");
            MenuHUDManager.registerMenu(newMenu);
            MenuHUDManager.bringToFront(newMenu);
        }
        // Add more actions here later
        if (action.startsWith("resourceSelected:")) {
            String resource = action.split(":")[1];
            HarvestingManager.setActiveResource(resource);
        }
    }

    public void toggleCheckbox(String action) {
        for (MenuWidget widget : widgets) {
            if (widget instanceof CheckBoxWidget checkbox && checkbox.getAction().equals(action)) {
                checkbox.setChecked(!checkbox.isChecked());
                break;
            }
        }
    }

    private void handleSliderAction(String action, float value) {
        if ("detectionRadius".equalsIgnoreCase(action)) {
            WynnAIClient.setDetectionRadius((int) value);
        }
    }


    private boolean checkMouseButtonState(String action) {
        for (MenuWidget widget : widgets) {
            if (widget instanceof MouseButtonSwitchWidget switchWidget &&
                    switchWidget.getAction().equalsIgnoreCase(action)) {
                return switchWidget.getSelectedButton() == MouseButtonSwitchWidget.MouseButton.RIGHT;
            }
        }
        return false;
    }

    private void updateHarvestBehavior(boolean useRightClick) {
        // Example implementation - update your harvesting system
        HarvestPathManager.getInstance().setHarvestButton(useRightClick);
    }

    private MenuHUDConfig cloneConfig(MenuHUDConfig original) {
        MenuHUDConfig copy = new MenuHUDConfig();
        copy.x = original.x;
        copy.y = original.y;
        copy.windowWidth = original.windowWidth;
        copy.windowHeight = original.windowHeight;
        copy.title = original.title;
        copy.widgets = original.widgets != null ? new ArrayList<>(original.widgets) : null;
        return copy;
    }

    public static MenuHUD createNewInstance(String baseName) {
        String uuid = java.util.UUID.randomUUID().toString().substring(0, 6);
        return new MenuHUD(baseName + "#" + uuid);
    }

    public String getTitle() {
        return config != null ? config.title : "Unknown";
    }

    public String getMenuId() {
        return menuId;
    }

    /*
    public MenuHUDConfig getConfig() {
        return config;
    }
    **/
}