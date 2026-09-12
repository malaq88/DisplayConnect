package com.example.displayconnect.offline

import com.example.displayconnect.navigation.*
import com.example.displayconnect.routing.*
import org.junit.Assert.*
import org.junit.Test

class GpsFilterTest {
    private fun sample(xMeters: Double, time: Long, accuracy: Float = 5f) =
        GpsSample(LatLon(0.0, xMeters / 111320), accuracy, 5f, 90f, time)

    @Test fun rejectsOldInaccurateAndOutOfOrderPositions() {
        val filter = GpsFilter(25.0)
        assertTrue(filter.accept(sample(0.0, 10000), 10000))
        assertFalse(filter.accept(sample(2.0, 9000), 11000))
        assertFalse(filter.accept(sample(2.0, 11000, 150f), 11000))
        assertFalse(filter.accept(sample(10.0, 12000), 20000))
        assertTrue(filter.accept(sample(5.0, 11000), 11000))
    }

    @Test fun isolatedJumpIntoAnotherAreaIsIgnoredAndNextGoodFixRecovers() {
        val filter = GpsFilter(25.0)
        assertTrue(filter.accept(sample(0.0, 10000), 10000))
        assertFalse(filter.accept(sample(500.0, 11000), 11000))
        assertTrue(filter.accept(sample(10.0, 12000), 12000))
    }

    @Test fun consistentAccurateReacquisitionIsAllowedAfterThreeFixes() {
        val filter = GpsFilter(25.0)
        assertTrue(filter.accept(sample(0.0, 10000), 10000))
        assertFalse(filter.accept(sample(500.0, 11000), 11000))
        assertFalse(filter.accept(sample(505.0, 12000), 12000))
        assertTrue(filter.accept(sample(510.0, 13000), 13000))
    }

    @Test fun smallGpsDriftCanFollowRouteButActualDetoursAreNotForcedBack() {
        val raw = LatLon(0.00005, 0.0)
        val route = LatLon(0.0, 0.0)
        assertEquals(route, GpsFilter.displayPosition(raw, route, 6.0, 10f, 5f, 90f, 90f))
        assertEquals(raw, GpsFilter.displayPosition(raw, route, 35.0, 10f, 5f, 90f, 90f))
        assertEquals(raw, GpsFilter.displayPosition(raw, route, 6.0, 10f, 5f, 270f, 90f))
        assertEquals(raw, GpsFilter.displayPosition(raw, route, 6.0, 100f, 5f, 90f, 90f))
    }

    @Test fun navigationInstructionsFollowSelectedLanguageAndPreserveAccents() {
        assertEquals("Vire à direita", NavigationText.instruction("Turn right", "pt-BR"))
        assertEquals("Turn right", NavigationText.instruction("Turn right", "en"))
        assertEquals("Faça o retorno", NavigationText.instruction("Uturn", "pt-BR"))
    }
}
