package com.easytier.fabric.lan.client;

import net.minecraft.client.gui.widget.EntryListWidget;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.narration.NarrationMessageBuilder;
import net.minecraft.text.Text;

import java.util.List;
import java.util.function.Consumer;

/**
 * 配置选择列表控件
 * 以可滚动列表形式展示所有网络配置，替代原来的下拉按钮
 */
public class ConfigListWidget extends EntryListWidget<ConfigListWidget.ConfigEntry> {

    private final Consumer<String> selectionCallback;
    private final int topY;

    public ConfigListWidget(MinecraftClient client, int width, int height, int top, int bottom,
                            int itemHeight, Consumer<String> selectionCallback) {
        super(client, width, height, top, bottom, itemHeight);
        this.selectionCallback = selectionCallback;
        this.topY = top;
    }

    public int getTopY() {
        return topY;
    }

    public void updateEntries(List<String> networkNames, String selected) {
        this.replaceEntries(List.of());
        for (String name : networkNames) {
            ConfigEntry entry = new ConfigEntry(name);
            this.addEntry(entry);
            if (name.equals(selected)) {
                this.setSelected(entry);
            }
        }
    }

    @Override
    public void setSelected(ConfigEntry entry) {
        super.setSelected(entry);
        if (entry != null && selectionCallback != null) {
            selectionCallback.accept(entry.name);
        }
    }

    @Override
    protected int getScrollbarPositionX() {
        return this.right - 6;
    }

    @Override
    public int getRowWidth() {
        return this.width - 12;
    }

    @Override
    public void appendNarrations(NarrationMessageBuilder builder) {
        ConfigEntry selected = this.getSelectedOrNull();
        if (selected != null) {
            builder.put(net.minecraft.client.gui.screen.narration.NarrationPart.USAGE,
                    Text.literal("配置: " + selected.name));
        }
    }

    public class ConfigEntry extends EntryListWidget.Entry<ConfigEntry> {
        final String name;

        ConfigEntry(String name) {
            this.name = name;
        }

        @Override
        public void render(DrawContext context, int index, int y, int x, int entryWidth,
                           int entryHeight, int mouseX, int mouseY, boolean hovered, float tickDelta) {
            int color = hovered ? 0xFFFFFF : 0xC0C0C0;
            if (ConfigListWidget.this.getSelectedOrNull() == this) {
                color = 0x55FF55;
            }
            context.drawTextWithShadow(client.textRenderer, name, x + 4, y + 4, color);
            // 版本数提示
            context.drawTextWithShadow(client.textRenderer, Text.literal("点击加载"),
                    x + entryWidth - 60, y + 4, 0x808080);
        }

        @Override
        public boolean mouseClicked(double mouseX, double mouseY, int button) {
            if (button == 0) {
                ConfigListWidget.this.setSelected(this);
                return true;
            }
            return false;
        }
    }
}
