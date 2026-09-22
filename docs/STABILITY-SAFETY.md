# CdrKnockout v0.3.1 — Stability & Safety Regression

## Persistence

- [ ] KO player -> `knockouts.yml` contains UUID.
- [ ] Revive -> UUID removed.
- [ ] Real death -> UUID removed.
- [ ] `/giveup` -> UUID removed after real-death transition.
- [ ] Logout while KO -> UUID remains.
- [ ] Restart while KO -> UUID remains and state recovers on login.
- [ ] Plugin reload while KO -> state recovers without duplicate effects/session.

## Offline timer

With `offline-time-counts: true`:
- [ ] Logout 10 seconds -> timer is ~10 seconds lower after login.
- [ ] Stay offline past expiry -> login immediately queues real death.

With `offline-time-counts: false`:
- [ ] Logout 10 seconds -> remaining timer is approximately unchanged after login.

## Movement / teleport

Default `stability.teleport.mode: BLOCK`:
- [ ] `/tp`, portal/plugin teleport, and cross-world teleport are blocked while KO.
- [ ] Camera movement still works.
- [ ] Unexpected world change is reasserted back to anchor.

`FOLLOW`:
- [ ] Teleport is allowed.
- [ ] Anchor updates to destination.
- [ ] Movement lock uses the new anchor.

## Concurrency

- [ ] Multiple lethal events do not create duplicate KO sessions.
- [ ] Multiple revivers do not create multiple active channels for one target.
- [ ] Death queue cancels active revive channel.
- [ ] One managed death still produces at most one AxGraves grave.

## AxGraves

- [ ] Logout/relog recovery does not create a grave.
- [ ] Restart recovery does not create a grave.
- [ ] Revive after recovery does not create a grave.
- [ ] Expired recovered KO creates one real death and one grave.
