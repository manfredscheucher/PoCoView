package org.bytefred.pocoview.ui

import kotlinx.browser.window

// navigator.platform is deprecated; the user agent names the Mac ("Macintosh")
actual val ctrlClickIsRightClick: Boolean = "Mac" in window.navigator.userAgent
