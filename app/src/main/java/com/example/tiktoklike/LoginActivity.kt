package com.example.tiktoklike

import android.annotation.SuppressLint
import android.os.Bundle
import android.view.ViewGroup
import android.webkit.WebView
import android.webkit.WebViewClient
import android.widget.Button
import android.widget.FrameLayout
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.webkit.ProfileStore
import androidx.webkit.WebViewCompat

/** Buka "browser" milik satu akun supaya user login manual. Sesi tersimpan di profil itu. */
class LoginActivity : AppCompatActivity() {

    private var web: WebView? = null
    private var profileId: String = ""

    @SuppressLint("SetJavaScriptEnabled")
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_login)

        profileId = intent.getStringExtra("id") ?: return finish()
        findViewById<TextView>(R.id.tvTitle).text =
            "Login: " + (intent.getStringExtra("label") ?: profileId)

        val w = WebView(this)
        // setProfile HARUS dipanggil sebelum WebView memuat apa pun.
        ProfileStore.getInstance().getOrCreateProfile(profileId)
        WebViewCompat.setProfile(w, profileId)
        w.settings.javaScriptEnabled = true
        w.settings.domStorageEnabled = true
        w.settings.userAgentString = w.settings.userAgentString.replace("; wv", "")
        w.webViewClient = WebViewClient()
        findViewById<FrameLayout>(R.id.webHolder).addView(
            w, ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT
        )
        web = w
        w.loadUrl("https://www.tiktok.com/login")

        findViewById<Button>(R.id.btnDone).setOnClickListener { finish() }
    }

    override fun onPause() {
        super.onPause()
        flush()
    }

    override fun onDestroy() {
        flush()
        findViewById<FrameLayout?>(R.id.webHolder)?.removeAllViews()
        web?.destroy()
        web = null
        super.onDestroy()
    }

    private fun flush() {
        runCatching { ProfileStore.getInstance().getProfile(profileId)?.cookieManager?.flush() }
    }
}
