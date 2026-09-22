# CdrKnockout

CdrKnockout adalah sistem knockout/revive modular untuk Paper yang dibuat oleh **CADERA / MENKIESTES**.

Versi saat ini: **v0.3.1 — Stability & Safety**

> Status: alpha / development. Core knockout, passive proximity revive, requirement engine, advanced bleedout/death, AxGraves compatibility, dan persistent recovery sudah tersedia.

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

## AxGraves Compatibility

CdrKnockout tidak mengganti sistem grave AxGraves. AxGraves tetap menjadi pemilik inventory/EXP grave ketika `PlayerDeathEvent` benar-benar terjadi.

Compatibility layer:
- `AxGraves` sebagai `softdepend`.
- Runtime `GravePreSpawnEvent` guard.
- Grave diblok jika player masih `KNOCKED`.
- Duplicate grave diblok untuk satu managed-death cycle.
- `/cdrko compat` untuk diagnostics.

## v0.3.1 Stability & Safety

State KNOCKED sekarang dapat bertahan melewati logout, restart server, maupun reload plugin.

- State disimpan di `plugins/CdrKnockout/knockouts.yml`.
- KO, quit, revive, dan real death melakukan persistence flush pada transition penting.
- Player yang relog dikembalikan ke state KNOCKED dan anchor semula.
- `offline-time-counts: true` membuat logout tidak dapat dipakai untuk membekukan bleedout.
- Jika timer habis ketika offline, player masuk real death setelah recovery saat login.
- `offline-time-counts: false` tersedia jika server ingin bleedout pause selama offline.
- Teleport KNOCKED default `BLOCK`.
- Mode teleport `FOLLOW` tersedia jika plugin lain memang perlu memindahkan player KO; anchor ikut dipindahkan secara aman.
- World-change safety-net mengembalikan player ke anchor jika perpindahan world lolos dari event teleport.
- KO creation memakai guard terhadap duplicate session.
- Revive channel tetap dibatalkan saat target masuk managed real-death path.

Default:

```yaml
stability:
  persistence:
    enabled: true
    autosave-ticks: 40
    offline-time-counts: true
    restore-anchor-on-join: true
    join-recovery-delay-ticks: 2
    missing-world-policy: CURRENT

  teleport:
    mode: BLOCK
    reassert-on-world-change: true
```

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
target/CdrKnockout-0.3.1.jar
```

## Stability regression flow

1. Knock player lalu logout; login lagi harus kembali KNOCKED.
2. Knock player lalu restart server; login lagi harus recover dengan timer yang benar.
3. Dengan `offline-time-counts: true`, tunggu sampai timer habis ketika offline; login harus menuju real death.
4. Coba `/tp` atau teleport plugin saat KO; default harus diblok.
5. Set `stability.teleport.mode: FOLLOW`, teleport ulang; anchor harus ikut pindah dan movement lock tetap benar.
6. Spam lethal hit pada tick yang sama; hanya satu KO session boleh dibuat.
7. Spam revive dari beberapa player; hanya satu revive channel target yang boleh aktif.
8. Setelah bleedout/giveup, AxGraves harus tetap membuat tepat satu grave.

## License

CdrKnockout menggunakan **MENKIESTES SOFTWARE LICENSE v1.0**. Source-available tidak berarti open-source. Lihat [LICENSE](LICENSE).

Implementasi CdrKnockout dibuat sebagai kode baru/clean-room; plugin pihak ketiga hanya digunakan sebagai referensi perilaku dan pengujian kompatibilitas, bukan sebagai sumber kode yang disalin.
