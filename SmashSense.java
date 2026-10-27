package com.smashsense;

import java.util.Locale;

import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.command.v2.ClientCommandManager;
import net.fabricmc.fabric.api.client.command.v2.ClientCommandRegistrationCallback;
import net.fabricmc.fabric.api.client.rendering.v1.hud.HudElementRegistry;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.core.component.DataComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.enchantment.Enchantments;
import net.minecraft.world.item.enchantment.ItemEnchantments;

/**
 * SmashSense: a mace PvP HUD. While you hold a mace and fall, it shows a live fall meter,
 * tells you when a smash attack is ready, and predicts the raw smash damage.
 * Display only: it never attacks, aims or changes anything for you. Toggle with /smashsense.
 */
public class SmashSense implements ClientModInitializer {

    private static final double MACE_BASE_DAMAGE = 6.0;
    private static final double MIN_SMASH_FALL = 1.5;
    private static final double METER_MAX_FALL = 8.0;

    private static boolean enabled = true;

    @Override
    public void onInitializeClient() {
        HudElementRegistry.addLast(Identifier.fromNamespaceAndPath("smashsense", "hud"), SmashSense::render);

        ClientCommandRegistrationCallback.EVENT.register((dispatcher, registryAccess) ->
                dispatcher.register(ClientCommandManager.literal("smashsense").executes(ctx -> {
                    enabled = !enabled;
                    ctx.getSource().sendFeedback(Component.literal("SmashSense " + (enabled ? "enabled" : "disabled")));
                    return 1;
                })));
    }

    private static void render(GuiGraphics graphics, DeltaTracker tickCounter) {
        if (!enabled) {
            return;
        }
        Minecraft mc = Minecraft.getInstance();
        LocalPlayer player = mc.player;
        if (player == null) {
            return;
        }
        ItemStack held = player.getMainHandItem();
        if (!held.is(Items.MACE)) {
            return;
        }
        double fall = player.fallDistance;
        if (fall < 0.75 || player.isFallFlying()) {
            return;
        }

        boolean ready = fall > MIN_SMASH_FALL;
        double total = MACE_BASE_DAMAGE;
        if (ready) {
            total += smashBonus(fall) + 0.5 * densityLevel(held) * fall;
        }

        String title;
        int color;
        if (!ready) {
            title = "FALL " + fmt(fall);
            color = 0xFFAAAAAA;
        } else if (total >= 40) {
            title = "MEGA SMASH";
            color = 0xFFFF5555;
        } else if (total >= 20) {
            title = "BIG SMASH";
            color = 0xFFFFAA00;
        } else {
            title = "SMASH READY";
            color = 0xFF55FF55;
        }
        String detail = ready
                ? "~" + fmt(total) + " dmg (" + fmt(total / 2.0) + " hearts)"
                : "fall 1.5+ blocks to smash";

        Font font = mc.font;
        int cx = graphics.guiWidth() / 2;
        int cy = graphics.guiHeight() / 2;

        graphics.drawString(font, title, cx - font.width(title) / 2, cy + 16, color);
        graphics.drawString(font, detail, cx - font.width(detail) / 2, cy + 27, 0xFFFFFFFF);

        // Fall meter
        int barWidth = 80;
        int x0 = cx - barWidth / 2;
        int y0 = cy + 40;
        int filled = (int) (barWidth * Math.min(fall / METER_MAX_FALL, 1.0));
        graphics.fill(x0 - 1, y0 - 1, x0 + barWidth + 1, y0 + 6, 0xAA000000);
        graphics.fill(x0, y0, x0 + filled, y0 + 5, color);
        int tick = x0 + (int) (barWidth * (MIN_SMASH_FALL / METER_MAX_FALL));
        graphics.fill(tick, y0 - 2, tick + 1, y0 + 7, 0xFFFFFFFF);
    }

    /** Extra damage from the fall: +4/block for the first 3, +2/block up to 8, then +1/block. */
    private static double smashBonus(double fall) {
        if (fall <= 3.0) {
            return 4.0 * fall;
        } else if (fall <= 8.0) {
            return 12.0 + 2.0 * (fall - 3.0);
        }
        return 22.0 + (fall - 8.0);
    }

    private static int densityLevel(ItemStack stack) {
        ItemEnchantments enchants = stack.get(DataComponents.ENCHANTMENTS);
        if (enchants == null) {
            return 0;
        }
        for (var entry : enchants.entrySet()) {
            if (entry.getKey().is(Enchantments.DENSITY)) {
                return entry.getIntValue();
            }
        }
        return 0;
    }

    private static String fmt(double value) {
        return String.format(Locale.ROOT, "%.1f", value);
    }
}
