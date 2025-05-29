package net.natga999.wynn_ai.menus.widgets;

import net.natga999.wynn_ai.managers.HarvestingManager;
import net.natga999.wynn_ai.managers.ResourceNodeManager;

import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;

import java.util.ArrayList;
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

        // 1) grab the flat list
        List<String> all = new ArrayList<>(ResourceNodeManager.getSortedResources());
        // 2) we know the four group sizes up front
        int[] groupSizes = { 15, 13, 14, 13 };
        // 3) slice into four column‐lists
        List<List<String>> columns = new ArrayList<>();
        int index = 0;
        for (int size : groupSizes) {
            int end = Math.min(index + size, all.size());
            columns.add(all.subList(index, end));
            index = end;
        }

        // layout parameters
        int columnsCount = columns.size();
        int itemWidth    = width  / columnsCount;
        int itemHeight   = 20;

        // 4) render column‐major
        for (int col = 0; col < columnsCount; col++) {
            List<String> group = columns.get(col);
            int itemX = drawX + col * itemWidth;

            for (int row = 0; row < group.size(); row++) {
                int itemY = drawY + row * itemHeight;

                String resource = group.get(row);
                boolean isSelected = resource.equals(selectedResource);

                // item background
                context.fill(
                        itemX, itemY,
                        itemX + itemWidth, itemY + itemHeight,
                        isSelected ? 0xAA00FF00 : 0xAA444444
                );

                // text
                context.drawText(
                        client.textRenderer,
                        resource,
                        itemX + 2, itemY + 6,
                        isSelected ? 0xFFFFFF00 : 0xFFFFFF,
                        false
                );
            }
        }
    }


    public boolean isMouseOver(double mouseX, double mouseY, int menuX, int menuY) {
        return mouseX >= menuX + x && mouseX <= menuX + x + width &&
                mouseY >= menuY + y && mouseY <= menuY + y + height;
    }

    public boolean mouseClicked(double mouseX, double mouseY) {
        int relX = (int)(mouseX - x);
        int relY = (int)(mouseY - y);

        if (relX < 0 || relX >= width || relY < 0 || relY >= height) {
            return false;
        }

        // your hardcoded column sizes
        int[] groupSizes = { 15, 13, 14, 13 };
        int columns    = groupSizes.length;
        int itemWidth  = width  / columns;
        int itemHeight = 20;

        int col = relX / itemWidth;
        int row = relY / itemHeight;

        // out‐of‐bounds row
        if (row >= groupSizes[col]) {
            return false;
        }

        // compute offset = sum of sizes of all columns before 'col'
        int offset = 0;
        for (int i = 0; i < col; i++) {
            offset += groupSizes[i];
        }

        int flatIndex = offset + row;
        List<String> resources = ResourceNodeManager.getSortedResources();
        if (flatIndex < 0 || flatIndex >= resources.size()) {
            return false;
        }

        String clicked = resources.get(flatIndex);
        HarvestingManager.setActiveResource(clicked);
        return true;
    }
}