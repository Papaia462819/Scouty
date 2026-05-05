#!/usr/bin/env python3
"""Tier-B expression-layer retrieval and faithfulness benchmark."""

from __future__ import annotations

import argparse
import json
import re
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
DEFAULT_QUERIES = ROOT / "tools/benchmarks/expression_ro_queries.json"
DEFAULT_OUTPUT = ROOT / "tools/benchmarks/expression_ro_results.json"
BENCH_DOMAINS = {"campfire_basics", "gear_and_preparation", "tips_and_tricks", "survival_basics"}


@dataclass
class Chunk:
    chunk_id: str
    domain: str
    topic: str
    title: str
    body: str
    keywords: str
    metadata: dict
    source_trust: int


class JinaReranker:
    def __init__(self, model_path: Path, tokenizer_path: Path, max_length: int = 512) -> None:
        options = ort.SessionOptions()
        options.graph_optimization_level = ort.GraphOptimizationLevel.ORT_ENABLE_ALL
        self.session = ort.InferenceSession(str(model_path), sess_options=options, providers=["CPUExecutionProvider"])
        self.tokenizer = Tokenizer.from_file(str(tokenizer_path))
        self.max_length = max_length

    def score(self, query: str, chunks: list[Chunk]) -> list[float]:
        encoded = [
            self.tokenizer.encode(query[:320], f"{chunk.title}\n{chunk.body}"[:1800])
            for chunk in chunks
        ]
        input_ids = np.array([pad(item.ids[: self.max_length], self.max_length, 1) for item in encoded], dtype=np.int64)
        attention_mask = np.array(
            [pad(item.attention_mask[: self.max_length], self.max_length, 0) for item in encoded],
            dtype=np.int64,
        )
        logits = self.session.run(None, {"input_ids": input_ids, "attention_mask": attention_mask})[0]
        return [sigmoid(float(value)) for value in np.asarray(logits).reshape(-1)]


def load_chunks(db_path: Path) -> list[Chunk]:
    con = sqlite3.connect(db_path)
    con.row_factory = sqlite3.Row
    rows = con.execute(
        f"""
        SELECT chunk_id, domain, topic, title, body, keywords, metadata_json, source_trust
        FROM knowledge_chunks
        WHERE language = 'ro'
          AND domain IN ({','.join('?' for _ in BENCH_DOMAINS)})
        """,
        sorted(BENCH_DOMAINS),
    ).fetchall()
    con.close()
    chunks: list[Chunk] = []
    for row in rows:
        metadata = json.loads(row["metadata_json"] or "{}")
        if metadata.get("tier") != "B":
            continue
        chunks.append(
            Chunk(
                chunk_id=row["chunk_id"],
                domain=row["domain"],
                topic=row["topic"],
                title=row["title"],
                body=row["body"],
                keywords=row["keywords"] or "",
                metadata=metadata,
                source_trust=int(row["source_trust"] or 0),
            )
        )
    return chunks


