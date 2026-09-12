package com.example.displayconnect.offline

import com.example.displayconnect.routing.*
import java.io.*
import java.util.zip.GZIPInputStream
import java.util.zip.GZIPOutputStream

/** App-private persistent files; a tile is only published after its complete payload is written. */
class OfflineDiskStore(private val root: File) {
    private val memory = LinkedHashMap<StreetTile, List<StreetWay>>(32, 0.75f, true)
    init { check(root.isDirectory || root.mkdirs()) { "Não foi possível criar o mapa offline" } }

    @Synchronized fun load(tile: StreetTile): List<StreetWay>? {
        memory[tile]?.let { return it }
        val result = read(File(root, tile.key + ".gz")) { input ->
            List(input.count(50000)) {
                val id = input.readLong()
                StreetWay(id, input.points())
            }
        } ?: return null
        remember(tile, result)
        return result
    }

    @Synchronized fun save(tile: StreetTile, ways: List<StreetWay>) {
        write(File(root, tile.key + ".gz")) { output ->
            output.writeInt(ways.size)
            for (way in ways) { output.writeLong(way.id); output.points(way.points) }
        }
        remember(tile, ways)
    }

    private fun remember(tile: StreetTile, ways: List<StreetWay>) {
        memory[tile] = ways
        if (memory.size > 32) memory.remove(memory.keys.first())
    }

    @Synchronized fun trim(protectedTiles: Set<StreetTile>) {
        val files = root.listFiles()?.filter { it.name.startsWith("14_") && it.extension == "gz" }.orEmpty()
        var bytes = files.sumOf { it.length() }
        val protectedNames = protectedTiles.map { it.key + ".gz" }.toSet()
        for (file in files.sortedBy { it.lastModified() }) {
            if (bytes <= 256L * 1024 * 1024) break
            val size = file.length()
            if (file.name !in protectedNames && file.delete()) bytes -= size
        }
    }

    @Synchronized fun saveRoute(route: RouteData, destination: LatLon, profile: RouteProfile) {
        write(File(root, "last-route.gz")) { output ->
            output.point(destination); output.writeUTF(profile.storageKey)
            output.points(route.coordinates)
            output.writeDouble(route.distanceM); output.writeDouble(route.durationS)
            output.writeInt(route.steps.size)
            for (step in route.steps) {
                output.writeUTF(step.instruction); output.writeUTF(step.street)
                output.writeInt(step.distanceM); output.point(step.endLocation)
            }
            output.doubles(route.segmentDistancesM); output.doubles(route.segmentDurationsS)
        }
    }

    @Synchronized fun findRoute(origin: LatLon, destination: LatLon, profile: RouteProfile): RouteData? {
        val saved = read(File(root, "last-route.gz")) { input ->
            val dest = input.point(); val storedProfile = input.readUTF()
            val coordinates = input.points()
            val distance = input.readDouble(); val duration = input.readDouble()
            val steps = List(input.count(100000)) {
                RouteStep(input.readUTF(), input.readUTF(), input.readInt(), input.point())
            }
            Triple(dest, storedProfile, RouteData(coordinates, steps, distance, duration, input.doubles(), input.doubles()))
        } ?: return null
        if (saved.second != profile.storageKey || kotlin.math.abs(saved.first.lat - destination.lat) > 0.00001 ||
            kotlin.math.abs(saved.first.lon - destination.lon) > 0.00001) return null
        val progress = RouteProgressCalculator(saved.third).update(origin)
        return saved.third.takeIf { progress.remainingDistanceM != null && !progress.offRoute }
    }

    private fun <T> read(file: File, block: (DataInputStream) -> T): T? = try {
        val backup = File(file.path + ".bak")
        val source = if (file.exists()) file else backup
        if (!source.exists()) null else DataInputStream(GZIPInputStream(BufferedInputStream(FileInputStream(source)))).use {
            require(it.readInt() == 1)
            val value = block(it)
            require(it.read() == -1) // Verify the gzip trailer, detecting truncated/partial downloads.
            value
        }
    } catch (_: Exception) { null }

    private fun write(file: File, block: (DataOutputStream) -> Unit) {
        val temp = File(file.path + ".tmp")
        val backup = File(file.path + ".bak")
        try {
            DataOutputStream(GZIPOutputStream(BufferedOutputStream(FileOutputStream(temp)))).use {
                it.writeInt(1); block(it)
            }
            if (file.exists()) { check(!backup.exists() || backup.delete()); check(file.renameTo(backup)) }
            if (!temp.renameTo(file)) { backup.renameTo(file); error("Não foi possível salvar o mapa") }
            backup.delete()
        } finally { temp.delete() }
    }

    private fun DataInputStream.count(max: Int): Int = readInt().also { require(it in 0..max) }
    private fun DataInputStream.point(): LatLon = LatLon(readDouble(), readDouble()).also {
        require(it.lat in -90.0..90.0 && it.lon in -180.0..180.0)
    }
    private fun DataInputStream.points(): List<LatLon> = List(count(300000)) { point() }.also { require(it.size >= 2) }
    private fun DataInputStream.doubles(): List<Double> = List(count(300000)) { readDouble().also { require(it.isFinite() && it >= 0) } }
    private fun DataOutputStream.point(point: LatLon) { writeDouble(point.lat); writeDouble(point.lon) }
    private fun DataOutputStream.points(points: List<LatLon>) { writeInt(points.size); points.forEach { point(it) } }
    private fun DataOutputStream.doubles(values: List<Double>) { writeInt(values.size); values.forEach { writeDouble(it) } }
}
