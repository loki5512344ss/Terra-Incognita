#!/usr/bin/env python3
"""Builds the dedicated-server pack zip: dist/TerraIncognita-<version>-server.zip

  script/build-server-pack.py [--version 0.1.0] [--offline] [--report mods/server-classification.md]

Mods are taken from mods/modlist.json (downloaded and cached in dist/cache/mods) plus our own jars
(custom_mods/*/build/libs, and loose jars in mods/ such as oc2r). Every mod is classified:

  1. mods/server-excludes.txt, explicit overrides ("exclude <jar-substring>" / "include <jar-substring>");
  2. Modrinth API: the project's server_side field ("unsupported" -> client-only), looked up by the jar sha1;
  3. fallback, the jar's META-INF/neoforge.mods.toml (a dependency on minecraft/neoforge with side = "CLIENT"
     means client-only); anything else is kept, because a client-only mod on a server is harmless in most cases
     while a missing common mod breaks the world.

The evidence for every decision is written to the report file (default mods/server-classification.md).
The zip holds mods/, config/, defaultconfigs/, kubejs/ (server_scripts, startup_scripts, data), start.sh, start.bat,
user_jvm_args.txt, server.properties and README-SERVER.txt. NeoForge itself is installed by the start script
(NeoForge installer --installServer) so the zip stays small. eula.txt is deliberately not created.
"""
import argparse, hashlib, json, re, subprocess, sys, tomllib, urllib.parse, urllib.request, zipfile
from pathlib import Path

ROOT = Path(__file__).resolve().parent.parent
sys.path.insert(0, str(Path(__file__).resolve().parent))
HEADERS = {"User-Agent": "terra-incognita-pack-tools"}
CACHE = ROOT / "dist/cache/mods"
API = "https://api.modrinth.com/v2"
# Flags from docs/perf.md section 4b (G1 for Java 21), heap fixed at 6 GB.
JVM_ARGS = ["-Xms6G", "-Xmx6G", "-XX:+UnlockExperimentalVMOptions", "-XX:+UseG1GC", "-XX:G1NewSizePercent=20",
            "-XX:G1ReservePercent=20", "-XX:MaxGCPauseMillis=50", "-XX:G1HeapRegionSize=32M"]
# config/ entries that only the client reads, besides config files named after a client-only modid.
CLIENT_CONFIG = {"fancymenu", "zakoopt.json"}
# kubejs folders the dedicated server never loads.
KUBEJS_SKIP = {"client_scripts", "assets"}


def default_version():
    try:
        return subprocess.run(["git", "describe", "--tags", "--always", "--dirty"], cwd=ROOT, capture_output=True,
                              text=True, check=True).stdout.strip().lstrip("v")
    except Exception:
        return "dev"


def http(url, data=None, timeout=120):
    req = urllib.request.Request(url, data=data, headers={**HEADERS, **({"Content-Type": "application/json"} if data else {})})
    return urllib.request.urlopen(req, timeout=timeout).read()


def source(mod):
    cf = mod.get("curseforge")
    if cf:
        fid = str(cf["file"])
        return f"https://edge.forgecdn.net/files/{fid[:4]}/{fid[4:].lstrip('0') or '0'}/{mod['file']}", None
    return mod["url"], mod.get("sha1")


def fetch_jar(mod, offline):
    target = CACHE / mod["file"]
    if target.exists() and target.stat().st_size > 0:
        return target
    if offline:
        return None
    url, sha1 = source(mod)
    data = http(url)
    if sha1 and hashlib.sha1(data).hexdigest() != sha1:
        sys.exit(f"sha1 mismatch: {url}")
    CACHE.mkdir(parents=True, exist_ok=True)
    target.write_bytes(data)
    return target


def own_jars():
    """Our jars: custom_mods/*/build/libs/*.jar (no sources/dev jars) and loose jars placed in mods/."""
    found = []
    for p in sorted((ROOT / "custom_mods").glob("*/build/libs/*.jar")) + sorted((ROOT / "mods").glob("*.jar")):
        if not re.search(r"sources|javadoc|dev|slim", p.name):
            found.append(p)
    # keep the newest jar per project folder / name prefix
    latest = {}
    for p in found:
        latest[(p.parent, re.split(r"-\d", p.name)[0])] = p
    return list(latest.values())


