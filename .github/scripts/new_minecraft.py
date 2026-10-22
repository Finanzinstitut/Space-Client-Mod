#!/usr/bin/env python3
"""Looks for a Minecraft release this mod has no build for yet.

Reads Mojang's version list, works out the release series of the newest full
release (26.3.1 belongs to 26.3, a 1.x release is its own series) and, when
there is no versions/<series> project, sets one up for every loader from the
newest project there is - same source, the new Minecraft version and the
newest Fabric API, NeoForge and Forge builds made for it. The workflow then
tries to build them and reports what happened.

Only the runner's checkout is changed. A new version usually needs source
changes as well, which is work for a person; this only says when it is time
and how far the existing code gets.

Writes key=value lines for the workflow to $GITHUB_OUTPUT (or prints them).
"""

import io
import json
import os
import re
import sys
import urllib.request
import xml.etree.ElementTree as ET
import zipfile

ROOT = os.path.dirname(os.path.dirname(os.path.dirname(os.path.abspath(__file__))))
MANIFEST = "https://piston-meta.mojang.com/mc/game/version_manifest_v2.json"
FABRIC_API = "https://maven.fabricmc.net/net/fabricmc/fabric-api/fabric-api/maven-metadata.xml"
NEOFORGE = "https://maven.neoforged.net/releases/net/neoforged/neoforge/maven-metadata.xml"
FORGE = "https://maven.minecraftforge.net/net/minecraftforge/forge/maven-metadata.xml"


def fetch(url):
    request = urllib.request.Request(url, headers={"User-Agent": "spaceclient-version-check"})
    with urllib.request.urlopen(request, timeout=30) as response:
        return response.read()


def maven_versions(url):
    try:
        return [v.text for v in ET.fromstring(fetch(url)).iter("version")]
    except Exception as error:  # a loader's maven being down is not fatal
        print(f"could not read {url}: {error}", file=sys.stderr)
        return []


def data_format(manifest, minecraft):
    """The data pack format of a release, from the version.json in its client jar."""
    try:
        entry = next(v for v in manifest["versions"] if v["id"] == minecraft)
        client = json.loads(fetch(entry["url"]))["downloads"]["client"]["url"]
        with zipfile.ZipFile(io.BytesIO(fetch(client))) as jar:
            return str(json.loads(jar.read("version.json"))["pack_version"]["data_major"])
    except Exception as error:
        print(f"could not read the pack format of {minecraft}: {error}", file=sys.stderr)
        return None


def series(version):
    parts = version.split(".")
    return version if parts[0] == "1" else ".".join(parts[:2])


def key(version):
    return [int(n) for n in re.findall(r"\d+", version)]


def supported():
    with open(os.path.join(ROOT, "settings.gradle")) as f:
        listed = re.search(r"\[([^\]]*)\]\.each \{ mc ->", f.read())
    return re.findall(r"'([^']+)'", listed.group(1))


def read_props(path):
    props = {}
    with open(path) as f:
        for line in f:
            if "=" in line and not line.lstrip().startswith("#"):
                k, v = line.rstrip("\n").split("=", 1)
                props[k] = v
    return props


def write_props(path, props):
    os.makedirs(os.path.dirname(path), exist_ok=True)
    with open(path, "w") as f:
        for k, v in props.items():
            f.write(f"{k}={v}\n")


def next_range(label):
    parts = [int(n) for n in label.split(".")]
    upper = parts[:-1] + [parts[-1] + 1]
    return f"[{label},{'.'.join(map(str, upper))})"


def neoforge_prefix(label):
    parts = label.split(".")
    if parts[0] == "1":
        return f"{parts[1]}.{parts[2] if len(parts) > 2 else 0}."
    return f"{parts[0]}.{parts[1]}.{parts[2] if len(parts) > 2 else 0}."


