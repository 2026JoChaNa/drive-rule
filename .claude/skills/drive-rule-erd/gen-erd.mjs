#!/usr/bin/env node
// DRIVE RULE ERD 재생성기 (drive-rule-erd 스킬 규칙을 코드로 옮긴 것)
//
//   node .claude/skills/drive-rule-erd/gen-erd.mjs [data.mjs] [out.drawio]
//   기본값: docs/diagrams/erd.data.mjs → docs/diagrams/ERD.drawio
//
// 하는 일: 데이터 검증 → 높이·밴드 y 계산 → 앵커 분산 → lane 배정 → XML 직렬화
//         → 자가검증(겹침 · 앵커 중복 · 엣지 관통 · 포개짐) → Flyway drift 경고
// 자가검증이 하나라도 실패하면 파일을 쓰지 않고 exit 1.

import fs from 'node:fs';
import path from 'node:path';
import { pathToFileURL } from 'node:url';

const dataPath = path.resolve(process.argv[2] ?? 'docs/diagrams/erd.data.mjs');
const outPath = path.resolve(process.argv[3] ?? 'docs/diagrams/ERD.drawio');
const data = (await import(pathToFileURL(dataPath).href)).default;

// ---------------------------------------------------------------- 상수 (§2.2)
const C = {
  TABLE_W: 400, HEADER_H: 30, ROW_H: 30,
  H_GUTTER: 320,      // 같은 밴드 좌우 테이블 사이
  V_GUTTER: 220,      // 위 밴드 실제 바닥 ~ 아래 밴드 top
  LEFT: 240,          // 왼쪽 여백 (구역·밴드 라벨 전용 열)
  TOP: 40,
  LANE_START: 40,     // gap 경계에서 첫 lane 까지
  LANE_STEP: 30,      // lane 간격 (포개짐 방지)
  ZONE_EXTRA: 100,    // 구역(서버/앱) 경계에 추가로 두는 높이 (구분선 + 구역 라벨)
  ...(data.config ?? {}),
};
const CELL = [ // 한글명 | 컬럼 | 타입 | 제약 (§3.3)
  { key: 'kr', x: 0, w: 100, align: 'align=center;' },
  { key: 'name', x: 100, w: 160, align: 'align=left;spacingLeft=6;' },
  { key: 'type', x: 260, w: 90, align: 'align=center;' },
  { key: 'cons', x: 350, w: 50, align: 'align=center;' },
];
const CARD = { // §7 카디널리티 → [부모 머리, 자식 머리]
  '1:0..N': ['ERone', 'ERzeroToMany'],
  '1:1..N': ['ERone', 'ERoneToMany'],
  '0..1:0..N': ['ERzeroToOne', 'ERzeroToMany'],
  '1:1': ['ERone', 'ERone'],
  '1:0..1': ['ERone', 'ERzeroToOne'],
};

