-- ERD taxonomy v1. Definitions describe the service categories, not Naver codes.
INSERT INTO place_categories (id, code, label, definition, sort_order, taxonomy_version) VALUES
    (1, 'CAFE', '카페', '커피·차 등 음료를 중심으로 머무르는 공간', 1, 1),
    (2, 'RESTAURANT', '음식점', '식사를 중심으로 방문하는 공간', 2, 1),
    (3, 'BAR', '술집', '술을 마시며 머무르는 공간', 3, 1),
    (4, 'PARK_WALK', '공원·산책', '공원이나 산책을 위해 방문하는 공간', 4, 1),
    (5, 'CULTURE', '문화', '전시·공연 등 문화 경험을 위한 공간', 5, 1),
    (6, 'STUDY_WORK', '공부·작업 공간', '공부 또는 작업을 위해 머무르는 공간', 6, 1),
    (7, 'SHOPPING', '쇼핑', '물건을 살펴보거나 구매하는 공간', 7, 1),
    (8, 'OTHER', '기타', '다른 일곱 유형에 해당하지 않는 경험의 공간', 8, 1);
