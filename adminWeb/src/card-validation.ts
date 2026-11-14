import type { Card, CardContent } from './types'

export const CARD_LIMITS = {
  title: 100, sourceOrg: 100, topic: 40, dontText: 300, doText: 300,
  sceneKey: 40, ttsText: 500, legalBasis: 200, lawExcerpt: 20000,
  penaltyText: 300, exceptions: 20000,
} as const

export const RULE_CONDITIONS: Record<string, readonly string[]> = {
  right_turn_traffic_light: ['yes', 'no'],
  front_traffic_light: ['red', 'yellow', 'green'],
  crosswalk_light: ['green', 'green_flashing', 'red'],
  pedestrian_present: ['yes', 'no'],
}

export const DECISION_EXAMPLE = {
  schema: 2,
  tables: [{
    key: 'approach', title: '교차로 진입 전', conditions: ['front_traffic_light'],
    rows: [
      { when: { front_traffic_light: 'red' }, text: '적색 신호일 때 안내 문장을 작성하세요.', tts: '적색 신호일 때 읽어 줄 문장을 작성하세요.', scene: null },
      { when: { front_traffic_light: 'yellow' }, text: '황색 신호일 때 안내 문장을 작성하세요.', tts: '황색 신호일 때 읽어 줄 문장을 작성하세요.', scene: null },
      { when: { front_traffic_light: 'green' }, text: '녹색 신호일 때 안내 문장을 작성하세요.', tts: '녹색 신호일 때 읽어 줄 문장을 작성하세요.', scene: null },
    ],
  }],
}

function isObject(value: unknown): value is Record<string, unknown> {
  return value !== null && typeof value === 'object' && !Array.isArray(value)
}

function isBlank(value: unknown) {
  return typeof value !== 'string' || !value.trim()
}

/** Mirrors the server's schema-2 validation, including complete, unique coverage. */
export function validateDecision(decision: unknown): string[] {
  const errors: string[] = []
  if (!isObject(decision)) return ['결정 테이블은 JSON 객체로 입력해 주세요.']
  if (decision.schema !== 2) errors.push('decision.schema: 현재 형식은 2입니다.')
  if (!Array.isArray(decision.tables)) return [...errors, 'decision.tables: 소표 배열이 필요합니다.']
  if (decision.tables.length < 1 || decision.tables.length > 2) errors.push('decision.tables: 소표는 1~2개여야 합니다.')
  const tableKeys = new Set<string>()
  decision.tables.forEach((table: unknown, tableIndex: number) => {
    const at = `소표 ${tableIndex + 1}`
    if (!isObject(table)) { errors.push(`${at}: 객체여야 합니다.`); return }
    if (isBlank(table.key)) errors.push(`${at}.key: 키를 입력해 주세요.`)
    else if (tableKeys.has(table.key as string)) errors.push(`${at}.key: 중복된 키입니다 (${String(table.key)}).`)
    else tableKeys.add(table.key as string)
    if (isBlank(table.title)) errors.push(`${at}.title: 제목을 입력해 주세요.`)
    const before = errors.length
    const conditions = new Map<string, readonly string[]>()
    if (!Array.isArray(table.conditions) || !table.conditions.length) {
      errors.push(`${at}.conditions: 조건이 1개 이상 필요합니다.`)
    } else {
      if (table.conditions.length > 3) errors.push(`${at}.conditions: 조건은 최대 3개입니다.`)
      table.conditions.forEach((key: unknown) => {
        if (typeof key !== 'string' || !Object.hasOwn(RULE_CONDITIONS, key)) errors.push(`${at}.conditions: 등록되지 않은 조건입니다 (${String(key)}).`)
        else if (conditions.has(key)) errors.push(`${at}.conditions: 중복된 조건입니다 (${key}).`)
        else conditions.set(key, RULE_CONDITIONS[key])
      })
    }
    const combinations = [...conditions.values()].reduce((count, options) => count * options.length, 1)
    if (combinations > 8) errors.push(`${at}.conditions: 선택지 조합은 최대 8개입니다 (현재 ${combinations}개).`)
    if (!Array.isArray(table.rows) || !table.rows.length) {
      errors.push(`${at}.rows: 행이 1개 이상 필요합니다.`)
      return
    }
    table.rows.forEach((row: unknown, rowIndex: number) => {
      const rowAt = `${at}, 행 ${rowIndex + 1}`
      if (!isObject(row)) { errors.push(`${rowAt}: 객체여야 합니다.`); return }
      if (isBlank(row.text)) errors.push(`${rowAt}.text: 화면 문장을 입력해 주세요.`)
      if (isBlank(row.tts)) errors.push(`${rowAt}.tts: 음성 문장을 입력해 주세요.`)
      if (row.scene != null && typeof row.scene !== 'string') errors.push(`${rowAt}.scene: 그림 키는 문자열 또는 null이어야 합니다.`)
      // A missing or null entire `when` is a wildcard; an individual null value is not.
      if (row.when == null) return
      if (!isObject(row.when)) { errors.push(`${rowAt}.when: 객체 또는 null이어야 합니다.`); return }
      Object.entries(row.when).forEach(([key, option]) => {
        if (!conditions.has(key)) errors.push(`${rowAt}.when: 이 소표에 없는 조건입니다 (${key}).`)
        else if (typeof option !== 'string' || !conditions.get(key)!.includes(option)) errors.push(`${rowAt}.when: ${key}에 없는 선택지입니다 (${String(option)}).`)
      })
    })
    if (errors.length !== before) return
    let combos: Record<string, string>[] = [{}]
    conditions.forEach((options, key) => {
      combos = combos.flatMap((combo) => options.map((option) => ({ ...combo, [key]: option })))
    })
    combos.forEach((combo) => {
      const hits = (table.rows as Record<string, unknown>[]).filter((row) =>
        Object.entries((row.when ?? {}) as Record<string, unknown>).every(([key, value]) => combo[key] === value),
      ).length
      const label = Object.entries(combo).map(([key, value]) => `${key}=${value}`).join(', ')
      if (!hits) errors.push(`${at}: 안내가 없는 조합입니다 (${label}).`)
      if (hits > 1) errors.push(`${at}: ${hits}개 행이 겹치는 조합입니다 (${label}).`)
    })
  })
  return errors
}

