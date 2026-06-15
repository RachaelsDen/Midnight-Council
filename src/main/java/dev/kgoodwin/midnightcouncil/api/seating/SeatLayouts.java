package dev.kgoodwin.midnightcouncil.api.seating;

import dev.kgoodwin.midnightcouncil.api.Position;

import java.util.List;
import java.util.Map;

public final class SeatLayouts {
	public static final List<String> SEAT_COLORS = List.of(
		"red",
		"orange",
		"yellow",
		"lime",
		"green",
		"cyan",
		"light_blue",
		"blue",
		"purple",
		"pink",
		"white",
		"black",
		"brown",
		"gray",
		"light_gray"
	);

	private static final Map<Integer, SeatLayout> LAYOUTS = Map.ofEntries(
		Map.entry(5, new SeatLayout(
			List.of(
				new Position(0.0, -1.0, 11.0),
				new Position(-11.0, -1.0, 1.0),
				new Position(-6.0, -1.0, -11.0),
				new Position(6.0, -1.0, -11.0),
				new Position(11.0, -1.0, 1.0)
			),
			List.of(
				new Position(0.0, -1.0, 6.0),
				new Position(-6.0, -1.0, 1.0),
				new Position(-4.0, -1.0, -6.0),
				new Position(4.0, -1.0, -6.0),
				new Position(6.0, -1.0, 1.0)
			),
			List.of(
				new Position(0.0, -1.0, 9.0),
				new Position(-9.0, -1.0, 1.0),
				new Position(-5.0, -1.0, -9.0),
				new Position(5.0, -1.0, -9.0),
				new Position(9.0, -1.0, 1.0)
			),
			List.of(Direction.W, Direction.N, Direction.E, Direction.E, Direction.S),
			SEAT_COLORS.subList(0, 5)
		)),
		Map.entry(6, new SeatLayout(
			List.of(
				new Position(-4.0, -1.0, 10.0),
				new Position(-11.0, -1.0, 0.0),
				new Position(-4.0, -1.0, -10.0),
				new Position(4.0, -1.0, -10.0),
				new Position(11.0, -1.0, 0.0),
				new Position(4.0, -1.0, 10.0)
			),
			List.of(
				new Position(-3.0, -1.0, 5.0),
				new Position(-6.0, -1.0, 0.0),
				new Position(-3.0, -1.0, -5.0),
				new Position(3.0, -1.0, -5.0),
				new Position(6.0, -1.0, 0.0),
				new Position(3.0, -1.0, 5.0)
			),
			List.of(
				new Position(-4.0, -1.0, 8.0),
				new Position(-9.0, -1.0, 0.0),
				new Position(-4.0, -1.0, -8.0),
				new Position(4.0, -1.0, -8.0),
				new Position(9.0, -1.0, 0.0),
				new Position(4.0, -1.0, 8.0)
			),
			List.of(Direction.W, Direction.N, Direction.E, Direction.E, Direction.S, Direction.W),
			SEAT_COLORS.subList(0, 6)
		)),
		Map.entry(7, new SeatLayout(
			List.of(
				new Position(0.0, -1.0, 11.0),
				new Position(-9.0, -1.0, 8.0),
				new Position(-11.0, -1.0, -3.0),
				new Position(-5.0, -1.0, -11.0),
				new Position(5.0, -1.0, -11.0),
				new Position(11.0, -1.0, -3.0),
				new Position(9.0, -1.0, 8.0)
			),
			List.of(
				new Position(0.0, -1.0, 6.0),
				new Position(-5.0, -1.0, 3.0),
				new Position(-6.0, -1.0, -2.0),
				new Position(-3.0, -1.0, -6.0),
				new Position(3.0, -1.0, -6.0),
				new Position(6.0, -1.0, -2.0),
				new Position(5.0, -1.0, 3.0)
			),
			List.of(
				new Position(0.0, -1.0, 9.0),
				new Position(-7.0, -1.0, 6.0),
				new Position(-9.0, -1.0, -3.0),
				new Position(-4.0, -1.0, -9.0),
				new Position(4.0, -1.0, -9.0),
				new Position(9.0, -1.0, -3.0),
				new Position(7.0, -1.0, 6.0)
			),
			List.of(Direction.W, Direction.NW, Direction.N, Direction.E, Direction.E, Direction.S, Direction.SW),
			SEAT_COLORS.subList(0, 7)
		)),
		Map.entry(8, new SeatLayout(
			List.of(
				new Position(-3.0, -1.0, 11.0),
				new Position(-11.0, -1.0, 3.0),
				new Position(-11.0, -1.0, -3.0),
				new Position(-3.0, -1.0, -11.0),
				new Position(3.0, -1.0, -11.0),
				new Position(11.0, -1.0, -3.0),
				new Position(11.0, -1.0, 3.0),
				new Position(3.0, -1.0, 11.0)
			),
			List.of(
				new Position(-2.0, -1.0, 6.0),
				new Position(-6.0, -1.0, 2.0),
				new Position(-6.0, -1.0, -2.0),
				new Position(-2.0, -1.0, -6.0),
				new Position(2.0, -1.0, -6.0),
				new Position(6.0, -1.0, -2.0),
				new Position(6.0, -1.0, 2.0),
				new Position(2.0, -1.0, 6.0)
			),
			List.of(
				new Position(-3.0, -1.0, 9.0),
				new Position(-9.0, -1.0, 3.0),
				new Position(-9.0, -1.0, -3.0),
				new Position(-3.0, -1.0, -9.0),
				new Position(3.0, -1.0, -9.0),
				new Position(9.0, -1.0, -3.0),
				new Position(9.0, -1.0, 3.0),
				new Position(3.0, -1.0, 9.0)
			),
			List.of(Direction.W, Direction.N, Direction.N, Direction.E, Direction.E, Direction.S, Direction.S, Direction.W),
			SEAT_COLORS.subList(0, 8)
		)),
		Map.entry(9, new SeatLayout(
			List.of(
				new Position(0.0, -1.0, 12.0),
				new Position(-9.0, -1.0, 10.0),
				new Position(-12.0, -1.0, 2.0),
				new Position(-11.0, -1.0, -7.0),
				new Position(-4.0, -1.0, -12.0),
				new Position(4.0, -1.0, -12.0),
				new Position(11.0, -1.0, -7.0),
				new Position(12.0, -1.0, 2.0),
				new Position(9.0, -1.0, 10.0)
			),
			List.of(
				new Position(0.0, -1.0, 7.0),
				new Position(-5.0, -1.0, 5.0),
				new Position(-7.0, -1.0, 1.0),
				new Position(-6.0, -1.0, -4.0),
				new Position(-2.0, -1.0, -7.0),
				new Position(2.0, -1.0, -7.0),
				new Position(6.0, -1.0, -4.0),
				new Position(7.0, -1.0, 1.0),
				new Position(5.0, -1.0, 5.0)
			),
			List.of(
				new Position(0.0, -1.0, 10.0),
				new Position(-7.0, -1.0, 8.0),
				new Position(-10.0, -1.0, 2.0),
				new Position(-9.0, -1.0, -5.0),
				new Position(-3.0, -1.0, -10.0),
				new Position(3.0, -1.0, -10.0),
				new Position(9.0, -1.0, -5.0),
				new Position(10.0, -1.0, 2.0),
				new Position(7.0, -1.0, 8.0)
			),
			List.of(Direction.W, Direction.NW, Direction.N, Direction.NE, Direction.E, Direction.E, Direction.SE, Direction.S, Direction.SW),
			SEAT_COLORS.subList(0, 9)
		)),
		Map.entry(10, new SeatLayout(
			List.of(
				new Position(-3.0, -1.0, 12.0),
				new Position(-10.0, -1.0, 8.0),
				new Position(-12.0, -1.0, 0.0),
				new Position(-10.0, -1.0, -8.0),
				new Position(-3.0, -1.0, -12.0),
				new Position(3.0, -1.0, -12.0),
				new Position(10.0, -1.0, -8.0),
				new Position(12.0, -1.0, 0.0),
				new Position(10.0, -1.0, 8.0),
				new Position(3.0, -1.0, 12.0)
			),
			List.of(
				new Position(-2.0, -1.0, 7.0),
				new Position(-6.0, -1.0, 4.0),
				new Position(-7.0, -1.0, 0.0),
				new Position(-6.0, -1.0, -4.0),
				new Position(-2.0, -1.0, -7.0),
				new Position(2.0, -1.0, -7.0),
				new Position(6.0, -1.0, -4.0),
				new Position(7.0, -1.0, 0.0),
				new Position(6.0, -1.0, 4.0),
				new Position(2.0, -1.0, 7.0)
			),
			List.of(
				new Position(-3.0, -1.0, 10.0),
				new Position(-8.0, -1.0, 6.0),
				new Position(-10.0, -1.0, 0.0),
				new Position(-8.0, -1.0, -6.0),
				new Position(-3.0, -1.0, -10.0),
				new Position(3.0, -1.0, -10.0),
				new Position(8.0, -1.0, -6.0),
				new Position(10.0, -1.0, 0.0),
				new Position(8.0, -1.0, 6.0),
				new Position(3.0, -1.0, 10.0)
			),
			List.of(Direction.W, Direction.NW, Direction.N, Direction.NE, Direction.E, Direction.E, Direction.SE, Direction.S, Direction.SW, Direction.W),
			SEAT_COLORS.subList(0, 10)
		)),
		Map.entry(11, new SeatLayout(
			List.of(
				new Position(0.0, -1.0, 12.0),
				new Position(-7.0, -1.0, 10.0),
				new Position(-11.0, -1.0, 5.0),
				new Position(-12.0, -1.0, -2.0),
				new Position(-9.0, -1.0, -9.0),
				new Position(-3.0, -1.0, -12.0),
				new Position(3.0, -1.0, -12.0),
				new Position(9.0, -1.0, -9.0),
				new Position(12.0, -1.0, -2.0),
				new Position(11.0, -1.0, 5.0),
				new Position(7.0, -1.0, 10.0)
			),
			List.of(
				new Position(0.0, -1.0, 7.0),
				new Position(-4.0, -1.0, 6.0),
				new Position(-6.0, -1.0, 3.0),
				new Position(-7.0, -1.0, -1.0),
				new Position(-5.0, -1.0, -5.0),
				new Position(-2.0, -1.0, -7.0),
				new Position(2.0, -1.0, -7.0),
				new Position(5.0, -1.0, -5.0),
				new Position(7.0, -1.0, -1.0),
				new Position(6.0, -1.0, 3.0),
				new Position(4.0, -1.0, 6.0)
			),
			List.of(
				new Position(0.0, -1.0, 10.0),
				new Position(-6.0, -1.0, 8.0),
				new Position(-9.0, -1.0, 4.0),
				new Position(-10.0, -1.0, -2.0),
				new Position(-7.0, -1.0, -7.0),
				new Position(-3.0, -1.0, -10.0),
				new Position(3.0, -1.0, -10.0),
				new Position(7.0, -1.0, -7.0),
				new Position(10.0, -1.0, -2.0),
				new Position(9.0, -1.0, 4.0),
				new Position(6.0, -1.0, 8.0)
			),
			List.of(Direction.W, Direction.NW, Direction.N, Direction.N, Direction.NE, Direction.E, Direction.E, Direction.SE, Direction.S, Direction.S, Direction.SW),
			SEAT_COLORS.subList(0, 11)
		)),
		Map.entry(12, new SeatLayout(
			List.of(
				new Position(-3.0, -1.0, 12.0),
				new Position(-9.0, -1.0, 9.0),
				new Position(-12.0, -1.0, 3.0),
				new Position(-12.0, -1.0, -3.0),
				new Position(-9.0, -1.0, -9.0),
				new Position(-3.0, -1.0, -12.0),
				new Position(3.0, -1.0, -12.0),
				new Position(9.0, -1.0, -9.0),
				new Position(12.0, -1.0, -3.0),
				new Position(12.0, -1.0, 3.0),
				new Position(9.0, -1.0, 9.0),
				new Position(3.0, -1.0, 12.0)
			),
			List.of(
				new Position(-2.0, -1.0, 7.0),
				new Position(-5.0, -1.0, 5.0),
				new Position(-7.0, -1.0, 2.0),
				new Position(-7.0, -1.0, -2.0),
				new Position(-5.0, -1.0, -5.0),
				new Position(-2.0, -1.0, -7.0),
				new Position(2.0, -1.0, -7.0),
				new Position(5.0, -1.0, -5.0),
				new Position(7.0, -1.0, -2.0),
				new Position(7.0, -1.0, 2.0),
				new Position(5.0, -1.0, 5.0),
				new Position(2.0, -1.0, 7.0)
			),
			List.of(
				new Position(-3.0, -1.0, 10.0),
				new Position(-7.0, -1.0, 7.0),
				new Position(-10.0, -1.0, 3.0),
				new Position(-10.0, -1.0, -3.0),
				new Position(-7.0, -1.0, -7.0),
				new Position(-3.0, -1.0, -10.0),
				new Position(3.0, -1.0, -10.0),
				new Position(7.0, -1.0, -7.0),
				new Position(10.0, -1.0, -3.0),
				new Position(10.0, -1.0, 3.0),
				new Position(7.0, -1.0, 7.0),
				new Position(3.0, -1.0, 10.0)
			),
			List.of(Direction.W, Direction.NW, Direction.N, Direction.N, Direction.NE, Direction.E, Direction.E, Direction.SE, Direction.S, Direction.S, Direction.SW, Direction.W),
			SEAT_COLORS.subList(0, 12)
		)),
		Map.entry(13, new SeatLayout(
			List.of(
				new Position(0.0, -1.0, 12.0),
				new Position(5.6, -1.0, 10.6),
				new Position(9.9, -1.0, 6.8),
				new Position(11.9, -1.0, 1.4),
				new Position(11.2, -1.0, -4.3),
				new Position(8.0, -1.0, -9.0),
				new Position(2.9, -1.0, -11.7),
				new Position(-2.9, -1.0, -11.7),
				new Position(-8.0, -1.0, -9.0),
				new Position(-11.2, -1.0, -4.3),
				new Position(-11.9, -1.0, 1.4),
				new Position(-9.9, -1.0, 6.8),
				new Position(-5.6, -1.0, 10.6)
			),
			List.of(
				new Position(0.0, -1.0, 7.0),
				new Position(3.3, -1.0, 6.2),
				new Position(5.8, -1.0, 4.0),
				new Position(6.9, -1.0, 0.8),
				new Position(6.5, -1.0, -2.5),
				new Position(4.6, -1.0, -5.2),
				new Position(1.7, -1.0, -6.8),
				new Position(-1.7, -1.0, -6.8),
				new Position(-4.6, -1.0, -5.2),
				new Position(-6.5, -1.0, -2.5),
				new Position(-6.9, -1.0, 0.8),
				new Position(-5.8, -1.0, 4.0),
				new Position(-3.3, -1.0, 6.2)
			),
			List.of(
				new Position(0.0, -1.0, 10.0),
				new Position(4.6, -1.0, 8.9),
				new Position(8.2, -1.0, 5.7),
				new Position(9.9, -1.0, 1.2),
				new Position(9.4, -1.0, -3.5),
				new Position(6.6, -1.0, -7.5),
				new Position(2.4, -1.0, -9.7),
				new Position(-2.4, -1.0, -9.7),
				new Position(-6.6, -1.0, -7.5),
				new Position(-9.4, -1.0, -3.5),
				new Position(-9.9, -1.0, 1.2),
				new Position(-8.2, -1.0, 5.7),
				new Position(-4.6, -1.0, 8.9)
			),
			List.of(Direction.N, Direction.NE, Direction.NE, Direction.E, Direction.E, Direction.SE, Direction.S, Direction.S, Direction.SW, Direction.W, Direction.W, Direction.NW, Direction.NW),
			SEAT_COLORS.subList(0, 13)
		)),
		Map.entry(14, new SeatLayout(
			List.of(
				new Position(0.0, -1.0, 12.0),
				new Position(5.2, -1.0, 10.8),
				new Position(9.4, -1.0, 7.5),
				new Position(11.7, -1.0, 2.7),
				new Position(11.7, -1.0, -2.7),
				new Position(9.4, -1.0, -7.5),
				new Position(5.2, -1.0, -10.8),
				new Position(0.0, -1.0, -12.0),
				new Position(-5.2, -1.0, -10.8),
				new Position(-9.4, -1.0, -7.5),
				new Position(-11.7, -1.0, -2.7),
				new Position(-11.7, -1.0, 2.7),
				new Position(-9.4, -1.0, 7.5),
				new Position(-5.2, -1.0, 10.8)
			),
			List.of(
				new Position(0.0, -1.0, 7.0),
				new Position(3.0, -1.0, 6.3),
				new Position(5.5, -1.0, 4.4),
				new Position(6.8, -1.0, 1.6),
				new Position(6.8, -1.0, -1.6),
				new Position(5.5, -1.0, -4.4),
				new Position(3.0, -1.0, -6.3),
				new Position(0.0, -1.0, -7.0),
				new Position(-3.0, -1.0, -6.3),
				new Position(-5.5, -1.0, -4.4),
				new Position(-6.8, -1.0, -1.6),
				new Position(-6.8, -1.0, 1.6),
				new Position(-5.5, -1.0, 4.4),
				new Position(-3.0, -1.0, 6.3)
			),
			List.of(
				new Position(0.0, -1.0, 10.0),
				new Position(4.3, -1.0, 9.0),
				new Position(7.8, -1.0, 6.2),
				new Position(9.7, -1.0, 2.2),
				new Position(9.7, -1.0, -2.2),
				new Position(7.8, -1.0, -6.2),
				new Position(4.3, -1.0, -9.0),
				new Position(0.0, -1.0, -10.0),
				new Position(-4.3, -1.0, -9.0),
				new Position(-7.8, -1.0, -6.2),
				new Position(-9.7, -1.0, -2.2),
				new Position(-9.7, -1.0, 2.2),
				new Position(-7.8, -1.0, 6.2),
				new Position(-4.3, -1.0, 9.0)
			),
			List.of(Direction.N, Direction.NE, Direction.NE, Direction.E, Direction.E, Direction.SE, Direction.SE, Direction.S, Direction.SW, Direction.SW, Direction.W, Direction.W, Direction.NW, Direction.NW),
			SEAT_COLORS.subList(0, 14)
		)),
		Map.entry(15, new SeatLayout(
			List.of(
				new Position(0.0, -1.0, 12.0),
				new Position(4.9, -1.0, 11.0),
				new Position(8.9, -1.0, 8.0),
				new Position(11.4, -1.0, 3.7),
				new Position(11.9, -1.0, -1.3),
				new Position(10.4, -1.0, -6.0),
				new Position(7.1, -1.0, -9.7),
				new Position(2.5, -1.0, -11.7),
				new Position(-2.5, -1.0, -11.7),
				new Position(-7.1, -1.0, -9.7),
				new Position(-10.4, -1.0, -6.0),
				new Position(-11.9, -1.0, -1.3),
				new Position(-11.4, -1.0, 3.7),
				new Position(-8.9, -1.0, 8.0),
				new Position(-4.9, -1.0, 11.0)
			),
			List.of(
				new Position(0.0, -1.0, 7.0),
				new Position(2.8, -1.0, 6.4),
				new Position(5.2, -1.0, 4.7),
				new Position(6.7, -1.0, 2.2),
				new Position(7.0, -1.0, -0.7),
				new Position(6.1, -1.0, -3.5),
				new Position(4.1, -1.0, -5.7),
				new Position(1.5, -1.0, -6.8),
				new Position(-1.5, -1.0, -6.8),
				new Position(-4.1, -1.0, -5.7),
				new Position(-6.1, -1.0, -3.5),
				new Position(-7.0, -1.0, -0.7),
				new Position(-6.7, -1.0, 2.2),
				new Position(-5.2, -1.0, 4.7),
				new Position(-2.8, -1.0, 6.4)
			),
			List.of(
				new Position(0.0, -1.0, 10.0),
				new Position(4.1, -1.0, 9.1),
				new Position(7.4, -1.0, 6.7),
				new Position(9.5, -1.0, 3.1),
				new Position(9.9, -1.0, -1.0),
				new Position(8.7, -1.0, -5.0),
				new Position(5.9, -1.0, -8.1),
				new Position(2.1, -1.0, -9.8),
				new Position(-2.1, -1.0, -9.8),
				new Position(-5.9, -1.0, -8.1),
				new Position(-8.7, -1.0, -5.0),
				new Position(-9.9, -1.0, -1.0),
				new Position(-9.5, -1.0, 3.1),
				new Position(-7.4, -1.0, 6.7),
				new Position(-4.1, -1.0, 9.1)
			),
			List.of(Direction.N, Direction.NE, Direction.NE, Direction.E, Direction.E, Direction.SE, Direction.SE, Direction.S, Direction.S, Direction.SW, Direction.SW, Direction.W, Direction.W, Direction.NW, Direction.NW),
			SEAT_COLORS.subList(0, 15)
		))
	);

	private SeatLayouts() {
	}

	public static SeatLayout getLayout(int playerCount) {
		SeatLayout layout = LAYOUTS.get(playerCount);
		if (layout == null) {
			throw new IllegalArgumentException("No seat layout available for player count: " + playerCount);
		}
		return layout;
	}
}
