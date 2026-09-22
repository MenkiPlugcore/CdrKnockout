package dev.cadera.cdrknockout.revive.requirement;

import dev.cadera.cdrknockout.CdrKnockoutPlugin;
import dev.cadera.cdrknockout.revive.requirement.hook.AuraSkillsHook;
import dev.cadera.cdrknockout.revive.requirement.hook.VaultHook;
import dev.cadera.cdrknockout.util.Messages;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

public final class RequirementEngine {

    private final CdrKnockoutPlugin plugin;
    private final Messages messages;
    private final VaultHook vaultHook;
    private final AuraSkillsHook auraSkillsHook;

    public RequirementEngine(CdrKnockoutPlugin plugin, Messages messages) {
        this.plugin = plugin;
        this.messages = messages;
        this.vaultHook = new VaultHook(plugin);
        this.auraSkillsHook = new AuraSkillsHook(plugin);
    }

    public void reload() {
        vaultHook.refresh();
        auraSkillsHook.refresh();
    }

    public RequirementResult evaluate(Player player) {
        List<RequirementType> enabled = enabledRequirements();
        if (enabled.isEmpty()) {
            return RequirementResult.success(Set.of());
        }

        String mode = plugin.getConfig().getString("revive.requirements.mode", "ALL");
        mode = mode == null ? "ALL" : mode.trim().toUpperCase(Locale.ROOT);

        if (mode.equals("ANY")) {
            return evaluateAny(player, enabled);
        }
        return evaluateAll(player, enabled);
    }

    public RequirementResult validateSelected(Player player, Set<RequirementType> selected) {
        if (selected == null || selected.isEmpty()) {
            return RequirementResult.success(Set.of());
        }

        for (RequirementType type : selected) {
            Check check = check(player, type);
            if (!check.passed()) {
                return RequirementResult.failure(check.message());
            }
        }
        return RequirementResult.success(selected);
    }

    public boolean commit(Player player, Set<RequirementType> selected) {
        RequirementResult finalCheck = validateSelected(player, selected);
        if (!finalCheck.passed()) {
            return false;
        }

        if (selected.contains(RequirementType.MONEY)
                && plugin.getConfig().getBoolean("revive.requirements.money.withdraw-on-success", true)) {
            double amount = Math.max(0.0D, plugin.getConfig().getDouble("revive.requirements.money.amount", 0.0D));
            if (amount > 0.0D && !vaultHook.withdraw(player, amount)) {
                return false;
            }
        }

        if (selected.contains(RequirementType.ITEM)
                && plugin.getConfig().getBoolean("revive.requirements.item.consume-on-success", true)) {
            consumeItem(player);
        }

        if (selected.contains(RequirementType.XP_LEVEL)
                && plugin.getConfig().getBoolean("revive.requirements.xp-level.consume-on-success", false)) {
            int levels = Math.max(0, plugin.getConfig().getInt("revive.requirements.xp-level.consume-levels", 0));
            if (levels > 0) {
                player.setLevel(Math.max(0, player.getLevel() - levels));
            }
        }
        return true;
    }

    public boolean isItemRequirementEnabled() {
        return plugin.getConfig().getBoolean("revive.requirements.item.enabled", true);
    }

    public boolean matchesRequiredItem(ItemStack stack) {
        if (!isItemRequirementEnabled()) {
            return true;
        }
        if (stack == null || stack.getType().isAir()) {
            return false;
        }
        return stack.getType() == requiredMaterial() && stack.getAmount() >= requiredAmount();
    }

    public Material requiredMaterial() {
        String configured = plugin.getConfig().getString(
                "revive.requirements.item.material",
                "GOLDEN_APPLE"
        );
        Material material = configured == null ? null : Material.matchMaterial(configured);
        return material == null ? Material.GOLDEN_APPLE : material;
    }

    public int requiredAmount() {
        return Math.max(1, plugin.getConfig().getInt("revive.requirements.item.amount", 1));
    }

    private RequirementResult evaluateAll(Player player, List<RequirementType> enabled) {
        LinkedHashSet<RequirementType> selected = new LinkedHashSet<>();
        for (RequirementType type : enabled) {
            Check check = check(player, type);
            if (!check.passed()) {
                return RequirementResult.failure(check.message());
            }
            selected.add(type);
        }
        return RequirementResult.success(selected);
    }

    private RequirementResult evaluateAny(Player player, List<RequirementType> enabled) {
        List<RequirementType> priority = anyPriority(enabled);
        String firstFailure = messages.format("revive-requirement-any-failed");

        for (RequirementType type : priority) {
            if (!enabled.contains(type)) {
                continue;
            }
            Check check = check(player, type);
            if (check.passed()) {
                return RequirementResult.success(Set.of(type));
            }
            if (firstFailure.equals(messages.format("revive-requirement-any-failed"))) {
                firstFailure = check.message();
            }
        }
        return RequirementResult.failure(firstFailure);
    }

