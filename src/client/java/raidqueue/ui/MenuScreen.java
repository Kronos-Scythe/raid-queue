package raidqueue.ui;

import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.minecraft.block.BlockState;
import net.minecraft.component.DataComponentTypes;
import net.minecraft.component.type.LoreComponent;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.texture.Sprite;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.item.ItemStack;
import net.minecraft.registry.Registries;
import net.minecraft.text.OrderedText;
import net.minecraft.text.Text;
import net.minecraft.util.math.MathHelper;
import raidqueue.network.ClickPayload;
import raidqueue.network.View;

import java.util.ArrayList;
import java.util.List;

/**
 * Client-side renderer for a {@link View}. Everything is laid out in a fixed 460x240
 * virtual coordinate space, then scaled (in whole-pixel steps, so text stays sharp at
 * any GUI scale) to fill about 90% of the window.
 */
public final class MenuScreen extends Screen {
    private static final int VIRTUAL_WIDTH = 460;
    private static final int VIRTUAL_HEIGHT = 240;
    private static final double FILL_FRACTION = 0.9;

    // Palette
    private static final int FRAME_FILL = 0xF00E1322;
    private static final int FRAME_BORDER = 0xFF7B4DFF;
    private static final int PANEL_FILL = 0xE0151C31;
    private static final int PARTY_BORDER = 0xFF3FB56B;
    private static final int PROGRESS_BORDER = 0xFF3D8BFF;
    private static final int OPTIONS_BORDER = 0xFFA13DFF;
    private static final int CARD_FILL = 0xE0202A48;
    private static final int CARD_BORDER = 0xFF5A4A9A;
    private static final int CARD_BORDER_HOVER = 0xFFFFFFFF;
    private static final int BACKGROUND_OVERLAY = 0xB0000000;
    private static final int TEXT_MAIN = 0xFFF5F3FF;
    private static final int TEXT_DIM = 0xFFB9B2E0;

    private final View view;
    private final List<ClickZone> clickZones = new ArrayList<>();
    private boolean replacedByServer = false;

    private double originX, originY, scale;

    public MenuScreen(View view) {
        super(view.header().title());
        this.view = view;
    }

    /** Set by ClientViewNetworking right before swapping to another MenuScreen, so removed() doesn't send a spurious close. */
    public void markReplaced() {
        this.replacedByServer = true;
    }

    @Override
    public boolean shouldPause() {
        return false;
    }

    @Override
    protected void init() {
        // Everything is custom-drawn; no vanilla widgets to add.
    }

    @Override
    public void render(DrawContext context, int mouseX, int mouseY, float delta) {
        this.clickZones.clear();
        computeTransform();

        MatrixStack matrices = context.getMatrices();
        matrices.push();
        matrices.translate((float) this.originX, (float) this.originY, 0);
        matrices.scale((float) this.scale, (float) this.scale, 1f);

        double[] localMouse = toVirtual(mouseX, mouseY);
        int vMouseX = (int) Math.round(localMouse[0]);
        int vMouseY = (int) Math.round(localMouse[1]);

        drawBackground(context);
        context.fill(0, 0, VIRTUAL_WIDTH, VIRTUAL_HEIGHT, FRAME_FILL);
        context.drawBorder(0, 0, VIRTUAL_WIDTH, VIRTUAL_HEIGHT, FRAME_BORDER);

        drawHeader(context);
        drawBody(context, vMouseX, vMouseY);
        drawFooter(context, vMouseX, vMouseY);

        matrices.pop();

        // Tooltips are drawn after popping the matrix, at the real (unscaled) mouse position.
        for (ClickZone zone : this.clickZones) {
            if (zone.tooltipStack != null && zone.containsReal(mouseX, mouseY)) {
                context.drawItemTooltip(this.textRenderer, zone.tooltipStack, mouseX, mouseY);
                break;
            }
        }
    }

    private void computeTransform() {
        double fitX = this.width * FILL_FRACTION / VIRTUAL_WIDTH;
        double fitY = this.height * FILL_FRACTION / VIRTUAL_HEIGHT;
        double fit = Math.min(fitX, fitY);

        double guiScale = this.client != null ? this.client.getWindow().getScaleFactor() : 1.0;
        double stepped = Math.floor(fit * guiScale) / guiScale;
        this.scale = Math.max(stepped, 1.0);

        this.originX = (this.width - VIRTUAL_WIDTH * this.scale) / 2.0;
        this.originY = (this.height - VIRTUAL_HEIGHT * this.scale) / 2.0;
    }

