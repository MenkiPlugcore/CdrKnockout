package dev.cadera.cdrknockout.api;

import org.bukkit.Bukkit;

import java.util.Optional;

/** Static helper for consumers that want the registered CdrKnockout service. */
public final class CdrKnockoutProvider {

    private CdrKnockoutProvider() {
    }

    public static Optional<CdrKnockoutApi> getOptional() {
        return Optional.ofNullable(Bukkit.getServicesManager().load(CdrKnockoutApi.class));
    }

    public static CdrKnockoutApi get() {
        return getOptional().orElseThrow(() ->
                new IllegalStateException("CdrKnockout API service is not registered"));
    }
}
