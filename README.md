# CdrKnockout

CdrKnockout adalah sistem knockout/revive modular untuk Paper yang dibuat oleh **CADERA / MENKIESTES**.

Versi saat ini: **v0.4.0 — Execution System**

> Status: alpha / development. Core knockout, passive proximity revive, requirement engine, bleedout/death, AxGraves compatibility, persistence/recovery, dan execution channel sudah tersedia.

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
  -> bleedout / fatal downed damage / /giveup / EXECUTION
  -> REAL DEATH
  -> PlayerDeathEvent normal
  -> AxGraves membuat grave
```

Selama player masih `KNOCKED`, inventory dan EXP tetap berada pada player dan `PlayerDeathEvent` belum dipicu.

## Passive Revive

Default revive:

```text
<= 1 block
+ sneak
+ GOLDEN_APPLE di main hand
+ tahan 8 detik
```

Tidak membutuhkan klik kiri/kanan. Requirement tersedia: `ITEM`, `XP_LEVEL`, `MONEY`, `AURASKILLS`, dan `PERMISSION`, dengan mode `ALL` atau `ANY`.

## Bleedout & Death Engine

- Warning configurable, default `30 / 10 / 5` detik.
- Heartbeat kondisi kritis.
- Downed damage: `IGNORE`, `REDUCE_TIMER`, `INSTANT_DEATH`.
- `/giveup`.
- Guard terhadap duplicate real-death queue.
- Real death tetap memakai flow Paper normal.

## AxGraves Compatibility

- AxGraves sebagai `softdepend`.
- Runtime `GravePreSpawnEvent` guard.
- Tidak ada grave saat hanya KNOCKED.
- Managed death duplicate-grave guard.
- Inventory/EXP grave tetap dikelola AxGraves.
- `/cdrko compat` untuk diagnostics.

## Stability & Safety

- Persistent state di `plugins/CdrKnockout/knockouts.yml`.
- Relog/restart/reload recovery.
- Offline bleedout configurable.
- Teleport `BLOCK` / `FOLLOW`.
- Duplicate KO guard.
- World-change reassert safety-net.

## v0.4.0 Execution System

Execution adalah passive channel untuk menghabisi player yang sedang `KNOCKED`.

Default:

```text
Target KNOCKED
+ executor <= 1 block
+ executor sneak
+ sword di main hand
+ tahan 3 detik
        ↓
EXECUTION COMPLETE
        ↓
managed real death
        ↓
AxGraves
```

Tidak perlu klik kiri atau kanan.

Default execution item:

- Wooden Sword
- Stone Sword
- Iron Sword
- Golden Sword
- Diamond Sword
- Netherite Sword

Execution dan revive saling eksklusif. Holding execution weapon memberi execution intent sehingga player tidak salah masuk revive channel walaupun requirement revive memakai mode lain.

Execution dapat dibatalkan saat executor berhenti sneak, menjauh, ganti item, mati/KO/logout, terkena damage, menyerang entity lain, atau target tidak lagi valid. Semua behaviour utama configurable.

Permission:

```text
cdrknockout.execute
```

Panduan lengkap: `docs/EXECUTION-SYSTEM.md`.

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
target/CdrKnockout-0.4.0.jar
```

## Regression utama v0.4.0

1. KO target -> tidak ada grave.
2. Revive dengan Golden Apple -> tetap revive normal.
3. Sword + sneak <=1 block -> execution channel muncul.
4. Lepas sneak / menjauh / ganti sword -> execution batal.
5. Execution 100% -> target real death.
6. AxGraves -> tepat satu grave.
7. Dua executor -> hanya satu session target.
8. Restart/relog target KNOCKED -> persistence v0.3.1 tetap bekerja.

## License

CdrKnockout menggunakan **MENKIESTES SOFTWARE LICENSE v1.0**. Source-available tidak berarti open-source. Lihat [LICENSE](LICENSE).

Implementasi CdrKnockout dibuat sebagai kode baru/clean-room; plugin pihak ketiga hanya digunakan sebagai referensi perilaku dan pengujian kompatibilitas, bukan sebagai sumber kode yang disalin.
