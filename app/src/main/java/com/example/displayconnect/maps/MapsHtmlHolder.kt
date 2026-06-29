package com.example.displayconnect.maps

import java.util.concurrent.atomic.AtomicReference

/**
 * Latest HTML fragment extracted from the Maps WebView (thread-safe).
 */
object MapsHtmlHolder {

    private val html = AtomicReference<String?>(null)

    fun update(fragment: String?) {
        html.set(fragment?.take(MAX_HTML_LENGTH))
    }

    fun get(): String? = html.get()

    fun clear() {
        html.set(null)
    }

    private const val MAX_HTML_LENGTH = 480
}
