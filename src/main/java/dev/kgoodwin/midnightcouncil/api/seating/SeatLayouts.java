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
		"black"
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
			SEAT_COLORS
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
