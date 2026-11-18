#!/usr/bin/env python3
"""Strict, read-only LOCAL UDF ZIP/XML shape inventory. Never a native UYAP PASS.

Only fixed vocabulary, numerical counts and broad categories are printed.
No UDF text, filenames, ZIP entry names, attribute values, or hashes.
Even aggregate metadata needs operator authorization before external sharing.
"""
from __future__ import annotations

import argparse
from io import BytesIO
import json
from pathlib import Path
import stat
import sys
import xml.etree.ElementTree as ET
import zipfile

MAX_ARCHIVE = 64 * 1024 * 1024
MAX_ENTRIES = 2048
MAX_UNCOMPRESSED = 32 * 1024 * 1024
MAX_XML = 4 * 1024 * 1024
MAX_ELEMENTS = 120000
MAX_DEPTH = 128
KNOWN_TAGS = ("template", "content", "elements", "paragraph", "styles",
              "style", "properties", "table", "image")
KNOWN_ATTRS = frozenset(("format_id", "startOffset", "length", "resolver",
                         "size", "bold", "foreground", "name", "ref",
                         "width", "height"))
CATEGORIES = ("xml", "png", "other_image", "other")


class Refused(ValueError):
    """Fixed error code only; never expose untrusted input text."""


def empty_report():
    return dict(
        schema="embasic-udf-local-shape-v1", status="BLOCKED",
        native_uyap="NOT_VERIFIED", all_versions="NOT_VERIFIED",
        export_quality="NOT_VERIFIED", release="NO_GO",
        filenames_recorded=False, document_text_recorded=False,
        network_upload="NONE_IN_TOOL", root_class="UNDETERMINED",
        version_class="UNDETERMINED", namespace_present=False,
        known_elements=dict.fromkeys(KNOWN_TAGS, 0),
        unknown_elements=0, unknown_attributes=0,
        member_types=dict.fromkeys(CATEGORIES, 0),
    )


def check_source(path):
    if path.suffix.lower() != ".udf":
        raise Refused("NOT_UDF_EXTENSION")
    absolute = path.absolute()
    if any(node.is_symlink() for node in (absolute, *absolute.parents)):
        raise Refused("SYMLINK_PATH")
    try:
        info = absolute.stat()
    except OSError as exc:
        raise Refused("UNREADABLE_INPUT") from exc
    if not stat.S_ISREG(info.st_mode) or info.st_size <= 0:
        raise Refused("NOT_REGULAR_NONEMPTY")
    if info.st_size > MAX_ARCHIVE:
        raise Refused("COMPRESSED_LIMIT")


def validate_entry(name):
    if not name or "\\" in name or "\x00" in name or name.startswith("/"):
        raise Refused("UNSAFE_ZIP_PATH")
    if len(name) > 1 and name[1] == ":":
        raise Refused("UNSAFE_ZIP_PATH")
    name = name[:-1] if name.endswith("/") else name
    if any(part in ("", ".", "..") for part in name.split("/")):
        raise Refused("UNSAFE_ZIP_PATH")


def member_category(name):
    lower = name.lower()
    if lower.endswith(".xml"):
        return "xml"
    if lower.endswith(".png"):
        return "png"
    if lower.endswith((".jpg", ".jpeg", ".gif", ".bmp", ".tif", ".tiff")):
        return "other_image"
    return "other"


def check_xml(data, report):
    # No network or external XML entities. Cap both decompression and tree work.
    if b"<!doctype" in data.lower() or b"<!entity" in data.lower():
        raise Refused("DTD_OR_ENTITY_NOT_ALLOWED")
    try:
        count = 0
        depth = 0
        for action, element in ET.iterparse(BytesIO(data), events=("start", "end")):
            if action == "end":
                depth -= 1
                element.clear()
                continue
            count += 1
            depth += 1
            if count > MAX_ELEMENTS or depth > MAX_DEPTH:
                raise Refused("XML_COMPLEXITY_LIMIT")
            raw = element.tag
            if not isinstance(raw, str):
                raise Refused("UNKNOWN_XML_TAG")
            has_namespace = raw.startswith("{")
            report["namespace_present"] |= has_namespace
            local = raw.split("}", 1)[-1] if has_namespace else raw
            if count == 1:
                report["root_class"] = "TEMPLATE" if local == "template" else "OTHER"
                value = element.attrib.get("format_id")
                report["version_class"] = ("SYNTHETIC_1_0" if value == "1.0"
                                           else "MISSING" if value is None
                                           else "OTHER")
            if local in report["known_elements"]:
                report["known_elements"][local] += 1
            else:
                report["unknown_elements"] += 1
            for attr in element.attrib:
                report["namespace_present"] |= attr.startswith("{")
                if attr not in KNOWN_ATTRS:
                    report["unknown_attributes"] += 1
        if count == 0 or depth != 0:
            raise Refused("INVALID_XML")
    except (ET.ParseError, UnicodeError, RecursionError, OverflowError) as exc:
        raise Refused("UNREADABLE_XML") from exc