    private double[] toVirtual(double realX, double realY) {
        return new double[] { (realX - this.originX) / this.scale, (realY - this.originY) / this.scale };
    }

    private double[] toReal(double virtualX, double virtualY) {
        return new double[] { this.originX + virtualX * this.scale, this.originY + virtualY * this.scale };
    }

    // ---- background -------------------------------------------------------------------

    private void drawBackground(DrawContext context) {
        if (this.client == null) return;
        BlockState state = Registries.BLOCK.get(this.view.header().background()).getDefaultState();
        Sprite sprite = this.client.getBlockRenderManager().getModels().getModelParticleSprite(state);

        int tile = 32;
        for (int x = 0; x < VIRTUAL_WIDTH; x += tile) {
            for (int y = 0; y < VIRTUAL_HEIGHT; y += tile) {
                int w = Math.min(tile, VIRTUAL_WIDTH - x);
                int h = Math.min(tile, VIRTUAL_HEIGHT - y);
                context.drawSprite(x, y, 0, w, h, sprite);
            }
        }
        context.fill(0, 0, VIRTUAL_WIDTH, VIRTUAL_HEIGHT, BACKGROUND_OVERLAY);
    }

    // ---- header -------------------------------------------------------------------------

    private static final int HEADER_HEIGHT = 22;

    private void drawHeader(DrawContext context) {
        context.drawText(this.textRenderer, this.view.header().title(), 10, 8, TEXT_MAIN, true);

        Text badge = this.view.header().badge();
        if (badge != null && !badge.getString().isEmpty()) {
            int width = this.textRenderer.getWidth(badge);
            context.drawText(this.textRenderer, badge, VIRTUAL_WIDTH - width - 10, 8, TEXT_DIM, true);
        }

        context.fill(8, HEADER_HEIGHT, VIRTUAL_WIDTH - 8, HEADER_HEIGHT + 1, FRAME_BORDER);
    }

    // ---- body: party (left) / options (middle) / progress+stats (right) -----------------

    private static final int BODY_TOP = HEADER_HEIGHT + 6;
    private static final int FOOTER_HEIGHT = 26;
    private static final int SIDE_WIDTH = 108;
    private static final int MARGIN = 8;

    private void drawBody(DrawContext context, int mouseX, int mouseY) {
        int bodyBottom = VIRTUAL_HEIGHT - FOOTER_HEIGHT;
        boolean hasParty = !this.view.panels().party().isEmpty();
        boolean hasSide = !this.view.panels().stats().isEmpty() || this.view.panels().progress().isPresent();

        int leftX = MARGIN;
        int rightEdge = VIRTUAL_WIDTH - MARGIN;
        int middleLeft = hasParty ? leftX + SIDE_WIDTH + MARGIN : leftX;
        int middleRight = hasSide ? rightEdge - SIDE_WIDTH - MARGIN : rightEdge;

        if (hasParty) drawPartyPanel(context, leftX, BODY_TOP, SIDE_WIDTH, bodyBottom - BODY_TOP);
        if (hasSide) drawSidePanel(context, middleRight + MARGIN, BODY_TOP, SIDE_WIDTH, bodyBottom - BODY_TOP);

        drawOptions(context, middleLeft, BODY_TOP, middleRight - middleLeft, bodyBottom - BODY_TOP, mouseX, mouseY);
    }

    private void drawPartyPanel(DrawContext context, int x, int y, int w, int h) {
        context.fill(x, y, x + w, y + h, PANEL_FILL);
        context.drawBorder(x, y, w, h, PARTY_BORDER);

        int rowHeight = 20;
        int cy = y + 4;
        for (PartyEntry entry : this.view.panels().party()) {
            if (cy + rowHeight > y + h) break;
            context.drawItem(entry.icon(), x + 4, cy);
            context.drawText(this.textRenderer, entry.name(), x + 24, cy, TEXT_MAIN, false);
            context.drawText(this.textRenderer, entry.line1(), x + 24, cy + 9, TEXT_DIM, false);
            cy += rowHeight;
        }
    }

