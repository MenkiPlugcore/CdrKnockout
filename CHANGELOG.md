# Changelog

Semua perubahan penting CdrKnockout dicatat di file ini.

## [0.2.0] - 2026-09-22

### Added
- Modular Revive Requirement Engine.
- Requirement `ITEM`, `XP_LEVEL`, `MONEY`, `AURASKILLS`, dan `PERMISSION`.
- Semua requirement dapat di-enable/disable dari config.
- Mode `ALL`: semua requirement aktif wajib terpenuhi.
- Mode `ANY`: cukup satu requirement aktif berdasarkan configurable priority.
- Requirement terpilih dikunci per revive session agar tidak berganti di tengah channel.
- Item cost hanya dikonsumsi saat revive sukses.
- Optional XP level consumption saat revive sukses.
- Optional Vault balance check + withdraw saat revive sukses.
- Optional AuraSkills built-in skill level check melalui soft integration.
- Optional permission requirement.
- Requirement re-validation selama channel dan tepat sebelum revive selesai.
- Cooldown pesan requirement agar tidak spam ketika syarat belum terpenuhi.
- `softdepend` untuk Vault dan AuraSkills tanpa menjadikan keduanya dependency wajib.

### Changed
- Default behavior tetap ITEM-only dengan `GOLDEN_APPLE`, sehingga setup v0.1.1 tetap familiar.
- Passive Revive sekarang menggunakan Requirement Engine sebagai sumber validasi tunggal.

## [0.1.1] - 2026-09-22

### Added
- Passive Proximity Revive tanpa klik kiri/kanan.
- Reviver wajib maksimal 1 block, jongkok, dan memegang item revive di main hand.
- Default revive item: `GOLDEN_APPLE`.
- Configurable revive duration, distance, item, amount, consumption, health, dan resistance.
- Revive item hanya dikonsumsi setelah revive berhasil 100%.
- Anti-consume: revive item tidak termakan ketika kondisi passive revive terpenuhi.
- Revive progress ActionBar untuk reviver dan target.
- Auto-cancel saat reviver berhenti jongkok, menjauh, mengganti item, mati/KO/logout/pindah world.
- Optional cancel saat reviver terkena damage, menyerang, atau bergerak.
- Satu reviver hanya dapat menangani satu target sekaligus.
- Knockout ActionBar disuppress selama target sedang direvive agar progress tidak flicker.
- `messages.yml` memakai bundled defaults sehingga upgrade config lama tetap mendapat message key baru.

## [0.1.0] - 2026-09-22

### Added
- Core lethal-damage interception.
- Knockout state/session manager.
- Configurable knockout timer and bleedout.
- Prone/swimming pose engine.
- Position lock with free camera movement.
- Configurable blindness, weakness, slowness, and darkness effects.
- Gameplay restrictions while knocked.
- ALL/WHITELIST/BLACKLIST world policy.
- Totem of Undying protection.
- `/cdrko reload|knockout|revive|kill|status`.
- Admin/debug permissions.
- `config.yml` and `messages.yml`.
- Real-death pass-through design for grave plugins such as AxGraves.
- MENKIESTES SOFTWARE LICENSE v1.0.
