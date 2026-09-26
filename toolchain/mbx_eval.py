"""Bounded build-time evaluator for MBX-CLASSIC-REF/1 source units.

The machine operations follow the vendored 1998 interpreter. This tool never ships
inside an Android artifact and never accepts runtime input.
"""

from __future__ import annotations

import argparse
from pathlib import Path

MEMORY_SIZE = 59049
WHITESPACE = frozenset(b"\t\n\v\f\r ")
XLAT1 = (
    b'+b(29e*j1VMEKLyC})8&m#~W>qxdRp0wkrUo[D7,XTcA"lI'
    b".v%{gJh4G\\-=O@5`_3i<?Z';FNQuY]szf$!BS/|t:Pn6^Ha"
)
XLAT2 = (
    b"5z]&gqtyfr$(we4{WP)H-Zn,[%\\3dL+Q;>U!pJS72FhOA1C"
    b'B6v^=I_0/8|jsb9m<.TVac`uY*MK\'X~xDl}REokN:#?G"i@'
)
VALID_INSTRUCTIONS = frozenset(b"ji*p</vo")
POWERS_OF_NINE = (1, 9, 81, 729, 6561)
CRAZY_TABLE = (
    (4, 3, 3, 1, 0, 0, 1, 0, 0),
    (4, 3, 5, 1, 0, 2, 1, 0, 2),
    (5, 5, 4, 2, 2, 1, 2, 2, 1),
    (4, 3, 3, 1, 0, 0, 7, 6, 6),
    (4, 3, 5, 1, 0, 2, 7, 6, 8),
    (5, 5, 4, 2, 2, 1, 8, 8, 7),
    (7, 6, 6, 7, 6, 6, 4, 3, 3),
    (7, 6, 8, 7, 6, 8, 4, 3, 5),
    (8, 8, 7, 8, 8, 7, 5, 5, 4),
)


class SourceError(ValueError):
    pass


class BudgetError(RuntimeError):
    pass


def crazy(x: int, y: int) -> int:
    """Reference op(x, y), with argument order preserved."""
    return sum(
        CRAZY_TABLE[(y // power) % 9][(x // power) % 9] * power
        for power in POWERS_OF_NINE
    )


def load(source: bytes, *, max_source_bytes: int) -> list[int]:
    if len(source) > max_source_bytes:
        raise BudgetError("source byte limit exceeded")
    code = []
    for byte in source:
        if byte in WHITESPACE:
            continue
        if not 33 <= byte <= 126:
            raise SourceError("source must contain printable ASCII or ASCII whitespace")
        if len(code) == MEMORY_SIZE:
            raise SourceError("source exceeds Malbolge memory")
        instruction = XLAT1[(byte - 33 + len(code)) % 94]
        if instruction not in VALID_INSTRUCTIONS:
            raise SourceError("invalid character in source file")
        if instruction == ord("/"):
            raise SourceError("input instruction prohibited in project units")
        code.append(byte)
    if len(code) < 2:
        raise SourceError("at least two logical source words required")
    memory = code + [0] * (MEMORY_SIZE - len(code))
    for index in range(len(code), MEMORY_SIZE):
        memory[index] = crazy(memory[index - 1], memory[index - 2])
    return memory


def evaluate(
    source: bytes,
    *,
    max_source_bytes: int = 59049,
    max_steps: int = 5_000_000,
    max_output_bytes: int = 1_048_576,
) -> bytes:
    memory = load(source, max_source_bytes=max_source_bytes)
    a = c = d = 0
    output = bytearray()
    for _ in range(max_steps):
        value = memory[c]
        if not 33 <= value <= 126:
            continue  # Exact reference behavior: C and D do not advance here.
        instruction = XLAT1[(value - 33 + c) % 94]
        if instruction == ord("j"):
            d = memory[d]
        elif instruction == ord("i"):
            c = memory[d]
        elif instruction == ord("*"):
            a = memory[d] = value = memory[d] // 3 + memory[d] % 3 * 19683
        elif instruction == ord("p"):
            a = memory[d] = crazy(a, memory[d])
        elif instruction == ord("<"):
            if len(output) == max_output_bytes:
                raise BudgetError("output byte limit exceeded")
            output.append(a & 0xFF)
        elif instruction == ord("/"):
            raise SourceError("input instruction prohibited in project units")
        elif instruction == ord("v"):
            return bytes(output)
        memory[c] = XLAT2[memory[c] - 33]
        c = (c + 1) % MEMORY_SIZE
        d = (d + 1) % MEMORY_SIZE
    raise BudgetError("step limit exceeded")


def main() -> None:
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("source", type=Path)
    parser.add_argument("--output", type=Path)
    args = parser.parse_args()
    result = evaluate(args.source.read_bytes())
    if args.output:
        args.output.write_bytes(result)
    else:
        import sys

        sys.stdout.buffer.write(result)


if __name__ == "__main__":
    main()