    private void drawSidePanel(DrawContext context, int x, int y, int w, int h) {
        context.fill(x, y, x + w, y + h, PANEL_FILL);
        context.drawBorder(x, y, w, h, PROGRESS_BORDER);

        int cy = y + 4;
        for (StatLine stat : this.view.panels().stats()) {
            context.drawText(this.textRenderer, stat.label(), x + 5, cy, TEXT_DIM, false);
            int valueWidth = this.textRenderer.getWidth(stat.value());
            context.drawText(this.textRenderer, stat.value(), x + w - valueWidth - 5, cy, TEXT_MAIN, false);
            cy += 10;
        }

        ProgressInfo progress = this.view.panels().progress();
        if (progress.isPresent()) {
            cy += 4;
            context.drawText(this.textRenderer, progress.title(), x + 5, cy, TEXT_DIM, false);
            cy += 10;
            int barX = x + 5;
            int barW = w - 10;
            int barY = cy;
            int barH = 6;
            context.fill(barX, barY, barX + barW, barY + barH, 0xFF11182C);
            float ratio = MathHelper.clamp((float) progress.current() / progress.max(), 0f, 1f);
            int filled = (int) (barW * ratio);
            if (filled > 0) context.fill(barX, barY, barX + filled, barY + barH, PROGRESS_BORDER);
            context.drawBorder(barX, barY, barW, barH, FRAME_BORDER);
        }
    }

    // ---- options (CONTENT slots, laid out per Layout) ------------------------------------

    private void drawOptions(DrawContext context, int x, int y, int w, int h, int mouseX, int mouseY) {
        context.fill(x, y, x + w, y + h, PANEL_FILL);
        context.drawBorder(x, y, w, h, OPTIONS_BORDER);

        int contentTop = y + 4;
        List<View.Entry> info = this.view.entries().info();
        if (!info.isEmpty()) {
            contentTop = drawInfoStrip(context, x, contentTop, w, info);
        }

        List<View.Entry> content = this.view.entries().content();
        int contentHeight = (y + h) - contentTop - 4;
        switch (this.view.header().layoutEnum()) {
            case CARDS -> drawCards(context, x + 4, contentTop, w - 8, contentHeight, content, mouseX, mouseY);
            case GRID -> drawGrid(context, x + 4, contentTop, w - 8, content, mouseX, mouseY);
            case LIST -> drawList(context, x + 4, contentTop, w - 8, contentHeight, content, mouseX, mouseY);
            case PAGE -> drawPage(context, x + 4, contentTop, w - 8, contentHeight, content);
        }
    }

    private int drawInfoStrip(DrawContext context, int x, int y, int w, List<View.Entry> info) {
        int boxHeight = 18;
        int cx = x + 4;
        for (View.Entry entry : info) {
            Text name = entry.stack().getName();
            int boxWidth = Math.min(w / Math.max(info.size(), 1) - 4, 140);
            context.fill(cx, y, cx + boxWidth, y + boxHeight, PANEL_FILL);
            context.drawBorder(cx, y, boxWidth, boxHeight, FRAME_BORDER);
            context.drawItem(entry.stack(), cx + 2, y + 1);
            context.drawText(this.textRenderer, name, cx + 20, y + 5, TEXT_MAIN, false);
            cx += boxWidth + 4;
        }
        return y + boxHeight + 6;
    }

    private void drawCards(DrawContext context, int x, int y, int w, int h, List<View.Entry> entries, int mouseX, int mouseY) {
        if (entries.isEmpty()) return;
        int gap = 6;
        int cardWidth = Math.min(90, (w - gap * (entries.size() - 1)) / entries.size());
        int totalWidth = cardWidth * entries.size() + gap * (entries.size() - 1);
        int startX = x + Math.max(0, (w - totalWidth) / 2);
        int cardHeight = Math.min(h, 96);
        int cardY = y + Math.max(0, (h - cardHeight) / 2);

        int cx = startX;
        for (View.Entry entry : entries) {
            boolean hovered = mouseX >= cx && mouseX < cx + cardWidth && mouseY >= cardY && mouseY < cardY + cardHeight;
            context.fill(cx, cardY, cx + cardWidth, cardY + cardHeight, CARD_FILL);
            context.drawBorder(cx, cardY, cardWidth, cardHeight, hovered ? CARD_BORDER_HOVER : CARD_BORDER);
            context.drawItem(entry.stack(), cx + cardWidth / 2 - 8, cardY + 10);
            drawWrappedCentered(context, entry.stack().getName(), cx, cardWidth, cardY + 32);
            registerClickZone(cx, cardY, cx + cardWidth, cardY + cardHeight, entry, entry.stack());
            cx += cardWidth + gap;
        }
    }

