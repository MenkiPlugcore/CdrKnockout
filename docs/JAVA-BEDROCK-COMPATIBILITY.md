# CdrKnockout v0.6.0 — Java & Bedrock Compatibility

## Detection

CdrKnockout mendeteksi client secara runtime tanpa hard dependency.

Urutan:

1. Geyser API (`isBedrockPlayer(UUID)`).
2. Floodgate API (`isFloodgatePlayer(UUID)`).
3. Jika keduanya tidak tersedia, platform menjadi `UNKNOWN`.

Diagnostics:

```text
/cdrko compat
/cdrko platform <player>
```

## Pose modes

```yaml
compatibility:
  client:
    pose:
      java-mode: SWIMMING
      bedrock-mode: SWIMMING
      unknown-mode: SWIMMING
```

Mode:

- `SWIMMING`: prone/crawl-style pose utama.
- `CROUCH`: fallback visual untuk Bedrock/Geyser build tertentu.
- `NONE`: tanpa forced pose; movement lock tetap bekerja.

Jika Bedrock terlihat berdiri atau pose berkedip, ubah:

```yaml
compatibility:
  client:
    pose:
      bedrock-mode: CROUCH
```

lalu:

```text
/cdrko reload
```

## Regression test — Java

1. `/cdrko platform JavaPlayer` -> `JAVA` ketika detector aktif.
2. `/cdrko knockout JavaPlayer`.
3. Pastikan pose SWIMMING/prone terlihat.
4. Pastikan player tidak bisa berjalan/lompat namun kamera tetap bebas.
5. Pastikan Blindness/Weakness/Slowness sesuai config.
6. Uji revive proximity + sneak + Golden Apple tanpa klik.
7. Uji execution proximity + sneak + sword tanpa klik.
8. Uji relog dan restart recovery.
9. Uji bleedout -> tepat satu grave AxGraves.

## Regression test — Bedrock

1. Login melalui Geyser/Floodgate.
2. `/cdrko platform BedrockPlayer` -> `BEDROCK`.
3. `/cdrko knockout BedrockPlayer`.
4. Pastikan movement lock dan kamera bekerja.
5. Pastikan ActionBar, title, warning, dan heartbeat diterima.
6. Pastikan pose SWIMMING terlihat layak. Jika tidak, gunakan `CROUCH` fallback.
7. Uji revive tanpa klik: <=1 block + sneak + Golden Apple.
8. Uji execution tanpa klik: <=1 block + sneak + sword.
9. Logout saat KO lalu login lagi; state harus kembali.
10. Restart server saat KO lalu login lagi; state harus kembali.
11. Bleedout/execution harus menghasilkan tepat satu `PlayerDeathEvent` dan satu grave AxGraves.

## Backend/proxy note

Jika Floodgate berada di proxy dan backend juga membutuhkan data Floodgate, pastikan konfigurasi Floodgate proxy/backend memang meneruskan data player dengan benar. Jika `/cdrko platform` menghasilkan `UNKNOWN`, cek `/cdrko compat` untuk melihat apakah hook Geyser/Floodgate aktif.

## Expected behaviour when no Geyser/Floodgate

CdrKnockout tetap dapat digunakan di server Java-only. Platform akan tampil `UNKNOWN` bila tidak ada detector API, dan `unknown-mode` dipakai untuk pose KO.
