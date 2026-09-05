from __future__ import annotations

from dataclasses import dataclass
from typing import Any, Callable


@dataclass(frozen=True)
class Finding:
    reason_code: str
    evidence: str
    confidence: float
    suggested_action: str


Diagnoser = Callable[[dict[str, Any]], list[Finding]]


def query_quality(item: dict[str, Any]) -> list[Finding]:
    signals = item["signals"]
    findings: list[Finding] = []
    if signals.get("query_quality"):
        findings.append(Finding("MALFORMED_QUERY", f"query_quality={signals['query_quality']}", 0.98, "오타·자소 복원 후보를 생성하고 원문과 함께 평가"))
    if signals.get("normalized_query"):
        findings.append(Finding("NORMALIZATION_REQUIRED", f"normalized_query={signals['normalized_query']}", 0.95, "정규화 결과를 별도 필드로 검색하고 원문 결과와 병합"))
    if float(signals.get("intent_confidence", 1.0)) < 0.6:
        findings.append(Finding("AMBIGUOUS_INTENT", f"intent_confidence={signals['intent_confidence']}", 0.82, "카테고리·속성 의도 후보를 분리해 재검색"))
    return findings


def result_relevance(item: dict[str, Any]) -> list[Finding]:
    signals = item["signals"]
    findings: list[Finding] = []
    if signals.get("zero_results"):
        findings.append(Finding("ZERO_RESULTS", "zero_results=true", 1.0, "동의어·오타 보정 후 단계적 완화 검색"))
    elif "result_count" in signals and int(signals["result_count"]) < 5:
        findings.append(Finding("LOW_RESULT_COUNT", f"result_count={signals['result_count']}", 0.96, "필터 완화 전후 결과 수와 적합도를 비교"))
    if float(signals.get("irrelevant_ratio_at_10", 0.0)) >= 0.3:
        findings.append(Finding("LOW_PRECISION_AT_10", f"irrelevant_ratio_at_10={signals['irrelevant_ratio_at_10']}", 0.94, "상위 10개 필드 일치와 카테고리 적합성 재가중"))
    if int(signals.get("negative_constraint_violations", 0)) > 0:
        findings.append(Finding("NEGATIVE_CONSTRAINT_VIOLATION", f"violations={signals['negative_constraint_violations']}", 0.97, "부정 조건을 후처리가 아닌 hard filter로 적용"))
    if int(signals.get("brand_result_count", 1)) == 0:
        findings.append(Finding("BRAND_RECALL_MISS", "brand_result_count=0", 0.99, "브랜드 엔티티 필드와 별칭 사전을 점검"))
    return findings


def term_understanding(item: dict[str, Any]) -> list[Finding]:
    signals = item["signals"]
    findings: list[Finding] = []
    if signals.get("synonym_missing"):
        findings.append(Finding("SYNONYM_GAP", "synonym_missing=true", 0.95, "양방향 동의어 후보를 골든셋으로 검증"))
    terms = signals.get("tokenized_terms", [])
    if len(terms) > 1 and item["segment"] in {"brand", "false_color"}:
        findings.append(Finding("ENTITY_SPLIT", f"tokenized_terms={terms}", 0.93, "엔티티 보존 토크나이저 또는 user_dictionary 적용"))
    if signals.get("missing_attributes"):
        findings.append(Finding("ATTRIBUTE_RECALL_MISS", f"missing_attributes={signals['missing_attributes']}", 0.91, "속성 필드 recall과 필터 매핑을 점검"))
    return findings


def boosting(item: dict[str, Any]) -> list[Finding]:
    signals = item["signals"]
    findings: list[Finding] = []
    if int(signals.get("exact_match_rank", 1)) > 3:
        findings.append(Finding("EXACT_MATCH_DEMOTED", f"exact_match_rank={signals['exact_match_rank']}", 0.98, "정확일치 보호 구간 또는 lexical floor 적용"))
    if signals.get("popularity_override"):
        findings.append(Finding("POPULARITY_OVERBOOST", "popularity_override=true", 0.96, "인기도 상한·query intent별 가중치 적용"))
    return findings


DIAGNOSERS: dict[str, Diagnoser] = {
    "query_quality": query_quality,
    "result_relevance": result_relevance,
    "term_understanding": term_understanding,
    "boosting": boosting,
}
