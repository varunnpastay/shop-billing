/* Login page: draws the background mandalas and stars, starts the animated peacock (peacock-engine.js) and runs
   the login. The peacock flies in, then lives (idle actions) until a successful login; a wrong password makes it
   startle and the card shake; a correct one makes it display its wings and fly away while the login screen fades,
   then the dashboard opens. Without WebGL the untouched logo is shown and the login simply fades. */
(function () {
  "use strict";
  const gsap = window.gsap;
  const reduce = !!(window.matchMedia && window.matchMedia("(prefers-reduced-motion: reduce)").matches);
  const page = document.getElementById("loginPage");
  const plate = document.getElementById("logoPlate");
  const host = document.getElementById("peacockHost");
  const form = document.getElementById("loginForm");
  const button = form.querySelector(".login-btn");
  const errorBox = form.querySelector(".error");
  const inputs = Array.from(form.querySelectorAll("input"));

  // ------------------------------------------------------------- background --
  const polar = (cx, cy, r, a) => [cx + r * Math.cos(a), cy + r * Math.sin(a)];
  const f = (n) => n.toFixed(1);
  function petalRing(r0, r1, n, rot, width) {
    rot = rot || 0;
    width = width || 0.9;
    let d = "";
    for (let k = 0; k < n; k++) {
      const a = rot + (2 * Math.PI * k) / n;
      const half = (Math.PI / n) * width;
      const [x0, y0] = polar(0, 0, r0, a - half);
      const [x1, y1] = polar(0, 0, r1, a);
      const [x2, y2] = polar(0, 0, r0, a + half);
      const rm = r0 + (r1 - r0) * 0.55;
      const [c0x, c0y] = polar(0, 0, rm, a - half * 1.15);
      const [c1x, c1y] = polar(0, 0, rm, a + half * 1.15);
      d += `M${f(x0)},${f(y0)} Q${f(c0x)},${f(c0y)} ${f(x1)},${f(y1)} Q${f(c1x)},${f(c1y)} ${f(x2)},${f(y2)} `;
    }
    return d;
  }
  const petals = [
    petalRing(40, 110, 12, 0),
    petalRing(70, 150, 12, Math.PI / 12),
    petalRing(150, 260, 16, 0, 0.95),
    petalRing(200, 330, 16, Math.PI / 16, 0.9),
    petalRing(330, 470, 24, 0, 0.95),
    petalRing(380, 520, 24, Math.PI / 24, 0.85),
  ].join(" ");
  document.querySelectorAll(".mandala-petals").forEach((p) => p.setAttribute("d", petals));

  function seeded(seed) {
    let s = seed;
    return () => ((s = (s * 16807) % 2147483647) - 1) / 2147483646;
  }
  const r = seeded(11);
  const stars = document.querySelector(".bg-stars");
  for (let i = 0; i < 70; i++) {
    const left = r() * 100, top = r() * 100;
    const size = r() < 0.85 ? 1 + r() * 1.4 : 2.4 + r() * 1.2;
    const delay = r() * 6, dur = 2.5 + r() * 4;
    const s = document.createElement("span");
    s.className = "star";
    s.style.cssText = `left:${left}%;top:${top}%;width:${size}px;height:${size}px;animation-delay:${delay}s;animation-duration:${dur}s`;
    stars.appendChild(s);
  }

  // ---------------------------------------------------------------- peacock --
  let api = null;       // { stage, chor } once the WebGL peacock is running
  let canvas = null;
  let tick = null;

  function useStaticLogo() {
    api = null;
    if (tick) gsap.ticker.remove(tick);
    if (canvas) canvas.remove();
    plate.src = plate.dataset.original;
  }

  (async function startPeacock() {
    if (!gsap || !window.SVTPeacock) return useStaticLogo();
    canvas = document.createElement("canvas");
    canvas.setAttribute("aria-hidden", "true");
    host.appendChild(canvas);
    // GPU reset / context loss: show the untouched original logo instead
    canvas.addEventListener("webglcontextlost", (e) => { e.preventDefault(); useStaticLogo(); });
    try {
      const d = document.body.dataset;
      const rigData = await (await fetch(d.rig)).json();
      if (!plate.complete) await new Promise((ok) => plate.addEventListener("load", ok, { once: true }));
      const stage = await window.SVTPeacock.createPeacockStage(canvas, () => plate.getBoundingClientRect(),
          { rigData: rigData, body: d.body, wing: d.wing, saree: d.saree });
      const chor = window.SVTPeacock.createChoreography(stage, { reducedMotion: reduce });

      // warm-up: draw a few invisible frames first, so the graphics are ready before the flight (no hitch at the start)
      stage.ctl.peacockAlpha = 0;
      stage.ctl.sareeAlpha = 0;
      for (let i = 0; i < 4; i++) {
        await new Promise((ok) => requestAnimationFrame(ok));
        stage.frame(1 / 60);
      }
      stage.ctl.peacockAlpha = 1;
      stage.ctl.sareeAlpha = 1;

      tick = (_t, deltaMs) => stage.frame(Math.min(deltaMs, 50) / 1000);
      gsap.ticker.add(tick);
      api = { stage: stage, chor: chor };
      // the background stars hold still during the flight, leaving the computer more time for the peacock
      document.body.classList.add("pk-flying");
      setTimeout(() => document.body.classList.remove("pk-flying"), 3300);
      chor.flyIn();   // flows straight into idle life when it lands
    } catch (err) {
      console.warn("[peacock] falling back to the static logo:", err);
      useStaticLogo();
    }
  })();

  // ------------------------------------------------------------------ login --
  let status = "idle";   // idle | checking | error | success
  function setStatus(s) {
    status = s;
    const busy = s === "checking" || s === "success";
    inputs.forEach((i) => (i.disabled = busy));
    button.disabled = busy;
    form.classList.toggle("is-success", s === "success");
    if (s === "checking") button.innerHTML = '<span class="spinner" aria-label="Signing in"></span>';
    else button.textContent = s === "success" ? "Welcome!" : "Login";
  }
  function showError(text) {
    errorBox.textContent = text || " ";
    errorBox.classList.toggle("is-visible", !!text);
  }

  form.addEventListener("submit", async function (e) {
    e.preventDefault();
    if (status === "checking" || status === "success") return;
    const body = new URLSearchParams(new FormData(form));   // read before the inputs are disabled
    setStatus("checking");
    showError("");

    let res;
    try {
      // a correct login answers with a redirect, a wrong one with the login page again
      res = await fetch(form.action, { method: "POST", body: body, credentials: "same-origin", redirect: "manual" });
    } catch (err) {
      setStatus("idle");
      showError("Could not reach the billing app. Please try again.");
      return;
    }

    if (res.type !== "opaqueredirect" && !(res.status >= 300 && res.status < 400)) {
      const doc = new DOMParser().parseFromString(await res.text(), "text/html");
      const msg = ((doc.querySelector(".login-card .error") || {}).textContent || "").trim();
      setStatus("error");
      showError(msg || "Invalid username or password");
      gsap.timeline().fromTo(form, { x: 0 },
          { keyframes: { x: [0, -12, 10, -7, 4, 0] }, duration: 0.5, ease: "power1.out", clearProps: "transform" });
      if (api) api.chor.reactToError();
      return;
    }

    setStatus("success");
    const home = form.dataset.home || "/";
    const openDashboard = () => {
      try { sessionStorage.setItem("svt-from-login", "1"); } catch (err) { /* storage blocked */ }
      window.location.href = home;
    };
    const callbacks = {
      // as the peacock takes off, the login screen fades away behind it
      onLeave: () => gsap.to(page.querySelectorAll(".bg, .logo-plate, .form-col"), { opacity: 0, duration: 0.5, ease: "power1.inOut" }),
      onPeacockGone: openDashboard,
    };
    if (api) {
      api.chor.playSuccess(callbacks);
    } else {
      callbacks.onLeave();
      setTimeout(openDashboard, 700);
    }
  });
})();
