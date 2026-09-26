import hashlib
import sys
import unittest
from pathlib import Path

ROOT = Path(__file__).resolve().parents[2]
sys.path.insert(0, str(ROOT / "toolchain"))

from mbx_eval import BudgetError, SourceError, evaluate
from mbx_frame import FrameError, parse_frame
from validate_units import ModelError, evaluate_units, validate


class FeatureUnitTest(unittest.TestCase):
    def setUp(self):
        self.source = (ROOT / "source/70_features/hide_ads_premium_offer.mal").read_bytes()
        self.output = evaluate(self.source)

    def test_feature_unit_is_valid_and_typed(self):
        frame = parse_frame(self.output)
        self.assertEqual(frame.unit_id, "hide_ads.premium_offer")
        self.assertEqual(frame.imports, ())
        self.assertEqual(frame.exports, ("hide_ads.premium_offer",))
        self.assertEqual([record["kind"] for record in frame.records], ["Feature", "Effect"])
        self.assertEqual(frame.records[1]["endpoint_id"], "premium_offer_visibility")
        self.assertEqual(frame.records[1]["handler_function"], "hide_view")

    def test_payload_tampering_fails(self):
        changed = self.output.replace(b"hide_view", b"show_view", 1)
        with self.assertRaises(FrameError):
            parse_frame(changed)

    def test_budget_blocks_unbounded_execution(self):
        with self.assertRaises(BudgetError):
            evaluate(self.source, max_steps=1)
        with self.assertRaises(BudgetError):
            evaluate(self.source, max_output_bytes=1)

    def test_invalid_source_rejected(self):
        with self.assertRaises(SourceError):
            evaluate(b"\xff\xff")

    def test_source_hash_is_stable(self):
        self.assertEqual(
            hashlib.sha256(self.source).hexdigest(),
            "99617a01be407dcfb444858c5c5334da6c6f6e31798e6b1b67641eb64b3f8dc8",
        )

    def test_cross_unit_references_and_target_hash(self):
        graph = validate(evaluate_units())
        self.assertEqual(len(graph["units"]), 32)
        self.assertEqual(len(graph["records"]), 94)
        effects = [record for record in graph["records"] if record["kind"] == "Effect"]
        self.assertEqual(
            {record["endpoint_id"] for record in effects},
            {"ad_attribution_visibility", "premium_offer_visibility", "video_ad_loader", "player_ad_layout",
             "player_controller", "video_stage", "playback_progress", "settings_root",
             "sponsored_feed_component", "shorts_item_primary", "shorts_item_secondary"},
        )

        frames = evaluate_units()
        binding = next(frame for frame in frames if frame.unit_id.startswith("binding."))
        binding.records[0]["target_base_sha256"] = "0" * 64
        with self.assertRaises(ModelError):
            validate(frames)


class SponsorBlockUnitTest(unittest.TestCase):
    def setUp(self):
        self.frames = evaluate_units()
        self.feature = next(frame for frame in self.frames if frame.unit_id == "sponsorblock.skip_segments")

    def effect(self, frames, handler):
        unit = next(frame for frame in frames if frame.unit_id == "sponsorblock.skip_segments")
        return next(r for r in unit.records if r.get("handler_function") == handler)

    def test_feature_records(self):
        kinds = [record["kind"] for record in self.feature.records]
        self.assertEqual(kinds, ["Feature", "Effect", "Effect", "Effect", "SegmentSource"])
        source = self.feature.records[4]
        self.assertEqual(source["api_origin"], "https://sponsor.ajay.app")
        self.assertEqual(source["action_type"], "skip")
        self.assertEqual(source["hash_prefix_length"], 4)

    def test_only_sponsor_category_skips_by_default(self):
        configs = {
            record["config_id"]: record["default"]
            for frame in self.frames if frame.unit_id == "config.sponsorblock" for record in frame.records
        }
        self.assertTrue(configs.pop("sponsorblock_enabled"))
        self.assertTrue(configs.pop("sponsorblock_skip_sponsor"))
        self.assertEqual(set(configs.values()), {False})

    def test_seek_endpoint_cannot_be_hooked(self):
        self.effect(self.frames, "capture_receiver")["endpoint_id"] = "player_seek"
        with self.assertRaises(ModelError):
            validate(self.frames)

    def test_segment_source_requires_https(self):
        source = self.feature.records[4]
        source["api_origin"] = "http://sponsor.ajay.app"
        with self.assertRaises(ModelError):
            validate(self.frames)

    def test_plan_lowers_seek_and_categories(self):
        from build_development import plan_lines

        graph = validate(self.frames)
        lines = plan_lines(graph["records"], "0" * 64, True)
        seek = next(line for line in lines if line[0] == "seek")
        self.assertEqual(seek[1:4], ["METHOD", "arwg", "ar"])
        self.assertEqual(seek[-1], "SEEK_SOURCE_UNKNOWN")
        progress = next(line for line in lines if line[0] == "hook" and line[7] == "skip_segments")
        self.assertEqual(progress[1:3], ["CONSTRUCTOR", "aqgu"])
        self.assertEqual(progress[-1], "0")
        stage = next(line for line in lines if line[0] == "hook" and line[7] == "observe_video_id")
        self.assertEqual(stage[1:4] + stage[-1:], ["METHOD", "jlu", "h", "h"])
        segments = next(line for line in lines if line[0] == "segments")
        self.assertTrue(segments[5].startswith("?categories=%5B%22sponsor%22"))
        self.assertEqual(len([line for line in lines if line[0] == "category"]), 9)


