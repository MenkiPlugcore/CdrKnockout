# Changelog

Semua perubahan penting CdrKnockout dicatat di file ini.

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
- `messages.yml` sekarang memakai bundled defaults sehingga upgrade config lama tetap mendapat message key baru.

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
