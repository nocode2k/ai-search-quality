# Quote-grounded recommendation explanations

## 문제와 가설

LLM이 검색·추천 결과의 이유를 자연스럽게 만들더라도, 상품 정보나 리뷰에 없는 근거를 함께 노출할 수 있습니다. 기준선은 생성된 설명을 그대로 노출합니다. 실험군은 구조화된 `evidenceQuote`가 모델에 전달된 근거 문서에 정확히 존재할 때만 설명을 노출합니다.

가설은 단순한 결정론적 검증만으로도 합성 데이터의 비근거 설명 노출률을 낮추면서 근거 설명의 재현율을 유지할 수 있다는 것입니다.

## 출처

- [No Quote, No Claim: Making an LLM Show Its Sources in Java](https://foojay.io/today/verified-match-explanations-spring-ai/)
- [Java Weekly 670](https://www.baeldung.com/java-weekly-670)

원문의 정규화된 부분 문자열 검증을 재현하되, `Java`가 `JavaScript`에 잘못 매칭되는 문제를 막기 위해 유니코드 문자·숫자 토큰 경계를 적용했습니다.

## 구성과 버전

- Java 17 이상
- 외부 라이브러리 없음
- `GroundingPoc.java`: 검증기, 테스트, 합성 평가, P95 지연 측정
- `data/claims.tsv`: 근거 6건과 비근거 6건의 합성 설명

## 실행

```bash
chmod +x run.sh
./run.sh
```

`java`의 source-file launcher를 사용하므로 별도 빌드 도구가 필요하지 않습니다.

## 평가 지표

- `precision`: 노출된 설명 중 근거 설명 비율
- `supported_recall`: 근거 설명 중 노출된 비율
- `unsupported_exposure_rate`: 비근거 설명 중 사용자에게 노출된 비율
- `validation_p95_us`: 10,000회 검증의 프로세스 내부 P95 지연

## 확인 결과

2026-11-07, OpenJDK 21.0.13 환경에서 실행했습니다.

```text
GroundingPocTest: PASS
mode,precision,supported_recall,unsupported_exposure_rate,accepted
baseline,0.5000,1.0000,1.0000,12
quote-verifier,1.0000,1.0000,0.0000,6
validation_p95_us=14.602
```

결과는 12건의 합성 데이터에 대한 오프라인 검증입니다. 실제 LLM 응답 품질, CTR·CVR 개선 또는 운영 P95/P99를 의미하지 않습니다.

## 운영 적용과 리스크

- 생성 모델은 `explanation`, `evidenceQuote`, `sourceId`를 구조화해 반환하고, 서버는 실제로 모델에 전달한 동일한 근거 텍스트로 검증해야 합니다.
- 전체 설명이 제거되면 근거 없는 대체 문장을 만들지 않고 설명 영역을 숨기는 fail-closed가 안전합니다.
- 정확한 인용은 존재성만 증명하며 설명의 논리적 타당성은 보장하지 않습니다. 다음 단계에서는 사람이 라벨링한 설명 적합성 골든셋으로 claim entailment와 인용 범위를 평가해야 합니다.
- 실제 트래픽 전에는 상품·리뷰 길이별 P95/P99, 제거율, 설명 미노출률, 모델 호출 비용을 측정해야 합니다.
