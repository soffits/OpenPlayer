# Release Packaging

OpenPlayer releases are local/offline clean-room Minecraft mod artifacts for Minecraft `1.20.1` and Java `17`. Do not describe them as commercial online 1:1 Player2NPC parity. The current scope is a near-parity local/offline Player2NPC-style companion experience.

## Release Channels

OpenPlayer currently publishes release artifacts only through GitHub Releases. Modrinth publishing is a future distribution step after the release process and metadata are stable; do not add Modrinth tokens, secrets, or publishing jobs yet.

- Alpha, beta, release-candidate, pre, and canary tags are prereleases and use version tags such as `v0.1.0-alpha.1`, `v0.1.0-beta.1`, `v0.1.0-rc.1`, `v0.1.0-pre.1`, or `v0.1.0-canary.1`.
- Stable releases use tags without prerelease channel text, such as `v0.1.0`.
- The project version in `gradle.properties` must match the tag without the leading `v` before a release tag is created.
- Use Conventional Commits in merged history so generated GitHub release notes are useful.
- Prerelease notes must summarize user-facing changes and list the Fabric and Forge runtime artifact names expected for that version.

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

The Gradle archive base name is `openplayer-{module}` and the version comes from `mod_version` in `gradle.properties`.

- Fabric runtime jar: `fabric/build/libs/openplayer-fabric-{version}.jar`.
- Forge runtime jar: `forge/build/libs/openplayer-forge-{version}.jar`.
- Fabric sources jar: `fabric/build/libs/openplayer-fabric-{version}-sources.jar`.
- Forge sources jar: `forge/build/libs/openplayer-forge-{version}-sources.jar`.
- Common module jars under `common/build/libs` are intermediate development artifacts and are not standalone user uploads.
- `*-dev-shadow.jar` outputs are development/shadow intermediates and are not release uploads.

## Upload

- GitHub Releases are created by `.github/workflows/release.yml` for `v*` tags or manual `workflow_dispatch` runs with a version tag input.
- The release workflow builds with Java `17`, creates a short curated release-note seed from `RELEASE_NOTES.md` when present, augments it with generated GitHub notes, and marks tags containing `-alpha`, `-beta`, `-rc`, `pre`, or `canary` as prereleases.
- Upload the Fabric runtime jar from `fabric/build/libs` to the GitHub Release.
- Upload the Forge runtime jar from `forge/build/libs` to the GitHub Release.
- Upload or link matching source code for the exact release revision to satisfy `AGPL-3.0-only` obligations.
- Include the license, Minecraft version, Java version, loader requirements, Architectury API requirement, and clean-room local/offline scope in release notes.
- Include meaningful user-facing changes for prereleases; do not publish notes that only contain a Full Changelog link.
- Include a link to `docs/manual-qa-checklist.md` or summarize the Fabric and Forge manual QA pass results.

## Do Not Upload

- Do not upload `common/build/libs` jars as end-user mods.
- Do not upload `*-dev-shadow.jar` files.
- Do not upload Gradle caches, run directories, logs, screenshots containing secrets, local character files with credentials, provider API keys, remote skin caches, opaque jars, or copied Player2NPC, PlayerEngine, or decompiled code.
- Do not bundle Baritone, Automatone, PlayerEngine, provider SDKs, account tokens, or local test configs into the release artifacts.

## Runtime Configuration

OpenPlayer is offline and provider-disabled until endpoint, model, and API key all resolve.

The `OPENPLAYER_*` names below can be set as environment variables or as JVM system properties with `-D<name>=<value>`. JVM system properties have highest priority, environment variables have second priority, and the optional in-game UI fallback in `<Minecraft config>/openplayer/provider.properties` has third priority.

- `OPENPLAYER_INTENT_PROVIDER_ENDPOINT=...` sets the OpenAI-compatible endpoint.
- `OPENPLAYER_INTENT_PROVIDER_API_KEY=your-provider-key` sets the provider API key. Never put this in character files, screenshots, logs, docs, or release notes.
- `OPENPLAYER_INTENT_PROVIDER_MODEL=...` sets the provider model.

Singleplayer hosts and players with sufficient server permission can save the provider fallback through the OpenPlayer controls UI. The fallback file uses `endpoint`, `model`, and `apiKey`, and is separate from character files. Blank API key saves preserve the existing key unless the explicit clear-key option is selected. Do not package local `provider.properties` files or screenshots showing provider secrets. World, inventory, and combat actions are controlled per character with `allowWorldActions=true`; missing values and default spawns are disabled.

## Known Limitations

- OpenPlayer does not provide account login, commercial online character service parity, cloud character sync, remote skin downloads, account profile lookup, or marketplace-style character import.
- Local skin PNGs are client-local. Multiplayer clients without the same local file fall back to configured resource skins or deterministic default skins.
- Provider-backed conversation is optional and disabled by default. Provider output is treated as untrusted and must parse into constrained intents before any action runs.
- There is no TTS, speech recognition, persisted conversation memory, raw model response display, per-character provider key support, or online memory service.
- The vanilla automation layer is bounded NPC-backed behavior, not full PlayerEngine parity.
- World, inventory, and combat actions remain disabled unless the selected local character has `allowWorldActions=true`.
- Full real-player emulation, containers, crafting, trading, arbitrary block entity interaction, and broad inventory transfer are not implemented.

## License And Source Obligations

- OpenPlayer repository code and metadata declare `AGPL-3.0-only`.
- Release notes must preserve the clean-room scope and must not imply copied Player2NPC, PlayerEngine, decompiled code, opaque jars, or proprietary assets are included.
- When distributing binaries, provide the corresponding source for the exact release revision and retain AGPL license notices.
- Architectury API is used from public Maven coordinates and is LGPL-3.0-only.
- Optional provider access uses Java 17 `HttpClient` and adds no provider SDK dependency.
