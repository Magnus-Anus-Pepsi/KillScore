package com.example.killscore;

import com.example.killscore.KillScoreConfig.Common;
import com.example.killscore.network.Network;
import com.example.killscore.network.ScorePacket;
import com.tacz.guns.api.TimelessAPI;
import com.tacz.guns.api.entity.IGunOperator;
import com.tacz.guns.api.event.common.EntityKillByGunEvent;
import com.tacz.guns.api.event.common.GunShootEvent;
import com.tacz.guns.api.item.IGun;
import com.tacz.guns.resource.index.CommonGunIndex;
import net.minecraft.commands.Commands;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.monster.Enemy;
import net.minecraft.world.entity.player.Player;
import net.minecraftforge.event.RegisterCommandsEvent;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.entity.living.LivingDeathEvent;
import net.minecraftforge.event.entity.player.PlayerEvent;
import net.minecraftforge.event.server.ServerStartingEvent;
import net.minecraftforge.eventbus.api.EventPriority;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/** Вся серверная логика: слушает события TacZ и считает бонусы. */
@Mod.EventBusSubscriber(modid = KillScoreMod.MOD_ID)
public final class ScoreEvents {
    private static final int BUFFER = 40;

    /** Данные, которые отслеживаются каждый тик у каждого игрока. */
    private static final class Track {
        float lastYaw;
        final float[] yawDeltas = new float[BUFFER];
        int idx;
        long aimStart = -1;
        Track(float yaw) { lastYaw = yaw; }

        float spin(int window) {
            float sum = 0;
            for (int i = 1; i <= window; i++) {
                sum += yawDeltas[Math.floorMod(idx - i, BUFFER)];
            }
            return sum;
        }
    }

    /** Состояние в момент выстрела. */
    private record Snapshot(long tick, float aimProgress, long aimTicks, float spin) {}

    private static final class Chain { int count; long lastTick = Long.MIN_VALUE / 2; }

    private static final Map<UUID, Track> TRACKS = new ConcurrentHashMap<>();
    private static final Map<UUID, Snapshot> SHOTS = new ConcurrentHashMap<>();
    private static final Map<UUID, Chain> CHAINS = new ConcurrentHashMap<>();
    private static final Map<UUID, Integer> STREAKS = new ConcurrentHashMap<>();
    private static final Map<UUID, Integer> SCORES = new ConcurrentHashMap<>();
    private static final Map<UUID, UUID> LAST_KILLED_BY = new ConcurrentHashMap<>();
    private static volatile boolean firstBloodDone = false;

    private ScoreEvents() {}

    // ---------------------------------------------------------------- tracking

    @SubscribeEvent
    public static void onPlayerTick(TickEvent.PlayerTickEvent e) {
        if (e.phase != TickEvent.Phase.END || !(e.player instanceof ServerPlayer p)) return;
        Track t = TRACKS.computeIfAbsent(p.getUUID(), k -> new Track(p.getYRot()));

        float yaw = p.getYRot();
        t.yawDeltas[t.idx] = Math.abs(Mth.wrapDegrees(yaw - t.lastYaw));
        t.idx = (t.idx + 1) % BUFFER;
        t.lastYaw = yaw;

        boolean aiming = IGun.mainhandHoldGun(p) && IGunOperator.fromLivingEntity(p).getSynIsAiming();
        if (aiming) {
            if (t.aimStart < 0) t.aimStart = p.level().getGameTime();
        } else {
            t.aimStart = -1;
        }
    }

    private static Snapshot capture(ServerPlayer p) {
        Track t = TRACKS.get(p.getUUID());
        long now = p.level().getGameTime();
        float progress = IGunOperator.fromLivingEntity(p).getSynAimingProgress();
        long aimTicks = (t == null || t.aimStart < 0) ? Long.MAX_VALUE : now - t.aimStart;
        float spin = t == null ? 0 : t.spin(KillScoreConfig.COMMON.spinWindowTicks.get());
        return new Snapshot(now, progress, aimTicks, spin);
    }

    /** Запоминаем состояние в момент выстрела: пуля долетает до цели позже. */
    @SubscribeEvent(priority = EventPriority.LOWEST)
    public static void onShoot(GunShootEvent e) {
        if (!e.getLogicalSide().isServer() || !(e.getShooter() instanceof ServerPlayer p)) return;
        SHOTS.put(p.getUUID(), capture(p));
    }

    @SubscribeEvent
    public static void onDeath(LivingDeathEvent e) {
        if (e.getEntity() instanceof ServerPlayer victim) {
            UUID id = victim.getUUID();
            STREAKS.remove(id);
            CHAINS.remove(id);
            if (e.getSource().getEntity() instanceof ServerPlayer killer && killer != victim) {
                LAST_KILLED_BY.put(id, killer.getUUID());
            }
        }
    }

    // ------------------------------------------------------------------- kills