def retrieve(
    query: str,
    domain: str,
    chunks: list[Chunk],
    reranker: JinaReranker | None,
    candidate_limit: int,
    rerank_top_k: int,
) -> tuple[Chunk | None, list[dict], float | None]:
    scored = []
    for chunk in chunks:
        if chunk.domain != domain:
            continue
        scored.append((lexical_score(query, chunk), chunk))
    scored.sort(key=lambda item: item[0], reverse=True)
    candidates = scored[:candidate_limit]
    rerank_latency_ms: float | None = None
    if reranker is not None and len(candidates) > 1:
        started = time.perf_counter()
        rerank_scores = reranker.score(query, [chunk for _, chunk in candidates[:rerank_top_k]])
        rerank_latency_ms = (time.perf_counter() - started) * 1000.0
        by_id = {chunk.chunk_id: base for base, chunk in candidates}
        adjusted = []
        for rerank_score, (_, chunk) in zip(rerank_scores, candidates[:rerank_top_k]):
            adjusted.append(((by_id[chunk.chunk_id] * 0.8) + (rerank_score * 80.0), chunk, rerank_score))
        reranked_ids = {chunk.chunk_id for _, chunk, _ in adjusted}
        adjusted.extend((score, chunk, None) for score, chunk in candidates if chunk.chunk_id not in reranked_ids)
        adjusted.sort(key=lambda item: item[0], reverse=True)
        ranked = adjusted
    else:
        ranked = [(score, chunk, None) for score, chunk in candidates]

    top = ranked[0][1] if ranked else None
    trace = [
        {
            "chunk_id": chunk.chunk_id,
            "title": chunk.title,
            "domain": chunk.domain,
            "score": round(score, 4),
            "reranker_score": None if rerank_score is None else round(rerank_score, 6),
        }
        for score, chunk, rerank_score in ranked[:5]
    ]
    return top, trace, rerank_latency_ms


def lexical_score(query: str, chunk: Chunk) -> float:
    tokens = [token for token in normalize(query).split() if len(token) >= 3]
    title = normalize(chunk.title)
    topic = normalize(chunk.topic)
    keywords = normalize(chunk.keywords)
    body = normalize(chunk.body)
    lead = normalize(str(chunk.metadata.get("lead") or ""))
    score = 0.0
    for token in tokens:
        if token in title:
            score += 18.0
        if token in topic:
            score += 14.0
        if token in keywords:
            score += 12.0
        if token in lead:
            score += 10.0
        if token in body:
            score += min(8.0, body.count(token) * 2.5)
    score += len(set(tokens)) * 0.25
    score += chunk.source_trust * 2.0
    return score


def key_fact_tokens(source: str, lead: str) -> set[str]:
    facts: set[str] = set()
    facts.update(normalize(match.group(0)) for match in re.finditer(r"\b\d+(?:[.,:/-]\d+)*\b", source))
    facts.update(normalize(match.group(0)) for match in re.finditer(r"\b[A-ZĂÂÎȘȚ][A-Za-zĂÂÎȘȚăâîșț0-9-]{2,}\b", source))
    facts.update(token for token in normalize(lead).split() if len(token) >= 4 and token not in STOPWORDS)
    return {fact for fact in facts if len(fact) >= 2 and fact not in STOPWORDS}


def faithfulness_pass(source: str, lead: str, response: str) -> tuple[bool, float, int]:
    facts = key_fact_tokens(source, lead)
    if not facts:
        return True, 1.0, 0
    response_tokens = set(normalize(response).split())
    normalized_response = normalize(response)
    covered = sum(1 for fact in facts if fact in response_tokens or fact in normalized_response)
    coverage = covered / len(facts)
    return coverage >= 0.70, coverage, len(facts)


def has_diacritic_when_long(text: str) -> bool:
    word_count = len([part for part in re.split(r"\s+", text.strip()) if part])
    if word_count < 30:
        return True
    return bool(re.search(r"[ăâîșțĂÂÎȘȚ]", text))


def normalize(value: str) -> str:
    value = unicodedata.normalize("NFD", value.lower())
    value = "".join(ch for ch in value if not unicodedata.combining(ch))
    value = re.sub(r"[^\w]+", " ", value, flags=re.UNICODE)
    return re.sub(r"\s+", " ", value).strip()


def pad(values: list[int], length: int, value: int) -> list[int]:
    return values + [value] * max(0, length - len(values))


def sigmoid(value: float) -> float:
    return 1.0 / (1.0 + np.exp(-value))


def percentile(values: list[float], pct: float) -> float | None:
    if not values:
        return None
    ordered = sorted(values)
    index = min(len(ordered) - 1, max(0, round((pct / 100.0) * (len(ordered) - 1))))
    return ordered[index]


