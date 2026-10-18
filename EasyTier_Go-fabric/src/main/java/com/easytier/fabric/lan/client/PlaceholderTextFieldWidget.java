package com.easytier.fabric.lan.client;

import net.minecraft.client.font.TextRenderer;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.widget.TextFieldWidget;
import net.minecraft.text.Text;

/**
 * 带淡灰色提示文字的输入框
 * 提示文字使用 0x808080 灰色，与原生输入文字形成对比度
 */
public class PlaceholderTextFieldWidget extends TextFieldWidget {

    private static final int PLACEHOLDER_COLOR = 0x808080;
    private final TextRenderer textRendererRef;
    private Text placeholderText;

    public PlaceholderTextFieldWidget(TextRenderer textRenderer, int x, int y, int width, int height, Text text) {
        super(textRenderer, x, y, width, height, text);
        this.textRendererRef = textRenderer;
    }

    @Override
    public void setPlaceholder(Text placeholder) {
        this.placeholderText = placeholder;
        super.setPlaceholder(placeholder);
    }

    @Override
    public void write(String text) {
        // 只允许输入数字
        String filtered = text.replaceAll("[^0-9]", "");
        if (!filtered.isEmpty()) {
            super.write(filtered);
        }
    }

    @Override
    public void render(DrawContext context, int mouseX, int mouseY, float delta) {
        super.render(context, mouseX, mouseY, delta);
        if (this.getText().isEmpty() && placeholderText != null && !this.isFocused()) {
            String text = placeholderText.getString();
            if (!text.isEmpty()) {
                int color = this.active ? PLACEHOLDER_COLOR : 0x606060;
                context.drawTextWithShadow(textRendererRef, text,
                        this.getX() + 4, this.getY() + (this.height - 8) / 2, color);
            }
        }
    }
}
