package dev.kgoodwin.midnightcouncil.api;

import org.junit.jupiter.api.Test;

import java.io.IOException;

class ApiPurityTest extends ApiTestBase {

	@Test
	void entireApiPackageHasNoMinecraftOrFabricImports() throws IOException {
		assertPackagePurity("src/main/java/dev/kgoodwin/midnightcouncil/api");
	}
}
