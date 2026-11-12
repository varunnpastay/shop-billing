/* Decorative animations for the dashboard and login page: falling rose petals, twinkling sparkles,
   drifting peacock feathers and numbers that count up. Purely visual; nothing here changes any data.
   Turned off when the computer asks for reduced motion. */
(function () {
  "use strict";
  const reduce = window.matchMedia && window.matchMedia("(prefers-reduced-motion: reduce)").matches;
  const rand = (a, b) => a + Math.random() * (b - a);
  const pick = (list) => list[Math.floor(Math.random() * list.length)];

  function each(selector, fn) {
    document.querySelectorAll(selector).forEach(fn);
  }

  // rose petals: an outer span falls, the inner span sways and flutters
  const PETAL_COLOURS = ["#e8436b", "#f06a8a", "#d6224f", "#ff8fab", "#c2185b"];
  function petals(box) {
    const n = +box.dataset.count || 22;
    for (let i = 0; i < n; i++) {
      const p = document.createElement("span");
      p.className = "petal";
      const dur = rand(7, 13);
      p.style.setProperty("--x", rand(-2, 100) + "%");
      p.style.setProperty("--dur", dur + "s");
      p.style.setProperty("--delay", -rand(0, dur) + "s");
      p.style.setProperty("--size", rand(9, 17) + "px");
      p.style.setProperty("--sway", rand(18, 55) + "px");
      p.style.setProperty("--spin", rand(1.6, 3.4) + "s");
      p.style.setProperty("--c", pick(PETAL_COLOURS));
      p.appendChild(document.createElement("i"));
      box.appendChild(p);
    }
  }

  // gold sparkles: twinkle in place (data-mode="twinkle") or rise slowly (data-mode="rise")
  function sparkles(box) {
    const n = +box.dataset.count || 16;
    const rise = box.dataset.mode === "rise";
    for (let i = 0; i < n; i++) {
      const s = document.createElement("span");
      s.className = rise ? "spark rise" : "spark";
      const dur = rise ? rand(9, 18) : rand(2.2, 4.5);
      s.style.setProperty("--x", rand(0, 100) + "%");
      s.style.setProperty("--y", rand(0, 100) + "%");
      s.style.setProperty("--dur", dur + "s");
      s.style.setProperty("--delay", -rand(0, dur) + "s");
      s.style.setProperty("--size", rand(rise ? 2 : 3, rise ? 5 : 7) + "px");
      box.appendChild(s);
    }
  }

  // peacock feathers drifting slowly across the page
  function feathers(box) {
    const n = +box.dataset.count || 5;
    for (let i = 0; i < n; i++) {
      const f = document.createElement("img");
      f.src = box.dataset.src;
      f.alt = "";
      f.className = "feather";
      const dur = rand(22, 38);
      f.style.setProperty("--x", rand(0, 95) + "%");
      f.style.setProperty("--dur", dur + "s");
      f.style.setProperty("--delay", -rand(0, dur) + "s");
      f.style.setProperty("--size", rand(48, 92) + "px");
      f.style.setProperty("--tilt", rand(-35, 35) + "deg");
      box.appendChild(f);
    }
  }

  // money figures count up from 0 (₹ 13,070.00 style, Indian grouping)
  function countUp(el) {
    const target = parseFloat(el.dataset.countup);
    if (!isFinite(target)) return;
    const prefix = el.dataset.prefix || "";
    const fmt = (v) => prefix + v.toLocaleString("en-IN", { minimumFractionDigits: 2, maximumFractionDigits: 2 });
    const ms = 1200, start = performance.now();
    function frame(now) {
      const t = Math.min(1, (now - start) / ms);
      const eased = 1 - Math.pow(1 - t, 3);
      el.textContent = fmt(target * eased);
      if (t < 1) requestAnimationFrame(frame);
      else el.textContent = fmt(target);
    }
    requestAnimationFrame(frame);
  }

  function init() {
    if (reduce) return;
    each('[data-fx="petals"]', petals);
    each('[data-fx="sparkles"]', sparkles);
    each('[data-fx="feathers"]', feathers);
    each("[data-countup]", countUp);
  }
  if (document.readyState === "loading") document.addEventListener("DOMContentLoaded", init);
  else init();
})();
