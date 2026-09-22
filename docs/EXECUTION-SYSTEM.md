# CdrKnockout v0.4.0 — Execution System

Execution adalah channel terpisah untuk menghabisi player yang sedang `KNOCKED`.

## Default flow

```text
Target = KNOCKED
Executor mendekat <= 1.0 block
Executor memegang sword di main hand
Executor jongkok
        ↓
EXECUTING... 3 detik
        ↓
CdrKnockout managed real death
        ↓
PlayerDeathEvent normal
        ↓
AxGraves dapat membuat grave
```

Tidak membutuhkan klik kiri atau klik kanan.

## Anti-conflict dengan revive

- Player yang menunjukkan execution intent tidak dipilih sebagai reviver.
- Target yang sedang dieksekusi tidak dapat memulai passive revive channel.
- Target yang sedang direvive tidak dapat memulai execution channel.
- Satu executor hanya dapat mengeksekusi satu target pada satu waktu.
- Satu target hanya dapat memiliki satu execution session.

Dengan default config, intent dibedakan dari revive lewat item di main hand:

- `GOLDEN_APPLE` + sneak = revive.
- Sword + sneak = execution.

## Default execution items

- WOODEN_SWORD
- STONE_SWORD
- IRON_SWORD
- GOLDEN_SWORD
- DIAMOND_SWORD
- NETHERITE_SWORD

Daftar dapat diubah di `execution.item.allowed-materials`.

## Cancellation

Execution batal jika salah satu kondisi penting putus, termasuk:

- executor berhenti sneak,
- executor keluar dari radius,
- executor mati / KNOCKED / logout,
- target tidak lagi KNOCKED,
- target logout / mati / berpindah ke managed death,
- executor kehilangan atau mengganti execution item,
- executor terkena damage jika `cancel-on-damage: true`,
- executor menyerang entity lain jika `cancel-on-attack: true`,
- executor bergerak melebihi tolerance jika `cancel-on-move: true`.

Damage pada target tidak membatalkan execution secara default. Downed-damage engine tetap memproses damage tersebut.

## Config utama

```yaml
execution:
  enabled: true
  max-distance: 1.0
  duration-seconds: 3.0
  require-sneak: true
  permission: cdrknockout.execute

  item:
    required: true
    allowed-materials:
      - WOODEN_SWORD
      - STONE_SWORD
      - IRON_SWORD
      - GOLDEN_SWORD
      - DIAMOND_SWORD
      - NETHERITE_SWORD

  channel:
    cancel-on-damage: true
    cancel-on-target-damage: false
    cancel-on-attack: true
    cancel-on-move: false
    move-tolerance: 0.10
    cancel-on-item-change: true
```

## Permission

```text
cdrknockout.execute
```

Default permission: `true`.

## Regression checklist

1. Knock target.
2. Dekatkan executor <=1 block sambil memegang sword dan sneak.
3. Pastikan ActionBar execution muncul tanpa klik.
4. Lepas sneak sebelum 100%; execution harus batal.
5. Ganti sword sebelum 100%; execution harus batal.
6. Menjauh >1 block; execution harus batal.
7. Terkena damage; executor harus batal jika default config dipakai.
8. Ulangi dan tahan sampai 100%; target harus masuk real death.
9. AxGraves harus membuat tepat satu grave.
10. Uji satu player dengan Golden Apple; passive revive harus tetap bekerja dan tidak tertukar menjadi execution.
11. Uji dua executor pada target yang sama; hanya satu session boleh aktif.
12. Uji executor logout/restart; execution channel tidak dipersist dan harus bersih.
