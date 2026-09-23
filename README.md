# CdrKnockout

CdrKnockout adalah sistem knockout/revive modular untuk Paper yang dibuat oleh **CADERA / MENKIESTES**.

Versi saat ini: **v0.8.0 — Gameplay Expansion**

> Status: alpha / development. Core knockout, passive revive, requirement engine, bleedout/death, AxGraves compatibility, persistence/recovery, execution, Java/Bedrock compatibility, public API, PlaceholderAPI, dan gameplay expansion sudah tersedia.

## Target platform

- Paper 1.21.11
- Java 21
- Java Edition
- Bedrock via Geyser/Floodgate
- Optional Vault, AuraSkills, AxGraves, PlaceholderAPI

## Core flow

```text
lethal damage
  -> KNOCKED
  -> revive / self-revive
     atau
  -> bleedout / fatal downed damage / giveup / execution
  -> REAL DEATH
  -> PlayerDeathEvent normal
  -> AxGraves
```

## Passive Revive

Default:

```text
<= 1 block + sneak + GOLDEN_APPLE + tahan 8 detik
```

Tidak membutuhkan klik. Requirement: ITEM, XP_LEVEL, MONEY, AURASKILLS, PERMISSION dengan mode ALL/ANY.

## Execution

Default:

```text
Target KNOCKED + executor <= 1 block + sneak + sword + tahan 3 detik
```

## v0.8.0 Gameplay Expansion

### Self Revive

Player KNOCKED dapat melakukan self-revive dengan item khusus. Default `ENCHANTED_GOLDEN_APPLE`, channel 10 detik, cooldown 120 detik, item dikonsumsi saat sukses. Bisa auto-start atau manual lewat `/selfrevive`.

### Medic Role & Medical Kit

Permission medic default:

```text
cdrknockout.medic
```

Admin memberi Medical Kit:

```text
/medkit <player> [amount]
```

Medical Kit memakai PDC marker sehingga PAPER biasa tidak dianggap medkit. Default medkit dapat melewati requirement revive standar dan memakai passive revive channel yang sama.

### Distress

Player KNOCKED dapat memanggil bantuan:

```text
/distress
```

Radius, cooldown, receiver permission, dan sound configurable.

### Statistics

Statistik tersimpan di:

```text
plugins/CdrKnockout/statistics.yml
```

Command:

```text
/kostats [player]
```

Tracked: knockouts, revives received, knockout deaths, self revives, distress signals, medical kit uses.

Dokumentasi: `docs/GAMEPLAY-EXPANSION.md` dan `docs/GAMEPLAY-EXPANSION-REGRESSION.md`.

## Java & Bedrock

Deteksi client `JAVA / BEDROCK / UNKNOWN` menggunakan Geyser/Floodgate secara optional. Pose per-platform: `SWIMMING`, `CROUCH`, `NONE`.

```text
/cdrko platform <player>
/cdrko compat
```

## Public API & Events

API tersedia lewat Bukkit ServicesManager / `CdrKnockoutProvider`.

Events:

```text
CdrKnockoutEvent
CdrRevivedEvent
CdrKnockoutDeathEvent
```

## PlaceholderAPI

Expansion `%cdrknockout_*%` optional. Selain state/time/platform/revive/execution, v0.8.0 menambahkan placeholder gameplay/statistik untuk self-revive, distress cooldown, dan stats.

## Commands

```text
/cdrko reload
/cdrko knockout <player>
/cdrko revive <player>
/cdrko kill <player>
/cdrko status <player>
/cdrko compat
/cdrko platform <player>
/giveup
/selfrevive
/distress
/kostats [player]
/medkit <player> [amount]
```

## Build

```bash
mvn clean package
```

Output:

```text
target/CdrKnockout-0.8.0.jar
```

## Carry System

Milestone v0.5.0 Carry System sengaja di-skip supaya pose KO tidak bergantung pada passenger/ArmorStand mechanics.

## License

CdrKnockout menggunakan **MENKIESTES SOFTWARE LICENSE v1.0**. Implementasi dibuat sebagai kode baru/clean-room.
