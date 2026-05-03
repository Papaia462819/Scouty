#!/usr/bin/env python3
"""Romanian reranker benchmark for the Scouty campfire retrieval path."""

from __future__ import annotations

import argparse
import json
import math
import sqlite3
import statistics
import time
import unicodedata
from dataclasses import dataclass
from pathlib import Path

import numpy as np
import onnxruntime as ort
from tokenizers import Tokenizer


ROOT = Path(__file__).resolve().parents[2]
DEFAULT_DB = ROOT / "app/src/main/scouty_assets/knowledge_pack.sqlite"
DEFAULT_MODEL = ROOT / "app/src/main/scouty_assets/ml/jina-reranker-v2-base-multilingual-int8.onnx"
DEFAULT_TOKENIZER = ROOT / "app/src/main/scouty_assets/ml/jina-reranker-v2-base-multilingual-tokenizer.json"
DEFAULT_QUERIES = ROOT / "tools/benchmarks/reranker_ro_queries.json"


@dataclass
class Card:
    chunk_id: str
    title: str
    body: str
    family: str
    priority: int
    keywords: str
    payload: dict


@dataclass
class ScoredCard:
    card: Card
    slot_compatibility: float
    lexical_hints: float
    priority_weight: float
    anti_negative_default: float
    final_score: float
    reranker_score: float | None = None

    @property
    def constraint_activation_confidence(self) -> float:
        return self.slot_compatibility * self.lexical_hints


class JinaReranker:
    def __init__(self, model_path: Path, tokenizer_path: Path, max_length: int = 512) -> None:
        so = ort.SessionOptions()
        so.graph_optimization_level = ort.GraphOptimizationLevel.ORT_ENABLE_ALL
        self.session = ort.InferenceSession(str(model_path), sess_options=so, providers=["CPUExecutionProvider"])
        self.tokenizer = Tokenizer.from_file(str(tokenizer_path))
        self.max_length = max_length

    def score(self, query: str, cards: list[Card]) -> list[float]:
        encoded = [
            self.tokenizer.encode(query[:320], f"{card.title}\n{card.body}"[:1800])
            for card in cards
        ]
        input_ids = np.array([pad(item.ids[: self.max_length], self.max_length, 1) for item in encoded], dtype=np.int64)
        attention_mask = np.array(
            [pad(item.attention_mask[: self.max_length], self.max_length, 0) for item in encoded],
            dtype=np.int64,
        )
        logits = self.session.run(None, {"input_ids": input_ids, "attention_mask": attention_mask})[0]
        return [sigmoid(float(value)) for value in np.asarray(logits).reshape(-1)]


def load_cards(db_path: Path) -> list[Card]:
    con = sqlite3.connect(db_path)
    con.row_factory = sqlite3.Row
    rows = con.execute(
        """
        SELECT chunk_id, title, body, card_family, priority, keywords, metadata_json
        FROM knowledge_chunks
        WHERE domain = 'field_know_how'
          AND topic = 'campfire'
          AND language = 'ro'
        """
    ).fetchall()
    return [
        Card(
            chunk_id=row["chunk_id"],
            title=row["title"],
            body=row["body"],
            family=row["card_family"],
            priority=row["priority"],
            keywords=row["keywords"] or "",
            payload=json.loads(row["metadata_json"] or "{}"),
        )
        for row in rows
    ]


def analyze_target_family(query: str) -> str:
    normalized = normalize(query)
    if looks_like_definition_query(normalized) and contains_any(
        normalized,
        "iasca",
        "tinder",
        "tindar",
        "kindling",
        "surcele",
        "amnar",
        "triunghiul focului",
        "vatra",
        "rece la atingere",
        "ordinea materialelor",
    ):
        return "definition"
    if contains_any(
        normalized,
        "totul e ud",
        "bate vantul",
        "vant puternic",
        "nu gasesc",
        "nu am",
        "nu merge",
        "nu se aprinde",
        "am voie",
        "interzis",
        "radacini",
        "iarba uscata",
        "in cort",
        "se intuneca",
        "sunt obosit",
        "merita sa mai incerc",
        "mai bine fac altceva",
    ):
        return "constraint"
    return "scenario"


