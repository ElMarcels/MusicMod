package com.musicmod.gui;

import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.widget.AlwaysSelectedEntryListWidget;
import net.minecraft.client.gui.widget.ElementListWidget;

import java.util.List;

public abstract class WidgetListWidget<E extends ElementListWidget.Entry<E>> extends AlwaysSelectedEntryListWidget<E> {
    protected final Screen screen;

    public WidgetListWidget(Screen screen, int width, int height, int y, int itemHeight) {
        super(MinecraftClient.getInstance(), width, height, y, itemHeight);
        this.screen = screen;
    }

    public void addEntry(E entry) {
        addEntryToEnd(entry);
    }
}
