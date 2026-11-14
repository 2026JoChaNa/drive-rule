import { useMemo, useState, type FormEvent } from 'react'
import { AlertTriangle, ArrowUpRight, Check, CircleHelp, FileText, Plus, RefreshCw, RotateCcw, Search, X } from 'lucide-react'
import type { ApiClient } from './api'
import type { Card, LawContent, LawRevision, LawSyncResult, RequestStatus, ReviewRequest, TriageStatus } from './types'
import { conditionLabels, requestLabels, topics, triageLabels } from './types'
import { Badge, Empty, ErrorBox, Field, Modal, Spinner, dateLabel } from './ui'

interface LawsPageProps {
  laws: LawRevision[]
  api: ApiClient
  onRefresh: () => Promise<void>
  notify: (message: string) => void
  disabled: boolean
}

const triageStatuses: TriageStatus[] = ['PENDING', 'APPLICABLE', 'NOT_APPLICABLE']
const requestStatuses: RequestStatus[] = ['OPEN', 'RESOLVED', 'DISMISSED']
const conditionValues: Record<string, string> = { yes: '있음', no: '없음', red: '적색', yellow: '황색', green: '녹색', green_flashing: '녹색 점멸' }

export function LawsPage({ laws, api, onRefresh, notify, disabled }: LawsPageProps) {
  const [filter, setFilter] = useState<TriageStatus | 'ALL'>('ALL')
  const [query, setQuery] = useState('')
  const [busy, setBusy] = useState<number | 'sync' | null>(null)
  const [error, setError] = useState<unknown>(null)
  const [syncResult, setSyncResult] = useState<LawSyncResult | null>(null)
  const [showForm, setShowForm] = useState(false)
  const pending = laws.filter(law => law.triageStatus === 'PENDING').length
  const filtered = useMemo(() => {
    const normalized = query.trim().toLowerCase()
    return laws.filter(law => (filter === 'ALL' || law.triageStatus === filter)
      && (!normalized || `${law.lawName} ${law.lawNo} ${law.sourceKey}`.toLowerCase().includes(normalized)))
      .sort((left, right) => right.promulgatedOn.localeCompare(left.promulgatedOn) || right.id - left.id)
  }, [laws, filter, query])

  async function changeTriage(law: LawRevision, status: TriageStatus) {
    if (disabled || busy !== null || status === law.triageStatus) return
    setError(null)
    setBusy(law.id)
    let saved = false
    try {
      await api.triage(law.id, status)
      saved = true
      notify(`${law.lawNo}을(를) ${triageLabels[status]}으로 분류했습니다.`)
      await onRefresh()
    } catch (reason) {
      setError(saved ? new Error('분류를 저장했지만 목록을 갱신하지 못했습니다. 새로고침해 결과를 확인하세요.') : reason)
    } finally { setBusy(null) }
  }

  async function sync() {
    if (disabled || busy !== null) return
    setBusy('sync')
    setError(null)
    setSyncResult(null)
    let synced = false
    try {
      const result = await api.syncLaws()
      synced = true
      setSyncResult(result)
      const created = result.laws.reduce((count, law) => count + law.created.length, 0)
      const failures = result.laws.filter(law => law.outcome === 'FAILED').length
      notify(failures ? `새 개정 ${created}건 수집 · ${failures}개 법령 수집 실패` : `법령 수집 완료 · 새 개정 ${created}건`)
      await onRefresh()
    } catch (reason) {
      setError(synced ? new Error('법령 수집은 끝났지만 목록을 갱신하지 못했습니다. 새로고침해 결과를 확인하세요.') : reason)
    } finally { setBusy(null) }
  }

  return <>
    <div className="section-callout">
      <div className="stat-icon blue"><FileText size={22}/></div>
      <div><strong>{pending ? `분류를 기다리는 법령 개정이 ${pending}건 있습니다` : '운전자에게 필요한 개정 내용을 확인하세요'}</strong><p>일반 운전자에게 적용되는 법령을 분류한 뒤 안내 카드의 근거로 연결하세요.</p></div>
      <div className="action-row">
        <button className="button button-secondary" disabled={disabled || busy !== null} onClick={() => setShowForm(true)}><Plus size={16}/>직접 등록</button>
        <button className="button button-primary-soft" disabled={disabled || busy !== null} onClick={() => void sync()}>{busy === 'sync' ? <Spinner/> : <RefreshCw size={16}/>}법령 수집</button>
      </div>
    </div>
    <ErrorBox error={error}/>
    {syncResult && <section className="panel sync-results" aria-label="법령 수집 결과" aria-live="polite">
      <div className="panel-heading"><div><h2>법령 수집 결과</h2><p>{dateLabel(syncResult.from)} — {dateLabel(syncResult.to)}</p></div><button className="icon-button" aria-label="수집 결과 닫기" onClick={() => setSyncResult(null)}><X size={18}/></button></div>
      {syncResult.laws.length === 0 ? <p className="notice">수집 대상 법령이 없습니다.</p> : syncResult.laws.map(law => <div className="notice" key={law.lawId}>
        <div className="inline-tags"><Badge value={law.outcome === 'OK' ? 'RESOLVED' : 'OPEN'}>{law.outcome === 'OK' ? '수집 완료' : '수집 실패'}</Badge><strong>{law.lawName || `법령 ${law.lawId}`}</strong></div>
        {law.outcome === 'OK' ? <p>신규 {law.created.length}건 · 기존 {law.existing}건{law.created.length > 0 ? ` · ${law.created.join(', ')}` : ''}</p> : <p role="alert">{law.error || '수집하지 못했습니다. 서버의 법령 Open API 설정을 확인하세요.'}</p>}
      </div>)}
    </section>}
    <section className="panel">
      <div className="panel-heading"><div><h2>법령 개정 목록 <span className="muted">{laws.length}</span></h2><p>최신 공포일 순으로 표시합니다.</p></div></div>
      <div className="list-toolbar">
        <div className="tabs" role="group" aria-label="법령 분류 필터">
          <button className={filter === 'ALL' ? 'active' : ''} aria-pressed={filter === 'ALL'} onClick={() => setFilter('ALL')}>전체 <span>{laws.length}</span></button>
          {triageStatuses.map(status => <button key={status} className={filter === status ? 'active' : ''} aria-pressed={filter === status} onClick={() => setFilter(status)}>{triageLabels[status]} <span>{laws.filter(law => law.triageStatus === status).length}</span></button>)}
        </div>
        <label className="search-input"><Search size={17}/><input aria-label="법령 검색" placeholder="법령명, 공포번호 검색" value={query} onChange={event => setQuery(event.target.value)}/></label>
      </div>
      {filtered.length ? <div className="table-wrap"><table><thead><tr><th>법령 개정</th><th>공포번호</th><th>공포일</th><th>분류</th><th>원문</th></tr></thead><tbody>
        {filtered.map(law => <tr key={law.id}>
          <td><div className="cell-primary">{law.lawName}</div><small className="muted">{law.sourceKey}</small></td>
          <td>{law.lawNo}</td><td className="date-cell">{dateLabel(law.promulgatedOn)}</td>
          <td><div className="inline-tags">{busy === law.id && <Spinner/>}<select className="compact-select" aria-label={`${law.lawNo} 분류`} value={law.triageStatus} disabled={disabled || busy !== null} onChange={event => void changeTriage(law, event.target.value as TriageStatus)}>{triageStatuses.map(status => <option key={status} value={status}>{triageLabels[status]}</option>)}</select></div></td>
          <td>{law.sourceUrl && /^https?:\/\//i.test(law.sourceUrl) ? <a className="text-link" href={law.sourceUrl} target="_blank" rel="noopener noreferrer">원문 보기<ArrowUpRight size={15}/></a> : <span className="muted">직접 등록</span>}</td>
        </tr>)}
      </tbody></table></div> : <Empty title={laws.length ? '조건에 맞는 법령이 없습니다' : '등록된 법령 개정이 없습니다'} text={laws.length ? '검색어나 분류 필터를 변경해 보세요.' : '법령을 자동 수집하거나 직접 등록해 시작하세요.'}/>}
      <div className="panel-footer"><span>전체 {laws.length}건 중 {filtered.length}건</span><span>분류는 선택 즉시 저장됩니다.</span></div>
    </section>
    {showForm && <LawForm api={api} onClose={() => setShowForm(false)} onSaved={async () => { notify('법령 개정을 등록했습니다.'); await onRefresh() }}/>}
  </>
}

export function LawForm({ api, onClose, onSaved }: { api: ApiClient; onClose: () => void; onSaved: () => Promise<void> }) {
  const [content, setContent] = useState<LawContent>({ lawName: '', lawNo: '', sourceKey: '', promulgatedOn: '' })
  const [saving, setSaving] = useState(false)
  const [created, setCreated] = useState(false)
  const [error, setError] = useState<unknown>(null)

  async function save(event: FormEvent<HTMLFormElement>) {
    event.preventDefault()
    if (saving || created) return
    const body = { ...content, lawName: content.lawName.trim(), lawNo: content.lawNo.trim(), sourceKey: content.sourceKey.trim() }
    if (!body.lawName || !body.lawNo || !body.sourceKey || !body.promulgatedOn) { setError(new Error('모든 필수 항목을 입력해 주세요.')); return }
    setSaving(true)
    setError(null)
    let saved = false
    try {
      await api.addLaw(body)
      saved = true
      setCreated(true)
      await onSaved()
      onClose()
    } catch (reason) {
      setError(saved ? new Error('법령을 등록했지만 목록을 갱신하지 못했습니다. 닫은 뒤 목록을 새로고침하세요.') : reason)
    } finally { setSaving(false) }
  }

  function update(field: keyof LawContent, value: string) { setContent(current => ({ ...current, [field]: value })) }

  return <Modal title="법령 개정 등록" subtitle="공포된 법령의 기본 정보를 직접 등록합니다." onClose={() => { if (!saving) onClose() }}>
    <form onSubmit={event => void save(event)} aria-busy={saving}>
      <div className="modal-body"><ErrorBox error={error}/><div className="form-grid">
        <Field label="법령명 *" className="field-full" hint="법령명과 제·개정 구분을 함께 입력하세요."><input autoFocus required maxLength={100} placeholder="예: 도로교통법 일부개정" value={content.lawName} onChange={event => update('lawName', event.target.value)} disabled={saving || created}/></Field>
        <Field label="공포번호 *" hint="같은 공포번호는 중복 등록할 수 없습니다."><input required maxLength={30} placeholder="예: 제22067호" value={content.lawNo} onChange={event => update('lawNo', event.target.value)} disabled={saving || created}/></Field>
        <Field label="공포일 *"><input type="date" required value={content.promulgatedOn} onChange={event => update('promulgatedOn', event.target.value)} disabled={saving || created}/></Field>
        <Field label="출처 식별자 *" className="field-full" hint="직접 등록은 MANUAL-번호 형식을 사용하세요."><input required maxLength={40} placeholder="예: MANUAL-22067" value={content.sourceKey} onChange={event => update('sourceKey', event.target.value)} disabled={saving || created}/></Field>
      </div><p className="notice">등록한 법령은 ‘분류 대기’ 상태로 추가됩니다.</p></div>
      <div className="modal-footer"><button type="button" className="button button-secondary" disabled={saving} onClick={onClose}>{created ? '닫기' : '취소'}</button><button type="submit" className="button button-primary" disabled={saving || created}>{saving ? <Spinner/> : <Plus size={16}/>}등록</button></div>
    </form>
  </Modal>
}

interface RequestsPageProps {
  requests: ReviewRequest[]
  cards: Card[]
  api: ApiClient
  onRefresh: () => Promise<void>
  notify: (message: string) => void
  onCard: (id: number) => void
  disabled: boolean
}

export function RequestsPage({ requests, cards, api, onRefresh, notify, onCard, disabled }: RequestsPageProps) {
  const [filter, setFilter] = useState<RequestStatus | 'ALL'>('OPEN')
  const [type, setType] = useState<ReviewRequest['requestType'] | 'ALL'>('ALL')
  const [query, setQuery] = useState('')
  const [busy, setBusy] = useState<number | null>(null)
  const [error, setError] = useState<unknown>(null)
  const cardMap = useMemo(() => new Map(cards.map(card => [card.id, card])), [cards])
  const filtered = useMemo(() => {
    const normalized = query.trim().toLowerCase()
    return requests.filter(request => (filter === 'ALL' || request.status === filter) && (type === 'ALL' || request.requestType === type)
      && (!normalized || `${request.id} ${request.topic || ''} ${topics[request.topic || ''] || ''} ${cardMap.get(request.cardId || 0)?.title || ''}`.toLowerCase().includes(normalized)))
      .sort((left, right) => right.createdAt.localeCompare(left.createdAt) || right.id - left.id)
  }, [requests, filter, type, query, cardMap])
  const open = requests.filter(request => request.status === 'OPEN').length

  async function changeStatus(id: number, status: RequestStatus) {
    if (disabled || busy !== null) return
    setBusy(id)
    setError(null)
    let saved = false
    try {
      await api.requestStatus(id, status)
      saved = true
      notify(`요청 #${id}을(를) ${requestLabels[status]}로 변경했습니다.`)
      await onRefresh()
    } catch (reason) {
      setError(saved ? new Error('요청 상태를 저장했지만 목록을 갱신하지 못했습니다. 새로고침해 결과를 확인하세요.') : reason)
    } finally { setBusy(null) }
  }

  return <>
    <div className="section-callout"><div className="stat-icon orange"><CircleHelp size={22}/></div><div><strong>{open ? `확인이 필요한 요청이 ${open}건 있습니다` : '접수된 요청을 모두 확인했습니다'}</strong><p>오류 신고와 답변 보류 상황을 살펴보고 카드 내용을 보완하세요.</p></div></div>
    <ErrorBox error={error}/>
    <section className="panel">
      <div className="panel-heading"><div><h2>신고 · 보류 요청 <span className="muted">{requests.length}</span></h2><p>사용자에게 안내되지 못한 상황을 최신 접수순으로 확인합니다.</p></div></div>
      <div className="list-toolbar">
        <div className="tabs" role="group" aria-label="요청 상태 필터">
          {requestStatuses.map(status => <button key={status} className={filter === status ? 'active' : ''} aria-pressed={filter === status} onClick={() => setFilter(status)}>{requestLabels[status]} <span>{requests.filter(request => request.status === status).length}</span></button>)}
          <button className={filter === 'ALL' ? 'active' : ''} aria-pressed={filter === 'ALL'} onClick={() => setFilter('ALL')}>전체 <span>{requests.length}</span></button>
        </div>
        <div className="action-row"><select aria-label="요청 유형 필터" className="compact-select" value={type} onChange={event => setType(event.target.value as typeof type)}><option value="ALL">모든 유형</option><option value="ERROR_REPORT">오류 신고</option><option value="ANSWER_HOLD">답변 보류</option></select><label className="search-input"><Search size={17}/><input aria-label="요청 검색" placeholder="주제, 카드명 검색" value={query} onChange={event => setQuery(event.target.value)}/></label></div>
      </div>
      {filtered.length ? <div className="request-list">{filtered.map(request => {
        const linkedCard = request.cardId !== null ? cardMap.get(request.cardId) : undefined
        const conditions = Object.entries(request.conditions || {})
        return <article className="request-item" key={request.id} aria-busy={busy === request.id}>
          <div className={`stat-icon ${request.requestType === 'ERROR_REPORT' ? 'orange' : 'blue'}`}>{request.requestType === 'ERROR_REPORT' ? <AlertTriangle size={20}/> : <CircleHelp size={20}/>}</div>
          <div className="request-content">
            <div className="inline-tags"><Badge value={request.status}/><span className="muted">{request.requestType === 'ERROR_REPORT' ? '오류 신고' : '답변 보류'} · #{request.id}</span><span className="date-cell">{dateLabel(request.createdAt, true)}</span></div>
            <h3>{request.requestType === 'ERROR_REPORT' ? linkedCard?.title || `카드 #${request.cardId ?? '—'} 오류 신고` : `${topics[request.topic || ''] || request.topic || '주제 미지정'} 안내 보류`}</h3>
            {request.cardId !== null ? <button className="text-link" onClick={() => onCard(request.cardId!)} disabled={disabled}>연결 카드 #{request.cardId}{request.cardVersion !== null ? ` · v${request.cardVersion}` : ''} 확인<ArrowUpRight size={15}/></button> : <p className="muted">연결된 카드가 없습니다. 해당 주제의 안내 내용을 확인해 주세요.</p>}
            {request.topic && request.requestType === 'ERROR_REPORT' && <p className="muted">주제: {topics[request.topic] || request.topic}</p>}
            {conditions.length > 0 && <div className="condition-chips" aria-label="접수 당시 조건">{conditions.map(([key, value]) => <span key={key}>{conditionLabels[key as keyof typeof conditionLabels] || key}: {conditionValues[value] || value}</span>)}</div>}
            {request.requestType === 'ANSWER_HOLD' && conditions.length === 0 && <p className="muted">접수된 조건 정보가 없습니다.</p>}
          </div>
          <div className="request-actions">{busy === request.id && <Spinner/>}{request.status === 'OPEN' ? <>
            <button className="button button-primary-soft" disabled={disabled || busy !== null} onClick={() => void changeStatus(request.id, 'RESOLVED')}><Check size={15}/>처리 완료</button>
            <button className="button button-secondary" disabled={disabled || busy !== null} onClick={() => void changeStatus(request.id, 'DISMISSED')}><X size={15}/>종결</button>
          </> : <button className="button button-secondary" disabled={disabled || busy !== null} onClick={() => void changeStatus(request.id, 'OPEN')}><RotateCcw size={15}/>다시 열기</button>}</div>
        </article>
      })}</div> : <Empty title={requests.length ? '조건에 맞는 요청이 없습니다' : '접수된 요청이 없습니다'} text={requests.length ? '상태와 유형 필터 또는 검색어를 변경해 보세요.' : '사용자 오류 신고와 답변 보류 요청이 여기에 모입니다.'}/>}
      <div className="panel-footer"><span>전체 {requests.length}건 중 {filtered.length}건</span><span>상태를 변경해도 요청 기록은 유지됩니다.</span></div>
    </section>
  </>
}
