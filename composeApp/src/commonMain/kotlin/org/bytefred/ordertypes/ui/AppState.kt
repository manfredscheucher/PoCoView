package org.bytefred.ordertypes.ui

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.coroutines.yield
import org.bytefred.ordertypes.core.ALL_PROPERTIES
import org.bytefred.ordertypes.core.COORD_LIMIT
import org.bytefred.ordertypes.core.CellHopMinimizer
import org.bytefred.ordertypes.core.CellHopParams
import org.bytefred.ordertypes.core.FormatException
import org.bytefred.ordertypes.core.PointFormat
import org.bytefred.ordertypes.core.PropertyDef
import org.bytefred.ordertypes.core.PropertyResult
import org.bytefred.ordertypes.core.Pt
import org.bytefred.ordertypes.core.Ticker
import org.bytefred.ordertypes.core.beautifyStep
import org.bytefred.ordertypes.core.canAdd
import org.bytefred.ordertypes.core.collinearPoints
import org.bytefred.ordertypes.core.extent
import org.bytefred.ordertypes.core.inRange
import org.bytefred.ordertypes.core.minimizeStep
import org.bytefred.ordertypes.core.orderTypeSignature
import org.bytefred.ordertypes.core.randomMove
import org.bytefred.ordertypes.core.randomPointSet
import org.bytefred.ordertypes.core.readAsc
import org.bytefred.ordertypes.core.readPointSets
import org.bytefred.ordertypes.core.sameOrderTypeAfterMove
import org.bytefred.ordertypes.core.saveFileName
import org.bytefred.ordertypes.core.writePointSets

/** count = numbers only; color = draw structures; incident = only structures containing the selected point. */
enum class Mode { COUNT, COLOR, INCIDENT }

enum class Tool { MOVE, ADD, DELETE }

enum class LayoutMode { AUTO, PORTRAIT, LANDSCAPE }

sealed interface PropState {
    data object Computing : PropState
    data class Done(val result: PropertyResult) : PropState
    data class Skipped(val reason: String) : PropState
    data class Failed(val message: String) : PropState
}

/** Per-property GUI switches: [count] = evaluate it, [draw] = draw its structures (color/incident mode). */
data class PropToggle(val count: Boolean, val draw: Boolean)

/**
 * All GUI state and actions. Plain Compose state + a coroutine scope; properties are
 * evaluated in a cancellable background job and cached on the order type signature
 * (all offered properties are order type invariants). Any edit cancels a running
 * operation (minimize / walk / beautify), so the operation never overwrites an edit.
 */
class AppState(private val scope: CoroutineScope = CoroutineScope(SupervisorJob() + Dispatchers.Main)) {
    var points by mutableStateOf<List<Pt>>(emptyList())
        private set
    var selected by mutableStateOf<Int?>(null)
        private set
    var mode by mutableStateOf(Mode.COLOR)
        private set

    /**
     * Incident mode: true = only the structures containing the selected point are drawn,
     * false = all are drawn and those are highlighted (qtvis).
     */
    var incidentOnly by mutableStateOf(true)
        private set
    var tool by mutableStateOf(Tool.MOVE)
    var layoutMode by mutableStateOf(LayoutMode.AUTO)

    /** Property chosen in the Optimize tab (walk, qtvis minimizer, Enter key). */
    var optimizeProperty by mutableStateOf("hole5")

    var showLines by mutableStateOf(true)
    var hideSelectedLines by mutableStateOf(true)
    var showLabels by mutableStateOf(true)
    var fixOrderType by mutableStateOf(false)

    /** All sets of the loaded file (multi order type files) and the shown one. */
    var sets by mutableStateOf<List<List<Pt>>>(emptyList())
        private set
    var setIndex by mutableIntStateOf(0)
        private set
    var fileName by mutableStateOf("points")
        private set
    var message by mutableStateOf<String?>(null)
    var busy by mutableStateOf<String?>(null)
        private set

    /** Incremented to ask the canvas to fit the view to the points. */
    var fitRequest by mutableIntStateOf(0)
        private set

    val toggles = mutableStateMapOf<String, PropToggle>().apply {
        for (p in ALL_PROPERTIES) put(p.id, PropToggle(count = p.id in DEFAULT_COUNTED, draw = p.id in DEFAULT_DRAWN))
    }
    val results = mutableStateMapOf<String, PropState>()

    var collinear by mutableStateOf<Set<Int>>(emptySet())
        private set

