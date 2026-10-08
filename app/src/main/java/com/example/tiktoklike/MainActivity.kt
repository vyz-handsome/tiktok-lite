package com.example.tiktoklike

import android.annotation.SuppressLint
import android.content.Intent
import android.os.Bundle
import android.os.SystemClock
import android.view.MotionEvent
import android.view.View
import android.view.ViewGroup
import android.view.WindowManager
import android.webkit.WebView
import android.webkit.WebViewClient
import android.widget.ArrayAdapter
import android.widget.Button
import android.widget.EditText
import android.widget.FrameLayout
import android.widget.ListView
import android.widget.ScrollView
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import androidx.webkit.ProfileStore
import androidx.webkit.WebViewCompat
import androidx.webkit.WebViewFeature
import com.google.android.material.bottomnavigation.BottomNavigationView
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONTokener
import java.net.HttpURLConnection
import java.net.URL
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withTimeoutOrNull
import kotlin.coroutines.resume
import kotlin.random.Random

class MainActivity : AppCompatActivity() {

    private lateinit var store: AccountStore
    private lateinit var homeView: View
    private lateinit var accountsView: View
    private lateinit var etUrl: EditText
    private lateinit var btnLike: Button
    private lateinit var btnStop: Button
    private lateinit var runContainer: FrameLayout
    private lateinit var tvLog: TextView
    private lateinit var logScroll: ScrollView
    private lateinit var lvAccounts: ListView

