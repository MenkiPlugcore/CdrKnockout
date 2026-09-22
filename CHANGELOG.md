# Changelog

Semua perubahan penting CdrKnockout dicatat di file ini.

## [0.4.0] - 2026-09-22

### Added
- Passive Execution System untuk menghabisi player `KNOCKED` tanpa klik kiri/kanan.
- Default execution flow: maksimal 1 block + sneak + sword di main hand selama 3 detik.
- `ExecutionSession` dan `ExecutionManager` sebagai engine terpisah dari revive/pose.
- Configurable execution duration, distance, sneak requirement, permission, allowed materials, dan cancel conditions.
- ActionBar progress untuk executor dan target.
- Permission `cdrknockout.execute`.
- Execution item whitelist default seluruh vanilla sword.
- Dokumentasi `docs/EXECUTION-SYSTEM.md`.

### Safety
- Satu target hanya dapat memiliki satu execution session.
- Satu executor hanya dapat mengeksekusi satu target pada satu waktu.
- Revive dan execution dibuat mutual-exclusive.
- Execution intent mengambil prioritas ketika player memegang execution item sehingga requirement revive mode `ANY` tidak salah memilih executor sebagai reviver.
- Execution dibatalkan ketika executor/target tidak valid, berhenti sneak, keluar radius, mengganti item, logout, mati/KO, atau sesuai cancel config.
- Execution selesai melalui managed real-death path agar `PlayerDeathEvent` tetap normal dan AxGraves dapat membuat grave.

## [0.3.1] - 2026-09-22

### Added
- Persistent knockout state di `plugins/CdrKnockout/knockouts.yml`.
- Relog/restart/reload recovery untuk player yang masih KNOCKED.
- Config `stability.persistence.offline-time-counts` untuk memilih timer tetap berjalan atau pause saat offline.
- Recovery anchor dengan configurable missing-world policy.
- Teleport safety policy `BLOCK` / `FOLLOW`.
- World-change reassert safety-net.
- Persistence autosave dan immediate flush pada state transition penting.
- Recovery messages untuk relog/expired/missing-world.
- Duplicate KO session guard menggunakan `putIfAbsent`.

### Changed
- Logout tidak lagi otomatis menghapus status KNOCKED ketika persistence aktif.
- Shutdown menyimpan session sebelum membersihkan runtime state.
- Downed timer reduction ikut memperbarui persisted state.
- Revive dan real death menghapus persisted state sebelum menyelesaikan transisi.
- Teleport internal recovery diberi bypass guard agar tidak diblok listener sendiri.

### Safety
- Logout tidak dapat dipakai untuk menghindari bleedout jika `offline-time-counts: true`.
- Managed real death tetap mempertahankan AxGraves compatibility flow.
- Runtime recovery dibuat idempotent agar join/reload hook ganda tidak membuat duplicate session.

## [0.3.0] - 2026-09-22

### Added
- AxGraves runtime compatibility layer tanpa compile-time dependency.
- `AxGraves` ditambahkan sebagai `softdepend` untuk load-order compatibility.
- Reflective hook ke `GravePreSpawnEvent` ketika AxGraves tersedia.
- Safety guard yang membatalkan grave jika player masih berstatus `KNOCKED`.
- Duplicate-grave guard untuk CdrKnockout-managed real-death cycle.
- Config `compatibility.axgraves.*`.
- `/cdrko compat` untuk melihat status hook, versi AxGraves, dan jumlah grave yang diblok safety guard.
- Regression checklist `docs/AXGRAVES-COMPATIBILITY.md`.

### Compatibility
- CdrKnockout tetap tidak mengubah death drops, keepInventory, keepLevel, atau dropped EXP.
- Inventory/EXP grave tetap dikelola AxGraves pada `PlayerDeathEvent` nyata.
- Jika AxGraves tidak terpasang atau event API berubah, CdrKnockout tetap memakai natural PlayerDeathEvent flow tanpa mematikan core knockout.

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
