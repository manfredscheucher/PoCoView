package org.bytefred.pocoview.ui

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.graphics.Color
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.coroutines.yield
import org.bytefred.pocoview.core.ALL_PROPERTIES
import org.bytefred.pocoview.core.centeredPoints
import org.bytefred.pocoview.core.isGeneralPosition
import org.bytefred.pocoview.core.COORD_LIMIT
import org.bytefred.pocoview.core.CellHopMinimizer
import org.bytefred.pocoview.core.CellHopParams
import org.bytefred.pocoview.core.FormatException
import org.bytefred.pocoview.core.PROPERTY_SEARCH_FLOWS
import org.bytefred.pocoview.core.PointFormat
import org.bytefred.pocoview.core.PropertyDef
import org.bytefred.pocoview.core.PropertyResult
import org.bytefred.pocoview.core.PropertySearchFlow
import org.bytefred.pocoview.core.Pt
import org.bytefred.pocoview.core.SearchParam
import org.bytefred.pocoview.core.Ticker
import org.bytefred.pocoview.core.beautify2Coords
import org.bytefred.pocoview.core.beautify2
import org.bytefred.pocoview.core.beautifyCoords
import org.bytefred.pocoview.core.beautifyPointSet
import org.bytefred.pocoview.core.bitsOf
import org.bytefred.pocoview.core.canAdd
import org.bytefred.pocoview.core.collinearPoints
import org.bytefred.pocoview.core.extent
import org.bytefred.pocoview.core.importPointSets
import org.bytefred.pocoview.core.inRange
import org.bytefred.pocoview.core.minimizeCoords
import org.bytefred.pocoview.core.minimizeIntegerCoordinates
import org.bytefred.pocoview.core.minimizePatternSearch
import org.bytefred.pocoview.core.orderTypeSignature
import org.bytefred.pocoview.core.randomPointSet
import org.bytefred.pocoview.core.readAsc
import org.bytefred.pocoview.core.readPointSets
import org.bytefred.pocoview.core.sameOrderTypeAfterMove
import org.bytefred.pocoview.core.saveFileName
import org.bytefred.pocoview.core.writePointSets
import kotlin.random.Random

/** Implementations of "Minimize coordinates" (M); all keep the labeled order type. */
enum class MinimizeMethod(val label: String) {
    MINIMIZE_COORDS("minimize_coords (pyotlib2)"),
    MINIMIZE_INTEGER_COORDINATES("minimizeIntegerCoordinates (old pyotlib): even + halve, blow up"),
    PATTERN_SEARCH("pattern search (old pyotlib): perturb towards the centre"),
}

/** Implementations of "Beautify" (B); all keep the labeled order type. */
enum class BeautifyMethod(val label: String) {
    BEAUTIFY_COORDS("beautify 1: beautify_coords (pyotlib2)"),
    BEAUTIFY_POINT_SET("beautify 1: beautifyPointSet (old pyotlib)"),
    BEAUTIFY2_COORDS("beautify 2: beautify2_coords (pyotlib2)"),
    BEAUTIFY2("beautify 2: beautify2 (old pyotlib)"),
}

/**
 * Parameters of the minimize / beautify implementations; defaults as in the pyotlib2 CLI, and
 * for the old pyotlib variants as in its scripts (MinimizeCoords, MinimizePatternSearch,
 * Beautify2Script: e.g. beautify2 with minimize 1, iter1 10, goodbits 30).
 */
data class CoordSettings(
    val trials: Int = 10,
    val randomize: Int = 10,
    val sheering: Boolean = false,
    val maxIter: Int = 100,
    val iter1: Int = 5,
    val iter2: Int = 2000,
    val minimizeAfter: Boolean = true,
    // old pyotlib
    val oldMaxTrials: Int = 1,
    val minimizeDiameter: Boolean = false,
    val symmetric: Int = 0,
    val psMaxTrials: Int = 2000,
    val psScale: Int = 1,
    val psBlowup: Int = 0,
    val oldIter1: Int = 10,
    val oldGoodBits: Int = 30,
)

