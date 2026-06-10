package dev.kgoodwin.midnightcouncil.api.seating;

import dev.kgoodwin.midnightcouncil.api.Position;
import java.util.List;

public record SeatLayout(
	List<Position> seatPositions,
	List<Position> lightPositions,
	List<Position> leverPositions,
	List<Direction> seatDirections,
	List<String> seatColors
) {
}
