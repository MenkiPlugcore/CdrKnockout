# CdrKnockout

CdrKnockout adalah sistem knockout/revive modular untuk Paper yang dibuat oleh **CADERA / MENKIESTES**.

Versi saat ini: **v0.2.0 — Requirement Engine**

> Status: alpha / development. Core knockout, passive proximity revive, dan requirement engine sudah tersedia. Regression AxGraves formal dan advanced bleedout mengikuti roadmap.

## Target platform

- Paper 1.21.11
- Java 21
- Optional integration: Vault + economy provider
- Optional integration: AuraSkills
- Real death tetap melewati `PlayerDeathEvent` normal agar grave plugin seperti AxGraves dapat bekerja setelah bleedout/forced death.

## Knockout Core

- Lethal damage di-intercept sebelum kematian asli.
- State `KNOCKED` dengan timer configurable.
- Pose tiarap berbasis swimming metadata.
- Position lock dengan kamera tetap bebas.
- Blindness / Weakness / Slowness / Darkness configurable.
- ALL / WHITELIST / BLACKLIST world policy.
- Bleedout menjadi real death.

## Passive Revive

Revive tidak membutuhkan klik kiri/kanan.

```text
Player KNOCKED
      ↑ <= 1 block
Reviver + SNEAK + requirement terpenuhi
      ↓
channel progress
      ↓
100% -> REVIVED
```

Default tetap kompatibel dengan v0.1.1:

- Max distance: `1.0` block.
- Duration: `8` detik.
- Requirement default: `1x GOLDEN_APPLE` di main hand.
- Cost baru dikonsumsi saat progress berhasil 100%.

## v0.2.0 Requirement Engine

Requirement tersedia:

- `ITEM`
- `XP_LEVEL`
- `MONEY` via Vault
- `AURASKILLS`
- `PERMISSION`

Semua requirement dapat di-enable/disable dari `config.yml`.

### Mode ALL

Semua requirement yang aktif wajib terpenuhi. Semua cost yang aktif akan diproses ketika revive sukses.

```yaml
revive:
  requirements:
    mode: ALL
    item:
      enabled: true
      material: GOLDEN_APPLE
      amount: 1
      consume-on-success: true
    xp-level:
      enabled: true
      minimum-level: 10
      consume-on-success: false
```

### Mode ANY

Cukup satu requirement aktif. Requirement yang digunakan dipilih berdasarkan `any-priority`, dan hanya cost requirement terpilih yang diproses.

```yaml
revive:
  requirements:
    mode: ANY
    any-priority:
      - ITEM
      - XP_LEVEL
      - MONEY
      - AURASKILLS
      - PERMISSION
```

Requirement yang terpilih saat channel dimulai dikunci untuk sesi tersebut. Jadi requirement tidak dapat diam-diam berganti di tengah revive.

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
target/CdrKnockout-0.2.0.jar
```

## Test flow v0.2.0

1. Test default ITEM-only revive terlebih dahulu.
2. Enable `xp-level` dan mode `ALL`; pastikan reviver dengan level kurang dari minimum tidak dapat memulai channel.
3. Ganti mode `ANY`; pastikan salah satu requirement cukup.
4. Jika Vault tersedia, enable `money` dan pastikan saldo hanya ditarik setelah revive sukses.
5. Jika AuraSkills tersedia, enable requirement skill dan uji skill/level yang dikonfigurasi.
6. Enable permission requirement untuk menguji node khusus.
7. Pastikan bleedout tetap menghasilkan real death dan AxGraves hanya bekerja setelah kematian asli.

## License

CdrKnockout menggunakan **MENKIESTES SOFTWARE LICENSE v1.0**. Source-available tidak berarti open-source. Lihat [LICENSE](LICENSE).

Implementasi CdrKnockout dibuat sebagai kode baru/clean-room; plugin pihak ketiga hanya digunakan sebagai referensi perilaku dan pengujian kompatibilitas, bukan sebagai sumber kode yang disalin.