    private void drawGrid(DrawContext context, int x, int y, int w, List<View.Entry> entries, int mouseX, int mouseY) {
        int cell = 20;
        for (View.Entry entry : entries) {
            int col = entry.slot() % 9;
            int row = entry.slot() / 9;
            int ex = x + col * cell;
            int ey = y + row * cell;
            if (ex + cell > x + w) continue;

            boolean hovered = mouseX >= ex && mouseX < ex + cell && mouseY >= ey && mouseY < ey + cell;
            context.fill(ex, ey, ex + cell - 2, ey + cell - 2, hovered ? CARD_FILL : PANEL_FILL);
            context.drawBorder(ex, ey, cell - 2, cell - 2, hovered ? CARD_BORDER_HOVER : CARD_BORDER);
            context.drawItem(entry.stack(), ex + 1, ey + 1);
            registerClickZone(ex, ey, ex + cell - 2, ey + cell - 2, entry, entry.stack());
        }
    }

    private void drawList(DrawContext context, int x, int y, int w, int h, List<View.Entry> entries, int mouseX, int mouseY) {
        int rowHeight = 20;
        int cy = y;
        for (View.Entry entry : entries) {
            if (cy + rowHeight > y + h) break;
            boolean hovered = mouseX >= x && mouseX < x + w && mouseY >= cy && mouseY < cy + rowHeight;
            context.fill(x, cy, x + w, cy + rowHeight - 2, hovered ? CARD_FILL : PANEL_FILL);
            context.drawBorder(x, cy, w, rowHeight - 2, hovered ? CARD_BORDER_HOVER : CARD_BORDER);
            context.drawItem(entry.stack(), x + 3, cy + 2);
            context.drawText(this.textRenderer, entry.stack().getName(), x + 22, cy + 2, TEXT_MAIN, false);
            List<Text> lore = loreLines(entry.stack());
            if (!lore.isEmpty()) context.drawText(this.textRenderer, lore.get(0), x + 22, cy + 11, TEXT_DIM, false);
            registerClickZone(x, cy, x + w, cy + rowHeight - 2, entry, null);
            cy += rowHeight;
        }
    }

    private void drawPage(DrawContext context, int x, int y, int w, int h, List<View.Entry> entries) {
        int cy = y;
        for (View.Entry entry : entries) {
            if (cy > y + h) break;
            context.drawItem(entry.stack(), x, cy);
            context.drawText(this.textRenderer, entry.stack().getName(), x + 20, cy + 4, TEXT_MAIN, false);
            cy += 14;
            for (Text line : loreLines(entry.stack())) {
                if (cy > y + h) break;
                context.drawText(this.textRenderer, line, x + 4, cy, TEXT_DIM, false);
                cy += 10;
            }
            cy += 6;
            registerClickZone(x, cy - 20, x + w, cy, entry, null);
        }
    }

    private void drawWrappedCentered(DrawContext context, Text text, int x, int w, int y) {
        for (OrderedText line : this.textRenderer.wrapLines(text, w - 4)) {
            int lineWidth = this.textRenderer.getWidth(line);
            context.drawText(this.textRenderer, line, x + (w - lineWidth) / 2, y, TEXT_MAIN, false);
            y += 10;
        }
    }

    // ---- footer: Close (bottom-left), footer buttons (centre), Back (bottom-right) ------

    private void drawFooter(DrawContext context, int mouseX, int mouseY) {
        int y = VIRTUAL_HEIGHT - FOOTER_HEIGHT + 4;
        int buttonHeight = FOOTER_HEIGHT - 8;

        int closeWidth = drawTextButton(context, MARGIN, y, buttonHeight, Text.literal("Close"), mouseX, mouseY, this::sendClose);

        List<View.Entry> backEntries = this.view.entries().back();
        int backWidth = 0;
        if (!backEntries.isEmpty()) {
            View.Entry backEntry = backEntries.get(0);
            Text label = backEntry.stack().getName();
            backWidth = this.textRenderer.getWidth(label) + 12;
            int bx = VIRTUAL_WIDTH - MARGIN - backWidth;
            drawTextButtonAt(context, bx, y, backWidth, buttonHeight, label, mouseX, mouseY, backEntry);
        }

        List<View.Entry> footerEntries = this.view.entries().footer();
        if (footerEntries.isEmpty()) return;

        int available = VIRTUAL_WIDTH - MARGIN - closeWidth - MARGIN - backWidth - MARGIN - MARGIN;
        int fullWidth = 0;
        for (View.Entry entry : footerEntries) fullWidth += this.textRenderer.getWidth(entry.stack().getName()) + 14 + 4;

        boolean iconOnly = fullWidth > available;
        int totalWidth = iconOnly ? footerEntries.size() * (buttonHeight + 4) - 4 : fullWidth - 4;
        int startX = MARGIN + closeWidth + Math.max(4, (available - totalWidth) / 2);

        int cx = startX;
        for (View.Entry entry : footerEntries) {
            if (iconOnly) {
                context.drawItem(entry.stack(), cx, y + (buttonHeight - 16) / 2);
                boolean hovered = mouseX >= cx && mouseX < cx + buttonHeight && mouseY >= y && mouseY < y + buttonHeight;
                if (hovered) context.drawBorder(cx - 1, y - 1, buttonHeight + 2, buttonHeight + 2, CARD_BORDER_HOVER);
                registerClickZone(cx, y, cx + buttonHeight, y + buttonHeight, entry, entry.stack());
                cx += buttonHeight + 4;
            } else {
                Text label = entry.stack().getName();
                int width = this.textRenderer.getWidth(label) + 14;
                drawTextButtonAt(context, cx, y, width, buttonHeight, label, mouseX, mouseY, entry);
                cx += width + 4;
            }
        }
    }

