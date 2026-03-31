#!/usr/bin/env python3
"""
Converts Detekt XML report to memory.json for trend tracking.
Usage: python3 detekt_to_memory.py <detekt.xml> <memory.json>
"""
import json
import sys
import xml.etree.ElementTree as ET
from datetime import datetime, timezone
from pathlib import Path

SEVERITY_MAP = {
    "error": "error",
    "warning": "warning",
    "info": "info",
}

def parse_detekt_xml(xml_path: str) -> dict:
    tree = ET.parse(xml_path)
    root = tree.getroot()

    issues = []
    by_rule: dict[str, int] = {}
    by_severity: dict[str, int] = {"error": 0, "warning": 0, "info": 0}

    for file_elem in root.findall("file"):
        file_name = file_elem.get("name", "")
        # Strip absolute path prefix to keep paths relative
        for prefix in ["/home/runner/work/", "/Users/"]:
            if prefix in file_name:
                parts = file_name.split("/")
                # Keep from repo root (after owner/repo)
                try:
                    idx = next(i for i, p in enumerate(parts) if "catchy" in p.lower())
                    file_name = "/".join(parts[idx + 1:])
                except StopIteration:
                    pass
                break

        for error_elem in file_elem.findall("error"):
            rule = error_elem.get("source", "unknown").split(".")[-1]
            severity = SEVERITY_MAP.get(error_elem.get("severity", "warning"), "warning")
            issue = {
                "file": file_name,
                "line": int(error_elem.get("line", 0)),
                "column": int(error_elem.get("column", 0)),
                "severity": severity,
                "rule": rule,
                "ruleSet": error_elem.get("source", "").rsplit(".", 2)[-2] if "." in error_elem.get("source", "") else "unknown",
                "message": error_elem.get("message", ""),
            }
            issues.append(issue)
            by_rule[rule] = by_rule.get(rule, 0) + 1
            by_severity[severity] = by_severity.get(severity, 0) + 1

    top_rules = sorted(by_rule.items(), key=lambda x: x[1], reverse=True)[:10]

    return {
        "total": len(issues),
        "by_severity": by_severity,
        "top_rules": [{"rule": r, "count": c} for r, c in top_rules],
        "issues": issues,
    }


def update_memory(memory_path: str, run_data: dict, commit_sha: str, branch: str) -> None:
    path = Path(memory_path)
    if path.exists():
        with open(path) as f:
            memory = json.load(f)
    else:
        memory = {
            "project": "de.taxel.catchy",
            "description": "Detekt code quality history",
            "runs": [],
        }

    entry = {
        "timestamp": datetime.now(timezone.utc).isoformat(),
        "commit": commit_sha,
        "branch": branch,
        "summary": {
            "total_issues": run_data["total"],
            "errors": run_data["by_severity"]["error"],
            "warnings": run_data["by_severity"]["warning"],
            "infos": run_data["by_severity"]["info"],
        },
        "top_rules": run_data["top_rules"],
        "issues": run_data["issues"],
    }

    memory["runs"].append(entry)
    # Keep last 50 runs to avoid unbounded growth
    memory["runs"] = memory["runs"][-50:]
    memory["latest"] = entry["summary"]
    memory["last_updated"] = entry["timestamp"]

    # Trend: compare with previous run
    if len(memory["runs"]) >= 2:
        prev = memory["runs"][-2]["summary"]["total_issues"]
        curr = entry["summary"]["total_issues"]
        memory["trend"] = {
            "delta": curr - prev,
            "direction": "improved" if curr < prev else ("worse" if curr > prev else "stable"),
        }

    path.parent.mkdir(parents=True, exist_ok=True)
    with open(path, "w") as f:
        json.dump(memory, f, indent=2, ensure_ascii=False)

    print(f"memory.json updated: {entry['summary']}")
    if "trend" in memory:
        t = memory["trend"]
        symbol = "↓" if t["direction"] == "improved" else ("↑" if t["direction"] == "worse" else "→")
        print(f"Trend: {symbol} {t['direction']} ({t['delta']:+d} issues)")


if __name__ == "__main__":
    if len(sys.argv) < 3:
        print("Usage: detekt_to_memory.py <detekt.xml> <memory.json> [commit_sha] [branch]")
        sys.exit(1)

    xml_path = sys.argv[1]
    memory_path = sys.argv[2]
    commit_sha = sys.argv[3] if len(sys.argv) > 3 else "unknown"
    branch = sys.argv[4] if len(sys.argv) > 4 else "unknown"

    if not Path(xml_path).exists():
        print(f"ERROR: XML report not found: {xml_path}")
        sys.exit(1)

    run_data = parse_detekt_xml(xml_path)
    update_memory(memory_path, run_data, commit_sha, branch)
