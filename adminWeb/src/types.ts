export type CardStatus = 'DRAFT' | 'IN_REVIEW' | 'PUBLISHED' | 'RETIRED';
export type CardType = 'SHORT' | 'CONDITIONAL' | 'FIXED_RESPONSE';
export type ChangeKind = 'LAW' | 'OPERATION';
export type Audience = 'GENERAL' | 'LIMITED';
export type EventTag = 'SCHOOL_ZONE_ENTRY' | 'PARKING_DESTINATION' | 'PARKING_HIGHWAY' | 'RIGHT_TURN_CROSSWALK';
export type TriageStatus = 'PENDING' | 'APPLICABLE' | 'NOT_APPLICABLE';
export type RequestStatus = 'OPEN' | 'RESOLVED' | 'DISMISSED';
export type ReviewDecision = 'APPROVE' | 'REJECT';
export type ConditionKey = 'right_turn_traffic_light' | 'front_traffic_light' | 'crosswalk_light' | 'pedestrian_present';
export interface DecisionTable { schema: 2; tables: { key: string; title: string; conditions: ConditionKey[]; rows: { when?: Partial<Record<ConditionKey, string>> | null; text: string; tts: string; scene?: string | null }[] }[] }
export interface CardContent {
  revisionId: number | null; cardType: CardType; changeKind: ChangeKind; audience: Audience;
  eventTag: EventTag | null; sourceOrg: string | null; topic: string | null; title: string;
  dontText: string | null; doText: string | null; sceneKey: string | null; ttsText: string;
  legalBasis: string; lawExcerpt: string; penaltyText: string | null; effectiveOn: string | null;
  exceptions: string | null; decision: DecisionTable | null;
}
export interface Card extends CardContent { id: number; status: CardStatus; version: number; verifiedAt: string | null; createdAt: string; updatedAt: string }
export interface Review { id: number; reviewerId: number; cardVersion: number; decision: ReviewDecision; reviewedAt: string }
export interface Reviewer { id: number; name: string }
export interface LawRevision { id: number; lawName: string; lawNo: string; sourceKey: string; promulgatedOn: string; triageStatus: TriageStatus; sourceUrl: string | null; createdAt?: string }
export interface LawContent { lawName: string; lawNo: string; sourceKey: string; promulgatedOn: string }
export interface ReviewRequest { id: number; requestType: 'ERROR_REPORT' | 'ANSWER_HOLD'; cardId: number | null; cardVersion: number | null; topic: string | null; conditions: Record<string, string> | null; status: RequestStatus; createdAt: string }
export interface LawSyncResult { from: string; to: string; laws: { lawId: string; outcome: 'OK' | 'FAILED'; lawName: string | null; created: string[]; existing: number; error: string | null }[] }
export interface AppSync { cards: { cardId: number; version: number; title: string; topic: string | null }[]; [key: string]: unknown }
export const statusLabels: Record<CardStatus, string> = { DRAFT: '작성 중', IN_REVIEW: '검수 대기', PUBLISHED: '게시 중', RETIRED: '회수됨' };
export const typeLabels: Record<CardType, string> = { SHORT: '단문', CONDITIONAL: '조건 분기', FIXED_RESPONSE: '고정 응답' };
export const triageLabels: Record<TriageStatus, string> = { PENDING: '분류 대기', APPLICABLE: '운전자 해당', NOT_APPLICABLE: '해당 없음' };
export const requestLabels: Record<RequestStatus, string> = { OPEN: '처리 대기', RESOLVED: '처리 완료', DISMISSED: '종결' };
export const topics: Record<string, string> = { right_turn: '우회전', left_turn: '좌회전', school_zone_crosswalk: '보호구역 횡단보도', school_zone_parking: '보호구역 주정차', highway_stopping: '고속도로 정차', speeding: '과속', safe_distance: '안전거리', turn_signal: '방향지시등', seatbelt: '안전띠', drunk_driving_aid: '음주운전 방조', breath_test_obstruction: '음주측정 방해', drug_driving: '약물운전', ignition_interlock_license: '음주운전 방지장치', license_renewal: '면허 갱신', other: '기타' };
export const conditionLabels: Record<ConditionKey, string> = { right_turn_traffic_light: '우회전 신호등', front_traffic_light: '전방 차량 신호', crosswalk_light: '횡단보도 보행 신호', pedestrian_present: '횡단 중·대기 보행자' };
