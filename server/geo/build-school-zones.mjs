#!/usr/bin/env node
// 어린이보호구역 좌표 파일을 만든다 (결정 010).
//
//   node server/geo/build-school-zones.mjs                 # 공공데이터포털에서 받아 만든다
//   node server/geo/build-school-zones.mjs --input a.json  # 받아 둔 원본으로 만든다 (JSON 배열 또는 CSV)
//   ... --out <경로>  --allow-shrink
//
// 결과: server/src/main/resources/geo/school-zones.json — 커밋해서 PR 로 올린다 (반기마다).
// 서버는 부팅 때 이 파일을 검사하고 해시를 계산해 /api/v1/geo/school-zones 로 내보낸다.

import { readFileSync, writeFileSync, existsSync, mkdirSync } from 'node:fs';
import { dirname, resolve } from 'node:path';
import { fileURLToPath } from 'node:url';

const HERE = dirname(fileURLToPath(import.meta.url));
const DEFAULT_OUT = resolve(HERE, '../src/main/resources/geo/school-zones.json');

const PORTAL = 'https://www.data.go.kr';
const PUBLIC_DATA_PK = '15012891';
const SOURCE = '공공데이터포털 전국어린이보호구역표준데이터 (data.go.kr/data/15012891)';

// 국내 범위 — 서버 GeoDataset 과 같은 값
const LAT = [33.0, 38.7];
const LON = [124.5, 132.0];
const DECIMALS = 5; // 약 1m
const MAX_SHRINK = 0.10;
const LINE = Symbol('원본 줄');
const MAX_BAD_ROWS = 0.01; // 좌표가 잘못된 행이 이보다 많으면 멈춘다

// 원본 칸 이름 (포털 내부 코드 / 화면 CSV 의 한글 머리글)
const FIELDS = {
  lat: ['LATITUDE', '위도'],
  lon: ['LONGITUDE', '경도'],
  designated: ['CHLD_PRTN_ZON_YN', '어린이보호구역지정여부'],
  referenceDate: ['REFERENCE_DATE', '데이터기준일자'],
};

function parseArgs(argv) {
  const args = { input: null, out: DEFAULT_OUT, allowShrink: false };
  for (let i = 0; i < argv.length; i++) {
    const a = argv[i];
    if (a === '--input') args.input = argv[++i];
    else if (a === '--out') args.out = resolve(argv[++i]);
    else if (a === '--allow-shrink') args.allowShrink = true;
    else throw new Error(`모르는 인자: ${a}`);
  }
  return args;
}

// ---------- 원본 읽기 ----------

async function fetchPortal() {
  const header = await getJson(`${PORTAL}/download/columList.json?pk=${PUBLIC_DATA_PK}&ext=JSON`);
  const total = header.totalCount;
  const table = header.tableVO.svcTableNm;
  const cols = header.tableVO.colNmList;
  const perPage = 10000;
  const rows = [];
  // 포털 화면과 같은 방식: page 는 1부터
  for (let page = 1; (page - 1) * perPage < total; page++) {
    const q = new URLSearchParams({ publicDataPk: PUBLIC_DATA_PK, svcTableNm: table, perPage, page, totalCount: total });
    cols.forEach((c) => q.append('colNmList', c));
    const chunk = await getJson(`${PORTAL}/download/standard.json?${q}`);
    if (!Array.isArray(chunk)) {
      throw new Error(`포털 ${page}쪽 응답이 행 배열이 아닙니다: ${JSON.stringify(chunk).slice(0, 200)}`);
    }
    rows.push(...chunk);
    console.error(`  받음 ${rows.length} / ${total}`);
  }
  if (rows.length !== total) {
    throw new Error(`포털이 ${total}건이라고 했는데 ${rows.length}건을 받았습니다 — 다시 받으세요`);
  }
  // 쪽 나눔이 흔들리면 앞 쪽 행이 다시 오고 다른 행이 빠진다. 건수는 맞아도 좌표 합치기가 그 구멍을 가린다
  const distinct = new Set(rows.map((r) => JSON.stringify(r))).size;
  if (distinct !== total) {
    throw new Error(`같은 행이 ${total - distinct}건 겹쳐 왔습니다 — 쪽 나눔이 흔들렸을 수 있으니 다시 받으세요`);
  }
  return rows;
}

async function getJson(url) {
  const res = await fetch(url, { headers: { Accept: 'application/json' } });
  if (!res.ok) throw new Error(`${res.status} ${url}`);
  return res.json();
}

function readInput(path) {
  const text = decode(readFileSync(path)).replace(/^﻿/, '');
  if (text.trimStart().startsWith('[')) return JSON.parse(text);
  if (text.trimStart().startsWith('{')) {
    const obj = JSON.parse(text);
    const arr = obj.records ?? obj.data ?? obj.items;
    if (!Array.isArray(arr)) throw new Error('JSON 에서 행 배열(records / data / items)을 찾지 못했습니다');
    return arr;
  }
  return parseCsv(text);
}

// 포털 CSV 는 UTF-8 일 수도, 엑셀용 CP949(EUC-KR) 일 수도 있다
function decode(buf) {
  try {
    return new TextDecoder('utf-8', { fatal: true }).decode(buf);
  }
  catch {
    return new TextDecoder('euc-kr').decode(buf);
  }
}

