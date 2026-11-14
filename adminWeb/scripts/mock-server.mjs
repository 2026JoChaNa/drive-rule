import { createServer } from 'node:http';
import { pathToFileURL } from 'node:url';

// DEMO ONLY: every record below is fictional UI test data, not legally verified content.
// Memory is intentionally reset on every start. No real backend or external API is contacted.
const CONDITIONS = [
  { key: 'right_turn_traffic_light', question: '우회전 신호등이 있었나요?', options: [{ key: 'yes', label: '있었어요' }, { key: 'no', label: '없었어요' }] },
  { key: 'front_traffic_light', question: '전방 차량 신호는 무슨 색이었나요?', options: [{ key: 'red', label: '빨간불' }, { key: 'yellow', label: '노란불' }, { key: 'green', label: '초록불' }] },
  { key: 'crosswalk_light', question: '횡단보도 보행 신호는 무슨 색이었나요?', options: [{ key: 'green', label: '초록불' }, { key: 'green_flashing', label: '녹색 점멸' }, { key: 'red', label: '빨간불' }] },
  { key: 'pedestrian_present', question: '건너거나 건너려는 사람이 있었나요?', options: [{ key: 'yes', label: '있었어요' }, { key: 'no', label: '없었어요' }] },
];
const CARD_TYPES = ['SHORT', 'CONDITIONAL', 'FIXED_RESPONSE'];
const CARD_STATUSES = ['DRAFT', 'IN_REVIEW', 'PUBLISHED', 'RETIRED'];
const TRIAGES = ['PENDING', 'APPLICABLE', 'NOT_APPLICABLE'];
const REQUEST_STATUSES = ['OPEN', 'RESOLVED', 'DISMISSED'];
const REQUEST_TYPES = ['ERROR_REPORT', 'ANSWER_HOLD'];
const EVENTS = ['SCHOOL_ZONE_ENTRY', 'PARKING_DESTINATION', 'PARKING_HIGHWAY', 'RIGHT_TURN_CROSSWALK'];
const LIMITS = { sourceOrg: 100, topic: 40, title: 100, dontText: 300, doText: 300, sceneKey: 40, ttsText: 500, legalBasis: 200, lawExcerpt: 20000, penaltyText: 300, exceptions: 20000 };
const CONTENT_FIELDS = ['revisionId', 'cardType', 'changeKind', 'audience', 'eventTag', ...Object.keys(LIMITS), 'effectiveOn', 'decision'];
const now = () => new Date(Date.now() + 9 * 60 * 60 * 1000).toISOString().slice(0, 19);
const ago = days => new Date(Date.now() - days * 86400000 + 9 * 3600000).toISOString().slice(0, 19);
const clone = value => structuredClone(value);
const blank = value => typeof value !== 'string' || !value.trim();
const normalize = value => typeof value === 'string' && value.trim() ? value.trim() : null;
const isDate = value => typeof value === 'string' && /^\d{4}-\d{2}-\d{2}$/.test(value) && !Number.isNaN(Date.parse(`${value}T00:00:00Z`)) && new Date(`${value}T00:00:00Z`).toISOString().slice(0, 10) === value;

class ApiError extends Error {
  constructor(status, code, detail, errors = []) { super(detail); Object.assign(this, { status, code, errors }); }
}
const invalid = errors => { throw new ApiError(400, 'INVALID_REQUEST', '요청 값이 올바르지 않습니다', errors); };
const conflict = (code, message) => { throw new ApiError(409, code, message); };
const requiredEnum = (value, options, name) => { if (!options.includes(value)) invalid([`${name}: ${options.join(', ')} 중 하나여야 합니다`]); };
const queryEnum = (url, name, options) => {
  const value = url.searchParams.get(name);
  if (value !== null) requiredEnum(value, options, name);
  return value;
};

