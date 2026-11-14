import { afterEach, beforeEach, describe, expect, it } from 'vitest';
import type { Server } from 'node:http';
// @ts-expect-error The standalone demo server is intentionally plain Node ESM.
import { startMockServer } from './mock-server.mjs';

let server: Server;
let base: string;
beforeEach(async () => {
  server = await startMockServer({ port: 0 });
  const address = server.address();
  if (!address || typeof address === 'string') throw new Error('Missing server port');
  base = `http://127.0.0.1:${address.port}`;
});
afterEach(async () => { await new Promise<void>(resolve => { server.close(() => resolve()); server.closeAllConnections(); }); });

async function call(path: string, method = 'GET', body?: unknown, key = 'local') {
  const response = await fetch(`${base}/api/v1${path}`, { method, headers: { 'Content-Type': 'application/json', 'X-Admin-Key': key }, body: body === undefined ? undefined : JSON.stringify(body) });
  const text = await response.text();
  return { status: response.status, data: text ? JSON.parse(text) : null };
}

describe('isolated demo API', () => {
  it('requires the demo key, returns plain lists, and leaves app sync public', async () => {
    expect((await call('/admin/cards', 'GET', undefined, '')).data.code).toBe('ADMIN_KEY_REQUIRED');
    expect((await call('/admin/cards')).data).toHaveLength(6);
    const sync = (await call('/cards/sync', 'GET', undefined, '')).data;
    expect(sync.cards).toHaveLength(2);
    expect(sync.cards[0].cardId).toBe(1);
    expect(sync.cards[0].id).toBeUndefined();
    expect(sync.conditions.find((item: { key: string }) => item.key === 'front_traffic_light').options.map((item: { key: string }) => item.key)).toContain('yellow');
  });

  it('supports single-person local approval and keeps rejected-version history', async () => {
    expect((await call('/admin/cards/4/submit', 'POST')).data.version).toBe(1);
    expect((await call('/admin/cards/4/reviews', 'POST', { reviewerId: 1, decision: 'REJECT' })).data.status).toBe('DRAFT');
    expect((await call('/admin/cards/4/submit', 'POST')).data.version).toBe(2);
    expect((await call('/admin/cards/4/reviews', 'POST', { reviewerId: 1, decision: 'APPROVE' })).data.status).toBe('PUBLISHED');
    expect((await call('/admin/cards/4/reviews', 'POST', { reviewerId: 1, decision: 'APPROVE' })).data.code).toBe('INVALID_CARD_STATE');
    const reviews = (await call('/admin/cards/4/reviews')).data;
    expect(reviews.map((item: { cardVersion: number }) => item.cardVersion)).toEqual([1, 2]);
    expect(reviews[0].cardId).toBeUndefined();
  });

  it('preserves published topics on edit and makes retirement terminal', async () => {
    const published = (await call('/admin/cards/1')).data;
    expect((await call('/admin/cards/1', 'PUT', { ...published, topic: 'seatbelt' })).data.code).toBe('TOPIC_CHANGE_NOT_SYNCED');
    expect((await call('/admin/cards/1', 'PUT', { ...published, title: '수정된 데모' })).data.status).toBe('DRAFT');
    expect((await call('/admin/cards/1/topic', 'PUT', { topic: null })).data.code).toBe('TOPIC_CHANGE_NOT_SYNCED');
    expect((await call('/admin/cards/4/topic', 'PUT', { topic: 'right_turn' })).data.code).toBe('TOPIC_TAKEN');
    expect((await call('/admin/cards/2/retire', 'POST')).data.topic).toBeNull();
    expect((await call('/admin/cards/4/topic', 'PUT', { topic: 'right_turn' })).status).toBe(200);
    expect((await call('/admin/cards/2/submit', 'POST')).data.code).toBe('INVALID_CARD_STATE');
    expect((await call('/admin/cards/2/retire', 'POST')).data.code).toBe('INVALID_CARD_STATE');
  });

  it('rejects stale decision examples that omit yellow-light coverage', async () => {
    const card = (await call('/admin/cards/2')).data;
    const decision = { schema: 2, tables: [{ key: 'test', title: '전방 신호', conditions: ['front_traffic_light'], rows: [{ when: { front_traffic_light: 'red' }, text: 'red', tts: 'red' }, { when: { front_traffic_light: 'green' }, text: 'green', tts: 'green' }] }] };
    const result = await call('/admin/cards', 'POST', { ...card, topic: null, decision });
    expect(result.status).toBe(422);
    expect(result.data.code).toBe('CARD_INVALID');
    expect(result.data.errors.join(' ')).toContain('yellow');
    expect(result.data.errors.every((error: unknown) => typeof error === 'string')).toBe(true);
  });

  it('supports triage, request reopening, and repeatable partial collection failures', async () => {
    const draft = (await call('/admin/cards/4')).data;
    expect((await call('/admin/cards', 'POST', { ...draft, topic: null, revisionId: 3 })).data.code).toBe('LAW_REVISION_NOT_APPLICABLE');
    expect((await call('/admin/law-revisions/3/triage', 'PATCH', { triageStatus: 'APPLICABLE' })).status).toBe(200);
    expect((await call('/admin/cards', 'POST', { ...draft, topic: null, revisionId: 3 })).status).toBe(201);
    expect((await call('/admin/review-requests/4', 'PATCH', { status: 'OPEN' })).data.status).toBe('OPEN');
    const first = (await call('/admin/law-revisions/sync', 'POST')).data;
    expect(first.laws[0].created).toHaveLength(1);
    expect(first.laws[2].outcome).toBe('FAILED');
    expect(first.laws[2].error).toContain('데모');
    expect((await call('/admin/law-revisions/sync', 'POST')).data.laws[0].created).toHaveLength(0);
  });
});
