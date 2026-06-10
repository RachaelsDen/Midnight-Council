package dev.kgoodwin.midnightcouncil.api;

import static org.junit.jupiter.api.Assertions.assertFalse;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.stream.Stream;

public abstract class ApiTestBase {

	protected static String readSourceFile(String relativePath) throws IOException {
		return Files.readString(Path.of(relativePath), StandardCharsets.UTF_8);
	}

	protected static void assertNoMinecraftOrFabricImports(String source, String filePath) {
		assertFalse(source.contains("net.minecraft"), "Found Minecraft import in " + filePath);
		assertFalse(source.contains("net.fabricmc"), "Found Fabric import in " + filePath);
	}

	protected static void assertPackagePurity(String packageDir) throws IOException {
		try (Stream<Path> paths = Files.walk(Path.of(packageDir))) {
			paths.filter(p -> p.toString().endsWith(".java"))
				.forEach(p -> {
					try {
						String source = Files.readString(p, StandardCharsets.UTF_8);
						assertNoMinecraftOrFabricImports(source, p.toString());
					} catch (IOException e) {
						throw new RuntimeException("Failed to read " + p, e);
					}
				});
		}
	}
}
