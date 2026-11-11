// ===== 부스럭 예냉박스 AI 코어 (브라우저/Node 공용) =====
// model.h에는 #pragma once를 넣지 않아요. .ino 안에 그대로 붙여 넣어도 경고가 나지 않게요.
const AI = (() => {
  const DT = 10 / 60;          // 격자 간격: 10초 (분 단위)
  const H = 2;                 // 예측 한 걸음: 2분
  const LAG = 12;              // 2분 = 10초 x 12
  const NF = 4;                // 특징: [식품칸-냉기, 냉기, 식품칸 2분기울기, 냉기 2분기울기]

  // 기록(가변 간격) -> 10초 격자로 선형보간
  function resample(rows) {
    // rows: [{t, b, c, lid, fan}] (t 분)
    const r = rows.filter(x => isFinite(x.t) && isFinite(x.b)).sort((a, b) => a.t - b.t);
    if (r.length < 2) return [];
    const out = []; let j = 0;
    for (let t = r[0].t; t <= r[r.length - 1].t + 1e-9; t += DT) {
      while (j < r.length - 2 && r[j + 1].t < t) j++;
      const a = r[j], z = r[j + 1];
      const u = z.t > a.t ? Math.min(1, Math.max(0, (t - a.t) / (z.t - a.t))) : 0;
      const lerp = (p, q) => (isFinite(p) && isFinite(q)) ? p + (q - p) * u : NaN;
      const near = u < 0.5 ? a : z;
      out.push({ t, b: lerp(a.b, z.b), c: lerp(a.c, z.c), lid: near.lid ? 1 : 0, fan: near.fan === 0 ? 0 : 1 });
    }
    return out;
  }

  function feat(b, c, sb, sc) { return [b - c, c, sb, sc]; }

  // 학습용 표본: 격자 i에서 [i-12, i+12] 동안 팬 켜짐·뚜껑 닫힘·값 정상
  function samples(g) {
    const X = [], YB = [], YC = [];
    for (let i = LAG; i + LAG < g.length; i++) {
      let ok = true;
      for (let k = i - LAG; k <= i + LAG; k++) {
        const s = g[k];
        if (!s.fan || s.lid || !isFinite(s.b) || !isFinite(s.c)) { ok = false; break; }
      }
      if (!ok) continue;
      const p = g[i - LAG], s = g[i], f = g[i + LAG];
      X.push(feat(s.b, s.c, (s.b - p.b) / H, (s.c - p.c) / H));
      YB.push(f.b - s.b); YC.push(f.c - s.c);
    }
    return { X, YB, YC };
  }

  function solve(A, y) { // 가우스 소거 (부분 피벗)
    const n = y.length, M = A.map((r, i) => [...r, y[i]]);
    for (let c = 0; c < n; c++) {
      let p = c; for (let r = c + 1; r < n; r++) if (Math.abs(M[r][c]) > Math.abs(M[p][c])) p = r;
      [M[c], M[p]] = [M[p], M[c]];
      for (let r = 0; r < n; r++) if (r !== c) {
        const f = M[r][c] / M[c][c]; for (let k = c; k <= n; k++) M[r][k] -= f * M[c][k];
      }
    }
    return M.map((r, i) => r[n] / r[i]);
  }

  // 릿지 회귀 (표준화 후). w[0]=절편
  function ridge(Z, y, lam) {
    const n = Z[0].length + 1, A = Array.from({ length: n }, () => Array(n).fill(0)), v = Array(n).fill(0);
    for (let i = 0; i < Z.length; i++) {
      const z = [1, ...Z[i]];
      for (let a = 0; a < n; a++) { v[a] += z[a] * y[i]; for (let b = 0; b < n; b++) A[a][b] += z[a] * z[b]; }
    }
    for (let a = 1; a < n; a++) A[a][a] += lam;
    return solve(A, v);
  }

  function train(grids, opt = {}) {
    let X = [], YB = [], YC = [];
    for (const g of grids) { const s = samples(g); X = X.concat(s.X); YB = YB.concat(s.YB); YC = YC.concat(s.YC); }
    if (X.length < 30) throw new Error('학습할 표본이 부족해요 (' + X.length + '개). 팬이 켜진 채 5분 이상 기록된 실험이 더 필요해요.');
    const mean = Array(NF).fill(0), std = Array(NF).fill(0);
    for (const x of X) x.forEach((v, k) => mean[k] += v / X.length);
    for (const x of X) x.forEach((v, k) => std[k] += (v - mean[k]) ** 2 / X.length);
    for (let k = 0; k < NF; k++) std[k] = Math.sqrt(std[k]) || 1;
    const Z = X.map(x => x.map((v, k) => (v - mean[k]) / std[k]));
    const lam = opt.lambda ?? 1.0;
    const wb = ridge(Z, YB, lam), wc = ridge(Z, YC, lam);
    const m = { v: 1, H, mean, std, wb, wc, n: X.length, runs: grids.length, note: opt.note || '' };
    // 학습 오차 (2분 뒤 온도 변화, ℃)
    let eb = 0, ec = 0;
    Z.forEach((z, i) => { eb += Math.abs(lin(wb, z) - YB[i]); ec += Math.abs(lin(wc, z) - YC[i]); });
    m.fitMaeB = eb / Z.length; m.fitMaeC = ec / Z.length;
    return m;
  }

  function lin(w, z) { let s = w[0]; for (let k = 0; k < z.length; k++) s += w[k + 1] * z[k]; return s; }
  function step(m, b, c, sb, sc) {
    const z = feat(b, c, sb, sc).map((v, k) => (v - m.mean[k]) / m.std[k]);
    return [lin(m.wb, z), lin(m.wc, z)];
  }

  // AI 예측: 목표까지 남은 분. -2 = 180분 안에 못 닿음(또는 더 안 식음)
  function etaAI(m, b, c, sb, sc, target, path) {
    if (b <= target) return 0;
    let t = 0;
    if (path) path.push([0, b, c]);
    for (let s = 0; s < 90; s++) {
      const [db, dc] = step(m, b, c, sb, sc);
      const nb = b + db, nc = c + dc;
      if (path) path.push([t + H, nb, nc]);
      if (nb <= target) return t + H * (b - target) / (b - nb);
      if (db > -0.005) return -2;
      b = nb; c = nc; sb = db / H; sc = dc / H; t += H;
    }
    return -2;
  }

  // ---- 뚜껑·팬 보정: 최근 2분 안에 뚜껑이 열렸거나 팬이 꺼졌으면 기울기가 틀어져요. ----
  // 이때는 마지막으로 "깨끗했던" 시점의 AI 예측을 그대로 두고, 그 뒤 지난 시간만큼만 줄여요.
  // (펌웨어 precool_ai_ble.ino의 updateAi()와 같은 규칙)
  function cleanAt(g, i) {
    if (i < LAG) return false;
    for (let k = i - LAG; k <= i; k++) { const s = g[k]; if (!s.fan || s.lid || !isFinite(s.b) || !isFinite(s.c)) return false; }
    return true;
  }
  // 반환: { eta, hold }  eta: 남은 분, -1 모름, -2 못 닿음 / hold: 유지값이면 true
  function etaAIHold(m, g, i, target, path) {
    if (isFinite(g[i].b) && g[i].b <= target) return { eta: 0, hold: false };
    for (let j = i; j >= LAG; j--) {
      if (!cleanAt(g, j)) continue;
      const s = g[j], p = g[j - LAG];
      const e = etaAI(m, s.b, s.c, (s.b - p.b) / H, (s.c - p.c) / H, target, j === i ? path : null);
      if (j === i) return { eta: e, hold: false };
      if (e < 0) return { eta: e, hold: true };
      return { eta: Math.max(0, e - (g[i].t - s.t)), hold: true };
    }
    return { eta: -1, hold: false };
  }

  // ---- 펌웨어와 같은 뉴턴/직선 예측 (최근 10분, 2분 이상 모였을 때) ----
  function fitSlope(x, y) {
    const n = x.length; if (n < 2) return null;
    let mx = 0, my = 0; for (let i = 0; i < n; i++) { mx += x[i]; my += y[i]; } mx /= n; my /= n;
    let sxx = 0, sxy = 0; for (let i = 0; i < n; i++) { const d = x[i] - mx; sxx += d * d; sxy += d * (y[i] - my); }
    return sxx > 0 ? sxy / sxx : null;
  }
  function etaNewton(hist, target) { // hist: 최근 10분 [{t,b,c}]
    const n = hist.length, last = hist[n - 1];
    if (n < 12) return { eta: -1, m: '-' };
    if (last.b <= target) return { eta: 0, m: 'N' };
    const cs = hist.filter(h => isFinite(h.c));
    if (cs.length >= n * 0.8) {
      const tc = cs.reduce((s, h) => s + h.c, 0) / cs.length;
      const xs = [], ys = [];
      for (const h of hist) { const d = h.b - tc; if (d > 0.3) { xs.push(h.t); ys.push(Math.log(d)); } }
      const k = xs.length >= 12 ? fitSlope(xs, ys) : null;
      if (k !== null && -k > 0.001) {
        if (target <= tc + 0.2) return { eta: -2, m: 'N' };
        return { eta: Math.log((last.b - tc) / (target - tc)) / -k, m: 'N' };
      }
    }
    const s = fitSlope(hist.map(h => h.t), hist.map(h => h.b));
    if (s !== null && s < -0.01) return { eta: (last.b - target) / -s, m: 'L' };
    return { eta: -1, m: '-' };
  }
  function etaLinear(hist, target) {
    if (hist.length < 12) return -1;
    const last = hist[hist.length - 1]; if (last.b <= target) return 0;
    const s = fitSlope(hist.map(h => h.t), hist.map(h => h.b));
    return (s !== null && s < -0.01) ? (last.b - target) / -s : -2;
  }

  // ---- 평가: 실제로 목표에 닿은 실험에서, 닿기 전 매 30초마다 남은 시간 예측 오차 ----
  function evaluate(m, grids, target, cap = 180) {
    const res = { ai: [], newton: [], linear: [], runsUsed: 0, warnAI: [0, 0, 0, 0] };
    for (const g of grids) {
      const hit = g.findIndex(s => s.b <= target);
      if (hit < 0) continue;
      res.runsUsed++;
      const T = g[hit].t;
      for (let i = LAG; i < hit; i += 3) {
        const s = g[i], p = g[i - LAG];
        const truth = T - s.t;
        const hist = g.slice(Math.max(0, i - 59), i + 1);
        const pen = e => (e < 0 ? cap : Math.min(e, cap));
        res.ai.push(Math.abs(pen(etaAI(m, s.b, s.c, (s.b - p.b) / H, (s.c - p.c) / H, target)) - truth));
        res.newton.push(Math.abs(pen(etaNewton(hist, target).eta) - truth));
        res.linear.push(Math.abs(pen(etaLinear(hist, target)) - truth));
      }
    }
    const mae = a => a.length ? a.reduce((s, v) => s + v, 0) / a.length : NaN;
    return { maeAI: mae(res.ai), maeNewton: mae(res.newton), maeLinear: mae(res.linear), points: res.ai.length, runsUsed: res.runsUsed };
  }

  // ---- 시뮬레이터: 식품칸 공기(측정) / 딸기 덩어리(측정 안 됨) / 냉기실(측정) / 아이스팩 ----
  // 계수는 그럴듯한 범위에서 무작위로 뽑은 가정값이에요. 실제 측정값이 아니에요.
  function rng(seed) { let s = seed >>> 0 || 1; return () => ((s = (s * 1664525 + 1013904223) >>> 0) / 4294967296); }
  function simParams(rand) {
    const R = (a, b) => a + (b - a) * rand();
    const amb = R(18, 30);
    return {
      amb, A0: amb + R(-2, 3), M0: amb + R(-1, 4), C0: R(-1, 6), ice: R(-8, -2), iceCap: R(150, 600),
      kf: R(0.15, 0.45), km: R(0.08, 0.3), kmm: R(0.03, 0.09), ki: R(0.1, 0.4), kl: R(0.002, 0.012),
      noise: 0.04
    };
  }
  function simStep(st, p, dt, fan, lid, rand) {
    const kf = fan ? p.kf : p.kf * 0.12, open = lid ? 0.25 : 0;
    const dA = -kf * (st.A - st.C) - p.km * (st.A - st.M) - (p.kl + open) * (st.A - p.amb);
    const dM = -p.kmm * (st.M - st.A);
    const iceT = st.cap > 0 ? p.ice * Math.min(1, st.cap / (p.iceCap * 0.3)) : st.I;
    const q = p.ki * (st.C - iceT);
    const dC = -q + kf * 0.35 * (st.A - st.C) - p.kl * 1.5 * (st.C - p.amb);
    st.A += dA * dt; st.M += dM * dt; st.C += dC * dt;
    if (st.cap > 0) { st.cap -= q * dt * 3; st.I = iceT; } else { st.I += 0.05 * (st.C - st.I) * dt; }
    st.t += dt;
  }
  function q16(x) { return Math.round(x * 16) / 16; } // DS18B20 0.0625℃
  function simulateRun(seed, minutes = 90, opt = {}) {
    const rand = rng(seed), p = simParams(rand);
    const st = { t: 0, A: p.A0, M: p.M0, C: p.C0, cap: p.iceCap, I: p.ice };
    const rows = [], dt = 1 / 60;
    const target = opt.target ?? 1;
    let done = false;
    for (let i = 0; i <= minutes * 60; i++) {
      if (i % 10 === 0) {
        const n = () => (rand() - 0.5) * 2 * p.noise;
        rows.push({ t: st.t, b: q16(st.A + n()), c: q16(st.C + n()), lid: 0, fan: done ? 0 : 1 });
      }
      if (opt.stopAtTarget && st.A <= target) done = true;
      simStep(st, p, dt, !done, false, rand);
    }
    return { rows, params: p };
  }

  function toHeader(m) {
    const f = a => a.map(v => (Math.abs(v) < 1e-12 ? 0 : v).toPrecision(8) + 'f').join(', ');
    const d = new Date().toISOString().slice(0, 16).replace('T', ' ');
    return `// model.h — 부스럭 예냉박스 학습 모델 (AI 스튜디오에서 자동 생성)
// 만든 시각: ${d} UTC
// 학습 데이터: 실험 ${m.runs}개, 학습 표본 ${m.n}개 ${m.note ? '(' + m.note + ')' : ''}
// 2분 뒤 온도 변화 학습 오차: 식품칸 ${m.fitMaeB.toFixed(3)}℃, 냉기실 ${m.fitMaeC.toFixed(3)}℃
// 특징 순서: [식품칸-냉기실, 냉기실, 식품칸 2분 기울기(℃/분), 냉기실 2분 기울기(℃/분)]
#define MODEL_READY 1
#define MODEL_H_MIN ${m.H.toFixed(1)}f
static const char MODEL_INFO[] = "runs=${m.runs} n=${m.n}${m.note ? ' ' + m.note.replace(/[^A-Za-z0-9 _.-]/g, '') : ''}";
static const float MODEL_MEAN[4] = { ${f(m.mean)} };
static const float MODEL_STD[4]  = { ${f(m.std)} };
static const float MODEL_WB[5]   = { ${f(m.wb)} };  // 식품칸 2분 뒤 변화: 절편 + 가중치 4개
static const float MODEL_WC[5]   = { ${f(m.wc)} };  // 냉기실 2분 뒤 변화
`;
  }

  return { toHeader, DT, H, LAG, resample, samples, train, step, etaAI, cleanAt, etaAIHold, etaNewton, etaLinear, evaluate, simulateRun, simParams, simStep, rng, q16 };
})();
if (typeof module !== 'undefined') module.exports = AI;
