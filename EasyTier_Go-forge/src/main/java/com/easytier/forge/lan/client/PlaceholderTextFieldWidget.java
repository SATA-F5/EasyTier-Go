package com.easytier.forge.lan.client;

import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.network.chat.Component;

/**
 * 带淡灰色提示文字的输入框
 *
 * 注意：不要在 insertText/write 中做全局字符过滤。
 * 数字限制只允许通过 {@link #setNumericOnly(boolean)} 显式开启，
 * 避免网络名称、中继地址、密码等文本字段被误设成只能输入数字。
 */
public class PlaceholderTextFieldWidget extends EditBox {

    private static final int PLACEHOLDER_COLOR = 0x808080;
    private final Font fontRef;
    private Component placeholderText;
    private boolean numericOnly = false;

    public PlaceholderTextFieldWidget(Font font, int x, int y, int width, int height, Component text) {
        super(font, x, y, width, height, text);
        this.fontRef = font;
    }

    public void setPlaceholder(Component placeholder) {
        this.placeholderText = placeholder;
    }

    /**
     * 仅端口等数字字段需要调用；默认关闭，文本字段保持自由输入。
     */
    public void setNumericOnly(boolean numericOnly) {
        this.numericOnly = numericOnly;
        this.setFilter(numericOnly ? value -> value.matches("[0-9]*") : null);
    }

    public boolean isNumericOnly() {
        return numericOnly;
    }

    @Override
    public void renderWidget(GuiGraphics guiGraphics, int mouseX, int mouseY, float delta) {
        super.renderWidget(guiGraphics, mouseX, mouseY, delta);
        if (this.getValue().isEmpty() && placeholderText != null && !this.isFocused()) {
            String text = placeholderText.getString();
            if (!text.isEmpty()) {
                int color = this.isActive() ? PLACEHOLDER_COLOR : 0x606060;
                guiGraphics.drawString(fontRef, text,
                        this.getX() + 4, this.getY() + (this.height - 8) / 2, color, false);
            }
        }
    }
}
