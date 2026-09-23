# v0.8.0 Gameplay Expansion Regression Checklist

1. Normal revive masih bekerja dengan Golden Apple + sneak <= 1 block.
2. `/medkit <player> [amount]` menghasilkan item PDC Medical Kit; PAPER biasa tidak dihitung sebagai medkit.
3. Player dengan `cdrknockout.medic` + Medical Kit dapat revive dan kit hanya terpakai saat sukses.
4. Self-revive hanya dapat berjalan saat KNOCKED, tidak saat sedang direvive/dieksekusi, dan item baru terpakai saat sukses.
5. Self-revive yang terkena damage terputus jika `cancel-on-damage: true`.
6. `/distress` hanya bekerja saat KNOCKED, menghormati radius/cooldown/receiver rules.
7. `/kostats` membaca statistik dari `statistics.yml` dan persistence tetap aman setelah restart.
8. Bleedout/execution/giveup tetap menghasilkan satu real death dan satu grave AxGraves.
9. Java + Bedrock: self-revive, distress, medkit, ActionBar, dan command flow diuji di kedua client.
10. PlaceholderAPI optional: server tanpa PAPI tetap enable; server dengan PAPI membaca placeholder gameplay/statistik.
