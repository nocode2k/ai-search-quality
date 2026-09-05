# 단일 프로세스와 A2A 구조 비교 — 1차 PoC

## 결론

동일 규칙과 40개 합성 질의에서는 두 구조의 진단 품질이 동일했습니다. A2A 구조는 에이전트 하나가 실패해도 다른 세 역할의 결과를 반환했지만, in-process 구현에서도 메시지·비동기 처리 오버헤드가 확인됐습니다. 따라서 현 단계에서 A2A의 품질 우위는 입증되지 않았고, 독립 배포·장애 격리가 필요한 경우에만 다음 검증으로 진행합니다.

## AS-IS

- 네 진단기를 한 프로세스에서 순차 호출
- 장애 지점과 배포 단위가 Orchestrator에 결합
- 네트워크 직렬화 비용 없음

## 비교 조건

- 데이터: `golden-set/representative-queries.jsonl`의 합성 질의 40개
- 진단기: Query Quality, Result Relevance, Term Understanding, Boosting
- 품질: reason-code precision/recall/F1, 질의 단위 완전 일치율
- 운영: P95/P99 latency, degraded/fully-failed query rate, agent-call failure rate
- 장애 실험: 모든 질의의 Boosting 호출에 의도적 예외 주입

## 정상 시나리오 결과

| 구조 | Precision | Recall | F1 | 완전 일치율 | Fully failed | P95 |
|---|---:|---:|---:|---:|---:|---:|
| 단일 | 1.0000 | 1.0000 | 1.0000 | 1.0000 | 0.0000 | 실행 결과 JSON 참조 |
| A2A | 1.0000 | 1.0000 | 1.0000 | 1.0000 | 0.0000 | 실행 결과 JSON 참조 |

규칙에서 합성한 정답을 같은 규칙으로 평가했기 때문에 1.0은 모델 성능이 아니라 하네스의 정합성 확인 값입니다.

## Boosting 장애 시나리오

- A2A agent-call failure rate: 25%(4개 역할 중 1개)
- degraded query rate: 100%(모든 질의에서 Boosting 실패)
- fully-failed query rate: 0%(나머지 세 역할 결과 반환)
- F1: 0.9062, Recall: 0.8286

## 권장안

현재 운영 후보는 단일 구조로 유지합니다. 다음 단계는 실제 검색 결과와 사람 라벨을 연결한 200개 이상 골든셋으로 진단 정확도를 다시 측정하는 것입니다. A2A 전환은 역할별 독립 배포, 서로 다른 모델·SLA, 장애 격리의 필요성이 단일 구조의 복잡도보다 커질 때 결정합니다.

## 리스크

- 합성 signal과 같은 규칙으로 정답을 만들었으므로 실제 검색 품질을 대표하지 않음
- in-process A2A라서 네트워크, 인증, 직렬화, 재시도 비용을 포함하지 않음
- LLM을 호출하지 않아 환각률과 토큰 비용을 측정하지 않음
- 현재 latency는 마이크로벤치마크이므로 운영 P95/P99로 해석할 수 없음
