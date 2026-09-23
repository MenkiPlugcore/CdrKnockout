# CdrKnockout v0.7.0 — API & Expansion

## Public service API

CdrKnockout registers `dev.cadera.cdrknockout.api.CdrKnockoutApi` in Bukkit's `ServicesManager` during `onEnable`.

Recommended lookup:

```java
import dev.cadera.cdrknockout.api.CdrKnockoutApi;
import dev.cadera.cdrknockout.api.CdrKnockoutProvider;

CdrKnockoutApi api = CdrKnockoutProvider.get();
```

Alternative:

```java
CdrKnockoutApi api = Bukkit.getServicesManager().load(CdrKnockoutApi.class);
if (api == null) {
    // CdrKnockout unavailable/not enabled.
}
```

Consumer plugins should declare CdrKnockout as `depend` or `softdepend` according to their own requirements.

## API methods

Read state:

```java
api.isKnocked(player);
api.isDeathInProgress(player);
api.getRemainingSeconds(player);
api.getSnapshot(player);
api.isBeingRevived(player);
api.isReviver(player);
api.isBeingExecuted(player);
api.isExecutor(player);
api.getClientPlatform(player);
api.getPoseMode(player);
```

Transitions:

```java
api.knockout(player);
api.knockout(player, EntityDamageEvent.DamageCause.CUSTOM, true);
api.revive(player);
api.revive(player, 6.0, 3);
api.forceDeath(player);
api.giveUp(player);
```

`KnockoutSnapshot` is immutable and returns a cloned anchor location so consumers cannot mutate the core session accidentally.

## Custom events

All v0.7.0 events are **post-transition** events and are intentionally not cancellable.

### CdrKnockoutEvent

Fires after the player has entered `KNOCKED`.

```java
@EventHandler
public void onKnockout(CdrKnockoutEvent event) {
    Player player = event.getPlayer();
    long remaining = event.getRemainingSeconds();
    boolean recovered = event.isRecovered();
}
```

`isRecovered()` is true when the transition was restored from persisted KO state after relog/restart recovery.

### CdrRevivedEvent

Fires after a KNOCKED player returns to a living non-KO state.

```java
@EventHandler
public void onRevived(CdrRevivedEvent event) {
    long downForMillis = event.getKnockoutDurationMillis();
}
```

### CdrKnockoutDeathEvent

Fires when a tracked KNOCKED player reaches a real Paper death.

```java
@EventHandler
public void onKoDeath(CdrKnockoutDeathEvent event) {
    if (event.isManagedDeath()) {
        // Bleedout, giveup, execution, or CdrKnockout-managed force death.
    }
}
```

The normal `PlayerDeathEvent` still occurs. Grave plugins such as AxGraves therefore continue to receive the standard Paper death lifecycle.

## Event bridge behaviour

Damage-caused KO is observed immediately after CdrKnockout's lethal interception. Admin/API KO and persistence recovery are caught by a lightweight state transition poller.

Default:

```yaml
api:
  events:
    enabled: true
    poll-ticks: 2
```

Reloading config restarts the poll task without clearing active transition tracking, preventing duplicate KO events for already-KNOCKED players.

## PlaceholderAPI

PlaceholderAPI is optional. When installed and `placeholderapi.enabled: true`, CdrKnockout registers identifier `cdrknockout`.

```text
%cdrknockout_version%
%cdrknockout_state%
%cdrknockout_is_knocked%
%cdrknockout_death_in_progress%
%cdrknockout_time%
%cdrknockout_time_formatted%
%cdrknockout_platform%
%cdrknockout_pose%
%cdrknockout_being_revived%
%cdrknockout_reviver%
%cdrknockout_being_executed%
%cdrknockout_executor%
```

State priority:

```text
DYING
EXECUTING
REVIVING
KNOCKED
NORMAL
```

`time` returns raw remaining seconds or `∞` if bleedout is disabled. `time_formatted` returns `MM:SS` or `∞`.

Platform returns:

```text
JAVA
BEDROCK
UNKNOWN
```

Pose returns:

```text
SWIMMING
CROUCH
NONE
```

Without PlaceholderAPI, the expansion class is never loaded and CdrKnockout core continues normally.

## Compatibility rule

Public consumers should depend only on classes under:

```text
dev.cadera.cdrknockout.api
```

Do not depend directly on `core`, `revive`, `execution`, `platform`, or `integration` implementation packages. Those remain internal and may change before v1.0.0.
