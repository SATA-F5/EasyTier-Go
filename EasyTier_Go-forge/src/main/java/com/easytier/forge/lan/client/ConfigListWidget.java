package com.easytier.forge.lan.client;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.AbstractSelectionList;
import net.minecraft.client.gui.narration.NarrationElementOutput;
import net.minecraft.network.chat.Component;

import java.util.List;
import java.util.function.Consumer;

/**
 * 配置选择列表控件
 */
public class ConfigListWidget extends AbstractSelectionList<ConfigListWidget.ConfigEntry> {

    private final Consumer<String> selectionCallback;
    private final int topY;

    public ConfigListWidget(Minecraft minecraft, int width, int height, int top, int bottom,
                            int itemHeight, Consumer<String> selectionCallback) {
        super(minecraft, width, height, top, bottom, itemHeight);
        this.selectionCallback = selectionCallback;
        this.topY = top;
    }

    public int getTopY() {
        return topY;
    }

    public void updateEntries(List<String> networkNames, String selected) {
        this.clearEntries();
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
    protected int getScrollbarPosition() {
        return this.getRight() - 6;
    }

    @Override
    public int getRowWidth() {
        return this.width - 12;
    }

    @Override
    public void updateNarration(NarrationElementOutput builder) {
        ConfigEntry selected = this.getSelected();
        if (selected != null) {
            builder.add(net.minecraft.client.gui.narration.NarratedElementType.USAGE,
                    Component.literal("配置: " + selected.name));
        }
    }

    public class ConfigEntry extends AbstractSelectionList.Entry<ConfigEntry> {
        final String name;

        ConfigEntry(String name) {
            this.name = name;
        }

        @Override
        public void render(GuiGraphics guiGraphics, int index, int y, int x, int entryWidth,
                           int entryHeight, int mouseX, int mouseY, boolean hovered, float tickDelta) {
            int color = hovered ? 0xFFFFFF : 0xC0C0C0;
            if (ConfigListWidget.this.getSelected() == this) {
                color = 0x55FF55;
            }
            guiGraphics.drawString(minecraft.font, name, x + 4, y + 4, color, false);
            guiGraphics.drawString(minecraft.font, Component.literal("点击加载"),
                    x + entryWidth - 60, y + 4, 0x808080, false);
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
