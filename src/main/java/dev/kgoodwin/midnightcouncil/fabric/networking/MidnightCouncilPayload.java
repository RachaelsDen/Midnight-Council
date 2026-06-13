package dev.kgoodwin.midnightcouncil.fabric.networking;

import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;

public record MidnightCouncilPayload(String channel, byte[] bytes) implements CustomPacketPayload {

    public static final Identifier ID = Identifier.fromNamespaceAndPath("midnightcouncil", "channel_payload");
    public static final Type<MidnightCouncilPayload> TYPE = new Type<>(ID);
    public static final StreamCodec<RegistryFriendlyByteBuf, MidnightCouncilPayload> CODEC = StreamCodec.composite(
            ByteBufCodecs.STRING_UTF8,
            MidnightCouncilPayload::channel,
            ByteBufCodecs.BYTE_ARRAY,
            MidnightCouncilPayload::bytes,
            MidnightCouncilPayload::new);

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
