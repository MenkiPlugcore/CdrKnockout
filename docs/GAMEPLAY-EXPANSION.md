# CdrKnockout v0.8.0 — Gameplay Expansion

v0.8.0 menambahkan gameplay layer di atas core knockout tanpa mengubah managed real-death/AxGraves flow.

## Self Revive

Player KNOCKED dapat merevive diri sendiri dengan item khusus. Default menggunakan `ENCHANTED_GOLDEN_APPLE`, channel 10 detik, cooldown 120 detik, dan item dikonsumsi hanya saat sukses. Auto-start dapat dinonaktifkan dan `/selfrevive` tersedia sebagai trigger manual.

## Medic Role & Medical Kit

Permission default medic: `cdrknockout.medic`.

Admin dapat memberi Medical Kit lewat:

```text
/medkit <player> [amount]
```

Medical Kit memakai PDC marker, bukan sekadar material/name, sehingga tidak tertukar dengan PAPER biasa. Medic yang memegang kit dapat memakai passive proximity revive. Secara default medkit dapat melewati requirement revive standar dan satu kit dikonsumsi saat revive berhasil.

## Distress

Player KNOCKED dapat memakai:

```text
/distress
```

Signal dikirim ke player dalam radius configurable, memiliki cooldown, sound optional, dan dapat membatasi receiver melalui permission.

## Statistics

Statistik tersimpan ke `plugins/CdrKnockout/statistics.yml`.

Tracked counters:

- knockouts
- revives received
- knockout deaths
- self revives
- distress signals
- medical kit uses

Command:

```text
/kostats [player]
```

## PlaceholderAPI additions

v0.8.0 menambahkan placeholder gameplay/statistik untuk self-revive, distress cooldown, dan counter statistik. PlaceholderAPI tetap optional.

## Compatibility

Gameplay expansion tetap menggunakan core revive/death pipeline. Bleedout, execution, giveup, self-revive, Java/Bedrock detection, persistence, dan AxGraves harus diuji bersama sebelum production release.