def score_cards(cards: list[Card], query: str) -> list[ScoredCard]:
    normalized = normalize(query)
    facts = extract_facts(normalized)
    is_fresh_conversation = True
    scored: list[ScoredCard] = []
    for card in cards:
        slot = slot_compatibility(card, facts)
        lexical = phrase_match_score(card, normalized)
        priority = max(0, min(card.priority, 130)) / 130.0
        anti = anti_negative_default(card, slot, lexical, is_fresh_conversation)
        final = (slot * 35.0) + (lexical * 15.0) + (priority * 5.0) + anti
        scored.append(
            ScoredCard(
                card=card,
                slot_compatibility=slot,
                lexical_hints=lexical,
                priority_weight=priority,
                anti_negative_default=anti,
                final_score=final,
            )
        )
    return sorted(scored, key=lambda item: item.final_score, reverse=True)


def select_primary(scored: list[ScoredCard], target_family: str) -> ScoredCard:
    best_scenario = best_scenario_candidate(scored)
    best_definition = best_definition_candidate(scored)
    best_constraint_override = best_constraint_override_candidate(scored, best_scenario, target_family)
    if target_family == "definition":
        return best_definition or best_scenario
    return best_constraint_override or best_scenario


def best_scenario_candidate(scored: list[ScoredCard]) -> ScoredCard:
    scenarios = [item for item in scored if item.card.family == "scenario"]
    best = max(scenarios, key=lambda item: item.final_score)
    fallback = next((item for item in scenarios if item.card.chunk_id == "campfire_general_entry"), None)
    if fallback is None or best.card.chunk_id == fallback.card.chunk_id:
        return best
    return best if has_strong_scenario_evidence(best) else fallback


def best_definition_candidate(scored: list[ScoredCard]) -> ScoredCard | None:
    definitions = [item for item in scored if item.card.family == "definition" and has_strong_definition_evidence(item)]
    return max(definitions, key=lambda item: item.final_score) if definitions else None


def best_constraint_override_candidate(
    scored: list[ScoredCard],
    best_scenario: ScoredCard,
    target_family: str,
) -> ScoredCard | None:
    candidates = [
        item for item in scored
        if item.card.family == "constraint" and can_constraint_be_primary(item, best_scenario, target_family)
    ]
    return max(candidates, key=lambda item: item.final_score) if candidates else None


def has_strong_scenario_evidence(candidate: ScoredCard) -> bool:
    return candidate.lexical_hints >= 0.35 or candidate.slot_compatibility > 0.5


def has_strong_definition_evidence(candidate: ScoredCard) -> bool:
    return candidate.lexical_hints >= 0.55 or candidate.final_score >= 32.0


def can_constraint_be_primary(candidate: ScoredCard, best_scenario: ScoredCard, target_family: str) -> bool:
    if target_family == "definition":
        return False
    if candidate.card.payload.get("constraint_mode", "support") != "override":
        return False
    if candidate.slot_compatibility < 0.8 or candidate.constraint_activation_confidence < 0.6:
        return False
    return candidate.final_score >= best_scenario.final_score - 15.0 or (
        candidate.slot_compatibility == 1.0 and candidate.lexical_hints >= 0.5
    )


def apply_reranker(
    reranker: JinaReranker,
    query: str,
    scored: list[ScoredCard],
    top_k: int,
    deterministic_weight: float,
    reranker_weight: float,
) -> tuple[list[ScoredCard], float]:
    candidates = scored[:top_k]
    started = time.perf_counter()
    scores = reranker.score(query, [item.card for item in candidates])
    elapsed_ms = (time.perf_counter() - started) * 1000.0
    by_id = {item.card.chunk_id: item for item in scored}
    adjusted = []
    for candidate, rerank_score in zip(candidates, scores):
        adjusted.append(
            ScoredCard(
                card=candidate.card,
                slot_compatibility=candidate.slot_compatibility,
                lexical_hints=candidate.lexical_hints,
                priority_weight=candidate.priority_weight,
                anti_negative_default=candidate.anti_negative_default,
                final_score=(candidate.final_score * deterministic_weight) + (rerank_score * reranker_weight),
                reranker_score=rerank_score,
            )
        )
    reranked_ids = {item.card.chunk_id for item in adjusted}
    return adjusted + [item for item in scored if item.card.chunk_id not in reranked_ids], elapsed_ms


