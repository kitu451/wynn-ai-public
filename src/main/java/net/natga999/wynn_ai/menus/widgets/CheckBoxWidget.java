package net.natga999.wynn_ai.menus.widgets;

import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;

import static net.natga999.wynn_ai.menus.MenuHUDLoader.getCheckboxState;
import static net.natga999.wynn_ai.menus.MenuHUDLoader.setCheckboxState;

public class CheckBoxWidget implements MenuWidget{
    private final int x, y, width, height;
    private final String text;
    private final String action;
    private boolean checked;

    public CheckBoxWidget(int x, int y, int width, int height, String text, String action, Boolean checkedDefault) {
        this.x = x;
        this.y = y;
        this.width = width;
        this.height = height;
        this.text = text;
        this.action = action;
        this.checked = getCheckboxState(action) || checkedDefault;
    }

    @Override
    public void render(DrawContext context, MinecraftClient client, int parentX, int parentY) {
        int drawX = parentX + x;
        int drawY = parentY + y;
        context.drawBorder(drawX, drawY, width, height, 0xFFFFFFFF);
        context.fill(drawX, drawY, drawX + width, drawY + height, 0x80000000); // Semi-transparent box
        context.drawBorder(drawX + 3, drawY + 3, height - 6, height - 6, 0xFFFFFFFF);
        context.drawText(client.textRenderer, text, drawX + height + (width - height - client.textRenderer.getWidth(text)
        ) / 2, drawY + (height - client.textRenderer.fontHeight + 1) / 2, 0xFFFFFF, false);

        if (checked) {
            context.fill(drawX + 5, drawY + 5, drawX + height - 5, drawY + height - 5, 0xFF00FF00); // Green box
        }
    }

    public boolean isChecked() {
        return checked;
    }

    public void setChecked(boolean checked) {
        this.checked = checked;
        setCheckboxState(action, checked);
    }

    public boolean isMouseOver(double mouseX, double mouseY, int menuX, int menuY) {
        return mouseX >= menuX + x && mouseX <= menuX + x + width &&
                mouseY >= menuY + y && mouseY <= menuY + y + height;
    }

    public String getAction() {
        return action;
    }
}
