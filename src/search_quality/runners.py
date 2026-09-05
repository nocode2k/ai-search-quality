from __future__ import annotations

import asyncio
import time
from dataclasses import asdict
from typing import Any

from .diagnosers import DIAGNOSERS


def _result(item: dict[str, Any], diagnoser: str, elapsed_ms: float) -> list[dict[str, Any]]:
    return [
        {
            "query_id": item["query_id"],
            "diagnoser": diagnoser,
            "reason_code": finding.reason_code,
            "evidence": [finding.evidence],
            "confidence": finding.confidence,
            "suggested_action": finding.suggested_action,
            "requires_human_review": True,
            "metrics": {"diagnoser_latency_ms": round(elapsed_ms, 3)},
            "metadata": {"architecture": "rule_baseline_v1"},
        }
        for finding in DIAGNOSERS[diagnoser](item)
    ]


class SingleProcessRunner:
    name = "single"

    def run(self, item: dict[str, Any]) -> tuple[list[dict[str, Any]], list[str]]:
        outputs: list[dict[str, Any]] = []
        for name in DIAGNOSERS:
            started = time.perf_counter()
            outputs.extend(_result(item, name, (time.perf_counter() - started) * 1000))
        return outputs, []


class A2ARunner:
    """In-process A2A envelope/timeout/failure-isolation reference implementation."""

    name = "a2a"

    def __init__(self, timeout_ms: float = 100.0, fault_agent: str | None = None):
        self.timeout_ms = timeout_ms
        self.fault_agent = fault_agent

    async def _send(self, item: dict[str, Any], receiver: str) -> dict[str, Any]:
        request = {"protocol":"a2a-poc/1.0", "message_id":f"{item['query_id']}:{receiver}", "receiver":receiver, "payload":item}
        await asyncio.sleep(0)
        if receiver == self.fault_agent:
            raise RuntimeError("injected agent failure")
        started = time.perf_counter()
        findings = _result(request["payload"], receiver, (time.perf_counter() - started) * 1000)
        return {"message_id":request["message_id"], "status":"completed", "findings":findings}

    async def run_async(self, item: dict[str, Any]) -> tuple[list[dict[str, Any]], list[str]]:
        names = list(DIAGNOSERS)
        tasks = [asyncio.create_task(asyncio.wait_for(self._send(item, name), self.timeout_ms / 1000)) for name in names]
        responses = await asyncio.gather(*tasks, return_exceptions=True)
        outputs: list[dict[str, Any]] = []
        errors: list[str] = []
        for name, response in zip(names, responses):
            if isinstance(response, BaseException):
                errors.append(f"{name}:{type(response).__name__}")
            else:
                for finding in response["findings"]:
                    finding["metadata"] = {"architecture":"a2a_in_process_v1", "message_id":response["message_id"]}
                    outputs.append(finding)
        return outputs, errors

    def run(self, item: dict[str, Any]) -> tuple[list[dict[str, Any]], list[str]]:
        return asyncio.run(self.run_async(item))
