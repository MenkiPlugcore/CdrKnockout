# CdrKnockout

CdrKnockout adalah sistem knockout/revive modular untuk Paper yang dibuat oleh **CADERA / MENKIESTES**.

Versi saat ini: **v0.2.1 — Bleedout & Death Engine**

> Status: alpha / development. Core knockout, passive proximity revive, requirement engine, dan advanced bleedout/death flow sudah tersedia. Regression AxGraves formal mengikuti v0.3.0.

## Target platform

- Paper 1.21.11
- Java 21
- Optional integration: Vault + economy provider
- Optional integration: AuraSkills
- Real death tetap melewati `PlayerDeathEvent` normal agar grave plugin seperti AxGraves dapat menangani kematian asli.

## Core flow

```text
lethal damage
  -> KNOCKED
  -> prone + movement lock + bleedout timer
  -> REVIVED
     atau
  -> timer habis / fatal downed damage / /giveup
  -> REAL DEATH
  -> PlayerDeathEvent
  -> grave plugin seperti AxGraves
```

## Passive Revive

Reviver harus berada maksimal `1.0` block, jongkok, dan memenuhi requirement aktif. Tidak perlu klik kiri/kanan. Default requirement adalah `1x GOLDEN_APPLE` di main hand dan channel `8` detik.

Requirement tersedia: `ITEM`, `XP_LEVEL`, `MONEY`, `AURASKILLS`, dan `PERMISSION`, dengan mode `ALL` atau `ANY`.

## v0.2.1 Bleedout & Death Engine

- Warning bleedout configurable, default `30 / 10 / 5` detik.
- Heartbeat saat kondisi kritis.
- Downed damage mode: `IGNORE`, `REDUCE_TIMER`, `INSTANT_DEATH`.
- Per-damage-cause override untuk VOID, lava, fire, drowning, suffocation, freeze, explosion, fall, dan cause lain dari Bukkit.
- VOID default menjadi `INSTANT_DEATH` setelah player sudah KNOCKED.
- Lava/fire/drowning/explosion default mempercepat bleedout.
- `/giveup` untuk menyerah saat KNOCKED.
- Death guard mencegah multiple real-death queue.
- Real death dijadwalkan aman di luar damage listener dan tetap menggunakan alur kematian normal Paper.

## Commands

```text
/cdrko reload
/cdrko knockout <player>
/cdrko revive <player>
/cdrko kill <player>
/cdrko status <player>
/giveup
```

Alias admin: `/cdrknockout`, `/cko`.

## Build

```bash
mvn clean package
```

Output:

```text
target/CdrKnockout-0.2.1.jar
```

## Test flow v0.2.1

1. Knock player dan pastikan warning muncul pada threshold.
2. Biarkan sampai <=10 detik dan pastikan heartbeat berjalan.
3. Hit player KNOCKED; default harus memotong bleedout, bukan mengurangi HP vanilla.
4. Test lava, fire, drowning, explosion, dan VOID.
5. Test `/giveup`; harus menuju real death.
6. Pastikan revive masih bekerja dan membatalkan seluruh death path.
7. Pastikan real death hanya terjadi sekali dan `PlayerDeathEvent` tetap muncul untuk AxGraves.

> Upgrade dari config v0.2.0: hapus `VOID` dari `knockout.ignored-damage-causes` jika ingin environmental VOID handling v0.2.1 aktif.

## License

CdrKnockout menggunakan **MENKIESTES SOFTWARE LICENSE v1.0**. Source-available tidak berarti open-source. Lihat [LICENSE](LICENSE).

Implementasi CdrKnockout dibuat sebagai kode baru/clean-room; plugin pihak ketiga hanya digunakan sebagai referensi perilaku dan pengujian kompatibilitas, bukan sebagai sumber kode yang disalin.
