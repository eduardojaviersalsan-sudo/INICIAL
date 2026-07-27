package com.eduardosalsan.tiktokstickersaver.ui

import android.Manifest
import android.annotation.SuppressLint
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.view.KeyEvent
import android.view.Menu
import android.view.MenuItem
import android.view.View
import android.view.inputmethod.EditorInfo
import android.view.inputmethod.InputMethodManager
import android.webkit.WebChromeClient
import android.webkit.WebResourceRequest
import android.webkit.WebView
import android.webkit.WebViewClient
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import com.eduardosalsan.tiktokstickersaver.R
import com.eduardosalsan.tiktokstickersaver.data.StickerDownloader
import com.eduardosalsan.tiktokstickersaver.databinding.ActivityMainBinding
import com.eduardosalsan.tiktokstickersaver.web.StickerDetectorScript
import com.eduardosalsan.tiktokstickersaver.web.StickerJsBridge
import kotlinx.coroutines.launch

/**
 * Navegador embebido para TikTok que permite guardar los stickers que
 * aparecen en los comentarios de un video, ya sea manteniendo presionada
 * cualquier imagen (como "guardar imagen" en un navegador normal) o usando
 * la detección automática best-effort de la franja inferior.
 *
 * No se usan endpoints privados ni credenciales de TikTok: solo se guardan
 * imágenes que el propio sitio ya cargó para mostrarlas en pantalla.
 */
class MainActivity : AppCompatActivity() {

    private lateinit var binding: ActivityMainBinding
    private lateinit var downloader: StickerDownloader
    private lateinit var stripAdapter: StickerStripAdapter

    private var pendingAction: (() -> Unit)? = null

