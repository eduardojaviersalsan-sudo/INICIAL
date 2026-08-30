(function () {
    "use strict";

    var dropzone = document.getElementById("dropzone");
    var fileInput = document.getElementById("file-input");
    var preview = document.getElementById("file-preview");

    if (!dropzone || !fileInput || !preview) {
        return;
    }

    var ALLOWED = ["webp", "gif", "png"];

    function extensionOf(name) {
        var parts = name.split(".");
        return parts.length > 1 ? parts.pop().toLowerCase() : "";
    }

    function renderPreview(fileList) {
        preview.innerHTML = "";
        Array.prototype.forEach.call(fileList, function (file) {
            var ext = extensionOf(file.name);
            var chip = document.createElement("span");
            chip.className = "file-preview__item";
            var icon = ALLOWED.indexOf(ext) !== -1 ? "✅" : "⚠️";
            chip.textContent = icon + " " + file.name;
            preview.appendChild(chip);
        });
    }

    fileInput.addEventListener("change", function () {
        renderPreview(fileInput.files);
    });

    ["dragenter", "dragover"].forEach(function (evtName) {
        dropzone.addEventListener(evtName, function (e) {
            e.preventDefault();
            e.stopPropagation();
            dropzone.classList.add("dropzone--drag");
        });
    });

    ["dragleave", "drop"].forEach(function (evtName) {
        dropzone.addEventListener(evtName, function (e) {
            e.preventDefault();
            e.stopPropagation();
            dropzone.classList.remove("dropzone--drag");
        });
    });

    dropzone.addEventListener("drop", function (e) {
        var dt = e.dataTransfer;
        if (dt && dt.files && dt.files.length) {
            fileInput.files = dt.files;
            renderPreview(dt.files);
        }
    });

    // Auto-ocultar mensajes flash luego de unos segundos.
    document.querySelectorAll(".flash").forEach(function (el) {
        setTimeout(function () {
            el.style.transition = "opacity 0.5s";
            el.style.opacity = "0";
            setTimeout(function () {
                el.remove();
            }, 500);
        }, 5000);
    });
})();
