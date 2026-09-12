package com.example.displayconnect.map

import org.junit.Assert.assertEquals
import org.junit.Test

class MapProjectorTest {
    @Test fun centerIsCenteredOnPortraitMap() {
        assertEquals(120 to 116, MapProjector.projectPoint(0.0, 0.0, 0.0, 0.0, 200.0))
    }

    @Test fun equalDistancesHaveEqualScaleOnBothAxes() {
        val delta = 100.0 / 111_320.0
        val east = MapProjector.projectPoint(0.0, 0.0, 0.0, delta, 200.0)
        val north = MapProjector.projectPoint(0.0, 0.0, delta, 0.0, 200.0)
        assertEquals(58, east.first - 120)
        assertEquals(east.first - 120, 116 - north.second)
    }

    @Test fun farCoordinatesStayWithinMap() {
        assertEquals(239 to 0, MapProjector.projectPoint(0.0, 0.0, 1.0, 1.0, 200.0))
        assertEquals(0 to 231, MapProjector.projectPoint(0.0, 0.0, -1.0, -1.0, 200.0))
    }
}
