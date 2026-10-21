package org.bytefred.pocoview.core

/** A literature reference; [url] may be null. */
data class Reference(val text: String, val url: String?)

/**
 * A family of point sets with a generator: the user enters n, the family produces
 * [size] (n) points, e.g. n rounded up to a valid size.
 */
interface PointSetFamily {
    val id: String
    val name: String
    val description: String
    val references: List<Reference>

    /** Largest n whose coordinates stay within [COORD_LIMIT]. */
    val maxN: Int

    /** Number of points [generate] produces for the requested [n]. */
    fun size(n: Int): Int

    /** @throws IllegalArgumentException if n < 1 or [size] (n) > [maxN]. */
    fun generate(n: Int): List<Pt>
}

val FAMILIES: List<PointSetFamily> = listOf(HortonFamily, ConvexFamily)

/**
 * Horton sets: H(1) = {(0,0)}; H(2m) = {(2x, y)} ∪ {(2x+1, y + d_m)} for (x, y) in H(m), with d_m
 * the smallest power of 2 such that the odd part lies above every line through two even points and
 * the even part below every line through two odd points (checked exactly). No empty convex 7-gon.
 */
object HortonFamily : PointSetFamily {
    override val id = "horton"
    override val name = "Horton set"
    override val description = "No empty convex 7-gon (7-hole). n is rounded up to the next power of 2."
    override val references = listOf(
        Reference("J. D. Horton, Sets with no empty convex 7-gons, Canad. Math. Bull. 26 (1983) 482-484", null),
        Reference("I. Bárány, Z. Füredi, Empty simplices in Euclidean space, Canad. Math. Bull. 30 (1987) 436-445", null),
        Reference(
            "O. Devillers, F. Hurtado, G. Károlyi, C. Seara, Chromatic variants of the Erdős-Szekeres theorem on points in convex position, Comput. Geom. 26(3) (2003) 193-208",
            null,
        ),
    )

    /** H(1), H(2), H(4), ... as long as the coordinates fit into [COORD_LIMIT]. */
    private val levels: List<List<Pt>> by lazy {
        val res = arrayListOf(listOf(Pt(0, 0)))
        while (true) res.add(double(res.last()) ?: break)
        res
    }

    override val maxN: Int get() = levels.last().size

    override fun size(n: Int): Int {
        require(n >= 1) { "n must be at least 1" }
        var s = 1
        while (s < n) s *= 2
        return s
    }

    override fun generate(n: Int): List<Pt> {
        val s = size(n)
        require(s <= maxN) { "Horton sets are available up to n = $maxN (coordinates within ±$COORD_LIMIT); $s requested" }
        return levels.first { it.size == s }
    }

    /** H(2m) from H(m), or null if the coordinates would exceed [COORD_LIMIT]. */
    private fun double(h: List<Pt>): List<Pt>? {
        val even = h.map { Pt(2 * it.x, it.y) }
        val maxY = h.maxOf { it.y }
        var d = 1L
        while (maxY + d <= COORD_LIMIT) {
            val odd = h.map { Pt(2 * it.x + 1, it.y + d) }
            if (allSides(even, odd, 1) && allSides(odd, even, -1)) {
                // x order: 0, 1, 2, ...
                return List(2 * h.size) { if (it % 2 == 0) even[it / 2] else odd[it / 2] }
            }
            d *= 2
        }
        return null
    }

    /** Every point of [q] lies on side [sign] (+1 above, -1 below) of every line through two points of [line]. */
    private fun allSides(line: List<Pt>, q: List<Pt>, sign: Int): Boolean {
        // the points of each part are sorted by x, so a = line[i], b = line[j], i < j, goes left to right
        for (i in line.indices) for (j in i + 1 until line.size) for (c in q)
            if (orient(line[i], line[j], c) != sign) return false
        return true
    }
}

/** Points in convex position on the moment curve (x, x^2), x = 0..n-1. */
object ConvexFamily : PointSetFamily {
    override val id = "convex"
    override val name = "Convex position"
    override val description = "n points on the moment curve (x, x²), x = 0..n-1."
    override val references = listOf(
        Reference("P. Erdős, G. Szekeres, A combinatorial problem in geometry, Compositio Math. 2 (1935) 463-470", null),
    )

    /** Largest n with (n-1)^2 <= [COORD_LIMIT]. */
    override val maxN: Int = run {
        var x = 0L
        while ((x + 1) * (x + 1) <= COORD_LIMIT) x++
        (x + 1).toInt()
    }

    override fun size(n: Int): Int {
        require(n >= 1) { "n must be at least 1" }
        return n
    }

    override fun generate(n: Int): List<Pt> {
        require(size(n) <= maxN) { "Convex position (moment curve) is available up to n = $maxN (coordinates within ±$COORD_LIMIT); $n requested" }
        return List(n) { Pt(it.toLong(), it.toLong() * it) }
    }
}