    /** Short hash of the labeled order type (info panel). */
    var orderTypeHash by mutableStateOf("")
        private set

    private val undoStack = ArrayDeque<List<Pt>>()
    private val redoStack = ArrayDeque<List<Pt>>()
    var canUndo by mutableStateOf(false)
        private set
    var canRedo by mutableStateOf(false)
        private set

    private val cache = HashMap<String, PropertyResult>()
    private var signature = ""
    private var computeJob: Job? = null
    private var opJob: Job? = null
    private var dragUndoPending = false

    init {
        setPoints(randomPointSet(10, 1000), recordUndo = false)
        sets = listOf(points)
        requestFit()
    }

    // ---- editing ---------------------------------------------------------------------

    private fun setPoints(p: List<Pt>, recordUndo: Boolean = true) {
        if (p == points) return
        if (recordUndo) pushUndo()
        points = p
        if (selected != null && selected!! >= p.size) selected = null
        onPointsChanged()
    }

    private fun pushUndo() {
        undoStack.addLast(points)
        if (undoStack.size > 500) undoStack.removeFirst()
        redoStack.clear()
        updateUndoFlags()
    }

    private fun clearHistory() {
        undoStack.clear()
        redoStack.clear()
        updateUndoFlags()
    }

    private fun updateUndoFlags() {
        canUndo = undoStack.isNotEmpty()
        canRedo = redoStack.isNotEmpty()
    }

    fun undo() {
        cancelOperation()
        val prev = undoStack.removeLastOrNull() ?: return
        redoStack.addLast(points)
        points = prev
        if (selected != null && selected!! >= prev.size) selected = null
        updateUndoFlags()
        onPointsChanged()
    }

    fun redo() {
        cancelOperation()
        val next = redoStack.removeLastOrNull() ?: return
        undoStack.addLast(points)
        points = next
        if (selected != null && selected!! >= next.size) selected = null
        updateUndoFlags()
        onPointsChanged()
    }

    fun select(i: Int?) {
        if (selected == i) return
        selected = i
        if (mode == Mode.INCIDENT) recompute()
    }

    /** Called when a drag starts; the first accepted move records one undo step for the whole drag. */
    fun beginDrag(i: Int) {
        cancelOperation()
        dragUndoPending = true
        select(i)
    }

    /** Moves point [i] to [q] during a drag. Returns false if the move was rejected. */
    fun dragTo(i: Int, q: Pt): Boolean {
        if (i !in points.indices || !q.inRange() || q == points[i]) return false
        if (points.any { it.x == q.x && it.y == q.y }) return false
        // O(n^2): if no triple through i changes sign, order type and collinearities stay the same
        val sameOrderType = sameOrderTypeAfterMove(points, i, q)
        if (fixOrderType && collinear.isEmpty() && !sameOrderType) return false
        if (dragUndoPending) { pushUndo(); dragUndoPending = false }
        points = points.toMutableList().also { it[i] = q.copy(color = points[i].color) }
        if (!sameOrderType) onPointsChanged()
        return true
    }

    fun addPoint(q: Pt) {
        if (!q.inRange()) return
        if (!canAdd(points, q)) {
            message = "Point (${q.x}, ${q.y}) would be collinear with two others or a duplicate"
            return
        }
        cancelOperation()
        setPoints(points + q)
        select(points.size - 1)
    }

    fun deletePoint(i: Int) {
        if (i !in points.indices) return
        cancelOperation()
        selected = null
        setPoints(points.filterIndexed { j, _ -> j != i })
    }

    fun deleteSelected() = selected?.let(::deletePoint)

    fun randomSet(n: Int, size: Long) {
        if (n !in 1..200 || size !in 2..COORD_LIMIT) {
            message = "Random set: n must be 1..200, grid size 2..$COORD_LIMIT"
            return
        }
        try {
            val p = randomPointSet(n, size)
            cancelOperation()
            selected = null
            setPoints(p)
            requestFit()
        } catch (e: IllegalStateException) {
            message = e.message
        }
    }

    /** Replaces all points by the parsed text (`x y` per line, optional count header). */
    fun applyCoordinatesText(text: String) {
        try {
            val p = readAsc(text).singleOrNull() ?: throw FormatException("Expected exactly one point set")
            if (p.any { !it.inRange() }) throw FormatException("Coordinates exceed ±$COORD_LIMIT")
            cancelOperation()
            selected = null
            setPoints(p)
            requestFit()
        } catch (e: Exception) {
            message = e.message ?: "Could not parse the coordinates"
        }
    }

