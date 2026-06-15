package dev.kgoodwin.midnightcouncil.client.gui;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.assertEquals;

public class SeatingChartScreenTest {

    @Test
    public void testCalculateSeatAngle() {
        double angle0 = SeatingChartScreen.calculateSeatAngle(0, 4);
        assertEquals(-Math.PI / 2, angle0, 0.001);

        double angle1 = SeatingChartScreen.calculateSeatAngle(1, 4);
        assertEquals(0, angle1, 0.001);
        
        double angle2 = SeatingChartScreen.calculateSeatAngle(2, 4);
        assertEquals(Math.PI / 2, angle2, 0.001);
    }

    @Test
    public void testCalculateSeatPosition() {
        int[] pos = SeatingChartScreen.calculateSeatPosition(0, 4, 100, 100, 50);
        assertEquals(100, pos[0]);
        assertEquals(50, pos[1]);
        
        int[] pos1 = SeatingChartScreen.calculateSeatPosition(1, 4, 100, 100, 50);
        assertEquals(150, pos1[0]);
        assertEquals(100, pos1[1]);
    }
}
