package com.gotham.fiestapunish.mixin;

// Empty stub — all chat filtering is handled via Fabric API events in ChatEventHandler.java
import net.minecraft.server.network.ServerPlayNetworkHandler;
import org.spongepowered.asm.mixin.Mixin;

@Mixin(ServerPlayNetworkHandler.class)
public abstract class ServerPlayNetworkHandlerMixin {
    // intentionally empty
}
