import { describe, expect, it } from 'vitest'
import type { Card, CardContent } from './types'
import { DECISION_EXAMPLE, newCardContent, prepareCardContent, validateCardContent, validateDecision } from './card-validation'

function validContent(): CardContent {
  return { ...newCardContent(null), title: '보행자 보호', ttsText: '보행자를 확인하세요.', legalBasis: '도로교통법 제27조', lawExcerpt: '근거 원문' }
}

const wildcard = (when: unknown = null) => ({ schema: 2, tables: [{ key: 'all', title: '보행자 확인', conditions: ['pedestrian_present'], rows: [{ when, text: '확인', tts: '확인', scene: 'retained_scene' }] }] })

describe('decision table server contract', () => {
  it('accepts current three-color options, including yellow and green_flashing', () => {
    expect(validateDecision(DECISION_EXAMPLE)).toEqual([])
    expect(validateDecision({ schema: 2, tables: [{ key: 'crosswalk', title: '횡단보도', conditions: ['crosswalk_light'], rows: ['green', 'green_flashing', 'red'].map((option) => ({ when: { crosswalk_light: option }, text: option, tts: option })) }] })).toEqual([])
  })

  it('finds missing yellow coverage in a formerly two-color table', () => {
    const table = structuredClone(DECISION_EXAMPLE)
    table.tables[0].rows = table.tables[0].rows.filter((row) => row.when.front_traffic_light !== 'yellow')
    expect(validateDecision(table)).toEqual([expect.stringContaining('front_traffic_light=yellow')])
  })

  it('accepts null, empty and omitted when as wildcards', () => {
    expect(validateDecision(wildcard(null))).toEqual([])
    expect(validateDecision(wildcard({}))).toEqual([])
    const omitted = wildcard(null)
    delete (omitted.tables[0].rows[0] as { when?: unknown }).when
    expect(validateDecision(omitted)).toEqual([])
  })

  it('rejects overlapping wildcard and specific rows', () => {
    const table = wildcard({})
    table.tables[0].rows.push({ when: { pedestrian_present: 'yes' }, text: '특정', tts: '특정', scene: 'x' })
    expect(validateDecision(table)).toEqual([expect.stringContaining('2개 행이 겹치는')])
  })

  it('rejects null option values and foreign conditions without adding coverage noise', () => {
    const errors = validateDecision(wildcard({ pedestrian_present: null, weather: 'rain' }))
    expect(errors).toHaveLength(2)
    expect(errors.join(' ')).toContain('없는 선택지')
    expect(errors.join(' ')).toContain('없는 조건')
    expect(errors.join(' ')).not.toContain('조합입니다')
  })

  it('limits actual cartesian combinations, including two three-value conditions', () => {
    const table = wildcard({})
    table.tables[0].conditions = ['front_traffic_light', 'crosswalk_light']
    expect(validateDecision(table)).toEqual([expect.stringContaining('현재 9개')])
  })

  it('rejects duplicate table keys, invalid schema and more than two tables', () => {
    const row = wildcard({}).tables[0]
    const errors = validateDecision({ schema: 1, tables: [row, row, row] })
    expect(errors.join(' ')).toContain('현재 형식은 2')
    expect(errors.join(' ')).toContain('1~2개')
    expect(errors.join(' ')).toContain('중복된 키')
  })

  it('returns validation errors for malformed JSON structures instead of crashing', () => {
    for (const decision of [null, [], { schema: 2 }, { schema: 2, tables: [null] }, { schema: 2, tables: [{ key: 'x', title: 'x', conditions: ['pedestrian_present'], rows: [null] }] }]) {
      expect(validateDecision(decision).length).toBeGreaterThan(0)
    }
  })
})

describe('full card content editing', () => {
  it('keeps all editable content fields and excludes response metadata from PUT', () => {
    const original = { ...validContent(), id: 5, version: 3, status: 'PUBLISHED', verifiedAt: '2026-11-14T00:00:00', createdAt: '2026-11-14T00:00:00', updatedAt: '2026-11-14T00:00:00', topic: 'custom_existing_topic', exceptions: '예외 조항', penaltyText: '벌점 10점', effectiveOn: '2027-01-01', revisionId: 4 } as Card
    const content = prepareCardContent({ ...newCardContent(original), title: '수정한 제목', topic: 'changed' }, '', original)
    expect(content).toEqual({ ...validContent(), title: '수정한 제목', topic: 'custom_existing_topic', exceptions: '예외 조항', penaltyText: '벌점 10점', effectiveOn: '2027-01-01', revisionId: 4 })
    expect(content).not.toHaveProperty('status')
    expect(content).not.toHaveProperty('verifiedAt')
  })

  it('preserves decision scenes and clears only API-forbidden fields without changing the draft', () => {
    const draft = { ...validContent(), cardType: 'CONDITIONAL', dontText: '기존 금지', doText: '기존 권장', sceneKey: 'old_scene', changeKind: 'OPERATION', sourceOrg: '  한국도로공사  ', revisionId: 4, audience: 'LIMITED', eventTag: 'SCHOOL_ZONE_ENTRY' } as CardContent
    const content = prepareCardContent(draft, JSON.stringify(wildcard(null)), null)
    expect(content).toMatchObject({ dontText: null, doText: null, sceneKey: null, sourceOrg: '한국도로공사', revisionId: null, eventTag: null })
    expect(content.decision?.tables[0].rows[0].scene).toBe('retained_scene')
    expect(draft.dontText).toBe('기존 금지')
    expect(draft.revisionId).toBe(4)
    expect(validateCardContent(content)).toEqual([])
  })

  it('validates paired short-card scenes, required content, and field limits', () => {
    expect(validateCardContent(validContent())).toEqual([])
    const errors = validateCardContent({ ...validContent(), title: ' ', dontText: '잘못된 행동', ttsText: 'x'.repeat(501) })
    expect(errors.join(' ')).toContain('카드 제목')
    expect(errors.join(' ')).toContain('함께 입력')
    expect(errors.join(' ')).toContain('500')
  })

  it('requires operation source and permits fixed-response action fields', () => {
    expect(validateCardContent({ ...validContent(), changeKind: 'OPERATION' })).toContain('운영 변경 카드에는 출처 기관이 필요합니다.')
    expect(validateCardContent({ ...validContent(), cardType: 'FIXED_RESPONSE', dontText: '안내' })).toEqual([])
  })

  it('does not discard malformed conditional JSON silently', () => {
    expect(() => prepareCardContent({ ...validContent(), cardType: 'CONDITIONAL' }, '{bad', null)).toThrow()
  })
})
