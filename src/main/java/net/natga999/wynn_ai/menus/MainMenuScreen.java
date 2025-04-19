package net.natga999.wynn_ai.menus;

import net.minecraft.client.util.InputUtil;
import net.natga999.wynn_ai.TestRender;
import net.natga999.wynn_ai.managers.EntityOutlinerManager;
import net.natga999.wynn_ai.managers.RenderManager;

import net.minecraft.client.gui.widget.ClickableWidget;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.widget.ButtonWidget;
import net.minecraft.client.gui.widget.CheckboxWidget;
import net.minecraft.client.gui.widget.SliderWidget;
import net.minecraft.client.option.KeyBinding;
import net.minecraft.text.Text;

import org.lwjgl.glfw.GLFW;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

public class MainMenuScreen extends Screen {
    private static final Logger LOGGER = LoggerFactory.getLogger(MainMenuScreen.class);

    private boolean isDragging = false; // Whether the menu is currently being dragged
    private boolean isDraggingSlider = false;
    private int dragOffsetX = 0;        // Offset between the mouse and menu position
    private int dragOffsetY = 0;

    private int mouseDownX, mouseDownY; // Mouse down position for drag threshold
    private final int DRAG_THRESHOLD = 5;

    private int windowX;          // Initial X position of the menu
    private int windowY;          // Initial Y position of the menu

    private final KeyBinding toggleKey;

    // Define the dimensions of the "window" inside the game screen
    private int windowWidth;
    private int windowHeight;

    // Menu name in menuconfig.json
    private final String menuName;

    // Track created widgets for position updates
    private final List<WidgetWithOffset> configWidgets = new ArrayList<>();

    public MainMenuScreen(Text title, KeyBinding toggleKey, String menuName) {
        super(title);
        this.toggleKey = toggleKey;
        this.menuName = menuName;
    }

    private static class WidgetWithOffset {
        ClickableWidget widget;
        int offsetX;
        int offsetY;

        WidgetWithOffset(ClickableWidget widget, int offsetX, int offsetY) {
            this.widget = widget;
            this.offsetX = offsetX;
            this.offsetY = offsetY;
        }
    }

    @Override
    protected void init() {

        // Clear previously created widgets if any
        configWidgets.clear();

        // Get screen dimensions
        assert this.client != null;
        int screenWidth = this.client.getWindow().getScaledWidth();
        int screenHeight = this.client.getWindow().getScaledHeight();

        //this.centerWindow(); // Center the window on screen

        // Load menu configuration from LayoutManager
        MenuConfig menuConfig = LayoutManager.getMenuConfig(menuName);

        if (menuConfig == null) {
            LOGGER.error("Menu configuration not found for: {}", menuName);
            this.close();
            return;
        }

        // Set window dimensions from config
        this.windowWidth = menuConfig.getWindowWidth();
        this.windowHeight = menuConfig.getWindowHeight();

        // Center the window initially
        this.windowX = (screenWidth - windowWidth) / 2;
        this.windowY = (screenHeight - windowHeight) / 2;

        // Create widgets from configuration
        if (menuConfig.getWidgets() != null) {
            for (WidgetConfig widgetConfig : menuConfig.getWidgets()) {
                createWidgetFromConfig(widgetConfig);
            }
        }

        // Update all widget positions
        updateWidgetPositions();
    }

    private void createWidgetFromConfig(WidgetConfig config) {
        // Calculate widget position relative to window
        int x = windowX + config.getX();
        int y = windowY + config.getY();

        int offsetX = config.getX();
        int offsetY = config.getY();

        // Create different widgets based on type using the builder pattern
        switch (config.getType().toLowerCase()) {
            case "button":
                ButtonWidget button = ButtonWidget.builder(
                                Text.of(config.getText()),
                                (btn) -> handleButtonAction(config.getAction())
                        ).dimensions(windowX + offsetX, windowY + offsetY, config.getWidth(), config.getHeight())
                        .build();

                this.addDrawableChild(button);
                configWidgets.add(new WidgetWithOffset(button, offsetX, offsetY));
                break;

            case "checkbox":
                boolean isChecked = config.getChecked() != null ? config.getChecked() : false;

                assert this.client != null; // Ensure client is not null
                CheckboxWidget checkbox = CheckboxWidget.builder(
                                Text.of(config.getText()),
                                this.client.textRenderer
                        ).pos(windowX + offsetX, windowY + offsetY)
                        .checked(isChecked)
                        .callback((checkboxWidget, checked) -> {
                            handleCheckboxAction(config.getAction(), checked);
                            LOGGER.info("Checkbox '{}' changed to: {}", config.getText(), checked);
                        })
                        .build();

                this.addDrawableChild(checkbox);
                configWidgets.add(new WidgetWithOffset(checkbox, offsetX, offsetY));
                break;

            case "slider":
                customSlider slider = new customSlider(
                        windowX + offsetX,
                        windowY + offsetY,
                        config.getWidth(),
                        config.getHeight(),
                        Text.of(config.getText()),
                        0.0) {
                    @Override
                    protected void applyValue() {
                        handleSliderAction(config.getAction(), this.value); // Call handleSliderAction here
                    }
                    @Override
                    protected void updateMessage() {
                        int radius = denormalizeRadius(this.value);
                        this.setMessage(Text.of(config.getText() + ": " + radius));
                    }
                };

                this.addDrawableChild(slider);
                configWidgets.add(new WidgetWithOffset(slider, offsetX, offsetY));
                break;

            default:
                LOGGER.warn("Unknown widget type: {}", config.getType());
                break;
        }
    }

