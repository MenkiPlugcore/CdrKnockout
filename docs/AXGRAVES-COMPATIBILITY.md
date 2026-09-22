# CdrKnockout v0.3.0 — AxGraves Compatibility

Dokumen ini adalah regression checklist untuk memastikan CdrKnockout dan AxGraves tidak saling mengambil alih state kematian secara salah.

## Prinsip integrasi

CdrKnockout memiliki dua state yang berbeda:

1. `KNOCKED` — player belum mati. Lethal damage dibatalkan, sehingga `PlayerDeathEvent` tidak terjadi.
2. `REAL DEATH` — bleedout, `/giveup`, admin kill, atau fatal downed damage mengakhiri KNOCKED dan membiarkan Paper menjalankan kematian nyata.

AxGraves hanya seharusnya membuat grave pada state kedua.

CdrKnockout tidak mengosongkan inventory, tidak memindahkan EXP, dan tidak memodifikasi drops pada `PlayerDeathEvent`. Tanggung jawab tersebut tetap milik AxGraves.

## Runtime guard

Jika AxGraves terdeteksi, CdrKnockout mencoba hook event berikut secara runtime:

`com.artillexstudios.axgraves.api.events.GravePreSpawnEvent`

Tidak ada dependency AxGraves pada compile time. Jika class/event berubah pada versi AxGraves di masa depan, core CdrKnockout tetap berjalan dan status `/cdrko compat` akan menunjukkan hook tidak aktif.

Guard yang tersedia:

- `block-graves-while-knocked`: membatalkan GravePreSpawnEvent bila player masih KNOCKED dan belum real death.
- `prevent-duplicate-managed-graves`: untuk real death yang berasal dari CdrKnockout, hanya satu GravePreSpawnEvent yang diterima selama duplicate window.

## Pre-test

- Gunakan Paper 1.21.11 + Java 21.
- Pasang CdrKnockout dan AxGraves.
- Hapus plugin Knockout/ReanimateMC lama dari server test agar tidak ada lethal listener tambahan.
- Pastikan player test memiliki permission `axgraves.allowgraves` bila versi/config AxGraves memerlukannya.
- Jalankan `/cdrko compat`.

Expected:

```text
Status: ACTIVE
AxGraves version: <installed version>
Pre-spawn hook: ACTIVE
```

Jika status `NATURAL_ONLY`, PlayerDeathEvent compatibility masih bekerja, tetapi safety hook GravePreSpawnEvent tidak aktif dan perlu dicek terhadap versi AxGraves yang terpasang.

## Test A — No grave on Knockout

1. Isi inventory player dengan beberapa item dan berikan EXP.
2. Jalankan `/cdrko knockout <player>` atau beri lethal damage normal.
3. Tunggu beberapa detik tanpa bleedout.

Expected:

- Player KNOCKED/tiarap.
- Tidak ada death screen.
- Tidak ada grave AxGraves.
- Inventory player tidak berubah.
- EXP player tidak berubah.

## Test B — Revive preserves inventory and EXP

1. Masukkan player ke KNOCKED.
2. Revive dengan passive revive atau `/cdrko revive <player>`.

Expected:

- Tidak ada grave.
- Item sebelum KO tetap sama.
- EXP sebelum KO tetap sama, kecuali cost revive memang dikonfigurasi menggunakan XP level.

## Test C — Bleedout creates exactly one grave

1. Knock player.
2. Biarkan timer mencapai 0.

Expected:

- Tepat satu `PlayerDeathEvent` nyata.
- Tepat satu grave AxGraves.
- Item/EXP tersimpan sesuai config AxGraves.
- Counter duplicate pada `/cdrko compat` tidak bertambah pada flow normal.

## Test D — Give up

1. Knock player.
2. Jalankan `/giveup`.

Expected:

- Player benar-benar mati.
- Tepat satu grave.
- Tidak ada second grave beberapa tick kemudian.

## Test E — Fatal downed damage

Uji cause yang dikonfigurasi `INSTANT_DEATH`, default `VOID`.

Expected:

- KO berakhir menjadi real death.
- Tepat satu grave.

## Test F — Damage REDUCE_TIMER

1. Knock player.
2. Hit player lagi atau letakkan pada environmental damage yang menggunakan `REDUCE_TIMER`.

Expected:

- HP KNOCKED tidak mati secara vanilla.
- Bleedout timer berkurang.
- Belum ada grave sampai timer benar-benar mencapai 0.

## Test G — keepInventory / AxGraves ownership

Uji kombinasi config AxGraves dan gamerule yang dipakai server production.

Expected:

- CdrKnockout tidak mengubah drops/keepInventory/keepLevel.
- Hasil item dan EXP mengikuti AxGraves + gamerule server.
- Tidak ada item duplikasi antara inventory, ground drops, dan grave.

## Test H — Duplicate protection

Dengan `compatibility.axgraves.log-diagnostics: true`, pantau console selama beberapa real death.

Jika plugin lain secara tidak normal memicu GravePreSpawnEvent kedua dalam CdrKnockout-managed death cycle, event kedua dibatalkan dan counter `Blocked duplicate graves` pada `/cdrko compat` bertambah.

## Recommended production config

```yaml
compatibility:
  axgraves:
    enabled: true
    block-graves-while-knocked: true
    prevent-duplicate-managed-graves: true
    duplicate-window-ticks: 40
    log-diagnostics: false
```

Aktifkan `log-diagnostics` hanya saat debugging.
