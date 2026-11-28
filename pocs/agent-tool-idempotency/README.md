# 에이전트 도구 호출 멱등성 PoC

## 문제와 가설

에이전트 런타임, 큐, 네트워크 타임아웃은 같은 논리적 쓰기 호출을 재전달할 수 있습니다. 기준선은 모든 시도를 실행합니다. 실험군은 논리 연산 ID, 계획 단계, 도구, 인자 해시로 만든 멱등성 키를 원자적으로 선점하고 완료 결과를 재사용합니다.

가설: 동일한 20개 논리 연산을 각 3회 동시에 전달할 때, 원자적 선점과 결과 캐시는 부작용을 논리 연산당 1회로 제한하고 모든 재시도에 동일한 결과를 반환합니다.

## 기준선과 실험군

| 구분 | 구현 | 기대 |
|---|---|---|
| 기준선 | 매 시도마다 도구 실행 | 60회 부작용, 결과 불일치 |
| 실험군 | ConcurrentHashMap.putIfAbsent로 선점하고 CompletableFuture 결과 공유 | 20회 부작용, 결과 100% 일치 |

실서비스에서는 메모리 맵 대신 조건부 쓰기, TTL, 선점 임대와 장애 복구를 제공하는 영속 저장소가 필요합니다.

## 출처

- [Your Agent's Tool Calls Will Run Twice](https://foojay.io/today/agent-tool-calls-run-twice/)
- [Human in the Loop with Spring AI 2.1](https://spring.io/blog/2026/11/24/spring-ai-tool-call-confirmation-park-resume/)
- [Java Weekly, Issue 673](https://www.baeldung.com/java-weekly-673)

## 의존성과 실행

- Java 21 이상
- 외부 라이브러리, 모델 API, 자격 증명 없음

    cd pocs/agent-tool-idempotency
    ./run.sh
    ./verify.sh

합성 입력은 data/logical-operations.csv, 최신 로컬 측정값은 results/latest.json에 기록됩니다.

## 평가 지표

- 중복 부작용률: (실제 부작용 수 - 논리 연산 수) / 논리 연산 수
- 결과 일치율: 한 논리 연산의 모든 재시도가 같은 영수증을 받은 비율
- 재시도 관점 P95/P99 latency: 선점 승자와 대기자의 로컬 경과시간

검색 적합도, 랭킹, mAP@k, 무결과율, CTR/CVR은 이 신뢰성 가설의 대상이 아니므로 측정하지 않습니다.

## 확인한 결과

2026-11-28에 Java 21 소스 파일 모드로 실행했습니다. 20개 논리 연산과 60개 동시 시도에서 기준선은 부작용 60회(중복 부작용률 2.0), 결과 일치율 0.0을 기록했습니다. 실험군은 부작용 20회(중복 부작용률 0.0), 결과 일치율 1.0을 기록했습니다. P95/P99는 실행 환경에 민감한 로컬 참고값이며 운영 SLA로 해석하지 않습니다. 정확한 값은 커밋된 results/latest.json을 확인하십시오.

## 한계와 후속 실험

- 프로세스 재시작과 분산 노드 간 경쟁은 재현하지 않았습니다.
- 선점자가 결과 저장 전에 죽는 경우를 위한 임대 만료·인수와 TTL이 없습니다.
- 다음 단계는 PostgreSQL INSERT ON CONFLICT, Redis SET NX, 또는 DynamoDB 조건부 쓰기로 동일 실험을 반복하는 것입니다.
- timeout/fallback이 실제로 중복을 유발하는 장애 주입, 저장소 오류, 재시도 폭풍에서 P95/P99와 비용을 별도로 측정해야 합니다.