function seedState() {
  const laws = [
    { id: 1, lawName: '[데모] 도로교통법 일부개정', lawNo: 'DEMO-2026-001', sourceKey: 'MANUAL-DEMO-001', promulgatedOn: ago(20).slice(0, 10), triageStatus: 'APPLICABLE', createdAt: ago(20), sourceUrl: null },
    { id: 2, lawName: '[데모] 도로교통법 시행규칙 일부개정', lawNo: 'DEMO-2026-002', sourceKey: 'MANUAL-DEMO-002', promulgatedOn: ago(4).slice(0, 10), triageStatus: 'PENDING', createdAt: ago(4), sourceUrl: null },
    { id: 3, lawName: '[데모] 운전면허 행정서식 개정', lawNo: 'DEMO-2026-003', sourceKey: 'MANUAL-DEMO-003', promulgatedOn: ago(12).slice(0, 10), triageStatus: 'NOT_APPLICABLE', createdAt: ago(12), sourceUrl: null },
  ];
  const base = {
    revisionId: 1, cardType: 'SHORT', changeKind: 'LAW', audience: 'GENERAL', eventTag: null, sourceOrg: null, topic: null,
    dontText: null, doText: null, sceneKey: null, legalBasis: '데모용 근거 표기 · 실제 법령 대조 필요',
    lawExcerpt: '화면과 API 흐름을 시험하기 위한 예시 데이터입니다. 실제 법률 안내로 사용할 수 없습니다.',
    penaltyText: null, effectiveOn: ago(10).slice(0, 10), exceptions: null, decision: null,
    status: 'DRAFT', version: 1, verifiedAt: null, createdAt: ago(7), updatedAt: ago(1),
  };
  const rows = [
    { id: 1, title: '어린이보호구역 횡단보도 안내', ttsText: '보호구역 안내 문장이 표시되는 데모입니다.', eventTag: 'SCHOOL_ZONE_ENTRY', topic: 'school_zone_crosswalk', status: 'PUBLISHED', verifiedAt: ago(3), dontText: '주변을 확인하지 않고 진입', doText: '주변 상황을 먼저 확인' },
    { id: 2, title: '우회전 상황별 안내', ttsText: '상황에 맞는 안내를 선택하는 데모입니다.', cardType: 'CONDITIONAL', topic: 'right_turn', status: 'PUBLISHED', verifiedAt: ago(2), decision: { schema: 2, tables: [{ key: 'pedestrians', title: '보행자 확인', conditions: ['pedestrian_present'], rows: [{ when: { pedestrian_present: 'yes' }, text: '보행자가 있는 상황의 데모 안내', tts: '보행자가 있는 상황입니다.' }, { when: { pedestrian_present: 'no' }, text: '보행자가 없는 상황의 데모 안내', tts: '보행자가 없는 상황입니다.' }] }] } },
    { id: 3, title: '고속도로 정차·주차 안내', ttsText: '고속도로 안내 카드의 검수 흐름을 시험합니다.', eventTag: 'PARKING_HIGHWAY', status: 'IN_REVIEW', topic: 'highway_stopping', updatedAt: ago(0) },
    { id: 4, title: '어린이보호구역 주정차 안내', ttsText: '주정차 안내 카드 초안입니다.', eventTag: 'PARKING_DESTINATION', topic: 'school_zone_parking', revisionId: 2 },
    { id: 5, title: '운영 안내 문구 업데이트', ttsText: '운영 변경 출처를 표시하는 데모입니다.', cardType: 'FIXED_RESPONSE', changeKind: 'OPERATION', sourceOrg: 'DRIVE RULE 데모 운영팀', revisionId: null, audience: 'LIMITED' },
    { id: 6, title: '이전 안전거리 안내', ttsText: '회수된 카드 표시를 시험합니다.', status: 'RETIRED', verifiedAt: ago(10), createdAt: ago(15), updatedAt: ago(5) },
  ];
  const cards = rows.map(row => ({ ...clone(base), ...row }));
  // Local solo-development profile: one approval. Production's default remains two.
  const reviewers = [{ id: 1, name: '로컬 운영자' }];
  const reviews = [1, 2, 6].map((cardId, index) => ({ id: index + 1, cardId, reviewerId: 1, cardVersion: 1, decision: 'APPROVE', reviewedAt: ago(cardId === 6 ? 10 : 3) }));
  const requests = [
    { id: 1, requestType: 'ERROR_REPORT', cardId: 1, cardVersion: 1, topic: null, conditions: null, status: 'OPEN', createdAt: ago(0) },
    { id: 2, requestType: 'ANSWER_HOLD', cardId: null, cardVersion: null, topic: 'left_turn', conditions: { front_traffic_light: 'yellow' }, status: 'OPEN', createdAt: ago(1) },
    { id: 3, requestType: 'ANSWER_HOLD', cardId: null, cardVersion: null, topic: 'seatbelt', conditions: null, status: 'OPEN', createdAt: ago(2) },
    { id: 4, requestType: 'ERROR_REPORT', cardId: 2, cardVersion: 1, topic: null, conditions: null, status: 'RESOLVED', createdAt: ago(4) },
    { id: 5, requestType: 'ANSWER_HOLD', cardId: null, cardVersion: null, topic: 'left_turn', conditions: null, status: 'DISMISSED', createdAt: ago(6) },
  ];
  return { cards, laws, reviewers, reviews, requests };
}

