import sys
import json
import unittest
from dataclasses import replace
from pathlib import Path

ROOT = Path(__file__).resolve().parents[2]
sys.path.insert(0, str(ROOT / "toolchain"))

from mbx_eval import BudgetError, SourceError, evaluate
from mbx_program import OPCODES, ProgramError, code_uses, parse_program
from validate_units import ModelError, evaluate_all, validate, validate_programs


class ExecutableSourceTest(unittest.TestCase):
    def setUp(self):
        self.path = ROOT / "source/70_features/hide_ads_premium_offer.mal"
        self.source = self.path.read_bytes()
        self.program = parse_program(evaluate(self.source))

    def test_program_is_typed_executable_bytecode(self):
        self.assertEqual(self.program.unit_id, "hide.ads.premium_offer")
        self.assertEqual(self.program.endpoint_id, "premium_offer_visibility")
        self.assertEqual(self.program.config_id, "hide_youtube_premium_promotions")
        self.assertTrue(code_uses(self.program.code, OPCODES["SET_VISIBILITY"]))
        self.assertEqual(self.program.code[-2:], bytes((OPCODES["PROCEED"], OPCODES["RETURN"])))

    def test_every_behavior_source_is_mbp1_not_json(self):
        areas = ("30_config", "40_manager", "50_diag", "60_resolver", "70_features")
        for path in (path for area in areas for path in (ROOT / "source" / area).glob("*.mal")):
            output = evaluate(path.read_bytes())
            self.assertTrue(output.startswith(b"MBX2|"), path)
            self.assertNotIn(b"handler_function", output)
            self.assertNotIn(b'"kind"', output)
            parse_program(output)

    def test_tamper_and_budgets_fail(self):
        output = evaluate(self.source)
        changed = output[:-1] + (b"0" if output[-1:] != b"0" else b"1")
        with self.assertRaises(ProgramError): parse_program(changed)
        with self.assertRaises(BudgetError): evaluate(self.source, max_steps=1)
        with self.assertRaises(BudgetError): evaluate(self.source, max_output_bytes=1)
        with self.assertRaises(SourceError): evaluate(b"\xff\xff")

    def test_graph_and_program_ownership(self):
        frames, programs = evaluate_all()
        graph = validate(frames)
        validate_programs(programs, graph)
        self.assertEqual((len(graph["units"]), len(graph["records"]), len(programs)), (19, 60, 14))
        self.assertFalse(any(r["kind"] in {"Feature", "Effect", "SegmentSource"} for r in graph["records"]))
        self.assertEqual(len({p.endpoint_id for p in programs if p.endpoint_id != "-"}), 11)


