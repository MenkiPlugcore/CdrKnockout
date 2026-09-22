package dev.cadera.cdrknockout.revive.requirement.hook;

import dev.cadera.cdrknockout.CdrKnockoutPlugin;
import org.bukkit.entity.Player;
import org.bukkit.plugin.Plugin;

import java.lang.reflect.Method;
import java.util.UUID;

public final class AuraSkillsHook {

    private final CdrKnockoutPlugin plugin;
    private Object api;
    private Class<?> skillsEnumClass;

    public AuraSkillsHook(CdrKnockoutPlugin plugin) {
        this.plugin = plugin;
        refresh();
    }

    public void refresh() {
        api = null;
        skillsEnumClass = null;

        try {
            Plugin auraSkills = plugin.getServer().getPluginManager().getPlugin("AuraSkills");
            if (auraSkills == null || !auraSkills.isEnabled()) {
                return;
            }

            ClassLoader loader = auraSkills.getClass().getClassLoader();
            Class<?> apiClass = Class.forName(
                    "dev.aurelium.auraskills.api.AuraSkillsApi",
                    true,
                    loader
            );
            skillsEnumClass = Class.forName(
                    "dev.aurelium.auraskills.api.skill.Skills",
                    true,
                    loader
            );
            api = apiClass.getMethod("get").invoke(null);
        } catch (ReflectiveOperationException | LinkageError exception) {
            debug("AuraSkills hook unavailable: " + exception.getClass().getSimpleName() + ": " + exception.getMessage());
            api = null;
            skillsEnumClass = null;
        }
    }

    public boolean isAvailable() {
        return api != null && skillsEnumClass != null && skillsEnumClass.isEnum();
    }

    public double getSkillLevel(Player player, String skillName) {
        if (!isAvailable() || skillName == null || skillName.isBlank()) {
            return -1.0D;
        }

        try {
            Object skill = findSkill(skillName);
            if (skill == null) {
                return -1.0D;
            }

            Method getUser = api.getClass().getMethod("getUser", UUID.class);
            Object user = getUser.invoke(api, player.getUniqueId());
            if (user == null) {
                return -1.0D;
            }

            for (Method method : user.getClass().getMethods()) {
                if (!method.getName().equals("getSkillLevel") || method.getParameterCount() != 1) {
                    continue;
                }
                Class<?> parameter = method.getParameterTypes()[0];
                if (!parameter.isAssignableFrom(skill.getClass())) {
                    continue;
                }
                Object value = method.invoke(user, skill);
                if (value instanceof Number number) {
                    return number.doubleValue();
                }
            }
        } catch (ReflectiveOperationException | LinkageError exception) {
            debug("AuraSkills level check failed: " + exception.getMessage());
        }
        return -1.0D;
    }

    private Object findSkill(String skillName) {
        String normalized = skillName.trim().replace('-', '_').toUpperCase();
        Object[] constants = skillsEnumClass.getEnumConstants();
        if (constants == null) {
            return null;
        }
        for (Object constant : constants) {
            if (constant instanceof Enum<?> enumValue
                    && enumValue.name().equalsIgnoreCase(normalized)) {
                return constant;
            }
        }
        return null;
    }

    private void debug(String message) {
        if (plugin.getConfig().getBoolean("debug", false)) {
            plugin.getLogger().info("[DEBUG] " + message);
        }
    }
}