function validateDecision(decision) {
  const errors = [];
  if (!decision || typeof decision !== 'object') return ['decision: 결정 테이블이 없습니다'];
  if (decision.schema !== 2) errors.push('decision.schema: 2 이어야 합니다');
  if (!Array.isArray(decision.tables) || decision.tables.length < 1 || decision.tables.length > 2) return [...errors, 'decision.tables: 소표는 1~2개여야 합니다'];
  const tableKeys = new Set();
  decision.tables.forEach((table, index) => {
    const at = `decision.tables[${index}]`;
    if (!table || typeof table !== 'object') { errors.push(`${at}: 비어 있습니다`); return; }
    if (blank(table.key)) errors.push(`${at}.key: 비어 있습니다`);
    else if (tableKeys.has(table.key)) errors.push(`${at}.key: 중복입니다`);
    tableKeys.add(table.key);
    if (blank(table.title)) errors.push(`${at}.title: 비어 있습니다`);
    if (!Array.isArray(table.conditions) || !table.conditions.length || table.conditions.length > 3 || new Set(table.conditions).size !== table.conditions.length) { errors.push(`${at}.conditions: 서로 다른 조건 1~3개가 필요합니다`); return; }
    const definitions = table.conditions.map(key => CONDITIONS.find(condition => condition.key === key));
    if (definitions.some(value => !value)) { errors.push(`${at}.conditions: 공통 목록에 없는 조건입니다`); return; }
    if (definitions.reduce((count, definition) => count * definition.options.length, 1) > 8) errors.push(`${at}.conditions: 선택지 조합은 8가지 이하여야 합니다`);
    if (!Array.isArray(table.rows) || !table.rows.length) { errors.push(`${at}.rows: 행이 1개 이상 있어야 합니다`); return; }
    const beforeRows = errors.length;
    table.rows.forEach((row, rowIndex) => {
      const rowAt = `${at}.rows[${rowIndex}]`;
      if (!row || typeof row !== 'object') { errors.push(`${rowAt}: 비어 있습니다`); return; }
      if (blank(row.text)) errors.push(`${rowAt}.text: 비어 있습니다`);
      if (blank(row.tts)) errors.push(`${rowAt}.tts: 비어 있습니다`);
      if (row.when != null && (typeof row.when !== 'object' || Array.isArray(row.when))) { errors.push(`${rowAt}.when: 조건 객체가 필요합니다`); return; }
      for (const [key, value] of Object.entries(row.when ?? {})) {
        const definition = definitions.find(item => item.key === key);
        if (!definition || !definition.options.some(option => option.key === value)) errors.push(`${rowAt}.when: 알려진 조건과 선택지를 사용해 주세요 (${key}=${value})`);
      }
    });
    if (errors.length !== beforeRows) return;
    const combinations = definitions.reduce((previous, definition) => previous.flatMap(partial => definition.options.map(option => ({ ...partial, [definition.key]: option.key }))), [{}]);
    for (const combination of combinations) {
      const matches = table.rows.filter(row => Object.entries(row.when ?? {}).every(([key, value]) => combination[key] === value));
      if (matches.length !== 1) errors.push(`${at}: ${matches.length ? '두 행 이상에 걸리는' : '어느 행에도 걸리지 않는'} 조합이 있습니다 ${JSON.stringify(combination)}`);
    }
  });
  return errors;
}

