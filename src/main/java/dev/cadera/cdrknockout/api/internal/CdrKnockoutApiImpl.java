package dev.cadera.cdrknockout.api.internal;

import dev.cadera.cdrknockout.CdrKnockoutPlugin;
import dev.cadera.cdrknockout.api.CdrKnockoutApi;
import dev.cadera.cdrknockout.api.KnockoutSnapshot;
import dev.cadera.cdrknockout.core.KnockoutManager;
import dev.cadera.cdrknockout.core.KnockoutSession;
import dev.cadera.cdrknockout.execution.ExecutionManager;
import dev.cadera.cdrknockout.platform.ClientPlatformResolver;
import dev.cadera.cdrknockout.platform.PoseEngine;
import dev.cadera.cdrknockout.revive.ReviveManager;
import org.bukkit.entity.Player;
import org.bukkit.event.entity.EntityDamageEvent;

import java.util.Optional;

public final class CdrKnockoutApiImpl implements CdrKnockoutApi {

    private final CdrKnockoutPlugin plugin;
    private final KnockoutManager knockoutManager;
    private final ReviveManager reviveManager;
    private final ExecutionManager executionManager;
    private final ClientPlatformResolver platformResolver;
    private final PoseEngine poseEngine;

    public CdrKnockoutApiImpl(
            CdrKnockoutPlugin plugin,
            KnockoutManager knockoutManager,
            ReviveManager reviveManager,
            ExecutionManager executionManager,
            ClientPlatformResolver platformResolver,
            PoseEngine poseEngine
    ) {
        this.plugin = plugin;
        this.knockoutManager = knockoutManager;
        this.reviveManager = reviveManager;
        this.executionManager = executionManager;
        this.platformResolver = platformResolver;
        this.poseEngine = poseEngine;
    }

    @Override
    public String getVersion() {
        return plugin.getDescription().getVersion();
    }

    @Override
    public boolean isKnocked(Player player) {
        return knockoutManager.isKnocked(player);
    }

    @Override
    public boolean isDeathInProgress(Player player) {
        return knockoutManager.isDeathInProgress(player);
    }

    @Override
    public long getRemainingSeconds(Player player) {
        return knockoutManager.getRemainingSeconds(player);
    }

    @Override
    public Optional<KnockoutSnapshot> getSnapshot(Player player) {
        KnockoutSession session = knockoutManager.getSession(player);
        if (session == null) {
            return Optional.empty();
        }
        long now = System.currentTimeMillis();
        return Optional.of(new KnockoutSnapshot(
                session.playerId(),
                session.startedAtMillis(),
                session.expiresAtMillis(),
                session.remainingSeconds(now),
                session.anchor()
        ));
    }

    @Override
    public boolean knockout(Player player) {
        return knockout(player, EntityDamageEvent.DamageCause.CUSTOM, true);
    }

    @Override
    public boolean knockout(Player player, EntityDamageEvent.DamageCause cause, boolean force) {
        return knockoutManager.knockout(player, cause == null ? EntityDamageEvent.DamageCause.CUSTOM : cause, force);
    }

    @Override
    public boolean revive(Player player) {
        return knockoutManager.revive(player);
    }

    @Override
    public boolean revive(Player player, double health, int resistanceSeconds) {
        return knockoutManager.revive(player, health, resistanceSeconds);
    }

    @Override
    public boolean forceDeath(Player player) {
        return knockoutManager.forceDeath(player);
    }

    @Override
    public boolean giveUp(Player player) {
        return knockoutManager.giveUp(player);
    }

    @Override
    public boolean isBeingRevived(Player player) {
        return player != null && reviveManager.isTargetBeingRevived(player);
    }

    @Override
    public boolean isReviver(Player player) {
        return player != null && reviveManager.isReviver(player);
    }

    @Override
    public boolean isBeingExecuted(Player player) {
        return player != null && executionManager.isTargetBeingExecuted(player);
    }

    @Override
    public boolean isExecutor(Player player) {
        return player != null && executionManager.isExecutor(player);
    }

    @Override
    public String getClientPlatform(Player player) {
        return platformResolver.resolve(player).name();
    }

    @Override
    public String getPoseMode(Player player) {
        return poseEngine.modeName(player);
    }
}
