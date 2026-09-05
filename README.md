# ai-search-quality

검색 입력과 결과를 분석해 품질 문제 및 후속 액션 후보를 생성하는 실험 저장소입니다.

## 목표

- Query Quality, Result Relevance, Term Understanding, Boosting 진단을 분리합니다.
- Orchestrator가 공통 진단 결과 스키마로 후보를 취합합니다.
- Spring AI 메모리와 Agent2Agent(A2A) 구조의 적용 가능성을 비교합니다.
- LLM은 후보 생성과 라벨링만 보조하며 최종 판단은 골든셋, 규칙, 지표, 사람 검토로 수행합니다.

## 현재 구현 범위

1. 40개 비식별 합성 대표 질의
2. 공통 진단 결과 스키마와 13개 reason code
3. 규칙 기반 단일 프로세스 기준선
4. A2A 메시지 envelope, 병렬 호출, timeout, 실패 격리
5. 정상·Boosting 에이전트 장애 비교 리포트

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

## 실행

```bash
PYTHONPATH=src python -m unittest discover -s tests -v
PYTHONPATH=src python -m search_quality.evaluate
PYTHONPATH=src python -m search_quality.evaluate \
  --fault-agent boosting \
  --output experiments/results/a2a-boosting-failure.json
```

결과 해석은 `experiments/a2a-comparison.md`를 참고합니다. 현재 A2A는 외부 네트워크나 Spring AI를 사용하지 않는 in-process 참조 구현이므로, 측정 latency를 운영 예상치로 사용하면 안 됩니다.
