import assert from 'node:assert/strict';
import { randomUUID } from 'node:crypto';
import { mkdir, writeFile } from 'node:fs/promises';
import { dirname, resolve } from 'node:path';

// Real HTTP integration verification. Run only against a disposable local database.
// ADMIN_API_KEY is read only from the process environment and is never printed.
// Fixtures are uniquely named; existing rows are never changed. Newly created
// cards are retired in finally. The API has no delete for laws or reviewers.
const base = new URL(process.env.API_TEST_BASE || 'http://127.0.0.1:8090');
if (!['http:', 'https:'].includes(base.protocol)
  || !['127.0.0.1', 'localhost', '[::1]'].includes(base.hostname)
  || base.username || base.password || base.pathname !== '/' || base.search || base.hash) {
  throw new Error('API_TEST_BASE must be a loopback HTTP(S) origin without credentials or a path.');
}
if (process.env.API_TEST_ISOLATED !== '1') {
  throw new Error('Set API_TEST_ISOLATED=1 only after verifying the backend uses a disposable local database.');
}
const key = process.env.ADMIN_API_KEY;
if (!key) throw new Error('ADMIN_API_KEY must be supplied in the process environment.');
const runId = `${Date.now().toString(36)}-${randomUUID().slice(0, 8)}`;
const prefix = `API 점검 ${runId}`;
const topic = `smoke_${runId}`;
const reportPath = resolve(process.env.API_TEST_REPORT || '.local/api-verification.json');
const report = {
  runId, base: base.origin, startedAt: new Date().toISOString(), status: 'RUNNING',
  steps: [], fixtures: { cardIds: [], reviewerId: null, revisionId: null, requestIds: [] },
  limitations: ['The law collector success path needs LAW_API_OC; this isolated run expects LAW_API_NOT_CONFIGURED.',
    'Law and reviewer fixtures remain because their API has no delete operation.'],
};
const retired = new Set();
const scrub = value => String(value).split(key).join('[REDACTED]');

async function request(label, method, path, { body, status = 200, code, admin = true, check } = {}) {
  const started = performance.now();
  const step = { label, method, path, expectedStatus: status, status: 'RUNNING' };
  report.steps.push(step);
  try {
    const response = await fetch(new URL(path, base), {
      method, redirect: 'manual', signal: AbortSignal.timeout(20000),
      headers: { ...(admin ? { 'X-Admin-Key': key } : {}), ...(body !== undefined ? { 'Content-Type': 'application/json' } : {}) },
      ...(body === undefined ? {} : { body: JSON.stringify(body) }),
    });
    step.httpStatus = response.status;
    const raw = await response.text();
    const data = raw ? JSON.parse(raw) : null;
    assert.equal(response.status, status, `${label}: HTTP ${response.status}; ${data?.code || ''} ${data?.detail || ''}`);
    if (code) {
      assert.equal(data?.code, code);
      assert.ok(Array.isArray(data.errors), 'ProblemDetail.errors must be an array');
      step.errorCode = data.code;
    }
    if (check) await check(data);
    step.status = 'PASS';
    return data;
  } catch (error) {
    step.status = 'FAIL';
    step.error = scrub(error.message);
    throw error;
  } finally {
    step.durationMs = Math.round(performance.now() - started);
  }
}
const adminPath = '/api/v1/admin';
const api = (label, method, path, options) => request(label, method, adminPath + path, options);
const arrayWithId = (data, id) => { assert.ok(Array.isArray(data)); assert.ok(data.some(row => row.id === id)); };
const equalContent = (data, content) => { for (const [field, value] of Object.entries(content)) assert.deepEqual(data[field], value, field); };

async function createCard(label, content) {
  return api(label, 'POST', '/cards', { body: content, status: 201, check: card => {
    assert.ok(Number.isSafeInteger(card.id));
    report.fixtures.cardIds.push(card.id);
    assert.equal(card.status, 'DRAFT'); assert.equal(card.version, 1);
    assert.equal(card.verifiedAt, null); equalContent(card, content);
  } });
}