const errors = [];
const warns = [];
const esc = (s) => String(s ?? '').replace(/&/g, '&amp;').replace(/</g, '&lt;').replace(/>/g, '&gt;').replace(/"/g, '&quot;');
const r10 = (v) => Math.round(v / 10) * 10;

// ---------------------------------------------------------------- 1. 데이터 검증
const zoneIdx = new Map(data.zones.map((z, i) => [z.id, i]));
const T = new Map();
for (const t of data.tables) {
  if (T.has(t.id)) errors.push(`TABLE DUP ${t.id}`);
  if (!zoneIdx.has(t.zone)) errors.push(`TABLE ${t.id}: 알 수 없는 zone '${t.zone}'`);
  const names = t.columns.map((c) => c.name);
  const dupCol = names.find((n, i) => names.indexOf(n) !== i);
  if (dupCol) errors.push(`TABLE ${t.id}: 컬럼 중복 ${dupCol}`);
  T.set(t.id, { ...t, zi: zoneIdx.get(t.zone), w: C.TABLE_W, h: C.HEADER_H + C.ROW_H * t.columns.length });
}
const hasCons = (col, c) => (col.cons ?? '').split(',').map((s) => s.trim()).includes(c);

const fkUsed = new Set();
for (const r of data.relations) {
  const p = T.get(r.from), q = T.get(r.to);
  if (!p || !q) { errors.push(`REL ${r.from}->${r.to}: 테이블 없음`); continue; }
  if (!CARD[r.card]) errors.push(`REL ${r.from}->${r.to}: 카디널리티 '${r.card}' 미지원 (${Object.keys(CARD).join(', ')})`);
  const col = q.columns.find((c) => c.name === r.fk);
  if (!col) errors.push(`REL ${r.from}->${r.to}: 자식에 FK 컬럼 ${r.fk} 없음`);
  else {
    if (!hasCons(col, 'FK')) errors.push(`REL ${r.from}->${r.to}: ${r.to}.${r.fk} 제약에 FK 표기 없음`);
    const nullable = !hasCons(col, 'NN') && !hasCons(col, 'PK');
    if (nullable && !r.card.startsWith('0..1')) warns.push(`REL ${r.from}->${r.to}: ${r.fk} 가 NULL 허용인데 부모 쪽이 '1' (0..1 아닌지 확인)`);
    if (!nullable && r.card.startsWith('0..1')) warns.push(`REL ${r.from}->${r.to}: ${r.fk} 가 NN 인데 부모 쪽이 '0..1'`);
  }
  fkUsed.add(`${r.to}.${r.fk}`);
}
for (const t of T.values()) for (const c of t.columns)
  if (hasCons(c, 'FK') && !fkUsed.has(`${t.id}.${c.name}`)) errors.push(`FK ${t.id}.${c.name}: 관계(relations) 누락`);
for (const f of data.flows ?? [])
  if (!T.get(f.from) || !T.get(f.to)) errors.push(`FLOW ${f.from}->${f.to}: 테이블 없음`);

if (errors.length) fail();

// ---------------------------------------------------------------- 2. 레이아웃 (§2.3 밴드 y 계산)
const bandKeys = [...new Set([...T.values()].map((t) => `${t.zi}:${t.band}`))]
  .map((k) => k.split(':').map(Number)).sort((a, b) => a[0] - b[0] || a[1] - b[1]);
const bands = []; // 전역 밴드 순서
const labels = []; // 라벨/구분선 (충돌 검사 대상 텍스트 박스)
const dividers = [];
let prevBottom = null, prevZone = null;
for (const [zi, band] of bandKeys) {
  const members = [...T.values()].filter((t) => t.zi === zi && t.band === band);
  let y;
  if (prevBottom === null) {
    labels.push({ id: `ZL_${zi}`, x: 40, y: C.TOP, w: 160, h: 40, value: data.zones[zi].label, zone: true });
    y = C.TOP + 100;
  } else if (zi !== prevZone) {
    const dy = prevBottom + C.V_GUTTER - 20;
    dividers.push(dy);
    labels.push({ id: `ZL_${zi}`, x: 40, y: dy + 20, w: 160, h: 40, value: data.zones[zi].label, zone: true });
    y = prevBottom + C.V_GUTTER + C.ZONE_EXTRA;
  } else {
    y = prevBottom + C.V_GUTTER;
  }
  const bandLabel = data.zones[zi].bands?.[band] ?? `Depth ${band}`;
  labels.push({ id: `BL_${zi}_${band}`, x: 40, y: y - 40, w: 160, h: 30, value: bandLabel });
  const cols = new Set();
  for (const t of members) {
    if (cols.has(t.col)) errors.push(`BAND ${data.zones[zi].id}/${band}: col ${t.col} 에 테이블 2개`);
    cols.add(t.col);
    t.x = C.LEFT + t.col * (C.TABLE_W + C.H_GUTTER);
    t.y = y;
    t.gb = bands.length;
  }
  const bottom = Math.max(...members.map((t) => t.y + t.h));
  bands.push({ zi, band, y, bottom });
  prevBottom = bottom; prevZone = zi;
}
const maxRight = Math.max(...[...T.values()].map((t) => t.x + t.w));

// ---------------------------------------------------------------- 3. 엣지 계획 (§4)
const edges = [];
for (const r of data.relations) {
  const [s, e] = CARD[r.card];
  edges.push({ id: `R_${r.from}__${r.to}__${r.fk}`, from: r.from, to: r.to, start: s, end: e, dashed: false, label: r.label ?? '' });
}
for (const f of data.flows ?? [])
  edges.push({ id: `F_${f.from}__${f.to}`, from: f.from, to: f.to, start: 'none', end: 'block', dashed: true, label: f.label ?? '' });

const gapY = new Map(); // 밴드 b 와 b+1 사이 gap → 사용한 lane 수
const gapX = new Map(); // col g-1 과 g 사이 gap → 사용한 lane 수
function laneY(b, eid) {
  const k = gapY.get(b) ?? 0; gapY.set(b, k + 1);
  const y = bands[b].bottom + C.LANE_START + C.LANE_STEP * k;
  const limit = (b + 1 < bands.length ? bands[b + 1].y : Infinity) - 60 - (b + 1 < bands.length && bands[b + 1].zi !== bands[b].zi ? C.ZONE_EXTRA : 0);
  if (y > limit) errors.push(`LANE OVERFLOW 밴드 gap ${b}: ${eid} — config.V_GUTTER 를 늘려라`);
  return y;
}
function laneX(g, eid) {
  const k = gapX.get(g) ?? 0; gapX.set(g, k + 1);
  const left = C.LEFT + (g - 1) * (C.TABLE_W + C.H_GUTTER) + C.TABLE_W;
  const x = left + C.LANE_START + C.LANE_STEP * k;
  if (x > left + C.H_GUTTER - C.LANE_START) errors.push(`LANE OVERFLOW 컬럼 gap ${g}: ${eid} — config.H_GUTTER 를 늘려라`);
  return x;
}

for (const e of edges) {
  const P = T.get(e.from), Q = T.get(e.to);
  const ov = data.overrides?.[e.id];
  if (ov?.fromSide && ov?.toSide) { e.type = 'manual'; e.ps = ov.fromSide; e.qs = ov.toSide; continue; }
  if (P.gb === Q.gb) {
    if (Math.abs(P.col - Q.col) === 1) { e.type = 'side'; e.ps = Q.col > P.col ? 'right' : 'left'; e.qs = Q.col > P.col ? 'left' : 'right'; }
    else { e.type = 'under'; e.ps = 'bottom'; e.qs = 'bottom'; }
  } else if (Q.gb > P.gb) { e.type = Q.gb - P.gb === 1 ? 'down1' : 'downN'; e.ps = 'bottom'; e.qs = 'top'; }
  else { e.type = P.gb - Q.gb === 1 ? 'up1' : 'upN'; e.ps = 'top'; e.qs = 'bottom'; }
}

// 앵커 분산 (§4.3): (테이블, 변) 마다 반대편 위치 순으로 정렬해 10px 격자 등분
const sideMap = new Map();
const cx = (t) => t.x + t.w / 2, cy = (t) => t.y + t.h / 2;
for (const e of edges) {
  for (const [end, tid, side, other] of [['p', e.from, e.ps, e.to], ['q', e.to, e.qs, e.from]]) {
    const key = `${tid}|${side}`;
    const o = T.get(other);
    const hint = side === 'top' || side === 'bottom' ? cx(o) : cy(o);
    if (!sideMap.has(key)) sideMap.set(key, []);
    sideMap.get(key).push({ e, end, hint });
  }
}
for (const [key, list] of sideMap) {
  const [tid, side] = key.split('|');
  const t = T.get(tid);
  const len = side === 'top' || side === 'bottom' ? t.w : t.h;
  list.sort((a, b) => a.hint - b.hint);
  // 하단·우측 = 20 의 배수, 상단·좌측 = 20 의 배수 + 10 → 출구 수직선과 입구 수직선이 같은 x 를 못 쓴다 (포개짐 구조적 차단)
  const odd = side === 'top' || side === 'left' ? 10 : 0;
  list.forEach((it, i) => {
    const off = Math.round((len * (i + 1)) / (list.length + 1) / 20) * 20 + odd;
    it.e[`${it.end}off`] = off + (data.overrides?.[it.e.id]?.[it.end === 'p' ? 'fromShift' : 'toShift'] ?? 0);
  });
  const offs = list.map((it) => it.e[`${it.end}off`]);
  if (new Set(offs).size !== offs.length || offs.some((o) => o <= 0 || o >= len))
    errors.push(`ANCHOR DUP ${tid} ${side}: ${offs.join(',')} — 변 길이에 비해 엣지가 많다`);
}
function anchor(t, side, off) {
  switch (side) {
    case 'top': return { x: t.x + off, y: t.y, fx: off / t.w, fy: 0 };
    case 'bottom': return { x: t.x + off, y: t.y + t.h, fx: off / t.w, fy: 1 };
    case 'left': return { x: t.x, y: t.y + off, fx: 0, fy: off / t.h };
    case 'right': return { x: t.x + t.w, y: t.y + off, fx: 1, fy: off / t.h };
  }
}
const colGapFor = (t) => (t.col >= 1 ? t.col : 1);

for (const e of edges) {
  const P = T.get(e.from), Q = T.get(e.to);
  e.a = anchor(P, e.ps, e.poff);
  e.b = anchor(Q, e.qs, e.qoff);
  const { a, b } = e;
  switch (e.type) {
    case 'manual': e.pts = data.overrides[e.id].points ?? []; break;
    case 'side': {
      const lx = laneX(Math.max(P.col, Q.col), e.id);
      e.pts = a.y === b.y ? [] : [[lx, a.y], [lx, b.y]];
      break;
    }
    case 'under': case 'down1': {
      const ly = laneY(P.gb, e.id); e.pts = [[a.x, ly], [b.x, ly]]; break;
    }
    case 'up1': { const ly = laneY(Q.gb, e.id); e.pts = [[a.x, ly], [b.x, ly]]; break; }
    case 'downN': {
      const y1 = laneY(P.gb, e.id), gx = laneX(colGapFor(Q), e.id), y2 = laneY(Q.gb - 1, e.id);
      e.pts = [[a.x, y1], [gx, y1], [gx, y2], [b.x, y2]]; break;
    }
    case 'upN': {
      const y1 = laneY(P.gb - 1, e.id), gx = laneX(colGapFor(Q), e.id), y2 = laneY(Q.gb, e.id);
      e.pts = [[a.x, y1], [gx, y1], [gx, y2], [b.x, y2]]; break;
    }
  }
  e.poly = [[a.x, a.y], ...e.pts, [b.x, b.y]];
  for (const [x, y] of e.pts) if (x % 10 || y % 10) errors.push(`WAYPOINT OFF-GRID ${e.id} (${x},${y})`);
}

// 범례 위치: 마지막 밴드 아래 우측
const LG = { x: r10(maxRight - 640), y: bands.at(-1).bottom + C.V_GUTTER, w: 640, h: 300 };

// 메모 (data.note): 범례 왼쪽, 자리가 없으면 범례 아래. 팀 공유용 설명을 ERD 안에 남긴다.
let NOTE = null;
if (data.note) {
  const lines = [data.note.title, ''];
  for (const s of data.note.sections ?? []) lines.push(s.heading, ...s.items.map((i) => `• ${i}`), '');
  if (data.note.footer?.length) lines.push(...data.note.footer);
  const w = data.note.width ?? 1040;
  const h = Math.ceil((24 + lines.length * 18) / 10) * 10;
  const x = LG.x - 40 - w >= C.LEFT ? LG.x - 40 - w : LG.x + LG.w - w;
  const y = x === LG.x + LG.w - w ? LG.y + LG.h + 40 : LG.y;
  const html = [`<b>${esc(data.note.title)}</b>`, '']
    .concat(...(data.note.sections ?? []).map((s) => [`<b>${esc(s.heading)}</b>`, ...s.items.map((i) => `• ${esc(i)}`), '']))
    .concat((data.note.footer ?? []).map((f) => `<span style="color:#666666">${esc(f)}</span>`));
  NOTE = { id: 'NOTE', x: r10(x), y, w, h, html: html.join('<br>') };
}

// ---------------------------------------------------------------- 4. 자가검증 (§11)
const tables = [...T.values()];
// (a) AABB 겹침
for (let i = 0; i < tables.length; i++) for (let j = i + 1; j < tables.length; j++) {
  const A = tables[i], B = tables[j];
  const ok = A.x + A.w + C.H_GUTTER <= B.x || B.x + B.w + C.H_GUTTER <= A.x ||
             A.y + A.h + C.V_GUTTER <= B.y || B.y + B.h + C.V_GUTTER <= A.y;
  if (!ok) errors.push(`OVERLAP(hard) ${A.id} vs ${B.id}`);
}
const boxes = [...labels, { id: 'LEGEND', ...LG }, ...(NOTE ? [NOTE] : [])];
for (const bx of boxes) for (const t of [...tables, ...boxes]) {
  if (bx === t) continue;
  const ok = bx.x + bx.w + 40 <= t.x || t.x + t.w + 40 <= bx.x || bx.y + bx.h + 10 <= t.y || t.y + t.h + 10 <= bx.y;
  if (!ok && !(t.id < bx.id && boxes.includes(t))) errors.push(`OVERLAP ${bx.id} vs ${t.id}`);
}
// (b) 엣지 관통: 8px 샘플링, 테이블 내부(경계 1px 안쪽)를 지나면 실패
const segs = (poly) => poly.slice(0, -1).map((p, i) => [p, poly[i + 1]]);
for (const e of edges) for (const [[x1, y1], [x2, y2]] of segs(e.poly)) {
  if (x1 !== x2 && y1 !== y2) errors.push(`DIAGONAL ${e.id} (${x1},${y1})->(${x2},${y2})`);
  const n = Math.max(1, Math.ceil(Math.hypot(x2 - x1, y2 - y1) / 8));
  for (let k = 0; k <= n; k++) {
    const x = x1 + ((x2 - x1) * k) / n, y = y1 + ((y2 - y1) * k) / n;
    for (const t of [...tables, ...boxes]) {
      if (x > t.x + 1 && x < t.x + t.w - 1 && y > t.y + 1 && y < t.y + t.h - 1) {
        errors.push(`EDGE THRU ${t.id}: ${e.id} at (${Math.round(x)},${Math.round(y)})`); k = n + 1; break;
      }
    }
  }
}
// (c) 포개짐(collinear): 서로 다른 엣지의 같은 x 수직 / 같은 y 수평 구간이 겹치면 실패
const ov1 = (a1, a2, b1, b2) => Math.min(Math.max(a1, a2), Math.max(b1, b2)) - Math.max(Math.min(a1, a2), Math.min(b1, b2));
for (let i = 0; i < edges.length; i++) for (let j = i + 1; j < edges.length; j++) {
  for (const [[ax1, ay1], [ax2, ay2]] of segs(edges[i].poly)) for (const [[bx1, by1], [bx2, by2]] of segs(edges[j].poly)) {
    if (ax1 === ax2 && bx1 === bx2 && ax1 === bx1 && ov1(ay1, ay2, by1, by2) > 0) errors.push(`COLLINEAR x=${ax1}: ${edges[i].id} / ${edges[j].id}`);
    if (ay1 === ay2 && by1 === by2 && ay1 === by1 && ov1(ax1, ax2, bx1, bx2) > 0) errors.push(`COLLINEAR y=${ay1}: ${edges[i].id} / ${edges[j].id}`);
  }
}
if (errors.length) fail();

// ---------------------------------------------------------------- 5. Flyway drift (§0) — 경고만
const flywayDir = path.resolve(data.flywayDir ?? 'server/src/main/resources/db/migration');
const sqlFiles = fs.existsSync(flywayDir) ? fs.readdirSync(flywayDir).filter((f) => /^V\d+.*\.sql$/i.test(f))
  .sort((a, b) => Number(a.match(/^V(\d+)/i)[1]) - Number(b.match(/^V(\d+)/i)[1])) : [];
const flywayZones = data.zones.filter((z) => z.source === 'flyway').map((z) => z.id);
if (!sqlFiles.length) {
  console.log(`ℹ️  Flyway 마이그레이션 없음 (${path.relative(process.cwd(), flywayDir)}) → 설계 모드, drift 검사 생략`);
} else {
  const schema = parseFlyway(sqlFiles.map((f) => fs.readFileSync(path.join(flywayDir, f), 'utf8')).join('\n;\n'));
  const erd = new Map(tables.filter((t) => flywayZones.includes(t.zone)).map((t) => [t.id, t]));
  for (const [name, cols] of schema) {
    const t = erd.get(name);
    if (!t) { warns.push(`DRIFT 테이블 ${name}: SQL 에 있고 ERD 에 없음`); continue; }
    for (const [cn, ty] of cols) {
      const c = t.columns.find((x) => x.name === cn);
      if (!c) warns.push(`DRIFT ${name}.${cn}: SQL 에 있고 ERD 에 없음`);
      else if (norm(c.type) !== norm(ty)) warns.push(`DRIFT ${name}.${cn}: 타입 ERD=${c.type} SQL=${ty}`);
    }
    for (const c of t.columns) if (!cols.has(c.name)) warns.push(`DRIFT ${name}.${c.name}: ERD 에 있고 SQL 에 없음`);
  }
  for (const id of erd.keys()) if (!schema.has(id)) warns.push(`DRIFT 테이블 ${id}: ERD 에 있고 SQL 에 없음`);
}
function norm(t) { return String(t).toUpperCase().replace(/\s+/g, '').replace(/^ENUM\(.*\)$/, 'ENUM').replace(/^(BOOL|BOOLEAN)$/, 'TINYINT(1)'); }
function parseFlyway(sql) {
  sql = sql.replace(/--[^\n]*/g, '').replace(/\/\*[\s\S]*?\*\//g, '');
  const out = new Map();
  const splitTop = (body) => { const parts = []; let d = 0, cur = '';
    for (const ch of body) { if (ch === '(') d++; if (ch === ')') d--; if (ch === ',' && d === 0) { parts.push(cur); cur = ''; } else cur += ch; }
    if (cur.trim()) parts.push(cur); return parts; };
  const colOf = (def) => { const m = def.trim().match(/^`?(\w+)`?\s+([A-Za-z]+(?:\s*\([^)]*\))?)/);
    return m && !/^(PRIMARY|KEY|UNIQUE|INDEX|CONSTRAINT|FOREIGN|CHECK|FULLTEXT|SPATIAL)$/i.test(m[1]) ? [m[1], m[2]] : null; };
  const re = /CREATE\s+TABLE\s+(?:IF\s+NOT\s+EXISTS\s+)?`?(\w+)`?\s*\(/gi;
  let m;
  while ((m = re.exec(sql))) {
    let d = 1, i = re.lastIndex;
    while (i < sql.length && d) { if (sql[i] === '(') d++; if (sql[i] === ')') d--; i++; }
    const cols = new Map();
    for (const def of splitTop(sql.slice(re.lastIndex, i - 1))) { const c = colOf(def); if (c) cols.set(c[0], c[1]); }
    out.set(m[1], cols);
  }
  for (const a of sql.matchAll(/ALTER\s+TABLE\s+`?(\w+)`?\s+([\s\S]*?);/gi)) {
    const cols = out.get(a[1]); if (!cols) continue;
    for (const clause of splitTop(a[2])) {
      const c = clause.trim();
      let x;
      if ((x = c.match(/^ADD\s+(?:COLUMN\s+)?([\s\S]+)$/i)) && !/^ADD\s+(CONSTRAINT|INDEX|KEY|UNIQUE|PRIMARY|FOREIGN)/i.test(c)) { const col = colOf(x[1]); if (col) cols.set(col[0], col[1]); }
      else if ((x = c.match(/^DROP\s+(?:COLUMN\s+)?`?(\w+)`?$/i))) cols.delete(x[1]);
      else if ((x = c.match(/^(?:MODIFY|CHANGE)\s+(?:COLUMN\s+)?/i))) warns.push(`DRIFT 파서: '${c.slice(0, 60)}' 는 수동 확인`);
    }
  }
  return out;
}

// ---------------------------------------------------------------- 6. XML
const X = [];
const W = (s) => X.push(s);
W('<mxfile host="app.diagrams.net">');
W('  <diagram name="ERD" id="drive-rule-erd">');
W(`    <mxGraphModel dx="1600" dy="1000" grid="1" gridSize="10" guides="1" tooltips="1" connect="1" arrows="1" fold="1" page="1" pageScale="1" pageWidth="${r10(maxRight + 200)}" pageHeight="${r10(Math.max(LG.y + LG.h, NOTE ? NOTE.y + NOTE.h : 0) + 100)}" math="0" shadow="0">`);
W('      <root>');
W('        <mxCell id="0"/>');
W('        <mxCell id="1" parent="0"/>');

for (const l of labels) {
  W(`        <mxCell id="${l.id}" value="${esc(l.value)}" style="text;html=1;align=left;verticalAlign=middle;whiteSpace=wrap;fontSize=${l.zone ? 14 : 12};fontStyle=${l.zone ? 1 : 2};strokeColor=none;fillColor=none;fontColor=${l.zone ? 'default' : '#666666'};" vertex="1" parent="1">`);
  W(`          <mxGeometry x="${l.x}" y="${l.y}" width="${l.w}" height="${l.h}" as="geometry"/>`);
  W('        </mxCell>');
}
dividers.forEach((y, i) => {
  W(`        <mxCell id="DIV_${i}" value="" style="endArrow=none;html=1;dashed=1;strokeColor=#666666;" edge="1" parent="1">`);
  W('          <mxGeometry relative="1" as="geometry">');
  W(`            <mxPoint x="40" y="${y}" as="sourcePoint"/>`);
  W(`            <mxPoint x="${r10(maxRight)}" y="${y}" as="targetPoint"/>`);
  W('          </mxGeometry>');
  W('        </mxCell>');
});

const TABLE_STYLE = (fill) => `shape=table;startSize=${C.HEADER_H};container=1;collapsible=0;childLayout=tableLayout;fixedRows=1;rowLines=1;fontStyle=1;align=center;resizeLast=1;html=1;rounded=1;arcSize=14;absoluteArcSize=1;fillColor=${fill};horizontal=1;swimlaneFillColor=default;fontSize=12;`;
const ROW_STYLE = 'shape=tableRow;horizontal=0;startSize=0;swimlaneHead=0;swimlaneBody=0;fillColor=none;collapsible=0;dropTarget=0;points=[[0,0.5],[1,0.5]];portConstraint=eastwest;fontSize=12;top=0;left=0;right=0;bottom=0;';
const CELL_STYLE = 'shape=partialRectangle;connectable=0;fillColor=none;top=0;left=0;bottom=0;right=0;overflow=hidden;whiteSpace=wrap;html=1;fontSize=12;';
function emitTable(id, value, fill, x, y, columns) {
  W(`        <mxCell id="${id}" value="${esc(value)}" style="${TABLE_STYLE(fill)}" vertex="1" parent="1">`);
  W(`          <mxGeometry x="${x}" y="${y}" width="${C.TABLE_W}" height="${C.HEADER_H + C.ROW_H * columns.length}" as="geometry"/>`);
  W('        </mxCell>');
  columns.forEach((col, i) => {
    const rid = `${id}_r${i}`;
    W(`        <mxCell id="${rid}" value="" style="${ROW_STYLE}" vertex="1" parent="${id}">`);
    W(`          <mxGeometry y="${C.HEADER_H + C.ROW_H * i}" width="${C.TABLE_W}" height="${C.ROW_H}" as="geometry"/>`);
    W('        </mxCell>');
    const pk = hasCons(col, 'PK');
    CELL.forEach((c, k) => {
      W(`        <mxCell id="${rid}_c${k}" value="${esc(col[c.key] ?? '')}" style="${CELL_STYLE}${c.align}fontStyle=${pk ? 1 : 0};" vertex="1" parent="${rid}">`);
      W(`          <mxGeometry x="${c.x}" width="${c.w}" height="${C.ROW_H}" as="geometry">`);
      W(`            <mxRectangle width="${c.w}" height="${C.ROW_H}" as="alternateBounds"/>`);
      W('          </mxGeometry>');
      W('        </mxCell>');
    });
  });
}
for (const t of tables) emitTable(`T_${t.id}`, `${t.id} (${t.kr})`, data.zones[t.zi].fill, t.x, t.y, t.columns);

const EDGE_BASE = 'edgeStyle=orthogonalEdgeStyle;rounded=0;orthogonalLoop=1;jettySize=auto;html=1;fontSize=12;startSize=10;endSize=10;';
const FLOW_STYLE = 'dashed=1;strokeColor=#9673a6;fontColor=#9673a6;labelBackgroundColor=default;';
for (const e of edges) {
  const style = `${EDGE_BASE}exitX=${e.a.fx};exitY=${e.a.fy};exitDx=0;exitDy=0;entryX=${e.b.fx};entryY=${e.b.fy};entryDx=0;entryDy=0;` +
    `startArrow=${e.start};startFill=0;endArrow=${e.end};endFill=${e.end === 'block' ? 1 : 0};${e.dashed ? FLOW_STYLE : ''}`;
  W(`        <mxCell id="${e.id}" value="${esc(e.label)}" style="${style}" edge="1" parent="1" source="T_${e.from}" target="T_${e.to}">`);
  W('          <mxGeometry relative="1" as="geometry">');
  if (e.pts.length) {
    W('            <Array as="points">');
    for (const [x, y] of e.pts) W(`              <mxPoint x="${x}" y="${y}"/>`);
    W('            </Array>');
  }
  W('          </mxGeometry>');
  W('        </mxCell>');
}

// 범례 (§9): 카디널리티 4종 + 데이터 이동 + 제약 설명 + 예제 엔티티
W(`        <mxCell id="LG_BG" value="" style="rounded=0;whiteSpace=wrap;html=1;fillColor=none;strokeColor=#36393d;" vertex="1" parent="1">`);
W(`          <mxGeometry x="${LG.x}" y="${LG.y}" width="${LG.w}" height="${LG.h}" as="geometry"/>`);
W('        </mxCell>');
const lgText = (id, x, y, w, h, v, extra = '') => {
  W(`        <mxCell id="${id}" value="${esc(v)}" style="text;html=1;align=left;verticalAlign=middle;whiteSpace=wrap;fontSize=12;strokeColor=none;fillColor=none;${extra}" vertex="1" parent="1">`);
  W(`          <mxGeometry x="${x}" y="${y}" width="${w}" height="${h}" as="geometry"/>`);
  W('        </mxCell>');
};
lgText('LG_TITLE', LG.x + 20, LG.y + 10, 200, 30, '범례', 'fontStyle=1;');
const samples = [
  ['1:0..N', '1 : 0..N (필수 FK)', ERs('ERone', 'ERzeroToMany')],
  ['0..1:0..N', '0..1 : 0..N (NULL 허용 FK)', ERs('ERzeroToOne', 'ERzeroToMany')],
  ['1:1', '1 : 1 (FK + UQ)', ERs('ERone', 'ERone')],
  ['1:1..N', '1 : 1..N', ERs('ERone', 'ERoneToMany')],
  ['flow', '서버 ↔ 차량 데이터 이동 (FK 아님)', `startArrow=none;endArrow=block;endFill=1;${FLOW_STYLE}`],
];
function ERs(s, e) { return `startArrow=${s};startFill=0;endArrow=${e};endFill=0;startSize=10;endSize=10;`; }
samples.forEach(([k, txt, st], i) => {
  const y = LG.y + 60 + i * 40;
  W(`        <mxCell id="LG_E${i}" value="" style="html=1;rounded=0;${st}" edge="1" parent="1">`);
  W('          <mxGeometry relative="1" as="geometry">');
  W(`            <mxPoint x="${LG.x + 20}" y="${y}" as="sourcePoint"/>`);
  W(`            <mxPoint x="${LG.x + 140}" y="${y}" as="targetPoint"/>`);
  W('          </mxGeometry>');
  W('        </mxCell>');
  lgText(`LG_T${i}`, LG.x + 160, y - 20, 260, 40, txt);
});
lgText('LG_CONS', LG.x + 20, LG.y + 260, 600, 30, 'PK 기본키 · FK 외래키 · NN NOT NULL · UQ UNIQUE — 부모(PK 쪽) → 자식(FK 쪽) 방향', 'fontSize=11;');
data.zones.forEach((z, i) => {
  W(`        <mxCell id="LG_Z${i}" value="${esc(z.label)}" style="rounded=1;whiteSpace=wrap;html=1;fontSize=11;fillColor=${z.fill};strokeColor=#36393d;" vertex="1" parent="1">`);
  W(`          <mxGeometry x="${LG.x + 440}" y="${LG.y + 50 + i * 40}" width="180" height="30" as="geometry"/>`);
  W('        </mxCell>');
});

if (NOTE) {
  // html 은 이미 셀 안 텍스트를 escape 했다. 속성값으로 넣기 위해 한 번 더 escape 한다.
  W(`        <mxCell id="NOTE" value="${esc(NOTE.html)}" style="shape=note;size=20;whiteSpace=wrap;html=1;align=left;verticalAlign=top;spacingLeft=16;spacingTop=8;spacingRight=16;fontSize=12;fillColor=light-dark(#fff2cc,#3d3420);strokeColor=#d6b656;" vertex="1" parent="1">`);
  W(`          <mxGeometry x="${NOTE.x}" y="${NOTE.y}" width="${NOTE.w}" height="${NOTE.h}" as="geometry"/>`);
  W('        </mxCell>');
}

W('      </root>');
W('    </mxGraphModel>');
W('  </diagram>');
W('</mxfile>');

fs.mkdirSync(path.dirname(outPath), { recursive: true });
fs.writeFileSync(outPath, X.join('\n') + '\n', 'utf8');

// ---------------------------------------------------------------- 7. 리포트
console.log('| id | zone | band | col | x | y | w | h |');
for (const t of tables) console.log(`| ${t.id} | ${t.zone} | ${t.band} | ${t.col} | ${t.x} | ${t.y} | ${t.w} | ${t.h} |`);
for (const w of warns) console.log(`⚠️  ${w}`);
console.log(`✅ 겹침 0 · 앵커 중복 0 · 엣지 관통 0 · 포개짐 0 — 테이블 ${tables.length} · 관계 ${data.relations.length} · 데이터 이동 ${(data.flows ?? []).length}`);
console.log(`→ ${path.relative(process.cwd(), outPath)}`);

function fail() {
  for (const e of errors) console.error(`❌ ${e}`);
  console.error(`실패 ${errors.length}건 — 파일을 쓰지 않았다.`);
  process.exit(1);
}
