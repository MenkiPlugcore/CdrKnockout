# CdrKnockout

CdrKnockout adalah sistem knockout/revive modular untuk Paper yang dibuat oleh **CADERA / MENKIESTES**.

Versi saat ini: **v0.7.0 — API & Expansion**

> Status: alpha / development. Core knockout, passive proximity revive, requirement engine, bleedout/death, AxGraves compatibility, persistence/recovery, execution, Java/Bedrock compatibility, public API, custom events, dan PlaceholderAPI expansion sudah tersedia.

## Target platform

- Paper 1.21.11
- Java 21
- Java Edition client
- Bedrock Edition via Geyser
- Optional Floodgate integration
- Optional Vault + economy provider
- Optional AuraSkills
- Optional AxGraves
- Optional PlaceholderAPI

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

## Execution

Default execution:

```text
Target KNOCKED
+ executor <= 1 block
+ executor sneak
+ sword di main hand
+ tahan 3 detik
```

Execution dan revive saling eksklusif. Execution selesai melalui managed real-death flow sehingga AxGraves tetap menerima death event normal.

## Stability & Safety

- Persistent state di `plugins/CdrKnockout/knockouts.yml`.
- Relog/restart/reload recovery.
- Offline bleedout configurable.
- Teleport `BLOCK` / `FOLLOW`.
- Duplicate KO guard.
- World-change reassert safety-net.

## Java & Bedrock Compatibility

CdrKnockout mendeteksi `JAVA / BEDROCK / UNKNOWN` melalui Geyser API dan fallback Floodgate API tanpa hard dependency.

Pose dapat diatur per platform:

```yaml
compatibility:
  client:
    pose:
      java-mode: SWIMMING
      bedrock-mode: SWIMMING
      unknown-mode: SWIMMING
```

Mode: `SWIMMING`, `CROUCH`, `NONE`.

Diagnostics:

```text
/cdrko compat
/cdrko platform <player>
```

## AxGraves Compatibility

- AxGraves sebagai `softdepend`.
- Runtime `GravePreSpawnEvent` guard.
- Tidak ada grave saat hanya KNOCKED.
- Managed death duplicate-grave guard.
- Inventory/EXP grave tetap dikelola AxGraves.

## v0.7.0 Public API

Plugin lain dapat mengambil API melalui Bukkit ServicesManager:

```java
CdrKnockoutApi api = CdrKnockoutProvider.get();

if (api.isKnocked(player)) {
    long remaining = api.getRemainingSeconds(player);
}

api.knockout(player);
api.revive(player);
```

API menyediakan:

- status `KNOCKED` dan managed death,
- sisa bleedout,
- immutable `KnockoutSnapshot`,
- knockout/revive/force-death/give-up,
- status revive/execution,
- platform Java/Bedrock,
- active pose mode.

Service juga dapat diambil langsung:

```java
CdrKnockoutApi api = Bukkit.getServicesManager().load(CdrKnockoutApi.class);
```

Dokumentasi developer: `docs/API-EXPANSION.md`.

## Custom Events

Post-transition Bukkit events tersedia:

```text
CdrKnockoutEvent
CdrRevivedEvent
CdrKnockoutDeathEvent
```

`CdrKnockoutEvent` membedakan KO biasa dan recovery dari persistence. `CdrKnockoutDeathEvent` memberi flag apakah kematian berasal dari managed CdrKnockout death flow.

## PlaceholderAPI

Jika PlaceholderAPI terpasang, expansion `%cdrknockout_*%` diregistrasikan otomatis.

Placeholder tersedia:

```text
%cdrknockout_version%
%cdrknockout_state%
%cdrknockout_is_knocked%
%cdrknockout_death_in_progress%
%cdrknockout_time%
%cdrknockout_time_formatted%
%cdrknockout_platform%
%cdrknockout_pose%
%cdrknockout_being_revived%
%cdrknockout_reviver%
%cdrknockout_being_executed%
%cdrknockout_executor%
```

`state` dapat menghasilkan `NORMAL`, `KNOCKED`, `REVIVING`, `EXECUTING`, atau `DYING`.

PlaceholderAPI optional. Tanpa PlaceholderAPI, core CdrKnockout tetap berjalan normal.

## Carry System

Milestone `v0.5.0 — Carry System` **SKIPPED**. Carry/passenger tidak dimasukkan agar pose KO tidak bergantung pada passenger/ArmorStand mechanics.

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
```

Alias admin: `/cdrknockout`, `/cko`.

## Build

```bash
mvn clean package
```

Output:

```text
target/CdrKnockout-0.7.0.jar
```

## Regression utama v0.7.0

1. Plugin tanpa PlaceholderAPI harus tetap enable normal.
2. Plugin dengan PlaceholderAPI harus mendaftarkan `%cdrknockout_*%`.
3. API service harus tersedia melalui `CdrKnockoutProvider` / ServicesManager.
4. Lethal KO harus memicu tepat satu `CdrKnockoutEvent`.
5. Admin/API KO juga harus memicu event melalui transition bridge.
6. Revive harus memicu tepat satu `CdrRevivedEvent`.
7. Bleedout/execution/giveup harus memicu `CdrKnockoutDeathEvent` dan tetap menghasilkan satu grave AxGraves.
8. Reload tidak boleh menggandakan transition event untuk player yang sudah KNOCKED.
9. Java/Bedrock flow v0.6.0 harus tetap bekerja.

## License

CdrKnockout menggunakan **MENKIESTES SOFTWARE LICENSE v1.0**. Source-available tidak berarti open-source. Lihat [LICENSE](LICENSE).

Implementasi CdrKnockout dibuat sebagai kode baru/clean-room; plugin pihak ketiga hanya digunakan sebagai referensi perilaku dan pengujian kompatibilitas, bukan sebagai sumber kode yang disalin.
