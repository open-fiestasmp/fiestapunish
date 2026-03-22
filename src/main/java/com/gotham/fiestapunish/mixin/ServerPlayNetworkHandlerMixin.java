package com.gotham.fiestapunish.mixin;

import net.minecraft.server.network.ServerPlayNetworkHandler;
import org.spongepowered.asm.mixin.Mixin;

// Empty — all chat logic is in ChatEventHandler via Fabric API events
@Mixin(ServerPlayNetworkHandler.class)
public abstract class ServerPlayNetworkHandlerMixin {
}
