-- DRIVE RULE 서버 DB 최초 스키마
-- 기준: docs/db/erd.data.mjs (서버 구역) · 결정 002·004·005·006·009
-- ENUM 값은 Java enum 이 기준이다. 값을 바꾸면 새 마이그레이션으로 이 정의도 함께 바꾼다.

CREATE TABLE law_revision (
    id             BIGINT       NOT NULL AUTO_INCREMENT,
    law_name       VARCHAR(100) NOT NULL,
    law_no         VARCHAR(30)  NOT NULL,
    source_key     VARCHAR(40)  NOT NULL,
    promulgated_on DATE         NOT NULL,
    triage_status  ENUM ('PENDING', 'APPLICABLE', 'NOT_APPLICABLE') NOT NULL,
    created_at     DATETIME(6)  NOT NULL,
    PRIMARY KEY (id),
    CONSTRAINT uq_law_revision_law_no UNIQUE (law_no)
);

CREATE TABLE reviewer (
    id   BIGINT      NOT NULL AUTO_INCREMENT,
    name VARCHAR(50) NOT NULL,
    PRIMARY KEY (id)
);

CREATE TABLE rule_card (
    id            BIGINT       NOT NULL AUTO_INCREMENT,
    revision_id   BIGINT       NULL,
    card_type     ENUM ('SHORT', 'CONDITIONAL', 'FIXED_RESPONSE') NOT NULL,
    -- 카드 종류 · 대상 · 이벤트 · 출처 (결정 009 A · C · H)
    change_kind   ENUM ('LAW', 'OPERATION') NOT NULL,
    audience      ENUM ('GENERAL', 'LIMITED') NOT NULL,
    event_tag     ENUM ('SCHOOL_ZONE_ENTRY', 'PARKING_DESTINATION', 'PARKING_HIGHWAY', 'RIGHT_TURN_CROSSWALK') NULL,
    source_org    VARCHAR(100) NULL,
    topic         VARCHAR(40)  NULL,
    title         VARCHAR(100) NOT NULL,
    dont_text     VARCHAR(300) NULL,
    do_text       VARCHAR(300) NULL,
    scene_key     VARCHAR(40)  NULL,
    tts_text      VARCHAR(500) NOT NULL,
    legal_basis   VARCHAR(200) NOT NULL,
    law_excerpt   TEXT         NOT NULL,
    penalty_text  VARCHAR(300) NULL,
    effective_on  DATE         NULL,
    exceptions    TEXT         NULL,
    decision_json JSON         NULL,
    status        ENUM ('DRAFT', 'IN_REVIEW', 'PUBLISHED', 'RETIRED') NOT NULL,
    version       INT          NOT NULL,
    verified_at   DATETIME(6)  NULL,
    created_at    DATETIME(6)  NOT NULL,
    updated_at    DATETIME(6)  NOT NULL,
    PRIMARY KEY (id),
    -- topic 은 Gleo 대표 카드에만 붙는다 (006). NULL 은 여러 행 가능
    CONSTRAINT uq_rule_card_topic UNIQUE (topic),
    CONSTRAINT fk_rule_card_revision FOREIGN KEY (revision_id) REFERENCES law_revision (id)
);

-- 동기화(since)는 status + updated_at 으로 거른다
CREATE INDEX ix_rule_card_status_updated ON rule_card (status, updated_at);

CREATE TABLE card_review (
    id           BIGINT      NOT NULL AUTO_INCREMENT,
    card_id      BIGINT      NOT NULL,
    reviewer_id  BIGINT      NOT NULL,
    card_version INT         NOT NULL,
    decision     ENUM ('APPROVE', 'REJECT') NOT NULL,
    reviewed_at  DATETIME(6) NOT NULL,
    PRIMARY KEY (id),
    CONSTRAINT uq_card_review UNIQUE (card_id, card_version, reviewer_id),
    CONSTRAINT fk_card_review_card FOREIGN KEY (card_id) REFERENCES rule_card (id),
    CONSTRAINT fk_card_review_reviewer FOREIGN KEY (reviewer_id) REFERENCES reviewer (id)
);

CREATE TABLE review_request (
    id            BIGINT       NOT NULL AUTO_INCREMENT,
    card_id       BIGINT       NULL,
    card_version  INT          NULL,
    request_type  ENUM ('ERROR_REPORT', 'ANSWER_HOLD') NOT NULL,
    -- 답변 보류(009 E): 질문 원문 대신 topic 과 공통 조건 키·값만
    topic         VARCHAR(40)  NULL,
    conditions_json JSON       NULL,
    status        ENUM ('OPEN', 'RESOLVED', 'DISMISSED') NOT NULL,
    created_at    DATETIME(6)  NOT NULL,
    PRIMARY KEY (id),
    CONSTRAINT fk_review_request_card FOREIGN KEY (card_id) REFERENCES rule_card (id)
);

CREATE INDEX ix_review_request_status ON review_request (status, created_at);
