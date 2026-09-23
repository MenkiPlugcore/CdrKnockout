# CdrKnockout

CdrKnockout adalah sistem knockout/revive modular untuk Paper yang dibuat oleh **CADERA / MENKIESTES**.

Versi saat ini: **v0.6.0 — Java & Bedrock Compatibility**

> Status: alpha / development. Core knockout, passive proximity revive, requirement engine, bleedout/death, AxGraves compatibility, persistence/recovery, execution channel, dan Java/Bedrock client compatibility sudah tersedia.

## Target platform

- Paper 1.21.11
- Java 21
- Java Edition client
- Bedrock Edition via Geyser
- Optional Floodgate integration
- Optional Vault + economy provider
- Optional AuraSkills
- Optional AxGraves

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

## AxGraves Compatibility

- AxGraves sebagai `softdepend`.
- Runtime `GravePreSpawnEvent` guard.
- Tidak ada grave saat hanya KNOCKED.
- Managed death duplicate-grave guard.
- Inventory/EXP grave tetap dikelola AxGraves.
- `/cdrko compat` untuk diagnostics.

## v0.6.0 Java & Bedrock Compatibility

CdrKnockout sekarang mendeteksi platform client tanpa hard dependency.

Detection order:

```text
Geyser API
  -> jika tersedia, cek isBedrockPlayer(UUID)
Floodgate API
  -> fallback cek isFloodgatePlayer(UUID)
Tidak ada hook
  -> UNKNOWN
```

Geyser/Floodgate diakses secara reflektif sehingga plugin tetap dapat berjalan di server Java-only.

Pose KNOCKED sekarang configurable per platform:

```yaml
compatibility:
  client:
    detection:
      enabled: true
      log-status: true

    pose:
      java-mode: SWIMMING
      bedrock-mode: SWIMMING
      unknown-mode: SWIMMING
```

Mode pose:

- `SWIMMING` — prone/crawl-style pose utama.
- `CROUCH` — fallback untuk build Geyser/Bedrock yang tidak merender SWIMMING dengan benar.
- `NONE` — tidak memaksa pose; movement lock/effect KO tetap aktif.

Original swimming dan sneaking state disimpan sehingga pose dapat dipulihkan setelah revive/death/recovery.

Diagnostics:

```text
/cdrko compat
/cdrko platform <player>
```

`/cdrko platform <player>` menampilkan hasil deteksi `JAVA / BEDROCK / UNKNOWN`, status hook Geyser/Floodgate, dan pose mode yang aktif untuk player tersebut.

Passive revive dan execution tetap no-click, sehingga tidak bergantung pada perbedaan click/touch control Java dan Bedrock.

## Carry System

Milestone `v0.5.0 — Carry System` **SKIPPED**. Carry/passenger tidak dimasukkan agar pose KO tidak bergantung pada passenger/ArmorStand mechanics dan core tetap stabil.

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
target/CdrKnockout-0.6.0.jar
```

## Crossplay regression v0.6.0

1. Java player -> `/cdrko platform` harus mendeteksi `JAVA` jika Geyser/Floodgate hook aktif.
2. Bedrock player -> `/cdrko platform` harus mendeteksi `BEDROCK`.
3. KO Java -> pose, movement lock, blindness, ActionBar, title, heartbeat berjalan.
4. KO Bedrock -> pose SWIMMING diuji; jika visual tidak sesuai, set `bedrock-mode: CROUCH` lalu reload.
5. Bedrock revive -> dekat <=1 block + Golden Apple + sneak, tanpa klik.
6. Bedrock execution -> dekat <=1 block + sword + sneak, tanpa klik.
7. Relog/restart player Bedrock saat KO -> state dan pose dipulihkan.
8. Bleedout/execution -> AxGraves harus tetap membuat tepat satu grave.

## License

CdrKnockout menggunakan **MENKIESTES SOFTWARE LICENSE v1.0**. Source-available tidak berarti open-source. Lihat [LICENSE](LICENSE).

Implementasi CdrKnockout dibuat sebagai kode baru/clean-room; plugin pihak ketiga hanya digunakan sebagai referensi perilaku dan pengujian kompatibilitas, bukan sebagai sumber kode yang disalin.
