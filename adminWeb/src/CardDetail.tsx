import { useEffect, useState } from 'react';
import { ArrowDownToLine, ArrowRight, Check, Volume2 } from 'lucide-react';
import { ApiClient } from './api';
import { Badge, dateLabel, ErrorBox, Modal, Spinner } from './ui';
import { conditionLabels, topics, typeLabels, type AppSync, type Card, type Review, type Reviewer } from './types';

export function CardDetail({ id, api, reviewers, onClose, onEdit, onRefresh, notify, disabled }: { id: number; api: ApiClient; reviewers: Reviewer[]; onClose: () => void; onEdit: (c: Card) => void; onRefresh: () => Promise<void>; notify: (s: string) => void; disabled: boolean }) {
  const [card, setCard] = useState<Card | null>(null);
  const [reviews, setReviews] = useState<Review[]>([]);
  const [busy, setBusy] = useState(false);
  const [error, setError] = useState<unknown>(null);
  const [topic, setTopic] = useState('');
  const [confirmRetire, setConfirmRetire] = useState(false);
  const [sync, setSync] = useState<AppSync | null>(null);
  const [ack, setAck] = useState(false);
  const [reviewNote, setReviewNote] = useState('');
  async function load() { const [c, r] = await Promise.all([api.card(id), api.reviews(id)]); setCard(c); setTopic(c.topic || ''); setReviews(r); }
  useEffect(() => {
    let active = true;
    Promise.all([api.card(id), api.reviews(id)]).then(([c, r]) => { if (active) { setCard(c); setTopic(c.topic || ''); setReviews(r); } }).catch(e => { if (active) setError(e); });
    return () => { active = false; };
  }, [id, api]);
  async function action(fn: () => Promise<unknown>, message: string) {
    if (busy) return; setBusy(true); setError(null); setSync(null);
    try { await fn(); await load(); await onRefresh(); notify(message); setAck(false); }
    catch (e) { setError(e); } finally { setBusy(false); }
  }
  async function review(decision: 'APPROVE' | 'REJECT') {
    await action(async () => {
      const all = await api.reviewers();
      const local = all.find(r => r.name === '로컬 운영자') || await api.addReviewer('로컬 운영자');
      const result = await api.review(id, local.id, decision);
      setReviewNote(decision === 'APPROVE' && result.status !== 'PUBLISHED' ? '승인을 기록했습니다. 현재 백엔드는 추가 승인을 요구합니다. 1인 검수는 백엔드 local 프로필로 실행하세요.' : '');
    }, decision === 'APPROVE' ? '승인 결과를 반영했습니다.' : '반려하여 작성 중 상태로 되돌렸습니다.');
  }
  const current = reviews.filter(r => r.cardVersion === card?.version);
  const local = reviewers.find(r => r.name === '로컬 운영자');
  const alreadyReviewed = !!local && current.some(r => r.reviewerId === local.id);
  const canTopic = card && card.status !== 'RETIRED' && (!card.verifiedAt || card.status === 'PUBLISHED');
  return <Modal title="카드 상세" subtitle={'CARD #' + String(id).padStart(3, '0')} onClose={() => { if (!busy) onClose(); }} wide>
    <div className="modal-body">
      <ErrorBox error={error}/>
      {!card ? <div className="loading-panel">{!error && <Spinner/>}<span>{error ? '카드를 불러오지 못했습니다.' : '카드를 불러오는 중입니다.'}</span></div> : <>
        <div className="detail-title"><div className="inline-tags"><Badge value={card.status}/><span className="tiny-label">{typeLabels[card.cardType]} · v{card.version}</span></div><h2>{card.title}</h2><p>{card.changeKind === 'LAW' ? '법령 개정' : '운영 변경 · ' + card.sourceOrg} · {card.audience === 'GENERAL' ? '일반 운전자' : '일부 운전자'} · 수정 {dateLabel(card.updatedAt, true)}</p></div>
        <div className="tts-preview"><span><Volume2 size={18}/> 운전자 안내 문장</span><p>{card.ttsText}</p></div>
        {(card.dontText || card.doText) && <div className="scene-pair"><div><span>하지 말 것</span><p>{card.dontText || '—'}</p></div><div><span>해야 할 것</span><p>{card.doText || '—'}</p></div></div>}
        <div className="detail-grid"><div><small>법적 근거</small><p>{card.legalBasis}</p></div><div><small>시행일</small><p>{dateLabel(card.effectiveOn)}</p></div><div><small>처분</small><p>{card.penaltyText || '등록되지 않음'}</p></div><div><small>이벤트 / 연결 개정</small><p>{card.eventTag || '질문 응답 전용'} / {card.revisionId ? '#' + card.revisionId : '없음'}</p></div></div>
        <details className="detail-disclosure"><summary>법령 원문 발췌</summary><p className="pre-wrap">{card.lawExcerpt}</p></details>
        {card.exceptions && <details className="detail-disclosure"><summary>예외 사항</summary><p className="pre-wrap">{card.exceptions}</p></details>}
        {card.decision && <details className="detail-disclosure" open><summary>조건 분기 결정 테이블</summary>{card.decision.tables.map((t, i) => <div key={i} className="decision-preview"><h4>{t.title}</h4>{t.rows.map((r, j) => <div key={j}><small>{Object.entries(r.when || {}).map(([k, v]) => (conditionLabels[k as keyof typeof conditionLabels] || k) + ': ' + v).join(' / ') || '모든 조건'}</small><p>{r.text}</p><span className="muted">TTS · {r.tts}</span></div>)}</div>)}</details>}
        <section className="detail-section"><h3>대표 주제</h3><div className="inline-form"><select aria-label="대표 주제" value={topic} disabled={!canTopic || busy || disabled} onChange={e => setTopic(e.target.value)}><option value="">지정하지 않음</option>{card.topic && !topics[card.topic] && <option value={card.topic}>{card.topic}</option>}{Object.entries(topics).map(([key, label]) => <option value={key} key={key}>{label}</option>)}</select><button className="button button-secondary" disabled={!canTopic || busy || disabled || topic === (card.topic || '')} onClick={() => void action(() => api.topic(id, topic || null), '대표 주제를 변경했습니다.')}>주제 저장</button></div>{!canTopic && card.status !== 'RETIRED' && <small className="muted">게시 이력이 있는 카드는 다시 게시된 뒤 주제를 변경할 수 있습니다.</small>}</section>
        <section className="detail-section"><h3>검수 기록 <span className="count-pill">{reviews.length}</span></h3>{reviews.length === 0 ? <p className="muted">아직 검수 기록이 없습니다.</p> : <div className="review-history">{[...reviews].reverse().map(r => <div key={r.id}><span className={'review-dot ' + (r.decision === 'REJECT' ? 'rejected' : '')}><Check size={12}/></span><strong>{reviewers.find(v => v.id === r.reviewerId)?.name || '검수자 #' + r.reviewerId}</strong><span>{r.decision === 'APPROVE' ? '승인' : '반려'}</span><small>v{r.cardVersion}{r.cardVersion === card.version ? ' · 현재 버전' : ''}</small><time>{dateLabel(r.reviewedAt, true)}</time></div>)}</div>}</section>
        {reviewNote && <p className="notice" role="status">{reviewNote}</p>}
        {card.status === 'IN_REVIEW' && <div className="review-action"><strong>로컬 운영자로 검수합니다.</strong><p>안내 문장과 근거를 확인한 뒤 승인하세요. local 프로필과 데모에서는 승인 1건으로 게시됩니다.</p>{alreadyReviewed ? <p className="notice">이미 이 버전을 검수했습니다. 추가 승인이 필요하다면 백엔드 local 프로필 설정을 확인하세요.</p> : <label className="checkbox-label"><input type="checkbox" checked={ack} onChange={e => setAck(e.target.checked)}/>안내 내용과 법적 근거를 확인했습니다.</label>}<div className="action-row"><button className="button button-secondary" disabled={busy || disabled || alreadyReviewed} onClick={() => void review('REJECT')}>반려</button><button className="button button-primary" disabled={busy || disabled || !ack || alreadyReviewed} onClick={() => void review('APPROVE')}><Check size={16}/> 승인</button></div></div>}
        {card.status === 'PUBLISHED' && <div className="publish-check"><button className="button button-secondary" disabled={busy} onClick={async () => { setBusy(true); setError(null); try { setSync(await api.appSync()); } catch (e) { setError(e); } finally { setBusy(false); } }}><ArrowDownToLine size={16}/> 앱 게시 확인</button>{sync && <p role="status" className={sync.cards.some(c => c.cardId === id && c.version === card.version) ? 'success-text' : 'warning-text'}>{sync.cards.some(c => c.cardId === id && c.version === card.version) ? '앱 동기화 응답에서 카드 #' + id + ' · v' + card.version + '을 확인했습니다.' : '앱 동기화 응답에 현재 버전이 없습니다. 새로고침 후 다시 확인하세요.'}</p>}</div>}
        {confirmRetire && <div className="danger-confirm" role="alert"><strong>이 카드를 회수할까요?</strong><p>회수는 되돌릴 수 없습니다. 대표 주제가 해제되고 다음 앱 동기화에서 내려갑니다.</p><div className="action-row"><button className="button button-secondary" disabled={busy} onClick={() => setConfirmRetire(false)}>취소</button><button className="button button-danger" disabled={busy} onClick={() => void action(async () => { await api.retire(id); setConfirmRetire(false); }, '카드를 회수했습니다.')}>회수 확정</button></div></div>}
      </>}
    </div>
    <div className="modal-footer"><div>{card && card.status !== 'RETIRED' && <button className="button button-ghost danger-text" disabled={busy || disabled} onClick={() => setConfirmRetire(true)}>카드 회수</button>}</div><div className="action-row">{busy && <Spinner/>}<button className="button button-secondary" disabled={busy} onClick={onClose}>닫기</button>{card && card.status !== 'RETIRED' && <button className="button button-secondary" disabled={busy || disabled} onClick={() => onEdit(card)}>내용 수정</button>}{card?.status === 'DRAFT' && <button className="button button-primary" disabled={busy || disabled} onClick={() => void action(() => api.submit(id), '검수를 요청했습니다. 내용을 확인하고 승인하세요.')}>검수 요청 <ArrowRight size={15}/></button>}</div></div>
  </Modal>;
}
