-- 공통 조건 키 이름 변경 (결정 012 2번) — Gleo App Schema 파라미터 이름이 되므로 LLM 이 헷갈리지 않는 이름으로
--   turn_light   → right_turn_traffic_light  (영어 turn light 는 방향지시등으로 읽힌다)
--   front_signal → front_traffic_light
--   ped_signal   → crosswalk_light
--   pedestrian   → pedestrian_present
-- 키는 JSON 안에서 항상 따옴표로 둘러싸여 있으므로 따옴표째 바꾼다. 값(yes/no/red/green)은 그대로다.

UPDATE rule_card
SET decision_json = CAST(
        REPLACE(REPLACE(REPLACE(REPLACE(CAST(decision_json AS CHAR),
            '"turn_light"', '"right_turn_traffic_light"'),
            '"front_signal"', '"front_traffic_light"'),
            '"ped_signal"', '"crosswalk_light"'),
            '"pedestrian"', '"pedestrian_present"') AS JSON)
WHERE decision_json IS NOT NULL;

UPDATE review_request
SET conditions_json = CAST(
        REPLACE(REPLACE(REPLACE(REPLACE(CAST(conditions_json AS CHAR),
            '"turn_light"', '"right_turn_traffic_light"'),
            '"front_signal"', '"front_traffic_light"'),
            '"ped_signal"', '"crosswalk_light"'),
            '"pedestrian"', '"pedestrian_present"') AS JSON)
WHERE conditions_json IS NOT NULL;
