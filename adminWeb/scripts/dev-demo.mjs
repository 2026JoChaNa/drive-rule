import { fileURLToPath } from 'node:url';
import { startMockServer } from './mock-server.mjs';

// Set before importing Vite/loading its config so the dev proxy always targets this local mock.
process.env.API_TARGET = 'http://127.0.0.1:8081';
process.env.VITE_DEMO_MODE = 'true';
process.chdir(fileURLToPath(new URL('..', import.meta.url)));

let api;
let web;
let stopping = false;
async function stop(code = 0) {
  if (stopping) return;
  stopping = true;
  try {
    await web?.close();
    if (api) await new Promise(resolve => { api.close(resolve); api.closeAllConnections(); });
  } finally { process.exit(code); }
}
for (const signal of ['SIGINT', 'SIGTERM']) process.once(signal, () => void stop());

try {
  api = await startMockServer();
  const { createServer } = await import('vite');
  web = await createServer({ server: { host: '127.0.0.1', port: 5173, strictPort: true } });
  await web.listen();
  console.log('\n[DEMO] DRIVE RULE 관리자 · http://127.0.0.1:5173');
  console.log('[DEMO] API http://127.0.0.1:8081 · 관리자 키: local');
  console.log('[DEMO] 모든 데이터는 임시 예시이며 재시작하면 초기화됩니다. 실제 서버 및 외부 API를 호출하지 않습니다.\n');
} catch (error) {
  console.error('[DEMO] 시작하지 못했습니다:', error.message);
  await stop(1);
}
