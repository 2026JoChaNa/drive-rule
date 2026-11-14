import { useEffect, useRef, useState, type ChangeEvent, type FormEvent } from 'react'
import { FileText, GitBranch, Volume2 } from 'lucide-react'
import type { Card, CardContent, LawRevision } from './types'
import { topics } from './types'
import { ErrorBox, Field, Modal, Spinner } from './ui'
import { CARD_LIMITS, DECISION_EXAMPLE, RULE_CONDITIONS, newCardContent, prepareCardContent, validateCardContent, validateDecision } from './card-validation'

interface CardEditorProps {
  card: Card | null
  revisions: LawRevision[]
  onSave: (content: CardContent) => Promise<void>
  onClose: () => void
}

export function CardEditor({ card, revisions, onSave, onClose }: CardEditorProps) {
  const [initial] = useState(() => newCardContent(card))
  const [content, setContent] = useState<CardContent>(initial)
  const [initialDecision] = useState(() => initial.decision ? JSON.stringify(initial.decision, null, 2) : '')
  const [decisionJson, setDecisionJson] = useState(initialDecision)
  const [errors, setErrors] = useState<string[]>([])
  const [error, setError] = useState<Error | null>(null)
  const [saving, setSaving] = useState(false)
  const [decisionChecked, setDecisionChecked] = useState(false)
  const errorRef = useRef<HTMLDivElement>(null)
  const dirty = JSON.stringify(content) !== JSON.stringify(initial) || decisionJson !== initialDecision
  const topicLocked = Boolean(card?.verifiedAt)
  const conditional = content.cardType === 'CONDITIONAL'

  useEffect(() => {
    if (!dirty) return
    const preventExit = (event: BeforeUnloadEvent) => { event.preventDefault(); event.returnValue = '' }
    window.addEventListener('beforeunload', preventExit)
    return () => window.removeEventListener('beforeunload', preventExit)
  }, [dirty])

  useEffect(() => {
    if (!error && !errors.length) return
    errorRef.current?.focus({ preventScroll: true })
    errorRef.current?.scrollIntoView({ block: 'nearest', behavior: 'smooth' })
  }, [error, errors])

  function update<K extends keyof CardContent>(key: K, value: CardContent[K]) {
    setContent((current) => ({ ...current, [key]: value }))
  }

  function close() {
    if (saving) return
    if (!dirty || window.confirm('저장하지 않은 변경 사항이 있습니다. 편집을 닫을까요?')) onClose()
  }

  function checkDecision() {
    try {
      const validationErrors = validateDecision(JSON.parse(decisionJson))
      setErrors(validationErrors)
      setDecisionChecked(validationErrors.length === 0)
    } catch {
      setErrors(['결정 테이블 JSON 형식이 올바르지 않습니다. 따옴표, 쉼표, 괄호를 확인해 주세요.'])
      setDecisionChecked(false)
    }
  }

  async function save(event: FormEvent<HTMLFormElement>) {
    event.preventDefault()
    if (saving) return
    setError(null)
    let prepared: CardContent
    try {
      prepared = prepareCardContent(content, decisionJson, card)
    } catch {
      setErrors(['결정 테이블 JSON 형식이 올바르지 않습니다. 따옴표, 쉼표, 괄호를 확인해 주세요.'])
      return
    }
    const validationErrors = validateCardContent(prepared)
    setErrors(validationErrors)
    if (validationErrors.length) return
    setSaving(true)
    try {
      await onSave(prepared)
      onClose()
    } catch (cause) {
      setError(cause instanceof Error ? cause : new Error('카드를 저장하지 못했습니다. 다시 시도해 주세요.'))
    } finally {
      setSaving(false)
    }
  }

  function textField(key: keyof typeof CARD_LIMITS, label: string, options: { required?: boolean; rows?: number; hint?: string; full?: boolean; placeholder?: string } = {}) {
    const value = content[key] ?? ''
    const shared = {
      value, maxLength: CARD_LIMITS[key], required: options.required,
      onChange: (event: ChangeEvent<HTMLInputElement | HTMLTextAreaElement>) => update(key, event.target.value),
      placeholder: options.placeholder,
    }
    return <Field label={`${label}${options.required ? ' *' : ''}`} className={options.full ? 'field-full' : undefined} hint={options.hint}>
      {options.rows ? <textarea {...shared} rows={options.rows} /> : <input {...shared} />}
      <small className="character-count">{value.length.toLocaleString()} / {CARD_LIMITS[key].toLocaleString()}</small>
    </Field>
  }

  const cleared: string[] = []
  if (conditional && (content.dontText || content.doText || content.sceneKey)) cleared.push('행동 비교 · 장면 키')
  if (!conditional && decisionJson.trim()) cleared.push('결정 테이블')
  if (content.changeKind === 'LAW' && content.sourceOrg) cleared.push('출처 기관')
  if (content.changeKind === 'OPERATION' && content.revisionId !== null) cleared.push('연결된 법령 개정')
  if (content.audience === 'LIMITED' && content.eventTag) cleared.push('이벤트 태그')

  return <Modal title={card ? '카드 수정' : '새 카드 만들기'} subtitle={card ? `카드 #${card.id} · 저장하면 초안 상태로 돌아갑니다.` : '안내 내용과 근거를 작성한 뒤 검수를 요청하세요.'} onClose={close} wide>
    <form onSubmit={save}>
      <div className="modal-body card-editor-body">
        <div className="notice">필수 항목은 *로 표시합니다. 저장한 카드는 초안입니다. 로컬 개발 환경에서는 검수 요청 후 한 번 승인하면 게시됩니다.</div>
        {(error || errors.length > 0) && <div ref={errorRef} tabIndex={-1}>
          {error && <ErrorBox error={error} />}
          {errors.length > 0 && <div className="error-box" role="alert"><div><strong>입력 내용을 확인해 주세요</strong><ul>{errors.map((message, index) => <li key={`${index}-${message}`}>{message}</li>)}</ul></div></div>}
        </div>}
        <fieldset disabled={saving} className="editor-fields">
          <section className="form-section">
            <h3><FileText size={18} aria-hidden="true" /> 기본 정보</h3>
            <div className="form-grid">
              {textField('title', '카드 제목', { required: true, full: true, placeholder: '예: 우회전 시 보행자 보호 의무' })}
              <Field label="카드 유형 *" hint="조건 분기형은 상황별 안내를 결정 테이블로 작성합니다.">
                <select value={content.cardType} onChange={(event) => update('cardType', event.target.value as CardContent['cardType'])}>
                  <option value="SHORT">단문</option><option value="CONDITIONAL">조건 분기형</option><option value="FIXED_RESPONSE">고정 응답</option>
                </select>
              </Field>
              <Field label="변경 종류 *"><select value={content.changeKind} onChange={(event) => update('changeKind', event.target.value as CardContent['changeKind'])}><option value="LAW">법령 개정</option><option value="OPERATION">운영 변경</option></select></Field>
              <Field label="대상 운전자 *"><select value={content.audience} onChange={(event) => update('audience', event.target.value as CardContent['audience'])}><option value="GENERAL">일반 운전자</option><option value="LIMITED">일부 운전자</option></select></Field>
              <Field label="주행 이벤트" hint={content.audience === 'LIMITED' ? '일부 운전자 대상 카드는 이벤트 안내를 사용하지 않습니다.' : '미지정 시 Gleo 질의 응답에만 사용합니다.'}>
                <select value={content.audience === 'LIMITED' ? '' : content.eventTag ?? ''} disabled={content.audience === 'LIMITED'} onChange={(event) => update('eventTag', (event.target.value || null) as CardContent['eventTag'])}>
                  <option value="">이벤트 미지정</option><option value="SCHOOL_ZONE_ENTRY">어린이보호구역 진입</option><option value="PARKING_DESTINATION">보호구역 근처 주정차</option><option value="PARKING_HIGHWAY">고속도로 주정차</option><option value="RIGHT_TURN_CROSSWALK">우회전 후 횡단보도</option>
                </select>
              </Field>
              {content.changeKind === 'LAW' ? <Field label="연결할 법령 개정" hint="관련 법령 개정이 있을 때 선택하세요."><select value={content.revisionId ?? ''} onChange={(event) => update('revisionId', event.target.value ? Number(event.target.value) : null)}><option value="">연결 안 함</option>{content.revisionId !== null && !revisions.some((revision) => revision.id === content.revisionId) && <option value={content.revisionId}>기존 법령 개정 #{content.revisionId}</option>}{revisions.map((revision) => <option key={revision.id} value={revision.id}>{revision.lawName} · {revision.lawNo}</option>)}</select></Field> : textField('sourceOrg', '출처 기관', { required: true, placeholder: '예: 한국도로공사' })}
              <Field label="Gleo 대표 주제" hint={topicLocked ? '게시 이력이 있는 카드입니다. 주제는 게시 중일 때 상세 화면에서 변경하세요.' : '주제별 대표 카드는 1개입니다. 비워 두면 대표 카드를 지정하지 않습니다.'}>
                <input value={content.topic ?? ''} list="card-topic-options" disabled={topicLocked} maxLength={40} onChange={(event) => update('topic', event.target.value)} placeholder="주제 키 입력 또는 선택" />
                <datalist id="card-topic-options">{[...new Set([...Object.keys(topics), ...(content.topic ? [content.topic] : [])])].map((topic) => <option key={topic} value={topic}>{topics[topic] ?? '기존 주제'}</option>)}</datalist>
              </Field>
            </div>
          </section>
          <section className="form-section">
            <h3><Volume2 size={18} aria-hidden="true" /> 운전자 안내</h3>
            <div className="form-grid">
              {textField('ttsText', '음성 안내', { required: true, rows: 3, full: true, placeholder: '운전자에게 읽어 줄 안내 문장을 입력하세요.' })}
              {!conditional && <>{textField('dontText', '잘못된 행동', { rows: 3, hint: content.cardType === 'SHORT' ? '올바른 행동과 함께 입력하거나 둘 다 비워 주세요.' : undefined, placeholder: '피해야 할 행동' })}{textField('doText', '올바른 행동', { rows: 3, placeholder: '권장하는 행동' })}{textField('sceneKey', '장면 그림 키', { hint: '앱에 등록된 그림의 키를 입력하세요.', placeholder: '예: right_turn_light' })}</>}
              {textField('penaltyText', '위반 시 제재', { rows: 2, full: conditional, placeholder: '범칙금, 벌점 등 필요한 내용을 입력하세요.' })}
            </div>
          </section>
          {conditional && <section className="form-section">
            <h3><GitBranch size={18} aria-hidden="true" /> 조건별 결정 테이블</h3>
            <p className="muted">소표 최대 2개 · 소표당 조건 최대 3개 · 선택지 조합 최대 8개. 각 상황에는 정확히 한 행이 일치해야 합니다.</p>
            <div className="form-grid">
              <Field label="결정 테이블 JSON *" className="field-full" hint="when에서 생략한 조건은 모든 값에 일치합니다. when 전체가 null이거나 {}여도 동일합니다. scene은 앱 그림 키이며 선택 항목입니다.">
                <textarea className="code-input" value={decisionJson} rows={17} required spellCheck={false} onChange={(event) => { setDecisionJson(event.target.value); setDecisionChecked(false) }} placeholder={'{ "schema": 2, "tables": [...] }'} />
              </Field>
            </div>
            <div className="editor-tools"><button className="button button-secondary" type="button" onClick={() => {
              if (decisionJson.trim() && !window.confirm('현재 결정 테이블을 예시로 바꿀까요?')) return
              setDecisionJson(JSON.stringify(DECISION_EXAMPLE, null, 2)); setDecisionChecked(false)
            }}>예시 불러오기</button><button className="button button-secondary" type="button" onClick={checkDecision}>형식 · 조합 검사</button>{decisionChecked && <span className="validation-success" role="status">모든 조합이 올바르게 연결되어 있습니다.</span>}</div>
            <details className="condition-reference"><summary>사용 가능한 공통 조건 확인</summary><dl>{Object.entries(RULE_CONDITIONS).map(([key, options]) => <div key={key}><dt><code>{key}</code></dt><dd>{options.join(' · ')}</dd></div>)}</dl><p className="muted">예시의 화면·음성 문장은 실제 안내 내용으로 수정한 뒤 저장하세요.</p></details>
          </section>}
          <section className="form-section">
            <h3>법적 근거 및 시행</h3>
            <div className="form-grid">
              {textField('legalBasis', '법적 근거', { required: true, full: true, placeholder: '예: 도로교통법 제27조 제1항' })}
              {textField('lawExcerpt', '법령 원문', { required: true, rows: 5, full: true, placeholder: '안내 내용의 근거가 되는 법령 원문을 입력하세요.' })}
              <Field label="시행일"><input type="date" value={content.effectiveOn ?? ''} onChange={(event) => update('effectiveOn', event.target.value || null)} /></Field>
              {textField('exceptions', '예외 및 유의 사항', { rows: 3, full: true, placeholder: '적용 예외나 확인이 필요한 사항을 입력하세요.' })}
            </div>
          </section>
        </fieldset>
        {cleared.length > 0 && <div className="notice">선택한 유형의 저장 규칙에 따라 다음 항목을 비웁니다: <strong>{cleared.join(', ')}</strong>. 저장 전 유형을 되돌리면 입력 내용이 복원됩니다.</div>}
      </div>
      <div className="modal-footer"><button className="button button-secondary" type="button" onClick={close} disabled={saving}>취소</button><button className="button button-primary" type="submit" disabled={saving || card?.status === 'RETIRED'}>{saving ? <><Spinner /> 저장 중</> : card ? '수정 내용 저장' : '초안 저장'}</button></div>
    </form>
  </Modal>
}
