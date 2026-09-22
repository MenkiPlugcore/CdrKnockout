# CdrKnockout

CdrKnockout adalah sistem knockout/revive modular untuk Paper yang dibuat oleh **CADERA / MENKIESTES**.

Versi saat ini: **v0.1.1 — Passive Revive Core**

> Status: alpha / development. Core knockout dan passive proximity revive sudah tersedia; requirement lanjutan dan regression AxGraves formal masih mengikuti roadmap.

## Target platform

- Paper 1.21.11
- Java 21
- Dirancang agar real death tetap melewati `PlayerDeathEvent` normal sehingga grave plugin seperti AxGraves dapat bekerja setelah bleedout/forced death.

## Knockout Core

- Lethal damage di-intercept sebelum kematian asli.
- State `KNOCKED` dengan timer configurable.
- Pose tiarap berbasis swimming metadata.
- Position lock dengan kamera tetap bebas.
- Blindness / Weakness / Slowness / Darkness configurable.
- ALL / WHITELIST / BLACKLIST world policy.
- Bleedout menjadi real death.

## v0.1.1 Passive Revive

Revive tidak membutuhkan klik.

```text
Player KNOCKED
      ↑ <= 1 block
Reviver + SNEAK + revive item di MAIN HAND
      ↓
channel progress
      ↓
100% -> REVIVED
```

Default:

- Max distance: `1.0` block.
- Item: `GOLDEN_APPLE`.
- Duration: `8` detik.
- Item dikonsumsi hanya setelah sukses.
- Klik kanan item diblok saat kondisi revive terpenuhi agar Golden Apple tidak termakan.
- Channel batal jika jongkok dilepas, terlalu jauh, item berubah/hilang, reviver mati/KO/logout/pindah world.
- Damage dan attack cancellation dapat diatur di config.

## Commands

```text
/cdrko reload
/cdrko knockout <player>
/cdrko revive <player>
/cdrko kill <player>
/cdrko status <player>
```

Alias: `/cdrknockout`, `/cko`.

## Build

```bash
mvn clean package
```

Output:

```text
target/CdrKnockout-0.1.1.jar
```

## Test flow

1. Knock target dengan lethal hit atau `/cdrko knockout <player>`.
2. Pastikan target tiarap dan tidak mati.
3. Reviver memegang Golden Apple di main hand.
4. Berdiri maksimal 1 block dari target lalu tahan sneak.
5. Jangan klik apa pun; progress ActionBar mulai otomatis.
6. Setelah 100%, target bangun dan 1 Golden Apple dikonsumsi.
7. Ulangi lalu putuskan sneak / menjauh / ganti item untuk memastikan revive cancel.
8. Biarkan target bleedout untuk memastikan real death masih diteruskan ke AxGraves.

## License

CdrKnockout menggunakan **MENKIESTES SOFTWARE LICENSE v1.0**. Source-available tidak berarti open-source. Lihat [LICENSE](LICENSE).

Implementasi CdrKnockout dibuat sebagai kode baru/clean-room; plugin pihak ketiga hanya digunakan sebagai referensi perilaku dan pengujian kompatibilitas, bukan sebagai sumber kode yang disalin.
