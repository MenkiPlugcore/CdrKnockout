# CdrKnockout Roadmap

## ✅ v0.1.0 — Core Knockout System
Lethal interception, KNOCKED state, prone pose, movement/action restrictions, effects, timer, world policy, admin commands, config/messages, debug.

## ✅ v0.1.1 — Passive Revive Core
Reviver maksimal 1 block, sneak + item revive di main hand, tanpa klik, channel progress, cancel conditions, consume item hanya saat sukses.

## ✅ v0.2.0 — Requirement Engine
Item, XP level, Vault money, AuraSkills, permission; ALL/ANY; requirement locking dan cost on success.

## ✅ v0.2.1 — Bleedout & Death Engine
Warning/heartbeat, downed-damage modes, environmental handling, `/giveup`, guarded real-death flow.

## ✅ v0.3.0 — AxGraves Compatibility
No-grave-on-KO guard, managed death duplicate-grave protection, normal PlayerDeathEvent pass-through.

## ✅ v0.3.1 — Stability & Safety
Persistent `knockouts.yml`, relog/restart recovery, offline bleedout, teleport BLOCK/FOLLOW, cleanup guards.

## ✅ v0.4.0 — Execution System
Passive execution: <=1 block + sneak + execution item, progress, cancellation, revive/execution mutual exclusion.

## ⏭ v0.5.0 — Carry System — SKIPPED
Carry/passenger sengaja tidak diterapkan agar pose KO tetap stabil.

## ✅ v0.6.0 — Java & Bedrock Compatibility
Geyser/Floodgate client detection, platform-aware pose, persistence, crossplay diagnostics.

## ✅ v0.7.0 — API & Expansion
Public API, immutable snapshot, custom transition events, optional PlaceholderAPI expansion.

## ✅ v0.8.0 — Gameplay Expansion
Self-revive, medic role + PDC Medical Kit, configurable gameplay modifiers/requirement bypass, distress signal, persistent statistics, gameplay commands, dan PlaceholderAPI additions.

## ✅ v1.0.0 — Production Stable
Production hardening: atomic YAML persistence, reload-safe storage state, built-in KO recovery command compatibility, startup/reload diagnostics, `/cdrko doctor`, CI release verification, dan final production regression checklist.

## Post-1.0 policy
v1.0.x difokuskan untuk bugfix/backward compatibility. Fitur besar berikutnya harus masuk minor release baru agar core production tidak berubah tanpa kebutuhan.
