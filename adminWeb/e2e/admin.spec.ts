import { expect, test, type Page } from '@playwright/test';

const unique = () => Date.now().toString(36);
const mainNav = (page: Page) => page.getByRole('navigation', { name: '주 메뉴' });
async function ready(page: Page) {
  await expect(page.getByRole('button', { name: '새 카드 작성', exact: true })).toBeEnabled();
  // Prevent a reused real-server session from receiving these demo mutations.
  await expect(page.getByText('DEMO · 샘플 데이터', { exact: true })).toBeVisible();
}

test.beforeEach(async ({ page }) => { await page.goto('/'); await ready(page); });

test('dashboard displays actual API counts and opens a selected status', async ({ page, request }) => {
  const response = await request.get('/api/v1/admin/cards', { headers: { 'X-Admin-Key': 'local' } });
  expect(response.ok()).toBeTruthy();
  const cards: { status: string }[] = await response.json();
  const published = cards.filter(card => card.status === 'PUBLISHED').length;
  await expect(page.getByRole('heading', { name: /운영 현황을 한눈에/ })).toBeVisible();
  const totalStat = page.locator('.stat-card').filter({ hasText: '전체 카드' });
  await expect(totalStat.locator('.stat-number')).toHaveText(`${cards.length}건`);
  const publishedStat = page.locator('.stat-card').filter({ hasText: '게시 중' });
  await expect(publishedStat.locator('.stat-number')).toHaveText(`${published}건`);
  await publishedStat.click();
  await expect(page.getByRole('heading', { name: /카드 관리/, level: 1 })).toBeVisible();
  await expect(page.getByRole('group', { name: '카드 상태 필터' }).getByRole('button', { name: /^게시 중/ })).toHaveAttribute('aria-pressed', 'true');
});

