"""Binary executable-unit format emitted by raw Malbolge programs.

MBP1 is deliberately not a semantic document.  Its payload is compact generic
hook bytecode.  The build compiler verifies it and emits JVM bytecode; Android
never interprets MBP1.
"""

from __future__ import annotations

import hashlib
import struct
from dataclasses import dataclass


MAGIC = b"MBP1"
FRAME = b"MBX2|"

OPCODES = {
    "ARG": 1,
    "THIS": 2,
    "PROCEED": 3,
    "CONST_NULL": 4,
    "CONST_INT": 5,
    "MEMBER_GET": 7,
    "MEMBER_CALL": 8,
    "SET_VISIBILITY": 9,
    "RETURN": 10,
    "DROP": 11,
    "DUP": 12,
    "STRING_CONTAINS": 13,
    "JUMP": 14,
    "JUMP_FALSE": 15,
    "LONG_ADD": 16,
    "LOG": 17,
    "TRUTH": 18,
    "CONST_LONG": 19,
    "JUMP_NULL": 20,
    "UI_APPLY": 21,
    "LOCAL_GET": 25,
    "LOCAL_SET": 26,
    "STATE_GET": 27,
    "STATE_SET": 28,
    "ARRAY_LENGTH": 29,
    "LONG_ARRAY_GET": 30,
    "LONG_ARRAY_SET": 31,
    "INT_ADD": 32,
    "INT_LT": 33,
    "LONG_LT": 34,
    "LONG_GE": 35,
    "LONG_EQ": 36,
    "CONFIG_DYNAMIC": 37,
    "CALL_LONG_PORT": 38,
    "BOUND_FIELD_GET": 39,
    "STRING_LENGTH": 40,
    "INT_EQ": 41,
    "OBJECT_EQUALS": 42,
    "NEW_LONG_ARRAY": 43,
    "ASYNC_LOAD": 44,
    "ARG_LONG": 45,
    "INT_VALUE": 46,
    "LONG_VALUE": 47,
    "INT_LOCAL_GET": 48,
    "INT_LOCAL_SET": 49,
    "LONG_LOCAL_GET": 50,
    "LONG_LOCAL_SET": 51,
    "LONG_ADD_VALUE": 52,
    "STATE_LONG_ARRAY": 53,
}
OPCODE_NAMES = {value: key for key, value in OPCODES.items()}
OPERANDS = {
    OPCODES["ARG"]: "B",
    OPCODES["CONST_INT"]: ">i",
    OPCODES["MEMBER_GET"]: ">H",
    OPCODES["MEMBER_CALL"]: ">HB",
    OPCODES["SET_VISIBILITY"]: ">i",
    OPCODES["STRING_CONTAINS"]: ">H",
    OPCODES["JUMP"]: ">h",
    OPCODES["JUMP_FALSE"]: ">h",
    OPCODES["LOG"]: ">H",
    OPCODES["CONST_LONG"]: ">q",
    OPCODES["JUMP_NULL"]: ">h",
    OPCODES["LOCAL_GET"]: "B",
    OPCODES["LOCAL_SET"]: "B",
    OPCODES["STATE_GET"]: "B",
    OPCODES["STATE_SET"]: "B",
    OPCODES["BOUND_FIELD_GET"]: ">HHB",
    OPCODES["ARG_LONG"]: "B",
    OPCODES["INT_VALUE"]: ">i",
    OPCODES["LONG_VALUE"]: ">q",
    OPCODES["INT_LOCAL_GET"]: "B",
    OPCODES["INT_LOCAL_SET"]: "B",
    OPCODES["LONG_LOCAL_GET"]: "B",
    OPCODES["LONG_LOCAL_SET"]: "B",
    OPCODES["STATE_LONG_ARRAY"]: "B",
}


class ProgramError(ValueError):
    pass


@dataclass(frozen=True)
class Program:
    unit_id: str
    endpoint_id: str
    group_id: str
    config_id: str | None
    startup: int
    fail_open: bool
    constants: tuple[str, ...]
    members: tuple[str, ...]
    code: bytes
    body_sha256: str