function validateContent(raw, state, selfId = null) {
  const errors = [];
  for (const field of ['title', 'ttsText', 'legalBasis', 'lawExcerpt']) if (blank(raw[field])) errors.push(`${field}: 비어 있을 수 없습니다`);
  for (const [field, max] of Object.entries(LIMITS)) if (raw[field] != null && (typeof raw[field] !== 'string' || raw[field].length > max)) errors.push(`${field}: 문자열 ${max}자 이하여야 합니다`);
  if (raw.revisionId != null && (!Number.isInteger(raw.revisionId) || raw.revisionId < 1)) errors.push('revisionId: 양의 정수여야 합니다');
  if (raw.effectiveOn != null && !isDate(raw.effectiveOn)) errors.push('effectiveOn: YYYY-MM-DD 날짜여야 합니다');
  if (errors.length) invalid(errors);
  requiredEnum(raw.cardType, CARD_TYPES, 'cardType');
  requiredEnum(raw.changeKind, ['LAW', 'OPERATION'], 'changeKind');
  requiredEnum(raw.audience, ['GENERAL', 'LIMITED'], 'audience');
  if (raw.eventTag != null) requiredEnum(raw.eventTag, EVENTS, 'eventTag');
  const content = Object.fromEntries(CONTENT_FIELDS.map(field => [field, raw[field] ?? null]));
  for (const field of Object.keys(LIMITS).filter(field => !['title', 'ttsText', 'legalBasis', 'lawExcerpt'].includes(field))) content[field] = normalize(content[field]);
  if (content.cardType === 'SHORT' && Boolean(content.dontText) !== Boolean(content.doText)) errors.push('dontText, doText: 두 장면은 둘 다 쓰거나 둘 다 비워 주세요');
  if (content.cardType === 'CONDITIONAL') {
    for (const field of ['dontText', 'doText', 'sceneKey']) if (content[field] != null) errors.push(`${field}: 이 카드 유형에는 쓰지 않습니다`);
    errors.push(...validateDecision(content.decision));
  } else if (content.decision != null) errors.push('decision: 이 카드 유형에는 쓰지 않습니다');
  if (content.changeKind === 'OPERATION') {
    if (!content.sourceOrg) errors.push('sourceOrg: 운영 변경 카드는 출처 기관이 필수입니다');
    if (content.revisionId != null) errors.push('revisionId: 운영 변경 카드는 법령 개정과 잇지 않습니다');
  } else if (content.sourceOrg != null) errors.push('sourceOrg: 법령 개정 카드는 출처 기관을 쓰지 않습니다');
  if (content.audience === 'LIMITED' && content.eventTag != null) errors.push('eventTag: 일부 운전자 대상 카드는 이벤트로 안내하지 않습니다');
  if (errors.length) throw new ApiError(422, 'CARD_INVALID', '카드 내용이 카드 유형 규칙에 맞지 않습니다', errors);
  ensureTopicFree(state, content.topic, selfId);
  if (content.revisionId != null) {
    const law = find(state.laws, content.revisionId, 'LAW_REVISION');
    if (law.triageStatus === 'NOT_APPLICABLE') conflict('LAW_REVISION_NOT_APPLICABLE', '일반 운전자 해당 없음으로 분류된 법령 개정에는 카드를 잇지 않습니다');
  }
  return content;
}

const nextId = items => Math.max(0, ...items.map(item => item.id)) + 1;
const find = (items, id, kind) => {
  const record = items.find(item => item.id === id);
  if (!record) throw new ApiError(404, `${kind}_NOT_FOUND`, `해당 항목이 없습니다: ${id}`);
  return record;
};
function ensureTopicFree(state, topic, selfId) {
  const other = topic && state.cards.find(card => card.topic === topic && card.id !== selfId);
  if (other) conflict('TOPIC_TAKEN', `주제 '${topic}' 는 이미 카드 ${other.id} 가 대표입니다`);
}
function ensureNotRetired(card) {
  if (card.status === 'RETIRED') conflict('INVALID_CARD_STATE', '회수된 카드는 바꿀 수 없습니다 (현재 RETIRED)');
}
function requireStatus(card, status) {
  if (card.status !== status) conflict('INVALID_CARD_STATE', `${status} 카드만 이 동작을 할 수 있습니다 (현재 ${card.status})`);
}

async function readBody(request) {
  let text = '';
  request.setEncoding('utf8');
  for await (const chunk of request) {
    text += chunk;
    if (text.length > 1048576) throw new ApiError(413, 'INVALID_REQUEST', '요청 본문이 너무 큽니다');
  }
  if (!text.trim()) return {};
  try {
    const parsed = JSON.parse(text);
    if (!parsed || typeof parsed !== 'object' || Array.isArray(parsed)) invalid([]);
    return parsed;
  } catch { invalid(['body: JSON 객체를 보내 주세요']); }
}

function respond(response, status, body) {
  response.writeHead(status, { 'Content-Type': 'application/json; charset=utf-8', 'Cache-Control': 'no-store', 'X-Drive-Rule-Mode': 'demo' });
  response.end(body == null ? undefined : JSON.stringify(body));
}

