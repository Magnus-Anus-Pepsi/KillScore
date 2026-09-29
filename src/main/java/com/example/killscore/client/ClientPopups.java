package com.example.killscore.client;

import com.example.killscore.KillScoreConfig;
import com.example.killscore.KillScoreMod;
import com.example.killscore.network.ScorePacket;
import com.mojang.blaze3d.systems.RenderSystem;
import net.minecraft.Util;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.resources.sounds.SimpleSoundInstance;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.RegisterGuiOverlaysEvent;
import net.minecraftforge.client.gui.overlay.ForgeGui;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** Клиент: хранит всплывающие строки и рисует их поверх HUD в стиле старых CoD. */
@Mod.EventBusSubscriber(modid = KillScoreMod.MOD_ID, bus = Mod.EventBusSubscriber.Bus.MOD, value = Dist.CLIENT)
public final class ClientPopups {
    private record Popup(int points, Component label, boolean bonus, long createdMs) {}

    private static final List<Popup> POPUPS = new ArrayList<>();
    private static int total = 0;
    private static long totalUpdatedMs = 0;

    private static final int GOLD = 0xFFD24A;
    private static final int WHITE = 0xFFFFFF;
    private static final long POP_MS = 150;
    private static final long FADE_MS = 600;
    private static final long TOTAL_VISIBLE_MS = 5000;

    /**
     * Голоса диктора. Порядок = приоритет: играет только один, самый «крутой» из полученных бонусов.
     * Ключ — ключ перевода бонуса, значение — событие из sounds.json.
     */
    private static final Map<String, SoundEvent> VOICES = new LinkedHashMap<>();
    static {
        voice("killscore.popup.firstblood", "firstblood");
        voice("killscore.popup.spin360", "spin360");
        voice("killscore.popup.streak", "streak");
        voice("killscore.popup.multi", "multikill");
        voice("killscore.popup.triple", "triplekill");
        voice("killscore.popup.double", "doublekill");
        voice("killscore.popup.quickscope", "quickscope");
        voice("killscore.popup.noscope", "noscope");
        voice("killscore.popup.revenge", "payback");
        voice("killscore.popup.longshot", "longshot");
        voice("killscore.popup.pointblank", "pointblank");
        voice("killscore.popup.headshot", "headshot");
    }

    private static void voice(String popupKey, String soundName) {
        VOICES.put(popupKey, SoundEvent.createVariableRangeEvent(new ResourceLocation(KillScoreMod.MOD_ID, soundName)));
    }

    private ClientPopups() {}

    @SubscribeEvent
    public static void registerOverlay(RegisterGuiOverlaysEvent event) {
        event.registerAboveAll("popups", ClientPopups::render);
    }

    /** Вызывается из сетевого пакета (главный поток клиента). */
    public static void receive(ScorePacket msg) {
        long now = Util.getMillis();
        boolean first = true;
        boolean anyBonus = false;
        for (ScorePacket.Entry e : msg.entries) {
            Component label = e.arg() > 0 ? Component.translatable(e.key(), e.arg()) : Component.translatable(e.key());
            boolean bonus = !first;
            anyBonus |= bonus;
            POPUPS.add(new Popup(e.points(), label, bonus, now));
            first = false;
        }
        int cap = KillScoreConfig.CLIENT.maxLines.get() * 2;
        while (POPUPS.size() > cap) POPUPS.remove(0);

        total = msg.total;
        totalUpdatedMs = now;

        if (KillScoreConfig.CLIENT.playSound.get()) {
            Minecraft mc = Minecraft.getInstance();
            SoundEvent voice = null;
            for (Map.Entry<String, SoundEvent> v : VOICES.entrySet()) {
                boolean present = false;
                for (ScorePacket.Entry e : msg.entries) {
                    if (e.key().equals(v.getKey())) { present = true; break; }
                }
                if (present) { voice = v.getValue(); break; }
            }
            if (voice != null) {
                mc.getSoundManager().play(SimpleSoundInstance.forUI(voice, 1.0f,
                        KillScoreConfig.CLIENT.voiceVolume.get().floatValue()));
            } else {
                // Обычное убийство без бонусов — короткий «пинг»
                mc.getSoundManager().play(SimpleSoundInstance.forUI(SoundEvents.EXPERIENCE_ORB_PICKUP, 1.2f, 0.7f));
            }
        }
    }

    private static int withAlpha(int rgb, float alpha) {
        int a = Math.max(5, Math.min(255, (int) (alpha * 255f)));
        return (a << 24) | (rgb & 0xFFFFFF);
    }

    private static void render(ForgeGui gui, GuiGraphics g, float partialTick, int w, int h) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.options.hideGui) return;

        long now = Util.getMillis();
        long life = KillScoreConfig.CLIENT.lifetimeMs.get();
        POPUPS.removeIf(p -> now - p.createdMs() > life);

        Font font = mc.font;
        RenderSystem.enableBlend();

        // Всплывающие строки
        int maxLines = KillScoreConfig.CLIENT.maxLines.get();
        int from = Math.max(0, POPUPS.size() - maxLines);
        float baseScale = KillScoreConfig.CLIENT.scale.get().floatValue();
        int lineH = Math.round(11 * baseScale);
        int x = w / 2 + KillScoreConfig.CLIENT.xOffset.get();
        int y0 = h / 2 + KillScoreConfig.CLIENT.yOffset.get();

        for (int i = from; i < POPUPS.size(); i++) {
            Popup p = POPUPS.get(i);
            long age = now - p.createdMs();

            float alpha = 1f;
            long fadeStart = life - FADE_MS;
            if (age > fadeStart) alpha = 1f - (float) (age - fadeStart) / FADE_MS;
            if (alpha <= 0.02f) continue;

            float pop = age < POP_MS ? 1f + 0.6f * (1f - (float) age / POP_MS) : 1f;
            float s = baseScale * pop;

            String pts = "+" + p.points();
            int y = y0 + (i - from) * lineH;

            g.pose().pushPose();
            g.pose().translate(x, y, 0);
            g.pose().scale(s, s, 1f);
            g.drawString(font, pts, 0, 0, withAlpha(GOLD, alpha), true);
            g.drawString(font, p.label(), font.width(pts) + 4, 0, withAlpha(p.bonus() ? GOLD : WHITE, alpha), true);
            g.pose().popPose();
        }

        // Суммарные очки
        if (KillScoreConfig.CLIENT.showTotal.get() && total > 0) {
            long age = now - totalUpdatedMs;
            if (age < TOTAL_VISIBLE_MS) {
                float alpha = age > TOTAL_VISIBLE_MS - FADE_MS
                        ? 1f - (float) (age - (TOTAL_VISIBLE_MS - FADE_MS)) / FADE_MS : 1f;
                if (alpha > 0.02f) {
                    Component text = Component.translatable("killscore.hud.score", total);
                    g.drawString(font, text, w - font.width(text) - 8, 8, withAlpha(GOLD, alpha), true);
                }
            }
        }

        RenderSystem.disableBlend();
    }
}
