package com.example.displayconnect.routing

import org.junit.Assert.*
import org.junit.Test

class RouteProgressCalculatorTest {
    private val points = listOf(LatLon(0.0, 0.0), LatLon(0.0, 0.01), LatLon(0.01, 0.01))
    private fun route() = RouteData(points, emptyList(), 2000.0, 600.0,
        listOf(1000.0, 1000.0), listOf(100.0, 500.0))

    @Test fun remainingDistanceFollowsTheBendAndTimeUsesSegmentSpeeds() {
        val progress = RouteProgressCalculator(route()).update(LatLon(0.0, 0.005))
        assertEquals(1500, progress.remainingDistanceM!!, 1)
        assertEquals(550, progress.remainingDurationS!!, 1)
    }

    @Test fun totalsDecreaseAsGpsMovesAndReachZeroAtDestination() {
        val calculator = RouteProgressCalculator(route())
        val start = calculator.update(points.first())
        val middle = calculator.update(LatLon(0.005, 0.01))
        val end = calculator.update(points.last())
        assertEquals(2000, start.remainingDistanceM)
        assertEquals(500, middle.remainingDistanceM!!, 1)
        assertEquals(250, middle.remainingDurationS!!, 1)
        assertEquals(0, end.remainingDistanceM)
        assertEquals(0, end.remainingDurationS)
    }

    @Test fun annotationsAreScaledToTheTotalIncludingTurnDelays() {
        val progress = RouteProgressCalculator(route().copy(durationS = 720.0)).update(LatLon(0.005, 0.01))
        assertEquals(300, progress.remainingDurationS!!, 1)
    }

    @Test fun missingAnnotationsFallBackToGeometry() {
        val calculator = RouteProgressCalculator(route().copy(segmentDistancesM = emptyList(), segmentDurationsS = emptyList()))
        val progress = calculator.update(points[1])
        assertEquals(1000, progress.remainingDistanceM!!, 1)
        assertEquals(300, progress.remainingDurationS!!, 1)
    }

    @Test fun offRouteHidesBothEstimatesAndRecoversOnReturn() {
        val calculator = RouteProgressCalculator(route())
        val away = calculator.update(LatLon(1.0, 1.0))
        assertTrue(away.offRoute)
        assertNull(away.remainingDistanceM)
        assertNull(away.remainingDurationS)
        assertFalse(calculator.update(points[1]).offRoute)
    }

    @Test fun duplicateVerticesAndInvalidAnnotationsDoNotProduceInvalidTotals() {
        val route = RouteData(listOf(points[0], points[0], points[1]), emptyList(), 1000.0, 120.0,
            listOf(Double.NaN, -1.0), listOf(-10.0))
        val progress = RouteProgressCalculator(route).update(LatLon(0.0, 0.005))
        assertEquals(500, progress.remainingDistanceM!!, 1)
        assertEquals(60, progress.remainingDurationS!!, 1)
    }

    @Test fun unknownDurationDoesNotPretendToBeZero() {
        assertNull(RouteProgressCalculator(route().copy(durationS = 0.0)).update(points[0]).remainingDurationS)
        assertNull(RouteProgressCalculator(RouteData(emptyList(), emptyList())).update(points[0]).remainingDistanceM)
    }

    private fun assertEquals(expected: Int, actual: Int, tolerance: Int) {
        assertTrue("expected $expected, actual $actual", kotlin.math.abs(expected - actual) <= tolerance)
    }
}
