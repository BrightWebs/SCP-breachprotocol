package com.breachprotocol.client;

import com.breachprotocol.network.DialogueActionPayload;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.network.chat.Component;
import net.neoforged.neoforge.network.PacketDistributor;

import java.util.*;

/** The family tree (after MCA's): generations in rows, click anyone to see their family. */
public class FamilyTreeScreen extends Screen {
    private final Screen back;
    private CompoundTag data;
    private final List<int[]> boxes = new ArrayList<>();
    private final List<CompoundTag> boxNodes = new ArrayList<>();

    public FamilyTreeScreen(CompoundTag tag, Screen back) {
        super(Component.literal(tag.getString("title")));
        this.data = tag;
        this.back = back instanceof FamilyTreeScreen f ? f.back : back;
    }

    public void update(CompoundTag tag) { data = tag; }

    @Override
    protected void init() {
        addRenderableWidget(Button.builder(Component.literal("Back"), b -> onClose()).bounds(10, height - 30, 60, 20).build());
    }

    @Override
    public void onClose() { Minecraft.getInstance().setScreen(back); }

    @Override
    public boolean isPauseScreen() { return false; }

    private static final String[] ROWS = {"Grandparents", "Parents", "", "Children", "Grandchildren"};

    @Override
    public void renderBackground(GuiGraphics g, int mx, int my, float pt) { g.fill(0, 0, width, height, 0xD0101418); }

    @Override
    public void render(GuiGraphics g, int mx, int my, float pt) {
        super.render(g, mx, my, pt);
        g.drawCenteredString(font, data.getString("title"), width / 2, 10, 0xFFE0C060);
        ListTag nodes = data.getList("nodes", Tag.TAG_COMPOUND);
        Map<Integer, List<CompoundTag>> rows = new TreeMap<>();
        for (Tag t : nodes) rows.computeIfAbsent(((CompoundTag) t).getInt("row"), k -> new ArrayList<>()).add((CompoundTag) t);
        boxes.clear();
        boxNodes.clear();
        int bw = 104, bh = 34, gap = 8;
        int rowH = (height - 70) / 5;
        int[] centre = null;
        Map<Integer, List<int[]>> placed = new HashMap<>();
        for (Map.Entry<Integer, List<CompoundTag>> e : rows.entrySet()) {
            int row = e.getKey();
            List<CompoundTag> list = e.getValue();
            int y = 30 + (row + 2) * rowH;
            int total = list.size() * bw + (list.size() - 1) * gap;
            int x = width / 2 - total / 2;
            if (!ROWS[row + 2].isEmpty()) g.drawString(font, ROWS[row + 2], 8, y + bh / 2 - 4, 0xFF8090A0);
            for (CompoundTag n : list) {
                int[] b = {x, y, bw, bh};
                boxes.add(b);
                boxNodes.add(n);
                placed.computeIfAbsent(row, k -> new ArrayList<>()).add(b);
                if (row == 0 && n.getString("rel").isEmpty()) centre = b;
                x += bw + gap;
            }
        }
        // lines: everyone to the person in the middle
        if (centre != null) {
            int cx = centre[0] + bw / 2, cy = centre[1] + bh / 2;
            for (Map.Entry<Integer, List<int[]>> e : placed.entrySet()) {
                for (int[] b : e.getValue()) {
                    if (b == centre) continue;
                    int bx = b[0] + bw / 2;
                    if (e.getKey() == 0) { g.hLine(Math.min(cx, bx), Math.max(cx, bx), cy, 0x60FFFFFF); continue; }
                    int fromY = e.getKey() < 0 ? b[1] + bh : b[1], toY = e.getKey() < 0 ? centre[1] : centre[1] + bh;
                    int midY = (fromY + toY) / 2;
                    g.vLine(bx, Math.min(fromY, midY), Math.max(fromY, midY), 0x60FFFFFF);
                    g.hLine(Math.min(cx, bx), Math.max(cx, bx), midY, 0x60FFFFFF);
                    g.vLine(cx, Math.min(midY, toY), Math.max(midY, toY), 0x60FFFFFF);
                }
            }
        }
        CompoundTag hover = null;
        for (int i = 0; i < boxes.size(); i++) {
            int[] b = boxes.get(i);
            CompoundTag n = boxNodes.get(i);
            boolean over = mx >= b[0] && mx < b[0] + b[2] && my >= b[1] && my < b[1] + b[3];
            if (over) hover = n;
            boolean alive = n.getBoolean("alive");
            int border = n.getBoolean("player") ? 0xFFFFD700 : n.getBoolean("female") ? 0xFFE08AB4 : 0xFF7AA8E0;
            if (!alive) border = 0xFF707070;
            g.fill(b[0] - 1, b[1] - 1, b[0] + b[2] + 1, b[1] + b[3] + 1, over ? 0xFFFFFFFF : border);
            g.fill(b[0], b[1], b[0] + b[2], b[1] + b[3], n.getString("rel").isEmpty() ? 0xFF2A3440 : 0xFF1A2028);
            String name = (alive ? "" : "† ") + n.getString("name");
            g.drawCenteredString(font, font.plainSubstrByWidth(name, b[2] - 6), b[0] + b[2] / 2, b[1] + 4, alive ? 0xFFFFFFFF : 0xFFA0A0A0);
            String rel = n.getString("rel");
            g.drawCenteredString(font, font.plainSubstrByWidth((rel.isEmpty() ? "" : Character.toUpperCase(rel.charAt(0)) + rel.substring(1) + " · ") + n.getInt("age"), b[2] - 6), b[0] + b[2] / 2, b[1] + 14, 0xFFB0B8C4);
            g.drawCenteredString(font, font.plainSubstrByWidth(n.getString("job"), b[2] - 6), b[0] + b[2] / 2, b[1] + 24, 0xFF8090A0);
        }
        if (hover != null) {
            List<Component> tip = new ArrayList<>();
            tip.add(Component.literal(hover.getString("name")).withColor(0xFFE0C060));
            tip.add(Component.literal(hover.getString("job")));
            if (!hover.getString("relState").isEmpty()) tip.add(Component.literal("Status: " + hover.getString("relState")).withColor(0xFFB0B8C4));
            if (!hover.getString("traits").isEmpty()) tip.add(Component.literal("Traits: " + hover.getString("traits")).withColor(0xFF9FD8FF));
            tip.add(Component.literal("Click to see their family").withColor(0xFF808080));
            g.renderComponentTooltip(font, tip, mx, my);
        }
    }

    @Override
    public boolean mouseClicked(double mx, double my, int button) {
        for (int i = 0; i < boxes.size(); i++) {
            int[] b = boxes.get(i);
            if (mx >= b[0] && mx < b[0] + b[2] && my >= b[1] && my < b[1] + b[3] && !boxNodes.get(i).getString("rel").isEmpty()) {
                PacketDistributor.sendToServer(new DialogueActionPayload("/familytree " + boxNodes.get(i).getString("id"), ""));
                return true;
            }
        }
        return super.mouseClicked(mx, my, button);
    }
}
