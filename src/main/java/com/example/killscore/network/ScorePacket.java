package com.example.killscore.network;

import com.example.killscore.client.ClientPopups;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.fml.DistExecutor;
import net.minecraftforge.network.NetworkEvent;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Supplier;

/** Сервер -> клиент: список всплывающих строк и суммарные очки. */
public class ScorePacket {
    public record Entry(String key, int points, int arg) {}

    public final List<Entry> entries;
    public final int total;

    public ScorePacket(List<Entry> entries, int total) {
        this.entries = entries;
        this.total = total;
    }

    public static void encode(ScorePacket msg, FriendlyByteBuf buf) {
        buf.writeVarInt(msg.total);
        buf.writeVarInt(msg.entries.size());
        for (Entry e : msg.entries) {
            buf.writeUtf(e.key(), 64);
            buf.writeVarInt(e.points());
            buf.writeVarInt(e.arg());
        }
    }

    public static ScorePacket decode(FriendlyByteBuf buf) {
        int total = buf.readVarInt();
        int n = buf.readVarInt();
        List<Entry> list = new ArrayList<>(n);
        for (int i = 0; i < n; i++) {
            list.add(new Entry(buf.readUtf(64), buf.readVarInt(), buf.readVarInt()));
        }
        return new ScorePacket(list, total);
    }

    public static void handle(ScorePacket msg, Supplier<NetworkEvent.Context> ctx) {
        ctx.get().enqueueWork(() ->
                DistExecutor.unsafeRunWhenOn(Dist.CLIENT, () -> () -> ClientPopups.receive(msg)));
        ctx.get().setPacketHandled(true);
    }
}
