package com.eduardosalsan.tiktokstickersaver.web

/**
 * Heurística de detección de stickers en los comentarios de TikTok.
 *
 * TikTok no expone una API pública para leer stickers de comentarios, y las
 * clases CSS de su sitio están ofuscadas/cambian con frecuencia. Este script
 * NO llama a ningún endpoint privado: solo inspecciona el DOM que el propio
 * sitio ya renderizó en el WebView (igual que "guardar imagen" en un
 * navegador) y reporta las <img> candidatas a sticker dentro del área de
 * comentarios, filtrando avatares por tamaño y atributos.
 *
 * Si TikTok cambia su maquetación esta heurística puede dejar de encontrar
 * resultados; el mecanismo de "mantener presionado para guardar" (basado en
 * WebView.HitTestResult) sigue funcionando siempre como respaldo.
 */
object StickerDetectorScript {

    val SOURCE = """
        (function() {
          if (window.__stickerSaverInstalled) { return; }
          window.__stickerSaverInstalled = true;

          var seen = {};

          function isLikelyAvatar(img) {
            var el = img;
            for (var i = 0; i < 6 && el; i++) {
              var e2e = el.getAttribute ? el.getAttribute('data-e2e') : null;
              var cls = (el.className && el.className.toString) ? el.className.toString() : '';
              if ((e2e && e2e.toLowerCase().indexOf('avatar') !== -1) ||
                  cls.toLowerCase().indexOf('avatar') !== -1) {
                return true;
              }
              el = el.parentElement;
            }
            return false;
          }

          function inCommentArea(img) {
            var el = img;
            for (var i = 0; i < 10 && el; i++) {
              var e2e = el.getAttribute ? el.getAttribute('data-e2e') : null;
              var cls = (el.className && el.className.toString) ? el.className.toString() : '';
              if (e2e && e2e.toLowerCase().indexOf('comment') !== -1) { return true; }
              if (cls.toLowerCase().indexOf('comment') !== -1) { return true; }
              el = el.parentElement;
            }
            return false;
          }

          function reportImage(src) {
            if (!src || seen[src]) { return; }
            seen[src] = true;
            if (window.AndroidStickers && window.AndroidStickers.onStickerFound) {
              window.AndroidStickers.onStickerFound(src);
            }
          }

          function scan() {
            try {
              var imgs = document.querySelectorAll('img');
              for (var i = 0; i < imgs.length; i++) {
                var img = imgs[i];
                var src = img.currentSrc || img.src;
                if (!src) { continue; }
                var w = img.naturalWidth || img.width || 0;
                var h = img.naturalHeight || img.height || 0;
                var isSmall = w > 0 && w <= 160 && h > 0 && h <= 160;
                if (isSmall && inCommentArea(img) && !isLikelyAvatar(img)) {
                  reportImage(src);
                }
              }
            } catch (e) { /* best-effort, ignorar errores */ }
          }

          var debounceTimer = null;
          function scheduleScan() {
            clearTimeout(debounceTimer);
            debounceTimer = setTimeout(scan, 500);
          }

          var observer = new MutationObserver(scheduleScan);
          observer.observe(document.body, { childList: true, subtree: true, attributes: true });

          document.addEventListener('scroll', scheduleScan, true);
          scan();
        })();
    """.trimIndent()
}
