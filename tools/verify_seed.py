#!/usr/bin/env python3
"""Audit the original Word data and the five screenshot-confirmed nutrition labels."""

import argparse
import json
from pathlib import Path
import sys
from xml.etree import ElementTree
from zipfile import ZipFile


DOCUMENTS = (
    ("心情口味区数据.docx", "takeout", 34),
    ("大餐日数据.docx", "dining", 12),
    ("零食数据.docx", "snacks", 15),
)
NAMESPACES = {"w": "http://schemas.openxmlformats.org/wordprocessingml/2006/main"}
NUTRITION_NAMES = (
    "赛百味", "沙县鸡腿饭", "潮汕牛肉汤饭", "蛋白粉+农耕记米饭", "霸碗盖码饭",
)


def require(condition, message):
    if not condition:
        raise ValueError(message)


def document_groups(path):
    with ZipFile(path) as document:
        body = ElementTree.fromstring(document.read("word/document.xml")).find("w:body", NAMESPACES)
    groups = []
    for paragraph in body.findall(".//w:p", NAMESPACES):
        text = "".join(node.text or "" for node in paragraph.findall(".//w:t", NAMESPACES))
        if not text.strip():
            continue
        if text.startswith("【") and text.endswith("】"):
            groups.append((text[1:-1], []))
        else:
            if not groups:
                # The snack document starts with seven untitled food entries.
                groups.append(("吃", []))
            groups[-1][1].append(text)
    return groups


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--source-dir", type=Path, default=Path.home() / "Desktop")
    parser.add_argument("--seed", type=Path, default=Path(__file__).resolve().parents[1] / "app/src/main/assets/seed.json")
    args = parser.parse_args()
    seed = json.loads(args.seed.read_text(encoding="utf-8"))
    groups, items = seed["groups"], seed["items"]
    original_groups = [group for group in groups if group["zone"] != "nutrition"]
    nutrition_groups = sorted((group for group in groups if group["zone"] == "nutrition"), key=lambda group: group["position"])
    require(len(groups) == 18, "Expected 13 original groups and 5 screenshot-confirmed nutrition groups.")
    require(len(original_groups) == 13, "Expected 13 original Word-document groups.")
    require(len(items) == 61, "Expected 61 original food entries.")
    require(len({row["id"] for row in groups}) == len(groups), "Duplicate group IDs.")
    require(len({row["id"] for row in items}) == len(items), "Duplicate item IDs.")
    require(all(row["count"] == 0 for row in items), "Source documents contain no historical counts.")
    group_ids = {row["id"] for row in groups}
    require(all(row["groupId"] in group_ids for row in items), "A food entry has no parent group.")
    require(tuple(row["name"] for row in nutrition_groups) == NUTRITION_NAMES, "Nutrition labels or clockwise order differ from the supplied screenshot.")
    require([row["position"] for row in nutrition_groups] == list(range(5)), "Nutrition positions must be 0 through 4.")
    require(all(row["scene"] == "takeout" for row in nutrition_groups), "Nutrition groups must belong to takeout.")
    nutrition_ids = {row["id"] for row in nutrition_groups}
    require(not any(row["groupId"] in nutrition_ids for row in items), "The screenshot supplies no nutrition dishes, notes, or historical counts.")
    require(all(row["name"] + row["notes"] == row["originalText"] for row in items), "Name/notes splitting changed original text.")

    for filename, scene, expected_count in DOCUMENTS:
        original = document_groups(args.source_dir / filename)
        scene_groups = sorted((group for group in original_groups if group["scene"] == scene), key=lambda group: group["position"])
        actual = [(group["name"], [row["originalText"] for row in sorted((row for row in items if row["groupId"] == group["id"]), key=lambda row: row["position"])]) for group in scene_groups]
        require(original == actual, f"Text, classification, or order differs from {filename}.")
        actual_count = sum(len(lines) for _, lines in actual)
        require(actual_count == expected_count, f"Unexpected number of entries in {filename}.")
        print(f"PASS {filename}: {len(actual)} groups, {actual_count} entries; exact text and order")
    print("PASS nutrition: 5 screenshot-confirmed labels in clockwise order; no invented child entries")
    print("PASS 18 groups, 61 original entries, unique IDs, zero initial counts, and intact notes")


if __name__ == "__main__":
    try:
        main()
    except (OSError, ValueError, KeyError) as error:
        print(f"FAIL {error}", file=sys.stderr)
        sys.exit(1)
