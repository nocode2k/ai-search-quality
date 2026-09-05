from __future__ import annotations

import argparse
import json
import statistics
import time
from pathlib import Path
from typing import Any

from .runners import A2ARunner, SingleProcessRunner


def percentile(values: list[float], p: float) -> float:
    ordered = sorted(values)
    index = min(len(ordered) - 1, max(0, int((len(ordered) - 1) * p)))
    return ordered[index]


def load_jsonl(path: Path) -> list[dict[str, Any]]:
    with path.open(encoding="utf-8") as stream:
        return [json.loads(line) for line in stream if line.strip()]


def evaluate(runner: Any, dataset: list[dict[str, Any]]) -> dict[str, Any]:
    tp = fp = fn = exact = degraded = fully_failed = failed_calls = 0
    latencies: list[float] = []
    rows: list[dict[str, Any]] = []
    for item in dataset:
        started = time.perf_counter()
        findings, errors = runner.run(item)
        elapsed = (time.perf_counter() - started) * 1000
        latencies.append(elapsed)
        expected = set(item["expected_reason_codes"])
        predicted = {entry["reason_code"] for entry in findings}
        tp += len(expected & predicted)
        fp += len(predicted - expected)
        fn += len(expected - predicted)
        exact += expected == predicted
        degraded += bool(errors)
        fully_failed += len(errors) == 4
        failed_calls += len(errors)
        rows.append({"query_id":item["query_id"], "expected":sorted(expected), "predicted":sorted(predicted), "errors":errors, "latency_ms":round(elapsed,3)})
    precision = tp / (tp + fp) if tp + fp else 1.0
    recall = tp / (tp + fn) if tp + fn else 1.0
    f1 = 2 * precision * recall / (precision + recall) if precision + recall else 0.0
    return {
        "architecture":runner.name,
        "query_count":len(dataset),
        "reason_code_precision":round(precision,4),
        "reason_code_recall":round(recall,4),
        "reason_code_f1":round(f1,4),
        "exact_match_rate":round(exact / len(dataset),4),
        "degraded_query_rate":round(degraded / len(dataset),4),
        "fully_failed_query_rate":round(fully_failed / len(dataset),4),
        "agent_call_failure_rate":round(failed_calls / (len(dataset) * 4),4),
        "latency_ms":{"mean":round(statistics.mean(latencies),3),"p95":round(percentile(latencies,0.95),3),"p99":round(percentile(latencies,0.99),3)},
        "rows":rows,
    }


def main() -> None:
    parser = argparse.ArgumentParser()
    parser.add_argument("--dataset", type=Path, default=Path("golden-set/representative-queries.jsonl"))
    parser.add_argument("--output", type=Path, default=Path("experiments/results/comparison.json"))
    parser.add_argument("--fault-agent", choices=["query_quality","result_relevance","term_understanding","boosting"])
    args = parser.parse_args()
    dataset = load_jsonl(args.dataset)
    results = {
        "experiment":"single-vs-a2a-v1",
        "limitations":["synthetic signals", "in-process A2A transport", "no LLM/token cost"],
        "results":[evaluate(SingleProcessRunner(), dataset), evaluate(A2ARunner(fault_agent=args.fault_agent), dataset)],
    }
    args.output.parent.mkdir(parents=True, exist_ok=True)
    args.output.write_text(json.dumps(results, ensure_ascii=False, indent=2) + "\n", encoding="utf-8")
    summary = [{key: value for key, value in result.items() if key != "rows"} for result in results["results"]]
    print(json.dumps({"output": str(args.output), "summary": summary}, ensure_ascii=False, indent=2))


if __name__ == "__main__":
    main()
