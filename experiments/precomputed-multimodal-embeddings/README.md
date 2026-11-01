# Precomputed multimodal embedding experiment

## Problem and hypothesis

Product titles often omit visual attributes that are visible in images. The hypothesis is that externally generated multimodal embeddings, stored with stable product IDs and searched with a query vector from the same embedding space, improve top-K relevance for visual-attribute queries without regenerating vectors inside the vector store.

This PoC is a dependency-free Java 17+ offline harness for the `VectorStore.upsert(List<EmbeddedDocument>)` and `similaritySearch(float[], SearchRequest)` contract introduced in Spring AI 2.1. It uses deterministic synthetic vectors rather than a hosted embedding model, so it validates indexing, retry, compatibility guards, ranking, and evaluation logic—not model quality.

## Baseline and treatment

- Baseline: lexical token overlap over product title and description.
- Treatment: application-supplied vectors containing text and synthetic image attributes, cosine similarity, top 3.
- Shared constraints: in-stock filter, stable SKU-derived IDs, same three-dimensional embedding space.
- Reliability checks: repeated upsert must not create duplicates; mismatched dimensions and non-finite vectors must be rejected; query/model version mismatch must fail closed.

## Sources

- [Using Your Own Embeddings in Spring AI 2.1](https://spring.io/blog/2026/10/28/spring-ai-upsert-own-embeddings/)
- [Spring AI reference: writing your own embeddings](https://docs.spring.io/spring-ai/reference/api/vectordbs.html)
- [Java Weekly 669](https://www.baeldung.com/java-weekly-669)

Spring AI 2.1.0-RC1 documents support for supplied-vector upsert and query-vector search in PGvector, Redis, Elasticsearch, Qdrant, and OpenSearch. This harness intentionally has no Spring AI dependency or credentials. A production spike should use the target vector store implementation and pin the Spring AI BOM used by the service.

## Run

```bash
cd experiments/precomputed-multimodal-embeddings
bash run.sh
```

The program loads the synthetic CSV fixtures, prints measured metrics, and exits non-zero when an acceptance criterion fails.

## Metrics and acceptance criteria

- `recall@3`: fraction of labeled relevant products retrieved in the first three results; treatment must exceed baseline.
- `nDCG@3`: graded ranking utility; treatment must exceed baseline.
- `zero_result_rate`: queries returning no in-stock results; treatment must not exceed baseline.
- `duplicate_count_after_retry`: must remain zero after replaying the same upsert batch.
- `dimension_guard`, `finite_guard`, `model_version_guard`: all must pass.
- `elapsed_us`: local harness time only; it is not an OpenSearch/Elasticsearch or network latency benchmark.

## Confirmed result

The deterministic fixture demonstrates the expected direction for queries whose visual attributes are absent from text. Exact values are emitted by `run.sh`. These are offline synthetic ranking results and must not be interpreted as CTR or CVR improvement.

## Limits and follow-up

- Synthetic vectors test the integration contract, not the accuracy of a real multimodal model.
- Re-run with a labeled product-image/query golden set and compare text-only, image-only, fused, and hybrid lexical+vector ranking.
- Measure indexing throughput, failed-batch retry behavior, index size, embedding cost, and P50/P95/P99 query latency on the target Elasticsearch/OpenSearch version.
- Store model name, version, dimensions, and input mode in metadata. Reject or route incompatible queries; equal dimensions do not imply compatible embedding spaces.
- Use small idempotent batches because a single `upsert` call is not guaranteed to be transactional.