    fun requestFit() {
        fitRequest++
    }

    // ---- files -----------------------------------------------------------------------

    fun open(name: String, bytes: ByteArray) {
        try {
            val loaded = readPointSets(name, bytes)
            cancelOperation()
            sets = loaded
            setIndex = 0
            fileName = name.substringBeforeLast('.')
            selected = null
            setPoints(loaded[0], recordUndo = false)
            clearHistory()
            requestFit()
            message = "Loaded $name: ${loaded.size} set(s), n=${loaded[0].size}"
        } catch (e: Exception) {
            message = "Cannot open $name: ${e.message}"
        }
    }

    /** Shows another set of the file. Undo history is per set, so it is cleared. */
    fun showSet(index: Int) {
        if (index !in sets.indices || index == setIndex) return
        cancelOperation()
        storeCurrentSet()
        setIndex = index
        selected = null
        setPoints(sets[index], recordUndo = false)
        clearHistory()
        requestFit()
    }

    private fun storeCurrentSet() {
        if (setIndex in sets.indices) sets = sets.toMutableList().also { it[setIndex] = points }
    }

    /** Suggested file name for saving; for binary formats it encodes n so the file can be read back. */
    fun saveName(format: PointFormat): String = saveFileName(fileName, points.size, format)

    /** Encodes the current set (or all sets of the file) for saving; null + message on error. */
    fun encode(format: PointFormat, allSets: Boolean): ByteArray? {
        storeCurrentSet()
        val out = if (allSets) sets else listOf(points)
        if (format.isBinary && out.map { it.size }.distinct().size > 1) {
            message = "Binary formats need all sets to have the same n; save the sets one by one or as .asc"
            return null
        }
        return try {
            writePointSets(format, out)
        } catch (e: FormatException) {
            message = e.message
            null
        }
    }

    // ---- properties ------------------------------------------------------------------

    fun setToggle(id: String, t: PropToggle) {
        toggles[id] = t
        recompute()
    }

    fun changeMode(m: Mode) {
        mode = m
        recompute()
    }

    fun changeIncidentOnly(only: Boolean) {
        incidentOnly = only
        if (mode == Mode.INCIDENT) recompute()
    }

    private fun onPointsChanged() {
        collinear = collinearPoints(points)
        val sig = orderTypeSignature(points)
        if (sig != signature) {
            signature = sig
            orderTypeHash = fnv64(sig)
            recompute()
        }
    }

    fun recompute() {
        computeJob?.cancel()
        val p = points
        val sig = signature
        val sel = if (mode == Mode.INCIDENT) selected else null
        val only = incidentOnly
        val generalPosition = collinear.isEmpty()
        val todo = ArrayList<Triple<PropertyDef, String, Boolean>>()
        // update every property right away (cached / skipped / computing), so the panel
        // never shows results of a different order type
        for (prop in ALL_PROPERTIES) {
            val t = toggles[prop.id] ?: continue
            if (!t.count) { results.remove(prop.id); continue }
            val draw = mode != Mode.COUNT && t.draw && prop.hasStructures
            val key = "$sig|${prop.id}|$sel|$draw|$only"
            results[prop.id] = when {
                !generalPosition -> PropState.Skipped("not in general position")
                p.size > prop.maxN -> PropState.Skipped("n > ${prop.maxN}")
                else -> cache[key]?.let { PropState.Done(it) } ?: PropState.Computing.also { todo.add(Triple(prop, key, draw)) }
            }
        }
        if (todo.isEmpty()) return
        computeJob = scope.launch {
            for ((prop, key, draw) in todo) {
                results[prop.id] = try {
                    val r = evaluate(prop, p, sel, draw, only)
                    if (cache.size >= CACHE_SIZE) cache.clear()
                    cache[key] = r
                    PropState.Done(r)
                } catch (e: CancellationException) {
                    throw e
                } catch (e: Exception) {
                    PropState.Failed(e.message ?: e.toString())
                }
            }
        }
    }

    private suspend fun evaluate(prop: PropertyDef, p: List<Pt>, sel: Int?, draw: Boolean, onlyIncident: Boolean = true) =
        withContext(Dispatchers.Default) { prop.evaluate(p, sel, draw, Ticker(), onlyIncident) }