    private val requestPermissionLauncher =
        registerForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
            if (granted) {
                pendingAction?.invoke()
            } else {
                Toast.makeText(this, R.string.permission_needed_message, Toast.LENGTH_LONG).show()
            }
            pendingAction = null
        }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        downloader = StickerDownloader(applicationContext)

        setSupportActionBar(binding.toolbar)
        supportActionBar?.setDisplayShowTitleEnabled(false)

        setupWebView()
        setupAddressBar()
        setupStickerStrip()

        val sharedUrl = extractSharedUrl(intent)
        val initialUrl = sharedUrl ?: DEFAULT_URL
        loadUrl(initialUrl)
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        extractSharedUrl(intent)?.let { loadUrl(it) }
    }

    private fun extractSharedUrl(intent: Intent?): String? {
        if (intent?.action == Intent.ACTION_SEND && intent.type == "text/plain") {
            val text = intent.getStringExtra(Intent.EXTRA_TEXT) ?: return null
            return Regex("https?://\\S+").find(text)?.value
        }
        return null
    }

    @SuppressLint("SetJavaScriptEnabled")
    private fun setupWebView() {
        val webView = binding.webView
        webView.settings.apply {
            javaScriptEnabled = true
            domStorageEnabled = true
            loadWithOverviewMode = true
            useWideViewPort = true
            userAgentString = MOBILE_USER_AGENT
            mediaPlaybackRequiresUserGesture = true
        }

        webView.addJavascriptInterface(
            StickerJsBridge { url -> onStickerDetected(url) },
            StickerJsBridge.INTERFACE_NAME
        )

        webView.webChromeClient = object : WebChromeClient() {
            override fun onProgressChanged(view: WebView?, newProgress: Int) {
                binding.progressBar.visibility = if (newProgress in 1..99) View.VISIBLE else View.GONE
                binding.progressBar.progress = newProgress
            }
        }

        webView.webViewClient = object : WebViewClient() {
            override fun shouldOverrideUrlLoading(view: WebView?, request: WebResourceRequest?): Boolean {
                val uri = request?.url ?: return false
                return if (uri.scheme == "http" || uri.scheme == "https") {
                    false
                } else {
                    try {
                        startActivity(Intent(Intent.ACTION_VIEW, uri))
                    } catch (e: Exception) {
                        // No hay app que maneje ese esquema; se ignora la navegación.
                    }
                    true
                }
            }

            override fun onPageFinished(view: WebView?, url: String?) {
                super.onPageFinished(view, url)
                url?.let { binding.urlInput.setText(it) }
                updateNavButtons()
                view?.evaluateJavascript(StickerDetectorScript.SOURCE, null)
            }
        }

        webView.setOnLongClickListener {
            val result = webView.hitTestResult
            val url = result.extra
            if ((result.type == WebView.HitTestResult.IMAGE_TYPE ||
                    result.type == WebView.HitTestResult.SRC_IMAGE_ANCHOR_TYPE) && !url.isNullOrBlank()
            ) {
                confirmAndSave(url)
                true
            } else {
                false
            }
        }
    }

    private fun setupAddressBar() {
        binding.btnGo.setOnClickListener { loadFromInput() }
        binding.urlInput.setOnEditorActionListener { _, actionId, event ->
            val isGo = actionId == EditorInfo.IME_ACTION_GO || actionId == EditorInfo.IME_ACTION_DONE
            val isEnter = event != null && event.keyCode == KeyEvent.KEYCODE_ENTER
            if (isGo || isEnter) {
                loadFromInput()
                true
            } else {
                false
            }
        }
        binding.btnBack.setOnClickListener { if (binding.webView.canGoBack()) binding.webView.goBack() }
        binding.btnForward.setOnClickListener { if (binding.webView.canGoForward()) binding.webView.goForward() }
        binding.btnReload.setOnClickListener { binding.webView.reload() }
    }

    private fun setupStickerStrip() {
        stripAdapter = StickerStripAdapter { url -> confirmAndSave(url) }
        binding.stickerStripList.layoutManager =
            LinearLayoutManager(this, LinearLayoutManager.HORIZONTAL, false)
        binding.stickerStripList.adapter = stripAdapter
        binding.btnDismissStrip.setOnClickListener { binding.stickerStripContainer.visibility = View.GONE }
        binding.btnSaveAll.setOnClickListener { ensureStoragePermission { saveAllDetected() } }
    }

    private fun loadFromInput() {
        var text = binding.urlInput.text?.toString()?.trim().orEmpty()
        if (text.isEmpty()) return
        if (!text.startsWith("http://") && !text.startsWith("https://")) {
            text = "https://$text"
        }
        loadUrl(text)
        val imm = getSystemService(INPUT_METHOD_SERVICE) as? InputMethodManager
        imm?.hideSoftInputFromWindow(binding.urlInput.windowToken, 0)
    }

    private fun loadUrl(url: String) {
        binding.urlInput.setText(url)
        binding.webView.loadUrl(url)
    }

    private fun updateNavButtons() {
        val canBack = binding.webView.canGoBack()
        val canForward = binding.webView.canGoForward()
        binding.btnBack.isEnabled = canBack
        binding.btnForward.isEnabled = canForward
        binding.btnBack.alpha = if (canBack) 1f else 0.4f
        binding.btnForward.alpha = if (canForward) 1f else 0.4f
    }

    private fun onStickerDetected(url: String) {
        val added = stripAdapter.addIfNew(url)
        if (added) {
            binding.stickerStripContainer.visibility = View.VISIBLE
            binding.stickerStripTitle.text =
                getString(R.string.detected_stickers_title, stripAdapter.itemCount)
        }
    }

    private fun confirmAndSave(url: String) {
        AlertDialog.Builder(this)
            .setTitle(R.string.save_sticker_title)
            .setMessage(R.string.save_sticker_message)
            .setPositiveButton(R.string.action_save) { _, _ -> ensureStoragePermission { saveSticker(url) } }
            .setNegativeButton(R.string.action_cancel, null)
            .show()
    }

    private fun ensureStoragePermission(onGranted: () -> Unit) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            onGranted()
            return
        }
        val granted = ContextCompat.checkSelfPermission(
            this, Manifest.permission.WRITE_EXTERNAL_STORAGE
        ) == PackageManager.PERMISSION_GRANTED

        if (granted) {
            onGranted()
        } else {
            pendingAction = onGranted
            requestPermissionLauncher.launch(Manifest.permission.WRITE_EXTERNAL_STORAGE)
        }
    }

    private fun saveSticker(url: String) {
        lifecycleScope.launch {
            when (val result = downloader.download(url, referer = binding.webView.url)) {
                is StickerDownloader.Result.Success ->
                    Toast.makeText(this@MainActivity, R.string.toast_saved, Toast.LENGTH_SHORT).show()
                is StickerDownloader.Result.Failure ->
                    Toast.makeText(
                        this@MainActivity,
                        "${getString(R.string.toast_save_error)}: ${result.message}",
                        Toast.LENGTH_SHORT
                    ).show()
            }
        }
    }

    private fun saveAllDetected() {
        val urls = stripAdapter.currentUrls()
        if (urls.isEmpty()) return
        Toast.makeText(this, getString(R.string.toast_saving_n, urls.size), Toast.LENGTH_SHORT).show()
        lifecycleScope.launch {
            var success = 0
            for (url in urls) {
                val result = downloader.download(url, referer = binding.webView.url)
                if (result is StickerDownloader.Result.Success) success++
            }
            Toast.makeText(
                this@MainActivity,
                getString(R.string.toast_saved_n, success, urls.size),
                Toast.LENGTH_LONG
            ).show()
        }
    }

    override fun onCreateOptionsMenu(menu: Menu): Boolean {
        menuInflater.inflate(R.menu.menu_main, menu)
        return true
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        return when (item.itemId) {
            R.id.action_gallery -> {
                startActivity(Intent(this, GalleryActivity::class.java))
                true
            }
            R.id.action_help -> {
                showHelp()
                true
            }
            else -> super.onOptionsItemSelected(item)
        }
    }

    private fun showHelp() {
        AlertDialog.Builder(this)
            .setTitle(R.string.action_help)
            .setMessage(R.string.help_text)
            .setPositiveButton(android.R.string.ok, null)
            .show()
    }

    @Deprecated("Deprecated in Java", ReplaceWith("super.onBackPressed()"))
    override fun onBackPressed() {
        if (binding.webView.canGoBack()) {
            binding.webView.goBack()
        } else {
            super.onBackPressed()
        }
    }

    companion object {
        private const val DEFAULT_URL = "https://www.tiktok.com/"
        private const val MOBILE_USER_AGENT =
            "Mozilla/5.0 (Linux; Android 13; Pixel 7) AppleWebKit/537.36 " +
                "(KHTML, like Gecko) Chrome/120.0.0.0 Mobile Safari/537.36"
    }
}
