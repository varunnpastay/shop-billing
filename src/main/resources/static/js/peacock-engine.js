/* Animated peacock for the login page: the original peacock and saree from the shop logo, cut into layers,
   rigged to bones and drawn with WebGL; GSAP times the fly-in, the idle life, the error reaction and the fly-away.
   (Engine: WebGL helpers, bone rig, flight paths, stage renderer, choreography. Needs window.gsap.)
   Exposes window.SVTPeacock = { createPeacockStage, createChoreography }. */
(function () {
  "use strict";
  const gsap = window.gsap;

  // ===================================================================== gl.js
  // Small WebGL helpers (WebGL2 when available, WebGL1 fallback; GLSL ES 1.00 shaders work in both).

  function createContext(canvas) {
    // no anti-aliasing: the edges come from the soft image layers, so it looks the same and saves a lot of graphics work;
    // ask for the faster graphics chip on laptops that have two
    const opts = { alpha: true, premultipliedAlpha: true, antialias: false, depth: false, stencil: false,
                   powerPreference: "high-performance" };
    let gl = canvas.getContext('webgl2', opts);
    const isGL2 = !!gl;
    if (!gl) gl = canvas.getContext('webgl', opts) || canvas.getContext('experimental-webgl', opts);
    if (!gl) return null;
    gl.enable(gl.BLEND);
    gl.blendFunc(gl.ONE, gl.ONE_MINUS_SRC_ALPHA); // premultiplied alpha
    return { gl, isGL2 };
  }

  function shader(gl, type, src) {
    const s = gl.createShader(type);
    gl.shaderSource(s, src);
    gl.compileShader(s);
    if (!gl.getShaderParameter(s, gl.COMPILE_STATUS)) {
      const log = gl.getShaderInfoLog(s);
      gl.deleteShader(s);
      throw new Error('Shader compile failed: ' + log);
    }
    return s;
  }

  function createProgram(gl, vsSrc, fsSrc) {
    const p = gl.createProgram();
    gl.attachShader(p, shader(gl, gl.VERTEX_SHADER, vsSrc));
    gl.attachShader(p, shader(gl, gl.FRAGMENT_SHADER, fsSrc));
    gl.linkProgram(p);
    if (!gl.getProgramParameter(p, gl.LINK_STATUS)) throw new Error('Program link failed: ' + gl.getProgramInfoLog(p));
    const attribs = {};
    const uniforms = {};
    const na = gl.getProgramParameter(p, gl.ACTIVE_ATTRIBUTES);
    for (let i = 0; i < na; i++) {
      const a = gl.getActiveAttrib(p, i);
      attribs[a.name] = gl.getAttribLocation(p, a.name);
    }
    const nu = gl.getProgramParameter(p, gl.ACTIVE_UNIFORMS);
    for (let i = 0; i < nu; i++) {
      const u = gl.getActiveUniform(p, i);
      uniforms[u.name] = gl.getUniformLocation(p, u.name);
    }
    return { program: p, attribs, uniforms };
  }

  const imageCache = new Map();
  function loadImage(url) {
    if (!imageCache.has(url)) {
      imageCache.set(
        url,
        new Promise((resolve, reject) => {
          const img = new Image();
          img.decoding = 'async';
          img.onload = () => resolve(img);
          img.onerror = () => {
            imageCache.delete(url);
            reject(new Error('Could not load ' + url));
          };
          img.src = url;
        })
      );
    }
    return imageCache.get(url);
  }

  const isPow2 = (v) => (v & (v - 1)) === 0;

  function createTexture(gl, isGL2, img) {
    const tex = gl.createTexture();
    gl.bindTexture(gl.TEXTURE_2D, tex);
    gl.pixelStorei(gl.UNPACK_PREMULTIPLY_ALPHA_WEBGL, true);
    gl.texImage2D(gl.TEXTURE_2D, 0, gl.RGBA, gl.RGBA, gl.UNSIGNED_BYTE, img);
    gl.texParameteri(gl.TEXTURE_2D, gl.TEXTURE_WRAP_S, gl.CLAMP_TO_EDGE);
    gl.texParameteri(gl.TEXTURE_2D, gl.TEXTURE_WRAP_T, gl.CLAMP_TO_EDGE);
    gl.texParameteri(gl.TEXTURE_2D, gl.TEXTURE_MAG_FILTER, gl.LINEAR);
    if (isGL2 || (isPow2(img.width) && isPow2(img.height))) {
      gl.generateMipmap(gl.TEXTURE_2D);
      gl.texParameteri(gl.TEXTURE_2D, gl.TEXTURE_MIN_FILTER, gl.LINEAR_MIPMAP_LINEAR);
    } else {
      gl.texParameteri(gl.TEXTURE_2D, gl.TEXTURE_MIN_FILTER, gl.LINEAR);
    }
    return tex;
  }

  function makeBuffer(gl, data, target = gl.ARRAY_BUFFER, usage = gl.STATIC_DRAW) {
    const b = gl.createBuffer();
    gl.bindBuffer(target, b);
    gl.bufferData(target, data, usage);
    return b;
  }

  function bindAttrib(gl, loc, buffer, size) {
    if (loc === undefined || loc < 0) return;
    gl.bindBuffer(gl.ARRAY_BUFFER, buffer);
    gl.enableVertexAttribArray(loc);
    gl.vertexAttribPointer(loc, size, gl.FLOAT, false, 0, 0);
  }

  /** Resize a canvas to the viewport at device pixel ratio (capped). Returns true when changed. */
  function fitCanvas(canvas, gl, maxDpr = 2) {
    const dpr = Math.min(window.devicePixelRatio || 1, maxDpr);
    const w = Math.max(1, Math.round(window.innerWidth * dpr));
    const h = Math.max(1, Math.round(window.innerHeight * dpr));
    if (canvas.width !== w || canvas.height !== h) {
      canvas.width = w;
      canvas.height = h;
      gl.viewport(0, 0, w, h);
      return true;
    }
    return false;
  }

  /** Grid mesh (cols x rows cells) indices. */
  function gridIndices(cols, rows) {
    const idx = new Uint16Array(cols * rows * 6);
    let k = 0;
    for (let j = 0; j < rows; j++) {
      for (let i = 0; i < cols; i++) {
        const a = j * (cols + 1) + i;
        const b = a + 1;
        const c = a + cols + 2;
        const d = a + cols + 1;
        idx[k++] = a; idx[k++] = b; idx[k++] = c;
        idx[k++] = a; idx[k++] = c; idx[k++] = d;
      }
    }
    return idx;
  }

  const clamp = (v, a, b) => Math.min(b, Math.max(a, v));
  const smoothstep = (e0, e1, x) => {
    const t = clamp((x - e0) / (e1 - e0), 0, 1);
    return t * t * (3 - 2 * t);
  };
  const lerp = (a, b, t) => a + (b - a) * t;

  // ===================================================================== rig.js
  // 2D bone rig + linear-blend skinning for meshes cut from the original artwork.
  // Coordinates are in "source" pixels of the reference screenshot.

  const DEG = Math.PI / 180;

  function makeParams() {
    return { tx: 0, ty: 0, rot: 0, sx: 1, sy: 1 };
  }

  function createRig(rigData) {
    const bones = rigData.bones.map((b, i) => ({
      index: i,
      name: b.name,
      parent: b.parent,
      parentIndex: -1,
      px: b.pivot[0],
      py: b.pivot[1],
      // three additive channels: choreography (pose), idle actions (act), procedural (proc)
      pose: makeParams(),
      act: makeParams(),
      proc: makeParams(),
    }));
    const byName = {};
    bones.forEach((b) => (byName[b.name] = b));
    bones.forEach((b) => (b.parentIndex = b.parent ? byName[b.parent].index : -1));

    const world = new Float32Array(bones.length * 6);

    function update() {
      for (let i = 0; i < bones.length; i++) {
        const b = bones[i];
        const rot = (b.pose.rot + b.act.rot + b.proc.rot) * DEG;
        const sx = b.pose.sx * b.act.sx * b.proc.sx;
        const sy = b.pose.sy * b.act.sy * b.proc.sy;
        const tx = b.pose.tx + b.act.tx + b.proc.tx;
        const ty = b.pose.ty + b.act.ty + b.proc.ty;
        const c = Math.cos(rot);
        const s = Math.sin(rot);
        // local = T(p + t) * R * S * T(-p)
        const a = c * sx;
        const bb = s * sx;
        const cc = -s * sy;
        const d = c * sy;
        const e = b.px + tx - (a * b.px + cc * b.py);
        const f = b.py + ty - (bb * b.px + d * b.py);
        const o = i * 6;
        if (b.parentIndex < 0) {
          world[o] = a; world[o + 1] = bb; world[o + 2] = cc; world[o + 3] = d; world[o + 4] = e; world[o + 5] = f;
        } else {
          const p = b.parentIndex * 6;
          const pa = world[p], pb = world[p + 1], pc = world[p + 2], pd = world[p + 3], pe = world[p + 4], pf = world[p + 5];
          world[o] = pa * a + pc * bb;
          world[o + 1] = pb * a + pd * bb;
          world[o + 2] = pa * cc + pc * d;
          world[o + 3] = pb * cc + pd * d;
          world[o + 4] = pa * e + pc * f + pe;
          world[o + 5] = pb * e + pd * f + pf;
        }
      }
    }

    function resetChannel(channel) {
      bones.forEach((b) => Object.assign(b[channel], makeParams()));
    }

    return { bones, byName, world, update, resetChannel };
  }

  /** Prepare a skinned mesh from rig.json data (max 4 influences per vertex). */
  function prepareSkin(meshData, box, boneIndexOf = (i) => i) {
    const rest = Float32Array.from(meshData.pos);
    const n = rest.length / 2;
    const uv = new Float32Array(n * 2);
    const [x0, y0, x1, y1] = box;
    for (let i = 0; i < n; i++) {
      uv[i * 2] = (rest[i * 2] - x0) / (x1 - x0);
      uv[i * 2 + 1] = (rest[i * 2 + 1] - y0) / (y1 - y0);
    }
    const bi = new Uint8Array(n * 4);
    const bw = new Float32Array(n * 4);
    meshData.w.forEach((list, i) => {
      const top = list.slice().sort((p, q) => q[1] - p[1]).slice(0, 4);
      const tot = top.reduce((s, x) => s + x[1], 0) || 1;
      top.forEach(([b, w], k) => {
        bi[i * 4 + k] = boneIndexOf(b);
        bw[i * 4 + k] = w / tot;
      });
    });
    return { n, rest, uv, bi, bw, idx: Uint16Array.from(meshData.idx), out: new Float32Array(n * 2) };
  }

  /** Deform rest vertices by bone world matrices and map source px -> screen px. */
  function skin(mesh, world, map) {
    const { n, rest, bi, bw, out } = mesh;
    const { ox, oy, s } = map;
    for (let i = 0; i < n; i++) {
      const x = rest[i * 2];
      const y = rest[i * 2 + 1];
      let X = 0;
      let Y = 0;
      for (let k = 0; k < 4; k++) {
        const w = bw[i * 4 + k];
        if (w === 0) continue;
        const o = bi[i * 4 + k] * 6;
        X += w * (world[o] * x + world[o + 2] * y + world[o + 4]);
        Y += w * (world[o + 1] * x + world[o + 3] * y + world[o + 5]);
      }
      out[i * 2] = ox + X * s;
      out[i * 2 + 1] = oy + Y * s;
    }
    return out;
  }

  /** Transform a single source-space point by one bone. */
  function bonePoint(world, boneIndex, x, y) {
    const o = boneIndex * 6;
    return [world[o] * x + world[o + 2] * y + world[o + 4], world[o + 1] * x + world[o + 3] * y + world[o + 5]];
  }

  // ===================================================================== path.js
  // Smooth flight paths: Catmull-Rom spline through screen-space waypoints
  // {x, y, k} (k = depth scale), re-parameterised by arc length so that
  // tweening `s` from 0 to 1 gives an even, controllable speed.

  function cr(p0, p1, p2, p3, t) {
    const t2 = t * t;
    const t3 = t2 * t;
    return 0.5 * (2 * p1 + (-p0 + p2) * t + (2 * p0 - 5 * p1 + 4 * p2 - p3) * t2 + (-p0 + 3 * p1 - 3 * p2 + p3) * t3);
  }

  function buildPath(points, steps = 48) {
    const P = [points[0], ...points, points[points.length - 1]];
    const xs = [];
    const ys = [];
    const ks = [];
    for (let i = 1; i < P.length - 2; i++) {
      for (let j = 0; j < steps; j++) {
        const t = j / steps;
        xs.push(cr(P[i - 1].x, P[i].x, P[i + 1].x, P[i + 2].x, t));
        ys.push(cr(P[i - 1].y, P[i].y, P[i + 1].y, P[i + 2].y, t));
        ks.push(cr(P[i - 1].k, P[i].k, P[i + 1].k, P[i + 2].k, t));
      }
    }
    const last = points[points.length - 1];
    xs.push(last.x);
    ys.push(last.y);
    ks.push(last.k);
    const len = [0];
    for (let i = 1; i < xs.length; i++) len.push(len[i - 1] + Math.hypot(xs[i] - xs[i - 1], ys[i] - ys[i - 1]));
    const total = len[len.length - 1] || 1;
    return (s) => {
      const d = Math.min(1, Math.max(0, s)) * total;
      let lo = 0;
      let hi = len.length - 1;
      while (hi - lo > 1) {
        const mid = (lo + hi) >> 1;
        if (len[mid] < d) lo = mid;
        else hi = mid;
      }
      const seg = len[hi] - len[lo] || 1;
      const f = (d - len[lo]) / seg;
      return { x: xs[lo] + (xs[hi] - xs[lo]) * f, y: ys[lo] + (ys[hi] - ys[lo]) * f, k: ks[lo] + (ks[hi] - ks[lo]) * f };
    };
  }

  // ===================================================================== peacockStage.js
  // WebGL stage that renders the ORIGINAL peacock (rigged layers cut from the
  // reference artwork) and the original saree, positioned exactly over the logo plate.
  //
  // Cinematic extras (all built from the same original pixels):
  //  - spline flight controller with depth (scale) and pitch. The logo peacock's
  //    body faces LEFT (its train flows right, its head is turned back over its
  //    shoulder), so it always flies leftwards and never has to flip; in flight
  //    it simply turns its head forward.
  //  - a second feather layer on each wing (fans out when the wings open)
  //  - real motion blur: several sub-frame samples of the rig averaged per frame
  //  - the saree streams out of its back as it lands
  //  - gold dust particles

  const SAREE_GRID = { cols: 64, rows: 36 };
  const EYE = [376, 178];
  const ROOT_PIVOT = [330, 300];

  // Extra copies of the ORIGINAL wing feathers:
  //  - wingB / wingFarB: a second feather layer per wing (fans out when the wings open)
  //  - wingR: the folded wing that stays on the body as plumage while the flight wings beat
  const chain3 = (n, parent) => [
    { name: n, parent, pivot: [343, 238] },
    { name: n + 'Mid', parent: n, pivot: [388, 246] },
    { name: n + 'Tip', parent: n + 'Mid', pivot: [428, 252] },
  ];
  const EXTRA_BONES = [...chain3('wingB', 'breathe'), ...chain3('wingFarB', 'breathe'), ...chain3('wingR', 'breathe')];
  const remapWing = (from, to) => ({ [from]: to, [from + 'Mid']: to + 'Mid', [from + 'Tip']: to + 'Tip' });

  const VS = `
  attribute vec2 aPos;
  attribute vec2 aUv;
  attribute vec2 aRest;
  attribute float aShade;
  uniform vec2 uView;
  varying vec2 vUv;
  varying vec2 vRest;
  varying float vShade;
  void main() {
    vUv = aUv; vRest = aRest; vShade = aShade;
    vec2 c = aPos / uView * 2.0 - 1.0;
    gl_Position = vec4(c.x, -c.y, 0.0, 1.0);
  }`;

  const FS = `
  precision mediump float;
  uniform sampler2D uTex;
  uniform float uAlpha;
  uniform float uTint;
  uniform float uSweep;
  uniform float uShimmer;
  uniform float uReveal;
  uniform float uRevealSoft;
  uniform float uGlint;
  uniform vec2 uEye;
  varying vec2 vUv;
  varying vec2 vRest;
  varying float vShade;
  void main() {
    vec4 c = texture2D(uTex, vUv);
    float gold = clamp((c.r - c.b) * 1.8 - 0.15 * c.a, 0.0, 1.0);
    c.rgb *= 1.0 + vShade;
    c.rgb += gold * max(vShade, 0.0) * 1.2 * vec3(1.0, 0.8, 0.4);
    float d = (vRest.x + vRest.y * 0.6) - uSweep;
    c.rgb += exp(-d * d / 160.0) * uShimmer * gold * vec3(1.0, 0.88, 0.6);
    vec2 e = vRest - uEye;
    c.rgb += uGlint * exp(-dot(e, e) / 2.2) * c.a;
    c.rgb *= uTint;
    float rv = 1.0 - smoothstep(uReveal - uRevealSoft, uReveal, vUv.x);
    gl_FragColor = c * (uAlpha * rv);
  }`;

  const COPY_VS = `
  attribute vec2 aPos;
  uniform vec2 uView;
  varying vec2 vUv;
  void main() {
    vUv = vec2(aPos.x / uView.x, 1.0 - aPos.y / uView.y);
    vec2 c = aPos / uView * 2.0 - 1.0;
    gl_Position = vec4(c.x, -c.y, 0.0, 1.0);
  }`;
  const COPY_FS = `
  precision mediump float;
  uniform sampler2D uTex;
  varying vec2 vUv;
  void main() { gl_FragColor = texture2D(uTex, vUv); }`;

  const PART_VS = `
  attribute vec2 aPos;
  attribute vec4 aCol;
  attribute float aSize;
  uniform vec2 uView;
  uniform float uDpr;
  varying vec4 vCol;
  void main() {
    vCol = aCol;
    vec2 c = aPos / uView * 2.0 - 1.0;
    gl_Position = vec4(c.x, -c.y, 0.0, 1.0);
    gl_PointSize = aSize * uDpr;
  }`;
  const PART_FS = `
  precision mediump float;
  varying vec4 vCol;
  void main() {
    float r = length(gl_PointCoord - 0.5) * 2.0;
    float a = smoothstep(1.0, 0.0, r);
    a *= a;
    float core = smoothstep(0.35, 0.0, r);
    gl_FragColor = vec4(vCol.rgb * (a + core), (a + core * 0.5) * 0.55) * vCol.a;
  }`;

  const MAX_PARTICLES = 700;

  // assets = { rigData, body, wing, saree } (image URLs come from the page so they get cache-busted URLs)
  async function createPeacockStage(canvas, getPlateRect, assets) {
    const rigData = assets.rigData;
    const PLATE_BOX = rigData.layers.plate.box; // [x0, y0, x1, y1] in source px
    const SAREE_BOX = rigData.layers.saree.box;
    const ctx = createContext(canvas);
    if (!ctx) throw new Error('WebGL unavailable');
    const { gl, isGL2 } = ctx;
    const prog = createProgram(gl, VS, FS);
    const copyProg = createProgram(gl, COPY_VS, COPY_FS);
    const partProg = createProgram(gl, PART_VS, PART_FS);
    const [bodyImg, wingImg, sareeImg] = await Promise.all([assets.body, assets.wing, assets.saree].map(loadImage));

    const rig = createRig({ bones: [...rigData.bones, ...EXTRA_BONES] });
    const B = rig.byName;
    const L = rigData.layers;

    // ---- skinned peacock layers ---------------------------------------------
    const bodyTex = createTexture(gl, isGL2, bodyImg);
    const wingTex = createTexture(gl, isGL2, wingImg);
    function gpuSkin(meshData, box, tex, remap) {
      const m = prepareSkin(meshData, box, remap);
      return {
        ...m,
        tex,
        posBuf: makeBuffer(gl, m.out, gl.ARRAY_BUFFER, gl.DYNAMIC_DRAW),
        uvBuf: makeBuffer(gl, m.uv),
        restBuf: makeBuffer(gl, m.rest),
        idxBuf: makeBuffer(gl, m.idx, gl.ELEMENT_ARRAY_BUFFER),
        count: m.idx.length,
      };
    }
    const remapTo = (map) => (i) => {
      const n = rig.bones[i].name;
      return map[n] !== undefined ? B[map[n]].index : i;
    };
    const body = gpuSkin(rigData.meshes.body, L.body.box, bodyTex);
    const wing = gpuSkin(rigData.meshes.wing, L.wing.box, wingTex);
    const wingFar = gpuSkin(rigData.meshes.wingFar, L.wing.box, wingTex);
    const wingB = gpuSkin(rigData.meshes.wing, L.wing.box, wingTex, remapTo(remapWing('wing', 'wingB')));
    const wingFarB = gpuSkin(rigData.meshes.wingFar, L.wing.box, wingTex, remapTo(remapWing('wingFar', 'wingFarB')));
    const wingR = gpuSkin(rigData.meshes.wing, L.wing.box, wingTex, remapTo(remapWing('wing', 'wingR')));
    // shaded under-feathers that stay on the body, so a lifted wing never leaves a hole
    const wingBase = gpuSkin(rigData.meshes.wing, L.wing.box, wingTex, remapTo({ wing: 'breathe', wingMid: 'breathe', wingTip: 'breathe' }));

    // ---- original saree as a regular cloth grid ------------------------------
    const { cols, rows } = SAREE_GRID;
    const nS = (cols + 1) * (rows + 1);
    const [sx0, sy0, sx1, sy1] = SAREE_BOX;
    const sRest = new Float32Array(nS * 2);
    const sUv = new Float32Array(nS * 2);
    for (let j = 0; j <= rows; j++) {
      for (let i = 0; i <= cols; i++) {
        const k = j * (cols + 1) + i;
        sUv[k * 2] = i / cols;
        sUv[k * 2 + 1] = j / rows;
        sRest[k * 2] = sx0 + (sx1 - sx0) * (i / cols);
        sRest[k * 2 + 1] = sy0 + (sy1 - sy0) * (j / rows);
      }
    }
    const saree = {
      n: nS, rest: sRest, uv: sUv,
      out: new Float32Array(nS * 2),
      shade: new Float32Array(nS),
      idx: gridIndices(cols, rows),
    };
    saree.tex = createTexture(gl, isGL2, sareeImg);
    saree.posBuf = makeBuffer(gl, saree.out, gl.ARRAY_BUFFER, gl.DYNAMIC_DRAW);
    saree.shadeBuf = makeBuffer(gl, saree.shade, gl.ARRAY_BUFFER, gl.DYNAMIC_DRAW);
    saree.uvBuf = makeBuffer(gl, sUv);
    saree.restBuf = makeBuffer(gl, sRest);
    saree.idxBuf = makeBuffer(gl, saree.idx, gl.ELEMENT_ARRAY_BUFFER);
    saree.count = saree.idx.length;


    // ---- particles ------------------------------------------------------------
    const P = {
      n: 0,
      x: new Float32Array(MAX_PARTICLES), y: new Float32Array(MAX_PARTICLES),
      vx: new Float32Array(MAX_PARTICLES), vy: new Float32Array(MAX_PARTICLES),
      life: new Float32Array(MAX_PARTICLES), max: new Float32Array(MAX_PARTICLES),
      size: new Float32Array(MAX_PARTICLES), hue: new Float32Array(MAX_PARTICLES), tw: new Float32Array(MAX_PARTICLES),
      pos: new Float32Array(MAX_PARTICLES * 2), col: new Float32Array(MAX_PARTICLES * 4), sz: new Float32Array(MAX_PARTICLES),
    };
    P.posBuf = makeBuffer(gl, P.pos, gl.ARRAY_BUFFER, gl.DYNAMIC_DRAW);
    P.colBuf = makeBuffer(gl, P.col, gl.ARRAY_BUFFER, gl.DYNAMIC_DRAW);
    P.szBuf = makeBuffer(gl, P.sz, gl.ARRAY_BUFFER, gl.DYNAMIC_DRAW);
    const quadBuf = makeBuffer(gl, new Float32Array(12), gl.ARRAY_BUFFER, gl.DYNAMIC_DRAW);

    // ---- control state (tweened by GSAP choreography) ------------------------
    const ctl = {
      ambient: 0, flap: 0, flapHz: 2.4, spread: 0, look: 0, farWing: 0, glint: 0,
      stretch: 0, // flight posture: neck stretched forward, crest blown back, plumes trailing
      headFwd: 0, // 0 = head turned back as in the logo, 1 = looking forward (left) in flight
      shimmer: 0, sweep: -1000,
      peacockAlpha: 1, sareeAlpha: 1, sareeReveal: 1.2, sareeFlutter: 1, sareeBoost: 0,
      sparkle: 1, // idle gold-dust amount
      flightFx: 0, // particle trail while flying
      blur: 1, // motion-blur strength
    };
    // flight controller: when active it drives the root bone along a screen-space spline
    const flight = { active: false, s: 0, build: null, hover: 0, key: '', path: null, start: null };

    let flapPhase = 0;
    let time = 0;
    const vel = { x: 0, y: 0 };
    let lastFlightPos = null;
    let map = { ox: 0, oy: 0, s: 1 };
    let haveMap = false;
    const prevRoot = { tx: 0, ty: 0, rot: 0, sx: 1, sy: 1 };
    let prevPhase = 0;
    let firstFrame = true;
    let fbo = null;
    let burstQueue = [];

    function updateMap() {
      const r = getPlateRect();
      if (!r || r.width === 0) return haveMap; // keep the last known placement
      const s = r.width / (PLATE_BOX[2] - PLATE_BOX[0]);
      map = { ox: r.left - PLATE_BOX[0] * s, oy: r.top - PLATE_BOX[1] * s, s };
      haveMap = true;
      return true;
    }
    const toScreen = (x, y) => [map.ox + x * map.s, map.oy + y * map.s];
    const perch = () => toScreen(ROOT_PIVOT[0], ROOT_PIVOT[1]);

    // ---- flight -----------------------------------------------------------------
    function flightUpdate(dt) {
      if (!flight.active || !flight.build) return;
      const vw = window.innerWidth;
      const vh = window.innerHeight;
      const [px, py] = perch();
      const key = `${vw}|${vh}|${px.toFixed(1)}|${py.toFixed(1)}`;
      if (key !== flight.key) {
        flight.path = buildPath(flight.build(vw, vh, px, py, map.s, flight.start));
        flight.key = key;
      }
      const p = flight.path(clamp(flight.s, 0, 1));
      if (lastFlightPos && dt > 0) {
        const a = Math.min(1, dt * 8);
        vel.x += ((p.x - lastFlightPos.x) / dt - vel.x) * a;
        vel.y += ((p.y - lastFlightPos.y) / dt - vel.y) * a;
      }
      lastFlightPos = { x: p.x, y: p.y };
      // The peacock always flies the way its body faces (to the left), so it never
      // has to flip. Pitch follows the flight direction: nose down in a descent.
      const pitch = -clamp(Math.atan2(vel.y, Math.abs(vel.x) + 90) * 57.2958 * 0.45, -20, 22);
      const R = B.root.pose;
      R.tx = (p.x - map.ox) / map.s - ROOT_PIVOT[0];
      R.ty = (p.y - map.oy) / map.s - ROOT_PIVOT[1];
      R.sx = p.k;
      R.sy = p.k;
      R.rot = pitch * (1 - flight.hover);
    }

    /** Current on-screen position of the bird's pivot. */
    function birdScreenPos() {
      const R = B.root.pose;
      const [x, y] = toScreen(ROOT_PIVOT[0] + R.tx, ROOT_PIVOT[1] + R.ty);
      return { x, y, k: Math.abs(R.sy) || 1 };
    }

    function startFlight(build) {
      flight.start = birdScreenPos();
      flight.build = build;
      flight.key = '';
      flight.s = 0;
      flight.active = true;
      flight.hover = 0;
      lastFlightPos = null;
      vel.x = vel.y = 0;
      flightUpdate(0);
      Object.assign(prevRoot, B.root.pose); // no motion-blur smear across a cut
    }
    function endFlight() {
      flight.active = false;
      Object.assign(B.root.pose, { tx: 0, ty: 0, rot: 0, sx: 1, sy: 1 });
    }

    // ---- procedural layer (ambient life, wing beats, display) --------------------
    // Faster, powerful downstroke and a slower recovery stroke.
    const warp = (x) => x + 0.32 * Math.sin(x);
    function procedural(t, phase) {
      rig.resetChannel('proc');
      const A = ctl.ambient;
      const fa = ctl.flap;
      const sp = ctl.spread;
      const st = ctl.stretch;
      const S = Math.sin;
      const open = Math.min(1, Math.max(fa, sp));
      const pw = warp(phase);

      // ---- always-on life: breathing, weight shift, S-neck sway, crest, plumes
      B.breathe.proc.sy = 1 + 0.016 * A * S(t * 1.85);
      B.breathe.proc.sx = 1 - 0.005 * A * S(t * 1.85);
      B.breathe.proc.rot = A * (0.8 * S(t * 0.9 + 0.4) + 0.35 * S(t * 2.3));
      B.root.proc.rot = A * 0.35 * S(t * 0.55 + 1.2);
      B.neck.proc.rot = A * (1.4 * S(t * 0.75 + 1.1) + 0.5 * S(t * 1.9));
      B.neck2.proc.rot = A * (1.7 * S(t * 0.75 + 0.3) + 0.6 * S(t * 2.2 + 1.0));
      B.head.proc.rot = A * (2.4 * S(t * 1.1 + 0.3) + 0.9 * S(t * 2.7) + 0.4 * S(t * 4.3));
      B.head.proc.ty = A * 0.6 * S(t * 1.6 + 0.5);
      for (let k = 1; k <= 4; k++) {
        B['crest' + k].proc.rot = A * (3.6 * S(t * 2.3 + k * 0.8) + 1.4 * S(t * 5.3 + k * 1.7));
      }
      const tails = [B.tail, B.tail2, B.tail3, B.tail4];
      tails.forEach((b, i) => {
        b.proc.rot = A * ((0.9 + 0.35 * i) * S(t * 1.3 - 0.7 * i) + 0.4 * S(t * 3.1 - i));
      });
      const flo = [B.flourish, B.flourish2, B.flourish3];
      flo.forEach((b, i) => {
        b.proc.rot = A * (1.2 + 0.6 * i) * S(t * 0.95 + 2.0 - 0.8 * i);
      });
      B.sweep.proc.rot = A * 1.2 * S(t * 1.1 + 0.7);
      B.tealLow.proc.rot = A * 3.4 * S(t * 1.7 + 0.6);
      B.wingR.proc.rot = A * 1.1 * S(t * 1.85 + 0.2);
      B.wingRMid.proc.rot = A * 1.2 * S(t * 1.6 + 0.5);
      B.wingRTip.proc.rot = A * 1.6 * S(t * 1.6 + 0.8);

      // ---- head turned forward (left) for flight: a quick real head turn
      const hfE = ctl.headFwd * ctl.headFwd * (3 - 2 * ctl.headFwd);
      const hc = Math.cos(Math.PI * hfE);
      const hs = (hc >= 0 ? 1 : -1) * Math.max(Math.abs(hc), 0.3);
      const turning = Math.sin(Math.PI * hfE);
      B.head.proc.sx *= hs;
      B.head.proc.rot += -7 * turning;
      B.neck2.proc.rot += -5 * hfE;
      const hsgn = hs >= 0 ? 1 : -1; // crest rotations are mirrored with the head

      // ---- flight body: rises on each downstroke, pitches, the neck absorbs it
      B.root.proc.ty += -fa * 4.2 * S(pw);
      B.breathe.proc.rot += -fa * 2.2 * S(pw + 0.6);
      B.neck.proc.rot += -st * 22 + fa * 1.0 * S(pw + 0.6);
      B.neck2.proc.rot += -st * 10 + fa * 1.4 * S(pw + 0.9);
      B.head.proc.rot += st * 28 + fa * 0.9 * S(pw + 0.6); // head stays level while the body bobs
      for (let k = 1; k <= 4; k++) {
        B['crest' + k].proc.rot += hsgn * (fa * 6 * S(pw - 1.3 - 0.3 * k) + st * (8 + 2.5 * k));
      }
      // the train streams out behind (to the right) and ripples
      tails.forEach((b, i) => {
        b.proc.rot += fa * (2.6 + 1.6 * i) * S(pw - 0.9 - 0.75 * i) + fa * 1.2 * S(t * 11 - i * 1.3) - st * (2 + 1.2 * i);
      });
      flo.forEach((b, i) => {
        b.proc.rot += fa * (3 + 2 * i) * S(pw - 1.2 - 0.8 * i) - st * (2 + i);
      });
      B.sweep.proc.rot += fa * 2.2 * S(pw - 1.0);
      B.tealLow.proc.rot += fa * 6 * S(pw - 0.8);
      B.wingR.proc.rot += fa * 3 * S(pw - 0.5); // body plumage ruffles with each beat

      // ---- display (spread) posture
      B.tail.proc.rot += sp * -3;
      B.crest1.proc.rot += hsgn * sp * -9;
      B.crest2.proc.rot += hsgn * sp * -4;
      B.crest3.proc.rot += hsgn * sp * 3;
      B.crest4.proc.rot += hsgn * sp * 8;

      // ---- wings, seen from the side as on a real bird: they beat UP and DOWN
      // around the shoulder and are foreshortened as they sweep toward the camera
      // mid-stroke. Outer feathers lag behind (flexible wing). No mirror flips.
      const wings = [
        [B.wing, B.wingMid, B.wingTip, 0, 0],
        [B.wingB, B.wingBMid, B.wingBTip, 0, 1],
        [B.wingFar, B.wingFarMid, B.wingFarTip, 1, 0],
        [B.wingFarB, B.wingFarBMid, B.wingFarBTip, 1, 1],
      ];
      const hold = sp * (1 - fa); // display / flare: wings held up
      for (const [w, mid, tip, far, extra] of wings) {
        const lag = far * 0.3 + extra * 0.14;
        const ph = warp(phase - lag);
        const c = Math.cos(ph); // +1 top of the stroke, -1 bottom
        const sn = Math.sin(ph);
        // top: up and slightly back (right); bottom: down and forward (left)
        const beat = 10 - 88 * c + far * 12;
        const held = -86 + far * 16;
        let rot = fa * beat + hold * held + (open - fa - hold) * -50;
        rot += open * extra * (far ? 12 : -13); // second feather layer fans out
        w.proc.rot += rot;
        // flight wings root on the back of the body (behind the head)
        w.proc.tx += (10 + far * 4) * open;
        w.proc.ty += 4 * open;
        const fore = 0.32 + 0.68 * Math.pow(Math.abs(c), 0.7); // foreshortening mid-stroke
        const len = 1 + 0.3 * open;
        w.proc.sx *= len * (1 - fa * (1 - fore));
        w.proc.sy *= 1 + 0.12 * open;
        mid.proc.rot += fa * 18 * sn + hold * 8 + open * extra * 5;
        tip.proc.rot += fa * 16 * Math.sin(ph - 0.5) + hold * 12 + open * extra * 5;
      }

      // ---- look toward the viewer (2.5D head turn)
      const lk = ctl.look;
      B.head.proc.sx *= 1 - 0.17 * lk;
      B.head.proc.rot += -5 * lk;
      B.head.proc.tx += -1.5 * lk;
      B.neck2.proc.rot += -2.5 * lk;
      B.crest1.proc.rot += -5 * lk;
      B.crest4.proc.rot += 4 * lk;
    }

    // ---- saree ----------------------------------------------------------------------
    function deformSaree() {
      const { rest, uv, out, shade, n } = saree;
      const t = time;
      const r = ctl.sareeReveal;
      const fl = ctl.sareeFlutter;
      const boost = ctl.sareeBoost;
      for (let k = 0; k < n; k++) {
        const x = rest[k * 2];
        const y = rest[k * 2 + 1];
        const u = uv[k * 2];
        const free = smoothstep(560, 900, x);
        const A = (3.4 * free + 2.3 * smoothstep(330, 405, y) * smoothstep(780, 925, x)) * fl + boost * (0.4 + free) * 6;
        const ph = x * 0.042 + y * 0.018 - t * 2.3;
        let dy = A * (0.72 * Math.sin(ph) + 0.28 * Math.sin(ph * 1.93 + 1.3));
        let dx = A * 0.32 * Math.sin(ph * 0.8 + 2.1);
        let sh = A * 0.045 * Math.cos(ph);
        if (r < 1.1) {
          const q = (u - r) / 0.07;
          const g = Math.exp(-q * q) * Math.max(0, 1 - r * 0.85);
          dy -= g * 9;
          dx -= g * 5;
          sh += g * 0.35;
        }
        out[k * 2] = map.ox + (x + dx) * map.s;
        out[k * 2 + 1] = map.oy + (y + dy) * map.s;
        shade[k] = sh;
      }
    }

    // ---- particles ------------------------------------------------------------------
    function spawn(x, y, vx, vy, life, size, hue) {
      if (P.n >= MAX_PARTICLES) return;
      const i = P.n++;
      P.x[i] = x; P.y[i] = y; P.vx[i] = vx; P.vy[i] = vy;
      P.life[i] = life; P.max[i] = life; P.size[i] = size; P.hue[i] = hue; P.tw[i] = Math.random() * 6.28;
    }
    function burst(x, y, count, speed) {
      burstQueue.push([x, y, count, speed]);
    }
    function updateParticles(dt, emitters, speed) {
      // emission
      const rate = ctl.flightFx * (30 + speed * 0.07) + ctl.ambient * ctl.sparkle * 2.2;
      let toEmit = rate * dt;
      while (toEmit > 0) {
        if (Math.random() < toEmit) {
          const e = emitters[(Math.random() * emitters.length) | 0];
          const flying = ctl.flightFx > 0.2;
          spawn(
            e[0] + (Math.random() - 0.5) * 10, e[1] + (Math.random() - 0.5) * 10,
            -vel.x * 0.12 + (Math.random() - 0.5) * 40, -vel.y * 0.12 + (Math.random() - 0.5) * 40 - 10,
            flying ? 0.7 + Math.random() * 0.9 : 1.2 + Math.random() * 1.2,
            (flying ? 2.5 : 1.8) + Math.random() * (flying ? 5 : 3) * map.s,
            Math.random() < 0.14 ? 1 : 0
          );
        }
        toEmit -= 1;
      }
      for (const [bx, by, count, sp] of burstQueue) {
        for (let i = 0; i < count; i++) {
          const a = Math.random() * Math.PI * 2;
          const v = sp * (0.3 + Math.random());
          spawn(bx, by, Math.cos(a) * v, Math.sin(a) * v * 0.7 - 20, 0.6 + Math.random() * 0.9, 2 + Math.random() * 5, Math.random() < 0.2 ? 1 : 0);
        }
      }
      burstQueue = [];
      // integrate + compact
      let w = 0;
      const drag = Math.pow(0.4, dt);
      for (let i = 0; i < P.n; i++) {
        const life = P.life[i] - dt;
        if (life <= 0) continue;
        P.vx[i] *= drag;
        P.vy[i] = P.vy[i] * drag + 18 * dt;
        P.x[w] = P.x[i] + P.vx[i] * dt;
        P.y[w] = P.y[i] + P.vy[i] * dt;
        P.vx[w] = P.vx[i]; P.vy[w] = P.vy[i];
        P.life[w] = life; P.max[w] = P.max[i]; P.size[w] = P.size[i]; P.hue[w] = P.hue[i]; P.tw[w] = P.tw[i];
        const age = 1 - life / P.max[w];
        const a = smoothstep(0, 0.12, age) * (1 - smoothstep(0.55, 1, age)) * (0.65 + 0.35 * Math.sin(time * 14 + P.tw[w]));
        P.pos[w * 2] = P.x[w];
        P.pos[w * 2 + 1] = P.y[w];
        if (P.hue[w] > 0.5) {
          P.col[w * 4] = 1.0; P.col[w * 4 + 1] = 0.45; P.col[w * 4 + 2] = 0.7;
        } else {
          P.col[w * 4] = 1.0; P.col[w * 4 + 1] = 0.8 + 0.12 * Math.sin(P.tw[w]); P.col[w * 4 + 2] = 0.42;
        }
        P.col[w * 4 + 3] = a;
        P.sz[w] = P.size[w] * (1 - age * 0.4);
        w++;
      }
      P.n = w;
    }

    // ---- drawing --------------------------------------------------------------------
    function drawMesh(m, opts) {
      const { attribs: a, uniforms: u } = prog;
      gl.bindTexture(gl.TEXTURE_2D, m.tex);
      gl.bindBuffer(gl.ARRAY_BUFFER, m.posBuf);
      gl.bufferSubData(gl.ARRAY_BUFFER, 0, m.out);
      bindAttrib(gl, a.aPos, m.posBuf, 2);
      bindAttrib(gl, a.aUv, m.uvBuf, 2);
      bindAttrib(gl, a.aRest, m.restBuf, 2);
      if (m.shadeBuf) {
        gl.bindBuffer(gl.ARRAY_BUFFER, m.shadeBuf);
        gl.bufferSubData(gl.ARRAY_BUFFER, 0, m.shade);
        bindAttrib(gl, a.aShade, m.shadeBuf, 1);
      } else if (a.aShade !== undefined) {
        gl.disableVertexAttribArray(a.aShade);
        gl.vertexAttrib1f(a.aShade, 0);
      }
      gl.uniform1f(u.uAlpha, opts.alpha);
      gl.uniform1f(u.uTint, opts.tint ?? 1);
      gl.uniform1f(u.uReveal, opts.reveal ?? 3);
      gl.uniform1f(u.uRevealSoft, 0.06);
      gl.uniform1f(u.uGlint, opts.glint ?? 0);
      gl.uniform1f(u.uShimmer, opts.shimmer ?? 0);
      gl.bindBuffer(gl.ELEMENT_ARRAY_BUFFER, m.idxBuf);
      gl.drawElements(gl.TRIANGLES, m.count, gl.UNSIGNED_SHORT, 0);
    }

    const maxAttribs = Math.min(8, gl.getParameter(gl.MAX_VERTEX_ATTRIBS));
    function resetAttribs() {
      for (let i = 0; i < maxAttribs; i++) gl.disableVertexAttribArray(i);
    }
    function useLayerProgram() {
      resetAttribs();
      gl.useProgram(prog.program);
      gl.uniform2f(prog.uniforms.uView, window.innerWidth, window.innerHeight);
      gl.uniform1i(prog.uniforms.uTex, 0);
      gl.uniform1f(prog.uniforms.uSweep, ctl.sweep);
      gl.uniform2f(prog.uniforms.uEye, EYE[0], EYE[1]);
      gl.activeTexture(gl.TEXTURE0);
    }

    function skinAll() {
      skin(body, rig.world, map);
      skin(wing, rig.world, map);
      skin(wingFar, rig.world, map);
      skin(wingB, rig.world, map);
      skin(wingFarB, rig.world, map);
      skin(wingR, rig.world, map);
      skin(wingBase, rig.world, map);
    }

    function drawPeacock() {
      const pa = ctl.peacockAlpha;
      const open = Math.min(1, Math.max(ctl.flap, ctl.spread));
      const fw = ctl.farWing * Math.min(1, open * 4);
      if (fw > 0.002) {
        drawMesh(wingFarB, { alpha: pa * fw, tint: 0.46 });
        drawMesh(wingFar, { alpha: pa * fw, tint: 0.58 });
      }
      drawMesh(body, { alpha: pa, glint: ctl.glint, shimmer: ctl.shimmer });
      const lifted = Math.abs(B.wingR.act.rot) + Math.abs(B.wingRMid.act.rot) + Math.abs(B.wingRTip.act.rot) + ctl.flap * 6;
      const ba = Math.min(1, lifted / 3);
      if (ba > 0.002) drawMesh(wingBase, { alpha: pa * ba, tint: 0.72 });
      drawMesh(wingR, { alpha: pa, shimmer: ctl.shimmer });
      const na = Math.min(1, open * 5);
      if (na > 0.002) {
        drawMesh(wingB, { alpha: pa * na, tint: 0.84 });
        drawMesh(wing, { alpha: pa * na, shimmer: ctl.shimmer });
      }
    }

    function makeTarget(w, h) {
      const tex = gl.createTexture();
      gl.bindTexture(gl.TEXTURE_2D, tex);
      gl.texImage2D(gl.TEXTURE_2D, 0, gl.RGBA, w, h, 0, gl.RGBA, gl.UNSIGNED_BYTE, null);
      gl.texParameteri(gl.TEXTURE_2D, gl.TEXTURE_MIN_FILTER, gl.NEAREST);
      gl.texParameteri(gl.TEXTURE_2D, gl.TEXTURE_MAG_FILTER, gl.NEAREST);
      gl.texParameteri(gl.TEXTURE_2D, gl.TEXTURE_WRAP_S, gl.CLAMP_TO_EDGE);
      gl.texParameteri(gl.TEXTURE_2D, gl.TEXTURE_WRAP_T, gl.CLAMP_TO_EDGE);
      const fb = gl.createFramebuffer();
      gl.bindFramebuffer(gl.FRAMEBUFFER, fb);
      gl.framebufferTexture2D(gl.FRAMEBUFFER, gl.COLOR_ATTACHMENT0, gl.TEXTURE_2D, tex, 0);
      const ok = gl.checkFramebufferStatus(gl.FRAMEBUFFER) === gl.FRAMEBUFFER_COMPLETE;
      gl.bindFramebuffer(gl.FRAMEBUFFER, null);
      return ok ? { tex, fb } : null;
    }
    function ensureFbo() {
      if (fbo && fbo.w === canvas.width && fbo.h === canvas.height) return fbo;
      if (fbo) {
        [fbo.a, fbo.acc].forEach((t) => { gl.deleteTexture(t.tex); gl.deleteFramebuffer(t.fb); });
        fbo = null;
      }
      const a = makeTarget(canvas.width, canvas.height);
      const acc = makeTarget(canvas.width, canvas.height);
      if (!a || !acc) return null;
      fbo = { w: canvas.width, h: canvas.height, a, acc };
      return fbo;
    }

    function drawQuad(texture, x0, y0, x1, y1) {
      resetAttribs();
      gl.useProgram(copyProg.program);
      gl.uniform2f(copyProg.uniforms.uView, window.innerWidth, window.innerHeight);
      gl.uniform1i(copyProg.uniforms.uTex, 0);
      gl.activeTexture(gl.TEXTURE0);
      gl.bindTexture(gl.TEXTURE_2D, texture);
      gl.bindBuffer(gl.ARRAY_BUFFER, quadBuf);
      gl.bufferSubData(gl.ARRAY_BUFFER, 0, new Float32Array([x0, y0, x1, y0, x0, y1, x0, y1, x1, y0, x1, y1]));
      bindAttrib(gl, copyProg.attribs.aPos, quadBuf, 2);
      gl.drawArrays(gl.TRIANGLES, 0, 6);
    }

    // screen bounds of the bird for the current rig pose (css px)
    const BOUNDS_SRC = [[130, 30], [650, 30], [130, 460], [650, 460]];
    function boundsInto(bb) {
      for (const [x, y] of BOUNDS_SRC) {
        const [wx, wy] = bonePoint(rig.world, 0, x, y);
        const sx = map.ox + wx * map.s;
        const sy = map.oy + wy * map.s;
        bb[0] = Math.min(bb[0], sx); bb[1] = Math.min(bb[1], sy);
        bb[2] = Math.max(bb[2], sx); bb[3] = Math.max(bb[3], sy);
      }
    }

    function drawParticles() {
      if (!P.n) return;
      const { attribs: a, uniforms: u } = partProg;
      resetAttribs();
      gl.useProgram(partProg.program);
      gl.uniform2f(u.uView, window.innerWidth, window.innerHeight);
      gl.uniform1f(u.uDpr, canvas.width / window.innerWidth);
      const up = (buf, data, size) => {
        gl.bindBuffer(gl.ARRAY_BUFFER, buf);
        gl.bufferSubData(gl.ARRAY_BUFFER, 0, data.subarray(0, P.n * size));
      };
      up(P.posBuf, P.pos, 2);
      up(P.colBuf, P.col, 4);
      up(P.szBuf, P.sz, 1);
      bindAttrib(gl, a.aPos, P.posBuf, 2);
      bindAttrib(gl, a.aCol, P.colBuf, 4);
      bindAttrib(gl, a.aSize, P.szBuf, 1);
      gl.drawArrays(gl.POINTS, 0, P.n);
    }

    let hidden = false;
    // render quality: lowered automatically on slow computers (see setQuality / peacock-login.js)
    let maxDpr = 1;
    let maxBlurSamples = 1;   // 1 = no motion blur (smoothest); setQuality can raise it
    function frame(dtIn) {
      const dt = Math.max(0, dtIn);
      time += dt;
      fitCanvas(canvas, gl, maxDpr);
      gl.bindFramebuffer(gl.FRAMEBUFFER, null);
      gl.clearColor(0, 0, 0, 0);
      gl.clear(gl.COLOR_BUFFER_BIT);
      if (hidden || !updateMap()) return;

      // flight + current pose
      flightUpdate(dt);
      const phase0 = prevPhase;
      if (ctl.flap > 0.001) flapPhase += dt * ctl.flapHz * Math.PI * 2;
      const cur = { ...B.root.pose };
      if (firstFrame) {
        Object.assign(prevRoot, cur);
        firstFrame = false;
      }
      procedural(time, flapPhase);
      rig.update();

      // emitters (current pose)
      const W = rig.world;
      const scr = (bone, x, y) => {
        const [wx, wy] = bonePoint(W, B[bone].index, x, y);
        return [map.ox + wx * map.s, map.oy + wy * map.s];
      };
      const emitters = [scr('tail4', 560, 300), scr('crest3', 360, 136), scr('breathe', 345, 270), scr('flourish3', 266, 322)];
      if (ctl.flap > 0.2 || ctl.spread > 0.2) emitters.push(scr('wingTip', 452, 252), scr('wingBTip', 452, 252));
      else emitters.push(scr('wingRTip', 452, 252));

      // saree: settled logo pose, optionally blended with the streaming ribbon
      if (ctl.sareeAlpha > 0.001 && ctl.sareeReveal > 0) deformSaree();

      // motion-blur sample count from how far the bird moved this frame
      const k = Math.abs(cur.sy);
      const disp = Math.hypot((cur.tx - prevRoot.tx) * map.s, (cur.ty - prevRoot.ty) * map.s) + Math.abs(cur.sy - prevRoot.sy) * 150 * map.s;
      const wingDisp = Math.abs(flapPhase - phase0) * 0.7 * 120 * map.s * k * ctl.flap;
      const N = ctl.blur > 0 ? clamp(Math.ceil(Math.max(disp, wingDisp) * ctl.blur / 9), 1, maxBlurSamples) : 1;
      const target = N > 1 ? ensureFbo() : null;

      useLayerProgram();
      if (ctl.sareeAlpha > 0.001 && ctl.sareeReveal > 0) {
        drawMesh(saree, { alpha: ctl.sareeAlpha, reveal: ctl.sareeReveal, shimmer: ctl.shimmer * 0.6 });
      }

      if (!target) {
        skinAll();
        drawPeacock();
      } else {
        // average N sub-frame samples across the shutter interval (true motion blur)
        const shutter = 0.32;
        const bb = [1e9, 1e9, -1e9, -1e9];
        const samples = [];
        for (let s = 0; s < N; s++) {
          const tau = (s / (N - 1)) * shutter;
          samples.push(tau);
        }
        const R = B.root.pose;
        const setPose = (tau) => {
          R.tx = cur.tx + (prevRoot.tx - cur.tx) * tau;
          R.ty = cur.ty + (prevRoot.ty - cur.ty) * tau;
          R.rot = cur.rot + (prevRoot.rot - cur.rot) * tau;
          R.sx = cur.sx + (prevRoot.sx - cur.sx) * tau;
          R.sy = cur.sy + (prevRoot.sy - cur.sy) * tau;
          procedural(time - tau * dt, flapPhase - tau * (flapPhase - phase0));
          rig.update();
        };
        for (const tau of samples) {
          setPose(tau);
          boundsInto(bb);
        }
        const dpr = canvas.width / window.innerWidth;
        const pad = 24;
        const x0 = clamp(Math.floor((bb[0] - pad) * dpr), 0, canvas.width);
        const y0 = clamp(Math.floor((bb[1] - pad) * dpr), 0, canvas.height);
        const x1 = clamp(Math.ceil((bb[2] + pad) * dpr), 0, canvas.width);
        const y1 = clamp(Math.ceil((bb[3] + pad) * dpr), 0, canvas.height);
        if (x1 > x0 && y1 > y0) {
          gl.enable(gl.SCISSOR_TEST);
          gl.scissor(x0, canvas.height - y1, x1 - x0, y1 - y0);
          gl.bindFramebuffer(gl.FRAMEBUFFER, target.acc.fb);
          gl.clear(gl.COLOR_BUFFER_BIT);
          samples.forEach((tau, si) => {
            setPose(tau);
            skinAll();
            gl.bindFramebuffer(gl.FRAMEBUFFER, target.a.fb);
            gl.clear(gl.COLOR_BUFFER_BIT);
            gl.blendFunc(gl.ONE, gl.ONE_MINUS_SRC_ALPHA);
            useLayerProgram();
            drawPeacock();
            // running average: acc = acc * (1 - w) + sample * w
            gl.bindFramebuffer(gl.FRAMEBUFFER, target.acc.fb);
            gl.blendColor(0, 0, 0, 1 / (si + 1));
            gl.blendFunc(gl.CONSTANT_ALPHA, gl.ONE_MINUS_CONSTANT_ALPHA);
            drawQuad(target.a.tex, x0 / dpr, y0 / dpr, x1 / dpr, y1 / dpr);
          });
          gl.bindFramebuffer(gl.FRAMEBUFFER, null);
          gl.blendFunc(gl.ONE, gl.ONE_MINUS_SRC_ALPHA);
          drawQuad(target.acc.tex, x0 / dpr, y0 / dpr, x1 / dpr, y1 / dpr);
          gl.disable(gl.SCISSOR_TEST);
        }
        Object.assign(R, cur);
      }

      // gold dust
      const speed = Math.hypot(vel.x, vel.y);
      updateParticles(dt, emitters, flight.active ? speed : 0);
      gl.blendFunc(gl.ONE, gl.ONE_MINUS_SRC_ALPHA);
      drawParticles();

      Object.assign(prevRoot, cur);
      prevPhase = flapPhase;
    }

    function destroy() {
      hidden = true;
      const ext = gl.getExtension('WEBGL_lose_context');
      if (ext) ext.loseContext();
    }

    updateMap();
    return {
      rig, ctl, flight, frame, destroy,
      startFlight, endFlight, birdScreenPos,
      burstAtBird(count = 40, speed = 160) {
        const [wx, wy] = bonePoint(rig.world, B.breathe.index, 345, 250);
        burst(map.ox + wx * map.s, map.oy + wy * map.s, count, speed);
      },
      getMap: () => (updateMap(), map),
      setHidden: (h) => (hidden = h),
      /** Lighter drawing for slow computers: fewer motion-blur samples and fewer pixels. */
      setQuality: (q) => {
        if (q.maxDpr) maxDpr = q.maxDpr;
        if (q.blurSamples) maxBlurSamples = q.blurSamples;
        if (q.blurSamples === 1) ctl.blur = 0;
      },
    };
  }

  // ===================================================================== choreography.js
  // GSAP choreography for the rigged peacock: cinematic fly-in, continuous idle
  // life, error reaction, success display + dive-out. All motion drives bones of
  // the original artwork.

  const rand = (a, b) => a + Math.random() * (b - a);
  const pick = (list, last) => {
    const pool = list.filter((x) => x.name !== last);
    const total = pool.reduce((s, x) => s + x.w, 0);
    let r = Math.random() * total;
    for (const x of pool) {
      if ((r -= x.w) <= 0) return x;
    }
    return pool[pool.length - 1];
  };

  // Screen-space waypoints {x, y, k=depth scale} for the bird's pivot (lower body).
  // (px, py) = perch on the logo, s = logo scale. The peacock's body faces left
  // (its train flows to the right), so it flies leftwards and never flips.

  // Fly in from the top-right corner, glide down across the screen and land.
  const flyInPath = (vw, vh, px, py, s) => [
    { x: vw + 150 * s, y: 0.03 * vh, k: 1.3 },
    { x: 0.8 * vw, y: Math.max(0.12 * vh, 230 * s), k: 1.24 },
    { x: 0.56 * vw, y: Math.max(py - 0.16 * vh, 250 * s), k: 1.15 },
    { x: px + 0.11 * vw, y: py - 0.08 * vh, k: 1.06 },
    { x: px + 0.025 * vw, y: py - 0.02 * vh, k: 1.0 },
    { x: px, y: py, k: 1.0 },
  ];

  // Take off and fly away to the left, rising slightly and coming a little
  // closer to the camera.
  const exitLeft = (perched) => (vw, vh, px, py, s, from) => {
    const pts = [{ x: from.x, y: from.y, k: from.k }];
    if (perched) pts.push({ x: from.x - 0.035 * vw, y: from.y - 0.07 * vh, k: 1.04 });
    pts.push({ x: from.x - 0.2 * vw, y: from.y - 0.11 * vh, k: 1.15 });
    pts.push({ x: -380 * s, y: from.y - 0.2 * vh, k: 1.3 });
    return pts;
  };

  function createChoreography(stage, { reducedMotion = false } = {}) {
    const { ctl, rig, flight } = stage;
    const B = rig.byName;
    const root = B.root.pose;
    const channels = { head: { call: null, tl: null, last: '' }, body: { call: null, tl: null, last: '' } };
    let shimmerCall = null;
    let idleOn = false;
    let busy = false;
    const timelines = new Set();
    const track = (tl) => {
      timelines.add(tl);
      const done = tl.eventCallback('onComplete'); // keep the caller's own onComplete
      tl.eventCallback('onComplete', () => {
        timelines.delete(tl);
        if (done) done();
      });
      return tl;
    };

    // ---------------------------------------------------------------- fly-in --
    function flyIn() {
      if (reducedMotion) {
        Object.assign(ctl, { ambient: 1, sareeReveal: 1.2 });
        startIdle();
        return;
      }
      Object.assign(ctl, {
        flap: 1, flapHz: 2.6, farWing: 1, ambient: 0.45, sareeReveal: 0,
        look: 0, spread: 0, stretch: 1, headFwd: 1, flightFx: 1, glint: 0,
      });
      stage.startFlight(flyInPath);
      const tl = track(gsap.timeline());
      // comes in fast from the top-right, decelerates into the landing
      tl.to(flight, { s: 1, duration: 2.7, ease: 'power2.out' }, 0)
        // strong beats, a glide, then beats again to set up the landing
        .to(ctl, { flap: 0.55, flapHz: 2.1, duration: 0.35, ease: 'sine.inOut' }, 0.7)
        .to(ctl, { flap: 0.95, flapHz: 2.7, duration: 0.3, ease: 'sine.inOut' }, 1.3)
        // flare: neck comes up, wings spread to brake, hover onto the perch
        .to(ctl, { flap: 1.05, flapHz: 3.5, spread: 0.65, duration: 0.3, ease: 'sine.inOut' }, 1.85)
        .to(ctl, { stretch: 0, duration: 0.7, ease: 'sine.inOut' }, 1.8)
        .to(flight, { hover: 1, duration: 0.5, ease: 'sine.inOut' }, 1.95)
        // touch down: wings fold back along the body
        .to(ctl, { flap: 0, spread: 0, duration: 0.6, ease: 'power2.inOut' }, 2.4)
        .to(ctl, { farWing: 0, duration: 0.45, ease: 'power1.in' }, 2.55)
        .to(ctl, { flightFx: 0, duration: 0.6 }, 2.3)
        .to(ctl, { ambient: 1, duration: 0.8, ease: 'sine.inOut' }, 2.3)
        .call(() => stage.burstAtBird(46, 170), null, 2.55)
        .call(() => stage.endFlight(), null, 2.72)
        .to(B.root.act, { keyframes: [{ sy: 0.965, ty: 2.5, duration: 0.14, ease: 'power2.out' }, { sy: 1, ty: 0, duration: 0.55, ease: 'sine.out' }] }, 2.65)
        // it turns its head to look back over its shoulder (the logo pose)...
        .to(ctl, { headFwd: 0, duration: 0.32, ease: 'power2.inOut' }, 2.75)
        // ...and the saree streams out from its back
        .to(ctl, { sareeReveal: 1.2, duration: 1.35, ease: 'power2.inOut' }, 2.85)
        .fromTo(ctl, { sareeBoost: 1 }, { sareeBoost: 0, duration: 1.9, ease: 'power2.out' }, 2.85)
        .call(() => startIdle(), null, 3.15)
        .to(ctl, { glint: 1, duration: 0.2, yoyo: true, repeat: 1, ease: 'sine.inOut' }, 3.2);
    }

    // -------------------------------------------------------------- idle life --
    // Two independent channels (head/neck and wings/crest/tail) run back-to-back
    // with only short gaps, so the bird is never frozen between actions.
    const headActions = [
      {
        name: 'headTurn', w: 3,
        run(tl) {
          const a = rand(-10, 10);
          tl.to(B.head.act, { rot: a, duration: rand(0.45, 0.7), ease: 'sine.inOut' })
            .to(B.neck2.act, { rot: a * 0.35, duration: 0.6, ease: 'sine.inOut' }, '<')
            .to(B.neck.act, { rot: a * 0.15, duration: 0.65, ease: 'sine.inOut' }, '<')
            .to([B.head.act, B.neck2.act, B.neck.act], { rot: 0, duration: 0.7, ease: 'sine.inOut' }, `+=${rand(0.25, 0.7)}`);
        },
      },
      {
        name: 'lookAtViewer', w: 2.2,
        run(tl) {
          tl.to(ctl, { look: 1, duration: 0.5, ease: 'power2.inOut' })
            .to(ctl, { glint: 1, duration: 0.25, ease: 'sine.out' }, '-=0.2')
            .to(ctl, { glint: 0, duration: 0.6, ease: 'sine.in' }, '+=0.4')
            .to(ctl, { look: 0, duration: 0.6, ease: 'power2.inOut' }, `+=${rand(0.1, 0.35)}`);
        },
      },
      {
        name: 'peck', w: 2.4,
        run(tl) {
          const n = Math.floor(rand(1, 3.99));
          for (let i = 0; i < n; i++) {
            tl.to(B.neck.act, { rot: 4, duration: 0.16, ease: 'power2.in' })
              .to(B.neck2.act, { rot: 6, duration: 0.16, ease: 'power2.in' }, '<')
              .to(B.head.act, { rot: 20, tx: 1.5, duration: 0.16, ease: 'power2.in' }, '<')
              .to([B.neck.act, B.neck2.act], { rot: 0, duration: 0.24, ease: 'power2.out' })
              .to(B.head.act, { rot: 0, tx: 0, duration: 0.26, ease: 'power2.out' }, '<');
            if (i < n - 1) tl.to({}, { duration: rand(0.05, 0.18) });
          }
        },
      },
      {
        name: 'headTilt', w: 1.6,
        run(tl) {
          const a = rand(-7, -3);
          tl.to(B.head.act, { rot: a, sy: 0.98, duration: 0.5, ease: 'sine.inOut' })
            .to(B.neck2.act, { rot: 2.5, duration: 0.5, ease: 'sine.inOut' }, '<')
            .to([B.head.act, B.neck2.act], { rot: 0, sy: 1, duration: 0.6, ease: 'sine.inOut' }, `+=${rand(0.2, 0.5)}`);
        },
      },
    ];
    const bodyActions = [
      {
        name: 'wingFlutter', w: 2,
        run(tl) {
          tl.to(B.wingR.act, { rot: -14, duration: 0.2, ease: 'sine.out' })
            .to(B.wingRTip.act, { rot: -8, duration: 0.2, ease: 'sine.out' }, '<')
            .to(B.wingR.act, { rot: -2, duration: 0.18, ease: 'sine.in' })
            .to(B.wingR.act, { rot: -11, duration: 0.18, ease: 'sine.out' })
            .to(B.wingR.act, { rot: 0, duration: 0.45, ease: 'sine.inOut' })
            .to(B.wingRTip.act, { rot: 0, duration: 0.5, ease: 'sine.inOut' }, '<')
            .to(B.tail.act, { rot: -2.5, duration: 0.25, yoyo: true, repeat: 1, ease: 'sine.inOut' }, 0);
        },
      },
      {
        name: 'crestRuffle', w: 1.5,
        run(tl) {
          const set = [B.crest1.act, B.crest2.act, B.crest3.act, B.crest4.act];
          tl.to(set, { rot: (i) => [-10, -4, 4, 10][i], duration: 0.3, ease: 'back.out(3)', stagger: 0.04 })
            .to(set, { rot: 0, duration: 0.7, ease: 'sine.inOut', stagger: 0.05 }, '+=0.25');
        },
      },
      {
        name: 'featherShake', w: 1.2,
        run(tl) {
          const parts = [B.tail.act, B.tail3.act, B.tealLow.act, B.flourish.act, B.flourish3.act, B.sweep.act];
          tl.to(parts, { rot: () => rand(-3, 3), duration: 0.07, repeat: 5, yoyo: true, ease: 'sine.inOut' })
            .to(parts, { rot: 0, duration: 0.3, ease: 'sine.out' })
            .to(B.breathe.act, { sy: 1.02, duration: 0.18, yoyo: true, repeat: 1, ease: 'sine.inOut' }, 0);
        },
      },
      {
        name: 'tailSway', w: 1.6,
        run(tl) {
          // a ripple that travels down the plumes, while the tail lifts
          const plumes = [B.tail.act, B.tail2.act, B.tail3.act, B.tail4.act];
          tl.to(plumes, { rot: -2.5, duration: 0.45, ease: 'sine.inOut', stagger: 0.12 })
            .to(plumes, { rot: 2, duration: 0.55, ease: 'sine.inOut', stagger: 0.12 }, 0.45)
            .to(plumes, { rot: 0, duration: 0.5, ease: 'sine.inOut', stagger: 0.12 }, 1.0)
            .to([B.flourish.act, B.flourish2.act, B.flourish3.act], { rot: 3, duration: 0.6, ease: 'sine.inOut', stagger: 0.1 }, 0.1)
            .to([B.flourish.act, B.flourish2.act, B.flourish3.act], { rot: 0, duration: 0.7, ease: 'sine.inOut', stagger: 0.1 }, 0.8);
        },
      },
      {
        name: 'wingStretch', w: 1.2,
        run(tl) {
          tl.to(B.wingR.act, { rot: -10, sx: 1.04, duration: 0.6, ease: 'sine.inOut' })
            .to(B.wingRMid.act, { rot: -5, duration: 0.6, ease: 'sine.inOut' }, '<')
            .to(B.wingRTip.act, { rot: -7, duration: 0.6, ease: 'sine.inOut' }, '<')
            .to([B.wingR.act, B.wingRMid.act, B.wingRTip.act], { rot: 0, sx: 1, duration: 0.7, ease: 'sine.inOut' }, '+=0.3');
        },
      },
    ];

    function runChannel(name, list, gapMin, gapMax, delay) {
      const ch = channels[name];
      ch.call = gsap.delayedCall(delay, () => {
        if (!idleOn) return;
        if (busy) return runChannel(name, list, gapMin, gapMax, 0.3);
        const action = pick(list, ch.last);
        ch.last = action.name;
        ch.tl = gsap.timeline({ onComplete: () => runChannel(name, list, gapMin, gapMax, rand(gapMin, gapMax)) });
        action.run(ch.tl);
      });
    }

    function scheduleShimmer(delay) {
      shimmerCall = gsap.delayedCall(delay, () => {
        track(
          gsap
            .timeline({ onComplete: () => idleOn && scheduleShimmer(rand(4, 7)) })
            .set(ctl, { sweep: 360 })
            .to(ctl, { shimmer: 0.9, duration: 0.3 }, 0)
            .to(ctl, { sweep: 1150, duration: 2.2, ease: 'sine.inOut' }, 0)
            .to(ctl, { shimmer: 0, duration: 0.4 }, 1.8)
        );
      });
    }

    function startIdle() {
      ctl.ambient = 1;
      if (reducedMotion || idleOn) return;
      idleOn = true;
      runChannel('head', headActions, 0.05, 0.35, 0.1);
      runChannel('body', bodyActions, 0.1, 0.6, 0.5);
      scheduleShimmer(1.5);
    }

    function stopIdle() {
      idleOn = false;
      Object.values(channels).forEach((ch) => {
        ch.call?.kill();
        ch.tl?.kill();
        ch.call = ch.tl = null;
      });
      shimmerCall?.kill();
      shimmerCall = null;
    }

    function settleActs(duration = 0.3) {
      rig.bones.forEach((b) => gsap.to(b.act, { rot: 0, tx: 0, ty: 0, sx: 1, sy: 1, duration, overwrite: true }));
    }

    // --------------------------------------------------------- failed login --
    function reactToError() {
      if (busy) return;
      channels.head.tl?.kill();
      gsap.to([B.head.act, B.neck.act, B.neck2.act], { rot: 0, tx: 0, sy: 1, duration: 0.15, overwrite: true });
      busy = true;
      const tl = track(gsap.timeline({ onComplete: () => (busy = false) }));
      const set = [B.crest1.act, B.crest2.act, B.crest3.act, B.crest4.act];
      tl.to(B.head.act, { rot: -12, duration: 0.22, ease: 'power2.out' }, 0.15)
        .to(B.neck.act, { rot: -4, duration: 0.22, ease: 'power2.out' }, 0.15)
        .to(set, { rot: (i) => [-12, -5, 5, 12][i], duration: 0.25, ease: 'back.out(3)', overwrite: true }, 0.15)
        .to(B.head.act, { rot: -4, duration: 0.08, repeat: 3, yoyo: true, ease: 'sine.inOut' }, 0.45)
        .to([B.head.act, B.neck.act, B.neck2.act], { rot: 0, duration: 0.55, ease: 'sine.inOut' }, 0.9)
        .to(set, { rot: 0, duration: 0.7, ease: 'sine.inOut' }, 0.9)
        .to(B.wingR.act, { rot: -10, duration: 0.18, yoyo: true, repeat: 1, ease: 'sine.inOut', overwrite: true }, 0.2);
    }

    // -------------------------------------------------------- successful login --
    function playSuccess({ onLeave, onPeacockGone } = {}) {
      stopIdle();
      timelines.forEach((tl) => tl.kill()); // also cancels a fly-in still in progress
      timelines.clear();
      settleActs(0.2);
      busy = true;
      const tl = track(gsap.timeline());
      const gone = () => {
        ctl.peacockAlpha = 0;
        ctl.flightFx = 0;
        onPeacockGone?.();
      };

      if (reducedMotion) {
        tl.call(() => onLeave?.(), null, 0)
          .to(ctl, { peacockAlpha: 0, sareeAlpha: 0, duration: 0.5 }, 0)
          .call(gone, null, 0.6);
        return tl;
      }

      // logged in while it is still flying in: it just keeps flying, on out to the left
      if (flight.active) {
        tl.to(ctl, { flap: 1, flapHz: 3.0, stretch: 1, headFwd: 1, spread: 0, farWing: 1, flightFx: 1, duration: 0.2 }, 0)
          .call(() => stage.startFlight(exitLeft(false)), null, 0)
          .to(flight, { s: 1, duration: 0.9, ease: 'power1.in' }, 0)
          .call(() => onLeave?.(), null, 0.05)
          .to(ctl, { sareeAlpha: 0, duration: 0.5 }, 0.05)
          .call(gone, null, 0.92);
        return tl;
      }

      tl.to(ctl, { look: 1, duration: 0.25, ease: 'power2.out' }, 0)
        .to(ctl, { glint: 1, duration: 0.12 }, 0.08)
        .to(ctl, { glint: 0, duration: 0.3 }, 0.3)
        // display: wings (both feather layers) rise and fan open
        .to(ctl, { farWing: 1, duration: 0.2 }, 0.1)
        .to(ctl, { spread: 1, duration: 0.32, ease: 'back.out(1.4)' }, 0.1)
        .to(ctl, { shimmer: 1, duration: 0.15 }, 0.1)
        .fromTo(ctl, { sweep: 360 }, { sweep: 1100, duration: 0.6, ease: 'sine.inOut' }, 0.1)
        .to(ctl, { shimmer: 0, duration: 0.2 }, 0.6)
        // turns its head to face the way it will fly (left)
        .to(ctl, { look: 0, duration: 0.2 }, 0.36)
        .to(ctl, { headFwd: 1, duration: 0.24, ease: 'power2.inOut' }, 0.36)
        // crouch...
        .to(B.root.act, { sy: 0.95, ty: 4, duration: 0.12, ease: 'power2.in' }, 0.46)
        // ...and take off, flying away to the left
        .call(() => {
          stage.startFlight(exitLeft(true));
          stage.burstAtBird(50, 200);
        }, null, 0.58)
        .to(B.root.act, { sy: 1, ty: 0, duration: 0.15, ease: 'power2.out' }, 0.58)
        .to(ctl, { spread: 0, flap: 1, flapHz: 3.1, stretch: 1, flightFx: 1, duration: 0.22, ease: 'power1.out' }, 0.58)
        .to(flight, { s: 1, duration: 1.05, ease: 'power2.in' }, 0.58)
        // the login screen fades away behind it, revealing the landing page
        .call(() => onLeave?.(), null, 0.62)
        .to(ctl, { sareeAlpha: 0, duration: 0.6, ease: 'sine.inOut' }, 0.62)
        .call(gone, null, 1.64);
      return tl;
    }

    function destroy() {
      stopIdle();
      timelines.forEach((tl) => tl.kill());
      timelines.clear();
      rig.bones.forEach((b) => gsap.killTweensOf([b.act, b.pose]));
      gsap.killTweensOf([ctl, flight]);
    }

    return { flyIn, startIdle, stopIdle, reactToError, playSuccess, destroy };
  }


  window.SVTPeacock = { createPeacockStage, createChoreography };
})();
