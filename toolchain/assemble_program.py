"""Build a real raw Malbolge program from one temporary MBP1 assembler object."""

from __future__ import annotations

import argparse
import json
import subprocess
from pathlib import Path

from mbx_program import encode_program, parse_program


def main() -> None:
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("spec", type=Path)
    parser.add_argument("output", type=Path)
    parser.add_argument("--generator", type=Path, required=True)
    args = parser.parse_args()
    framed = encode_program(json.loads(args.spec.read_text(encoding="utf-8")))
    args.output.parent.mkdir(parents=True, exist_ok=True)
    subprocess.run(
        ["node", str(args.generator), "-pool", "o" * 64, str(args.output)],
        input=framed,
        check=True,
    )
    # Independent project evaluator verification happens in validate_units.py;
    # this local round-trip catches generator transport errors immediately.
    from mbx_eval import evaluate

    parse_program(evaluate(args.output.read_bytes()))


if __name__ == "__main__":
    main()
