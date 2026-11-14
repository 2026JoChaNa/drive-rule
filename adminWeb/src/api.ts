import type { AppSync, Card, CardContent, CardStatus, LawContent, LawRevision, LawSyncResult, RequestStatus, Review, ReviewDecision, Reviewer, ReviewRequest, TriageStatus } from './types';

export interface FieldError { field: string; message: string }
export class ApiError extends Error {
  constructor(public status: number, public code: string, message: string, public errors: (string | FieldError)[] = []) { super(message); this.name = 'ApiError'; }
}
export class ApiClient {
  constructor(private readonly key: string) {}
  async request<T>(path: string, method = 'GET', body?: unknown, admin = true): Promise<T> {
    const controller = new AbortController();
    const timer = setTimeout(() => controller.abort(), 20000);
    try {
      const response = await fetch(`/api/v1/${admin ? 'admin/' : ''}${path}`, {
        method, signal: controller.signal, cache: 'no-store',
        headers: { ...(admin && this.key ? { 'X-Admin-Key': this.key } : {}), ...(body !== undefined ? { 'Content-Type': 'application/json' } : {}) },
        ...(body !== undefined ? { body: JSON.stringify(body) } : {}),
      });
      const text = await response.text();
      let data: any;
      try { data = text ? JSON.parse(text) : null; } catch { throw new ApiError(response.status, 'INVALID_RESPONSE', 'API 응답을 읽을 수 없습니다. 백엔드 주소와 실행 상태를 확인해 주세요.'); }
      if (!response.ok) throw new ApiError(response.status, data?.code || 'API_ERROR', data?.detail || (data?.code === 'ADMIN_KEY_REQUIRED' ? 'API 키가 없거나 올바르지 않습니다. 연결 설정을 확인해 주세요.' : data?.title) || `요청 실패 (${response.status})`, data?.errors || []);
      return data as T;
    } catch (error) {
      if (error instanceof ApiError) throw error;
      throw new ApiError(0, 'CONNECTION_ERROR', error instanceof Error && error.name === 'AbortError' ? '서버 응답 시간이 초과되었습니다. 변경 요청이었다면 목록을 새로고침해 결과를 확인하세요.' : '백엔드에 연결할 수 없습니다. 서버 실행 상태와 API_TARGET 설정을 확인해 주세요.');
    } finally { clearTimeout(timer); }
  }
  cards = (status?: CardStatus) => this.request<Card[]>(`cards${status ? `?status=${status}` : ''}`);
  card = (id: number) => this.request<Card>(`cards/${id}`);
  saveCard = (content: CardContent, id?: number) => this.request<Card>(id ? `cards/${id}` : 'cards', id ? 'PUT' : 'POST', content);
  submit = (id: number) => this.request<Card>(`cards/${id}/submit`, 'POST');
  reviews = (id: number) => this.request<Review[]>(`cards/${id}/reviews`);
  review = (id: number, reviewerId: number, decision: ReviewDecision) => this.request<Card>(`cards/${id}/reviews`, 'POST', { reviewerId, decision });
  retire = (id: number) => this.request<Card>(`cards/${id}/retire`, 'POST');
  topic = (id: number, topic: string | null) => this.request<Card>(`cards/${id}/topic`, 'PUT', { topic });
  reviewers = () => this.request<Reviewer[]>('reviewers');
  addReviewer = (name: string) => this.request<Reviewer>('reviewers', 'POST', { name });
  laws = () => this.request<LawRevision[]>('law-revisions');
  addLaw = (content: LawContent) => this.request<LawRevision>('law-revisions', 'POST', content);
  triage = (id: number, triageStatus: TriageStatus) => this.request<LawRevision>(`law-revisions/${id}/triage`, 'PATCH', { triageStatus });
  syncLaws = () => this.request<LawSyncResult>('law-revisions/sync', 'POST');
  requests = () => this.request<ReviewRequest[]>('review-requests');
  requestStatus = (id: number, status: RequestStatus) => this.request<ReviewRequest>(`review-requests/${id}`, 'PATCH', { status });
  appSync = () => this.request<AppSync>('cards/sync', 'GET', undefined, false);
}
