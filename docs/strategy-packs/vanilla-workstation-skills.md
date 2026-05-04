# Vanilla Workstation Skills

This strategy pack is advisory planner knowledge only. The Java runtime does not parse, execute, or trust this file, and it does not create special tool names for vanilla mechanics.

## Runtime Contract

- Use generic primitives such as `block_at`, `find_blocks`, `pathfinder_goto`, `activate_block`, `open_block`, `open_container`, `window_deposit`, `window_withdraw`, `window_close`, `transfer`, `inventory`, `equip`, `unequip`, `move_slot_item`, and `set_quick_bar_slot`.
- Every primitive is still validated by the runtime for policy, loaded chunks, reachability, line of sight, slot or container existence, inventory capacity, and no-loss transaction semantics where the adapter supports those checks.
- Illegal container moves, slot-restricted containers, missing items, unsupported block/entity interactions, or unsafe state changes may be rejected by Minecraft or OpenPlayer. Treat rejection as real feedback, not as success.
- Do not invent Java tool names for workstations or mechanics. Use `unavailable` or `report_status` when the next safe generic primitive is absent.

## Smelting Goal

- Locate a candidate workstation with `find_blocks` or inspect a known position with `block_at`.
- Move within loaded-area bounds with `pathfinder_goto` and face or interact with the block using generic interaction primitives.
- Open the block only through `open_block` or `open_container`.
- If generic transfer into the opened container is rejected because the container has restricted slots, report that the runtime lacks a safe generic slot transaction for this workstation. Do not call furnace-specific tools.

## Armor Goal

- Inspect inventory and equipment with `inventory`.
- Choose a candidate item in planner logic, then use `equip` for a specific item or `unequip` for a specific destination.
- If the planner cannot prove a safe upgrade from visible inventory state, ask for clarification or report unavailable. Do not call an armor-manager tool.

## Trading, Enchanting, Anvils, Brewing, Smithing

- Use loaded-world observation, movement, and generic interaction primitives only.
- If a required UI/session adapter is missing or generic container transfer is rejected, report unavailable with the missing safe adapter. Do not expose or call mechanics-specific Java tools.
