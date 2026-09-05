import json
import unittest
from pathlib import Path

from search_quality.evaluate import evaluate, load_jsonl
from search_quality.runners import A2ARunner, SingleProcessRunner


DATASET = Path("golden-set/representative-queries.jsonl")


class ExperimentTest(unittest.TestCase):
    def test_dataset_has_40_unique_synthetic_queries(self):
        rows = load_jsonl(DATASET)
        self.assertEqual(40, len(rows))
        self.assertEqual(40, len({row["query_id"] for row in rows}))
        self.assertTrue(all(row["privacy"] == "synthetic" for row in rows))

    def test_single_and_a2a_are_quality_equivalent(self):
        rows = load_jsonl(DATASET)
        single = evaluate(SingleProcessRunner(), rows)
        a2a = evaluate(A2ARunner(), rows)
        self.assertEqual(single["reason_code_f1"], a2a["reason_code_f1"])
        self.assertEqual(1.0, a2a["exact_match_rate"])

    def test_a2a_isolates_agent_failure(self):
        item = load_jsonl(DATASET)[0]
        findings, errors = A2ARunner(fault_agent="boosting").run(item)
        self.assertIn("boosting:RuntimeError", errors)
        self.assertIsInstance(findings, list)

    def test_findings_follow_minimum_schema_contract(self):
        item = load_jsonl(DATASET)[0]
        findings, _ = SingleProcessRunner().run(item)
        required = set(json.loads(Path("schemas/diagnosis-result.schema.json").read_text())["required"])
        self.assertTrue(findings)
        self.assertTrue(all(required <= finding.keys() for finding in findings))


if __name__ == "__main__":
    unittest.main()