function appSync(state) {
  return {
    syncToken: `demo-${Date.now()}`, fullSync: true,
    cards: state.cards.filter(card => card.status === 'PUBLISHED').map(card => {
      const { id, revisionId, status: _status, createdAt: _created, updatedAt: _updated, ...content } = card;
      const law = state.laws.find(item => item.id === revisionId);
      return { cardId: id, ...content, lawName: law?.lawName ?? null, lawNo: law?.lawNo ?? null, promulgatedOn: law?.promulgatedOn ?? null, verifiedAt: `${card.verifiedAt}+09:00` };
    }),
    retiredCardIds: state.cards.filter(card => card.status === 'RETIRED' && card.verifiedAt).map(card => card.id),
    conditions: CONDITIONS,
  };
}

async function route(request, response, state) {
  const url = new URL(request.url ?? '/', 'http://127.0.0.1');
  const path = url.pathname.replace(/\/$/, '');
  const method = request.method ?? 'GET';
  if (method === 'GET' && path === '/actuator/health') return respond(response, 200, { status: 'UP', mode: 'DEMO' });
  if (path.startsWith('/api/v1/admin') && request.headers['x-admin-key'] !== 'local') throw new ApiError(401, 'ADMIN_KEY_REQUIRED', '데모 API 키는 local 입니다');
  const body = ['POST', 'PUT', 'PATCH'].includes(method) ? await readBody(request) : {};
  const admin = '/api/v1/admin';

  if (path === `${admin}/cards`) {
    if (method === 'GET') {
      const status = queryEnum(url, 'status', CARD_STATUSES);
      return respond(response, 200, state.cards.filter(card => !status || card.status === status));
    }
    if (method === 'POST') {
      const content = validateContent(body, state);
      const card = { id: nextId(state.cards), ...content, status: 'DRAFT', version: 1, verifiedAt: null, createdAt: now(), updatedAt: now() };
      state.cards.push(card);
      return respond(response, 201, card);
    }
  }
  const cardRoute = path.match(/^\/api\/v1\/admin\/cards\/(\d+)(?:\/(submit|reviews|retire|topic))?$/);
  if (cardRoute) {
    const card = find(state.cards, Number(cardRoute[1]), 'CARD');
    const action = cardRoute[2];
    if (!action && method === 'GET') return respond(response, 200, card);
    if (!action && method === 'PUT') {
      const content = validateContent(body, state, card.id);
      ensureNotRetired(card);
      if (card.verifiedAt && content.topic !== card.topic) conflict('TOPIC_CHANGE_NOT_SYNCED', '게시된 적 있는 카드의 주제는 게시 중일 때 대표 주제 지정으로만 바꿀 수 있습니다');
      Object.assign(card, content, { status: 'DRAFT', updatedAt: now() });
      return respond(response, 200, card);
    }
    if (action === 'submit' && method === 'POST') {
      requireStatus(card, 'DRAFT');
      if (state.reviews.some(review => review.cardId === card.id && review.cardVersion === card.version)) card.version++;
      Object.assign(card, { status: 'IN_REVIEW', updatedAt: now() });
      return respond(response, 200, card);
    }
    if (action === 'reviews' && method === 'GET') return respond(response, 200, state.reviews.filter(review => review.cardId === card.id).map(({ cardId: _cardId, ...review }) => review));
    if (action === 'reviews' && method === 'POST') {
      requireStatus(card, 'IN_REVIEW');
      requiredEnum(body.decision, ['APPROVE', 'REJECT'], 'decision');
      if (!Number.isInteger(body.reviewerId)) invalid(['reviewerId: 정수 ID가 필요합니다']);
      find(state.reviewers, body.reviewerId, 'REVIEWER');
      if (state.reviews.some(review => review.cardId === card.id && review.cardVersion === card.version && review.reviewerId === body.reviewerId)) conflict('ALREADY_REVIEWED', '이 검수자는 이미 이 버전을 검수했습니다');
      state.reviews.push({ id: nextId(state.reviews), cardId: card.id, reviewerId: body.reviewerId, cardVersion: card.version, decision: body.decision, reviewedAt: now() });
      if (body.decision === 'REJECT') Object.assign(card, { status: 'DRAFT', updatedAt: now() });
      else if (state.reviews.filter(review => review.cardId === card.id && review.cardVersion === card.version && review.decision === 'APPROVE').length >= 1) Object.assign(card, { status: 'PUBLISHED', verifiedAt: now(), updatedAt: now() });
      return respond(response, 200, card);
    }
    if (action === 'retire' && method === 'POST') {
      ensureNotRetired(card);
      Object.assign(card, { status: 'RETIRED', topic: null, updatedAt: now() });
      return respond(response, 200, card);
    }
    if (action === 'topic' && method === 'PUT') {
      if (body.topic != null && (typeof body.topic !== 'string' || body.topic.length > 40)) invalid(['topic: 문자열 40자 이하여야 합니다']);
      const topic = normalize(body.topic);
      ensureTopicFree(state, topic, card.id);
      ensureNotRetired(card);
      if (card.verifiedAt && card.status !== 'PUBLISHED' && card.topic !== topic) conflict('TOPIC_CHANGE_NOT_SYNCED', '수정 · 검수 중인 카드의 주제는 게시 후 바꿀 수 있습니다');
      Object.assign(card, { topic, updatedAt: now() });
      return respond(response, 200, card);
    }
  }

  if (path === `${admin}/law-revisions`) {
    if (method === 'GET') {
      const triage = queryEnum(url, 'triageStatus', TRIAGES);
      return respond(response, 200, state.laws.filter(law => !triage || law.triageStatus === triage).sort((a, b) => b.promulgatedOn.localeCompare(a.promulgatedOn) || b.id - a.id));
    }
    if (method === 'POST') {
      const errors = Object.entries({ lawName: 100, lawNo: 30, sourceKey: 40 }).flatMap(([field, max]) => blank(body[field]) || body[field].length > max ? [`${field}: 1~${max}자 문자열이 필요합니다`] : []);
      if (!isDate(body.promulgatedOn)) errors.push('promulgatedOn: YYYY-MM-DD 날짜가 필요합니다');
      if (errors.length) invalid(errors);
      if (state.laws.some(law => law.lawNo === body.lawNo)) conflict('LAW_NO_DUPLICATED', `이미 등록된 공포 번호입니다: ${body.lawNo}`);
      const law = { id: nextId(state.laws), lawName: body.lawName, lawNo: body.lawNo, sourceKey: body.sourceKey, promulgatedOn: body.promulgatedOn, triageStatus: 'PENDING', createdAt: now(), sourceUrl: /^LAWGO-\d+$/.test(body.sourceKey) ? `https://www.law.go.kr/LSW/lsInfoP.do?lsiSeq=${body.sourceKey.slice(6)}` : null };
      state.laws.push(law);
      return respond(response, 201, law);
    }
  }
  const triageRoute = path.match(/^\/api\/v1\/admin\/law-revisions\/(\d+)\/triage$/);
  if (triageRoute && method === 'PATCH') {
    requiredEnum(body.triageStatus, TRIAGES, 'triageStatus');
    const law = find(state.laws, Number(triageRoute[1]), 'LAW_REVISION');
    law.triageStatus = body.triageStatus;
    return respond(response, 200, law);
  }
  if (path === `${admin}/law-revisions/sync` && method === 'POST') {
    const lawNo = 'DEMO-SYNC-001';
    const exists = state.laws.some(law => law.lawNo === lawNo);
    if (!exists) state.laws.push({ id: nextId(state.laws), lawName: '[데모 수집] 도로교통법 일부개정', lawNo, sourceKey: 'MANUAL-DEMO-SYNC', promulgatedOn: now().slice(0, 10), triageStatus: 'PENDING', createdAt: now(), sourceUrl: null });
    return respond(response, 200, { from: ago(30).slice(0, 10), to: now().slice(0, 10), laws: [{ lawId: '001638', outcome: 'OK', lawName: '[데모] 도로교통법', created: exists ? [] : [lawNo], existing: exists ? 1 : 0, error: null }, { lawId: '003395', outcome: 'OK', lawName: '[데모] 도로교통법 시행령', created: [], existing: 1, error: null }, { lawId: '007079', outcome: 'FAILED', lawName: null, created: [], existing: 0, error: '데모: 부분 실패 표시를 위한 예시입니다. 외부 API는 호출하지 않았습니다.' }] });
  }

  if (path === `${admin}/reviewers`) {
    if (method === 'GET') return respond(response, 200, state.reviewers);
    if (method === 'POST') {
      if (blank(body.name) || body.name.length > 50) invalid(['name: 1~50자 문자열이 필요합니다']);
      const reviewer = { id: nextId(state.reviewers), name: body.name };
      state.reviewers.push(reviewer);
      return respond(response, 201, reviewer);
    }
  }
  if (path === `${admin}/review-requests` && method === 'GET') {
    const status = queryEnum(url, 'status', REQUEST_STATUSES);
    const type = queryEnum(url, 'requestType', REQUEST_TYPES);
    return respond(response, 200, state.requests.filter(item => (!status || item.status === status) && (!type || item.requestType === type)).sort((a, b) => b.createdAt.localeCompare(a.createdAt) || b.id - a.id));
  }
  const requestRoute = path.match(/^\/api\/v1\/admin\/review-requests\/(\d+)$/);
  if (requestRoute && method === 'PATCH') {
    requiredEnum(body.status, REQUEST_STATUSES, 'status');
    const item = find(state.requests, Number(requestRoute[1]), 'REVIEW_REQUEST');
    item.status = body.status;
    return respond(response, 200, item);
  }

  if (path === '/api/v1/cards/sync' && method === 'GET') return respond(response, 200, appSync(state));
  const reportRoute = path.match(/^\/api\/v1\/cards\/(\d+)\/error-reports$/);
  if (reportRoute && method === 'POST') {
    const card = find(state.cards.filter(item => item.verifiedAt), Number(reportRoute[1]), 'CARD');
    if (!Number.isInteger(body.cardVersion) || body.cardVersion < 1) invalid(['cardVersion: 양의 정수여야 합니다']);
    if (body.cardVersion > card.version) throw new ApiError(422, 'CARD_VERSION_UNKNOWN', '서버에 없는 카드 버전입니다');
    state.requests.push({ id: nextId(state.requests), requestType: 'ERROR_REPORT', cardId: card.id, cardVersion: body.cardVersion, topic: null, conditions: null, status: 'OPEN', createdAt: now() });
    return respond(response, 202, null);
  }
  if (path === '/api/v1/answer-holds' && method === 'POST') {
    if (blank(body.topic) || body.topic.length > 40) invalid(['topic: 1~40자 문자열이 필요합니다']);
    if (body.conditions != null && (typeof body.conditions !== 'object' || Array.isArray(body.conditions))) invalid(['conditions: 조건 객체가 필요합니다']);
    const errors = Object.entries(body.conditions ?? {}).flatMap(([key, value]) => CONDITIONS.some(item => item.key === key && item.options.some(option => option.key === value)) ? [] : [`conditions.${key}: 공통 목록에 없는 조건 또는 선택지입니다`]);
    if (errors.length) throw new ApiError(422, 'CONDITION_UNKNOWN', '조건이 공통 목록에 맞지 않습니다', errors);
    state.requests.push({ id: nextId(state.requests), requestType: 'ANSWER_HOLD', cardId: null, cardVersion: null, topic: body.topic.trim(), conditions: Object.keys(body.conditions ?? {}).length ? body.conditions : null, status: 'OPEN', createdAt: now() });
    return respond(response, 202, null);
  }
  throw new ApiError(404, 'NOT_FOUND', '데모 서버에 없는 경로입니다');
}

/** Starts an isolated in-memory demo API. Resolves a Node HTTP Server after it is listening. */
export async function startMockServer({ port = 8081 } = {}) {
  const state = seedState();
  const server = createServer((request, response) => {
    route(request, response, state).catch(error => {
      if (!(error instanceof ApiError)) console.error('[DEMO] API error:', error.message);
      respond(response, error.status ?? 500, { status: error.status ?? 500, title: error.status === 401 ? 'Unauthorized' : 'Demo API error', detail: error.message ?? '데모 API 오류', code: error.code ?? 'INTERNAL_ERROR', errors: error.errors ?? [] });
    });
  });
  await new Promise((resolve, reject) => {
    server.once('error', reject);
    server.listen(port, '127.0.0.1', resolve);
  });
  return server;
}

if (process.argv[1] && import.meta.url === pathToFileURL(process.argv[1]).href) {
  const server = await startMockServer();
  console.log('[DEMO] In-memory API http://127.0.0.1:8081 · key: local · all changes reset on restart');
  for (const signal of ['SIGINT', 'SIGTERM']) process.once(signal, () => server.close(() => process.exit(0)));
}
