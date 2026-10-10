package com.example.tiktoklike

import android.annotation.SuppressLint
import android.content.Intent
import android.os.Bundle
import android.view.ViewGroup
import android.view.WindowManager
import android.webkit.WebResourceError
import android.webkit.WebResourceRequest
import android.webkit.WebView
import android.widget.Button
import android.widget.CheckBox
import android.widget.FrameLayout
import android.widget.TextView
import androidx.activity.OnBackPressedCallback
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import androidx.webkit.ProfileStore
import androidx.webkit.WebViewCompat
import com.google.android.material.progressindicator.LinearProgressIndicator
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
    private var needReload = false
    private var loadFailed = false
    private var finished = false
    private val liked = mutableSetOf<String>()
    private val already = mutableSetOf<String>()

    private lateinit var holder: FrameLayout
    private lateinit var tvTitle: TextView
    private lateinit var tvStatus: TextView
    private lateinit var progressBar: LinearProgressIndicator
    private lateinit var btnPrev: Button
    private lateinit var btnNext: Button
    private lateinit var cbAuto: CheckBox

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_manual)

        val u = intent.getStringExtra("url")
        val list = AccountStore(this).all()
        if (u == null || list.isEmpty()) {
            finish()
            return
        }
        url = u
        accounts = list
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)

        holder = findViewById(R.id.holder)
        tvTitle = findViewById(R.id.tvTitle)
        tvStatus = findViewById(R.id.tvStatus)
        progressBar = findViewById(R.id.progress)
        btnPrev = findViewById(R.id.btnPrev)
        btnNext = findViewById(R.id.btnNext)
        cbAuto = findViewById(R.id.cbAuto)
        progressBar.max = accounts.size

        btnPrev.setOnClickListener { go(index - 1) }
        btnNext.setOnClickListener { if (index == accounts.lastIndex) finishRun() else go(index + 1) }
        findViewById<Button>(R.id.btnFocus).setOnClickListener {
            lifecycleScope.launch { web?.eval(FOCUS_JS) }
        }
        findViewById<Button>(R.id.btnReload).setOnClickListener { go(index) }
        findViewById<Button>(R.id.btnLogin).setOnClickListener { openLogin() }

        onBackPressedDispatcher.addCallback(this, object : OnBackPressedCallback(true) {
            override fun handleOnBackPressed() {
                AlertDialog.Builder(this@ManualLikeActivity)
                    .setTitle("Keluar dari mode manual?")
                    .setMessage("${liked.size} dari ${accounts.size} akun sudah ter-like.")
                    .setPositiveButton("Keluar") { _, _ -> finishRun() }
                    .setNegativeButton("Lanjut", null)
                    .show()
            }
        })

        go(0)
    }

    override fun onResume() {
        super.onResume()
        // Kembali dari layar login: muat ulang halaman supaya memakai sesi yang baru.
        if (needReload && !finished) {
            needReload = false
            go(index)
        }
    }

    private fun updateTitle() {
        tvTitle.text = "Akun ${index + 1}/${accounts.size}: ${accounts[index].label}   (ter-like: ${liked.size})"
    }

    private fun openLogin() {
        val acc = accounts[index]
        pollJob?.cancel()
        releaseWeb()
        needReload = true
        startActivity(
            Intent(this, LoginActivity::class.java)
                .putExtra("id", acc.id).putExtra("label", acc.label)
        )
    }

    @SuppressLint("SetJavaScriptEnabled")
    private fun go(i: Int) {
        if (i < 0 || i > accounts.lastIndex) return
        index = i
        pollJob?.cancel()
        releaseWeb()
        loadFailed = false

        val acc = accounts[i]
        updateTitle()
        progressBar.setProgressCompat(i + 1, true)
        btnPrev.isEnabled = i > 0
        btnNext.text = if (i == accounts.lastIndex) "Selesai" else "Berikutnya"
        tvStatus.text = "Memuat video..."

        val w = WebView(this)
        ProfileStore.getInstance().getOrCreateProfile(acc.id)
        WebViewCompat.setProfile(w, acc.id) // sebelum memuat apa pun
        currentId = acc.id
        w.settings.javaScriptEnabled = true
        w.settings.domStorageEnabled = true
        w.settings.allowFileAccess = false
        w.settings.userAgentString = DESKTOP_UA
        w.settings.useWideViewPort = true
        w.settings.loadWithOverviewMode = true
        w.settings.setSupportZoom(true)
        w.settings.builtInZoomControls = true
        w.settings.displayZoomControls = false
        w.webViewClient = object : TikTokWebViewClient() {
            override fun onReceivedError(
                view: WebView?, request: WebResourceRequest?, error: WebResourceError?
            ) {
                if (request?.isForMainFrame == true) loadFailed = true
            }
        }
        holder.addView(w, ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT)
        web = w
        w.loadUrl(url)

        pollJob = lifecycleScope.launch { poll(w, acc) }
    }

    private suspend fun poll(w: WebView, acc: Account) {
        delay(4_000)
        var focused = false
        var firstReading = true
        var missing = 0
        while (true) {
            val st = w.eval(STATE_JS)
            if (st == "NOBTN") {
                missing++
                tvStatus.text = when {
                    loadFailed -> "Gagal memuat halaman. Cek internet lalu tekan \"Muat ulang\"."
                    missing > 15 -> "Tombol like tidak ketemu. Kalau akun ini belum login, tekan \"Login ulang\". Atau tekan \"Muat ulang\"."
                    else -> "Mencari tombol like..."
                }
            } else {
                missing = 0
                if (!focused) {
                    w.eval(FOCUS_JS)
                    focused = true
                }
                if (st == "LIKED") {
                    if (firstReading) already.add(acc.id)
                    liked.add(acc.id)
                    updateTitle()
                    tvStatus.text = if (acc.id in already) "✓ Sudah di-like sebelumnya" else "✓ Sudah di-like"
                    if (cbAuto.isChecked) {
                        delay(1_500)
                        if (index == accounts.lastIndex) finishRun() else go(index + 1)
                        return
                    }
                } else {
                    w.eval(MARK_JS) // tanda merah bisa hilang kalau TikTok merender ulang
                    tvStatus.text = "Tekan tombol like yang ditandai merah"
                }
                firstReading = false
            }
            delay(1_000)
        }
    }

    private fun finishRun() {
        if (finished) return
        finished = true
        pollJob?.cancel()
        releaseWeb()
        val summary = accounts.joinToString("\n") { a ->
            when {
                a.id in already -> "✓ ${a.label} (sudah di-like sebelumnya)"
                a.id in liked -> "✓ ${a.label}"
                else -> "✗ ${a.label} (belum)"
            }
        }
        AlertDialog.Builder(this)
            .setTitle("Selesai: ${liked.size} dari ${accounts.size} akun ter-like")
            .setMessage(summary)
            .setCancelable(false)
            .setPositiveButton("Tutup") { _, _ -> finish() }
            .show()
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
        // Hanya menandai (idempotent), tanpa menggulir - aman dipanggil berulang.
        private const val MARK_JS = """
(function(){
  var icon=document.querySelector('[data-e2e="like-icon"],[data-e2e="browse-like-icon"]');
  if(!icon) return 'NOBTN';
  var btn=icon.closest('button')||icon;
  btn.style.outline='4px solid #ff2d55';
  btn.style.borderRadius='50%';
  btn.style.transformOrigin='center';
  btn.style.transform='scale(2)';
  btn.style.position='relative';
  btn.style.zIndex='99999';
  return 'OK';
})()
"""
        // Menggulir tombol ke tengah layar lalu menandainya.
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
