package org.bytefred.pocoview.ui

actual val ctrlClickIsRightClick: Boolean = System.getProperty("os.name").orEmpty().startsWith("Mac")
