package org.bytefred.pocoview.ui

import kotlinx.browser.localStorage

// browser storage can be unavailable (private mode, blocked site data): then nothing is remembered
actual fun loadPref(key: String): String? = try { localStorage.getItem("pocoview.$key") } catch (e: Throwable) { null }

actual fun savePref(key: String, value: String) {
    try { localStorage.setItem("pocoview.$key", value) } catch (e: Throwable) { }
}

actual fun removePref(key: String) {
    try { localStorage.removeItem("pocoview.$key") } catch (e: Throwable) { }
}
