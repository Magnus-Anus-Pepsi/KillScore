package com.example.killscore.network;

import com.example.killscore.KillScoreMod;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.network.NetworkRegistry;
import net.minecraftforge.network.PacketDistributor;
import net.minecraftforge.network.simple.SimpleChannel;

public final class Network {
    private static final String PROTOCOL = "1";
    public static final SimpleChannel CHANNEL = NetworkRegistry.newSimpleChannel(
            new ResourceLocation(KillScoreMod.MOD_ID, "main"),
            () -> PROTOCOL, PROTOCOL::equals, PROTOCOL::equals);

    private Network() {}

    public static void register() {
        CHANNEL.registerMessage(0, ScorePacket.class, ScorePacket::encode, ScorePacket::decode, ScorePacket::handle);
    }

    public static void sendTo(ServerPlayer player, ScorePacket packet) {
        CHANNEL.send(PacketDistributor.PLAYER.with(() -> player), packet);
    }
}
