"use strict";

/*
 * mpk-ink.js — слой рукописных аннотаций «Мой Политех» поверх страниц pdf.mjs.
 *
 * Единственный новый JS в проекте (остальное — файлы gander как есть). Работает через
 * тот же WebMessage-порт, что и поиск: window-message «vw-search-port» слушают и pdf.mjs
 * (ставит port.onmessage), и этот файл (addEventListener — оба слушателя получают события,
 * onmessage и addEventListener на MessagePort сосуществуют).
 *
 * Протокол (см. DocWebBridge.kt):
 *   native → JS:  M<0|1> режим рисования; E<0|1> ластик; S <argb> <wNorm> инструмент;
 *                 K<page0> <json> канонические штрихи страницы [{c,w,pts:[x,y,…]}].
 *   JS → native:  ink <page0> <x1> <y1> … — готовый штрих (норм. 0..1);
 *                 zap <page0> <x> <y> <aspect> — точка ластика (aspect = h/w бокса страницы).
 *
 * Вся «умная» логика (hit-test ластика, undo, персист в Room) живёт в Kotlin; JS только
 * рисует канонические штрихи и optimistically — текущий жест, до подтверждения native.
 * Координаты нормализуются к getBoundingClientRect бокса .pg, поэтому композиторный
 * pinch-zoom pdf.mjs (layout не меняется) на них не влияет: clientX/clientY у pointer-
 * событий в Chrome — то же пространство CSS px, что и rect.
 *
 * ponytail: канвас страницы без штрихов не аллоцирует backing store (width=0); резкость
 * штрихов при композиторном зуме до следующего resize не восстанавливается — потолок v1,
 * апгрейд: перерисовка по visualViewport resize при zoom>1.
 */

