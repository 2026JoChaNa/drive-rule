import { useEffect, useRef, useState, type FormEvent } from 'react';
import { ArrowRight, Bell, BookOpen, ChevronRight, FileText, LayoutDashboard, Link2, Menu, Plus, RefreshCw, Settings2, ShieldCheck, TrafficCone, X, Check } from 'lucide-react';
import { ApiClient } from './api';
import { CardEditor } from './CardEditor';
import { CardDetail } from './CardDetail';
import { CardsPage } from './CardsPage';
import Dashboard from './Dashboard';
import { LawsPage, LawForm, RequestsPage } from './ManagementPages';
import { ErrorBox, Field, Modal, Spinner } from './ui';
import type { Card, CardStatus, LawRevision, Reviewer, ReviewRequest } from './types';

type Page = 'dashboard' | 'cards' | 'laws' | 'requests';
type Data = { cards: Card[]; laws: LawRevision[]; requests: ReviewRequest[]; reviewers: Reviewer[] };
const emptyData: Data = { cards: [], laws: [], requests: [], reviewers: [] };
const names: Record<Page, string> = { dashboard: '대시보드', cards: '카드 관리', laws: '법령 개정', requests: '신고 대기열' };
const items = [{ key: 'dashboard', icon: LayoutDashboard }, { key: 'cards', icon: FileText }, { key: 'laws', icon: BookOpen }, { key: 'requests', icon: Bell }] as const;

