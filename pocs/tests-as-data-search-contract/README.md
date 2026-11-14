# Tests-as-data 검색 계약 PoC

## 문제와 가설

AI 에이전트가 운영 코드와 테스트 코드를 함께 변경하면, 동일한 오해가 양쪽에 반영되어 테스트가 통과해도 기능 오류가 남을 수 있습니다.

가설은 **검색식의 입력과 기대 AST를 독립 TSV 계약으로 관리하면**, Java 코드에 박힌 소수의 단위 테스트보다 검색 연산자·구문 회귀를 더 잘 검출하면서도 검토 가능한 diff를 유지할 수 있다는 것입니다.

## 기준선과 실험군

- 기준선: Java 소스 안에 정의한 3개의 테스트 케이스
- 실험군: `contracts/search-contract.tsv`의 10개 블랙박스 계약 케이스
- 합성 결함: `NOT` 제거, `AND/OR` 교환, phrase 분리, wildcard 제거
- 품질 지표: mutation kill rate
- 운영 지표: 정상 파서 호출 2,000회의 단일 프로세스 P95 실행 시간

이 PoC의 파서는 Elasticsearch 쿼리 파서가 아니라 계약 검증 구조를 비교하기 위한 최소 합성 구현입니다.

## 출처

- Matteo Vaccari, [Tests as data](https://www.martinfowler.com/articles/tests-as-data.html), 2026-11-11
- Java Weekly 671, `Issue 671 of Java Weekly – Wins in the pom.xml`, 2026-11-14 수신

## 요구 사항

- Java 21 이상
- 외부 라이브러리와 API 키 없음

## 실행

```bash
cd pocs/tests-as-data-search-contract
sh run.sh
```

또는 다음처럼 Java source-file mode로 실행합니다.

```bash
java SearchContractExperiment.java contracts/search-contract.tsv
```

## 평가 방법

각 테스트 집합을 네 가지 합성 결함 구현에 실행합니다. 하나 이상의 계약 위반을 검출하면 해당 mutation을 kill한 것으로 집계합니다. 이어서 정상 구현을 2,000회 실행해 P95를 측정합니다. 절대 지연은 JVM·하드웨어·워밍업에 민감하므로 두 방식의 운영 부담을 대략 확인하는 보조 지표로만 사용합니다.

## 확인한 결과

OpenJDK 21.0.13 source-file mode에서 실행했습니다.

| 구분 | 케이스 | 검출 mutation | kill rate | P95 |
|---|---:|---:|---:|---:|
| 코드 내 기준선 | 3 | 2/4 | 0.500 | 10.025µs |
| 데이터 계약 | 10 | 4/4 | 1.000 | 4.387µs |

계약 파일 SHA-256은 `a7c769e2de72e330e5a7c3ed5d7de4b847fc2ad90dd5ab838c67fc9ad0f23d60`입니다. P95 차이는 이 짧은 단일 실행의 참고값이며 성능 우위를 일반화할 수 없습니다. `sh run.sh`는 실험군이 네 결함을 모두 검출하지 못하면 실패합니다.

## 한계와 후속 실험

- 합성 파서와 합성 결함이므로 실제 검색 정확도, mAP@k, 무결과율, CTR/CVR 개선을 뜻하지 않습니다.
- 실제 Lexer/Parser/DSL 변환 계층의 골든셋으로 교체하고 PIT 또는 Stryker4j 같은 mutation 도구와 결합해야 합니다.
- 계약 파일 변경을 별도 CODEOWNERS 승인 대상으로 두고, 일반 리팩터링 PR에서는 SHA-256 또는 변경 경로 정책으로 차단하는 CI 검증이 필요합니다.
- Elasticsearch 통합 시 결과 정합성뿐 아니라 P95/P99, timeout, 실패 시 fallback 동작을 별도 측정해야 합니다.
