# Release Notes

## v0.1.0-alpha.4

- Resolves OpenAI-compatible provider base URLs ending in `/v1` or `/v1/` to `/v1/chat/completions` while preserving explicit `/chat/completions` and custom gateway endpoints.
- Adds a permission-gated Provider page test action that performs a sanitized connectivity/parse check without exposing API keys or raw provider output.
- Improves provider and conversation diagnostics with safe failure classes such as not configured, permission required, HTTP status, timeout, request failure, and parse rejection.

Artifacts:

- `openplayer-fabric-0.1.0-alpha.4.jar`
- `openplayer-forge-0.1.0-alpha.4.jar`

## v0.1.0-alpha.3

- Reworks the controls screen around real profile selection: profiles appear in the left list, unassigned profiles are selectable, and the protected `openplayer_default` profile can be edited but not deleted.
- Replaces ambiguous default follow/stop actions with selected-profile actions and a state-aware `Start Following` / `Stop Following` control.
- Adds a Profiles page for creating, editing, duplicating, deleting, and configuring local AI profiles, including skin path, prompt/settings, and `allowWorldActions`.
- Adds an Imports page that opens the local imports folder, lists safe `.properties` profile files, imports the selected file, and removes the import source only after a successful import.
- Adds `/ai <profile-or-assignment> <message>` and `/aichat <profile-or-assignment> <message>` server commands for chat-first AI interaction.
- Adds English, Japanese, and French language resources for user-facing controls, with repository guidance requiring new UI strings to use language JSON files.
- Gates profile file mutations behind singleplayer-owner or permission-level-2 access while keeping profile reads available.

Artifacts:

- `openplayer-fabric-0.1.0-alpha.3.jar`
- `openplayer-forge-0.1.0-alpha.3.jar`

## v0.1.0-alpha.2

- Redesigns the OpenPlayer Controls screen into Main, Provider, and Status pages so setup and companion actions remain usable at 1280x720 with default GUI scale.
- Keeps the local assignment gallery visible while moving provider configuration and status text out from under action widgets.
- Preserves provider API key safety: saved blank keys keep the existing secret unless the explicit clear-key toggle is selected, and key values are not displayed.

Artifacts:

- `openplayer-fabric-0.1.0-alpha.2.jar`
- `openplayer-forge-0.1.0-alpha.2.jar`