let mainContent;
try {
  await request('Admin rejects missing key', 'GET', `${adminPath}/cards`, { admin: false, status: 401, code: 'ADMIN_KEY_REQUIRED' });
  const reviewer = await api('Create unique operator fixture', 'POST', '/reviewers', {
    body: { name: prefix }, status: 201,
    check: data => { assert.ok(Number.isSafeInteger(data.id)); assert.equal(data.name, prefix); report.fixtures.reviewerId = data.id; },
  });
  await api('List reviewers', 'GET', '/reviewers', { check: data => arrayWithId(data, reviewer.id) });

  const lawBody = { lawName: `${prefix} 법령`, lawNo: `TEST-${runId}`, sourceKey: `MANUAL-${runId}`, promulgatedOn: '2026-11-14' };
  const revision = await api('Register law revision', 'POST', '/law-revisions', {
    body: lawBody, status: 201, check: data => {
      assert.ok(Number.isSafeInteger(data.id)); report.fixtures.revisionId = data.id;
      equalContent(data, lawBody); assert.equal(data.triageStatus, 'PENDING'); assert.equal(data.sourceUrl, null);
    },
  });
  await api('Duplicate law number error', 'POST', '/law-revisions', { body: lawBody, status: 409, code: 'LAW_NO_DUPLICATED' });
  await api('List law revisions', 'GET', '/law-revisions', { check: data => arrayWithId(data, revision.id) });
  await api('Filter pending laws', 'GET', '/law-revisions?triageStatus=PENDING', { check: data => {
    arrayWithId(data, revision.id); assert.ok(data.every(row => row.triageStatus === 'PENDING'));
  } });
  await api('Triage law as applicable', 'PATCH', `/law-revisions/${revision.id}/triage`, {
    body: { triageStatus: 'APPLICABLE' }, check: data => assert.equal(data.triageStatus, 'APPLICABLE'),
  });
  await api('Filter applicable laws', 'GET', '/law-revisions?triageStatus=APPLICABLE', { check: data => {
    arrayWithId(data, revision.id); assert.ok(data.every(row => row.triageStatus === 'APPLICABLE'));
  } });
  await api('Collector reports missing external credential', 'POST', '/law-revisions/sync', { status: 503, code: 'LAW_API_NOT_CONFIGURED' });

  mainContent = {
    revisionId: revision.id, cardType: 'SHORT', changeKind: 'LAW', audience: 'GENERAL',
    eventTag: 'SCHOOL_ZONE_ENTRY', sourceOrg: null, topic: null, title: `${prefix} 단문`,
    dontText: '테스트 잘못된 행동', doText: '테스트 올바른 행동', sceneKey: 'smoke_scene',
    ttsText: '로컬 API 검증용 음성 안내입니다.', legalBasis: '로컬 테스트 근거',
    lawExcerpt: '로컬 테스트용 원문입니다. 실제 운전자 안내용 콘텐츠가 아닙니다.',
    penaltyText: '테스트 처분 안내', effectiveOn: '2026-11-14', exceptions: '테스트 예외', decision: null,
  };
  await api('Reject invalid short-card field combination', 'POST', '/cards', {
    body: { ...mainContent, doText: null }, status: 422, code: 'CARD_INVALID', check: data => assert.ok(data.errors.length),
  });
  await api('Reject excessive title length', 'POST', '/cards', {
    body: { ...mainContent, title: 'x'.repeat(101) }, status: 400, code: 'INVALID_REQUEST', check: data => assert.ok(data.errors.some(e => e.startsWith('title:'))),
  });
  await api('Triage law as not applicable', 'PATCH', `/law-revisions/${revision.id}/triage`, {
    body: { triageStatus: 'NOT_APPLICABLE' }, check: data => assert.equal(data.triageStatus, 'NOT_APPLICABLE'),
  });
  await api('Reject card linked to excluded law', 'POST', '/cards', {
    body: mainContent, status: 409, code: 'LAW_REVISION_NOT_APPLICABLE',
  });
  await api('Restore fixture law applicability', 'PATCH', `/law-revisions/${revision.id}/triage`, {
    body: { triageStatus: 'APPLICABLE' }, check: data => assert.equal(data.triageStatus, 'APPLICABLE'),
  });
  const main = await createCard('Create short law card with all content fields', mainContent);
  await api('List cards', 'GET', '/cards', { check: data => arrayWithId(data, main.id) });
  await api('Filter draft cards', 'GET', '/cards?status=DRAFT', { check: data => {
    arrayWithId(data, main.id); assert.ok(data.every(row => row.status === 'DRAFT'));
  } });
  await api('Reject invalid card filter', 'GET', '/cards?status=INVALID', { status: 400, code: 'INVALID_REQUEST' });
  await api('Load card detail and preserve all fields', 'GET', `/cards/${main.id}`, { check: data => equalContent(data, mainContent) });
  await api('No initial review history', 'GET', `/cards/${main.id}/reviews`, { check: data => assert.deepEqual(data, []) });
  await request('Unpublished card hidden from public sync', 'GET', '/api/v1/cards/sync', { admin: false, check: data => {
    assert.equal(data.fullSync, true); assert.ok(!data.cards.some(card => card.cardId === main.id));
  } });
  await request('Unpublished card cannot receive app error report', 'POST', `/api/v1/cards/${main.id}/error-reports`, {
    admin: false, body: { cardVersion: 1 }, status: 404, code: 'CARD_NOT_FOUND',
  });

  mainContent = { ...mainContent, title: `${prefix} 단문 수정` };
  await api('Update card with complete content payload', 'PUT', `/cards/${main.id}`, { body: mainContent, check: data => {
    equalContent(data, mainContent); assert.equal(data.status, 'DRAFT'); assert.equal(data.version, 1);
  } });
  await api('Submit draft for review', 'POST', `/cards/${main.id}/submit`, { check: data => {
    assert.equal(data.status, 'IN_REVIEW'); assert.equal(data.version, 1);
  } });
  await api('Reject duplicate submission', 'POST', `/cards/${main.id}/submit`, { status: 409, code: 'INVALID_CARD_STATE' });
  await api('Unknown reviewer error', 'POST', `/cards/${main.id}/reviews`, {
    body: { reviewerId: 9007199254740000, decision: 'APPROVE' }, status: 404, code: 'REVIEWER_NOT_FOUND',
  });
  await api('Reject first review version', 'POST', `/cards/${main.id}/reviews`, {
    body: { reviewerId: reviewer.id, decision: 'REJECT' }, check: data => { assert.equal(data.status, 'DRAFT'); assert.equal(data.version, 1); },
  });
  mainContent = { ...mainContent, ttsText: '반려 후 수정한 로컬 API 검증용 음성 안내입니다.' };
  await api('Save changes following rejection', 'PUT', `/cards/${main.id}`, { body: mainContent, check: data => equalContent(data, mainContent) });
  await api('Resubmit increments reviewed version', 'POST', `/cards/${main.id}/submit`, { check: data => {
    assert.equal(data.status, 'IN_REVIEW'); assert.equal(data.version, 2);
  } });
  await api('Single operator approval publishes in local profile', 'POST', `/cards/${main.id}/reviews`, {
    body: { reviewerId: reviewer.id, decision: 'APPROVE' }, check: data => {
      assert.equal(data.status, 'PUBLISHED', 'local profile must use required-approvals=1');
      assert.equal(data.version, 2); assert.ok(data.verifiedAt);
    },
  });
  await api('Review history retains rejection and approval by version', 'GET', `/cards/${main.id}/reviews`, { check: data => {
    assert.equal(data.length, 2); assert.deepEqual(data.map(r => [r.reviewerId, r.cardVersion, r.decision]), [[reviewer.id, 1, 'REJECT'], [reviewer.id, 2, 'APPROVE']]);
  } });
  await api('Filter published cards', 'GET', '/cards?status=PUBLISHED', { check: data => {
    arrayWithId(data, main.id); assert.ok(data.every(row => row.status === 'PUBLISHED'));
  } });

  const peerContent = {
    ...mainContent, revisionId: null, cardType: 'FIXED_RESPONSE', changeKind: 'OPERATION', audience: 'LIMITED',
    eventTag: null, sourceOrg: '로컬 검증 기관', title: `${prefix} 고정 응답`, dontText: null, doText: null, sceneKey: null,
  };
  const peer = await createCard('Create fixed-response limited-audience operation card', peerContent);
  await api('Assign unique primary topic to published card', 'PUT', `/cards/${main.id}/topic`, { body: { topic }, check: data => {
    assert.equal(data.topic, topic); assert.equal(data.status, 'PUBLISHED'); assert.equal(data.version, 2);
  } });
  await api('Reject duplicate primary topic on another card', 'PUT', `/cards/${peer.id}/topic`, {
    body: { topic }, status: 409, code: 'TOPIC_TAKEN',
  });
  await api('Unassign primary topic', 'PUT', `/cards/${main.id}/topic`, { body: { topic: null }, check: data => assert.equal(data.topic, null) });
  await api('Assign released topic to fixture peer', 'PUT', `/cards/${peer.id}/topic`, { body: { topic }, check: data => assert.equal(data.topic, topic) });
  await api('Release peer topic', 'PUT', `/cards/${peer.id}/topic`, { body: { topic: null }, check: data => assert.equal(data.topic, null) });
  await api('Restore topic to published card', 'PUT', `/cards/${main.id}/topic`, { body: { topic }, check: data => assert.equal(data.topic, topic) });
  mainContent = { ...mainContent, topic };

  const firstSync = await request('Published card reaches public app sync with legal metadata', 'GET', '/api/v1/cards/sync', { admin: false, check: data => {
    assert.equal(data.fullSync, true); assert.ok(data.syncToken); assert.ok(Array.isArray(data.retiredCardIds));
    const card = data.cards.find(row => row.cardId === main.id); assert.ok(card);
    assert.equal(card.version, 2); assert.equal(card.topic, topic); assert.equal(card.lawNo, lawBody.lawNo);
    assert.equal(card.lawName, lawBody.lawName); assert.equal(card.promulgatedOn, lawBody.promulgatedOn);
    assert.match(card.verifiedAt, /\+09:00$/);
    for (const condition of ['front_traffic_light', 'crosswalk_light', 'pedestrian_present', 'right_turn_traffic_light']) assert.ok(data.conditions.some(c => c.key === condition));
    assert.ok(data.conditions.find(c => c.key === 'front_traffic_light').options.some(o => o.key === 'yellow'));
    assert.ok(data.conditions.find(c => c.key === 'crosswalk_light').options.some(o => o.key === 'green_flashing'));
  } });
  await request('Invalid app sync token reports 410', 'GET', '/api/v1/cards/sync?syncToken=invalid', { admin: false, status: 410, code: 'SYNC_TOKEN_EXPIRED' });
  await request('App submits card error report, 202 empty response', 'POST', `/api/v1/cards/${main.id}/error-reports`, {
    admin: false, body: { cardVersion: 2 }, status: 202, check: data => assert.equal(data, null),
  });
  await request('App cannot report a future card version', 'POST', `/api/v1/cards/${main.id}/error-reports`, {
    admin: false, body: { cardVersion: 99 }, status: 422, code: 'CARD_VERSION_UNKNOWN',
  });
  const conditions = { front_traffic_light: 'yellow', crosswalk_light: 'green_flashing', pedestrian_present: 'no' };
  await request('App submits answer hold, 202 empty response', 'POST', '/api/v1/answer-holds', {
    admin: false, body: { topic, conditions }, status: 202, check: data => assert.equal(data, null),
  });
  await request('Unknown answer-hold condition is rejected', 'POST', '/api/v1/answer-holds', {
    admin: false, body: { topic, conditions: { weather: 'rain' } }, status: 422, code: 'CONDITION_UNKNOWN',
  });
  let errorReport; let holdRequest;
  await api('Load both application submissions in admin queue', 'GET', '/review-requests', { check: data => {
    errorReport = data.find(r => r.requestType === 'ERROR_REPORT' && r.cardId === main.id && r.cardVersion === 2);
    holdRequest = data.find(r => r.requestType === 'ANSWER_HOLD' && r.topic === topic);
    assert.ok(errorReport); assert.ok(holdRequest); assert.equal(errorReport.status, 'OPEN'); assert.equal(holdRequest.status, 'OPEN');
    assert.deepEqual(holdRequest.conditions, conditions); assert.equal(holdRequest.cardId, null);
    report.fixtures.requestIds.push(errorReport.id, holdRequest.id);
  } });
  await api('Filter requests by status and type', 'GET', '/review-requests?status=OPEN&requestType=ANSWER_HOLD', { check: data => {
    arrayWithId(data, holdRequest.id); assert.ok(data.every(r => r.status === 'OPEN' && r.requestType === 'ANSWER_HOLD'));
  } });
  await api('Resolve error report', 'PATCH', `/review-requests/${errorReport.id}`, { body: { status: 'RESOLVED' }, check: data => assert.equal(data.status, 'RESOLVED') });
  await api('Dismiss answer hold', 'PATCH', `/review-requests/${holdRequest.id}`, { body: { status: 'DISMISSED' }, check: data => assert.equal(data.status, 'DISMISSED') });
  await api('Reopen answer hold', 'PATCH', `/review-requests/${holdRequest.id}`, { body: { status: 'OPEN' }, check: data => assert.equal(data.status, 'OPEN') });
  await api('Resolve reopened fixture request', 'PATCH', `/review-requests/${holdRequest.id}`, { body: { status: 'RESOLVED' }, check: data => assert.equal(data.status, 'RESOLVED') });
  await api('Filter resolved error reports', 'GET', '/review-requests?status=RESOLVED&requestType=ERROR_REPORT', { check: data => {
    arrayWithId(data, errorReport.id); assert.ok(data.every(r => r.status === 'RESOLVED' && r.requestType === 'ERROR_REPORT'));
  } });

  const decision = { schema: 2, tables: [{ key: 'approach', title: '전방 신호', conditions: ['front_traffic_light'], rows:
    ['red', 'yellow', 'green'].map(color => ({ when: { front_traffic_light: color }, text: `${color} 테스트 화면 문장`, tts: `${color} 테스트 음성 문장`, scene: null })),
  }] };
  const conditionalContent = { ...mainContent, topic: null, title: `${prefix} 조건 분기`, cardType: 'CONDITIONAL', dontText: null, doText: null, sceneKey: null, decision };
  await api('Reject decision table with missing combination', 'POST', '/cards', {
    body: { ...conditionalContent, decision: { schema: 2, tables: [{ ...decision.tables[0], rows: decision.tables[0].rows.slice(0, 2) }] } },
    status: 422, code: 'CARD_INVALID', check: data => assert.ok(data.errors.some(e => e.includes('front_traffic_light=green'))),
  });
  const conditional = await createCard('Create complete schema-2 conditional card', conditionalContent);
  await api('Submit conditional card', 'POST', `/cards/${conditional.id}/submit`, { check: data => assert.equal(data.status, 'IN_REVIEW') });
  await api('Publish conditional card with one approval', 'POST', `/cards/${conditional.id}/reviews`, {
    body: { reviewerId: reviewer.id, decision: 'APPROVE' }, check: data => assert.equal(data.status, 'PUBLISHED'),
  });
  await request('Decision table persists into public app sync', 'GET', '/api/v1/cards/sync', { admin: false, check: data => {
    assert.deepEqual(data.cards.find(c => c.cardId === conditional.id)?.decision, decision);
  } });

  await api('Published topic cannot be changed through content update', 'PUT', `/cards/${main.id}`, {
    body: { ...mainContent, topic: null }, status: 409, code: 'TOPIC_CHANGE_NOT_SYNCED',
  });
  await api('Editing published card returns to draft', 'PUT', `/cards/${main.id}`, { body: mainContent, check: data => {
    assert.equal(data.status, 'DRAFT'); assert.equal(data.version, 2); assert.ok(data.verifiedAt);
  } });
  await api('Previously published draft topic cannot be reassigned', 'PUT', `/cards/${main.id}/topic`, {
    body: { topic: null }, status: 409, code: 'TOPIC_CHANGE_NOT_SYNCED',
  });
  await request('Public sync excludes edited draft without retiring it', 'GET', '/api/v1/cards/sync', { admin: false, check: data => {
    assert.ok(!data.cards.some(c => c.cardId === main.id)); assert.ok(!data.retiredCardIds.includes(main.id));
  } });
  await api('Resubmit published edit increments version again', 'POST', `/cards/${main.id}/submit`, { check: data => assert.equal(data.version, 3) });
  await api('Publish edited version with same single operator', 'POST', `/cards/${main.id}/reviews`, {
    body: { reviewerId: reviewer.id, decision: 'APPROVE' }, check: data => { assert.equal(data.status, 'PUBLISHED'); assert.equal(data.version, 3); },
  });
  await api('Retire published fixture card', 'POST', `/cards/${main.id}/retire`, { check: data => {
    assert.equal(data.status, 'RETIRED'); assert.equal(data.topic, null); retired.add(main.id);
  } });
  await api('Retired card cannot be edited', 'PUT', `/cards/${main.id}`, { body: { ...mainContent, topic: null }, status: 409, code: 'INVALID_CARD_STATE' });
  await api('Retired card cannot be submitted', 'POST', `/cards/${main.id}/submit`, { status: 409, code: 'INVALID_CARD_STATE' });
  await api('Retired card cannot be assigned a topic', 'PUT', `/cards/${main.id}/topic`, { body: { topic }, status: 409, code: 'INVALID_CARD_STATE' });
  await request('Incremental sync includes retired ID and excludes card content', 'GET', `/api/v1/cards/sync?syncToken=${encodeURIComponent(firstSync.syncToken)}`, { admin: false, check: data => {
    assert.equal(data.fullSync, false); assert.ok(data.retiredCardIds.includes(main.id)); assert.ok(!data.cards.some(c => c.cardId === main.id));
  } });
  await api('Retired topic becomes available to another fixture', 'PUT', `/cards/${peer.id}/topic`, { body: { topic }, check: data => assert.equal(data.topic, topic) });
  await api('Filter retired cards', 'GET', '/cards?status=RETIRED', { check: data => {
    arrayWithId(data, main.id); assert.ok(data.every(c => c.status === 'RETIRED'));
  } });
  report.status = 'PASS';
} catch (error) {
  report.status = 'FAIL'; report.error = scrub(error.message); process.exitCode = 1;
} finally {
  for (const id of report.fixtures.cardIds.filter(id => !retired.has(id))) {
    try {
      const current = await api(`Cleanup: verify fixture card #${id}`, 'GET', `/cards/${id}`, { check: data => {
        assert.ok(data.title.startsWith(prefix), 'Refusing to retire a card not owned by this run');
      } });
      if (current.status !== 'RETIRED') await api(`Cleanup: retire fixture card #${id}`, 'POST', `/cards/${id}/retire`, { check: data => {
        assert.equal(data.status, 'RETIRED'); assert.equal(data.topic, null); retired.add(id);
      } });
    } catch (error) {
      report.status = 'FAIL'; report.cleanupError = scrub(error.message); process.exitCode = 1;
    }
  }
  report.finishedAt = new Date().toISOString();
  report.passed = report.steps.filter(step => step.status === 'PASS').length;
  report.failed = report.steps.filter(step => step.status === 'FAIL').length;
  report.fixtures.retiredCardIds = [...retired];
  await mkdir(dirname(reportPath), { recursive: true });
  await writeFile(reportPath, JSON.stringify(report, null, 2) + '\n', 'utf8');
  console.log(JSON.stringify({ status: report.status, passed: report.passed, failed: report.failed, runId, reportPath, ...(report.error ? { error: report.error } : {}) }, null, 2));
}
