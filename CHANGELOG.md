# Changelog

Semua perubahan penting CdrKnockout dicatat di file ini.

## [0.7.0] - 2026-09-23

### Added
- Public `CdrKnockoutApi` service yang diregistrasikan melalui Bukkit `ServicesManager`.
- `CdrKnockoutProvider` helper untuk consumer plugin.
- Immutable `KnockoutSnapshot` untuk membaca state tanpa mengekspos internal session object.
- API methods untuk status KO, remaining time, knockout, revive, force death, give up, revive/execution state, client platform, dan active pose mode.
- Custom Bukkit post-transition events: `CdrKnockoutEvent`, `CdrRevivedEvent`, `CdrKnockoutDeathEvent`.
- Recovery flag pada `CdrKnockoutEvent` untuk membedakan persistent relog/restart recovery.
- Managed-death flag pada `CdrKnockoutDeathEvent`.
- Optional PlaceholderAPI expansion `%cdrknockout_*%`.
- Placeholder state/time/platform/pose/revive/execution/version.
- Config `api.events.*` dan `placeholderapi.enabled`.
- Developer documentation `docs/API-EXPANSION.md`.

### Compatibility
- PlaceholderAPI tetap optional melalui softdepend dan lazy reflective expansion loading.
- Server tanpa PlaceholderAPI tidak memuat class expansion dan core tetap dapat enable.
- API transition bridge tidak mengubah core death/revive behaviour atau AxGraves flow.
- Reload mempertahankan tracked KO transition state agar tidak menggandakan custom event.

## [0.6.0] - 2026-09-23

### Added
- Java/Bedrock client detection tanpa hard dependency.
- Reflective Geyser API hook menggunakan `GeyserApi#isBedrockPlayer(UUID)` ketika tersedia.
- Reflective Floodgate API fallback menggunakan `FloodgateApi#isFloodgatePlayer(UUID)` ketika tersedia.
- Platform-aware knockout pose modes: `SWIMMING`, `CROUCH`, `NONE`.
- Config `compatibility.client.detection.*` dan `compatibility.client.pose.*`.
- `/cdrko platform <player>` untuk melihat platform, detector, Geyser/Floodgate version, dan active KO pose.
- `/cdrko compat` sekarang juga menampilkan diagnostics Geyser/Floodgate.
- `Geyser-Spigot` dan `floodgate` sebagai optional softdepend.
- Persisted original sneaking state agar crossplay pose dapat dipulihkan setelah revive/recovery.

### Changed
- Core pose enforcement sekarang melalui `PoseEngine` dan tidak lagi hard-coded ke swimming untuk semua client.
- `knockouts.yml` persistence format naik ke version 2, tetap backward-compatible dengan data lama.
- Build version lompat dari `0.4.0` ke `0.6.0` karena milestone Carry System dilewati.

### Skipped
- `v0.5.0 — Carry System` tidak dilanjutkan agar CdrKnockout tidak bergantung pada passenger/ArmorStand mechanics yang berisiko merusak pose dan crossplay behaviour.

### Compatibility
- Passive revive tetap proximity + sneak + item tanpa klik, sehingga control Java/Bedrock konsisten.
- Execution tetap proximity + sneak + sword tanpa klik.
- Standard Paper ActionBar/title/sound flow dipertahankan agar dapat diterjemahkan Geyser.
- AxGraves managed real-death path tidak diubah.

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
