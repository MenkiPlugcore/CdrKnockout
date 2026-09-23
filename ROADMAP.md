# CdrKnockout Roadmap

## ✅ v0.1.0 — Core Knockout System
Lethal interception, KNOCKED state, prone pose, movement/action restrictions, effects, timer, world policy, admin commands, config/messages, debug.

## ✅ v0.1.1 — Passive Revive Core
Reviver maksimal 1 block, wajib sneak + pegang item revive di main hand, tanpa klik, channel progress, cancel conditions, consume item hanya saat sukses.

## ✅ v0.2.0 — Requirement Engine
Toggle item, XP level, Vault money, AuraSkills, permission; mode ALL/ANY; requirement session locking; cost processing on success.

## ✅ v0.2.1 — Bleedout & Death Engine
Warning/heartbeat, downed-damage modes, per-cause environmental handling, `/giveup`, guarded real-death queue, AxGraves-friendly `PlayerDeathEvent` pass-through.

## ✅ v0.3.0 — AxGraves Compatibility
AxGraves softdepend, runtime GravePreSpawnEvent guard, no-grave-on-KO protection, managed death duplicate-grave guard, compatibility diagnostics, inventory/EXP ownership tetap di AxGraves, regression checklist.

## ✅ v0.3.1 — Stability & Safety
Persistent `knockouts.yml`, relog/restart/reload recovery, configurable offline bleedout, duplicate KO guard, teleport BLOCK/FOLLOW policy, world-change reassertion, safe cleanup pada revive/death/quit.

## ✅ v0.4.0 — Execution System
Passive execution channel tanpa klik: executor maksimal 1 block, sneak + execution item, progress ActionBar, cancellation guards, revive/execution mutual exclusion, permission, dan managed real-death flow yang tetap kompatibel dengan AxGraves.

## ⏭ v0.5.0 — Carry System — SKIPPED
Carry/drop sengaja tidak dilanjutkan agar pose KO tetap sederhana, stabil, dan tidak bergantung pada passenger/ArmorStand mechanics.

## ✅ v0.6.0 — Java & Bedrock Compatibility
Reflective Geyser/Floodgate client detection, platform-aware pose mode `SWIMMING/CROUCH/NONE`, persisted pose state, softdepend Geyser/Floodgate, `/cdrko platform`, dan compatibility diagnostics. Passive revive/execution tetap no-click sehingga aman untuk Java maupun Bedrock.

## ✅ v0.7.0 — API & Expansion
Public Bukkit ServicesManager API, immutable knockout snapshot, transition events (`CdrKnockoutEvent`, `CdrRevivedEvent`, `CdrKnockoutDeathEvent`), optional PlaceholderAPI expansion, state/time/platform/pose/revive/execution placeholders, dan developer documentation.

## v0.8.0 — Gameplay Expansion
Self revive, medical roles/items, revive modifiers, distress system, statistics.

## v1.0.0 — Production Stable
Production release setelah regression test Java/Bedrock, PvP/PvE, environmental deaths, restart/relog, multi-player concurrency, API/event integrations, PlaceholderAPI, dan AxGraves.
