# Release Notes

## Unreleased

- Reset public release naming to the product-prefixed Minecraft-line scheme: `openplayer_1.20.1_1`, with prerelease channels such as `openplayer_1.20.1_1-alpha.1`, `openplayer_1.20.1_1-beta.1`, and `openplayer_1.20.1_1-rc.1`.
- Simplify public documentation around the local-first clean-room companion runtime, current safety boundaries, install/build basics, and maintainer roadmap links.
- Remove stale process, packaging, and historical alpha release documentation from the public docs set.

Artifacts for the next release should use the loader prefix plus `mod_version` without the repeated `openplayer_` product prefix:

- `openplayer-fabric-1.20.1_1-alpha.1.jar`
- `openplayer-forge-1.20.1_1-alpha.1.jar`

Release tags must match `gradle.properties` `mod_version` exactly. Runtime artifact filenames omit the repeated `openplayer_` product prefix from that version. Tags containing `-alpha.`, `-beta.`, `-rc.`, `-pre.`, or `-canary.` are prereleases.
