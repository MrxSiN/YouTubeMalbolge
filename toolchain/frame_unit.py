"""Frame a unit's typed records as the exact MBX1 output a Malbolge unit must print.

The framed text is the target fed to the pinned zb3/malbolge-tools linear generator,
for example: ``python frame_unit.py unit.json | node cli/gen-linear.js unit.mal``.
The generated source is then accepted only after ``validate_units.py`` re-evaluates it.
"""

from __future__ import annotations

import argparse
import hashlib
import json
import sys
from pathlib import Path

from mbx_frame import parse_frame


def frame(unit: dict) -> bytes:
    body = json.dumps(unit, sort_keys=True, separators=(",", ":")).encode("ascii")
    if b"\\" in body:
        raise ValueError("backslash is a generator escape and cannot appear in a unit")
    output = b"MBX1|%d|%s|%s" % (len(body), body, hashlib.sha256(body).hexdigest().encode("ascii"))
    parse_frame(output)
    return output


def main() -> None:
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("unit", type=Path, help="JSON object with unit_id, imports, exports and records")
    args = parser.parse_args()
    sys.stdout.buffer.write(frame(json.loads(args.unit.read_text(encoding="utf-8"))))


if __name__ == "__main__":
    main()
