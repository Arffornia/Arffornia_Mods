package fr.thegostsniperfr.arffornia.client.crate;

import fr.thegostsniperfr.arffornia.network.ClientboundCratePreviewPacket;
import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.ItemStack;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.List;

/** A crate's contents with exact odds and, for the streak crate, the week strip (spec 020, D4 and "Streak calendar"). */
public class CratePreviewScreen extends Screen {
    private static final int WIDTH = 290;
    private static final int COLUMNS = 5;
    private static final int CELL_W = 56;
    private static final int CELL_H = 38;
    private static final int DAY_W = 36;
    private static final int DAY_GAP = 3;
    private static final int DAY_H = 46;

    private final ClientboundCratePreviewPacket preview;

    public CratePreviewScreen(ClientboundCratePreviewPacket preview) {
        super(preview.name());
        this.preview = preview;
    }

    public static void open(ClientboundCratePreviewPacket preview) {
        Minecraft.getInstance().setScreen(new CratePreviewScreen(preview));
    }

    /** Up to two decimals, no trailing zeros: 40, 12.5, 0.03. */
    static String percent(float chance) {
        return new BigDecimal(chance * 100.0).setScale(2, RoundingMode.HALF_UP).stripTrailingZeros().toPlainString();
    }

    private int calendarHeight() {
        if (!preview.hasCalendar()) {
            return 0;
        }
        return preview.calendar().isPresent() ? DAY_H + 26 : 22;
    }

    private int height() {
        int rows = (preview.entries().size() + COLUMNS - 1) / COLUMNS;
        return 26 + calendarHeight() + 14 + Math.max(1, rows) * CELL_H + 30;
    }

    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        super.render(graphics, mouseX, mouseY, partialTick);
        int left = (width - WIDTH) / 2;
        int top = Math.max(4, (height - height()) / 2);
        int border = 0xFF000000 | preview.rarity().colour();
        graphics.fill(left - 1, top - 1, left + WIDTH + 1, top + height() + 1, border);
        graphics.fillGradient(left, top, left + WIDTH, top + height(), 0xF0181820, 0xF0101018);
        graphics.drawCenteredString(font, title.copy().withStyle(preview.rarity().chatColour(), ChatFormatting.BOLD), width / 2, top + 9, 0xFFFFFF);

        int y = top + 26;
        List<Component> tooltip = null;
        if (preview.hasCalendar()) {
            if (preview.calendar().isPresent()) {
                tooltip = renderCalendar(graphics, preview.calendar().get(), y, mouseX, mouseY);
            } else {
                graphics.drawCenteredString(font, Component.translatable("gui.arffornia.crate.calendar_unavailable").withStyle(ChatFormatting.GRAY),
                        width / 2, y + 4, 0xFFFFFF);
            }
            y += calendarHeight();
        }

        graphics.drawString(font, Component.translatable("gui.arffornia.crate.contents").withStyle(ChatFormatting.GRAY), left + 8, y, 0xFFFFFF);
        y += 14;
        if (preview.entries().isEmpty()) {
            graphics.drawCenteredString(font, Component.translatable("gui.arffornia.crate.empty").withStyle(ChatFormatting.GRAY), width / 2, y + 10, 0xFFFFFF);
        }
        int gridLeft = left + (WIDTH - COLUMNS * CELL_W) / 2;
        for (int i = 0; i < preview.entries().size(); i++) {
            ClientboundCratePreviewPacket.Entry entry = preview.entries().get(i);
            int x = gridLeft + (i % COLUMNS) * CELL_W;
            int cellY = y + (i / COLUMNS) * CELL_H;
            boolean hovered = mouseX >= x && mouseX < x + CELL_W - 2 && mouseY >= cellY && mouseY < cellY + CELL_H - 2;
            int rarityColour = 0xFF000000 | entry.rarity().colour();
            graphics.fill(x, cellY, x + CELL_W - 2, cellY + CELL_H - 2, hovered ? 0x60FFFFFF : 0x30FFFFFF);
            graphics.fill(x, cellY + CELL_H - 3, x + CELL_W - 2, cellY + CELL_H - 2, rarityColour);
            int itemX = x + (CELL_W - 2) / 2 - 8;
            graphics.renderItem(entry.stack(), itemX, cellY + 3);
            String amount = entry.minCount() == entry.maxCount() ? (entry.minCount() > 1 ? Integer.toString(entry.minCount()) : null)
                    : entry.minCount() + "-" + entry.maxCount();
            graphics.renderItemDecorations(font, entry.stack(), itemX, cellY + 3, amount);
            Component chance = Component.translatable("gui.arffornia.crate.chance", percent(entry.chance()));
            graphics.drawCenteredString(font, chance, x + (CELL_W - 2) / 2, cellY + 23, rarityColour);
            if (hovered) {
                tooltip = entryTooltip(entry);
            }
        }

