package dev.kgoodwin.midnightcouncil.api.voice;

import dev.kgoodwin.midnightcouncil.api.ApiTestBase;

import org.junit.jupiter.api.Test;

import java.io.IOException;

class VoiceApiPurityTest extends ApiTestBase {

	@Test
	void voicePackageHasNoMinecraftOrFabricImports() throws IOException {
		assertPackagePurity("src/main/java/dev/kgoodwin/midnightcouncil/api/voice");
	}
}
