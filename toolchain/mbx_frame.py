"""Parse deterministic Malbolge unit output into typed build records."""

from __future__ import annotations

import hashlib
import json
from dataclasses import dataclass


class FrameError(ValueError):
    pass


@dataclass(frozen=True)
class UnitFrame:
    unit_id: str
    imports: tuple[str, ...]
    exports: tuple[str, ...]
    records: tuple[dict, ...]
    body_sha256: str


def parse_frame(output: bytes, *, max_records: int = 128) -> UnitFrame:
    if not output.startswith(b"MBX1|"):
        raise FrameError("invalid frame magic")
    first = output.find(b"|", 5)
    if first < 0:
        raise FrameError("missing payload length delimiter")
    size_text = output[5:first]
    if not size_text or (size_text.startswith(b"0") and size_text != b"0"):
        raise FrameError("noncanonical payload length")
    if not all(48 <= digit <= 57 for digit in size_text):
        raise FrameError("invalid payload length")
    size = int(size_text)
    body_start = first + 1
    body_end = body_start + size
    if body_end + 65 != len(output) or output[body_end : body_end + 1] != b"|":
        raise FrameError("payload length mismatch")
    body = output[body_start:body_end]
    digest = output[body_end + 1 :]
    if digest != hashlib.sha256(body).hexdigest().encode("ascii"):
        raise FrameError("payload SHA-256 mismatch")
    try:
        value = json.loads(body.decode("ascii"))
    except (UnicodeDecodeError, json.JSONDecodeError) as error:
        raise FrameError("invalid ASCII JSON payload") from error
    if json.dumps(value, sort_keys=True, separators=(",", ":")).encode("ascii") != body:
        raise FrameError("noncanonical JSON payload")
    if not isinstance(value, dict) or set(value) != {"unit_id", "imports", "exports", "records"}:
        raise FrameError("invalid unit fields")
    unit_id = value["unit_id"]
    imports = value["imports"]
    exports = value["exports"]
    records = value["records"]
    if not isinstance(unit_id, str) or not unit_id:
        raise FrameError("invalid unit ID")
    if not all(
        isinstance(items, list)
        and all(isinstance(item, str) and item for item in items)
        and len(set(items)) == len(items)
        for items in (imports, exports)
    ):
        raise FrameError("invalid import or export IDs")
    if not isinstance(records, list) or not 1 <= len(records) <= max_records:
        raise FrameError("invalid record count")
    if not all(isinstance(record, dict) and isinstance(record.get("kind"), str) for record in records):
        raise FrameError("invalid typed record")
    return UnitFrame(unit_id, tuple(imports), tuple(exports), tuple(records), digest.decode("ascii"))
