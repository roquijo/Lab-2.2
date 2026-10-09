#!/usr/bin/env python3
"""Compara los summary-export de k6 y las muestras de docker stats.

No inventa cifras: si falta un archivo, termina con error.
"""

import json
import sys
from pathlib import Path

ROOT = Path(__file__).resolve().parent / "results"


def load_json(name: str) -> dict:
    path = ROOT / name
    if not path.exists():
        print(f"Falta {path}. Ejecuta primero load-tests/run-benchmark.ps1.")
        sys.exit(1)
    return json.loads(path.read_text(encoding="utf-8"))


def metric(summary: dict, name: str, stat: str) -> float:
    node = summary["metrics"][name]
    values = node.get("values", node)
    if stat not in values and stat == "rate" and "value" in values:
        return float(values["value"])
    return float(values[stat])


def pct(baseline: float, instrumented: float) -> str:
    if baseline == 0:
        return "n/a (baseline 0)"
    return f"{((instrumented - baseline) / baseline) * 100:.2f}%"


def parse_cpu(value: str) -> float:
    return float(value.strip().replace("%", ""))


def parse_mem(value: str) -> float:
    number = value.split("/")[0].strip()
    amount = float("".join(ch for ch in number if ch.isdigit() or ch == "."))
    if "GiB" in number or "GB" in number:
        return amount * 1024
    return amount


def average_stats(name: str) -> dict:
    path = ROOT / name
    if not path.exists():
        print(f"Falta {path}.")
        sys.exit(1)
    grouped = {}
    for line in path.read_text(encoding="utf-8").splitlines():
        if not line.strip():
            continue
        sample = json.loads(line)
        container = sample["Name"]
        grouped.setdefault(container, {"cpu": [], "mem": []})
        grouped[container]["cpu"].append(parse_cpu(sample["CPUPerc"]))
        grouped[container]["mem"].append(parse_mem(sample["MemUsage"]))
    return {
        container: {
            "cpu": sum(values["cpu"]) / len(values["cpu"]),
            "mem": sum(values["mem"]) / len(values["mem"]),
        }
        for container, values in grouped.items()
    }


def main() -> None:
    if hasattr(sys.stdout, "reconfigure"):
        sys.stdout.reconfigure(encoding="utf-8", errors="replace")
    baseline = load_json("baseline-summary.json")
    instrumented = load_json("instrumented-summary.json")
    base_stats = average_stats("baseline-stats.jsonl")
    otel_stats = average_stats("instrumented-stats.jsonl")

    rows = [
        ("Latencia p50 (ms)", metric(baseline, "http_req_duration", "med"), metric(instrumented, "http_req_duration", "med")),
        ("Latencia p95 (ms)", metric(baseline, "http_req_duration", "p(95)"), metric(instrumented, "http_req_duration", "p(95)")),
        ("Latencia p99 (ms)", metric(baseline, "http_req_duration", "p(99)"), metric(instrumented, "http_req_duration", "p(99)")),
        ("Throughput (req/s)", metric(baseline, "http_reqs", "rate"), metric(instrumented, "http_reqs", "rate")),
        ("Solicitudes", metric(baseline, "http_reqs", "count"), metric(instrumented, "http_reqs", "count")),
        ("Tasa de errores", metric(baseline, "http_req_failed", "rate"), metric(instrumented, "http_req_failed", "rate")),
    ]

    lines = [
        "# Resultados del benchmark",
        "",
        "Generado por `load-tests/analyze_overhead.py` a partir de los JSON de k6 y de `docker stats`.",
        "",
        "Fórmulas:",
        "",
        "- overhead absoluto = instrumentado - baseline",
        "- overhead porcentual = (instrumentado - baseline) / baseline * 100",
        "- CPU y memoria son el promedio de las muestras de `docker stats` durante la corrida",
        "",
        "| Métrica | Baseline | Instrumentado | Overhead absoluto | Overhead % |",
        "|---|---:|---:|---:|---:|",
    ]
    for label, base, otel in rows:
        lines.append(f"| {label} | {base:.4f} | {otel:.4f} | {otel - base:.4f} | {pct(base, otel)} |")

    lines.extend(
        [
            "",
            "## Recursos por contenedor",
            "",
            "CPU es el porcentaje que informa `docker stats` (puede superar 100 en varios núcleos). Memoria está en MiB.",
            "El overhead de CPU se expresa en puntos porcentuales (instrumentado - baseline) y tambien como porcentaje respecto del baseline.",
            "",
            "| Contenedor | CPU baseline % | CPU OTel % | CPU absoluto (pp) | CPU % | Mem baseline MiB | Mem OTel MiB | Mem absoluto MiB | Mem % |",
            "|---|---:|---:|---:|---:|---:|---:|---:|---:|",
        ]
    )
    names = sorted(set(base_stats) | set(otel_stats))
    for name in names:
        base = base_stats.get(name, {"cpu": float("nan"), "mem": float("nan")})
        otel = otel_stats.get(name, {"cpu": float("nan"), "mem": float("nan")})
        lines.append(
            f"| {name} | {base['cpu']:.2f} | {otel['cpu']:.2f} | {otel['cpu'] - base['cpu']:.2f} | {pct(base['cpu'], otel['cpu'])} | "
            f"{base['mem']:.2f} | {otel['mem']:.2f} | {otel['mem'] - base['mem']:.2f} | {pct(base['mem'], otel['mem'])} |"
        )

    target = ROOT / "overhead-report.md"
    target.write_text("\n".join(lines) + "\n", encoding="utf-8")
    print(target.read_text(encoding="utf-8"))


if __name__ == "__main__":
    main()
