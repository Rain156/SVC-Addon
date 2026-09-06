"""Verify the distributable mod, its dependency contract, and shared classes."""
from __future__ import annotations

import argparse
import hashlib
import json
from pathlib import Path
import re
import struct
import tomllib
import zipfile


def verify(game: str, loader: str) -> Path:
    root = Path(__file__).resolve().parents[1]
    properties = dict(
        line.split("=", 1)
        for line in (root / "gradle.properties").read_text(encoding="utf-8").splitlines()
        if "=" in line and not line.startswith("#")
    )
    version = properties["modVersion"]
    artifact = root / f"platforms/{loader}/build/{game}/libs/svcaddon-{loader}-{game}-{version}.jar"
    expected_major = 65 if game == "1.21.1" else 69
    with zipfile.ZipFile(artifact) as jar:
        names = set(jar.namelist())
        prefix = "io/github/rain156/svcaddon/"
        required = {
            prefix + "api/audio/AudioEffect.class",
            prefix + "core/routing/VoiceRouter.class",
            prefix + "voicechat/VoiceRuntime.class",
            prefix + "minecraft/MinecraftVersion.class",
            prefix + "minecraft/AddonCommands.class",
            "svcaddon-defaults.properties",
            "LICENSE",
        }
        assert required <= names, f"Missing runtime files: {required - names}"
        assert not any(name.startswith(("net/minecraft/", "de/maxhenkel/voicechat/", "org/junit/")) for name in names), "Bundled upstream/test classes"
        for name in names:
            if name.endswith(".class"):
                magic, _, major = struct.unpack(">IHH", jar.read(name)[:8])
                assert magic == 0xCAFEBABE and major <= expected_major, f"Invalid bytecode version: {name} ({major})"
                if name.startswith(prefix + "api/"):
                    assert major <= 65, "Public API requires more than Java 21"

        if loader == "fabric":
            metadata = json.loads(jar.read("fabric.mod.json"))
            assert metadata["id"] == "svcaddon" and metadata["version"] == version
            assert metadata["depends"]["minecraft"] == game
            assert metadata["depends"]["voicechat_api"] == ">=2.6.20"
            for entries in metadata["entrypoints"].values():
                for entry in entries:
                    assert entry.replace(".", "/") + ".class" in names, f"Missing entrypoint {entry}"
        else:
            raw = jar.read("META-INF/neoforge.mods.toml").decode("utf-8")
            assert "${" not in raw, "Unexpanded NeoForge metadata"
            metadata = tomllib.loads(raw)
            assert metadata["mods"][0]["modId"] == "svcaddon"
            assert metadata["mods"][0]["version"] == version
            dependencies = {item["modId"]: item for item in metadata["dependencies"]["svcaddon"]}
            assert dependencies["voicechat_api"]["versionRange"] == "[2.6.20,)"
            assert dependencies["minecraft"]["versionRange"].startswith("[" + game + ",")
            assert prefix + "neoforge/NeoForgeVoicechatPlugin.class" in names

        english = json.loads(jar.read("assets/svcaddon/lang/en_us.json"))
        chinese = json.loads(jar.read("assets/svcaddon/lang/zh_cn.json"))
        assert english.keys() == chinese.keys(), "Language keys differ"
        for key in english:
            assert re.findall(r"%(?:\d+\$)?[sd]", english[key]) == re.findall(r"%(?:\d+\$)?[sd]", chinese[key]), f"Placeholder mismatch: {key}"
    digest = hashlib.sha256(artifact.read_bytes()).hexdigest()
    print(f"{digest}  {artifact.relative_to(root).as_posix()}")
    return artifact


if __name__ == "__main__":
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("game", choices=["1.21.1", "26.2"])
    parser.add_argument("loader", choices=["fabric", "neoforge"])
    args = parser.parse_args()
    verify(args.game, args.loader)
