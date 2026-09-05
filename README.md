# ai-search-quality

검색 입력과 결과를 분석해 품질 문제 및 후속 액션 후보를 생성하는 실험 저장소입니다.

## 목표

- Query Quality, Result Relevance, Term Understanding, Boosting 진단을 분리합니다.
- Orchestrator가 공통 진단 결과 스키마로 후보를 취합합니다.
- Spring AI 메모리와 Agent2Agent(A2A) 구조의 적용 가능성을 비교합니다.
- LLM은 후보 생성과 라벨링만 보조하며 최종 판단은 골든셋, 규칙, 지표, 사람 검토로 수행합니다.

## Phase 0

1. 비식별 대표 질의 35~50개 준비
2. 공통 진단 결과 스키마 확정
3. 단일 오케스트레이션 기준선 측정
4. 메모리 적용 전후 비교
5. A2A 분리 구조 비교

## 구조

```text
docs/          문제 정의, 아키텍처, 구현 계획
golden-set/    비식별 대표 질의와 기대 결과
schemas/       진단 결과 JSON Schema
evaluations/   오프라인·운영 평가 규칙
experiments/   실험 계획과 결과
decisions/     Architecture Decision Records
```

## 보안 원칙

- 실제 고객 원문, 개인정보, 운영 자격정보를 커밋하지 않습니다.
- 업무 데이터는 비식별 샘플 또는 합성 데이터로 대체합니다.
- 비밀값은 환경변수나 승인된 비밀 저장소로만 전달합니다.

## 시작하기

현재 저장소는 설계·평가 규격을 먼저 확정하는 Phase 0 상태입니다. `golden-set/sample.jsonl`을 복사해 대표 질의를 추가하고, `schemas/diagnosis-result.schema.json`에 맞춰 진단 출력을 생성합니다.
