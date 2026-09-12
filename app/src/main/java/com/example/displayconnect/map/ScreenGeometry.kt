package com.example.displayconnect.map

import kotlin.math.roundToInt

data class ScreenPoint(val x: Double, val y: Double)

/** Geometry is clipped as segments, never by independently clamping endpoints. */
object ScreenGeometry {
    fun clip(a: ScreenPoint, b: ScreenPoint): Pair<ScreenPoint, ScreenPoint>? {
        val dx = b.x - a.x
        val dy = b.y - a.y
        var enter = 0.0
        var leave = 1.0
        val p = doubleArrayOf(-dx, dx, -dy, dy)
        val q = doubleArrayOf(a.x, MapProjector.MAP_WIDTH - 1.0 - a.x,
            a.y, MapProjector.MAP_HEIGHT - 1.0 - a.y)
        for (i in p.indices) {
            if (p[i] == 0.0) {
                if (q[i] < 0.0) return null
            } else {
                val t = q[i] / p[i]
                if (p[i] < 0.0) enter = maxOf(enter, t) else leave = minOf(leave, t)
                if (enter > leave) return null
            }
        }
        fun point(t: Double) = ScreenPoint(
            (a.x + t * dx).roundToInt().coerceIn(0, MapProjector.MAP_WIDTH - 1).toDouble(),
            (a.y + t * dy).roundToInt().coerceIn(0, MapProjector.MAP_HEIGHT - 1).toDouble())
        return point(enter) to point(leave)
    }

    fun visibleRuns(points: List<ScreenPoint>): List<List<ScreenPoint>> {
        val runs = mutableListOf<MutableList<ScreenPoint>>()
        var current: MutableList<ScreenPoint>? = null
        for (i in 0 until points.lastIndex) {
            val segment = clip(points[i], points[i + 1])
            if (segment == null) {
                current = null
                continue
            }
            val (a, b) = segment
            if (a == b) continue
            if (current?.lastOrNull() != a) {
                current = mutableListOf(a)
                runs.add(current)
            }
            current!!.add(b)
        }
        return runs
    }

    fun distanceSquared(p: ScreenPoint, a: ScreenPoint, b: ScreenPoint): Double {
        val dx = b.x - a.x
        val dy = b.y - a.y
        val length = dx * dx + dy * dy
        val t = if (length == 0.0) 0.0 else
            (((p.x - a.x) * dx + (p.y - a.y) * dy) / length).coerceIn(0.0, 1.0)
        val ex = p.x - (a.x + t * dx)
        val ey = p.y - (a.y + t * dy)
        return ex * ex + ey * ey
    }

    fun simplify(points: List<ScreenPoint>, tolerance: Double = 1.0): List<ScreenPoint> {
        if (points.size < 3) return points
        val keep = BooleanArray(points.size)
        keep[0] = true
        keep[points.lastIndex] = true
        val pending = ArrayDeque<Pair<Int, Int>>()
        pending.addLast(0 to points.lastIndex)
        while (pending.isNotEmpty()) {
            val (first, last) = pending.removeLast()
            var furthest = -1
            var distance = tolerance * tolerance
            for (i in first + 1 until last) {
                val d = distanceSquared(points[i], points[first], points[last])
                if (d > distance) { distance = d; furthest = i }
            }
            if (furthest >= 0) {
                keep[furthest] = true
                pending.addLast(first to furthest)
                pending.addLast(furthest to last)
            }
        }
        return points.filterIndexed { index, _ -> keep[index] }
    }
}
