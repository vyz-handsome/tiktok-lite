package com.example.tiktoklike

import android.net.Uri
import android.webkit.WebResourceRequest
import android.webkit.WebView
import android.webkit.WebViewClient

/** UA desktop: versi mobile TikTok hanya menampilkan halaman "buka di app" tanpa tombol like yang bisa diklik. */
const val DESKTOP_UA =
    "Mozilla/5.0 (X11; Linux x86_64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/130.0.0.0 Safari/537.36"

/** Ambil bentuk link bersih https://www.tiktok.com/@user/video/ID dari link apa pun yang memuatnya. */
fun cleanTikTokUrl(input: String): String? {
    val m = Regex("""tiktok\.com/(@[\w.\-]+)/video/(\d+)""").find(Uri.decode(input)) ?: return null
    return "https://www.tiktok.com/${m.groupValues[1]}/video/${m.groupValues[2]}"
}

/**
 * Halaman TikTok sering mencoba membuka app lewat deep link (snssdk1180://, tiktok://, intent://).
 * WebView tidak bisa membukanya → ERR_UNKNOWN_URL_SCHEME. Di sini deep link itu diblokir,
 * dan kalau di dalamnya ada link video, kita muat versi web bersihnya.
 */
open class TikTokWebViewClient : WebViewClient() {
    private var redirected = false

    override fun shouldOverrideUrlLoading(view: WebView, request: WebResourceRequest): Boolean {
        val scheme = request.url.scheme
        if (scheme == "http" || scheme == "https") return false
        if (!redirected) {
            cleanTikTokUrl(request.url.toString())?.let {
                redirected = true
                view.loadUrl(it)
            }
        }
        return true // blokir semua skema non-http(s)
    }
}
