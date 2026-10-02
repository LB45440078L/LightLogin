#!/usr/bin/env python3
"""Regenerate the BouncyCastle class filter in lightlogin-paper/pom.xml.

The plugin uses exactly one thing from BouncyCastle: Argon2id. Shipping the whole of `bcprov`
costs ~5.5 MB, so the pom keeps only the classes Argon2 actually reaches at runtime.

The set is a *runtime* closure, not a guess: it is computed from the class files' constant pools
starting at `Argon2BytesGenerator`, and it is verified by
`lightlogin-paper/src/test/java/dev/lightlogin/paper/packaging/ShadedJarIT.java`, which hashes and
verifies a password using only the classes present in the built jar.

Note that the closure is larger than "Argon2 and Blake2b" because BouncyCastle's
`CryptoServicesRegistrar` static initialiser references the ASN.1/EC parameter types; trimming those
out throws `NoClassDefFoundError: org/bouncycastle/asn1/x9/X9ECParameters` at the first hash.

Usage:
    python3 scripts/generate-bouncycastle-filter.py [path/to/bcprov-jdk18on-<version>.jar]

Without an argument it looks for the jar in the local Maven repository.
"""

from __future__ import annotations

import re
import sys
import zipfile
from pathlib import Path

ROOT = Path(__file__).resolve().parent.parent
POM = ROOT / "lightlogin-paper" / "pom.xml"
START = "<!-- BEGIN GENERATED bouncycastle filter -->"
END = "<!-- END GENERATED bouncycastle filter -->"
ROOT_CLASS = "org/bouncycastle/crypto/generators/Argon2BytesGenerator"


def find_jar() -> Path:
    repository = Path.home() / ".m2" / "repository" / "org" / "bouncycastle" / "bcprov-jdk18on"
    candidates = sorted(repository.glob("*/bcprov-jdk18on-*.jar"))
    if not candidates:
        sys.exit(f"no bcprov jar found under {repository}; pass the path explicitly")
    return candidates[-1]


def closure(jar: Path) -> list[str]:
    archive = zipfile.ZipFile(jar)
    classes = {
        name[:-6]: archive.read(name)
        for name in archive.namelist()
        if name.startswith("org/bouncycastle/")
        and name.endswith(".class")
        and "/versions/" not in name
    }
    if ROOT_CLASS not in classes:
        sys.exit(f"{ROOT_CLASS} not present in {jar}")

    reference = re.compile(rb"org/bouncycastle/[A-Za-z0-9_$/]+")
    edges = {
        name: {ref.decode() for ref in reference.findall(data) if ref.decode() in classes}
        for name, data in classes.items()
    }

    seen: set[str] = set()
    stack = [ROOT_CLASS]
    while stack:
        current = stack.pop()
        if current in seen or current not in classes:
            continue
        seen.add(current)
        stack.extend(edges[current])
    return sorted(seen)


def main() -> None:
    jar = Path(sys.argv[1]) if len(sys.argv) > 1 else find_jar()
    names = closure(jar)
    block = "\n".join(
        f"                                        <include>{name}.class</include>" for name in names
    )
    print(f"{jar.name}: {len(names)} classes reachable from {ROOT_CLASS}")

    pom = POM.read_text(encoding="utf-8")
    pattern = re.compile(re.escape(START) + r".*?" + re.escape(END), re.S)
    if not pattern.search(pom):
        sys.exit(f"markers not found in {POM}")
    POM.write_text(pattern.sub(f"{START}\n{block}\n{END}", pom), encoding="utf-8")
    print(f"updated {POM.relative_to(ROOT)}")


if __name__ == "__main__":
    main()