def _take(payload: bytes, at: int, size: int) -> tuple[bytes, int]:
    end = at + size
    if end > len(payload):
        raise ProgramError("truncated MBP1 payload")
    return payload[at:end], end


def _text(payload: bytes, at: int) -> tuple[str, int]:
    raw, at = _take(payload, at, 1)
    value, at = _take(payload, at, raw[0])
    try:
        text = value.decode("ascii")
    except UnicodeDecodeError as error:
        raise ProgramError("MBP1 text must be ASCII") from error
    if not text:
        raise ProgramError("empty MBP1 text")
    return text, at


def parse_program(output: bytes) -> Program:
    if not output.startswith(FRAME):
        raise ProgramError("invalid MBX2 frame magic")
    first = output.find(b"|", len(FRAME))
    if first < 0:
        raise ProgramError("missing MBX2 payload delimiter")
    size_text = output[len(FRAME):first]
    if not size_text.isdigit() or (size_text.startswith(b"0") and size_text != b"0"):
        raise ProgramError("invalid MBX2 payload length")
    size = int(size_text)
    start = first + 1
    end = start + size * 2
    if end + 65 != len(output) or output[end:end + 1] != b"|":
        raise ProgramError("MBX2 payload length mismatch")
    try:
        payload = bytes.fromhex(output[start:end].decode("ascii"))
    except (UnicodeDecodeError, ValueError) as error:
        raise ProgramError("invalid MBX2 payload hex") from error
    digest = output[end + 1:].decode("ascii", errors="strict")
    actual = hashlib.sha256(payload).hexdigest()
    if digest != actual:
        raise ProgramError("MBX2 payload digest mismatch")
    if not payload.startswith(MAGIC):
        raise ProgramError("invalid MBP1 payload magic")
    at = len(MAGIC)
    unit_id, at = _text(payload, at)
    endpoint_id, at = _text(payload, at)
    group_id, at = _text(payload, at)
    config_id, at = _text(payload, at)
    config = None if config_id == "-" else config_id
    raw, at = _take(payload, at, 1)
    startup = raw[0]
    if startup not in (0, 1, 2):
        raise ProgramError("invalid startup class")
    raw, at = _take(payload, at, 1)
    if raw[0] not in (0, 1):
        raise ProgramError("invalid failure policy")
    fail_open = bool(raw[0])
    raw, at = _take(payload, at, 2)
    count = struct.unpack(">H", raw)[0]
    constants = []
    for _ in range(count):
        text, at = _text(payload, at)
        constants.append(text)
    raw, at = _take(payload, at, 2)
    member_count = struct.unpack(">H", raw)[0]
    members = []
    for _ in range(member_count):
        text, at = _text(payload, at)
        members.append(text)
    raw, at = _take(payload, at, 2)
    code_size = struct.unpack(">H", raw)[0]
    code, at = _take(payload, at, code_size)
    if at != len(payload):
        raise ProgramError("trailing MBP1 bytes")
    validate_code(code, len(constants), len(members))
    return Program(unit_id, endpoint_id, group_id, config, startup, fail_open,
                   tuple(constants), tuple(members), code, actual)