def main() -> int:
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--db", type=Path, default=DEFAULT_DB)
    parser.add_argument("--queries", type=Path, default=DEFAULT_QUERIES)
    parser.add_argument("--model", type=Path, default=DEFAULT_MODEL)
    parser.add_argument("--tokenizer", type=Path, default=DEFAULT_TOKENIZER)
    parser.add_argument("--json-out", type=Path, default=DEFAULT_OUTPUT)
    parser.add_argument("--candidate-limit", type=int, default=48)
    parser.add_argument("--rerank-top-k", type=int, default=8)
    parser.add_argument("--no-reranker", action="store_true")
    args = parser.parse_args()

    payload = json.loads(args.queries.read_text(encoding="utf-8"))
    queries = payload["queries"]
    chunks = load_chunks(args.db)
    reranker = None if args.no_reranker else JinaReranker(args.model, args.tokenizer)

    rows = []
    latencies = []
    for item in queries:
        top, trace, latency = retrieve(
            query=item["query"],
            domain=item["domain"],
            chunks=chunks,
            reranker=reranker,
            candidate_limit=args.candidate_limit,
            rerank_top_k=args.rerank_top_k,
        )
        if latency is not None:
            latencies.append(latency)
        top_id = top.chunk_id if top else None
        correct = top_id == item["golden_chunk_id"]
        expression_text = item["ideal_answer"]
        source = top.body if top else ""
        lead = str(top.metadata.get("lead") or "") if top else ""
        faithful, coverage, fact_count = faithfulness_pass(source, lead, expression_text)
        rows.append(
            {
                **item,
                "top1_chunk_id": top_id,
                "top1_correct": correct,
                "top5": trace,
                "rerank_latency_ms": latency,
                "expression_mode": "ideal_answer_simulation",
                "faithfulness_pass": faithful if correct else None,
                "faithfulness_coverage": coverage if correct else None,
                "key_fact_count": fact_count if correct else None,
                "diacritic_correct": has_diacritic_when_long(expression_text) if correct else None,
            }
        )

    correct_rows = [row for row in rows if row["top1_correct"]]
    faithfulness_values = [row["faithfulness_pass"] for row in correct_rows if row["faithfulness_pass"] is not None]
    diacritic_values = [row["diacritic_correct"] for row in correct_rows if row["diacritic_correct"] is not None]
    summary = {
        "query_count": len(rows),
        "retrieval_top1_accuracy": len(correct_rows) / len(rows) if rows else 0.0,
        "faithfulness_pass_rate": sum(1 for value in faithfulness_values if value) / len(faithfulness_values)
        if faithfulness_values else None,
        "diacritic_correctness": sum(1 for value in diacritic_values if value) / len(diacritic_values)
        if diacritic_values else None,
        "reranker_latency_ms_p50": statistics.median(latencies) if latencies else None,
        "reranker_latency_ms_p95": percentile(latencies, 95.0),
        "expression_latency_ms_p50": None,
        "expression_latency_ms_p95": None,
        "expression_mode": "ideal_answer_simulation",
        "qwen_validated": False,
        "notes": (
            "This host benchmark validates retrieval and the deterministic faithfulness checker. "
            "Qwen expression latency and output quality require a device/llama.cpp run."
        ),
    }
    result = {"summary": summary, "rows": rows}
    args.json_out.write_text(json.dumps(result, ensure_ascii=False, indent=2) + "\n", encoding="utf-8")
    print(json.dumps(summary, ensure_ascii=False, indent=2))
    return 0


STOPWORDS = {
    "acest", "aceasta", "aceste", "acolo", "adica", "apoi", "asa", "asadar", "cand", "care",
    "catre", "daca", "deci", "despre", "este", "fara", "foarte", "iata", "intr", "intre",
    "mai", "mult", "nici", "pentru", "peste", "prin", "sau", "sunt", "trebuie", "unui",
    "unei", "unde", "scouty",
}


if __name__ == "__main__":
    raise SystemExit(main())
