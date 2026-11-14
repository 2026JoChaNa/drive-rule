import { ArrowRight, Check, ChevronRight, CircleCheck, ClipboardCheck, FileText, GitBranch, Inbox, Layers3, ListFilter, MessageSquareWarning, Radio, Scale } from 'lucide-react';
import type { Card, CardStatus, LawRevision, Reviewer, ReviewRequest } from './types';
import { statusLabels, topics, typeLabels } from './types';
import { Badge, dateLabel, Empty } from './ui';

interface DashboardProps {
  data: { cards: Card[]; laws: LawRevision[]; requests: ReviewRequest[]; reviewers: Reviewer[] };
  connected: boolean;
  onCards: (status?: CardStatus | 'ALL') => void;
  onCard: (id: number) => void;
  onLaws: () => void;
  onRequests: () => void;
}

export default function Dashboard({ data, connected, onCards, onCard, onLaws, onRequests }: DashboardProps) {
  const counts: Record<CardStatus, number> = { DRAFT: 0, IN_REVIEW: 0, PUBLISHED: 0, RETIRED: 0 };
  data.cards.forEach(card => counts[card.status]++);
  const openRequests = data.requests.filter(request => request.status === 'OPEN');
  const pendingLaws = data.laws.filter(law => law.triageStatus === 'PENDING');
  const recent = [...data.cards].sort((a, b) => b.updatedAt.localeCompare(a.updatedAt) || b.id - a.id).slice(0, 5);
  const demand = Object.entries(openRequests.filter(request => request.requestType === 'ANSWER_HOLD').reduce<Record<string, number>>((grouped, request) => {
    const topic = request.topic || 'other';
    grouped[topic] = (grouped[topic] || 0) + 1;
    return grouped;
  }, {})).sort((a, b) => b[1] - a[1] || a[0].localeCompare(b[0])).slice(0, 3);
  const tasks = [
    { icon: ClipboardCheck, title: '검수 대기 카드', note: '내용을 확인하고 게시해 주세요', count: counts.IN_REVIEW, action: () => onCards('IN_REVIEW'), tone: 'orange' },
    { icon: Scale, title: '분류가 필요한 법령', note: '일반 운전자에게 해당하는지 확인', count: pendingLaws.length, action: onLaws, tone: 'blue' },
    { icon: MessageSquareWarning, title: '미처리 앱 신고', note: '오류 신고와 답변 보류 확인', count: openRequests.length, action: onRequests, tone: 'gray' },
  ];
  const stats = [
    { title: '전체 카드', value: data.cards.length, icon: Layers3, tone: 'gray', note: '등록된 모든 안내 카드', action: () => onCards('ALL') },
    { title: '게시 중', value: counts.PUBLISHED, icon: Radio, tone: 'green', note: '앱에 동기화되는 카드', action: () => onCards('PUBLISHED') },
    { title: '검수 대기', value: counts.IN_REVIEW, icon: ClipboardCheck, tone: 'orange', note: '게시 전 확인이 필요한 카드', action: () => onCards('IN_REVIEW') },
    { title: '처리 대기 신고', value: openRequests.length, icon: Inbox, tone: 'blue', note: '아직 처리하지 않은 앱 신고', action: onRequests },
  ];
  const actionable = counts.IN_REVIEW + pendingLaws.length + openRequests.length;

  return <>
    <div className="stats-grid">
      {stats.map(({ title, value, icon: Icon, tone, note, action }) => <button type="button" className="stat-card" onClick={action} key={title}>
        <div className="stat-top"><span>{title}</span><span className={`stat-icon ${tone}`}><Icon size={18} strokeWidth={1.7}/></span></div>
        <div className="stat-number">{connected ? value.toLocaleString() : '—'}<span>건</span></div>
        <div className="stat-bottom"><span>{note}</span><ChevronRight size={15}/></div>
      </button>)}
    </div>

    <section className="workflow-banner" aria-label="카드 운영 순서">
      <div className="workflow-copy"><span className="eyebrow">CLEAR RULES. SAFER DRIVES.</span><h2>정확한 안내가 시작되는 곳</h2><p>카드를 작성하고, 내용을 확인한 뒤 앱에 게시하세요.</p></div>
      <div className="workflow-steps">
        <button type="button" onClick={() => onCards('DRAFT')}><span className="tiny-label">01</span><FileText size={19}/><strong>카드 작성</strong><small>안내와 근거 작성</small></button><ChevronRight size={16}/>
        <button type="button" onClick={() => onCards('IN_REVIEW')}><span className="tiny-label">02</span><ClipboardCheck size={19}/><strong>내용 검수</strong><small>정확한 내용 확인</small></button><ChevronRight size={16}/>
        <button type="button" onClick={() => onCards('PUBLISHED')}><span className="tiny-label">03</span><CircleCheck size={19}/><strong>앱 게시</strong><small>운전자에게 전달</small></button>
      </div>
    </section>

    <div className="dashboard-grid">
      <section className="panel recent-panel">
        <div className="panel-heading"><div><h2>최근 업데이트한 카드</h2><p>작성과 검수 진행 상황을 확인하세요.</p></div><button type="button" className="text-button" onClick={() => onCards('ALL')}>전체 보기 <ArrowRight size={15}/></button></div>
        {recent.length ? <>
          <div className="table-wrap"><table><thead><tr><th>카드</th><th>상태</th><th>수정일</th><th><span className="sr-only">상세</span></th></tr></thead><tbody>
            {recent.map(card => <tr key={card.id}>
              <td><button type="button" className="card-title-cell" onClick={() => onCard(card.id)}><span className="document-icon">{card.cardType === 'CONDITIONAL' ? <GitBranch size={18}/> : <FileText size={18}/>}</span><span><strong>{card.title}</strong><small>#{String(card.id).padStart(3, '0')} <span>·</span> {typeLabels[card.cardType]} <span>·</span> v{card.version}</small></span></button></td>
              <td><Badge value={card.status}/></td><td className="date-cell">{dateLabel(card.updatedAt)}</td>
              <td><button type="button" className="icon-button" aria-label={`${card.title} 상세 보기`} onClick={() => onCard(card.id)}><ChevronRight size={17}/></button></td>
            </tr>)}
          </tbody></table></div>
          <div className="panel-footer"><span className="muted">전체 {data.cards.length}개 중 최근 {recent.length}개</span><span className="tiny-label">최근 수정 순</span></div>
        </> : <Empty title={connected ? '첫 번째 카드를 작성해 보세요' : 'API 연결을 기다리고 있습니다'} text={connected ? '등록한 카드는 이곳에서 바로 확인할 수 있습니다.' : '연결 설정에서 로컬 서버를 연결하면 운영 현황이 표시됩니다.'}/>}
      </section>

      <section className="panel tasks-panel">
        <div className="panel-heading"><div><h2>지금 확인할 일 <span className="count-pill">{connected ? actionable : '—'}</span></h2><p>처리가 필요한 항목을 모았어요.</p></div><ListFilter size={19} className="muted"/></div>
        <div className="task-list">{tasks.map(({ icon: Icon, title, note, count, action, tone }) => <button type="button" className="task-row" key={title} onClick={action}>
          <span className={`stat-icon ${tone}`}><Icon size={19}/></span><span className="task-text"><strong>{title}</strong><small>{note}</small></span><b>{connected ? count : '—'}</b><ChevronRight size={16}/>
        </button>)}</div>
        <div className="task-note">{connected && actionable === 0 ? <><Check size={15}/><span>현재 처리 대기 중인 항목이 없습니다.</span></> : <><CircleCheck size={15}/><span>확인한 내용은 각 관리 화면에서 처리할 수 있어요.</span></>}</div>
      </section>

      <section className="panel lifecycle-panel">
        <div className="panel-heading"><div><h2>카드 운영 현황</h2><p>전체 카드의 상태별 분포</p></div><span className="tiny-label">총 {connected ? data.cards.length : '—'}개</span></div>
        {data.cards.length ? <>
          <div className="lifecycle-bar" role="img" aria-label={(Object.entries(counts) as [CardStatus, number][]).map(([status, count]) => `${statusLabels[status]} ${count}개`).join(', ')}>
            {(Object.entries(counts) as [CardStatus, number][]).filter(([, count]) => count > 0).map(([status, count]) => <span className={`bar-${status.toLowerCase()}`} key={status} style={{ width: `${count / data.cards.length * 100}%` }} title={`${statusLabels[status]} ${count}개`}/>)}
          </div>
          <div className="lifecycle-legend">{(Object.entries(counts) as [CardStatus, number][]).map(([status, count]) => <button type="button" key={status} onClick={() => onCards(status)}><span className={`bar-${status.toLowerCase()}`}/><span>{statusLabels[status]}</span><strong>{count}</strong></button>)}</div>
        </> : <div className="quiet-state">카드가 등록되면 상태별 분포가 표시됩니다.</div>}
      </section>

      <section className="panel demand-panel">
        <div className="panel-heading"><div><h2>안내가 필요한 주제</h2><p>처리 대기 중인 답변 보류 신고 기준</p></div><button type="button" className="icon-button" aria-label="답변 보류 신고 보기" onClick={onRequests}><ArrowRight size={18}/></button></div>
        {demand.length ? <div className="topic-demand">{demand.map(([topic, count], index) => <button type="button" key={topic} onClick={onRequests}><span className="rank">{String(index + 1).padStart(2, '0')}</span><strong>{topics[topic] || topic}</strong><span>{count}건</span><ChevronRight size={15}/></button>)}</div> : <div className="quiet-state"><CircleCheck size={20}/><span>{connected ? '대기 중인 답변 보류 신고가 없습니다.' : '서버 연결 후 주제별 요청을 확인할 수 있습니다.'}</span></div>}
      </section>
    </div>
  </>;
}
