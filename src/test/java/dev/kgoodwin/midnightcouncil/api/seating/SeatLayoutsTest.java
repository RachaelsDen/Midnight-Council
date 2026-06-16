package dev.kgoodwin.midnightcouncil.api.seating;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import org.junit.jupiter.api.Test;

class SeatLayoutsTest {

	@Test
	void getLayoutReturnsCorrectSizesForAllPlayerCounts() {
		for (int count = 5; count <= 15; count++) {
			SeatLayout layout = SeatLayouts.getLayout(count);
			assertEquals(count, layout.seatPositions().size(), "seatPositions for " + count);
			assertEquals(count, layout.lightPositions().size(), "lightPositions for " + count);
			assertEquals(count, layout.leverPositions().size(), "leverPositions for " + count);
			assertEquals(count, layout.seatDirections().size(), "seatDirections for " + count);
			assertEquals(count, layout.seatColors().size(), "seatColors for " + count);
		}
	}

	@Test
	void getLayoutThrowsForUnsupportedCounts() {
		assertThrows(IllegalArgumentException.class, () -> SeatLayouts.getLayout(4));
		assertThrows(IllegalArgumentException.class, () -> SeatLayouts.getLayout(0));
		assertThrows(IllegalArgumentException.class, () -> SeatLayouts.getLayout(16));
		assertThrows(IllegalArgumentException.class, () -> SeatLayouts.getLayout(20));
	}

	@Test
	void noDuplicatePositionsInAnyLayout() {
		for (int count = 5; count <= 15; count++) {
			SeatLayout layout = SeatLayouts.getLayout(count);
			assertNoDuplicates(layout.seatPositions(), "seatPositions for " + count);
			assertNoDuplicates(layout.lightPositions(), "lightPositions for " + count);
			assertNoDuplicates(layout.leverPositions(), "leverPositions for " + count);
		}
	}

	@Test
	void seatingCodeHasNoMinecraftOrFabricImports() throws Exception {
		List<String> sourceFiles = List.of(
			"src/main/java/dev/kgoodwin/midnightcouncil/api/seating/Direction.java",
			"src/main/java/dev/kgoodwin/midnightcouncil/api/seating/SeatLayout.java",
			"src/main/java/dev/kgoodwin/midnightcouncil/api/seating/SeatLayouts.java"
		);

		for (String sourceFile : sourceFiles) {
			String src = Files.readString(Path.of(sourceFile), StandardCharsets.UTF_8);
			assertFalse(src.contains("net.minecraft"), "Found Minecraft import in " + sourceFile);
			assertFalse(src.contains("net.fabricmc"), "Found Fabric import in " + sourceFile);
			assertFalse(src.contains("Vec3"), "Found Vec3 in " + sourceFile);
		}
	}

	private static <T> void assertNoDuplicates(List<T> values, String label) {
		Set<T> unique = new HashSet<>(values);
		assertEquals(values.size(), unique.size(), "Duplicate entries in " + label);
	}
}
