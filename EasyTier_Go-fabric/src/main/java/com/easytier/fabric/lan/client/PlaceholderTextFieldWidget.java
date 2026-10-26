package com.easytier.fabric.lan.client;

import net.minecraft.client.font.TextRenderer;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.widget.TextFieldWidget;
import net.minecraft.text.Text;

/**
 * 带淡灰色提示文字的输入框
 * 提示文字使用 0x808080 灰色，与原生输入文字形成对比度
 *
 * 注意：不要在 write 中做全局字符过滤。
 * 数字限制只允许通过 {@link #setNumericOnly(boolean)} 显式开启，
 * 避免网络名称、中继地址、密码等文本字段被误设成只能输入数字。
 */
public class PlaceholderTextFieldWidget extends TextFieldWidget {

    private static final int PLACEHOLDER_COLOR = 0x808080;
    private final TextRenderer textRendererRef;
    private Text placeholderText;
    private boolean numericOnly = false;

    public PlaceholderTextFieldWidget(TextRenderer textRenderer, int x, int y, int width, int height, Text text) {
        super(textRenderer, x, y, width, height, text);
        this.textRendererRef = textRenderer;
        // 默认允许所有字符；只有端口字段会显式切换到纯数字过滤
        this.setTextPredicate(value -> true);
    }

    @Override
    public void setPlaceholder(Text placeholder) {
        this.placeholderText = placeholder;
        super.setPlaceholder(placeholder);
    }

    /**
     * 仅端口等数字字段需要调用；默认关闭，文本字段保持自由输入。
     */
    public void setNumericOnly(boolean numericOnly) {
        this.numericOnly = numericOnly;
        this.setTextPredicate(numericOnly ? value -> value.matches("[0-9]*") : value -> true);
    }

    public boolean isNumericOnly() {
        return numericOnly;
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
