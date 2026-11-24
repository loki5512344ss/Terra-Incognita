#!/usr/bin/env python3
"""Builds the pack zip in CurseForge format (imports into the CurseForge App, Prism, ATLauncher and others).

  script/build-cf-pack.py [--version 0.1.0] [--no-external]

The archive contains:
  manifest.json   Minecraft and NeoForge versions plus the CurseForge mod list (projectID/fileID);
                  the launcher downloads the jars itself
  modlist.html    human-readable mod list
  overrides/      what gets copied into the game folder: config, defaultconfigs, kubejs, our Incognita mod
                  and mods missing from CurseForge (downloaded by link and checked by sha1)

--no-external leaves third-party mods that are not on CurseForge out of the archive (install them by hand).
The mod list comes from mods/modlist.json (refreshed by local_script/gen-mod-manifest.sh).
"""
import argparse, hashlib, html, json, subprocess, sys, urllib.request, zipfile
from pathlib import Path

ROOT = Path(__file__).resolve().parent.parent
NAME, AUTHOR = "Terra Incognita", "loki5512344"
OVERRIDES = [("conf/config", "config"), ("conf/defaultconfigs", "defaultconfigs"), ("kubejs", "kubejs"),
             ("resources/resourcepacks", "resourcepacks"), ("resources/shaderpacks", "shaderpacks")]


def default_version():
    try:
        return subprocess.run(["git", "describe", "--tags", "--always", "--dirty"], cwd=ROOT, capture_output=True,
                              text=True, check=True).stdout.strip().lstrip("v")
    except Exception:
        return "dev"


def fetch(url, sha1):
    cache = ROOT / "dist/cache" / sha1
    if not cache.exists():
        cache.parent.mkdir(parents=True, exist_ok=True)
        req = urllib.request.Request(url, headers={"User-Agent": "modpack-build-script"})
        data = urllib.request.urlopen(req, timeout=120).read()
        if hashlib.sha1(data).hexdigest() != sha1:
            sys.exit(f"sha1 mismatch: {url}")
        cache.write_bytes(data)
    return cache


def main():
    ap = argparse.ArgumentParser()
    ap.add_argument("--version", default=default_version())
    ap.add_argument("--no-external", action="store_true")
    ap.add_argument("--with-server", action="store_true", help="also build the dedicated-server zip (script/build-server-pack.py)")
    args = ap.parse_args()

    modlist = json.loads((ROOT / "mods/modlist.json").read_text())
    cf = [m for m in modlist["mods"] if "curseforge" in m]
    external = [m for m in modlist["mods"] if "curseforge" not in m]
    # our own mods: prebuilt jars live in custom_mods/<name>/libs and are tracked in git;
    # a fresh local build in build/libs is used only when libs is absent.
    jars = []
    for name in ("incognita", "oc2r", "hillsphere"):
        found = None
        for folder in ("libs", "build/libs"):
            base = ROOT / f"custom_mods/{name}/{folder}"
            candidates = sorted(p for p in base.glob(f"{name}-*.jar")
                                if "sources" not in p.name and "javadoc" not in p.name) if base.is_dir() else []
            if candidates:
                found = candidates[-1]
                break
        if found:
            jars.append(found)
        elif name == "incognita":
            sys.exit("mod jar not found, run script/build-mod.sh first")
        else:
            print(f"warning: {name} jar not found in custom_mods/{name}/libs or build/libs, the pack ships without it", file=sys.stderr)

    manifest = {
        "minecraft": {"version": modlist["minecraft"],
                      "modLoaders": [{"id": f"neoforge-{modlist['neoforge']}", "primary": True}],
                      # CurseForge launcher hint in MB; the audit recommends 6 GB, more only when GC pauses are frequent.
                      "recommendedRam": 6144},
        "manifestType": "minecraftModpack",
        "manifestVersion": 1,
        "name": NAME,
        "version": args.version,
        "author": AUTHOR,
        "files": [{"projectID": m["curseforge"]["project"], "fileID": m["curseforge"]["file"], "required": True} for m in cf],
        "overrides": "overrides",
    }
    rows = [f"<li>{html.escape(m['name'])}</li>" for m in sorted(modlist["mods"], key=lambda m: m["name"].lower())]
    rows.append("<li>Incognita</li>")
    if any(j.name.startswith("oc2r-") for j in jars):
        rows.append("<li>OC2R</li>")
    if any(j.name.startswith("hillsphere-") for j in jars):
        rows.append("<li>Hill Sphere</li>")

    out = ROOT / "dist" / f"TerraIncognita-{args.version}-curseforge.zip"
    out.parent.mkdir(exist_ok=True)
    count = 0
    with zipfile.ZipFile(out, "w", zipfile.ZIP_DEFLATED, compresslevel=9) as z:
        z.writestr("manifest.json", json.dumps(manifest, indent=2))
        z.writestr("modlist.html", "<ul>\n" + "\n".join(rows) + "\n</ul>\n")
        for src, dst in OVERRIDES:
            for f in sorted((ROOT / src).rglob("*")) if (ROOT / src).is_dir() else []:
                if f.is_file() and f.name != ".gitkeep":
                    z.write(f, f"overrides/{dst}/{f.relative_to(ROOT / src)}")
                    count += 1
        for j in jars:
            z.write(j, f"overrides/mods/{j.name}")
        for m in [] if args.no_external else external:
            z.write(fetch(m["url"], m["sha1"]), f"overrides/mods/{m['file']}")
    with zipfile.ZipFile(out) as z:
        for j in jars:
            if f"overrides/mods/{j.name}" not in z.namelist():
                sys.exit(f"the archive does not contain the mod {j.name}")
    print(f"{out.relative_to(ROOT)}: {out.stat().st_size / 1e6:.1f} MB, mods in manifest {len(cf)}, "
          f"in overrides {len(jars) + (0 if args.no_external else len(external))}, config files {count}")
    if args.no_external and external:
        print("Not included (install manually): " + ", ".join(m["file"] for m in external))
    if args.with_server:
        subprocess.run([sys.executable, str(ROOT / "script/build-server-pack.py"), "--version", args.version], check=True)


if __name__ == "__main__":
    main()
