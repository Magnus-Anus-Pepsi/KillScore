package com.example.killscore;

import net.minecraftforge.common.ForgeConfigSpec;
import net.minecraftforge.common.ForgeConfigSpec.BooleanValue;
import net.minecraftforge.common.ForgeConfigSpec.ConfigValue;
import net.minecraftforge.common.ForgeConfigSpec.DoubleValue;
import net.minecraftforge.common.ForgeConfigSpec.IntValue;
import org.apache.commons.lang3.tuple.Pair;

import java.util.List;

public final class KillScoreConfig {
    public static final Common COMMON;
    public static final ForgeConfigSpec COMMON_SPEC;
    public static final Client CLIENT;
    public static final ForgeConfigSpec CLIENT_SPEC;

    static {
        Pair<Common, ForgeConfigSpec> c = new ForgeConfigSpec.Builder().configure(Common::new);
        COMMON = c.getLeft();
        COMMON_SPEC = c.getRight();
        Pair<Client, ForgeConfigSpec> cl = new ForgeConfigSpec.Builder().configure(Client::new);
        CLIENT = cl.getLeft();
        CLIENT_SPEC = cl.getRight();
    }

    private KillScoreConfig() {}

    /** Серверные настройки: очки и условия бонусов. Файл: killscore-common.toml */
    public static class Common {
        // базовые очки
        public final IntValue pointsKillPlayer, pointsKillHostile, pointsKillOther;
        // бонусы
        public final IntValue pointsHeadshot, pointsLongshot, pointsPointBlank, pointsNoScope, pointsQuickscope,
                pointsSpin360, pointsFirstBlood, pointsMultiKill, pointsRevenge, pointsStreak;
        // условия
        public final DoubleValue longshotDistance, pointBlankDistance, noScopeMinDistance;
        public final DoubleValue scopedMinAim, noScopeMaxAim;
        public final IntValue quickscopeMaxTicks;
        public final DoubleValue spin360Degrees;
        public final IntValue spinWindowTicks, multiKillWindowTicks, shotMemoryTicks, streakStep;
        public final BooleanValue firstBloodPlayersOnly;
        public final ConfigValue<List<? extends String>> scopeGunTypes;

        Common(ForgeConfigSpec.Builder b) {
            b.push("base_points");
            pointsKillPlayer = b.comment("Очки за убийство игрока (0 = не считать)").defineInRange("player", 100, 0, 100000);
            pointsKillHostile = b.comment("Очки за убийство враждебного моба (0 = не считать)").defineInRange("hostile", 50, 0, 100000);
            pointsKillOther = b.comment("Очки за убийство остальных существ (0 = не считать)").defineInRange("other", 10, 0, 100000);
            b.pop();

            b.push("bonus_points");
            pointsHeadshot = b.defineInRange("headshot", 50, 0, 100000);
            pointsLongshot = b.defineInRange("longshot", 100, 0, 100000);
            pointsPointBlank = b.defineInRange("point_blank", 50, 0, 100000);
            pointsNoScope = b.defineInRange("no_scope", 150, 0, 100000);
            pointsQuickscope = b.defineInRange("quickscope", 200, 0, 100000);
            pointsSpin360 = b.defineInRange("spin_360_no_scope", 500, 0, 100000);
            pointsFirstBlood = b.defineInRange("first_blood", 300, 0, 100000);
            pointsMultiKill = b.comment("За каждое убийство в цепочке после первого: 2 убийства = 1x, 3 = 2x, ...").defineInRange("multi_kill_per_step", 100, 0, 100000);
            pointsRevenge = b.comment("Убил того, кто убил тебя последним (только игроки)").defineInRange("revenge", 100, 0, 100000);
            pointsStreak = b.comment("Бонус за каждые N убийств подряд без смерти (см. streak_step)").defineInRange("streak", 200, 0, 100000);
            b.pop();

            b.push("conditions");
            longshotDistance = b.comment("Дистанция (блоки) для Longshot").defineInRange("longshot_distance", 50.0, 1.0, 1000.0);
            pointBlankDistance = b.comment("Дистанция (блоки) для Point Blank").defineInRange("point_blank_distance", 3.0, 0.5, 50.0);
            noScopeMinDistance = b.comment("Минимальная дистанция для No-Scope (чтобы упор не считался)").defineInRange("no_scope_min_distance", 8.0, 0.0, 1000.0);
            scopeGunTypes = b.comment("Типы оружия TacZ, для которых считаются No-Scope / Quickscope / 360.",
                    "Значения: sniper, rifle, smg, pistol, shotgun, mg, rpg. \"*\" = любое оружие.")
                    .defineListAllowEmpty("scope_gun_types", List.of("sniper"), o -> o instanceof String);
            noScopeMaxAim = b.comment("Прогресс прицеливания (0..1) ниже которого выстрел считается «без прицела»").defineInRange("no_scope_max_aim", 0.15, 0.0, 1.0);
            scopedMinAim = b.comment("Прогресс прицеливания (0..1) выше которого считается что игрок «в прицеле»").defineInRange("scoped_min_aim", 0.85, 0.0, 1.0);
            quickscopeMaxTicks = b.comment("Макс. время (тики, 20 = 1 сек) от начала прицеливания до выстрела для Quickscope").defineInRange("quickscope_max_ticks", 16, 1, 200);
            spin360Degrees = b.comment("Суммарный поворот камеры (градусы) за окно, чтобы считалось 360").defineInRange("spin_360_degrees", 330.0, 90.0, 2000.0);
            spinWindowTicks = b.comment("Окно для подсчёта вращения (тики, максимум 40)").defineInRange("spin_window_ticks", 20, 5, 40);
            multiKillWindowTicks = b.comment("Время между убийствами для цепочки Double/Triple/Multi (тики)").defineInRange("multi_kill_window_ticks", 80, 5, 600);
            shotMemoryTicks = b.comment("Сколько тиков после выстрела помнить состояние прицела/вращения (пуля летит не мгновенно)").defineInRange("shot_memory_ticks", 60, 5, 600);
            streakStep = b.comment("Каждые N убийств подряд без смерти даётся бонус Killstreak").defineInRange("streak_step", 5, 2, 100);
            firstBloodPlayersOnly = b.comment("true = First Blood только за убийство игрока; false = за любое первое убийство").define("first_blood_players_only", false);
            b.pop();
        }
    }

    /** Клиентские настройки HUD. Файл: killscore-client.toml */
    public static class Client {
        public final BooleanValue playSound;
        public final DoubleValue voiceVolume;
        public final DoubleValue scale;
        public final IntValue xOffset, yOffset, maxLines, lifetimeMs;
        public final BooleanValue showTotal;

        Client(ForgeConfigSpec.Builder b) {
            playSound = b.comment("Озвучка диктора и звук убийства").define("play_sound", true);
            voiceVolume = b.comment("Громкость голоса диктора").defineInRange("voice_volume", 1.0, 0.0, 2.0);
            showTotal = b.comment("Показывать суммарные очки сессии справа сверху").define("show_total", true);
            scale = b.defineInRange("scale", 1.5, 0.5, 4.0);
            xOffset = b.comment("Смещение по X от центра экрана (пиксели)").defineInRange("x_offset", 40, -1000, 1000);
            yOffset = b.comment("Смещение по Y от центра экрана (пиксели)").defineInRange("y_offset", -55, -1000, 1000);
            maxLines = b.defineInRange("max_lines", 6, 1, 20);
            lifetimeMs = b.defineInRange("lifetime_ms", 2600, 500, 20000);
        }
    }
}
