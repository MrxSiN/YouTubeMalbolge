"""Reject conventional-code feature policy and legacy dispatch architecture."""

from __future__ import annotations

import ast
from pathlib import Path

from mbx_eval import evaluate
from mbx_program import parse_program

ROOT = Path(__file__).resolve().parents[1]
CODE = [ROOT / "toolchain/build_development.py", ROOT / "toolchain/validate_units.py"]
CODE += sorted((ROOT / "toolchain/backend").glob("*.java"))
CODE += sorted((ROOT / "app/src/main/java").rglob("*.java"))

BANNED = {
    "handler_function", "HANDLERS", "filter_litho_ads", "filter_shorts_ads",
    "capture_receiver", "observe_video_id", "skip_segments", "inject_settings_entry",
    "LithoAdGenerator", "ShortsFeedGenerator", "SettingsGenerator", "DiagnosticsGenerator",
    "carousel_ad", "sponsor.ajay.app", "sponsorblock_skip_sponsor",
    "/api/skipSegments", "\"videoID\"", "SEGMENT_END_MARGIN_MS", "RANGE_PROGRESS",
    "STATE_CAPTURE", "sponsorblock_", "hide_video_ads", "hide_ad_attribution",
    "hide_youtube_premium_promotions", "premium_offer", "shorts_ad", "litho_ad",
}


def check() -> None:
    violations = []
    for path in CODE:
        text = path.read_text(encoding="utf-8")
        for token in BANNED:
            if token in text:
                violations.append(f"{path.relative_to(ROOT)}: forbidden {token}")
        if path.suffix == ".py":
            tree = ast.parse(text, path.as_posix())
            for node in ast.walk(tree):
                if isinstance(node, ast.Dict) and len(node.keys) > 2:
                    keys = {k.value for k in node.keys if isinstance(k, ast.Constant) and isinstance(k.value, str)}
                    if keys & {"handler", "feature", "effect"}:
                        violations.append(f"{path.relative_to(ROOT)}:{node.lineno}: semantic dispatch table")
    behavior_sources = [ROOT / area for area in ("source/30_config", "source/40_manager",
                                                  "source/50_diag", "source/60_resolver",
                                                  "source/70_features")]
    for path in sorted(path for area in behavior_sources for path in area.glob("*.mal")):
        try:
            parse_program(evaluate(path.read_bytes()))
        except Exception as error:
            violations.append(f"{path.relative_to(ROOT)}: not executable MBP1: {error}")
    if violations:
        raise SystemExit("purity violations:\n" + "\n".join(violations))


if __name__ == "__main__":
    check()
