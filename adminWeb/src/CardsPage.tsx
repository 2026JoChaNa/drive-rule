import { useEffect, useMemo, useState } from 'react'
import { ArrowRight, ChevronLeft, ChevronRight, FileText, Plus, Search, X } from 'lucide-react'
import type { Card, CardStatus } from './types'
import { statusLabels, topics, typeLabels } from './types'
import { Badge, Empty, dateLabel } from './ui'

interface CardsPageProps {
  cards: Card[]
  status: CardStatus | 'ALL'
  setStatus: (status: CardStatus | 'ALL') => void
  onCard: (id: number) => void
  onNew: () => void
  disabled: boolean
}

const statuses: (CardStatus | 'ALL')[] = ['ALL', 'DRAFT', 'IN_REVIEW', 'PUBLISHED', 'RETIRED']
const PAGE_SIZE = 12

export function CardsPage({ cards, status, setStatus, onCard, onNew, disabled }: CardsPageProps) {
  const [query, setQuery] = useState('')
  const [page, setPage] = useState(1)
  const search = query.trim().toLocaleLowerCase('ko-KR')
  const filtered = useMemo(() => cards.filter((card) => {
    if (status !== 'ALL' && card.status !== status) return false
    if (!search) return true
    return [card.title, String(card.id), `#${card.id}`, card.legalBasis, card.topic, card.topic ? topics[card.topic] : null]
      .some((value) => value?.toLocaleLowerCase('ko-KR').includes(search))
  }).sort((left, right) => right.updatedAt.localeCompare(left.updatedAt) || right.id - left.id), [cards, status, search])
  const totalPages = Math.max(1, Math.ceil(filtered.length / PAGE_SIZE))
  const currentPage = Math.min(page, totalPages)
  const start = (currentPage - 1) * PAGE_SIZE
  const visible = filtered.slice(start, start + PAGE_SIZE)
  const counts = useMemo(() => cards.reduce((result, card) => {
    result[card.status] += 1
    return result
  }, { DRAFT: 0, IN_REVIEW: 0, PUBLISHED: 0, RETIRED: 0 }), [cards])

  useEffect(() => { setPage(1) }, [status, search])
  useEffect(() => { setPage((current) => Math.min(current, totalPages)) }, [totalPages])

  function resetFilters() {
    setQuery('')
    setStatus('ALL')
    setPage(1)
  }

  return <section className="panel" aria-label="안내 카드 목록">
    <div className="list-toolbar">
      <div className="tabs" role="group" aria-label="카드 상태 필터">
        {statuses.map((value) => <button key={value} type="button" className={status === value ? 'selected' : ''} aria-pressed={status === value} onClick={() => { setStatus(value); setPage(1) }}>
          {value === 'ALL' ? '전체' : statusLabels[value]} <span>{value === 'ALL' ? cards.length : counts[value]}</span>
        </button>)}
      </div>
      <div className="search-field">
        <Search size={17} aria-hidden="true" />
        <input aria-label="카드 검색" type="search" placeholder="제목, ID, 근거 조문 또는 주제 검색" value={query} onChange={(event) => { setQuery(event.target.value); setPage(1) }} />
        {query && <button type="button" className="icon-button" aria-label="검색어 지우기" onClick={() => { setQuery(''); setPage(1) }}><X size={15} /></button>}
      </div>
    </div>
    {visible.length ? <>
      <div className="table-wrap">
        <table>
          <thead><tr><th scope="col">카드 정보</th><th scope="col">대상</th><th scope="col">대표 주제</th><th scope="col">상태</th><th scope="col">최근 수정</th><th scope="col"><span className="sr-only">상세 보기</span></th></tr></thead>
          <tbody>{visible.map((card) => <tr key={card.id}>
            <td><div className="card-title-cell"><span className="document-icon"><FileText size={19} aria-hidden="true" /></span><div>
              <button type="button" className="cell-primary" onClick={() => onCard(card.id)}>{card.title}</button>
              <div className="muted">#{card.id} · {typeLabels[card.cardType]} · {card.changeKind === 'LAW' ? '법령 개정' : card.sourceOrg || '운영 변경'} · v{card.version}</div>
            </div></div></td>
            <td className="nowrap">{card.audience === 'GENERAL' ? '일반 운전자' : '일부 운전자'}</td>
            <td>{card.topic ? <span title={card.topic}>{topics[card.topic] ?? card.topic}</span> : <span className="muted">미지정</span>}</td>
            <td><Badge value={card.status} /></td>
            <td className="date-cell nowrap"><time dateTime={card.updatedAt} title={dateLabel(card.updatedAt, true)}>{dateLabel(card.updatedAt)}</time></td>
            <td><button type="button" className="icon-button" aria-label={`${card.title} 상세 보기`} onClick={() => onCard(card.id)}><ArrowRight size={17} /></button></td>
          </tr>)}</tbody>
        </table>
      </div>
      <div className="pagination">
        <span className="muted" aria-live="polite">총 {filtered.length.toLocaleString()}개 중 {start + 1}–{Math.min(start + PAGE_SIZE, filtered.length)}개</span>
        <div><button type="button" className="icon-button" aria-label="이전 페이지" disabled={currentPage === 1} onClick={() => setPage(currentPage - 1)}><ChevronLeft size={17} /></button><span aria-live="polite">{currentPage} / {totalPages}</span><button type="button" className="icon-button" aria-label="다음 페이지" disabled={currentPage === totalPages} onClick={() => setPage(currentPage + 1)}><ChevronRight size={17} /></button></div>
      </div>
    </> : <>
      <Empty title={cards.length ? '조건에 맞는 카드가 없습니다' : '첫 안내 카드를 만들어 보세요'} text={cards.length ? '검색어를 바꾸거나 다른 상태를 선택해 보세요.' : '카드를 작성하고 검수를 요청하면 운전자에게 안내할 수 있습니다.'} />
      <div className="empty-action">{cards.length ? <button type="button" className="button button-secondary" onClick={resetFilters}>필터 초기화</button> : <button type="button" className="button button-primary" onClick={onNew} disabled={disabled}><Plus size={17} /> 새 카드 만들기</button>}</div>
    </>}
  </section>
}