def read_toml(jar):
    with zipfile.ZipFile(jar) as z:
        for n in ("META-INF/neoforge.mods.toml", "META-INF/mods.toml"):
            if n in z.namelist():
                return tomllib.loads(z.read(n).decode("utf-8", "replace"))
    return {}


def modids(toml):
    return [m.get("modId") for m in toml.get("mods", []) if m.get("modId")]


def toml_verdict(toml):
    """Returns (verdict, evidence) from neoforge.mods.toml only."""
    if not toml:
        return "both", "no neoforge.mods.toml found (library or unusual jar), kept"
    for deps in (toml.get("dependencies") or {}).values():
        for d in deps if isinstance(deps, list) else []:
            if d.get("modId") in ("minecraft", "neoforge") and str(d.get("side", "BOTH")).upper() == "CLIENT":
                return "client-only", f"mods.toml: dependency on {d['modId']} has side=CLIENT"
    return "both", "mods.toml declares no client-only side, kept (unverified by Modrinth)"


def modrinth_lookup(hashes):
    """sha1 -> (project_id, slug, client_side, server_side); empty dict when the API is unreachable."""
    try:
        versions = json.loads(http(f"{API}/version_files", json.dumps({"hashes": hashes, "algorithm": "sha1"}).encode()))
        pids = sorted({v["project_id"] for v in versions.values()})
        projects = {}
        for i in range(0, len(pids), 50):
            for p in json.loads(http(f"{API}/projects?ids={urllib.parse.quote(json.dumps(pids[i:i + 50]))}")):
                projects[p["id"]] = p
        return {h: (projects[v["project_id"]]["slug"], projects[v["project_id"]]["client_side"],
                    projects[v["project_id"]]["server_side"]) for h, v in versions.items()}
    except Exception as e:
        print(f"warning: Modrinth API unavailable ({e}), using jar metadata only", file=sys.stderr)
        return {}


def load_overrides():
    rules = []
    f = ROOT / "mods/server-excludes.txt"
    if f.exists():
        for line in f.read_text().splitlines():
            line = line.strip()
            if line and not line.startswith("#"):
                kind, _, rest = line.partition(" ")
                pat, _, why = rest.partition("#")
                if kind in ("exclude", "include"):
                    rules.append((kind, pat.strip().lower(), why.strip()))
    return rules


def classify(entries, offline):
    mr = {} if offline else modrinth_lookup([e["sha1"] for e in entries])
    rules = load_overrides()
    for e in entries:
        toml = read_toml(e["path"])
        e["modids"] = modids(toml)
        rule = next((r for r in rules if r[1] in e["file"].lower() or r[1] in [m.lower() for m in e["modids"]]), None)
        if rule:
            e["verdict"] = "client-only" if rule[0] == "exclude" else "both"
            e["evidence"] = f"override ({rule[0]}): {rule[2] or 'mods/server-excludes.txt'}"
            continue
        info = mr.get(e["sha1"])
        if info:
            slug, cs, ss = info
            ev = f"Modrinth {slug}: client_side={cs}, server_side={ss}"
            if ss == "unsupported":
                e["verdict"], e["evidence"] = "client-only", ev
            elif cs == "unsupported":
                e["verdict"], e["evidence"] = "server-only", ev
            else:
                e["verdict"], e["evidence"] = "both", ev
        else:
            e["verdict"], why = toml_verdict(toml)
            e["evidence"] = "not found on Modrinth by sha1; " + why


