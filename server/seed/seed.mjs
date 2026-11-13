// 2차 PT 시연 카드를 검수 CMS API 로 넣는다 (모두 DRAFT — 검수자 2명이 승인해야 차에 나간다).
//
//   BASE_URL=http://localhost:8080 ADMIN_API_KEY=local node server/seed/seed.mjs
//
// 같은 공포 번호의 법령 개정이 이미 있으면(409) 그 행을 찾아 쓴다.
// 같은 제목의 카드(회수된 것 제외)가 이미 있으면 건너뛴다 — 시드에 카드를 더한 뒤 다시 돌려도 새 카드만 들어간다.
// 이미 들어간 카드의 내용을 시드에 맞춰 고치지는 않는다 (CMS 에서 고친 내용을 덮어쓰지 않으려고).
import { readFile } from 'node:fs/promises';

const BASE_URL = process.env.BASE_URL ?? 'http://localhost:8080';
const KEY = process.env.ADMIN_API_KEY;
if (!KEY) {
  console.error('ADMIN_API_KEY 환경변수가 필요합니다');
  process.exit(1);
}

const seed = JSON.parse(await readFile(new URL('./pt-cards.json', import.meta.url), 'utf8'));

async function admin(method, path, body) {
  const res = await fetch(`${BASE_URL}/api/v1/admin${path}`, {
    method,
    headers: { 'Content-Type': 'application/json', 'X-Admin-Key': KEY },
    body: body === undefined ? undefined : JSON.stringify(body),
  });
  const text = await res.text();
  return { status: res.status, body: text ? JSON.parse(text) : null };
}

const revisionIdByLawNo = {};
for (const rev of seed.lawRevisions) {
  let res = await admin('POST', '/law-revisions', rev);
  if (res.status === 409) {
    const all = await admin('GET', '/law-revisions');
    res = { status: 200, body: all.body.find((r) => r.lawNo === rev.lawNo) };
  }
  if (res.status >= 300 || !res.body) throw new Error(`법령 개정 ${rev.lawNo}: ${JSON.stringify(res.body)}`);
  revisionIdByLawNo[rev.lawNo] = res.body.id;
  console.log(`법령 개정 ${rev.lawNo} → id ${res.body.id}`);
}

const existing = await admin('GET', '/cards');
if (existing.status !== 200) throw new Error(`카드 목록: ${existing.status}`);
const liveTitles = new Set(existing.body.filter((c) => c.status !== 'RETIRED').map((c) => c.title));

for (const { _demo, revisionLawNo, ...card } of seed.cards) {
  if (liveTitles.has(card.title)) {
    console.log(`카드 ${_demo} — 같은 제목이 있어 건너뜀`);
    continue;
  }
  if (revisionLawNo) card.revisionId = revisionIdByLawNo[revisionLawNo];
  const res = await admin('POST', '/cards', card);
  if (res.status !== 201) {
    console.error(`카드 ${_demo} 실패 (${res.status}):`, JSON.stringify(res.body, null, 2));
    process.exitCode = 1;
    continue;
  }
  console.log(`카드 ${_demo} → id ${res.body.id} (DRAFT)`);
}
