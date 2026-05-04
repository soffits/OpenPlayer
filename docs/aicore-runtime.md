# AICore Runtime

OpenPlayer keeps product concerns in the OpenPlayer layer: profiles, UI, provider configuration, chat, commands, entity lifecycle, and owner policy. The AICore package is the bottom server-side runtime contract for bounded primitive tools.

AICore provider output is untrusted tool-call JSON. The runtime validates tool names, arguments, and `allowWorldActions` policy before mapping a primitive tool to the existing server-thread automation adapters. Provider output never executes code and does not bypass permissions, loaded-world checks, reachability checks, or combat allowlists.

The provider-facing primitive surface is intentionally low level: observe/report, loaded block/entity search, coordinate movement/look, block break/place, dropped-item pickup, inventory query, equip/drop, interact, bounded attack, and stop/pause/unpause. High-level goals such as acquiring resources, crafting chains, smelting, farming, fishing, building structures, locating structures, portal use, Nether travel, or exploration routes belong in prompts or local strategy packs that choose one truthful primitive at a time.