def validate_code(code: bytes, constant_count: int, member_count: int) -> None:
    at = 0
    instructions = 0
    decoded = {}
    while at < len(code):
        start = at
        opcode = code[at]
        at += 1
        instructions += 1
        spec = OPERANDS.get(opcode)
        if opcode not in OPCODE_NAMES:
            raise ProgramError(f"unknown MBP1 opcode {opcode}")
        if spec:
            size = struct.calcsize(spec)
            raw, at = _take(code, at, size)
            values = struct.unpack(spec, raw)
            if opcode in (OPCODES["STRING_CONTAINS"], OPCODES["LOG"]) and values[0] >= constant_count:
                raise ProgramError("constant index out of range")
            if opcode == OPCODES["BOUND_FIELD_GET"] and (values[0] >= constant_count or values[1] >= constant_count):
                raise ProgramError("constant index out of range")
            if opcode in (OPCODES["MEMBER_GET"], OPCODES["MEMBER_CALL"]) and values[0] >= member_count:
                raise ProgramError("member index out of range")
        else:
            values = ()
        decoded[start] = (at, opcode, values)
    if not instructions or code[-1] != OPCODES["RETURN"]:
        raise ProgramError("program must end in RETURN")
    boundaries = set(decoded) | {len(code)}
    conditional = {OPCODES["JUMP_FALSE"], OPCODES["JUMP_NULL"]}
    branches = {OPCODES["JUMP"], *conditional}
    for _start, (after, opcode, values) in decoded.items():
        if opcode in branches and after + values[0] not in boundaries:
            raise ProgramError("branch target is not an instruction boundary")

    effects = {
        OPCODES["ARG"]: (0, 1), OPCODES["THIS"]: (0, 1), OPCODES["PROCEED"]: (0, 1),
        OPCODES["CONST_NULL"]: (0, 1), OPCODES["CONST_INT"]: (0, 1),
        OPCODES["MEMBER_GET"]: (1, 0), OPCODES["SET_VISIBILITY"]: (1, -1),
        OPCODES["RETURN"]: (1, -1), OPCODES["DROP"]: (1, -1), OPCODES["DUP"]: (1, 1),
        OPCODES["STRING_CONTAINS"]: (1, 0), OPCODES["JUMP"]: (0, 0),
        OPCODES["JUMP_FALSE"]: (1, -1), OPCODES["LONG_ADD"]: (2, -1),
        OPCODES["LOG"]: (0, 0), OPCODES["TRUTH"]: (1, 0), OPCODES["CONST_LONG"]: (0, 1),
        OPCODES["JUMP_NULL"]: (1, -1), OPCODES["UI_APPLY"]: (1, -1),
        OPCODES["LOCAL_GET"]: (0, 1), OPCODES["LOCAL_SET"]: (1, -1),
        OPCODES["STATE_GET"]: (0, 1), OPCODES["STATE_SET"]: (1, -1),
        OPCODES["ARRAY_LENGTH"]: (1, 0), OPCODES["LONG_ARRAY_GET"]: (2, -1),
        OPCODES["LONG_ARRAY_SET"]: (3, -3), OPCODES["INT_ADD"]: (2, -1),
        OPCODES["INT_LT"]: (2, -1), OPCODES["LONG_LT"]: (2, -1),
        OPCODES["LONG_GE"]: (2, -1), OPCODES["LONG_EQ"]: (2, -1),
        OPCODES["CONFIG_DYNAMIC"]: (1, 0), OPCODES["CALL_LONG_PORT"]: (1, 0),
        OPCODES["BOUND_FIELD_GET"]: (1, 0), OPCODES["STRING_LENGTH"]: (1, 0),
        OPCODES["INT_EQ"]: (2, -1), OPCODES["OBJECT_EQUALS"]: (2, -1),
        OPCODES["NEW_LONG_ARRAY"]: (1, 0), OPCODES["ASYNC_LOAD"]: (1, -1),
        OPCODES["ARG_LONG"]: (0, 1), OPCODES["INT_VALUE"]: (0, 1),
        OPCODES["LONG_VALUE"]: (0, 1), OPCODES["INT_LOCAL_GET"]: (0, 1),
        OPCODES["INT_LOCAL_SET"]: (1, -1), OPCODES["LONG_LOCAL_GET"]: (0, 1),
        OPCODES["LONG_LOCAL_SET"]: (1, -1), OPCODES["LONG_ADD_VALUE"]: (2, -1),
        OPCODES["STATE_LONG_ARRAY"]: (0, 1),
    }
    depths = {0: 0}
    pending = [0]
    while pending:
        start = pending.pop()
        after, opcode, values = decoded[start]
        if opcode == OPCODES["MEMBER_CALL"]:
            needed, delta = values[1] + 1, -values[1]
        else:
            needed, delta = effects[opcode]
        depth = depths[start]
        if depth < needed:
            raise ProgramError(f"stack underflow at {start}")
        if opcode == OPCODES["RETURN"] and depth != 1:
            raise ProgramError(f"RETURN requires one stack value at {start}")
        next_depth = depth + delta
        if opcode == OPCODES["RETURN"]:
            successors = []
        elif opcode == OPCODES["JUMP"]:
            successors = [after + values[0]]
        elif opcode in conditional:
            successors = [after, after + values[0]]
        else:
            successors = [after]
        for successor in successors:
            if successor == len(code):
                raise ProgramError("reachable path falls off program")
            previous = depths.get(successor)
            if previous is not None and previous != next_depth:
                raise ProgramError(f"incompatible stack merge at {successor}")
            if previous is None:
                depths[successor] = next_depth
                pending.append(successor)