function parseCsv(text) {
  const rows = [];
  let row = [], cell = '', quoted = false;
  for (let i = 0; i < text.length; i++) {
    const ch = text[i];
    if (quoted) {
      if (ch === '"' && text[i + 1] === '"') { cell += '"'; i++; }
      else if (ch === '"') quoted = false;
      else cell += ch;
    } else if (ch === '"') quoted = true;
    else if (ch === ',') { row.push(cell); cell = ''; }
    else if (ch === '\n' || ch === '\r') {
      if (ch === '\r' && text[i + 1] === '\n') i++;
      row.push(cell); cell = '';
      if (row.some((c) => c !== '')) rows.push(row);
      row = [];
    } else cell += ch;
  }
  row.push(cell);
  if (row.some((c) => c !== '')) rows.push(row);
  const [head, ...body] = rows;
  // 원본 줄 번호를 남긴다 (머리글이 1줄) — 오류 보고가 원본 파일의 그 줄을 가리키게
  return body.map((r, i) => Object.defineProperty(Object.fromEntries(head.map((h, j) => [h.trim(), r[j]])),
    LINE, { value: i + 2 }));
}

function field(row, name) {
  for (const key of FIELDS[name]) {
    if (row[key] !== undefined) return row[key];
  }
  return undefined;
}

// ---------- 만들기 ----------

export function build(rows) {
  if (rows.length === 0) throw new Error('원본이 비어 있습니다');
  if (field(rows[0], 'lat') === undefined || field(rows[0], 'lon') === undefined) {
    throw new Error(`위도·경도 칸을 찾지 못했습니다. 칸 이름: ${Object.keys(rows[0]).join(', ')}`);
  }
  const errors = [];
  const seen = new Set();
  const zones = [];
  let excluded = 0;
  let referenceDate = '';
  rows.forEach((r, i) => {
    if (String(field(r, 'designated') ?? '').trim().toUpperCase() === 'N') {
      excluded++;
      return;
    }
    const lat = Number(String(field(r, 'lat')).trim());
    const lon = Number(String(field(r, 'lon')).trim());
    if (!Number.isFinite(lat) || !Number.isFinite(lon)
        || lat < LAT[0] || lat > LAT[1] || lon < LON[0] || lon > LON[1]) {
      errors.push(`${r[LINE] ? `${r[LINE]}줄` : `${i + 1}번째 행`}: 좌표 (${field(r, 'lat')}, ${field(r, 'lon')}) 가 국내 범위 밖이거나 숫자가 아닙니다`);
      return;
    }
    const p = [round(lat), round(lon)];
    const key = `${p[0]},${p[1]}`;
    if (!seen.has(key)) {
      seen.add(key);
      zones.push(p);
    }
    const d = String(field(r, 'referenceDate') ?? '').trim();
    if (/^\d{4}-\d{2}-\d{2}$/.test(d) && d > referenceDate) referenceDate = d;
  });
  // 지자체 한두 곳의 잘못된 행은 빼고 알린다. 많으면 원본(칸 뒤바뀜 등)을 의심해 멈춘다
  if (errors.length > 0) {
    const list = `\n  ${errors.slice(0, 20).join('\n  ')}`;
    if (errors.length > rows.length * MAX_BAD_ROWS) {
      throw new Error(`좌표 오류 ${errors.length}건 (${MAX_BAD_ROWS * 100}% 초과) — 원본을 확인하세요${list}`);
    }
    console.error(`경고: 좌표 오류 ${errors.length}건은 뺐습니다${list}`);
  }
  if (zones.length === 0) throw new Error('남은 좌표가 없습니다');
  zones.sort((a, b) => a[0] - b[0] || a[1] - b[1]);
  return { schema: 1, dataset: 'school-zones', source: SOURCE, referenceDate: referenceDate || null,
    count: zones.length, zones, stats: { rows: rows.length, excluded, invalid: errors.length } };
}

function round(v) {
  return Number(v.toFixed(DECIMALS));
}

// 한 줄에 한 곳 — 반기 갱신 PR 에서 바뀐 곳이 diff 로 보이게
export function serialize(data) {
  const head = JSON.stringify({ schema: data.schema, dataset: data.dataset, source: data.source,
    referenceDate: data.referenceDate, count: data.count }, null, 2);
  const body = data.zones.map((z) => `    [${z[0]},${z[1]}]`).join(',\n');
  return `${head.slice(0, -2)},\n  "zones": [\n${body}\n  ]\n}\n`;
}

async function main() {
  const args = parseArgs(process.argv.slice(2));
  console.error(args.input ? `원본: ${args.input}` : '원본: 공공데이터포털에서 받는 중');
  const rows = args.input ? readInput(args.input) : await fetchPortal();
  const data = build(rows);

  if (existsSync(args.out)) {
    const prev = JSON.parse(readFileSync(args.out, 'utf8'));
    const shrink = (prev.count - data.count) / prev.count;
    console.error(`이전 ${prev.count}곳 → 이번 ${data.count}곳`);
    if (shrink > MAX_SHRINK && !args.allowShrink) {
      throw new Error(`이전보다 ${(shrink * 100).toFixed(1)}% 줄었습니다 — 원본을 다 받았는지 확인하고, 맞으면 --allow-shrink`);
    }
  }
  mkdirSync(dirname(args.out), { recursive: true });
  writeFileSync(args.out, serialize(data));
  console.error(`원본 ${data.stats.rows}행 · 지정 해제(N) 제외 ${data.stats.excluded} · 좌표 오류 제외 ${data.stats.invalid} · 같은 좌표 합침 → ${data.count}곳`);
  console.error(`기준일 ${data.referenceDate} → ${args.out}`);
}

// Windows 는 드라이브 문자 대소문자가 다를 수 있다 — 아무것도 안 하고 성공으로 끝나지 않게
const same = (a, b) => (process.platform === 'win32' ? a.toLowerCase() === b.toLowerCase() : a === b);
if (process.argv[1] && same(resolve(process.argv[1]), fileURLToPath(import.meta.url))) {
  main().catch((e) => {
    console.error(`실패: ${e.message}`);
    process.exit(1);
  });
}