def main():
    manifest = json.loads(fetch(MANIFEST))
    release = manifest["latest"]["release"]
    label = series(release)
    have = supported()
    out = {"release": release, "series": label, "supported": " ".join(have)}

    if label in have or any(key(label) <= key(s) for s in have if s.split(".")[0] == label.split(".")[0]):
        out["new"] = "false"
        return out
    out["new"] = "true"

    # The newest Minecraft release in this series, for the build to run on
    in_series = [v["id"] for v in manifest["versions"]
                 if v["type"] == "release" and series(v["id"]) == label]
    minecraft = max(in_series, key=key) if in_series else release

    template = max(have, key=key)
    tasks = []

    fabric = [v for v in maven_versions(FABRIC_API) if v.endswith("+" + minecraft) or v.endswith("+" + label)]
    if fabric:
        props = read_props(os.path.join(ROOT, "versions", template, "gradle.properties"))
        props.update(minecraft_version=minecraft, mc_label=label, mc_dep="~" + label,
                     fabric_version=max(fabric, key=key))
        write_props(os.path.join(ROOT, "versions", label, "gradle.properties"), props)
        copy_rest(template, label)
        tasks.append(f":{label}:build")
        out["fabric"] = props["fabric_version"]
    else:
        out["fabric"] = "none yet"

    prefix = neoforge_prefix(minecraft)
    neo = [v for v in maven_versions(NEOFORGE) if v.startswith(prefix)]
    if neo:
        stable = [v for v in neo if "-" not in v]
        props = read_props(os.path.join(ROOT, "versions", template + "-neoforge", "gradle.properties"))
        props.update(minecraft_version=minecraft, mc_label=label, mc_dep="~" + label,
                     mc_range=next_range(label), neoforge_version=max(stable or neo, key=key),
                     loader_range=next_range(".".join(prefix.split(".")[:2])))
        write_props(os.path.join(ROOT, "versions", label + "-neoforge", "gradle.properties"), props)
        copy_rest(template + "-neoforge", label + "-neoforge")
        tasks.append(f":{label}-neoforge:build")
        out["neoforge"] = props["neoforge_version"]
    else:
        out["neoforge"] = "none yet"

    forge = [v for v in maven_versions(FORGE) if v.startswith(minecraft + "-")]
    if forge:
        newest = max(forge, key=key)
        props = read_props(os.path.join(ROOT, "versions", template + "-forge", "gradle.properties"))
        props.update(minecraft_version=minecraft, mc_label=label, mc_dep="~" + label,
                     mc_range=next_range(label), forge_version=newest,
                     loader_range=f"[{newest.split('-')[1].split('.')[0]},)")
        props.pop("forge_mappings", None)
        props["pack_format"] = data_format(manifest, minecraft) or props.get("pack_format", "")
        write_props(os.path.join(ROOT, "versions", label + "-forge", "gradle.properties"), props)
        copy_rest(template + "-forge", label + "-forge")
        tasks.append(f":{label}-forge:build")
        out["forge"] = props["forge_version"]
    else:
        out["forge"] = "none yet"

    if tasks:
        path = os.path.join(ROOT, "settings.gradle")
        with open(path) as f:
            text = f.read()
        text = re.sub(r"(\[[^\]]*)'(\]\.each \{ mc ->)", rf"\1', '{label}'\2", text, count=1)
        with open(path, "w") as f:
            f.write(text)
    out["tasks"] = " ".join(tasks)
    return out


def copy_rest(template, target):
    """Everything in the template project but its properties and build output."""
    source = os.path.join(ROOT, "versions", template)
    for name in os.listdir(source):
        if name in ("gradle.properties", "build", "run", ".gradle"):
            continue
        # 1.21.11's renames only make sense for 1.21.11
        if name == "renames.txt":
            continue
        with open(os.path.join(source, name), "rb") as f:
            data = f.read()
        with open(os.path.join(ROOT, "versions", target, name), "wb") as f:
            f.write(data)


if __name__ == "__main__":
    result = main()
    lines = [f"{k}={v}" for k, v in result.items()]
    target = os.environ.get("GITHUB_OUTPUT")
    if target:
        with open(target, "a") as f:
            f.write("\n".join(lines) + "\n")
    print("\n".join(lines))