enum class Tool { MOVE, ADD, DELETE }

/** The panels next to the canvas; each one is a column that can be shown or hidden. */
enum class Widget(val label: String) {
    PROPERTIES("Properties"), POINTS("Points"), OPTIMIZE("Optimize"), DATA("Data"), DISPLAY("Display"),
}

/** How the lines through the selected point are drawn. */
enum class IncidentLines(val label: String) { HIDE("hide"), NORMAL("normal"), HIGHLIGHT("highlight") }

enum class LayoutMode { AUTO, PORTRAIT, LANDSCAPE }

sealed interface PropState {
    data object Computing : PropState
    data class Done(val result: PropertyResult) : PropState
    data class Skipped(val reason: String) : PropState
    data class Failed(val message: String) : PropState
}

/** Per-property GUI switches: [count] = shown in the panel (and evaluated), [draw] = visualize it (if highlight is on). */
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
    /** Draw the structures of the properties whose visualize toggle is on; off = numbers only. */
    var highlight by mutableStateOf(false)
        private set

    /** Count and draw only the structures containing the selected point. */
    var incidentOnly by mutableStateOf(false)
        private set
    var tool by mutableStateOf(Tool.MOVE)
    var layoutMode by mutableStateOf(LayoutMode.AUTO)

    /** Implementations and parameters chosen in the Optimize widget (also used by the M / B keys). */
    var minimizeMethod by mutableStateOf(MinimizeMethod.MINIMIZE_COORDS)
    var beautifyMethod by mutableStateOf(BeautifyMethod.BEAUTIFY_COORDS)
    var coordSettings by mutableStateOf(CoordSettings())

    /** qtvis minimizer parameters (Optimize widget; also used by the Enter key). */
    var cellHopParams by mutableStateOf(CellHopParams())

    /** Property search flow (old pyotlib PointWalk etc.) and its parameter values, keyed "flowId.param". */
    var searchFlowId by mutableStateOf(PROPERTY_SEARCH_FLOWS.first().id)
    val searchValues = mutableStateMapOf<String, Any>()

    fun searchValue(flow: PropertySearchFlow, param: SearchParam): Any = searchValues["${flow.id}.${param.name}"] ?: param.default

    /** Random point set settings (Points widget). */
    var randomN by mutableStateOf(12)
    var randomGrid by mutableStateOf(1000L)

    /** Unapplied edit of the coordinate list (Data widget); null = shows the current points. */
    var coordinatesDraft by mutableStateOf<String?>(null)

    /** Number of focused text fields: keyboard shortcuts are off while one has focus. */
    var textFieldsFocused by mutableIntStateOf(0)

    /** Property chosen in the Optimize widget (walk, qtvis minimizer, Enter key). */
    var optimizeProperty by mutableStateOf("hole5")

    /** View settings: point radius (px), selected point size relative to normal (%), line width (px). */
    var pointRadius by mutableStateOf(6f)
    var selectedScalePercent by mutableStateOf(100)
    var lineWidth by mutableStateOf(1f)

    /** Line arrangement colour and opacity (Display widget). */
    var lineColor by mutableStateOf(DEFAULT_LINE_COLOR)
    var lineAlpha by mutableStateOf(DEFAULT_LINE_ALPHA)

    /** Highlight colour per property id (chosen in "Select properties…"); default: [defaultPropertyColor]. */
    val propertyColors = mutableStateMapOf<String, Color>()

    fun colorOf(id: String): Color = propertyColors[id] ?: defaultPropertyColor(ALL_PROPERTIES.indexOfFirst { it.id == id })

    /** Selected point: fill and marker colour. */
    var selectedColor by mutableStateOf(DEFAULT_SELECTED_COLOR)

    /** Lines through the selected point: hidden (qtvis), like the others, or highlighted. */
    var incidentLines by mutableStateOf(IncidentLines.HIDE)
    var incidentLineColor by mutableStateOf(DEFAULT_INCIDENT_LINE_COLOR)
    var incidentLineWidth by mutableStateOf(2f)

    var showLines by mutableStateOf(true)
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

    /** Title of the operation dialog. */
    var busyTitle by mutableStateOf("")
        private set

    /** Progress line of the running operation (shown in the operation dialog). */
    var progress by mutableStateOf<String?>(null)

    /**
     * Loop: operations do not stop at the first improvement (or after one run) but continue
     * until aborted. Read live, so it can be switched while an operation runs.
     */
    var loop by mutableStateOf(false)

    /** Open widget columns, in opening order. */
    val openWidgets = mutableStateListOf(Widget.PROPERTIES, Widget.OPTIMIZE)

    /** At most this many widget columns are open; opening one more closes the least recently clicked. */
    var maxColumns by mutableIntStateOf(2)
        private set

    private val lastClicked = mutableMapOf<Widget, Long>()
    private var clickCounter = 0L

    init {
        for (w in openWidgets) touchWidget(w)
    }

    /** A click into a widget column (or opening it): it becomes the most recently used one. */
    fun touchWidget(w: Widget) {
        lastClicked[w] = ++clickCounter
    }

    fun toggleWidget(w: Widget) {
        if (w in openWidgets) {
            openWidgets.remove(w)
        } else {
            openWidgets.add(w)
            touchWidget(w)
            closeExtraWidgets()
        }
    }

    fun changeMaxColumns(k: Int) {
        maxColumns = k.coerceIn(1, Widget.entries.size)
        closeExtraWidgets()
    }

    private fun closeExtraWidgets() {
        while (openWidgets.size > maxColumns) openWidgets.remove(openWidgets.minBy { lastClicked[it] ?: 0L })
    }

    /** Display widget "Reset to defaults": every setting of the widget. */
    fun resetDisplay() {
        showLines = true; showLabels = true; layoutMode = LayoutMode.AUTO
        changeMaxColumns(2)
        pointRadius = 6f; selectedScalePercent = 100; lineWidth = 1f
        lineColor = DEFAULT_LINE_COLOR; lineAlpha = DEFAULT_LINE_ALPHA
        selectedColor = DEFAULT_SELECTED_COLOR
        incidentLines = IncidentLines.HIDE; incidentLineColor = DEFAULT_INCIDENT_LINE_COLOR; incidentLineWidth = 2f
    }

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
        setPoints(START_POINTS, recordUndo = false)
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
        message = null
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
        if (incidentOnly) recompute()
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
        if (fixOrderType && !sameOrderType) {
            if (collinear.isEmpty()) return false
            message = "Lock order type has no effect while points are collinear"
        }
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
            coordinatesDraft = null
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
            load(name, readPointSets(name, bytes))
        } catch (e: Exception) {
            message = "Cannot open $name: ${e.message}"
        }
    }

    /** Import dialog: pasted content in the chosen format. Returns an error message, or null on success. */
    fun import(format: PointFormat, text: String, n: Int?): String? = try {
        load("imported.${format.extension}", importPointSets(format, text, n))
        null
    } catch (e: Exception) {
        e.message ?: "Cannot parse the content"
    }

    private fun load(name: String, loaded: List<List<Pt>>) {
        cancelOperation()
        coordinatesDraft = null
        sets = loaded
        setIndex = 0
        fileName = name.substringBeforeLast('.')
        selected = null
        setPoints(loaded[0], recordUndo = false)
        clearHistory()
        requestFit()
        message = "Loaded $name: ${loaded.size} set(s), n=${loaded[0].size}"
    }

    /** Shows another set of the file. Undo history is per set, so it is cleared. */
    fun showSet(index: Int) {
        if (index !in sets.indices || index == setIndex) return
        cancelOperation()
        storeCurrentSet()
        coordinatesDraft = null
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

    /** Show (and compute) a property in the Properties widget or not. */
    fun setShown(id: String, shown: Boolean) {
        val t = toggles[id] ?: return
        if (t.count != shown) setToggle(id, t.copy(count = shown))
    }

    fun changeHighlight(on: Boolean) {
        highlight = on
        recompute()
    }

    fun changeIncidentOnly(only: Boolean) {
        incidentOnly = only
        recompute()
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
        val sel = if (incidentOnly) selected else null
        val generalPosition = collinear.isEmpty()
        val todo = ArrayList<Triple<PropertyDef, String, Boolean>>()
        // update every property right away (cached / skipped / computing), so the panel
        // never shows results of a different order type
        for (prop in ALL_PROPERTIES) {
            val t = toggles[prop.id] ?: continue
            if (!t.count) { results.remove(prop.id); continue }
            val draw = highlight && t.draw && prop.hasStructures
            val key = "$sig|${prop.id}|$sel|$draw"
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
                    val r = evaluate(prop, p, sel, draw)
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

    private suspend fun evaluate(prop: PropertyDef, p: List<Pt>, sel: Int?, draw: Boolean) =
        withContext(Dispatchers.Default) { prop.evaluate(p, sel, draw, Ticker()) }

    // ---- operations ------------------------------------------------------------------

    fun cancelOperation() {
        opJob?.cancel()
        opJob = null
        busy = null
        progress = null
    }

    /** The Abort button of the operation dialog. */
    fun abortOperation() {
        val label = busy
        cancelOperation()
        if (label != null) message = "${label.removeSuffix("…")}: aborted"
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
    private fun runOperation(
        label: String,
        title: String = "Running",
        block: suspend (start: List<Pt>, update: (List<Pt>) -> Unit) -> Unit,
    ) {
        if (!requireGeneralPosition()) return
        cancelOperation()
        val start = points
        var changed = false
        busy = label
        busyTitle = title
        opJob = scope.launch {
            try {
                block(start) { p ->
                    if (!changed) { pushUndo(); changed = true }
                    points = p
                    onPointsChanged()
                }
                // e.g. a failed search that restored the start: no undo step
                if (changed && points == start) { undoStack.removeLastOrNull(); updateUndoFlags() }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                message = "$label failed: ${e.message ?: e}"
            } finally {
                if (opJob == coroutineContext[Job]) { busy = null; progress = null }
            }
        }
    }

    /** "Minimize coordinates" with the chosen implementation (M). */
    fun minimize() {
        val m = minimizeMethod
        val cfg = coordSettings
        runCoordOptimizer("Minimize, ${m.label}") { start ->
            when (m) {
                MinimizeMethod.MINIMIZE_COORDS ->
                    minimizeCoords(start, trials = cfg.trials, randomize = cfg.randomize, sheering = cfg.sheering)
                MinimizeMethod.MINIMIZE_INTEGER_COORDINATES -> minimizeIntegerCoordinates(
                    start, maxTrials = cfg.oldMaxTrials, randomize = cfg.randomize, sheering = cfg.sheering,
                    minimizeDiameter = cfg.minimizeDiameter, symmetric = cfg.symmetric,
                )
                MinimizeMethod.PATTERN_SEARCH ->
                    minimizePatternSearch(start, maxTrials = cfg.psMaxTrials, scale = cfg.psScale.toLong(), blowup = cfg.psBlowup).points
            }
        }
    }

    /** "Beautify" with the chosen implementation (B). */
    fun beautify() {
        val m = beautifyMethod
        val cfg = coordSettings
        runCoordOptimizer("Beautify, ${m.label}") { start ->
            when (m) {
                BeautifyMethod.BEAUTIFY_COORDS -> beautifyCoords(start, maxIter = cfg.maxIter)
                BeautifyMethod.BEAUTIFY_POINT_SET -> beautifyPointSet(start, maxIter = cfg.maxIter)
                BeautifyMethod.BEAUTIFY2_COORDS ->
                    beautify2Coords(start, iter1 = cfg.iter1, iter2 = cfg.iter2, minimize = cfg.minimizeAfter)
                BeautifyMethod.BEAUTIFY2 -> beautify2(
                    start, iter1 = cfg.oldIter1, iter2 = cfg.iter2, goodBits = cfg.oldGoodBits, minimize = cfg.minimizeAfter,
                )
            }
        }
    }

    /** Runs a coordinate optimizer once, or with [loop] again and again on its result until aborted. */
    private fun runCoordOptimizer(label: String, run: suspend (List<Pt>) -> List<Pt>) =
        runOperation("$label…", if (label.startsWith("Minimize")) "Minimizing coordinates" else "Beautifying") { start, update ->
            val bitsBefore = bitsOf(start)
            var cur = start
            var round = 0
            do {
                round++
                progress = "run $round, ${bitsOf(cur)} bits (start $bitsBefore)"
                val r = withContext(Dispatchers.Default) { run(cur) }
                if (r != cur) { cur = r; update(r); requestFit() }
                yield()
            } while (loop)
            message = if (cur == start) "$label: no change" else "$label: bits $bitsBefore → ${bitsOf(cur)}"
        }

    /**
     * Minimize w.r.t. property (old pyotlib minimizeIntegerCoordinates with ensure_property,
     * parameters of that minimize method): the coordinates shrink while the order type may
     * change, as long as the chosen property does not get worse. A result that is worse, has
     * collinear points or does not fit is undone. With [loop] it runs again and again.
     */
    fun minimizeWrtProperty() {
        val prop = ALL_PROPERTIES.firstOrNull { it.id == optimizeProperty } ?: return
        val cfg = coordSettings
        val label = "Minimize w.r.t. ${prop.label}"
        runOperation("$label…", "Minimizing coordinates") { start, update ->
            // the minimizer's ticker, so a long run of rejected moves still yields (web) and can be aborted
            val count: suspend (List<Pt>, Ticker) -> Long = { p, t -> prop.evaluate(p, null, false, t).count }
            val bitsBefore = bitsOf(start)
            val countBefore = evaluate(prop, start, null, false).count
            var cur = start
            var curCount = countBefore
            var round = 0
            var undone = 0
            var rejected = 0
            do {
                round++
                progress = "run $round, ${bitsOf(cur)} bits, ${prop.label} = $curCount (start $bitsBefore bits, $countBefore)"
                val from = cur
                val r = withContext(Dispatchers.Default) {
                    minimizeIntegerCoordinates(
                        from, maxTrials = cfg.oldMaxTrials, randomize = cfg.randomize, sheering = cfg.sheering,
                        minimizeDiameter = cfg.minimizeDiameter, symmetric = cfg.symmetric, ensureProperty = count,
                    )
                }
                // the result comes back normalized: centred it fits into ±2^29 whenever possible
                val rc = centeredPoints(r)?.takeIf { isGeneralPosition(it) }
                val rCount = rc?.let { evaluate(prop, it, null, false).count }
                if (rc == null || rCount == null) {
                    rejected++ // collinear or too large
                } else if (rCount > curCount) {
                    undone++ // worse -> undo
                } else if (rc != cur) {
                    cur = rc; curCount = rCount; update(rc); requestFit()
                }
                yield()
            } while (loop)
            message = when {
                cur != start -> "$label: bits $bitsBefore → ${bitsOf(cur)}, ${prop.label} $countBefore → $curCount"
                undone > 0 -> "$label: result was worse, undone"
                rejected > 0 -> "$label: result had collinear points or was too large, undone"
                else -> "$label: no change"
            }
        }
    }

    /**
     * Runs a property search flow (ports of the old pyotlib scripts, core/PropertySearch.kt)
     * for the chosen property. Configurations with the same value are taken over, worse ones
     * not; the first strictly better one ends the search unless [loop] is on.
     */
    fun searchProperty() {
        val prop = ALL_PROPERTIES.firstOrNull { it.id == optimizeProperty } ?: return
        val flow = PROPERTY_SEARCH_FLOWS.firstOrNull { it.id == searchFlowId } ?: return
        if (points.size > prop.maxN) { message = "${prop.label}: n > ${prop.maxN}"; return }
        if (points.size < 3) { message = "Need at least 3 points"; return }
        val values = flow.params.associate { it.name to searchValue(flow, it) }
        runOperation("${flow.label}, ${prop.label}…", SEARCH_TITLE) { start, update ->
            val first = evaluate(prop, start, null, false).count
            var current = first
            var reports = 0
            coroutineScope {
                // accepted configurations are applied in order on the UI side, as children of this operation
                val channel = Channel<Pair<List<Pt>, Long>>(Channel.UNLIMITED)
                launch {
                    for ((p, v) in channel) {
                        update(p)
                        progress = "${prop.label} = $v (start $first), $reports configurations examined"
                    }
                }
                withContext(Dispatchers.Default) {
                    flow.run(start, { prop.evaluate(it, null, false, Ticker()).count }, values, Random.Default) { p, v ->
                        reports++
                        if (v > current) return@run true
                        val improved = v < current
                        current = v
                        channel.trySend(p to v)
                        !(improved && !loop)
                    }
                }
                channel.close()
            }
            message = if (current < first) "${prop.label}: $first → $current" else "${prop.label}: no better configuration found ($first)"
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
        runOperation("qtvis minimizer (${prop.label})…", SEARCH_TITLE) { start, update ->
            val before = evaluate(prop, start, null, false).count
            var cur = start
            var round = 0
            do {
                round++
                progress = "round $round, ${prop.label} = ${evaluate(prop, cur, null, false).count} (start $before)"
                var result: List<Pt>? = null
                val from = cur
                coroutineScope {
                    // the search runs off the UI thread; accepted configurations are applied in
                    // order by one consumer, a child of this operation (cancelling drops them too)
                    val channel = Channel<List<Pt>>(Channel.UNLIMITED)
                    launch { for (p in channel) update(p) }
                    result = withContext(Dispatchers.Default) {
                        CellHopMinimizer(
                            count = { prop.evaluate(it, null, false, Ticker()).count },
                            params = params,
                            onProgress = { channel.trySend(it) },
                        ).minimize(from)
                    }
                    channel.close()
                }
                result?.let { cur = it }
            } while (loop)
            val after = evaluate(prop, cur, null, false).count
            message = if (after >= before) "${prop.label}: no configuration below $before found"
            else "${prop.label}: $before → $after"
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
        const val SEARCH_TITLE = "Searching for a better configuration"
        val DEFAULT_LINE_COLOR = Color(0xFF1C1B1F)
        const val DEFAULT_LINE_ALPHA = 0.18f
        /** The set shown at start: a 13-point set found by Manfred. */
        val START_POINTS = listOf(
            Pt(160, 7359), Pt(1768, 6530), Pt(2592, 6679), Pt(4239, 6383), Pt(3955, 5593),
            Pt(2960, 5759), Pt(2338, 4960), Pt(2880, 4320), Pt(2960, 2520), Pt(5759, 7359),
            Pt(3076, 5497), Pt(2684, 5783), Pt(3113, 5976),
        )
        val DEFAULT_SELECTED_COLOR = Color(0xFFFF9800)
        val DEFAULT_INCIDENT_LINE_COLOR = Color(0xFFE53935)

        private fun fnv64(s: String): String {
            var h = -0x340d631b7bdddcdbL // FNV-1a offset basis
            for (c in s) { h = h xor c.code.toLong(); h *= 0x100000001b3L }
            return h.toULong().toString(16).padStart(16, '0')
        }
    }
}
