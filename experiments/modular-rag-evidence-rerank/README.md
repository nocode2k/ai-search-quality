# Modular RAG evidence filter/rerank experiment

## Problem and hypothesis

Vector similarity finds passages about a query, but not necessarily passages that answer it. The hypothesis is that an atomic post-retrieval policy—prompt-injection screening, premise-conflict preservation, relevance filtering, answer-evidence filtering, then reranking—improves answer-evidence quality at top K while preserving a fail-open path.

This PoC is an offline, dependency-free Java 17+ harness. It reproduces the decision contract described for Spring AI `JevDocumentFilter` and `JevDocumentReranker` with deterministic synthetic scores. It **does not** claim to measure the hosted Jev model's accuracy, latency, or cost.

## Baseline and treatment

- Baseline: vector similarity ranking, top 3.
- Treatment: exclude prompt injection (`> 0.70`), retain but tag premise conflicts (`> 0.70`), exclude low relevance (`< 0.45`) and weak answer evidence (`< 0.55`), rerank survivors by answerability, top 3.
- Failure case: simulated Jev timeout uses fail-open vector ranking and records the fallback.

## Source

- [Spring AI Modular RAG and TypeSafe Jev](https://spring.io/blog/2026/10/02/spring-ai-modular-rag-typesafe-jev/)
- [Spring AI RAG documentation](https://docs.spring.io/spring-ai/reference/api/retrieval-augmented-generation.html)
- [Article demo](https://github.com/spring-ai-community/spring-ai-typesafe-examples/tree/main/05-1-modular-rag)

The source article uses `spring-ai-rag` plus `spring-ai-starter-typesafe`/`typesafe-spring-ai` 0.3.0. This harness intentionally uses only Java 17+ so it is repeatable without API credentials. A production spike should replace `SyntheticDecisionClient` with the real `JevDocumentFilter` and `JevDocumentReranker` and pin the Spring AI BOM used by the target service.

## Run

```bash
cd experiments/modular-rag-evidence-rerank
bash run.sh
```

The program prints measured metrics and exits non-zero if the expected safety/quality assertions fail.

## Metrics and acceptance criteria

- `precision@3`: answer-evidence precision in the final context; treatment must exceed baseline.
- `nDCG@3`: graded evidence utility; treatment must exceed baseline.
- `prompt_injection_leakage`: malicious chunks entering final context; treatment must be 0.
- `fallback_preserves_results`: a simulated timeout must return the baseline result set.
- `decision_calls`: number of post-retrieval passage decisions, used as a cost/latency proxy.
- `elapsed_us`: local harness execution only; it is not a network latency benchmark.

## Confirmed result

Verified locally with OpenJDK 17. The six-chunk synthetic case raises precision@3 and nDCG@3, blocks the injected passage, and preserves baseline results on timeout. Exact values are emitted by the program; they are not CTR/CVR claims.

## Limits and follow-up

- Synthetic scores test orchestration and evaluation logic, not model quality.
- Run a real Jev comparison on a labeled, non-sensitive RAG golden set before adoption.
- Measure P50/P95/P99 end-to-end latency, timeout rate, per-query decision count and provider cost.
- Compare filter→rerank against vector-only and rerank-only. Tune thresholds on held-out queries, not the evaluation set.
- The article's fail-open behavior protects availability but can pass unsafe or irrelevant chunks. Production should cap timeouts, log fallback reason, and consider fail-closed injection screening for high-risk corpora.
