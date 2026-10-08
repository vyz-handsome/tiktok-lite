package com.example.tiktoklike

import android.annotation.SuppressLint
import android.os.Bundle
import android.view.ViewGroup
import android.view.WindowManager
import android.webkit.WebView
import android.widget.Button
import android.widget.CheckBox
import android.widget.FrameLayout
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import androidx.webkit.ProfileStore
import androidx.webkit.WebViewCompat
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlin.coroutines.resume

/**
 * Mode manual: tiap akun dibuka di browser-nya sendiri, halaman digulir ke tombol like
 * (ditandai merah dan dibesarkan), lalu pengguna menekan like sendiri.
 */
class ManualLikeActivity : AppCompatActivity() {

    private lateinit var accounts: List<Account>
    private lateinit var url: String
    private var index = 0
    private var web: WebView? = null
    private var currentId: String? = null
    private var pollJob: Job? = null
    private val liked = mutableSetOf<String>()

    private lateinit var holder: FrameLayout
    private lateinit var tvTitle: TextView
    private lateinit var tvStatus: TextView
    private lateinit var btnPrev: Button
    private lateinit var btnNext: Button
    private lateinit var cbAuto: CheckBox

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_manual)

        url = intent.getStringExtra("url") ?: return finish()
        accounts = AccountStore(this).all()
        if (accounts.isEmpty()) return finish()
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)

        holder = findViewById(R.id.holder)
        tvTitle = findViewById(R.id.tvTitle)
        tvStatus = findViewById(R.id.tvStatus)
        btnPrev = findViewById(R.id.btnPrev)
        btnNext = findViewById(R.id.btnNext)
        cbAuto = findViewById(R.id.cbAuto)

        btnPrev.setOnClickListener { go(index - 1) }
        btnNext.setOnClickListener { if (index == accounts.lastIndex) finishRun() else go(index + 1) }
        findViewById<Button>(R.id.btnFocus).setOnClickListener {
            lifecycleScope.launch { web?.eval(FOCUS_JS) }
        }
        go(0)
    }

    private fun updateTitle() {
        tvTitle.text = "Akun ${index + 1}/${accounts.size}: ${accounts[index].label}  (ter-like: ${liked.size})"
    }

    @SuppressLint("SetJavaScriptEnabled")
    private fun go(i: Int) {
        if (i < 0 || i > accounts.lastIndex) return
        index = i
        pollJob?.cancel()
        releaseWeb()

        val acc = accounts[i]
        updateTitle()
        btnPrev.isEnabled = i > 0
        btnNext.text = if (i == accounts.lastIndex) "Selesai" else "Berikutnya"
        tvStatus.text = "Memuat video..."

        val w = WebView(this)
        ProfileStore.getInstance().getOrCreateProfile(acc.id)
        WebViewCompat.setProfile(w, acc.id) // sebelum memuat apa pun
        currentId = acc.id
        w.settings.javaScriptEnabled = true
        w.settings.domStorageEnabled = true
        w.settings.userAgentString = DESKTOP_UA
        w.settings.useWideViewPort = true
        w.settings.loadWithOverviewMode = true
        w.settings.setSupportZoom(true)
        w.settings.builtInZoomControls = true
        w.settings.displayZoomControls = false
        w.webViewClient = TikTokWebViewClient()
        holder.addView(w, ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT)
        web = w
        w.loadUrl(url)

        pollJob = lifecycleScope.launch { poll(w, acc) }
    }

    private suspend fun poll(w: WebView, acc: Account) {
        delay(4_000)
        var prepared = false
        while (true) {
            val st = w.eval(STATE_JS)
            if (st == "NOBTN") {
                tvStatus.text = "Mencari tombol like... (kalau lama, scroll sendiri atau tekan \"Ke tombol like\")"
            } else {
                if (!prepared) {
                    w.eval(FOCUS_JS)
                    prepared = true
                }
                if (st == "LIKED") {
                    liked.add(acc.id)
                    updateTitle()
                    tvStatus.text = "✓ Sudah di-like"
                    if (cbAuto.isChecked) {
                        delay(1_500)
                        if (index == accounts.lastIndex) finishRun() else go(index + 1)
                        return
                    }
                } else {
                    tvStatus.text = "Tekan tombol like yang ditandai merah"
                }
            }
            delay(1_000)
        }
    }

    private fun finishRun() {
        Toast.makeText(this, "Selesai: ${liked.size} dari ${accounts.size} akun ter-like", Toast.LENGTH_LONG).show()
        finish()
    }

    private fun releaseWeb() {
        val w = web ?: return
        runCatching { ProfileStore.getInstance().getProfile(currentId ?: "")?.cookieManager?.flush() }
        holder.removeAllViews()
        w.destroy()
        web = null
    }

    override fun onDestroy() {
        pollJob?.cancel()
        releaseWeb()
        super.onDestroy()
    }

    private suspend fun WebView.eval(js: String): String = suspendCancellableCoroutine { c ->
        evaluateJavascript(js) { c.resume(it?.trim('"') ?: "NULL") }
    }

    companion object {
        private const val STATE_JS = """
(function(){
  var icon=document.querySelector('[data-e2e="like-icon"],[data-e2e="browse-like-icon"]');
  if(!icon) return 'NOBTN';
  var btn=icon.closest('button')||icon;
  return btn.getAttribute('aria-pressed')==='true' ? 'LIKED' : 'NOTLIKED';
})()
"""
        private const val FOCUS_JS = """
(function(){
  var icon=document.querySelector('[data-e2e="like-icon"],[data-e2e="browse-like-icon"]');
  if(!icon) return 'NOBTN';
  var btn=icon.closest('button')||icon;
  btn.scrollIntoView({block:'center',inline:'center'});
  btn.style.outline='4px solid #ff2d55';
  btn.style.borderRadius='50%';
  btn.style.transformOrigin='center';
  btn.style.transform='scale(2)';
  btn.style.position='relative';
  btn.style.zIndex='99999';
  return 'OK';
})()
"""
    }
}