class ShortsAdUnitTest(unittest.TestCase):
    def test_filter_uses_bound_feed_and_ad_predicate(self):
        from build_development import plan_lines, verified_bindings

        frames = evaluate_units()
        graph = validate(frames)
        bindings = {r["endpoint_id"]: r for r in graph["records"] if r["kind"] == "BindingSpec"}
        verified_bindings(bindings)
        hooks = [line for line in plan_lines(graph["records"], "0" * 64, True)
                 if line[0] == "hook" and line[7] == "filter_shorts_ads"]
        self.assertEqual(len(hooks), 2)
        self.assertEqual({hook[3] for hook in hooks}, {"H", "I"})
        for hook in hooks:
            self.assertEqual(hook[10:14], ["METHOD", "aqdf", "U", "(Lbcpl;)Z"])

    def test_filter_requires_predicate_role(self):
        frames = evaluate_units()
        feature = next(frame for frame in frames if frame.unit_id == "hide_ads.shorts_ad")
        feature.records[1]["call_roles"].pop("predicate")
        with self.assertRaises(ModelError):
            validate(frames)


class SettingsUnitTest(unittest.TestCase):
    def setUp(self):
        self.frames = evaluate_units()

    def page(self, frames):
        unit = next(frame for frame in frames if frame.unit_id == "manager.settings")
        return unit.records[0]

    def test_page_exposes_every_config_item_once(self):
        page = self.page(self.frames)
        self.assertEqual(page["entry_order"], -1)
        self.assertEqual([s["section_id"] for s in page["sections"]], ["hide_ads", "sponsorblock"])
        exposed = [item["config_id"] for s in page["sections"] for item in s["items"]]
        configs = [r["config_id"] for f in self.frames for r in f.records if r["kind"] == "ConfigItem"]
        self.assertEqual(sorted(exposed), sorted(configs))

    def test_missing_config_item_rejected(self):
        self.page(self.frames)["sections"][0]["items"].pop()
        with self.assertRaises(ModelError):
            validate(self.frames)

    def test_only_settings_entry_may_be_unguarded(self):
        unit = next(frame for frame in self.frames if frame.unit_id == "hide_ads.video_ads")
        unit.records[1]["config_guard"] = None
        with self.assertRaises(ModelError):
            validate(self.frames)

    def test_plan_lowers_settings(self):
        from build_development import plan_lines

        lines = plan_lines(validate(self.frames)["records"], "0" * 64, True)
        hook = next(line for line in lines if line[0] == "hook" and line[7] == "inject_settings_entry")
        self.assertEqual(hook[1:4] + [hook[8]], ["METHOD", "oyj", "run", "-1"])
        roles = {line[1] for line in lines if line[0] == "role"}
        self.assertEqual(len(roles), 14)
        self.assertIn(["layouts", "preference_with_icon"], lines)
        page = next(line for line in lines if line[0] == "page")
        self.assertEqual(page[1:], ["ytmalbolge", "YouTube Malbolge", "-1", "io.github.mrxsin.ytmalbolge",
                                    "Configure ads and SponsorBlock", "module"])
        self.assertEqual(len([line for line in lines if line[0] == "item"]), 13)


if __name__ == "__main__":
    unittest.main()
