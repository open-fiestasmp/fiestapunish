package com.gotham.fiestapunish.mixin;

import net.minecraft.server.network.ServerGamePacketListenerImpl;
import org.spongepowered.asm.mixin.Mixin;

// Empty stub — all chat logic handled via Fabric API events in ChatEventHandler
@Mixin(ServerGamePacketListenerImpl.class)
public abstract class ServerPlayNetworkHandlerMixin {
}
