package com.gotham.fiestapunish.mixin;

// This mixin is intentionally left minimal.
// Chat interception is handled via Fabric API's ServerMessageEvents in ChatEventHandler.java
// which is version-stable and does not require mixin method-name matching.

import net.minecraft.server.network.ServerPlayNetworkHandler;
import org.spongepowered.asm.mixin.Mixin;

@Mixin(ServerPlayNetworkHandler.class)
public abstract class ServerPlayNetworkHandlerMixin {
    // intentionally empty — see ChatEventHandler
}