def slot_compatibility(card: Card, facts: dict[str, str]) -> float:
    constraints = card.payload.get("slot_constraints") or {}
    if not constraints:
        return 0.5
    matched = 0
    for key, value in constraints.items():
        fact = facts.get(key)
        if fact is None:
            continue
        if fact.lower() != str(value).lower():
            return 0.0
        matched += 1
    return matched / len(constraints)


def phrase_match_score(card: Card, normalized_query: str) -> float:
    if not normalized_query:
        return 0.0
    max_score = 0.0
    if normalized_query == normalize(card.title):
        return 1.0
    term = card.payload.get("term")
    if term:
        max_score = max(max_score, term_match_score(normalize(term), normalized_query))
    for phrasing in card.payload.get("user_phrasings") or []:
        normalized_phrasing = normalize(phrasing)
        if normalized_phrasing in normalized_query or normalized_query in normalized_phrasing:
            max_score = max(max_score, 0.9)
        else:
            ratio = phrase_coverage_score(normalized_phrasing, normalized_query, 1)
            if ratio >= 0.6:
                max_score = max(max_score, 0.7 * ratio)
    card_tokens = tokenize(card.keywords)
    query_tokens = tokenize(normalized_query)
    if card_tokens and query_tokens:
        fuzzy = sum(
            1 for card_token in card_tokens
            if any(tokens_match(card_token, query_token, 1) for query_token in query_tokens)
        )
        if fuzzy:
            max_score = max(max_score, 0.5 * fuzzy / len(card_tokens))
    return max_score


def term_match_score(normalized_term: str, normalized_query: str) -> float:
    if not normalized_term:
        return 0.0
    if normalized_term in normalized_query:
        return 0.95
    ratio = phrase_coverage_score(normalized_term, normalized_query, 2)
    if ratio >= 1.0:
        return 0.8
    if ratio >= 0.6:
        return 0.65 * ratio
    return 0.0


def phrase_coverage_score(phrase: str, normalized_query: str, max_token_distance: int) -> float:
    phrase_tokens = list(tokenize(phrase))
    query_tokens = list(tokenize(normalized_query))
    if not phrase_tokens or not query_tokens:
        return 0.0
    best = 0.0
    if len(query_tokens) >= len(phrase_tokens):
        for start in range(0, len(query_tokens) - len(phrase_tokens) + 1):
            matched = sum(
                1 for index, token in enumerate(phrase_tokens)
                if tokens_match(token, query_tokens[start + index], max_token_distance)
            )
            best = max(best, matched / len(phrase_tokens))
            if best == 1.0:
                return best
    fuzzy = sum(
        1 for phrase_token in phrase_tokens
        if any(tokens_match(phrase_token, query_token, max_token_distance) for query_token in query_tokens)
    )
    return max(best, fuzzy / len(phrase_tokens))


def tokens_match(left: str, right: str, max_distance: int) -> bool:
    if left == right:
        return True
    minimum = min(len(left), len(right))
    if minimum <= 3:
        allowed = 0
    elif minimum <= 6:
        allowed = min(max_distance, 1)
    else:
        allowed = max_distance
    return levenshtein(left, right) <= allowed


def anti_negative_default(card: Card, slot: float, lexical: float, is_fresh_conversation: bool) -> float:
    penalty = 0.0
    if card.family == "constraint":
        if slot == 0.5 and lexical < 0.3:
            penalty -= 20.0
        elif lexical < 0.4 and slot < 0.8:
            penalty -= 25.0
    if card.chunk_id in {"campfire_not_worth_it_for_goal", "campfire_alternative_to_fire_for_warmth"} and is_fresh_conversation:
        penalty -= 15.0
    return penalty


