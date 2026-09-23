# CdrKnockout v1.0.2 — Fixed Prone Pose

## Masalah v1.0.1

Pose engine sebelumnya hanya memanggil `Player#setSwimming(true)`. Pada player yang KNOCKED di darat, state swimming dapat aktif tanpa client benar-benar mempertahankan visual body `SWIMMING`, sehingga player masih dapat terlihat berdiri.

## Perbaikan

v1.0.2 menggunakan native Paper 1.21.11 fixed pose:

```java
player.setSwimming(true);
player.setPose(Pose.SWIMMING, true);
```

`fixed=true` membuat pose tidak kembali otomatis ke `STANDING` selama KO. Tick engine tetap mengawasi state tersebut dan mengaplikasikannya kembali jika plugin lain mencoba mengubahnya.

Saat revive, death, quit, shutdown, atau cleanup, CdrKnockout melepaskan fixed pose lalu memulihkan swimming/sneaking state yang disimpan sebelum KO.

Tidak ada ArmorStand, passenger, mount, atau carry entity yang digunakan.

## Mode

- `SWIMMING`: fixed `Pose.SWIMMING`, visual crawl/prone.
- `CROUCH`: fixed `Pose.SNEAKING`, fallback crossplay.
- `NONE`: CdrKnockout tidak memaksa pose.

Default:

```yaml
compatibility:
  client:
    pose:
      java-mode: SWIMMING
      bedrock-mode: SWIMMING
      unknown-mode: SWIMMING
```

## Regression test Java

1. Dua client Java masuk server.
2. Jalankan `/cdrko knockout PlayerA`.
3. Dari PlayerB, pastikan model PlayerA langsung terlihat tiarap/crawl di darat.
4. PlayerA tidak boleh berpindah posisi tetapi masih boleh menggerakkan kamera bila config mengizinkan.
5. Tunggu minimal 10 detik. Pose tidak boleh snap kembali berdiri.
6. Revive PlayerA. Model harus kembali berdiri normal.
7. Ulangi dengan bleedout, `/giveup`, dan execution. Setelah real death/respawn tidak boleh ada fixed pose tersisa.
8. KO lalu logout/login. Setelah recovery, pose harus kembali prone.
9. KO lalu restart server. Setelah recovery, pose harus kembali prone.

## Regression test Bedrock

1. Masuk melalui Geyser/Floodgate.
2. Jalankan `/cdrko platform <player>` dan pastikan client terdeteksi BEDROCK bila hook tersedia.
3. KO player dan amati dari client lain.
4. Jika build Geyser tidak menerjemahkan fixed `SWIMMING` seperti Java, ubah sementara:

```yaml
compatibility:
  client:
    pose:
      bedrock-mode: CROUCH
```

5. `/cdrko reload`, lalu ulangi pengujian.

## AxGraves safety

Perubahan v1.0.2 hanya menyentuh visual pose engine. Death pipeline, inventory, EXP, `PlayerDeathEvent`, dan AxGraves integration tidak diubah.
