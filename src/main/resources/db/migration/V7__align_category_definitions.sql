-- Align only the canonical category-definition text; identifiers, labels, ordering, and taxonomy stay unchanged.
UPDATE place_categories
SET definition = '음료·카페 이용이 중심인 공간'
WHERE id = 1 AND code = 'CAFE';

UPDATE place_categories
SET definition = '식사 제공·식사 이용이 중심인 공간'
WHERE id = 2 AND code = 'RESTAURANT';

UPDATE place_categories
SET definition = '술을 마시는 이용이 중심인 공간'
WHERE id = 3 AND code = 'BAR';

UPDATE place_categories
SET definition = '공원·산책로 등 걷거나 쉬는 야외 공간'
WHERE id = 4 AND code = 'PARK_WALK';

UPDATE place_categories
SET definition = '전시·공연·박물관 등 문화 경험을 위한 공간'
WHERE id = 5 AND code = 'CULTURE';

UPDATE place_categories
SET definition = '공부·작업 용도가 본문에서 드러나는 공간'
WHERE id = 6 AND code = 'STUDY_WORK';

UPDATE place_categories
SET definition = '상품을 둘러보거나 구매하는 매장·시장 등'
WHERE id = 7 AND code = 'SHOPPING';

UPDATE place_categories
SET definition = '장소 유형은 알 수 있으나 다른 일곱 유형에 해당하지 않는 경우'
WHERE id = 8 AND code = 'OTHER';
