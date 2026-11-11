#!/usr/bin/env python3
"""Собирает zip сборки в формате CurseForge (импортируется в CurseForge App, Prism, ATLauncher и др.).

  script/build-cf-pack.py [--version 0.1.0] [--no-external]

В архиве:
  manifest.json   версия Minecraft, NeoForge и список модов с CurseForge (projectID/fileID);
                  сами jar лаунчер скачивает сам
  modlist.html    список модов для людей
  overrides/      то, что копируется в папку игры: config, defaultconfigs, kubejs, наш мод Incognita
                  и моды, которых нет на CurseForge (скачиваются по ссылке и проверяются по sha1)

--no-external не кладёт в архив чужие моды не с CurseForge (их придётся ставить руками).
Список модов берётся из mods/modlist.json (обновляется local_script/gen-mod-manifest.sh).
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
            sys.exit(f"sha1 не совпал: {url}")
        cache.write_bytes(data)
    return cache


def main():
    ap = argparse.ArgumentParser()
    ap.add_argument("--version", default=default_version())
    ap.add_argument("--no-external", action="store_true")
    args = ap.parse_args()

    modlist = json.loads((ROOT / "mods/modlist.json").read_text())
    cf = [m for m in modlist["mods"] if "curseforge" in m]
    external = [m for m in modlist["mods"] if "curseforge" not in m]
    jars = sorted(p for p in (ROOT / "custom_mods/incognita/build/libs").glob("incognita-*.jar") if "sources" not in p.name)
    if not jars:
        sys.exit("jar мода не найден, сначала script/build-mod.sh")

    manifest = {
        "minecraft": {"version": modlist["minecraft"],
                      "modLoaders": [{"id": f"neoforge-{modlist['neoforge']}", "primary": True}]},
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
        z.write(jars[-1], f"overrides/mods/{jars[-1].name}")
        for m in [] if args.no_external else external:
            z.write(fetch(m["url"], m["sha1"]), f"overrides/mods/{m['file']}")
    print(f"{out.relative_to(ROOT)}: {out.stat().st_size / 1e6:.1f} МБ, модов в manifest {len(cf)}, "
          f"в overrides {1 + (0 if args.no_external else len(external))}, файлов настроек {count}")
    if args.no_external and external:
        print("Не вошли (ставить вручную): " + ", ".join(m["file"] for m in external))


if __name__ == "__main__":
    main()
