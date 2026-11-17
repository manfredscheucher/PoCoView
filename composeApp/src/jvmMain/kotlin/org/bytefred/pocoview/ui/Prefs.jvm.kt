package org.bytefred.pocoview.ui

import java.util.prefs.Preferences

// the JVM tests run with -Dpocoview.prefs=memory: a fresh in-memory store, so they
// never read or change the real desktop preferences (as in ktoy)
private val inMemory: MutableMap<String, String>? =
    if (System.getProperty("pocoview.prefs") == "memory") mutableMapOf() else null

private val prefs: Preferences? =
    if (inMemory != null) null
    else try { Preferences.userRoot().node("org/bytefred/pocoview") } catch (e: Exception) { null }

actual fun loadPref(key: String): String? =
    inMemory?.get(key) ?: try { prefs?.get(key, null) } catch (e: Exception) { null }

/** Tests only: empty the in-memory store (no-op on the real preferences). */
internal fun clearInMemoryPrefs() { inMemory?.clear() }

actual fun savePref(key: String, value: String) {
    if (inMemory != null) { inMemory[key] = value; return }
    try { prefs?.put(key, value); prefs?.flush() } catch (e: Exception) { }
}

actual fun removePref(key: String) {
    if (inMemory != null) { inMemory.remove(key); return }
    try { prefs?.remove(key); prefs?.flush() } catch (e: Exception) { }
}