    private int drawTextButton(DrawContext context, int x, int y, int height, Text label, int mouseX, int mouseY, Runnable onClick) {
        int width = this.textRenderer.getWidth(label) + 12;
        boolean hovered = mouseX >= x && mouseX < x + width && mouseY >= y && mouseY < y + height;
        context.fill(x, y, x + width, y + height, hovered ? CARD_FILL : PANEL_FILL);
        context.drawBorder(x, y, width, height, hovered ? CARD_BORDER_HOVER : CARD_BORDER);
        context.drawText(this.textRenderer, label, x + 6, y + (height - 8) / 2, TEXT_MAIN, false);
        double[] a = toReal(x, y);
        double[] b = toReal(x + width, y + height);
        this.clickZones.add(new ClickZone(a[0], a[1], b[0], b[1], null, onClick, null));
        return width;
    }

    private void drawTextButtonAt(DrawContext context, int x, int y, int width, int height, Text label, int mouseX, int mouseY, View.Entry entry) {
        boolean hovered = mouseX >= x && mouseX < x + width && mouseY >= y && mouseY < y + height;
        context.fill(x, y, x + width, y + height, hovered ? CARD_FILL : PANEL_FILL);
        context.drawBorder(x, y, width, height, hovered ? CARD_BORDER_HOVER : CARD_BORDER);
        context.drawText(this.textRenderer, label, x + 6, y + (height - 8) / 2, TEXT_MAIN, false);
        registerClickZone(x, y, x + width, y + height, entry, entry.stack());
    }

    private void registerClickZone(int vx1, int vy1, int vx2, int vy2, View.Entry entry, ItemStack tooltipStack) {
        if (!entry.clickable()) return;
        double[] a = toReal(vx1, vy1);
        double[] b = toReal(vx2, vy2);
        this.clickZones.add(new ClickZone(a[0], a[1], b[0], b[1], entry, null, tooltipStack));
    }

    private static List<Text> loreLines(ItemStack stack) {
        LoreComponent lore = stack.get(DataComponentTypes.LORE);
        return lore != null ? lore.lines() : List.of();
    }

    private void sendClose() {
        // removed() is responsible for actually sending the close packet, so every way of
        // leaving this screen (Close button, Esc, inventory key) reports it exactly once.
        this.close();
    }

    // ---- input ----------------------------------------------------------------------------

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (button != 0 && button != 1) return super.mouseClicked(mouseX, mouseY, button);

        for (ClickZone zone : this.clickZones) {
            if (!zone.containsReal(mouseX, mouseY)) continue;
            if (zone.onClick != null) {
                zone.onClick.run();
            } else if (zone.entry != null) {
                ClientPlayNetworking.send(new ClickPayload(this.view.header().id(), zone.entry.slot(), button));
            }
            return true;
        }
        return super.mouseClicked(mouseX, mouseY, button);
    }

    @Override
    public void removed() {
        super.removed();
        if (!this.replacedByServer) {
            ClientPlayNetworking.send(new ClickPayload(this.view.header().id(), -1, -1));
        }
    }

    @Override
    public boolean shouldCloseOnEsc() {
        return true;
    }

    @Override
    public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
        if (this.client != null && this.client.options.inventoryKey.matchesKey(keyCode, scanCode)) {
            this.close();
            return true;
        }
        return super.keyPressed(keyCode, scanCode, modifiers);
    }

    private record ClickZone(double x1, double y1, double x2, double y2, View.Entry entry, Runnable onClick, ItemStack tooltipStack) {
        boolean containsReal(double x, double y) {
            return x >= this.x1 && x < this.x2 && y >= this.y1 && y < this.y2;
        }
    }
}
