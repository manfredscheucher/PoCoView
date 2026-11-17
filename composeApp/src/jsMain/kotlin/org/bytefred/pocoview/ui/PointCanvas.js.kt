package org.bytefred.pocoview.ui

import kotlinx.browser.window

actual val ctrlClickIsRightClick: Boolean = "Mac" in window.navigator.platform