def write_report(entries, path):
    order = {"client-only": 0, "server-only": 1, "both": 2}
    rows = sorted(entries, key=lambda e: (order[e["verdict"]], e["file"].lower()))
    counts = {k: sum(e["verdict"] == k for e in entries) for k in order}
    out = ["# Server pack classification", "",
           "Generated by script/build-server-pack.py. Client-only mods are left out of the server zip; overrides live in "
           "mods/server-excludes.txt.", "",
           "Summary: " + ", ".join(f"{k} {v}" for k, v in counts.items()), "",
           "| Mod jar | Class | Evidence |", "|---|---|---|"]
    out += [f"| {e['file']} | {e['verdict']} | {e['evidence'].replace('|', '/')} |" for e in rows]
    Path(path).write_text("\n".join(out) + "\n")
    return counts


SERVER_PROPERTIES = """# Template from the Terra Incognita server pack. Edit freely.
view-distance=10
simulation-distance=6
motd=Terra Incognita
max-players=10
difficulty=normal
online-mode=true
enable-command-block=false
sync-chunk-writes=false
"""

START_SH = """#!/usr/bin/env bash
# Starts the Terra Incognita server. Needs Java 21 (java -version).
set -euo pipefail
cd "$(dirname "$0")"
NEOFORGE="{neoforge}"
ARGS="libraries/net/neoforged/neoforge/$NEOFORGE/unix_args.txt"
if [[ ! -f "$ARGS" ]]; then
  echo "Installing NeoForge $NEOFORGE ..."
  curl -fL -o neoforge-installer.jar "https://maven.neoforged.net/releases/net/neoforged/neoforge/$NEOFORGE/neoforge-$NEOFORGE-installer.jar"
  java -jar neoforge-installer.jar --installServer
  rm -f neoforge-installer.jar neoforge-installer.jar.log
fi
if ! grep -qi '^eula=true' eula.txt 2>/dev/null; then
  echo "Read https://aka.ms/MinecraftEULA and, if you agree, put eula=true into eula.txt (created on first start)."
fi
exec java @user_jvm_args.txt @"$ARGS" nogui "$@"
"""

START_BAT = """@echo off
REM Starts the Terra Incognita server. Needs Java 21 (java -version).
cd /d "%~dp0"
set NEOFORGE={neoforge}
set ARGS=libraries\\net\\neoforged\\neoforge\\%NEOFORGE%\\win_args.txt
if not exist "%ARGS%" (
  echo Installing NeoForge %NEOFORGE% ...
  curl -fL -o neoforge-installer.jar https://maven.neoforged.net/releases/net/neoforged/neoforge/%NEOFORGE%/neoforge-%NEOFORGE%-installer.jar || exit /b 1
  java -jar neoforge-installer.jar --installServer || exit /b 1
  del neoforge-installer.jar neoforge-installer.jar.log 2>nul
)
findstr /b /i "eula=true" eula.txt >nul 2>&1 || echo Read https://aka.ms/MinecraftEULA and, if you agree, put eula=true into eula.txt (created on first start).
java @user_jvm_args.txt @%ARGS% nogui %*
pause
"""

README = """Terra Incognita - dedicated server pack {version}
Minecraft {mc}, NeoForge {neoforge}, Java 21 required.

First run
  1. Install Java 21 (Temurin or similar) and make sure `java -version` shows 21.
  2. Run ./start.sh (Linux/macOS) or start.bat (Windows). The first run downloads and installs NeoForge {neoforge}
     (needs internet), then the server starts and stops because the EULA is not accepted.
  3. Read https://aka.ms/MinecraftEULA. If you agree, set eula=true in eula.txt. This pack never sets it for you.
  4. Run the start script again. The first world generation takes a while.

Settings
  - user_jvm_args.txt: heap (-Xms6G -Xmx6G by default, lower it on a small machine) and G1 flags.
  - server.properties: template with view-distance=10 and simulation-distance=6 (the server only uses it when no
    server.properties exists yet; replace the file or edit it before the first start).
  - config/, defaultconfigs/, kubejs/: gameplay configuration and scripts. Do not copy the client's kubejs/client_scripts
    or assets here; the server does not read them.
  - Client-only mods are not included. Players install the matching client pack (same version).

Updating: replace mods/, config/, defaultconfigs/ and kubejs/ with the new zip's content, keep world/, server.properties,
eula.txt and your ops/whitelist files.
"""