def inventory(path):
    report = empty_report()
    try:
        check_source(path)
        try:
            with zipfile.ZipFile(path) as archive:
                infos = archive.infolist()
                if not 1 <= len(infos) <= MAX_ENTRIES:
                    raise Refused("ENTRY_COUNT_LIMIT")
                unique = set()
                total = 0
                content = None
                for info in infos:
                    validate_entry(info.filename)
                    # Inventory must never follow archive links or silently
                    # accept encrypted/unsupported member encodings.
                    unix_mode = (info.external_attr >> 16) & 0xffff
                    if info.create_system == 3:
                        kind = stat.S_IFMT(unix_mode)
                        if kind == stat.S_IFLNK:
                            raise Refused("ZIP_LINK_MEMBER")
                        if kind not in (0, stat.S_IFREG, stat.S_IFDIR):
                            raise Refused("ZIP_SPECIAL_MEMBER")
                        if kind == stat.S_IFDIR and not info.is_dir():
                            raise Refused("ZIP_TYPE_MISMATCH")
                        if kind == stat.S_IFREG and info.is_dir():
                            raise Refused("ZIP_TYPE_MISMATCH")
                    if info.flag_bits & 0x1:
                        raise Refused("ENCRYPTED_MEMBER")
                    if info.compress_type not in (zipfile.ZIP_STORED, zipfile.ZIP_DEFLATED):
                        raise Refused("UNSUPPORTED_COMPRESSION")
                    key = info.filename.casefold()
                    if key in unique:
                        raise Refused("DUPLICATE_MEMBER")
                    unique.add(key)
                    if info.file_size < 0:
                        raise Refused("UNKNOWN_UNCOMPRESSED_SIZE")
                    total += info.file_size
                    if total > MAX_UNCOMPRESSED:
                        raise Refused("UNCOMPRESSED_LIMIT")
                    if not info.is_dir():
                        report["member_types"][member_category(info.filename)] += 1
                    if key == "content.xml" and not info.is_dir():
                        if info.file_size > MAX_XML:
                            raise Refused("XML_SIZE_LIMIT")
                        content = info
                report["entry_count"] = len(infos)
                report["uncompressed_size_class"] = (
                    "SMALL" if total <= 1024 * 1024 else
                    "MEDIUM" if total <= 8 * 1024 * 1024 else "LARGE"
                )
                if content is None:
                    raise Refused("CONTENT_XML_NOT_FOUND")
                with archive.open(content) as stream:
                    data = stream.read(MAX_XML + 1)
                if not data or len(data) > MAX_XML:
                    raise Refused("XML_SIZE_LIMIT")
                check_xml(data, report)
            report["status"] = "SHAPE_ONLY_REVIEW_REQUIRED"
            report["code"] = "STRUCTURE_INSPECTED_NOT_SUPPORTED"
        except (zipfile.BadZipFile, zipfile.LargeZipFile, EOFError, OSError, RuntimeError, NotImplementedError) as exc:
            raise Refused("INVALID_ZIP") from exc
    except Refused as exc:
        report["code"] = str(exc)
    return report


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--udf", type=Path, required=True)
    path = parser.parse_args().udf
    result = inventory(path)
    print(json.dumps(result, ensure_ascii=True, sort_keys=True, separators=(",", ":")))
    return 0 if result["status"] == "SHAPE_ONLY_REVIEW_REQUIRED" else 3


if __name__ == "__main__":
    sys.exit(main())
