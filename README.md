# CdrKnockout

CdrKnockout adalah sistem knockout/revive modular untuk Paper yang dibuat oleh **CADERA / MENKIESTES**.

Versi saat ini: **v0.1.0 — Core Knockout System**

> Status: alpha / development. v0.1.0 fokus membangun fondasi knockout yang stabil sebelum passive revive dan integrasi lanjutan ditambahkan.

## Target platform

- Paper 1.21.11
- Java 21
- Dirancang agar kematian asli tetap menggunakan alur Bukkit/Paper normal sehingga plugin grave seperti AxGraves dapat menangani `PlayerDeathEvent` setelah bleedout/forced death.

## v0.1.0

- Intercept lethal damage sebelum player benar-benar mati.
- State `KNOCKED` dengan timer configurable.
- Pose tiarap berbasis swimming pose, terpisah dari sistem carry masa depan.
- Position lock: tidak dapat jalan/lompat, tetapi kamera masih dapat digerakkan.
- Efek Blindness, Weakness, Slowness, dan Darkness configurable.
- Restriction attack, interact, item use/drop, inventory, dan command.
- World policy: `ALL`, `WHITELIST`, atau `BLACKLIST`.
- Respect Totem of Undying secara default.
- Bleedout menjadi real death; ini sengaja tidak menahan `PlayerDeathEvent`.
- Admin commands untuk test/debug.
- Debug logging configurable.

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
target/CdrKnockout-0.1.0.jar
```

## Test flow v0.1.0

```text
lethal hit
  -> damage dibatalkan
  -> KNOCKED
  -> player tiarap + terkunci
  -> timer berjalan
  -> /cdrko revive <player> = kembali hidup
  ATAU
  -> timer habis = real death -> PlayerDeathEvent -> grave plugin
```

Pengujian AxGraves yang diharapkan:

1. Saat masuk KNOCKED, grave **tidak** boleh dibuat.
2. Saat `/cdrko revive`, inventory tetap utuh dan tidak ada grave.
3. Saat bleedout atau `/cdrko kill`, player benar-benar mati dan AxGraves boleh membuat grave.

## Next

v0.1.1 akan menambahkan **Passive Proximity Revive**: penolong berada maksimal 1 block, memegang item revive di main hand, lalu jongkok selama durasi revive. Tidak menggunakan klik kiri/kanan dan item baru dikonsumsi setelah revive berhasil.

## License

CdrKnockout menggunakan **MENKIESTES SOFTWARE LICENSE v1.0**. Source-available tidak berarti open-source. Lihat [LICENSE](LICENSE).

Implementasi CdrKnockout dibuat sebagai kode baru/clean-room; plugin pihak ketiga hanya digunakan sebagai referensi perilaku dan pengujian kompatibilitas, bukan sebagai sumber kode yang disalin.