    private List<RequirementType> enabledRequirements() {
        List<RequirementType> enabled = new ArrayList<>();
        for (RequirementType type : RequirementType.values()) {
            boolean fallback = type == RequirementType.ITEM;
            if (plugin.getConfig().getBoolean(
                    "revive.requirements." + type.configKey() + ".enabled",
                    fallback
            )) {
                enabled.add(type);
            }
        }
        return enabled;
    }

    private List<RequirementType> anyPriority(List<RequirementType> enabled) {
        List<RequirementType> result = new ArrayList<>();
        List<String> configured = plugin.getConfig().getStringList("revive.requirements.any-priority");
        for (String value : configured) {
            RequirementType type = RequirementType.parse(value);
            if (type != null && !result.contains(type)) {
                result.add(type);
            }
        }
        for (RequirementType type : enabled) {
            if (!result.contains(type)) {
                result.add(type);
            }
        }
        return result;
    }

    private Check check(Player player, RequirementType type) {
        return switch (type) {
            case ITEM -> checkItem(player);
            case XP_LEVEL -> checkXpLevel(player);
            case MONEY -> checkMoney(player);
            case AURASKILLS -> checkAuraSkills(player);
            case PERMISSION -> checkPermission(player);
        };
    }

    private Check checkItem(Player player) {
        if (matchesRequiredItem(player.getInventory().getItemInMainHand())) {
            return Check.pass();
        }
        return Check.fail(messages.format(
                "revive-requirement-item",
                "%item%", requiredMaterial().name(),
                "%amount%", Integer.toString(requiredAmount())
        ));
    }

    private Check checkXpLevel(Player player) {
        int minimum = Math.max(0, plugin.getConfig().getInt("revive.requirements.xp-level.minimum-level", 10));
        int consume = plugin.getConfig().getBoolean("revive.requirements.xp-level.consume-on-success", false)
                ? Math.max(0, plugin.getConfig().getInt("revive.requirements.xp-level.consume-levels", 0))
                : 0;
        int required = Math.max(minimum, consume);
        if (player.getLevel() >= required) {
            return Check.pass();
        }
        return Check.fail(messages.format(
                "revive-requirement-xp",
                "%level%", Integer.toString(required)
        ));
    }

    private Check checkMoney(Player player) {
        double amount = Math.max(0.0D, plugin.getConfig().getDouble("revive.requirements.money.amount", 5000.0D));
        if (!vaultHook.isAvailable()) {
            return Check.fail(messages.format("revive-requirement-vault-unavailable"));
        }
        if (vaultHook.has(player, amount)) {
            return Check.pass();
        }
        return Check.fail(messages.format(
                "revive-requirement-money",
                "%amount%", formatNumber(amount)
        ));
    }

    private Check checkAuraSkills(Player player) {
        String skill = plugin.getConfig().getString("revive.requirements.auraskills.skill", "FIGHTING");
        skill = skill == null ? "FIGHTING" : skill.trim().toUpperCase(Locale.ROOT);
        double minimum = Math.max(0.0D, plugin.getConfig().getDouble(
                "revive.requirements.auraskills.minimum-level",
                20.0D
        ));

        if (!auraSkillsHook.isAvailable()) {
            return Check.fail(messages.format("revive-requirement-auraskills-unavailable"));
        }

        double actual = auraSkillsHook.getSkillLevel(player, skill);
        if (actual >= minimum) {
            return Check.pass();
        }
        return Check.fail(messages.format(
                "revive-requirement-auraskills",
                "%skill%", skill,
                "%level%", formatNumber(minimum)
        ));
    }

    private Check checkPermission(Player player) {
        String node = plugin.getConfig().getString(
                "revive.requirements.permission.node",
                "cdrknockout.revive.special"
        );
        node = node == null ? "" : node.trim();
        if (!node.isBlank() && player.hasPermission(node)) {
            return Check.pass();
        }
        return Check.fail(messages.format(
                "revive-requirement-permission",
                "%permission%", node.isBlank() ? "<empty>" : node
        ));
    }

    private void consumeItem(Player player) {
        ItemStack stack = player.getInventory().getItemInMainHand();
        if (!matchesRequiredItem(stack)) {
            return;
        }
        int remaining = stack.getAmount() - requiredAmount();
        if (remaining <= 0) {
            player.getInventory().setItemInMainHand(new ItemStack(Material.AIR));
        } else {
            stack.setAmount(remaining);
        }
    }

    private String formatNumber(double value) {
        if (Math.rint(value) == value) {
            return Long.toString((long) value);
        }
        return String.format(Locale.US, "%.2f", value);
    }

    private record Check(boolean passed, String message) {
        private static Check pass() {
            return new Check(true, "");
        }

        private static Check fail(String message) {
            return new Check(false, message);
        }
    }
}