    private void handleButtonAction(String action) {
        if (action == null) return;

        LOGGER.info("Button action: {}", action);

        switch (action) {
            case "close":
                this.close();
                break;

            // Add other actions as needed

            default:
                LOGGER.warn("Unknown button action: {}", action);
                break;
        }
    }

    private void handleCheckboxAction(String action, Boolean checked) {
        if (action == null) return;

        LOGGER.error("Checkbox action: {}", action);

        switch (action) {
            case "showHUD":
                RenderManager.setHudEnabled(checked);
                break;

            case "showOutlines":
                EntityOutlinerManager.setOutliningEnabled(checked);
                break;

            case "showEntityBoxes":
                RenderManager.setBoxEnabled(checked);
                break;

            // Add other actions as needed

            default:
                LOGGER.warn("Unknown button action: {}", action);
                break;
        }
    }

    private void handleSliderAction(String action, double value) {
        if (action == null) return;

        LOGGER.info("Slider action: {}, value: {}", action, value);

        switch (action) {
            case "detectionRadius":
                // Determine the current detection radius
                int currentRadius = TestRender.getDetectionRadius();

                // Recalculate slider value if necessary
                if (value == 0.0) { // Assume slider starts at 0.0 if unset
                    value = normalizeRadius(currentRadius);
                }

                // Update the detection radius with the slider value
                int updatedRadius = denormalizeRadius(value);
                TestRender.setDetectionRadius(updatedRadius);

                LOGGER.info("Detection radius updated to: {}", updatedRadius);
                break;

            // Add other slider-related actions if needed

            default:
                LOGGER.warn("Unknown slider action: {}", action);
                break;
        }
    }

    // Helper method to normalize radius to slider value (0.0 to 1.0)
    private double normalizeRadius(int radius) {
        // Convert radius value to slider position (0.0 to 1.0)
        return (radius - 1) / (512.0 - 1.0);
    }

    // Helper method to convert slider value to radius
    private int denormalizeRadius(double value) {
        // Convert slider position (0.0 to 1.0) to radius value
        return (int) Math.round(1 + value * (512 - 1));
    }

    private void centerWindow() {
        this.windowX = this.width / 2 - this.windowWidth / 2;
        this.windowY = this.height / 2 - this.windowHeight / 2;
    }

    // Custom slider class for detection radius
    private abstract static class customSlider extends SliderWidget {
        public customSlider(int x, int y, int width, int height, Text text, double value) {
            super(x, y, width, height, text, value);
        }
    }

    @Override
    public void render(DrawContext context, int mouseX, int mouseY, float delta) {

        // Render a semi-transparent background
        int windowColor = 0xAA000000; // Semi-transparent black
        int borderColor = 0xFFFFFFFF; // White border

        if (RenderManager.isMenuVisible()) {
            // Draw window background and border
            context.fill(windowX, windowY, windowX + windowWidth, windowY + windowHeight, windowColor);
            context.drawBorder(windowX, windowY, windowWidth, windowHeight, borderColor);

            // Render title at the top of the window
            context.drawText(
                    this.textRenderer,
                    this.title,
                    windowX + (windowWidth / 2) - (this.textRenderer.getWidth(this.title) / 2),
                    windowY + 6,
                    0xFFFFFF,
                    true
            );

            // Render all widgets
            super.render(context, mouseX, mouseY, delta);
        }
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (button == 0) {

            if (isMouseOverSlider(mouseX, mouseY)) {
                isDraggingSlider = true;
            }

            if (isMouseOverWindow(mouseX, mouseY)) {
                // Save the mouse down position but don't start dragging yet
                mouseDownX = (int) mouseX;
                mouseDownY = (int) mouseY;
                dragOffsetX = (int) mouseX - windowX;
                dragOffsetY = (int) mouseY - windowY;
                // Let the widgets handle the click too
            }
        }
        return super.mouseClicked(mouseX, mouseY, button);
    }

    @Override
    public boolean mouseReleased(double mouseX, double mouseY, int button) {
        if (button == 0) {
            isDraggingSlider = false;

            if (isDragging) {
                isDragging = false;
                return true; // Consume drag release
            }

            // If it's a short click and over the window, pass the click through to widgets
            if (isMouseOverWindow(mouseX, mouseY)) {
                return super.mouseReleased(mouseX, mouseY, button);
            }
        }

        return super.mouseReleased(mouseX, mouseY, button);
    }