(function () {
  var port = null;
  var mode = 0;        // 1 = рисуем/стираем, pointer-события наши
  var eraser = 0;
  var color = -16777216; // 0xFF000000 signed, как шлёт Kotlin
  var wNorm = 0.01;
  var strokes = {};    // page0 -> [{c,w,pts}]
  var root = null;
  var kind = null;     // "pdf" (#pages > .pg) | "docx" (.docx-wrapper > section.docx)
  var live = null;     // {page0, box, pointerId, cv, ctx, pts, rect} | {page0, box, pointerId, erasing:true}
  var layoutSent = 0;  // последняя отправленная физическая ширина бокса
  var lastPageMsg = ""; // anti-spam для docx-репортинга страницы
  var MIN_DIST = 0.004; // как MIN_POINT_DIST в AnnotationLayer

  /* v2.0: тот же слой работает и в DOCX — docx-preview раскладывает страницы как
     .docx-wrapper > section.docx. PDF определяет себя #pages сразу, DOCX — когда
     документ отрендерился, поэтому kind resolved лениво. */
  function pagesRoot() {
    if (root) return root;
    var p = document.getElementById("pages");
    if (p) { root = p; kind = "pdf"; return root; }
    var w = document.querySelector(".docx-wrapper");
    if (w) { root = w; kind = "docx"; return root; }
    return null;
  }

  function isPageBox(box) {
    return !!box && box.classList && (box.classList.contains("pg") || box.classList.contains("docx"));
  }

  /* Список page-боксов. PDF: #pages содержит только .pg (children == боксы).
     DOCX: у .docx-wrapper детьми могут быть не только section.docx — берём селектором. */
  function pageBoxes() {
    var r = pagesRoot();
    if (!r) return [];
    return kind === "docx" ? r.querySelectorAll("section.docx") : r.children;
  }

  function pageIndexOf(box) {
    if (box._mpkIdx === undefined) {
      var boxes = pageBoxes();
      for (var i = 0; i < boxes.length; i++) {
        if (boxes[i] === box) { box._mpkIdx = i; break; }
      }
      if (box._mpkIdx === undefined) box._mpkIdx = -1;
    }
    return box._mpkIdx;
  }

  function argbCss(c) {
    var u = c >>> 0;
    return "rgba(" + ((u >> 16) & 255) + "," + ((u >> 8) & 255) + "," + (u & 255) + "," + (((u >> 24) & 255) / 255) + ")";
  }

  function ensureOverlay(box) {
    var cv = box.querySelector(":scope > canvas.mpk-ink");
    if (!cv) {
      /* Overlay абсолютно позиционирован — бокс страницы обязан быть позиционирован.
         .pg у pdf.mjs имеет position:relative из pdf.html; section.docx у docx-preview
         может быть static — ставим relative сами (минимальное вмешательство в стиль). */
      var pos = window.getComputedStyle(box).position;
      if (!pos || pos === "static") box.style.position = "relative";
      cv = document.createElement("canvas");
      cv.className = "mpk-ink";
      cv.style.cssText = "position:absolute;left:0;top:0;width:100%;height:100%;z-index:2;pointer-events:none";
      box.appendChild(cv);
    }
    return cv;
  }

  function sizeCanvas(cv, rect) {
    var dpr = window.devicePixelRatio || 1;
    var w = Math.max(1, Math.round(rect.width * dpr));
    var h = Math.max(1, Math.round(rect.height * dpr));
    if (cv.width !== w || cv.height !== h) { cv.width = w; cv.height = h; }
    var ctx = cv.getContext("2d");
    ctx.setTransform(dpr, 0, 0, dpr, 0, 0);
    return ctx;
  }

  function paintStroke(ctx, s, rectW, rectH) {
    var pts = s.pts;
    if (!pts || pts.length < 2) return;
    ctx.strokeStyle = argbCss(s.c);
    ctx.lineWidth = Math.max(0.5, s.w * rectW);
    ctx.lineCap = "round";
    ctx.lineJoin = "round";
    ctx.beginPath();
    ctx.moveTo(pts[0] * rectW, pts[1] * rectH);
    if (pts.length === 2) {
      // одиночная точка — круглая «капля»
      ctx.lineTo(pts[0] * rectW + 0.01, pts[1] * rectH + 0.01);
    } else {
      for (var i = 2; i < pts.length; i += 2) ctx.lineTo(pts[i] * rectW, pts[i + 1] * rectH);
    }
    ctx.stroke();
  }

  function redrawPage(page0, extra) {
    var boxes = pageBoxes();
    var box = boxes[page0];
    if (!isPageBox(box)) return;
    var list = strokes[page0];
    if ((!list || !list.length) && !extra) {
      var old = box.querySelector(":scope > canvas.mpk-ink");
      if (old) { old.width = 0; old.height = 0; } // освободить память пустой страницы
      return;
    }
    var rect = box.getBoundingClientRect();
    if (!rect.width || !rect.height) return;
    var cv = ensureOverlay(box);
    var ctx = sizeCanvas(cv, rect);
    ctx.clearRect(0, 0, rect.width, rect.height);
    if (list) for (var i = 0; i < list.length; i++) paintStroke(ctx, list[i], rect.width, rect.height);
    if (extra) paintStroke(ctx, extra, rect.width, rect.height);
  }

  function redrawAllocated() {
    for (var key in strokes) if (strokes[key] && strokes[key].length) redrawPage(+key);
  }

  /* Ревью MED #3: физическая ширина первого page-бокса — база нормировки толщины
     штриха и порога ластика в Kotlin. pdf.mjs фиксирует её при открытии документа и
     не перекладывает при ротации, поэтому источник истины — это измерение, а не
     Compose-размер viewer'а. */
  function reportLayout() {
    if (!port) return;
    var box = pageBoxes()[0];
    if (!box) return;
    var rect = box.getBoundingClientRect();
    if (!rect.width) return;
    var phys = Math.round(rect.width * (window.devicePixelRatio || 1));
    if (phys > 0 && phys !== layoutSent) {
      layoutSent = phys;
      send("layout " + phys);
    }
  }

  /* ---- порт ---- */

  function send(msg) { if (port) port.postMessage(msg); }

  function onPortCommand(data) {
    var verb = data.charAt(0);
    if (verb === "M") {
      mode = data.charAt(1) === "1" ? 1 : 0;
      var r = pagesRoot();
      if (r) r.style.touchAction = mode ? "none" : "";
    } else if (verb === "E") {
      eraser = data.charAt(1) === "1" ? 1 : 0;
    } else if (verb === "S") {
      var parts = data.split(" ");
      var c = Number(parts[1]);
      var w = Number(parts[2]);
      if (isFinite(c)) color = c | 0;
      if (isFinite(w) && w > 0) wNorm = w;
    } else if (verb === "K") {
      var sp = data.indexOf(" ");
      if (sp < 0) return;
      var page0 = Number(data.slice(1, sp));
      if (!isFinite(page0) || page0 < 0) return;
      try {
        strokes[page0 | 0] = JSON.parse(data.slice(sp + 1));
      } catch (e) { return; }
      redrawPage(page0 | 0);
    } else if (verb === "g" && kind === "docx") {
      /* Переход к странице (закладки/индикатор). В pdf.mjs «g» обрабатывает сам
         pdf.mjs — здесь только DOCX, иначе был бы двойной скролл. */
      var n = Math.floor(Number(data.slice(1)));
      var boxes = pageBoxes();
      if (isFinite(n) && n >= 1 && n <= boxes.length) {
        boxes[n - 1].scrollIntoView({ block: "start", behavior: "auto" });
        requestAnimationFrame(reportDocxPage);
      }
    }
  }

  window.addEventListener("message", function (e) {
    if (!e.ports || !e.ports.length || port) return;
    port = e.ports[0];
    port.addEventListener("message", function (m) {
      try { onPortCommand(String(m.data || "")); } catch (err) { /* канал не должен умереть */ }
    });
    port.start();
    requestAnimationFrame(reportLayout);
  });

  /* Страницы (.pg) создаются одним фрагментом после открытия документа — штрихи,
     присланные раньше, рисуются, когда боксы появятся. */
  var mo = new MutationObserver(function () {
    redrawAllocated();
    requestAnimationFrame(reportLayout);
    requestAnimationFrame(reportDocxPage);
  });
  function observeRoot() {
    var r = pagesRoot();
    if (r) mo.observe(r, { childList: true });
    else setTimeout(observeRoot, 50);
  }
  observeRoot();

  /* Компоновка могла измениться (vwFitHeight) — перерисовать уже размеченные страницы. */
  if (window.visualViewport) {
    var raf = 0;
    window.visualViewport.addEventListener("resize", function () {
      if (raf) return;
      raf = requestAnimationFrame(function () { raf = 0; redrawAllocated(); reportLayout(); });
    });
  }

  /* v2.0 DOCX: pdf.mjs сам шлёт «page N OF»; docx — нет, поэтому для закладок и
     индикатора страницы считаем её здесь: страница, чей бокс ближе к верху вьюпорта. */
  function reportDocxPage() {
    if (kind !== "docx" || !port) return;
    var boxes = pageBoxes();
    if (!boxes.length) return;
    var top = window.visualViewport ? window.visualViewport.pageTop : window.scrollY;
    var mid = top + (window.visualViewport ? window.visualViewport.height : window.innerHeight) / 3;
    var idx = 0;
    for (var i = 0; i < boxes.length; i++) {
      var rect = boxes[i].getBoundingClientRect();
      var abs = rect.top + top;
      if (abs <= mid) idx = i; else break;
    }
    var msg = "page " + (idx + 1) + " " + boxes.length;
    if (msg !== lastPageMsg) { lastPageMsg = msg; send(msg); }
  }

  window.addEventListener("scroll", function () {
    if (kind === "docx") requestAnimationFrame(reportDocxPage);
  }, { passive: true });

  /* ---- указатель ---- */

  function normPoint(e, rect) {
    var x = (e.clientX - rect.left) / rect.width;
    var y = (e.clientY - rect.top) / rect.height;
    return [x < 0 ? 0 : x > 1 ? 1 : x, y < 0 ? 0 : y > 1 ? 1 : y];
  }

  function aspectOf(rect) { return rect.height / rect.width; }

  document.addEventListener("pointerdown", function (e) {
    if (!mode || !port) return;
    if (live) return; // MED #4: второй палец не перезаписывает первый штрих (иначе ghost)
    var box = e.target && e.target.closest ? e.target.closest(".pg, .docx") : null;
    if (!box || !pagesRoot() || !pagesRoot().contains(box)) return;
    e.preventDefault();
    var page0 = pageIndexOf(box);
    var rect = box.getBoundingClientRect();
    if (!rect.width || !rect.height) return;
    var p = normPoint(e, rect);
    if (eraser) {
      send("zap " + page0 + " " + p[0] + " " + p[1] + " " + aspectOf(rect));
      live = { page0: page0, box: box, erasing: true, pointerId: e.pointerId };
      box.setPointerCapture(e.pointerId);
      return;
    }
    var cv = ensureOverlay(box);
    live = {
      page0: page0, box: box, cv: cv, pointerId: e.pointerId,
      ctx: sizeCanvas(cv, rect), rect: rect,
      pts: [p[0], p[1]],
    };
    box.setPointerCapture(e.pointerId);
    redrawPage(page0, { c: color, w: wNorm, pts: live.pts });
  }, true);

  document.addEventListener("pointermove", function (e) {
    if (!live || e.pointerId !== live.pointerId) return;
    e.preventDefault();
    var box = live.box;
    var rect = live.erasing ? box.getBoundingClientRect() : live.rect;
    var p = normPoint(e, rect);
    if (live.erasing) {
      send("zap " + live.page0 + " " + p[0] + " " + p[1] + " " + aspectOf(rect));
      return;
    }
    var n = live.pts.length;
    var dx = p[0] - live.pts[n - 2], dy = p[1] - live.pts[n - 1];
    if (dx * dx + dy * dy < MIN_DIST * MIN_DIST) return;
    live.pts.push(p[0], p[1]);
    redrawPage(live.page0, { c: color, w: wNorm, pts: live.pts });
  }, true);

  function releaseLive(e) {
    var l = live;
    live = null;
    try { l.box.releasePointerCapture(e.pointerId); } catch (err) { }
    return l;
  }

  function endLive(e) {
    if (!live || e.pointerId !== live.pointerId) return;
    var l = releaseLive(e);
    if (l.erasing) return;
    if (l.pts.length >= 2 && port) {
      send("ink " + l.page0 + " " + l.pts.join(" "));
    }
    // канонический ответ native перерисует страницу; до него остаётся optimistic-штрих
  }

  // LOW #6: отменённый жест (pointercancel) НЕ сохраняется как штрих — только стираем
  // optimistic-пиксели, иначе случайный system-gesture оставил бы мусорную линию.
  function cancelLive(e) {
    if (!live || e.pointerId !== live.pointerId) return;
    var l = releaseLive(e);
    if (!l.erasing) redrawPage(l.page0);
  }

  document.addEventListener("pointerup", endLive, true);
  document.addEventListener("pointercancel", cancelLive, true);
})();