    private var job: Job? = null
    private val multiProfile get() = WebViewFeature.isFeatureSupported(WebViewFeature.MULTI_PROFILE)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)
        store = AccountStore(this)

        homeView = findViewById(R.id.homeView)
        accountsView = findViewById(R.id.accountsView)
        etUrl = findViewById(R.id.etUrl)
        btnLike = findViewById(R.id.btnLike)
        btnStop = findViewById(R.id.btnStop)
        runContainer = findViewById(R.id.runContainer)
        tvLog = findViewById(R.id.tvLog)
        logScroll = findViewById(R.id.logScroll)
        lvAccounts = findViewById(R.id.lvAccounts)

        findViewById<BottomNavigationView>(R.id.bottomNav).setOnItemSelectedListener {
            val home = it.itemId == R.id.nav_home
            homeView.visibility = if (home) View.VISIBLE else View.GONE
            accountsView.visibility = if (home) View.GONE else View.VISIBLE
            if (!home) refreshAccounts()
            true
        }

        findViewById<Button>(R.id.btnAdd).setOnClickListener { addAccountDialog() }
        lvAccounts.setOnItemClickListener { _, _, pos, _ -> accountMenu(store.all()[pos]) }
        btnLike.setOnClickListener { startLiking() }
        btnStop.setOnClickListener { job?.cancel() }

        if (!multiProfile) {
            log("PERINGATAN: WebView di HP ini belum mendukung multi-profile. " +
                "Update 'Android System WebView' / Chrome dari Play Store lalu buka ulang app.")
        }
        refreshAccounts()
    }

    // ───────────────────────── Add Account ─────────────────────────

    private fun refreshAccounts() {
        val list = store.all()
        lvAccounts.adapter = ArrayAdapter(
            this, android.R.layout.simple_list_item_1,
            list.map { "${it.label}  (${it.id})" }
        )
    }

    private fun addAccountDialog() {
        if (!multiProfile) {
            Toast.makeText(this, "WebView belum mendukung multi-profile. Update dulu.", Toast.LENGTH_LONG).show()
            return
        }
        val et = EditText(this).apply { hint = "Nama akun (bebas, mis. akun utama)" }
        AlertDialog.Builder(this)
            .setTitle("Tambah akun")
            .setView(et)
            .setPositiveButton("Lanjut login") { _, _ ->
                val label = et.text.toString().trim().ifEmpty { "Akun ${store.all().size + 1}" }
                openLogin(store.add(label))
                refreshAccounts()
            }
            .setNegativeButton("Batal", null)
            .show()
    }

    private fun accountMenu(acc: Account) {
        AlertDialog.Builder(this)
            .setTitle(acc.label)
            .setItems(arrayOf("Login ulang", "Hapus akun")) { _, which ->
                if (which == 0) openLogin(acc) else deleteAccount(acc)
            }
            .show()
    }

    private fun openLogin(acc: Account) {
        startActivity(
            Intent(this, LoginActivity::class.java)
                .putExtra("id", acc.id).putExtra("label", acc.label)
        )
    }

    private fun deleteAccount(acc: Account) {
        if (job?.isActive == true) {
            Toast.makeText(this, "Stop proses like dulu.", Toast.LENGTH_SHORT).show()
            return
        }
        runCatching { ProfileStore.getInstance().deleteProfile(acc.id) }
        store.remove(acc.id)
        refreshAccounts()
    }

    // ───────────────────────── Beranda: proses like ─────────────────────────

    private fun log(msg: String) {
        tvLog.append(msg + "\n")
        logScroll.post { logScroll.fullScroll(View.FOCUS_DOWN) }
    }

    private fun startLiking() {
        val raw = etUrl.text.toString().trim()
        val url = cleanTikTokUrl(raw) ?: raw
        val accounts = store.all()
        when {
            !multiProfile -> return log("WebView belum mendukung multi-profile.")
            !url.startsWith("http") || !url.contains("tiktok.com") ->
                return log("Link tidak valid. Tempel link video TikTok.")
            accounts.isEmpty() -> return log("Belum ada akun. Tambah dulu di menu Add Account.")
        }

        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        btnLike.isEnabled = false
        btnStop.isEnabled = true
        tvLog.text = ""

        job = lifecycleScope.launch {
            var liked = 0
            try {
                val target = if (cleanTikTokUrl(url) != null) url else resolveUrl(url)
                log("Link video: $target")
                accounts.forEachIndexed { i, acc ->
                    log("[${i + 1}/${accounts.size}] ${acc.label}: membuka video...")
                    val r = likeWith(acc, target)
                    if (r == "VERIFIED") liked++
                    log("   → " + describe(r))
                    if (i < accounts.lastIndex) {
                        val d = Random.nextLong(20_000, 60_001)
                        log("   jeda ${d / 1000} detik...")
                        delay(d)
                    }
                }
                log("\nSelesai. $liked dari ${accounts.size} akun diproses.")
            } finally {
                btnLike.isEnabled = true
                btnStop.isEnabled = false
                window.clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
            }
        }
    }

    private fun describe(r: String) = when (r) {
        "VERIFIED" -> "berhasil di-like (terverifikasi setelah muat ulang)"
        "NOT_SAVED" -> "ketukan terkirim tapi like TIDAK tersimpan di akun (setelah dimuat ulang belum di-like)"
        "UNKNOWN" -> "ketukan terkirim, tapi status like tidak bisa diverifikasi"
        "ALREADY" -> "sudah di-like sebelumnya, dilewati"
        "NOT_FOUND" -> "tombol like tidak ketemu (selector berubah / halaman belum siap)"
        "LOGIN" -> "perlu login ulang (menu Add Account → ketuk akun)"
        "CAPTCHA" -> "kena captcha/verifikasi, selesaikan manual lewat Login ulang"
        "TIMEOUT" -> "halaman terlalu lama dimuat"
        else -> r
    }

    /** Satu akun = satu WebView baru yang terikat ke profil (browser) akun itu. */
    @SuppressLint("SetJavaScriptEnabled")
    private suspend fun likeWith(acc: Account, url: String): String {
        val web = WebView(this)
        try {
            ProfileStore.getInstance().getOrCreateProfile(acc.id)
            WebViewCompat.setProfile(web, acc.id) // sebelum load apa pun
            web.settings.javaScriptEnabled = true
            web.settings.domStorageEnabled = true
            web.settings.userAgentString = DESKTOP_UA
            web.settings.useWideViewPort = true
            web.settings.loadWithOverviewMode = true

            var loaded = CompletableDeferred<Unit>()
            web.webViewClient = object : TikTokWebViewClient() {
                override fun onPageFinished(view: WebView?, u: String?) {
                    if (!loaded.isCompleted) loaded.complete(Unit)
                }
            }
            runContainer.removeAllViews()
            runContainer.addView(
                web, ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT
            )
            web.loadUrl(url)

            withTimeoutOrNull(30_000) { loaded.await() } ?: return "TIMEOUT"
            delay(Random.nextLong(4_000, 7_000)) // tunggu halaman benar-benar render

            // Halaman desktop TikTok berat; coba beberapa kali sampai tombol like muncul
            var first = "NOT_FOUND"
            repeat(5) {
                if (first == "NOT_FOUND") {
                    first = web.eval(CLICK_JS)
                    if (first == "NOT_FOUND") delay(3_000)
                }
            }
            if (first == "NOT_FOUND") log("   [debug] " + web.evalString(DIAG_JS))
            if (!first.startsWith("POS|")) return first

            // Ketukan sentuh asli (bukan sekadar JS click) di posisi tombol like
            val (cx, cy, iw) = first.split("|").drop(1).map { it.toFloat() }
            val f = web.width / iw
            tap(web, cx * f, cy * f)
            delay(4_000)
            if (web.eval(CHECK_JS) == "LOGIN") return "LOGIN"

            // Verifikasi sebenarnya: muat ulang halaman, lihat apakah like tersimpan di akun
            loaded = CompletableDeferred()
            web.reload()
            withTimeoutOrNull(30_000) { loaded.await() }
            delay(6_000)
            var state = "NOBTN"
            repeat(4) {
                if (state == "NOBTN") {
                    state = web.eval(STATE_JS)
                    if (state == "NOBTN") delay(3_000)
                }
            }
            return when (state) {
                "LIKED" -> "VERIFIED"
                "NOTLIKED" -> "NOT_SAVED"
                else -> {
                    log("   [debug] " + web.evalString(DIAG_JS))
                    "UNKNOWN"
                }
            }
        } finally {
            runContainer.removeAllViews()
            web.destroy()
            runCatching { ProfileStore.getInstance().getProfile(acc.id)?.cookieManager?.flush() }
        }
    }

    private suspend fun tap(web: WebView, x: Float, y: Float) {
        val t = SystemClock.uptimeMillis()
        web.dispatchTouchEvent(MotionEvent.obtain(t, t, MotionEvent.ACTION_DOWN, x, y, 0))
        delay(90)
        web.dispatchTouchEvent(MotionEvent.obtain(t, SystemClock.uptimeMillis(), MotionEvent.ACTION_UP, x, y, 0))
    }

    /** Ikuti redirect link pendek (vt.tiktok.com / vm.tiktok.com) sampai ketemu link video aslinya. */
    private suspend fun resolveUrl(url: String): String = withContext(Dispatchers.IO) {
        var cur = url
        repeat(6) {
            val c = URL(cur).openConnection() as HttpURLConnection
            try {
                c.instanceFollowRedirects = false
                c.connectTimeout = 10_000
                c.readTimeout = 10_000
                c.setRequestProperty("User-Agent", DESKTOP_UA)
                val code = c.responseCode
                if (code !in 300..399) return@withContext cur
                val loc = c.getHeaderField("Location") ?: return@withContext cur
                cur = URL(URL(cur), loc).toString()
                cleanTikTokUrl(cur)?.let { return@withContext it }
            } catch (e: Exception) {
                return@withContext cur
            } finally {
                c.disconnect()
            }
        }
        cur
    }

    private suspend fun WebView.evalString(js: String): String = suspendCancellableCoroutine { c ->
        evaluateJavascript(js) { raw ->
            c.resume(runCatching { JSONTokener(raw).nextValue().toString() }.getOrDefault(raw ?: "NULL"))
        }
    }

    private suspend fun WebView.eval(js: String): String = suspendCancellableCoroutine { c ->
        evaluateJavascript(js) { c.resume(it?.trim('"') ?: "NULL") }
    }

    override fun onDestroy() {
        job?.cancel()
        super.onDestroy()
    }

    companion object {
        private const val CLICK_JS = """
(function(){
  if(document.querySelector('#captcha-verify-container-main-page,.captcha_verify_container')) return 'CAPTCHA';
  var icon=document.querySelector('[data-e2e="like-icon"],[data-e2e="browse-like-icon"]');
  if(!icon) return document.querySelector('[data-e2e="login-modal"],#login-modal') ? 'LOGIN' : 'NOT_FOUND';
  var btn=icon.closest('button')||icon;
  if(btn.getAttribute('aria-pressed')==='true') return 'ALREADY';
  btn.scrollIntoView({block:'center',inline:'center'});
  var r=btn.getBoundingClientRect();
  if(r.width===0||r.height===0) return 'NOT_FOUND';
  return 'POS|'+(r.left+r.width/2)+'|'+(r.top+r.height/2)+'|'+window.innerWidth;
})()
"""
        private const val STATE_JS = """
(function(){
  var icon=document.querySelector('[data-e2e="like-icon"],[data-e2e="browse-like-icon"]');
  if(!icon) return 'NOBTN';
  var btn=icon.closest('button')||icon;
  var p=btn.getAttribute('aria-pressed');
  if(p==='true') return 'LIKED';
  if(p==='false') return 'NOTLIKED';
  return 'NOBTN';
})()
"""
        private const val DIAG_JS = """
(function(){
  var s={};
  document.querySelectorAll('[data-e2e]').forEach(function(e){s[e.getAttribute('data-e2e')]=1});
  return location.href.substring(0,150)+' | '+document.title+' | '+Object.keys(s).slice(0,40).join(',');
})()
"""
        private const val CHECK_JS = """
(function(){
  if(document.querySelector('[data-e2e="login-modal"],#login-modal')) return 'LOGIN';
  var icon=document.querySelector('[data-e2e="like-icon"],[data-e2e="browse-like-icon"]');
  var btn=icon&&(icon.closest('button')||icon);
  if(btn&&btn.getAttribute('aria-pressed')==='true') return 'LIKED';
  return 'UNKNOWN';
})()
"""
    }
}