def main():
    ap = argparse.ArgumentParser()
    ap.add_argument("--version", default=default_version())
    ap.add_argument("--offline", action="store_true", help="use only cached jars and jar metadata")
    ap.add_argument("--report", default=str(ROOT / "mods/server-classification.md"))
    args = ap.parse_args()

    ml = json.loads((ROOT / "mods/modlist.json").read_text())
    entries = []
    for m in ml["mods"]:
        p = fetch_jar(m, args.offline)
        if p is None:
            sys.exit(f"{m['file']} is not cached and --offline is set")
        entries.append({"file": m["file"], "path": p, "sha1": hashlib.sha1(p.read_bytes()).hexdigest(), "own": False})
    jars = own_jars()
    if not any(p.name.startswith("incognita-") for p in jars):
        sys.exit("incognita jar not found, run script/build-mod.sh first")
    for p in jars:
        entries.append({"file": p.name, "path": p, "sha1": hashlib.sha1(p.read_bytes()).hexdigest(), "own": True})
    classify(entries, args.offline)
    for e in entries:
        if e["own"]:  # our mods are always shipped, they handle the dist themselves
            e["verdict"], e["evidence"] = "both", "own mod (client+server), always included"
    counts = write_report(entries, args.report)

    out = ROOT / "dist" / f"TerraIncognita-{args.version}-server.zip"
    out.parent.mkdir(exist_ok=True)
    client_ids = {i.lower() for e in entries if e["verdict"] == "client-only" for i in e["modids"]}
    n_cfg = 0
    with zipfile.ZipFile(out, "w", zipfile.ZIP_DEFLATED, compresslevel=9) as z:
        for e in entries:
            if e["verdict"] != "client-only":
                z.write(e["path"], f"mods/{e['file']}")
        cfg = ROOT / "conf/config"
        for f in sorted(cfg.rglob("*")) if cfg.is_dir() else []:
            rel = f.relative_to(cfg)
            top = rel.parts[0]
            stem = top.split(".")[0].lower()
            if not f.is_file() or f.name == ".gitkeep" or top in CLIENT_CONFIG or stem in client_ids \
                    or any(stem.startswith(c + "-") for c in client_ids):
                continue
            z.write(f, f"config/{rel}")
            n_cfg += 1
        dc = ROOT / "conf/defaultconfigs"
        for f in sorted(dc.rglob("*")) if dc.is_dir() else []:
            if f.is_file() and f.name != ".gitkeep":
                z.write(f, f"defaultconfigs/{f.relative_to(dc)}")
        kj = ROOT / "kubejs"
        for f in sorted(kj.rglob("*")):
            if f.is_file() and f.relative_to(kj).parts[0] not in KUBEJS_SKIP and f.name != ".gitkeep":
                z.write(f, f"kubejs/{f.relative_to(kj)}")
        fmt = {"neoforge": ml["neoforge"], "mc": ml["minecraft"], "version": args.version}
        for name, text, mode in (("start.sh", START_SH, 0o755), ("start.bat", START_BAT.replace("\n", "\r\n"), 0o644)):
            zi = zipfile.ZipInfo(name)
            zi.external_attr = (0o100000 | mode) << 16
            zi.compress_type = zipfile.ZIP_DEFLATED
            z.writestr(zi, text.replace("{neoforge}", fmt["neoforge"]))
        z.writestr("user_jvm_args.txt", "# JVM arguments (docs/perf.md). Heap: lower on a small machine.\n" + "\n".join(JVM_ARGS) + "\n")
        z.writestr("server.properties", SERVER_PROPERTIES)
        z.writestr("README-SERVER.txt", README.format(**fmt))
    with zipfile.ZipFile(out) as z:
        names = z.namelist()
    if not any(n.startswith("mods/incognita-") for n in names):
        sys.exit("the server zip does not contain incognita")
    print(f"{out.relative_to(ROOT)}: {out.stat().st_size / 1e6:.1f} MB, mods {sum(n.startswith('mods/') for n in names)}, "
          f"config files {n_cfg}; classification {counts}; report {Path(args.report).relative_to(ROOT)}")


if __name__ == "__main__":
    main()
