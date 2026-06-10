package dev.kgoodwin.midnightcouncil.fabric;

import dev.kgoodwin.midnightcouncil.fabric.adapter.FabricVoiceAdapter;

import net.fabricmc.api.ModInitializer;

public final class MidnightCouncilMod implements ModInitializer {

	@Override
	public void onInitialize() {
		FabricVoiceAdapter voiceAdapter = new FabricVoiceAdapter();
		voiceAdapter.register();
	}
}