    // ---- operations ------------------------------------------------------------------

    fun cancelOperation() {
        opJob?.cancel()
        opJob = null
        busy = null
    }

    private fun requireGeneralPosition(): Boolean {
        if (collinear.isEmpty()) return true
        message = "The point set is not in general position (red points); fix that first"
        return false
    }

    /**
     * Runs a point-changing operation in the background. All its changes form one undo
     * step (only recorded if something changed); any edit cancels it.
     */
    private fun runOperation(label: String, block: suspend (start: List<Pt>, update: (List<Pt>) -> Unit) -> Unit) {
        if (!requireGeneralPosition()) return
        cancelOperation()
        val start = points
        var changed = false
        busy = label
        opJob = scope.launch {
            try {
                block(start) { p ->
                    if (!changed) { pushUndo(); changed = true }
                    points = p
                    onPointsChanged()
                }
            } finally {
                if (opJob == coroutineContext[Job]) busy = null
            }
        }
    }

    fun beautify() = runOperation("Beautifying…") { start, update ->
        val r = withContext(Dispatchers.Default) { beautifyStep(start) }
        if (r == start) message = "Beautify: no change" else update(r)
    }

    /** One minimization step, or (auto) repeat until no further halving is possible. */
    fun minimize(auto: Boolean) = runOperation("Minimizing…") { start, update ->
        val before = extent(start)
        var cur = start
        while (true) {
            cur = withContext(Dispatchers.Default) { minimizeStep(cur) } ?: break
            update(cur)
            requestFit()
            yield()
            if (!auto) break
        }
        message = if (cur == start) "No further minimization possible (extent $before)"
        else "Minimized: extent $before → ${extent(cur)}"
    }

    /**
     * Property walk (qtvis/pyotlib2 "property min/max"): random local moves of single
     * points, accepted if the chosen property does not get worse.
     */
    fun walk(propId: String, maximize: Boolean, steps: Int) {
        val prop = ALL_PROPERTIES.firstOrNull { it.id == propId } ?: return
        if (points.size > prop.maxN) { message = "${prop.label}: n > ${prop.maxN}"; return }
        runOperation("Walking (${prop.label})…") { start, update ->
            var cur = start
            var best = evaluate(prop, cur, null, false).count
            val first = best
            for (s in 0 until steps) {
                val radius = maxOf(1L, extent(cur) / 20)
                val cand = randomMove(cur, radius) ?: continue
                val v = evaluate(prop, cand, null, false).count
                if (if (maximize) v >= best else v <= best) {
                    cur = cand
                    best = v
                    update(cur)
                }
                busy = "Walking (${prop.label}): step ${s + 1}/$steps, value $best"
            }
            message = "${prop.label}: $first → $best"
        }
    }

    /**
     * The qtvis cell-hopping minimizer ("try to minimize", Enter in qtvis) for the chosen
     * property: stops at the first configuration with a smaller value, or restores the
     * start configuration when all trials failed.
     */
    fun cellHop(propId: String, params: CellHopParams) {
        val prop = ALL_PROPERTIES.firstOrNull { it.id == propId } ?: return
        if (points.size > prop.maxN) { message = "${prop.label}: n > ${prop.maxN}"; return }
        if (points.size < 3) { message = "Need at least 3 points"; return }
        runOperation("qtvis minimizer (${prop.label})…") { start, update ->
            val before = evaluate(prop, start, null, false).count
            val result = CellHopMinimizer(
                count = { evaluate(prop, it, null, false).count },
                params = params,
                onProgress = update,
            ).minimize(start)
            message = if (result == null) "${prop.label}: no configuration below $before found (start restored)"
            else "${prop.label}: $before → ${evaluate(prop, result, null, false).count}"
        }
    }

    /** Waits for the running property computation and operation (used by tests). */
    suspend fun awaitIdle() {
        opJob?.join()
        computeJob?.join()
    }

    companion object {
        val DEFAULT_COUNTED = setOf("hull", "onion", "crossings", "hole3", "hole4", "hole5", "hole6", "triangulations")
        val DEFAULT_DRAWN = setOf("hole5")
        private const val CACHE_SIZE = 64

        private fun fnv64(s: String): String {
            var h = -0x340d631b7bdddcdbL // FNV-1a offset basis
            for (c in s) { h = h xor c.code.toLong(); h *= 0x100000001b3L }
            return h.toULong().toString(16).padStart(16, '0')
        }
    }
}
