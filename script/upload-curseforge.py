#!/usr/bin/env python3
"""Uploads a release to the CurseForge project: the client pack, then the server pack as its additional file.

  script/upload-curseforge.py --version 0.1.0-beta.2 [--changelog notes.md] [--dry-run]

Needs two environment variables (in CI: a repository secret and a repository variable):
  CURSEFORGE_TOKEN        API token from https://authors.curseforge.com/account/api-tokens
  CURSEFORGE_PROJECT_ID   numeric project id shown on the project page
A version with "-alpha" or "-beta" is uploaded as that release type, anything else as a release.
"""
import argparse, json, os, sys, urllib.error, urllib.request, uuid
from pathlib import Path

ROOT = Path(__file__).resolve().parent.parent
API = "https://minecraft.curseforge.com/api"
AGENT = "terra-incognita-release"


def request(url, token, data=None, content_type=None):
    headers = {"X-Api-Token": token, "User-Agent": AGENT}
    if content_type:
        headers["Content-Type"] = content_type
    try:
        with urllib.request.urlopen(urllib.request.Request(url, data=data, headers=headers), timeout=600) as r:
            return json.load(r)
    except urllib.error.HTTPError as e:
        sys.exit(f"CurseForge answered {e.code} for {url}: {e.read().decode(errors='replace')[:500]}")


def release_type(version):
    return next((kind for kind in ("alpha", "beta") if f"-{kind}" in version), "release")


def minecraft_version_id(types, versions, minecraft):
    """Several entries share a name like 1.21.1; only the one of a "minecraft-*" version type fits a modpack."""
    minecraft_types = {t["id"] for t in types if t["slug"].startswith("minecraft-")}
    for v in versions:
        if v["name"] == minecraft and v["gameVersionTypeID"] in minecraft_types:
            return v["id"]
    sys.exit(f"CurseForge does not list Minecraft {minecraft}")


def multipart(metadata, path):
    boundary = uuid.uuid4().hex
    head = (f'--{boundary}\r\nContent-Disposition: form-data; name="metadata"\r\n\r\n{json.dumps(metadata)}\r\n'
            f'--{boundary}\r\nContent-Disposition: form-data; name="file"; filename="{path.name}"\r\n'
            "Content-Type: application/zip\r\n\r\n").encode()
    return head + path.read_bytes() + f"\r\n--{boundary}--\r\n".encode(), f"multipart/form-data; boundary={boundary}"


def main():
    ap = argparse.ArgumentParser()
    ap.add_argument("--version", required=True)
    ap.add_argument("--changelog", type=Path)
    ap.add_argument("--dry-run", action="store_true", help="print what would be sent, without network access")
    args = ap.parse_args()

    client = ROOT / "dist" / f"TerraIncognita-{args.version}-curseforge.zip"
    server = ROOT / "dist" / f"TerraIncognita-{args.version}-server.zip"
    for path in (client, server):
        if not path.is_file():
            sys.exit(f"missing {path.relative_to(ROOT)}, build the packs first")
    changelog = args.changelog.read_text() if args.changelog else f"Terra Incognita {args.version}"
    metadata = {"changelog": changelog, "changelogType": "markdown", "releaseType": release_type(args.version),
                "displayName": f"Terra Incognita {args.version}"}
    minecraft = json.loads((ROOT / "mods/modlist.json").read_text())["minecraft"]
    if args.dry_run:
        print(json.dumps(metadata, indent=2), f"Minecraft {minecraft}", client.name, server.name, sep="\n")
        return

    token, project = os.environ.get("CURSEFORGE_TOKEN"), os.environ.get("CURSEFORGE_PROJECT_ID")
    if not token or not project:
        sys.exit("set CURSEFORGE_TOKEN and CURSEFORGE_PROJECT_ID")
    version_id = minecraft_version_id(request(f"{API}/game/version-types", token),
                                      request(f"{API}/game/versions", token), minecraft)
    upload = f"{API}/projects/{project}/upload-file"
    body, kind = multipart({**metadata, "gameVersions": [version_id]}, client)
    parent = request(upload, token, body, kind)["id"]
    print(f"client pack uploaded, file id {parent}")
    # an additional file inherits the game versions of its parent, CurseForge rejects them here
    body, kind = multipart({**metadata, "displayName": f"Terra Incognita {args.version} server",
                            "parentFileID": parent}, server)
    print(f"server pack uploaded, file id {request(upload, token, body, kind)['id']}")


if __name__ == "__main__":
    main()