export function newCardContent(card: Card | null): CardContent {
  return {
    revisionId: card?.revisionId ?? null,
    cardType: card?.cardType ?? 'SHORT',
    changeKind: card?.changeKind ?? 'LAW',
    audience: card?.audience ?? 'GENERAL',
    eventTag: card?.eventTag ?? null,
    sourceOrg: card?.sourceOrg ?? null,
    topic: card?.topic ?? null,
    title: card?.title ?? '',
    dontText: card?.dontText ?? null,
    doText: card?.doText ?? null,
    sceneKey: card?.sceneKey ?? null,
    ttsText: card?.ttsText ?? '',
    legalBasis: card?.legalBasis ?? '',
    lawExcerpt: card?.lawExcerpt ?? '',
    penaltyText: card?.penaltyText ?? null,
    effectiveOn: card?.effectiveOn ?? null,
    exceptions: card?.exceptions ?? null,
    decision: card?.decision ?? null,
  }
}

const optionalText = ['sourceOrg', 'topic', 'dontText', 'doText', 'sceneKey', 'penaltyText', 'exceptions'] as const

/** Keep every content field for PUT, clear only fields forbidden by the selected type. */
export function prepareCardContent(draft: CardContent, decisionJson: string, original: Card | null): CardContent {
  const content = { ...draft }
  optionalText.forEach((key) => { content[key] = content[key]?.trim() || null })
  content.effectiveOn = content.effectiveOn || null
  if (original?.verifiedAt) content.topic = original.topic
  if (content.cardType === 'CONDITIONAL') {
    content.dontText = null
    content.doText = null
    content.sceneKey = null
    content.decision = JSON.parse(decisionJson) as CardContent['decision']
  } else content.decision = null
  if (content.changeKind === 'OPERATION') content.revisionId = null
  else content.sourceOrg = null
  if (content.audience === 'LIMITED') content.eventTag = null
  return content
}

export function validateCardContent(content: CardContent): string[] {
  const errors: string[] = []
  const names = { title: '카드 제목', ttsText: '음성 안내', legalBasis: '법적 근거', lawExcerpt: '법령 원문' }
  Object.entries(names).forEach(([key, label]) => {
    if (isBlank(content[key as keyof typeof names])) errors.push(`${label}을 입력해 주세요.`)
  })
  Object.entries(CARD_LIMITS).forEach(([key, max]) => {
    const value = content[key as keyof typeof CARD_LIMITS]
    if (typeof value === 'string' && value.length > max) errors.push(`${key}: 최대 ${max.toLocaleString()}자까지 입력할 수 있습니다.`)
  })
  if (content.cardType === 'SHORT' && Boolean(content.dontText?.trim()) !== Boolean(content.doText?.trim())) {
    errors.push('단문 카드의 잘못된 행동과 올바른 행동은 함께 입력하거나 모두 비워 주세요.')
  }
  if (content.cardType === 'CONDITIONAL') {
    if (content.dontText || content.doText || content.sceneKey) errors.push('조건 분기형에서는 행동 비교와 장면 키를 비워 주세요.')
    errors.push(...validateDecision(content.decision))
  } else if (content.decision !== null) errors.push('결정 테이블은 조건 분기형 카드에서만 사용합니다.')
  if (content.changeKind === 'OPERATION') {
    if (!content.sourceOrg?.trim()) errors.push('운영 변경 카드에는 출처 기관이 필요합니다.')
    if (content.revisionId !== null) errors.push('운영 변경 카드는 법령 개정과 연결할 수 없습니다.')
  } else if (content.sourceOrg?.trim()) errors.push('법령 개정 카드는 출처 기관을 사용하지 않습니다.')
  if (content.audience === 'LIMITED' && content.eventTag !== null) errors.push('일부 운전자 대상 카드는 이벤트 태그를 사용할 수 없습니다.')
  return errors
}