def extract_facts(normalized: str) -> dict[str, str]:
    facts: dict[str, str] = {}
    if contains_any(normalized, "caldura", "incalz", "incalzire", "frig"):
        facts["goal"] = "warmth"
    if contains_any(normalized, "gatit", "gatesc", "mancare", "mancarea"):
        facts["goal"] = "cooking"
    if contains_any(normalized, "fierb apa", "fiert apa", "boil water", "apa pentru baut"):
        facts["goal"] = "boil_water"
    if contains_any(normalized, "amnar", "ferro"):
        facts["ignition_source"] = "ferro"
    if contains_any(normalized, "bricheta", "brichete"):
        facts["ignition_source"] = "lighter"
    if contains_any(normalized, "chibrit", "chibrite"):
        facts["ignition_source"] = "matches"
    if contains_any(normalized, "pot face scanteie", "pot produce scanteie", "am scanteie", "sursa de scanteie", "spark"):
        facts["ignition_source"] = "recognized_spark"
    if contains_any(normalized, "n am nimic de aprindere", "nu am nimic de aprindere", "fara aprindere", "n am nimic", "nu am nimic"):
        if not contains_any(normalized, "amnar", "ferro", "bricheta", "chibrit"):
            facts["ignition_source"] = "none"
    if contains_any(normalized, "totul e ud", "tot e ud", "plouat", "ploua", "ud leoarca", "lemn ud", "lemne ude"):
        facts["fuel_condition"] = "wet"
    if contains_any(normalized, "umed", "umezeala", "cam ud"):
        facts["fuel_condition"] = "damp"
    if contains_any(normalized, "uscat", "uscate", "uscata"):
        facts["fuel_condition"] = "dry"
    if contains_any(normalized, "nu gasesc material", "nu gasesc lemn", "nu am lemne", "material putin", "putin material", "nu gasesc surcele"):
        facts["fuel_condition"] = "scarce"
    if contains_any(normalized, "vant puternic", "bate vantul tare", "bate vantul", "vijelie"):
        facts["wind"] = "high"
    elif contains_any(normalized, "vant", "vanticel"):
        facts["wind"] = "moderate"
    if contains_any(normalized, "nu am voie", "interzis", "interzisa", "forbidden"):
        facts["permission"] = "forbidden"
    if contains_any(normalized, "este permis", "e permis", "am voie aici", "pot face foc aici"):
        facts["permission"] = "unknown"
    if contains_any(normalized, "radacini", "turba", "pamant turbos"):
        facts["ground_risk"] = "roots_or_peat"
    if contains_any(normalized, "iarba uscata", "vegetatie uscata", "litiera uscata"):
        facts["ground_risk"] = "dry_vegetation"
    if contains_any(normalized, "in cort", "langa cort", "sub prelata", "sub tarp"):
        facts["ground_risk"] = "indoor_or_tent"
    if contains_any(normalized, "nu am iasca", "n am iasca", "improvizez iasca", "improvizam"):
        facts["tinder_strategy"] = "improvise"
    if contains_any(normalized, "se intuneca", "e noapte", "aproape noapte", "mai e putina lumina"):
        facts["daylight"] = "low"
    if contains_any(normalized, "sunt obosit", "suntem obositi", "prea obosit", "epuizat"):
        facts["fatigue"] = "high"
    if contains_any(normalized, "ar fi util", "doar daca merge", "optional", "nu e necesar"):
        facts["need_level"] = "optional"
    return facts


def contains_any(normalized: str, *phrases: str) -> bool:
    return any(normalize(phrase) in normalized for phrase in phrases)


def looks_like_definition_query(normalized: str) -> bool:
    return normalized.startswith(("ce e ", "ce inseamna ", "ce este ", "adica "))


def tokenize(value: str) -> set[str]:
    return {token for token in normalize(value).split(" ") if len(token) >= 3}


def normalize(value: str) -> str:
    decomposed = unicodedata.normalize("NFD", value.lower())
    stripped = "".join(ch for ch in decomposed if unicodedata.category(ch) != "Mn")
    clean = "".join(ch if ch.isalnum() else " " for ch in stripped)
    return " ".join(normalize_ro_token(token) for token in clean.split() if token)


def normalize_ro_token(token: str) -> str:
    if token in {"tinder", "tindar", "tindr"}:
        return "iasca"
    while len(token) > 1 and any(a == b for a, b in zip(token, token[1:])):
        chars = []
        previous = None
        for ch in token:
            if ch != previous:
                chars.append(ch)
            previous = ch
        token = "".join(chars)
    if token.endswith("ului") and len(token) > 6:
        return token[:-5]
    if token.endswith(("ilor", "elor")) and len(token) > 6:
        return token[:-4]
    if token.endswith("lor") and len(token) > 5:
        return token[:-3]
    if token.endswith("ul") and len(token) > 4:
        return token[:-2]
    if token.endswith("le") and len(token) > 4:
        return token[:-2]
    if token.endswith("u") and len(token) > 3 and token[-2:] not in {"au", "eu", "iu", "ou"}:
        return token[:-1]
    return token


