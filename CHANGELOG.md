# Changelog

Semua perubahan penting CdrKnockout dicatat di file ini.

## [0.2.1] - 2026-09-22

### Added
- Advanced Bleedout & Death Engine.
- Configurable warning threshold, default 30/10/5 detik.
- Critical heartbeat sound menjelang bleedout.
- Downed damage modes: `IGNORE`, `REDUCE_TIMER`, `INSTANT_DEATH`.
- Per-damage-cause override untuk environmental dan combat damage.
- Default handling untuk VOID, lava, fire, drowning, suffocation, freeze, explosion, dan fall.
- `/giveup` command + permission `cdrknockout.giveup`.
- ActionBar feedback ketika downed damage memotong bleedout timer.
- Death-in-progress guard untuk mencegah duplicate real-death queue.
- Real death dijalankan di scheduler agar tidak re-entrant di dalam damage listener.

### Changed
- `VOID` tidak lagi di-ignore pada default config sehingga dapat melewati environmental KO/death flow.
- Damage saat KNOCKED sekarang diproses oleh bleedout engine, bukan sekadar selalu di-ignore.
- Bleedout timeout, admin kill, giveup, dan fatal downed damage memakai satu real-death path yang konsisten.

## [0.2.0] - 2026-09-22

### Added
- Modular Revive Requirement Engine.
- Requirement `ITEM`, `XP_LEVEL`, `MONEY`, `AURASKILLS`, dan `PERMISSION`.
- Mode `ALL` dan `ANY`.
- Requirement session locking dan cost processing on success.
- Optional Vault dan AuraSkills soft integration.

## [0.1.1] - 2026-09-22

### Added
- Passive Proximity Revive tanpa klik kiri/kanan.
- Reviver wajib maksimal 1 block, jongkok, dan memegang item revive di main hand.
- Configurable revive duration, item, consumption, result health, resistance, dan cancel conditions.

## [0.1.0] - 2026-09-22

### Added
- Core lethal-damage interception.
- Knockout state/session manager.
- Configurable knockout timer and bleedout.
- Prone/swimming pose engine.
- Position lock with free camera movement.
- Configurable effects and gameplay restrictions.
- World policy, admin commands, debug, config/messages.
- Real-death pass-through design for grave plugins such as AxGraves.
- MENKIESTES SOFTWARE LICENSE v1.0.