export default function App() {
  const [api, setApi] = useState(() => new ApiClient(__DEMO_MODE__ ? 'local' : ''));
  const [page, setPage] = useState<Page>('dashboard');
  const [data, setData] = useState<Data>(emptyData);
  const [connected, setConnected] = useState(false);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState<unknown>(null);
  const [updated, setUpdated] = useState<Date | null>(null);
  const [connection, setConnection] = useState(false);
  const [menu, setMenu] = useState(false);
  const [editor, setEditor] = useState<Card | 'new' | null>(null);
  const [detail, setDetail] = useState<number | null>(null);
  const [lawForm, setLawForm] = useState(false);
  const [status, setStatus] = useState<CardStatus | 'ALL'>('ALL');
  const [toast, setToast] = useState('');
  const [help, setHelp] = useState(false);
  const generation = useRef(0);
  async function refresh(client = api) {
    const id = ++generation.current; setLoading(true); setError(null);
    try {
      const [cards, laws, requests, reviewers] = await Promise.all([client.cards(), client.laws(), client.requests(), client.reviewers()]);
      if (id !== generation.current) return;
      setData({ cards, laws, requests, reviewers }); setConnected(true); setUpdated(new Date());
    } catch (e) { if (id === generation.current) { setError(e); setConnected(false); } }
    finally { if (id === generation.current) setLoading(false); }
  }
  useEffect(() => { void refresh(api); return () => { generation.current++; }; }, [api]);
  useEffect(() => { if (!toast) return; const id = setTimeout(() => setToast(''), 6000); return () => clearTimeout(id); }, [toast]);
  const navigate = (next: Page) => { setPage(next); setMenu(false); };
  const showCards = (filter: CardStatus | 'ALL' = 'ALL') => { setStatus(filter); navigate('cards'); };
  const open = data.requests.filter(r => r.status === 'OPEN').length;
  const canAct = connected && !loading;
  return <div className="app-shell">
    {menu && <button className="nav-scrim" aria-label="메뉴 닫기" onClick={() => setMenu(false)}/>}
    <aside className={'sidebar ' + (menu ? 'is-open' : '')}>
      <a className="brand" href="#" onClick={e => { e.preventDefault(); navigate('dashboard'); }}><span className="brand-symbol"><TrafficCone size={23}/></span><span>DRIVE RULE<small>CONTENT ADMIN</small></span></a>
      <div className="workspace-label">워크스페이스 <span>LOCAL</span></div>
      <nav aria-label="주 메뉴">{items.map(({ key, icon: Icon }) => <button key={key} className={'nav-item ' + (page === key ? 'active' : '')} onClick={() => navigate(key)} aria-current={page === key ? 'page' : undefined}><Icon size={19}/><span>{names[key]}</span>{key === 'requests' && open > 0 && <b>{open}</b>}</button>)}</nav>
      <div className="sidebar-note"><div className="note-mark"><ShieldCheck size={22}/></div><strong>더 정확한 안내의 시작</strong><p>운전자에게 전달될 정보를<br/>한곳에서 관리하세요.</p><button onClick={() => setHelp(true)}>운영 가이드 <ArrowRight size={14}/></button></div>
      <div className="sidebar-bottom"><button className="nav-item" onClick={() => setConnection(true)}><Settings2 size={19}/><span>연결 설정</span></button><div className="operator"><span className="avatar">DR</span><div><strong>로컬 운영자</strong><small>개인 개발 워크스페이스</small></div><span className="online-dot"/></div></div>
    </aside>
    <div className="workspace">
      <header className="topbar"><div className="breadcrumb"><button className="icon-button mobile-only" aria-label="메뉴 열기" onClick={() => setMenu(true)}><Menu size={20}/></button><span>워크스페이스</span><ChevronRight size={14}/><strong>{names[page]}</strong></div><div className="topbar-actions"><span className={'connection-pill ' + (connected ? 'is-connected' : '')}><i/>{__DEMO_MODE__ ? 'DEMO · 샘플 데이터' : connected ? 'API 연결됨' : 'API 연결 확인'}</span><button className="icon-button notification-button" aria-label={'신고 대기열 ' + open + '건'} onClick={() => navigate('requests')}><Bell size={19}/>{open > 0 && <i/>}</button><div className="avatar small">DR</div></div></header>
      <main>
        <div className="page-heading"><div><div className="eyebrow">{page === 'dashboard' ? 'WORKSPACE OVERVIEW' : page === 'cards' ? 'CONTENT LIBRARY' : page === 'laws' ? 'LAW REVISIONS' : 'REVIEW INBOX'}</div><h1>{page === 'dashboard' ? '운영 현황을 한눈에' : names[page]}<span className="heading-dot">.</span></h1><p>{page === 'dashboard' ? '좋은 안내는 꼼꼼한 관리에서 시작됩니다. 오늘의 콘텐츠를 확인해 보세요.' : page === 'cards' ? '작성부터 검수, 게시까지 콘텐츠의 전체 과정을 관리하세요.' : page === 'laws' ? '법령 개정을 확인하고 운전자에게 필요한 변화를 분류하세요.' : '앱에서 접수된 오류 신고와 답변 보류 요청을 확인하세요.'}</p></div><div className="page-actions"><button className="button button-secondary" onClick={() => void refresh()} disabled={loading}>{loading ? <Spinner/> : <RefreshCw size={16}/>} 새로고침</button>{(page === 'dashboard' || page === 'cards') && <button className="button button-primary" disabled={!canAct} onClick={() => setEditor('new')}><Plus size={17}/> 새 카드 작성</button>}{page === 'laws' && <button className="button button-primary" disabled={!canAct} onClick={() => setLawForm(true)}><Plus size={17}/> 개정 등록</button>}</div></div>
        {__DEMO_MODE__ && <div className="demo-banner"><span>DEMO</span>로컬 샘플 데이터로 기능을 테스트하고 있습니다. 변경 내용은 데모 서버 종료 시 초기화됩니다.</div>}
        {error ? <div className="connection-error"><ErrorBox error={error}/><button className="button button-secondary" onClick={() => setConnection(true)}>연결 설정 확인</button></div> : null}
        {loading && !updated ? <div className="loading-panel"><Spinner/><span>운영 데이터를 불러오고 있습니다.</span></div> : <>
          {page === 'dashboard' && <Dashboard data={data} connected={connected} onCards={showCards} onCard={setDetail} onLaws={() => navigate('laws')} onRequests={() => navigate('requests')}/>}
          {page === 'cards' && <CardsPage cards={data.cards} status={status} setStatus={setStatus} onCard={setDetail} onNew={() => setEditor('new')} disabled={!canAct}/>}
          {page === 'laws' && <LawsPage laws={data.laws} api={api} onRefresh={refresh} notify={setToast} disabled={!canAct}/>}
          {page === 'requests' && <RequestsPage requests={data.requests} cards={data.cards} api={api} onRefresh={refresh} notify={setToast} onCard={setDetail} disabled={!canAct}/>}
        </>}
        <footer className="page-footer"><span>DRIVE RULE <span className="footer-divider">/</span> 콘텐츠 운영 워크스페이스</span><span>{updated ? '마지막 갱신 ' + updated.toLocaleTimeString('ko-KR', { hour12: false, timeZone: 'Asia/Seoul' }) : 'API 연결을 기다리고 있습니다'}<span className="footer-divider">·</span>서울 기준</span></footer>
      </main>
    </div>
    {connection && <ConnectionModal onClose={() => setConnection(false)} onConnect={client => { setData(emptyData); setUpdated(null); setConnected(false); setApi(client); setConnection(false); }}/>}
    {editor && <CardEditor card={editor === 'new' ? null : editor} revisions={data.laws} onClose={() => setEditor(null)} onSave={async content => { await api.saveCard(content, editor === 'new' ? undefined : editor.id); setEditor(null); await refresh(); setToast('카드를 작성 중 상태로 저장했습니다.'); }}/>}
    {detail !== null && <CardDetail id={detail} api={api} reviewers={data.reviewers} disabled={!connected} onClose={() => setDetail(null)} onEdit={card => { setDetail(null); setEditor(card); }} onRefresh={refresh} notify={setToast}/>}
    {lawForm && <LawForm api={api} onClose={() => setLawForm(false)} onSaved={async () => { setLawForm(false); await refresh(); setToast('법령 개정을 등록했습니다.'); }}/>}
    {help && <Modal title="로컬 운영 가이드" subtitle="한 사람이 작성부터 게시까지" onClose={() => setHelp(false)}><div className="modal-body guide"><div><span>01</span><h3>법령을 확인하고 카드를 작성하세요</h3><p>관련 개정을 등록·분류하고 안내 문장과 근거를 입력합니다. 운영 변경은 출처 기관을 함께 작성합니다.</p></div><div><span>02</span><h3>내용을 확인하고 검수하세요</h3><p>카드 상세에서 검수 요청 후 로컬 운영자로 승인합니다. 데모와 백엔드 local 프로필은 승인 1건으로 게시됩니다. 기본 백엔드 설정은 승인 2건입니다.</p></div><div><span>03</span><h3>앱 동기화 결과를 확인하세요</h3><p>게시된 카드의 ‘앱 게시 확인’에서 동기화 응답을 확인합니다. 게시 후 수정하면 다시 검수가 필요하며, 회수는 되돌릴 수 없습니다.</p></div><p className="muted">별도 계정 로그인은 없습니다. API 키는 연결 설정에서 입력하거나 로컬 .env의 ADMIN_API_KEY로 설정합니다.</p></div><div className="modal-footer"><button className="button button-primary" onClick={() => setHelp(false)}>확인했어요</button></div></Modal>}
    {toast && <div className="toast" role="status"><Check size={18}/>{toast}<button className="icon-button" aria-label="알림 닫기" onClick={() => setToast('')}><X size={16}/></button></div>}
  </div>;
}

