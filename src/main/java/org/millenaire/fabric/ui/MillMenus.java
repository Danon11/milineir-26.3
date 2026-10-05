package org.millenaire.fabric.ui;

import net.fabricmc.fabric.api.networking.v1.PayloadTypeRegistry;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerPlayer;

/** Sends menus to players: as a screen when their client has the mod, otherwise as chat. */
public final class MillMenus {
    public record Payload(String json) implements CustomPacketPayload {
        public static final Type<Payload> TYPE = new Type<>(Identifier.fromNamespaceAndPath("millenaire", "menu"));
        public static final StreamCodec<RegistryFriendlyByteBuf, Payload> CODEC =
                ByteBufCodecs.stringUtf8(1 << 18).map(Payload::new, Payload::json).cast();
        @Override public Type<? extends CustomPacketPayload> type() { return TYPE; }
    }

    private MillMenus() {}

    public static void register() {
        PayloadTypeRegistry.clientboundPlay().register(Payload.TYPE, Payload.CODEC);
    }

    public static void show(ServerPlayer player, MillMenu menu) {
        if (ServerPlayNetworking.canSend(player, Payload.TYPE)) ServerPlayNetworking.send(player, new Payload(menu.toJson()));
        else player.sendSystemMessage(menu.toChat());
    }
}
