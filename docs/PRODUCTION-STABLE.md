# CdrKnockout v1.0.0 — Production Stable Acceptance

Target release:

- Paper 1.21.11
- Java 21
- Java Edition
- Bedrock via Geyser/Floodgate
- Optional AxGraves / PlaceholderAPI / Vault / AuraSkills

## 1. Startup gate

1. Start server dengan JAR v1.0.0.
2. Pastikan tidak ada exception saat enable.
3. Jalankan:

```text
/cdrko doctor
```

Expected:

- Java 21+ = OK.
- Minecraft 1.21.11 = OK.
- Data folder = writable.
- World policy / teleport policy / pose modes / requirement mode = valid.
- Optional hook yang tidak dipasang boleh tampil NOT_INSTALLED tanpa mematikan core.
- Hook FAILED harus ditinjau sebelum production sign-off.

## 2. Core KO

Java dan Bedrock masing-masing:

1. Lethal PvP hit -> KNOCKED, bukan PlayerDeathEvent langsung.
2. Lethal PvE hit -> KNOCKED.
3. Player prone/fallback pose sesuai platform config.
4. Position locked; kamera tetap dapat bergerak bila `allow-camera: true`.
5. Attack/interact/inventory/drop sesuai restriction config.
6. Bleedout ActionBar/timer berjalan.

## 3. Revive

1. Target KNOCKED.
2. Reviver <=1 block.
3. Sneak + required item tanpa klik.
4. Progress berjalan sampai 100%.
5. Item/cost hanya dipotong pada sukses.
6. Stop sneak / ganti item / keluar radius / damage sesuai config -> channel batal.
7. Revive sukses -> target normal, tidak ada grave.

Test requirement engine terpisah untuk ITEM, XP_LEVEL, MONEY, AURASKILLS, PERMISSION, ALL, dan ANY jika fitur tersebut dipakai di production.

## 4. Self revive / medic / distress

- Self revive dengan default Enchanted Golden Apple.
- Damage saat self revive membatalkan channel jika configured.
- Medical Kit PDC bekerja; PAPER biasa tidak dianggap medkit.
- Non-medic ditolak bila role permission diwajibkan.
- `/distress` hanya saat KNOCKED, radius/cooldown berjalan.
- `/kostats` berubah setelah event terkait.

## 5. Execution

1. Target KNOCKED.
2. Executor <=1 block + sneak + execution item.
3. Channel 100% -> real death.
4. Revive dan execution tidak boleh aktif bersamaan pada target yang sama.
5. Execution cancel conditions bekerja.

## 6. Bleedout & environmental death

Test minimal:

- combat damage saat downed,
- lava,
- fire/fire tick,
- drowning,
- suffocation,
- freeze,
- explosion,
- void,
- `/giveup`,
- timer habis.

Setiap path real death harus menghasilkan satu PlayerDeathEvent nyata.

## 7. AxGraves

Dengan AxGraves aktif:

- KO saja -> 0 grave.
- Revive -> 0 grave.
- Self revive -> 0 grave.
- Bleedout -> tepat 1 grave.
- `/giveup` -> tepat 1 grave.
- Execution -> tepat 1 grave.
- Fatal downed damage -> tepat 1 grave.
- Inventory/EXP mengikuti config AxGraves, bukan dipindahkan lebih awal oleh CdrKnockout.

Gunakan `/cdrko compat` untuk diagnostics.

## 8. Persistence / relog / restart

- KO -> logout -> login: state pulih.
- KO -> restart server -> login: state pulih.
- Offline bleedout mengikuti `offline-time-counts`.
- Tidak ada duplicate session setelah reconnect berulang.
- `knockouts.yml` tetap valid setelah beberapa autosave/restart.
- `statistics.yml` tetap valid setelah autosave/restart.

v1.0.0 menulis kedua YAML melalui temp file + replace untuk mengurangi risiko file setengah tertulis saat crash.

## 9. Reload

Test:

```text
/cdrko reload
```

Saat:

- tidak ada player KO,
- ada player KO,
- statistics enabled,
- persistence enabled.

Kemudian test toggle persistence/statistics enable -> disable -> enable pada staging. Tidak boleh menghasilkan exception atau resurrect stale runtime state.

## 10. Command compatibility

Saat KNOCKED dan command restriction aktif, built-in commands berikut harus tetap dapat mencapai Bukkit command dispatcher walau config lama belum memasukkannya ke allow-list:

```text
/giveup
/selfrevive
/distress
/kostats
/cdrko
/cdrknockout
/cko
```

Namespaced label juga harus dinormalisasi, misalnya `/cdrknockout:selfrevive`.

Permission command tetap berlaku setelah lolos dari restriction layer.

## 11. API / PlaceholderAPI

- `CdrKnockoutProvider` / Bukkit ServicesManager mengembalikan API.
- `CdrKnockoutEvent` tepat satu kali per KO transition.
- `CdrRevivedEvent` tepat satu kali per revive.
- `CdrKnockoutDeathEvent` tepat satu kali per managed real-death transition.
- Placeholder state/time/platform/gameplay/statistics merespons normal bila PlaceholderAPI aktif.
- Server tanpa PlaceholderAPI tetap enable normal.

## 12. Concurrency smoke test

Dengan beberapa player sekaligus:

- beberapa target KO bersamaan,
- dua calon reviver pada satu target,
- revive pada target A sementara execution pada target B,
- beberapa distress signal,
- restart dengan beberapa persisted KO.

Tidak boleh ada ConcurrentModificationException, duplicate revive, duplicate execution, atau duplicate grave.

## Production sign-off

Release dapat dianggap lolos live acceptance bila:

- GitHub Actions v1.0.0 SUCCESS,
- `/cdrko doctor` tidak menunjukkan ERROR,
- seluruh flow yang dipakai server lolos staging test,
- real-death + AxGraves menghasilkan tepat satu grave,
- Java dan Bedrock flow utama lolos,
- relog/restart recovery lolos.
