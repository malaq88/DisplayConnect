package com.example.displayconnect.map

import com.example.displayconnect.routing.LatLon
import org.junit.Assert.*
import org.junit.Test

class StreetGeometryTest {
    @Test fun denseCenterDoesNotDiscardAllDistantRoads() {
        val center = (0 until 80).map { y -> listOf(geo(90.0, 90.0 + y / 10.0), geo(140.0, 90.0 + y / 10.0)) }
        val corners = listOf(listOf(geo(10.0, 10.0), geo(30.0, 10.0)), listOf(geo(210.0, 210.0), geo(230.0, 210.0)))
        val drawn = segments(center + corners, 64)
        assertTrue(drawn.any { it[0] < 40 })
        assertTrue(drawn.any { it[0] > 200 })
    }

    @Test fun firmwareBudgetAllowsUpToMaxSegments() {
        val ways = (0 until StreetContextProjector.MAX_SEGMENTS).map { index ->
            val x = (index % 16) * 14.0
            val y = (index / 16) * 14.0
            listOf(geo(x, y), geo(x + 10, y))
        }
        assertEquals(StreetContextProjector.MAX_SEGMENTS, segments(ways, StreetContextProjector.MAX_SEGMENTS).size)
    }

    private fun geo(x: Double, y: Double) = LatLon((116.0 - y) / 111_320.0, (x - 120.0) / 111_320.0)
    private fun segments(ways: List<List<LatLon>>, budget: Int = 64) =
        StreetContextProjector.projectSegments(0.0, 0.0, ways, 116.0, budget)

    @Test fun offscreenRoadDoesNotBecomeAnEdgeLine() {
        assertTrue(segments(listOf(listOf(geo(-20.0, 10.0), geo(-10.0, 200.0)))).isEmpty())
    }

    @Test fun diagonalIsIntersectedAtItsActualBoundary() {
        val line = ScreenGeometry.clip(ScreenPoint(-10.0, 10.0), ScreenPoint(10.0, 30.0))!!
        assertEquals(ScreenPoint(0.0, 20.0), line.first)
        assertEquals(ScreenPoint(10.0, 30.0), line.second)
    }

    @Test fun crossingRoadWithBothEndpointsOutsideIsKept() {
        val lines = segments(listOf(listOf(geo(-20.0, 100.0), geo(260.0, 100.0))))
        assertEquals(1, lines.size)
        assertArrayEquals(intArrayOf(0, 100, 239, 100), lines.single())
    }

    @Test fun closelySpacedNodesBecomeOneContinuousStreet() {
        val way = (20..200).map { geo(it.toDouble(), 100.0) }
        val lines = segments(listOf(way))
        assertEquals(1, lines.size)
        assertArrayEquals(intArrayOf(20, 100, 200, 100), lines.single())
    }

    @Test fun cornerIsPreservedWhenSimplifying() {
        val points = listOf(ScreenPoint(10.0, 10.0), ScreenPoint(50.0, 10.0),
            ScreenPoint(50.0, 60.0))
        assertEquals(points, ScreenGeometry.simplify(points))
    }

    @Test fun allSmallWaysAreKeptWhenTheyFit() {
        val ways = (0 until 50).map { y ->
            listOf(geo(10.0, 2.0 * y), geo(200.0, 2.0 * y))
        }
        assertEquals(50, segments(ways).size)
    }

    @Test fun budgetNeverChopsTheMiddleOfAWay() {
        val bend = listOf(geo(110.0, 110.0), geo(120.0, 110.0), geo(120.0, 140.0))
        assertTrue(segments(listOf(bend), 1).isEmpty())
        assertEquals(2, segments(listOf(bend), 2).size)
    }

    @Test fun offscreenExcursionDoesNotDrawAShortcutAcrossTheMap() {
        val points = listOf(ScreenPoint(100.0, 50.0), ScreenPoint(-20.0, 50.0),
            ScreenPoint(-20.0, 180.0), ScreenPoint(100.0, 180.0))
        val runs = ScreenGeometry.visibleRuns(points)
        assertEquals(2, runs.size)
        assertEquals(ScreenPoint(0.0, 50.0), runs[0].last())
        assertEquals(ScreenPoint(0.0, 180.0), runs[1].first())
        val route = MapProjector.projectRoute(0.0, 0.0,
            points.map { geo(it.x, it.y).let { p -> p.lat to p.lon } }, 116.0)
        assertTrue(route.contains(-1 to -1))
        assertTrue(route.size <= 64)
    }

    @Test fun segmentBudgetIsAlwaysRespected() {
        val ways = (0 until 200).map { y -> listOf(geo(20.0, y.toDouble()), geo(220.0, y.toDouble())) }
        assertEquals(64, segments(ways).size)
        assertTrue(segments(ways, 0).isEmpty())
    }
}
