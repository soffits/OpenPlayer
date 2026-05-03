# Release Packaging

OpenPlayer releases are local/offline clean-room Minecraft mod artifacts for Minecraft `1.20.1` and Java `17`. Do not describe them as commercial online 1:1 Player2NPC parity. The current scope is a near-parity local/offline Player2NPC-style companion experience.

## Build Command

Run the release build from the repository root with Java `17`:

```sh
./gradlew build
```

If multiple JDKs are installed, use a Java 17 environment, for example:

```sh
JAVA_HOME=<path-to-jdk-17> PATH=<path-to-jdk-17>/bin:$PATH ./gradlew build
```

Also run:

```sh
git diff --check
```

## Artifact Locations

The Gradle archive base name is `openplayer-<module>` and the version comes from `mod_version` in `gradle.properties`.

- Fabric runtime jar: `fabric/build/libs/openplayer-fabric-<version>.jar`.
- Forge runtime jar: `forge/build/libs/openplayer-forge-<version>.jar`.
- Fabric sources jar: `fabric/build/libs/openplayer-fabric-<version>-sources.jar`.
- Forge sources jar: `forge/build/libs/openplayer-forge-<version>-sources.jar`.
- Common module jars under `common/build/libs` are intermediate development artifacts and are not standalone user uploads.
- `*-dev-shadow.jar` outputs are development/shadow intermediates and are not release uploads.

## Upload

- Upload the Fabric runtime jar to Fabric-compatible release channels.
- Upload the Forge runtime jar to Forge-compatible release channels.
- Upload or link matching source code for the exact release revision to satisfy `AGPL-3.0-only` obligations.
- Include the license, Minecraft version, Java version, loader requirements, Architectury API requirement, and clean-room local/offline scope in release notes.
- Include a link to `docs/manual-qa-checklist.md` or summarize the Fabric and Forge manual QA pass results.

## Do Not Upload

- Do not upload `common/build/libs` jars as end-user mods.
- Do not upload `*-dev-shadow.jar` files.
- Do not upload Gradle caches, run directories, logs, screenshots containing secrets, local character files with credentials, provider API keys, remote skin caches, opaque jars, or copied Player2NPC, PlayerEngine, or decompiled code.
- Do not bundle Baritone, Automatone, PlayerEngine, provider SDKs, account tokens, or local test configs into the release artifacts.

## Runtime Configuration

OpenPlayer is offline and provider-disabled by default.

The `OPENPLAYER_*` names below can be set as environment variables or as JVM system properties with `-D<name>=<value>`. JVM system properties have highest priority, environment variables have second priority, and the optional in-game UI fallback in `<Minecraft config>/openplayer/provider.properties` has third priority.

- `OPENPLAYER_INTENT_PARSER_ENABLED=true` enables the optional runtime intent parser path.
- `OPENPLAYER_INTENT_PROVIDER_ENDPOINT=...` sets the OpenAI-compatible endpoint.
- `OPENPLAYER_INTENT_PROVIDER_API_KEY=<secret>` sets the provider API key. Never put this in character files, screenshots, logs, docs, or release notes.
- `OPENPLAYER_INTENT_PROVIDER_MODEL=...` sets the provider model.
- `OPENPLAYER_AUTOMATION_BACKEND=vanilla` uses the default NPC-backed vanilla automation layer.
- `OPENPLAYER_AUTOMATION_BACKEND=disabled` rejects automation commands.
- `OPENPLAYER_AUTOMATION_BACKEND=baritone` tries the optional reflective Baritone command bridge when a compatible separate Baritone install is present.
- `OPENPLAYER_AUTOMATION_ALLOW_WORLD_ACTIONS=true` or `openplayer.automation.allowWorldActions=true` enables local world, inventory, and violent actions. Keep this disabled for ordinary release QA except in throwaway worlds.

Singleplayer hosts and players with sufficient server permission can save the provider fallback through the OpenPlayer controls UI. The fallback file uses `parserEnabled`, `endpoint`, `model`, and `apiKey`, and is separate from character files. Blank API key saves preserve the existing key unless the explicit clear-key option is selected. Do not package local `provider.properties` files or screenshots showing provider secrets.

## Known Limitations

- OpenPlayer does not provide account login, commercial online character service parity, cloud character sync, remote skin downloads, account profile lookup, or marketplace-style character import.
- Local skin PNGs are client-local. Multiplayer clients without the same local file fall back to configured resource skins or deterministic default skins.
- Provider-backed conversation is optional and disabled by default. Provider output is treated as untrusted and must parse into constrained intents before any action runs.
- There is no TTS, speech recognition, persisted conversation memory, raw model response display, per-character provider key support, or online memory service.
- The vanilla automation layer is bounded NPC-backed behavior, not full PlayerEngine parity.
- The optional Baritone bridge is reflective and controls only supported Baritone command paths when available; it is not a true NPC-backed Baritone adapter.
- World, inventory, and violent actions remain disabled unless explicitly enabled by local configuration.
- Full real-player emulation, containers, crafting, trading, arbitrary block entity interaction, and broad inventory transfer are not implemented.

## License And Source Obligations

- OpenPlayer repository code and metadata declare `AGPL-3.0-only`.
- Release notes must preserve the clean-room scope and must not imply copied Player2NPC, PlayerEngine, decompiled code, opaque jars, or proprietary assets are included.
- When distributing binaries, provide the corresponding source for the exact release revision and retain AGPL license notices.
- Architectury API is used from public Maven coordinates and is LGPL-3.0-only.
- Optional provider access uses Java 17 `HttpClient` and adds no provider SDK dependency.
- Optional Baritone support is reflective only and requires users to install a compatible separate Baritone mod/API when they choose that backend.