class AotBehaviorTest(unittest.TestCase):
    @classmethod
    def setUpClass(cls):
        frames, cls.programs = evaluate_all()
        cls.graph = validate(frames)

    def program(self, unit_id):
        return next(p for p in self.programs if p.unit_id == unit_id)

    def test_shorts_is_program_owned(self):
        for unit in ("hide.ads.shorts_primary", "hide.ads.shorts_secondary"):
            program = self.program(unit)
            self.assertEqual(program.members, ("shorts_ad_predicate",))
            self.assertTrue(code_uses(program.code, OPCODES["MEMBER_CALL"]))
            self.assertTrue(code_uses(program.code, OPCODES["LONG_ADD"]))

    def test_feed_patterns_live_only_in_program_constants(self):
        program = self.program("hide.ads.sponsored_feed")
        self.assertIn("carousel_ad", program.constants)
        self.assertTrue(code_uses(program.code, OPCODES["STRING_CONTAINS"]))
        backend = "\n".join(p.read_text(encoding="utf-8") for p in (ROOT / "toolchain/backend").glob("*.java"))
        self.assertNotIn("carousel_ad", backend)

    def test_range_program_owns_hot_decision(self):
        capture = self.program("sponsorblock.capture")
        stage = self.program("sponsorblock.stage")
        progress = self.program("sponsorblock.progress")
        self.assertTrue(code_uses(capture.code, OPCODES["STATE_SET"]))
        for operation in ("BOUND_FIELD_GET", "STRING_LENGTH", "OBJECT_EQUALS",
                          "STATE_SET", "NEW_LONG_ARRAY", "ASYNC_LOAD"):
            self.assertTrue(code_uses(stage.code, OPCODES[operation]), operation)
        for operation in ("LONG_ARRAY_GET", "LONG_ARRAY_SET", "LONG_LT", "LONG_GE",
                          "CONFIG_DYNAMIC", "CALL_LONG_PORT"):
            self.assertTrue(code_uses(progress.code, OPCODES[operation]), operation)
        self.assertNotIn("RANGE_PROGRESS", OPCODES)
        backend = (ROOT / "toolchain/backend/GenerateModule.java").read_text()
        self.assertNotIn("SEGMENT_END_MARGIN_MS", backend)
        self.assertNotIn("onProgress", backend)
        self.assertEqual(stage.constants[2], "https://sponsor.ajay.app")
        self.assertEqual(stage.constants[21::2][0], "sponsor")
        self.assertEqual(len(stage.constants[21::2]), 9)

    def test_resolver_scoring_weights_are_malbolge_owned(self):
        policy = self.program("resolver.policy")
        self.assertEqual(tuple(map(int, policy.constants)), (100, 30, 20, 20, 20, 40, 60))

    def test_settings_program_owns_injection(self):
        program = self.program("settings.entry")
        self.assertTrue(code_uses(program.code, OPCODES["UI_APPLY"]))
        self.assertEqual(len(program.members), 14)
        configs = set(self.program("config.policy").constants[1::3])
        self.assertEqual(program.constants[0:6], ("ytmalbolge", "YouTube Malbolge", "-1",
                                                 "Configure ads and SponsorBlock", "module",
                                                 "preference_with_icon"))
        self.assertEqual(configs, set(program.constants) & configs)

    def test_diagnostics_policy_and_group_isolation_are_malbolge_driven(self):
        policy = self.program("status.policy")
        self.assertEqual(policy.constants[10:13],
                         ("degraded", "One or more hook groups disabled", "failed"))
        backend = (ROOT / "toolchain/backend/UiModelGenerator.java").read_text()
        self.assertIn('m.visitLdcInsn("groups")', backend)
        self.assertIn('h.configIndex() == item.configIndex()', backend)

    def test_plan_has_only_generic_mbp_hooks(self):
        from build_development import plan_lines
        lines = plan_lines(self.graph["records"], "0" * 64, self.programs)
        hooks = [line for line in lines if line[0] == "hook"]
        self.assertEqual(len(hooks), 11)
        self.assertTrue(all(line[11] == "MBP1" for line in hooks))
        phases = {}
        for line in hooks:
            phases.setdefault(line[10], set()).add(line[12])
        self.assertTrue(all(len(values) == 1 for values in phases.values()))
        self.assertFalse(any("handler" in field for line in hooks for field in line))
        self.assertEqual(len([line for line in lines if line[0] == "category"]), 9)
        self.assertEqual(len([line for line in lines if line[0] == "item"]), 13)

    def test_new_program_needs_no_dispatch_registration(self):
        from build_development import plan_lines
        base = self.program("hide.ads.premium_offer")
        synthetic = replace(base, unit_id="acceptance.synthetic")
        programs = [p for p in self.programs if p.endpoint_id != synthetic.endpoint_id] + [synthetic]
        lines = plan_lines(self.graph["records"], "0" * 64, programs)
        self.assertTrue(any(line[0] == "hook" and line[9] == "ytm.premium_offer_visibility.v2"
                            and line[10] == "premium_offers" for line in lines))


class ArchitectureTest(unittest.TestCase):
    def test_purity_gate(self):
        from check_purity import check
        check()

    def test_runtime_resolver_has_cache_and_single_bridge_lifecycle(self):
        source = (ROOT / "app/src/main/java/io/github/mrxsin/ytmalbolge/RuntimeResolver.java").read_text()
        self.assertIn("ytm-resolution-v3.properties", source)
        self.assertIn("DexKitBridge.create", source)
        self.assertIn("bridge.close()", source)
        self.assertIn("splitSourceDirs", source)
        self.assertIn("protoShorty", source)

    def test_generated_entry_owns_api102_hot_reload(self):
        source = (ROOT / "toolchain/backend/GenerateModule.java").read_text()
        self.assertIn('"onHotReloading"', source)
        self.assertIn('"onHotReloaded"', source)
        self.assertIn('"getOldHookHandles"', source)
        self.assertIn('"unregisterOnSharedPreferenceChangeListener"', source)
        self.assertIn('"uninstall"', source)
        self.assertIn('"autoHotReload=true\\n"',
                      (ROOT / "toolchain/build_development.py").read_text())

    def test_missing_program_reference_is_rejected(self):
        frames, programs = evaluate_all()
        graph = validate(frames)
        program = replace(programs[0], members=("missing_endpoint",))
        with self.assertRaises(ModelError): validate_programs([program], graph)

    def test_resolver_candidate_vectors(self):
        vectors = json.loads((ROOT / "testdata/resolver-vectors/candidates.json").read_text())
        weights = vectors["policy"][:-1]
        minimum = vectors["policy"][-1]
        for case in vectors["cases"]:
            scores = [sum(weight for weight, matched in zip(weights, candidate) if matched)
                      for candidate in case["candidates"]]
            winner = None
            if scores:
                best = max(scores)
                if best >= minimum and scores.count(best) == 1:
                    winner = scores.index(best)
            if not case.get("hard_valid", True):
                winner = None
            self.assertEqual(winner, case["winner"], case["name"])
        for case in vectors["cache"]:
            uses_discovery = not (case["identity_matches"] and case["descriptor_valid"])
            self.assertEqual(uses_discovery, case["uses_discovery"], case["name"])


if __name__ == "__main__":
    unittest.main()
