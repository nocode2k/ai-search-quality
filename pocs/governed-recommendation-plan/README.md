# Governed Recommendation Plan PoC

## 문제와 가설

LLM이 검색·추천 실행 계획을 자유롭게 만들면 존재하지 않는 capability 호출, 순환 의존성, 과도한 timeout, 필수 카탈로그 필터 누락이 운영 요청에 유입될 수 있습니다.

가설은 **LLM 제안 계획을 비신뢰 입력으로 보고 코드 기반 검증과 검증된 fallback을 적용하면**, 정상 계획의 수용률을 유지하면서 안전하지 않은 계획의 실행률을 낮출 수 있다는 것입니다.

## 기준선과 실험군

- 기준선: 비어 있지 않은 계획을 그대로 실행 대상으로 수용
- 실험군: capability allowlist, timeout 상한, DAG, 의존성, 단일 종료점, retrieval·catalog filter 필수 조건을 검증
- 실패 처리: 검증 실패 시 미리 검증된 lexical search 계획으로 fallback
- 합성 데이터: 정상 계획 4건, 결함 계획 6건

## 출처

- Netflix Technology Blog, [Engineering AI Discover](https://netflixtechblog.com/engineering-ai-discover-e244944cdc38)
- Java Weekly 672, Issue 672 of Java Weekly – Black Friday is here, 2026-11-22 수신

## 요구 사항

- Java 21 이상
- 외부 라이브러리, 모델 API, 비밀키 없음

## 실행

~~~bash
cd pocs/governed-recommendation-plan
sh run.sh
~~~

## 평가 지표

- Precision: 실행을 허용한 계획 중 정상 계획 비율
- Recall: 정상 계획 중 허용된 비율
- Unsafe acceptance rate: 결함 계획 중 실행이 허용된 비율
- Fallback count: 검증 실패 후 검증된 계획으로 대체된 건수
- P95: 계획 파싱과 검증 5,000회의 단일 프로세스 P95

## 확인한 결과

OpenJDK 21.0.13 source-file mode에서 두 차례 실행했습니다.

| 구분 | Precision | Recall | Unsafe acceptance | Fallback | P95 |
|---|---:|---:|---:|---:|---:|
| 기준선 | 0.400 | 1.000 | 1.000 | 0 | 0.210~0.300µs |
| 검증기 | 1.000 | 1.000 | 0.000 | 6 | 43.806~52.198µs |

검증기는 정상 계획 4건을 모두 수용하고 결함 계획 6건을 모두 차단한 뒤 fallback으로 전환했습니다. P95는 합성 계획 10건을 같은 JVM 프로세스에서 반복한 참고값이며 운영 환경의 절대 성능을 뜻하지 않습니다.

## 한계와 후속 실험

- 합성 계획과 결정론적 검증 결과이므로 실제 검색 적합도, mAP@k, 무결과율, CTR/CVR 개선을 의미하지 않습니다.
- 실제 Spring Boot 서비스에서는 capability별 JSON Schema, 입력·출력 타입, 개인정보 접근 범위와 권한을 추가해야 합니다.
- fallback 결과의 품질은 별도로 Recall@k·nDCG@k와 무결과율을 측정해야 합니다.
- capability별 deadline, 전체 P95/P99, timeout 비율, fallback 성공률과 비용을 운영 지표로 기록해야 합니다.
- LLM planner 자체의 품질 비교는 동일 골든셋에서 모델·프롬프트 버전을 고정한 A/B 평가가 필요합니다.