test('creates a card, searches it, approves as one local operator, and verifies app sync', async ({ page }) => {
  const title = `브라우저 테스트 카드 ${unique()}`;
  await page.getByRole('button', { name: '새 카드 작성', exact: true }).click();
  const editor = page.getByRole('dialog', { name: '새 카드 만들기' });
  await editor.getByLabel(/^카드 제목/).fill(title);
  await editor.getByLabel(/^음성 안내/).fill('브라우저 테스트용 안내 문장입니다. 실제 법률 안내가 아닙니다.');
  await editor.getByLabel(/^법적 근거/).fill('E2E 테스트용 근거');
  await editor.getByLabel(/^법령 원문/).fill('화면 작성과 게시 흐름을 검증하기 위한 허구의 테스트 데이터입니다.');
  await editor.getByRole('button', { name: '초안 저장', exact: true }).click();
  await expect(editor).not.toBeVisible();

  await mainNav(page).getByRole('button', { name: '카드 관리', exact: true }).click();
  await page.getByRole('searchbox', { name: '카드 검색' }).fill(title);
  await page.getByRole('button', { name: title, exact: true }).click();
  const detail = page.getByRole('dialog', { name: '카드 상세' });
  await expect(detail.getByText('작성 중', { exact: true })).toBeVisible();
  await detail.getByRole('button', { name: '검수 요청', exact: true }).click();
  await expect(detail.getByText('검수 대기', { exact: true })).toBeVisible();
  await expect(detail.getByRole('button', { name: '승인', exact: true })).toBeDisabled();
  await detail.getByRole('checkbox', { name: '안내 내용과 법적 근거를 확인했습니다.' }).check();
  await detail.getByRole('button', { name: '승인', exact: true }).click();
  await expect(detail.getByText('게시 중', { exact: true })).toBeVisible();
  await expect(detail.getByText('로컬 운영자', { exact: true })).toBeVisible();
  await detail.getByRole('button', { name: '앱 게시 확인', exact: true }).click();
  await expect(detail.getByRole('status')).toContainText(/앱 동기화 응답에서 카드 #\d+ · v1을 확인했습니다/);
});

test('registers a law revision and persists its triage classification', async ({ page }) => {
  const suffix = unique();
  const lawName = `[E2E] 분류 시험 법령 ${suffix}`;
  const lawNo = `DEMO-E2E-${suffix}`;
  await mainNav(page).getByRole('button', { name: '법령 개정', exact: true }).click();
  await page.getByRole('button', { name: '개정 등록', exact: true }).click();
  const form = page.getByRole('dialog', { name: '법령 개정 등록' });
  await form.getByLabel(/^법령명/).fill(lawName);
  await form.getByLabel(/^공포번호/).fill(lawNo);
  await form.getByLabel(/^공포일/).fill('2026-11-14');
  await form.getByLabel(/^출처 식별자/).fill(`MANUAL-E2E-${suffix}`);
  await form.getByRole('button', { name: '등록', exact: true }).click();
  await expect(form).not.toBeVisible();
  await page.getByRole('textbox', { name: '법령 검색' }).fill(lawNo);
  const triage = page.getByRole('combobox', { name: `${lawNo} 분류` });
  await expect(triage).toHaveValue('PENDING');
  await triage.selectOption('APPLICABLE');
  await expect(triage).toHaveValue('APPLICABLE');
  await expect(triage).toBeEnabled();
  await page.getByRole('button', { name: '새로고침', exact: true }).click();
  await expect(triage).toHaveValue('APPLICABLE');
});

test('resolves and reopens an answer-hold report without losing its record', async ({ page, request }) => {
  const topic = `e2e_hold_${unique()}`;
  const created = await request.post('/api/v1/answer-holds', { data: { topic, conditions: { front_traffic_light: 'yellow' } } });
  expect(created.status()).toBe(202);
  await page.reload();
  await ready(page);
  await mainNav(page).getByRole('button', { name: /^신고 대기열/ }).click();
  await page.getByRole('textbox', { name: '요청 검색' }).fill(topic);
  const report = page.getByRole('article').filter({ hasText: topic });
  await expect(report).toBeVisible();
  await expect(report.getByText('전방 차량 신호: 황색')).toBeVisible();
  await report.getByRole('button', { name: '처리 완료', exact: true }).click();
  await expect(report).not.toBeVisible();
  const filters = page.getByRole('group', { name: '요청 상태 필터' });
  await filters.getByRole('button', { name: /^처리 완료/ }).click();
  await expect(report).toBeVisible();
  await report.getByRole('button', { name: '다시 열기', exact: true }).click();
  await expect(report).not.toBeVisible();
  await filters.getByRole('button', { name: /^처리 대기/ }).click();
  await expect(report).toBeVisible();
  await expect(report.getByText('처리 대기', { exact: true })).toBeVisible();
});

test('shows a friendly authorization error and reconnects with the demo key', async ({ page }) => {
  await page.getByRole('button', { name: '연결 설정', exact: true }).click();
  const connection = page.getByRole('dialog', { name: '백엔드 연결 설정' });
  await connection.getByLabel(/^관리자 API 키/).fill('incorrect-demo-key');
  await connection.getByRole('button', { name: '연결 확인', exact: true }).click();
  await expect(connection.getByRole('alert')).toContainText('API 키');
  await expect(connection.getByRole('alert')).toContainText('ADMIN_KEY_REQUIRED');
  await connection.getByLabel(/^관리자 API 키/).fill('local');
  await connection.getByRole('button', { name: '연결 확인', exact: true }).click();
  await expect(connection).not.toBeVisible();
  await ready(page);
});

test('mobile navigation opens and closes without horizontal page overflow', async ({ page }) => {
  await page.setViewportSize({ width: 390, height: 844 });
  const menu = page.getByRole('button', { name: '메뉴 열기', exact: true });
  await expect(menu).toBeVisible();
  const assertNoPageOverflow = async () => {
    await expect.poll(() => page.evaluate(() => document.documentElement.scrollWidth - window.innerWidth)).toBeLessThanOrEqual(1);
  };
  await assertNoPageOverflow();
  await menu.click();
  await expect(page.locator('.sidebar')).toHaveClass(/is-open/);
  await mainNav(page).getByRole('button', { name: '카드 관리', exact: true }).click();
  await expect(page.locator('.sidebar')).not.toHaveClass(/is-open/);
  await expect(page.getByRole('heading', { name: /카드 관리/, level: 1 })).toBeVisible();
  await assertNoPageOverflow();
});