function ConnectionModal({ onClose, onConnect }: { onClose: () => void; onConnect: (api: ApiClient) => void }) {
  const [key, setKey] = useState(__DEMO_MODE__ ? 'local' : ''); const [error, setError] = useState<unknown>(null); const [busy, setBusy] = useState(false);
  async function submit(e: FormEvent) { e.preventDefault(); setBusy(true); setError(null); try { const client = new ApiClient(key.trim()); await client.reviewers(); onConnect(client); } catch (e) { setError(e); } finally { setBusy(false); } }
  return <Modal title="백엔드 연결 설정" subtitle="별도 계정 없이 API 키로 연결합니다." onClose={() => { if (!busy) onClose(); }}><form onSubmit={submit}><div className="modal-body"><div className="connection-target"><span className="stat-icon green"><Link2 size={20}/></span><div><strong>{__DEMO_MODE__ ? '로컬 데모 서버' : 'API 프록시 대상'}</strong><code>{__API_LABEL__}</code></div></div><Field label="관리자 API 키" hint="입력한 키는 현재 화면의 메모리에만 보관합니다. 새로고침하면 지워집니다."><input type="password" autoComplete="off" placeholder="서버의 ADMIN_API_KEY 값" value={key} onChange={e => setKey(e.target.value)}/></Field><p className="notice">로컬 .env에 ADMIN_API_KEY를 설정했다면 입력하지 않아도 연결됩니다. 서버 주소는 .env의 API_TARGET을 변경한 뒤 웹 개발 서버를 다시 실행하세요.</p><ErrorBox error={error}/></div><div className="modal-footer"><button type="button" disabled={busy} className="button button-secondary" onClick={onClose}>닫기</button><button className="button button-primary" disabled={busy}>{busy ? <Spinner/> : <Link2 size={16}/>} 연결 확인</button></div></form></Modal>;
}
