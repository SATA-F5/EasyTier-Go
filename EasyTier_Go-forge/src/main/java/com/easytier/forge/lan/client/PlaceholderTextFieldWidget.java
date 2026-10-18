package com.easytier.forge.lan.client;

import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.network.chat.Component;

/**
 * 带淡灰色提示文字的输入框
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

    public void setNumericOnly(boolean numericOnly) {
        this.numericOnly = numericOnly;
    }

    @Override
    public void insertText(String text) {
        if (numericOnly) {
            String filtered = text.replaceAll("[^0-9]", "");
            if (!filtered.isEmpty()) {
                super.insertText(filtered);
            }
        } else {
            super.insertText(text);
        }
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
