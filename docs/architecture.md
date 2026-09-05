# 아키텍처

## 구성 요소

1. **Orchestrator**: 입력을 표준화하고 역할별 진단기를 호출해 결과를 취합합니다.
2. **Query Quality**: 오타, 띄어쓰기, 신조어, 질의 이상을 탐지합니다.
3. **Result Relevance**: 무관 결과, 제로 결과, 카테고리 편향을 진단합니다.
4. **Term Understanding**: 텀 분절, 동의어, 엔티티, 속성어 후보를 생성합니다.
5. **Boosting**: 상품·브랜드·콘텐츠 가중치 조정 후보를 생성합니다.
6. **Policy Engine**: 규칙, 골든셋, 지표, 사람 승인으로 적용 가능성을 판정합니다.

## 비교할 두 구조

### 기준선

하나의 프로세스에서 Orchestrator가 네 진단기를 직접 호출합니다.

### 실험안

각 진단기를 무상태 에이전트로 분리하고 A2A 방식으로 표준 메시지를 교환합니다. 단기 메모리는 재현에 필요한 입력, 근거, 이전 판정만 보존합니다.

## Retrieval 평가 경계

Hybrid Retrieval(BM25 + Vector), Fusion(RRF 또는 weighted), Reranker, Business Ranking, LLM 단계를 분리 평가합니다. 생성 품질이 검색 품질 문제를 가리지 않도록 Retriever, Reranker, Generator 지표를 각각 기록합니다.