    @Override
    public boolean mouseDragged(double mouseX, double mouseY, int button, double deltaX, double deltaY) {
        if (isDraggingSlider) {
            return super.mouseDragged(mouseX, mouseY, button, deltaX, deltaY);
        }

        // Allow dragging as long as you're over the window
        if (isMouseOverWindow(mouseX, mouseY)) {
            if (!isDragging && button == 0) {
                if (Math.abs(mouseX - mouseDownX) > DRAG_THRESHOLD || Math.abs(mouseY - mouseDownY) > DRAG_THRESHOLD) {
                    isDragging = true;
                }
            }

            if (isDragging && button == 0) {
                windowX = (int) mouseX - dragOffsetX;
                windowY = (int) mouseY - dragOffsetY;
                updateWidgetPositions();
                return true; // Consume the event
            }
        }
        return super.mouseDragged(mouseX, mouseY, button, deltaX, deltaY);
    }

    private boolean isMouseOverWidget(double mouseX, double mouseY) {
        for (var widget : this.children()) {
            if (widget instanceof SliderWidget slider && slider.isMouseOver(mouseX, mouseY)) {
                return true; // Block dragging only if mouse is over a slider
            }
        }
        return false;
    }

    private boolean isMouseOverSlider(double mouseX, double mouseY) {
        for (var widget : this.children()) {
            if (widget instanceof SliderWidget slider && slider.isMouseOver(mouseX, mouseY)) {
                return true;
            }
        }
        return false;
    }

    private void updateWidgetPositions() {
        for (WidgetWithOffset item : configWidgets) {
            item.widget.setX(windowX + item.offsetX);
            item.widget.setY(windowY + item.offsetY);
        }
    }

    private WidgetConfig findWidgetConfig(String text) {
        MenuConfig menuConfig = LayoutManager.getMenuConfig(menuName);
        if (menuConfig != null && menuConfig.getWidgets() != null) {
            for (WidgetConfig config : menuConfig.getWidgets()) {
                if (text.equals(config.getText())) {
                    return config;
                }
            }
        }
        return null;
    }

    private boolean isMouseOverWindow(double mouseX, double mouseY) {
        // Check if the mouse pointer is within the menu bounds
        return mouseX >= windowX && mouseX <= windowX + windowWidth
                && mouseY >= windowY && mouseY <= windowY + windowHeight;
    }

    @Override
    public boolean shouldPause() {
        return false; // Keep the game running while the menu is open
    }

    @Override
    public void renderBackground(DrawContext context, int mouseX, int mouseY, float delta) {
        // Do nothing to prevent Minecraft's default blurred background rendering
    }

    // Add these methods to allow player movement while the menu is open
    @Override
    public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
        // Handle only ESC and your toggle key
        if (keyCode == GLFW.GLFW_KEY_ESCAPE || toggleKey.matchesKey(keyCode, scanCode)) {
            this.close();
            return true;
        }

        // Allow the game to handle movement keys (WASD, space, etc.)
        return false; // Return false for key presses to pass through to the game
    }

    @Override
    public boolean keyReleased(int keyCode, int scanCode, int modifiers) {
        // Allow all key releases to pass through
        return false;
    }

    @Override
    public void tick() {
        super.tick();

        // Get Minecraft client instance
        net.minecraft.client.MinecraftClient client = net.minecraft.client.MinecraftClient.getInstance();

        // Make sure the mouse is locked (so player can move camera)
        if (!client.mouse.isCursorLocked()) {
            //client.mouse.lockCursor(); // This re-locks the mouse to the game window
        }

        // Unpause key bindings for movement
        KeyBinding[] movementKeys = {
                client.options.forwardKey,
                client.options.leftKey,
                client.options.backKey,
                client.options.rightKey,
                client.options.jumpKey,
                client.options.sneakKey
        };

        // Set these keys as unpressed
        for (KeyBinding key : movementKeys) {
            // Get the actual key code based on the binding
            int keyCode = key.getDefaultKey().getCode();

            // Check if the key is pressed and update the binding state
            boolean isPressed = GLFW.glfwGetKey(client.getWindow().getHandle(), keyCode) == GLFW.GLFW_PRESS;
            KeyBinding.setKeyPressed(InputUtil.fromKeyCode(keyCode, 0), isPressed);

            // If the key is pressed, force it to be processed
            if (isPressed) {
                KeyBinding.onKeyPressed(InputUtil.fromKeyCode(keyCode, 0));
            }
        }
    }

    @Override
    public void close() {
        RenderManager.toggleMenuVisible();
        assert Objects.requireNonNull(client).player != null;
        assert client.player != null;
        client.player.sendMessage(Text.literal("Persistent menu: " + (RenderManager.isMenuVisible() ? "ON" : "OFF")), true);
        assert this.client != null;
        this.client.setScreen(null);
    }
}
