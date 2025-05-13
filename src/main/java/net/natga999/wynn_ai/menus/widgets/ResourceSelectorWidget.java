package net.natga999.wynn_ai.menus.widgets;

import net.natga999.wynn_ai.managers.HarvestingManager;
import net.natga999.wynn_ai.managers.ResourceNodeManager;

import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;

import java.util.List;

public class ResourceSelectorWidget implements MenuWidget {
    private final int x, y, width, height;
    private String selectedResource;

    public ResourceSelectorWidget(int x, int y, int width, int height) {
        this.x = x;
        this.y = y;
        this.width = width;
        this.height = height;
        this.selectedResource = HarvestingManager.getActiveResource();
        HarvestingManager.addChangeListener(this::onResourceChanged);
    }

    private void onResourceChanged() {
        // Force redraw when resource changes
        this.selectedResource = HarvestingManager.getActiveResource();
    }

    @Override
    public void render(DrawContext context, MinecraftClient client, int parentX, int parentY) {
        this.selectedResource = HarvestingManager.getActiveResource();
        int drawX = parentX + x;
        int drawY = parentY + y;

        // Background
        context.fill(drawX, drawY, drawX + width, drawY + height, 0xAA000000);

        // Get all harvestable resources
        List<String> resources = ResourceNodeManager.getSortedResources();

        // Calculate rows and columns
        int columns = 2;
        int itemWidth = width / columns;
        int itemHeight = 20;

        // Scrollable content
        for (int i = 0; i < resources.size(); i++) {
            int column = i % columns;
            int row = i / columns;

            int itemX = drawX + column * itemWidth;
            int itemY = drawY + row * itemHeight;

            String resource = resources.get(i);
            boolean isSelected = resource.equals(selectedResource);

            // Draw item background
            context.fill(itemX, itemY, itemX + itemWidth, itemY + itemHeight,
                    isSelected ? 0xAA00FF00 : 0xAA444444);

            // Draw resource name
            context.drawText(client.textRenderer, resource,
                    itemX + 2, itemY + 6,
                    isSelected ? 0xFFFFFF00 : 0xFFFFFF,
                    false);
        }
    }

    public boolean isMouseOver(double mouseX, double mouseY, int menuX, int menuY) {
        return mouseX >= menuX + x && mouseX <= menuX + x + width &&
                mouseY >= menuY + y && mouseY <= menuY + y + height;
    }

    public boolean mouseClicked(double mouseX, double mouseY) {
        // Calculate which resource was clicked
        int relativeX = (int) (mouseX - x);
        int relativeY = (int) (mouseY - y);

        if (relativeX >= 0 && relativeX < width &&
                relativeY >= 0 && relativeY < height) {

            int columns = 2;
            int itemWidth = width / columns;
            int itemHeight = 20;

            int col = relativeX / itemWidth;
            int row = relativeY / itemHeight;

            int index = row * columns + col;
            List<String> resources = ResourceNodeManager.getSortedResources();

            if (index < resources.size()) {
                String newResource = resources.get(index);
                HarvestingManager.setActiveResource(newResource); // Centralized update
                return true;
            }
        }
        return false;
    }
}