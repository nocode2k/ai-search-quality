# PoC reason codes

| 진단기 | reason code | 의미 |
|---|---|---|
| Query Quality | `MALFORMED_QUERY` | 자소·오타 등 비정상 입력 |
| Query Quality | `NORMALIZATION_REQUIRED` | 띄어쓰기·시즌 표현 정규화 필요 |
| Query Quality | `AMBIGUOUS_INTENT` | 의도 확신도가 낮음 |
| Result Relevance | `ZERO_RESULTS` | 검색 결과 없음 |
| Result Relevance | `LOW_RESULT_COUNT` | 유효 후보가 5개 미만 |
| Result Relevance | `LOW_PRECISION_AT_10` | 상위 10개 부적합 비율이 30% 이상 |
| Result Relevance | `NEGATIVE_CONSTRAINT_VIOLATION` | 제외 조건 위반 상품 노출 |
| Result Relevance | `BRAND_RECALL_MISS` | 브랜드 결과 미노출 |
| Term Understanding | `SYNONYM_GAP` | 동의어 확장 누락 |
| Term Understanding | `ENTITY_SPLIT` | 브랜드·복합 엔티티가 잘못 분절됨 |
| Term Understanding | `ATTRIBUTE_RECALL_MISS` | 필수 속성 후보 누락 |
| Boosting | `EXACT_MATCH_DEMOTED` | 정확일치 상품이 3위 밖으로 밀림 |
| Boosting | `POPULARITY_OVERBOOST` | 인기도가 관련성보다 과도하게 작동 |

> 표에는 13개 코드가 포함됩니다. 초기 계획의 11개보다 실제 사례 분리를 위해 `BRAND_RECALL_MISS`, `ATTRIBUTE_RECALL_MISS`를 추가했습니다.
