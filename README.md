# CdrKnockout

CdrKnockout adalah sistem knockout/revive modular untuk Paper yang dibuat oleh **CADERA / MENKIESTES**.

Versi saat ini: **v1.0.2 — Production Stable**

Target utama: **Paper 1.21.11 + Java 21**, dengan dukungan Java Edition dan Bedrock melalui Geyser/Floodgate.

## Core flow

```text
lethal damage
  -> KNOCKED
  -> fixed prone pose
  -> revive / self-revive
     atau
  -> bleedout / fatal downed damage / giveup / execution
  -> REAL DEATH
  -> PlayerDeathEvent normal
  -> AxGraves
```

Selama player masih `KNOCKED`, inventory/EXP tidak dipindahkan oleh CdrKnockout dan death event asli belum terjadi.

## Fitur utama

- Lethal-damage interception dan state KNOCKED.
- Fixed prone/crawl-style pose + movement lock.
- Passive proximity revive tanpa klik.
- Requirement engine: item, XP, Vault, AuraSkills, permission; mode ALL/ANY.
- Bleedout, heartbeat, warning, environmental downed damage, `/giveup`.
- Execution system tanpa klik.
- Persistent KO recovery setelah relog/restart.
- AxGraves compatibility dengan no-grave-on-KO + duplicate-grave guard.
- Java/Bedrock client detection dan pose fallback.
- Public API + custom Bukkit events.
- PlaceholderAPI expansion.
- Self-revive, medic role, PDC Medical Kit, distress signal, persistent statistics.
- Mandatory runtime license integrity guard.

## v1.0.2 — Fixed Prone Pose

Versi sebelumnya hanya memakai `Player#setSwimming(true)` untuk mencoba membuat player terlihat tiarap. Pada player yang berada di darat, swimming state tersebut tidak selalu memaksa client menampilkan body pose SWIMMING sehingga korban KO dapat tetap terlihat berdiri.

v1.0.2 memakai native Paper 1.21.11 `Entity#setPose(Pose, boolean)` dengan fixed pose:

```text
KNOCKED + SWIMMING mode
        -> Pose.SWIMMING (fixed)
        -> player terlihat crawl / prone di darat
        -> pose terus dipertahankan selama KO
        -> revive/death/quit
        -> fixed pose dilepas
        -> swimming/sneaking state sebelum KO dipulihkan
```

Pose ini tidak memakai ArmorStand, passenger, mount, atau fake carrier entity. Karena itu pose engine tetap terpisah dari sistem interaksi revive/execution.

Default Java dan Bedrock tetap memakai `SWIMMING`. Jika Geyser build tertentu tidak merender pose tersebut dengan benar, `compatibility.client.pose.bedrock-mode` dapat diganti ke `CROUCH` sebagai fallback.

## Production hardening

v1.0.x memiliki lapisan hardening untuk penggunaan live:

- `knockouts.yml` dan `statistics.yml` ditulis menggunakan temporary file + atomic replace bila filesystem mendukungnya.
- Runtime reload persistence/statistics diperkeras supaya enable/disable tidak meninggalkan state memory yang salah.
- Command recovery milik CdrKnockout tetap dapat dipakai saat KNOCKED walau server masih memakai config lama.
- Namespaced command seperti `/cdrknockout:selfrevive` dinormalisasi saat command restriction aktif.
- Startup/reload production diagnostics.
- `/cdrko doctor` untuk mengecek Java, Minecraft version, data-folder write access, config enum penting, optional dependency, crossplay hook, PlaceholderAPI, AxGraves, persistence, dan statistics.
- CI memakai `mvn clean verify` dan memvalidasi isi/version JAR sebelum artifact di-upload.

Dokumentasi acceptance test: `docs/PRODUCTION-STABLE.md`.

## Runtime License Integrity — v1.0.1+

Pada startup valid pertama, CdrKnockout otomatis membuat:

```text
plugins/CdrKnockout/LICENSE.txt
```

Isi file tersebut adalah **MENKIESTES SOFTWARE LICENSE v1.0** yang dibundel di dalam JAR. Plugin juga membuat installation marker internal di folder `plugins/` untuk membedakan instalasi yang sudah pernah diinisialisasi.

`LICENSE.txt` bersifat wajib. Jika file dihapus atau isinya dimodifikasi:

- saat startup: plugin gagal enable dengan `LICENSE INTEGRITY FAILURE`;
- saat server sedang berjalan: integrity monitor mendeteksi perubahan lalu menonaktifkan CdrKnockout.

Interval pengecekan runtime default 100 ticks dan dapat diubah melalui `production.license-integrity-check-ticks`. Kewajiban license-nya sendiri tidak dapat dimatikan lewat config.

Sistem ini adalah local integrity/tamper guard, bukan online activation/DRM server.

## Default revive

```text
<= 1 block
+ sneak
+ GOLDEN_APPLE di main hand
+ tahan 8 detik
```

Tidak membutuhkan klik kiri/kanan.

## Default execution

```text
Target KNOCKED
+ executor <= 1 block
+ sneak
+ sword di main hand
+ tahan 3 detik
```

## Gameplay expansion

Self revive default memakai `ENCHANTED_GOLDEN_APPLE`, channel 10 detik dan cooldown 120 detik. Medic menggunakan permission `cdrknockout.medic` dan Medical Kit bertanda PDC. Distress tersedia lewat `/distress`, statistik lewat `/kostats [player]`.

## Commands

```text
/cdrko reload
/cdrko knockout <player>
/cdrko revive <player>
/cdrko kill <player>
/cdrko status <player>
/cdrko compat
/cdrko platform <player>
/cdrko doctor
/giveup
/selfrevive
/distress
/kostats [player]
/medkit <player> [amount]
```

`/cdrko doctor` direkomendasikan dijalankan setelah upgrade config/plugin atau perubahan dependency.

## Optional integrations

- Vault + economy provider
- AuraSkills
- AxGraves
- PlaceholderAPI
- Geyser
- Floodgate

Semua hook dibuat optional; kegagalan hook tidak boleh mematikan core knockout.

## Public API & Events

API tersedia melalui Bukkit `ServicesManager` atau `CdrKnockoutProvider`.

Events:

```text
CdrKnockoutEvent
CdrRevivedEvent
CdrKnockoutDeathEvent
```

## Build

```bash
mvn clean verify
```

Output:

```text
target/CdrKnockout-1.0.2.jar
```

## Deployment note

Saat upgrade dari v1.0.1, cukup ganti JAR. `LICENSE.txt`, marker lisensi, config, statistics, dan persisted knockout data tetap dipertahankan. Untuk pengujian pose, gunakan `/cdrko knockout <player>` lalu lihat player tersebut dari client lain agar visual third-person benar-benar terverifikasi.

## Carry System

Milestone v0.5.0 Carry System sengaja di-skip agar pose KO tidak bergantung pada passenger/ArmorStand mechanics.

## License

CdrKnockout menggunakan **MENKIESTES SOFTWARE LICENSE v1.0**. Implementasi dibuat sebagai kode baru/clean-room.
