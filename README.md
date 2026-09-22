# CdrKnockout

CdrKnockout adalah sistem knockout/revive modular untuk Paper yang dibuat oleh **CADERA / MENKIESTES**.

Versi saat ini: **v0.3.0 — AxGraves Compatibility**

> Status: alpha / development. Core knockout, passive proximity revive, requirement engine, advanced bleedout/death, dan compatibility guard AxGraves sudah tersedia.

## Target platform

- Paper 1.21.11
- Java 21
- Optional integration: Vault + economy provider
- Optional integration: AuraSkills
- Optional integration: AxGraves

## Core flow

```text
lethal damage
  -> KNOCKED
  -> prone + movement lock + bleedout timer
  -> REVIVED
     atau
  -> timer habis / fatal downed damage / /giveup
  -> REAL DEATH
  -> PlayerDeathEvent normal
  -> AxGraves membuat grave
```

Selama player masih `KNOCKED`, CdrKnockout belum menganggap player mati. Inventory dan EXP tidak dipindahkan oleh CdrKnockout dan `PlayerDeathEvent` tidak dipicu.

## Passive Revive

Reviver harus berada maksimal `1.0` block, jongkok, dan memenuhi requirement aktif. Tidak perlu klik kiri/kanan. Default requirement adalah `1x GOLDEN_APPLE` di main hand dan channel `8` detik.

Requirement tersedia: `ITEM`, `XP_LEVEL`, `MONEY`, `AURASKILLS`, dan `PERMISSION`, dengan mode `ALL` atau `ANY`.

## Bleedout & Death Engine

- Warning bleedout configurable, default `30 / 10 / 5` detik.
- Heartbeat saat kondisi kritis.
- Downed damage mode: `IGNORE`, `REDUCE_TIMER`, `INSTANT_DEATH`.
- Per-damage-cause override untuk VOID, lava, fire, drowning, suffocation, freeze, explosion, fall, dan cause Bukkit lainnya.
- `/giveup` untuk menyerah saat KNOCKED.
- Death guard mencegah multiple real-death queue.
- Real death tetap memakai alur kematian Paper normal.

## v0.3.0 AxGraves Compatibility

CdrKnockout tidak mengganti sistem grave AxGraves. AxGraves tetap menjadi pemilik inventory/EXP grave ketika `PlayerDeathEvent` benar-benar terjadi.

Compatibility layer menambahkan:

- `AxGraves` sebagai `softdepend`, sehingga load order lebih konsisten.
- Runtime detection versi AxGraves.
- Hook `GravePreSpawnEvent` tanpa compile-time dependency ke AxGraves.
- Grave diblok jika player masih `KNOCKED` dan belum memasuki real death.
- Duplicate `GravePreSpawnEvent` diblok untuk satu CdrKnockout-managed death cycle.
- Tidak mengubah `PlayerDeathEvent#getDrops()`, `keepInventory`, `keepLevel`, atau dropped EXP; pengelolaan item/EXP tetap diserahkan ke AxGraves.
- `/cdrko compat` untuk melihat status hook dan counter safety guard.

Default config:

```yaml
compatibility:
  axgraves:
    enabled: true
    block-graves-while-knocked: true
    prevent-duplicate-managed-graves: true
    duplicate-window-ticks: 40
    log-diagnostics: false
```

Jika AxGraves tidak terpasang, CdrKnockout tetap bekerja normal.

## Commands

```text
/cdrko reload
/cdrko knockout <player>
/cdrko revive <player>
/cdrko kill <player>
/cdrko status <player>
/cdrko compat
/giveup
```

Alias admin: `/cdrknockout`, `/cko`.

## Build

```bash
mvn clean package
```

Output:

```text
target/CdrKnockout-0.3.0.jar
```

## AxGraves regression flow

1. `/cdrko knockout <player>` -> player harus KNOCKED tanpa grave.
2. Revive player -> tidak boleh ada grave dan inventory/EXP tetap utuh.
3. Knock lagi lalu biarkan bleedout -> tepat satu real death dan satu grave.
4. Uji `/giveup` -> tepat satu grave.
5. Uji fatal downed damage / VOID -> tepat satu grave.
6. Ambil grave dan verifikasi item + EXP sesuai config AxGraves.
7. Jalankan `/cdrko compat` untuk memastikan status `ACTIVE` ketika AxGraves terpasang.

Checklist lebih lengkap ada di `docs/AXGRAVES-COMPATIBILITY.md`.

## License

CdrKnockout menggunakan **MENKIESTES SOFTWARE LICENSE v1.0**. Source-available tidak berarti open-source. Lihat [LICENSE](LICENSE).

Implementasi CdrKnockout dibuat sebagai kode baru/clean-room; plugin pihak ketiga hanya digunakan sebagai referensi perilaku dan pengujian kompatibilitas, bukan sebagai sumber kode yang disalin.