    @SubscribeEvent
    public static void onKill(EntityKillByGunEvent e) {
        if (!e.getLogicalSide().isServer() || !(e.getAttacker() instanceof ServerPlayer p)) return;
        LivingEntity victim = e.getKilledEntity();
        if (victim == null || victim == p) return;

        Common c = KillScoreConfig.COMMON;
        UUID id = p.getUUID();
        long now = p.level().getGameTime();

        boolean victimIsPlayer = victim instanceof Player;
        int base = victimIsPlayer ? c.pointsKillPlayer.get()
                : victim instanceof Enemy ? c.pointsKillHostile.get() : c.pointsKillOther.get();
        if (base <= 0) return;

        List<ScorePacket.Entry> out = new ArrayList<>();
        out.add(new ScorePacket.Entry("killscore.popup.kill", base, 0));

        // First Blood
        if (!firstBloodDone && (victimIsPlayer || !c.firstBloodPlayersOnly.get())) {
            firstBloodDone = true;
            add(out, "killscore.popup.firstblood", c.pointsFirstBlood.get(), 0);
        }

        // Headshot
        if (e.isHeadShot()) add(out, "killscore.popup.headshot", c.pointsHeadshot.get(), 0);

        // Дистанция
        double dist = p.distanceTo(victim);
        if (dist >= c.longshotDistance.get()) {
            add(out, "killscore.popup.longshot", c.pointsLongshot.get(), 0);
        } else if (dist <= c.pointBlankDistance.get()) {
            add(out, "killscore.popup.pointblank", c.pointsPointBlank.get(), 0);
        }

        // No-Scope / Quickscope / 360
        if (isScopeGun(e)) {
            Snapshot s = SHOTS.get(id);
            if (s == null || now - s.tick() > c.shotMemoryTicks.get()) s = capture(p);

            boolean hip = s.aimProgress() < c.noScopeMaxAim.get();
            if (hip && s.spin() >= c.spin360Degrees.get()) {
                add(out, "killscore.popup.spin360", c.pointsSpin360.get(), 0);
            } else if (hip && dist >= c.noScopeMinDistance.get()) {
                add(out, "killscore.popup.noscope", c.pointsNoScope.get(), 0);
            } else if (s.aimProgress() >= c.scopedMinAim.get() && s.aimTicks() <= c.quickscopeMaxTicks.get()) {
                add(out, "killscore.popup.quickscope", c.pointsQuickscope.get(), 0);
            }
        }

        // Цепочка убийств
        Chain chain = CHAINS.computeIfAbsent(id, k -> new Chain());
        chain.count = (chain.count > 0 && now - chain.lastTick <= c.multiKillWindowTicks.get()) ? chain.count + 1 : 1;
        chain.lastTick = now;
        if (chain.count >= 2) {
            int pts = c.pointsMultiKill.get() * (chain.count - 1);
            switch (chain.count) {
                case 2 -> add(out, "killscore.popup.double", pts, 0);
                case 3 -> add(out, "killscore.popup.triple", pts, 0);
                default -> add(out, "killscore.popup.multi", pts, chain.count);
            }
        }

        // Расплата
        if (victimIsPlayer && id.equals(LAST_KILLED_BY.get(victim.getUUID()))) {
            LAST_KILLED_BY.remove(victim.getUUID());
            add(out, "killscore.popup.revenge", c.pointsRevenge.get(), 0);
        }

        // Серия убийств без смерти
        int streak = STREAKS.merge(id, 1, Integer::sum);
        if (streak % c.streakStep.get() == 0) {
            add(out, "killscore.popup.streak", c.pointsStreak.get(), streak);
        }

        int gained = out.stream().mapToInt(ScorePacket.Entry::points).sum();
        int total = SCORES.merge(id, gained, Integer::sum);
        Network.sendTo(p, new ScorePacket(out, total));
    }

    private static void add(List<ScorePacket.Entry> list, String key, int points, int arg) {
        if (points > 0) list.add(new ScorePacket.Entry(key, points, arg));
    }

    private static boolean isScopeGun(EntityKillByGunEvent e) {
        List<? extends String> types = KillScoreConfig.COMMON.scopeGunTypes.get();
        if (types.contains("*")) return true;
        String type = TimelessAPI.getCommonGunIndex(e.getGunId()).map(CommonGunIndex::getType).orElse("");
        return types.contains(type);
    }

    // ---------------------------------------------------------------- lifecycle

    private static void resetRound() {
        firstBloodDone = false;
        SHOTS.clear();
        CHAINS.clear();
        STREAKS.clear();
        SCORES.clear();
        LAST_KILLED_BY.clear();
    }

    @SubscribeEvent
    public static void onServerStarting(ServerStartingEvent e) {
        resetRound();
        TRACKS.clear();
    }

    @SubscribeEvent
    public static void onLogout(PlayerEvent.PlayerLoggedOutEvent e) {
        UUID id = e.getEntity().getUUID();
        TRACKS.remove(id);
        SHOTS.remove(id);
        CHAINS.remove(id);
        STREAKS.remove(id);
    }

    @SubscribeEvent
    public static void onCommands(RegisterCommandsEvent e) {
        e.getDispatcher().register(Commands.literal("killscore")
                .requires(src -> src.hasPermission(2))
                .then(Commands.literal("reset").executes(ctx -> {
                    resetRound();
                    ctx.getSource().sendSuccess(() -> Component.translatable("killscore.command.reset"), true);
                    return 1;
                })));
    }
}