def code_uses(code: bytes, wanted: int) -> bool:
    at = 0
    while at < len(code):
        opcode = code[at]
        if opcode == wanted:
            return True
        at += 1 + (struct.calcsize(OPERANDS[opcode]) if opcode in OPERANDS else 0)
    return False


def disassemble(program: Program) -> list[str]:
    """Stable human-readable review form; never consumed by the runtime."""
    lines = [
        f"unit {program.unit_id}", f"endpoint {program.endpoint_id}",
        f"group {program.group_id}", f"config {program.config_id or '-'}",
        f"startup {program.startup}", f"fail_open {str(program.fail_open).lower()}",
    ]
    lines += [f"const {index} {value!r}" for index, value in enumerate(program.constants)]
    lines += [f"member {index} {value}" for index, value in enumerate(program.members)]
    at = 0
    while at < len(program.code):
        start = at
        opcode = program.code[at]
        at += 1
        spec = OPERANDS.get(opcode)
        values = ()
        if spec:
            size = struct.calcsize(spec)
            values = struct.unpack(spec, program.code[at:at + size])
            at += size
        suffix = "" if not values else " " + " ".join(map(str, values))
        lines.append(f"{start:04x} {OPCODE_NAMES[opcode]}{suffix}")
    return lines


def encode_program(spec: dict) -> bytes:
    """Encode one assembler object. Used only by the generic source generator."""
    payload = bytearray(MAGIC)

    def put_text(value: str) -> None:
        raw = value.encode("ascii")
        if not 1 <= len(raw) <= 255:
            raise ProgramError("text length outside 1..255")
        payload.append(len(raw))
        payload.extend(raw)

    put_text(spec["unit_id"])
    put_text(spec["endpoint_id"])
    put_text(spec["group_id"])
    put_text(spec.get("config_id") or "-")
    payload.append({"EARLY_REQUIRED": 0, "NORMAL": 1, "LAZY_SAFE": 2}[spec["startup"]])
    payload.append(1 if spec.get("failure_policy", "PROCEED") == "PROCEED" else 0)
    constants = spec.get("constants", [])
    payload.extend(struct.pack(">H", len(constants)))
    for value in constants:
        put_text(value)
    members = spec.get("members", [])
    payload.extend(struct.pack(">H", len(members)))
    for value in members:
        put_text(value)
    instructions = spec["code"]
    labels = {}
    offset = 0
    for instruction in instructions:
        name, *_ = instruction
        if name == "LABEL":
            labels[instruction[1]] = offset
            continue
        opcode = OPCODES[name]
        offset += 1 + (struct.calcsize(OPERANDS[opcode]) if opcode in OPERANDS else 0)
    code = bytearray()
    for instruction in instructions:
        name, *values = instruction
        if name == "LABEL":
            continue
        opcode = OPCODES[name]
        code.append(opcode)
        operand = OPERANDS.get(opcode)
        if operand:
            if opcode in (OPCODES["JUMP"], OPCODES["JUMP_FALSE"], OPCODES["JUMP_NULL"]) and isinstance(values[0], str):
                after = len(code) + struct.calcsize(operand)
                values = [labels[values[0]] - after]
            code.extend(struct.pack(operand, *values))
        elif values:
            raise ProgramError(f"{name} takes no operands")
    payload.extend(struct.pack(">H", len(code)))
    payload.extend(code)
    digest = hashlib.sha256(payload).hexdigest().encode("ascii")
    return FRAME + str(len(payload)).encode("ascii") + b"|" + payload.hex().encode("ascii") + b"|" + digest
