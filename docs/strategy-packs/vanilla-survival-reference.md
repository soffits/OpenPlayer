# Vanilla Survival Reference

This is docs-only advisory reference text. OpenPlayer does not automatically load, parse, or execute this file at runtime.

Use this kind of guide to help an LLM decompose ordinary vanilla Minecraft goals into available OpenPlayer primitives. The runtime must still validate every emitted intent and every world/inventory/combat mutation through reviewed capability adapters.

## General Survival

- Observe current health, inventory, nearby hazards, current dimension, and capability diagnostics with status/context first when state is unclear.
- Prefer loaded, visible, player-like actions: move, follow, collect visible drops, select carried tools, use the currently selected usable item when explicitly requested, defend against loaded hostile danger entities, and stop/cancel when recovery is unclear.
- If a needed action is missing, report the missing primitive or adapter instead of inventing success.

## Resource Gathering

- Prefer exact item ids and registry-backed recipes.
- Use visible dropped-item acquisition when the target stack is already loaded and reachable.
- Treat crafting or smelting as strategy decomposition only unless server recipe data and reviewed generic container/material capability gates are available.
- Hidden mining, bucket workflows, trading, fishing, custom machines, and long-range searches need explicit adapters before mutation or success can be claimed.

## Travel Preparation

- Treat dimensions as ResourceLocation ids, not as a closed vanilla-only set.
- For vanilla Nether travel, use loaded portal evidence or the scoped vanilla Nether portal build adapter when materials and policy allow it.
- For unknown or modded dimensions, rely on observation, loaded portal evidence, loaded exploration, owner-follow in the same dimension, inventory prep, and missing-adapter diagnostics.

## Building And Trading

- Building should use strict reviewed primitives such as small lines, walls, floors, boxes, or stairs with exact carried materials.
- Villager trading and modded trading UIs need a reviewed no-loss UI/trade-state adapter before any trade can be executed or claimed complete.