        int footer = top + height() - 22;
        graphics.drawCenteredString(font, Component.translatable("gui.arffornia.crate.hint_open").withStyle(ChatFormatting.DARK_GRAY), width / 2, footer, 0xFFFFFF);
        graphics.drawCenteredString(font, Component.translatable("gui.arffornia.crate.hint_skip").withStyle(ChatFormatting.DARK_GRAY), width / 2, footer + 10, 0xFFFFFF);
        if (tooltip != null) {
            graphics.renderComponentTooltip(font, tooltip, mouseX, mouseY);
        }
    }

    private List<Component> entryTooltip(ClientboundCratePreviewPacket.Entry entry) {
        List<Component> lines = new ArrayList<>(getTooltipFromItem(Minecraft.getInstance(), entry.stack()));
        lines.add(Component.translatable("gui.arffornia.crate.tooltip.chance", percent(entry.chance())).withStyle(ChatFormatting.YELLOW));
        lines.add(entry.minCount() == entry.maxCount()
                ? Component.translatable("gui.arffornia.crate.tooltip.amount", entry.minCount()).withStyle(ChatFormatting.GRAY)
                : Component.translatable("gui.arffornia.crate.tooltip.amount_range", entry.minCount(), entry.maxCount()).withStyle(ChatFormatting.GRAY));
        lines.add(Component.translatable("gui.arffornia.crate.rarity." + entry.rarity().getSerializedName()).withStyle(entry.rarity().chatColour()));
        return lines;
    }

    private List<Component> renderCalendar(GuiGraphics graphics, ClientboundCratePreviewPacket.Calendar calendar, int y, int mouseX, int mouseY) {
        List<Component> tooltip = null;
        int count = calendar.week().size();
        int stripWidth = count * DAY_W + (count - 1) * DAY_GAP;
        int x = (width - stripWidth) / 2;
        for (ClientboundCratePreviewPacket.Day day : calendar.week()) {
            boolean done = day.day() <= calendar.dayStreak();
            boolean today = day.day() == calendar.today();
            int borderColour = today ? 0xFFFFB300 : done ? 0xFF55CC55 : 0xFF444450;
            graphics.fill(x - 1, y - 1, x + DAY_W + 1, y + DAY_H + 1, borderColour);
            graphics.fill(x, y, x + DAY_W, y + DAY_H, today ? 0xFF3A2E10 : done ? 0xFF173017 : 0xFF202028);
            graphics.drawCenteredString(font, Component.translatable("gui.arffornia.crate.day_short", day.day()), x + DAY_W / 2, y + 3,
                    today ? 0xFFFFCC33 : 0xFFDDDDDD);
            for (int i = 0; i < Math.min(2, day.contents().size()); i++) {
                ItemStack icon = day.contents().get(i);
                int iconX = x + DAY_W / 2 - 8 + (day.contents().size() > 1 ? (i == 0 ? -6 : 6) : 0);
                graphics.renderItem(icon, iconX, y + 14);
                graphics.renderItemDecorations(font, icon, iconX, y + 14);
            }
            if (done) {
                graphics.drawCenteredString(font, Component.translatable("gui.arffornia.crate.done"), x + DAY_W / 2, y + DAY_H - 11, 0xFF55FF55);
            }
            if (mouseX >= x && mouseX < x + DAY_W && mouseY >= y && mouseY < y + DAY_H) {
                tooltip = dayTooltip(day, done, today);
            }
            x += DAY_W + DAY_GAP;
        }
        Component streak = Component.translatable("gui.arffornia.crate.streak", calendar.dayStreak(), calendar.bestStreak()).withStyle(ChatFormatting.GOLD);
        graphics.drawCenteredString(font, streak, width / 2, y + DAY_H + 8, 0xFFFFFF);
        return tooltip;
    }

    private List<Component> dayTooltip(ClientboundCratePreviewPacket.Day day, boolean done, boolean today) {
        List<Component> lines = new ArrayList<>();
        lines.add(Component.translatable("gui.arffornia.crate.day", day.day()).withStyle(ChatFormatting.WHITE));
        for (ItemStack stack : day.contents()) {
            lines.add(Component.translatable("chat.arffornia.crate.stack", stack.getCount(), stack.getHoverName()).withStyle(ChatFormatting.GRAY));
        }
        if (today) {
            lines.add(Component.translatable("gui.arffornia.crate.today").withStyle(ChatFormatting.GOLD));
        } else if (done) {
            lines.add(Component.translatable("gui.arffornia.crate.claimed").withStyle(ChatFormatting.GREEN));
        }
        return lines;
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }
}