def levenshtein(left: str, right: str) -> int:
    previous = list(range(len(right) + 1))
    for left_index, left_char in enumerate(left):
        current = [left_index + 1] + [0] * len(right)
        for right_index, right_char in enumerate(right):
            substitution = 0 if left_char == right_char else 1
            current[right_index + 1] = min(
                previous[right_index + 1] + 1,
                current[right_index] + 1,
                previous[right_index] + substitution,
            )
        previous = current
    return previous[len(right)]


def pad(values: list[int], size: int, pad_value: int) -> list[int]:
    if len(values) >= size:
        return values[:size]
    return values + [pad_value] * (size - len(values))


def sigmoid(value: float) -> float:
    bounded = max(-50.0, min(50.0, value))
    return 1.0 / (1.0 + math.exp(-bounded))


def main() -> None:
    parser = argparse.ArgumentParser()
    parser.add_argument("--db", type=Path, default=DEFAULT_DB)
    parser.add_argument("--model", type=Path, default=DEFAULT_MODEL)
    parser.add_argument("--tokenizer", type=Path, default=DEFAULT_TOKENIZER)
    parser.add_argument("--queries", type=Path, default=DEFAULT_QUERIES)
    parser.add_argument("--top-k", type=int, default=3)
    parser.add_argument("--max-length", type=int, default=96)
    parser.add_argument("--deterministic-weight", type=float, default=0.8)
    parser.add_argument("--reranker-weight", type=float, default=20.0)
    parser.add_argument("--json-out", type=Path)
    args = parser.parse_args()

    cards = load_cards(args.db)
    labelled_queries = json.loads(args.queries.read_text(encoding="utf-8"))
    reranker = JinaReranker(args.model, args.tokenizer, max_length=args.max_length)

    rows = []
    latencies = []
    for item in labelled_queries:
        query = item["query"]
        expected = item["expected_chunk_id"]
        target_family = analyze_target_family(query)
        baseline_scored = score_cards(cards, query)
        baseline_primary = select_primary(baseline_scored, target_family)
        reranked_scored, latency_ms = apply_reranker(
            reranker,
            query,
            baseline_scored,
            args.top_k,
            args.deterministic_weight,
            args.reranker_weight,
        )
        reranked_primary = select_primary(reranked_scored, target_family)
        latencies.append(latency_ms)
        rows.append(
            {
                "query": query,
                "expected_chunk_id": expected,
                "baseline_chunk_id": baseline_primary.card.chunk_id,
                "reranked_chunk_id": reranked_primary.card.chunk_id,
                "baseline_correct": baseline_primary.card.chunk_id == expected,
                "reranked_correct": reranked_primary.card.chunk_id == expected,
                "latency_ms": latency_ms,
            }
        )

    baseline_correct = sum(1 for row in rows if row["baseline_correct"])
    reranked_correct = sum(1 for row in rows if row["reranked_correct"])
    summary = {
        "query_count": len(rows),
        "baseline_top1_accuracy": baseline_correct / len(rows),
        "reranked_top1_accuracy": reranked_correct / len(rows),
        "accuracy_delta": (reranked_correct - baseline_correct) / len(rows),
        "rerank_latency_p50_ms": statistics.median(latencies),
        "rerank_latency_p95_ms": sorted(latencies)[max(0, math.ceil(len(latencies) * 0.95) - 1)],
        "top_k": args.top_k,
        "max_length": args.max_length,
        "deterministic_weight": args.deterministic_weight,
        "reranker_weight": args.reranker_weight,
    }

    print(json.dumps(summary, indent=2, ensure_ascii=False))
    for row in rows:
        marker = "OK" if row["reranked_correct"] else "MISS"
        print(
            f"{marker} | {row['latency_ms']:.1f} ms | expected={row['expected_chunk_id']} "
            f"baseline={row['baseline_chunk_id']} reranked={row['reranked_chunk_id']} | {row['query']}"
        )

    payload = {"summary": summary, "rows": rows}
    if args.json_out:
        args.json_out.parent.mkdir(parents=True, exist_ok=True)
        args.json_out.write_text(json.dumps(payload, indent=2, ensure_ascii=False), encoding="utf-8")

    if summary["rerank_latency_p50_ms"] >= 50.0:
        raise SystemExit("p50 latency gate failed")
    if summary["accuracy_delta"] <= 0.0:
        raise SystemExit("accuracy delta gate failed")


if __name__ == "__main__":
    main()
