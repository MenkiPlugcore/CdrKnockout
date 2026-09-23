package dev.cadera.cdrknockout.gameplay;

import dev.cadera.cdrknockout.CdrKnockoutPlugin;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataType;

import java.util.ArrayList;
import java.util.List;

public final class MedicalKitItems {

    private static final LegacyComponentSerializer LEGACY = LegacyComponentSerializer.legacyAmpersand();

    private final CdrKnockoutPlugin plugin;
    private final NamespacedKey key;

    public MedicalKitItems(CdrKnockoutPlugin plugin) {
        this.plugin = plugin;
        this.key = new NamespacedKey(plugin, "medical_kit");
    }

    public ItemStack create(int amount) {
        Material material = configuredMaterial();
        ItemStack item = new ItemStack(material, Math.max(1, amount));
        ItemMeta meta = item.getItemMeta();
        if (meta == null) {
            return item;
        }

        String name = plugin.getConfig().getString("gameplay.medical.medkit.name", "&c&lMedical Kit");
        meta.displayName(LEGACY.deserialize(name == null ? "&c&lMedical Kit" : name));

        List<String> configuredLore = plugin.getConfig().getStringList("gameplay.medical.medkit.lore");
        if (!configuredLore.isEmpty()) {
            List<Component> lore = new ArrayList<>();
            for (String line : configuredLore) {
                lore.add(LEGACY.deserialize(line));
            }
            meta.lore(lore);
        }

        meta.getPersistentDataContainer().set(key, PersistentDataType.BYTE, (byte) 1);
        item.setItemMeta(meta);
        return item;
    }

    public boolean isMedicalKit(ItemStack item) {
        if (item == null || item.getType().isAir() || !item.hasItemMeta()) {
            return false;
        }
        Byte marker = item.getItemMeta().getPersistentDataContainer().get(key, PersistentDataType.BYTE);
        return marker != null && marker == (byte) 1;
    }

    public boolean isEligibleMedic(Player player) {
        if (player == null || !plugin.getConfig().getBoolean("gameplay.medical.enabled", true)) {
            return false;
        }
        if (!plugin.getConfig().getBoolean("gameplay.medical.medkit.enabled", true)) {
            return false;
        }
        if (!isMedicalKit(player.getInventory().getItemInMainHand())) {
            return false;
        }
        boolean requirePermission = plugin.getConfig().getBoolean("gameplay.medical.require-role-permission", true);
        if (!requirePermission) {
            return true;
        }
        String permission = plugin.getConfig().getString("gameplay.medical.role-permission", "cdrknockout.medic");
        return permission == null || permission.isBlank() || player.hasPermission(permission);
    }

    public void consumeOne(Player player) {
        if (player == null) {
            return;
        }
        ItemStack item = player.getInventory().getItemInMainHand();
        if (!isMedicalKit(item)) {
            return;
        }
        if (!plugin.getConfig().getBoolean("gameplay.medical.medkit.consume-on-success", true)) {
            return;
        }
        int remaining = item.getAmount() - 1;
        if (remaining <= 0) {
            player.getInventory().setItemInMainHand(new ItemStack(Material.AIR));
        } else {
            item.setAmount(remaining);
        }
    }

    public Material configuredMaterial() {
        String configured = plugin.getConfig().getString("gameplay.medical.medkit.material", "PAPER");
        Material material = configured == null ? null : Material.matchMaterial(configured);
        return material == null ? Material.PAPER : material;
    }
}
