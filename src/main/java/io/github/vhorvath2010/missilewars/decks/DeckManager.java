package io.github.vhorvath2010.missilewars.decks;

import io.github.vhorvath2010.missilewars.MissileWarsPlugin;
import io.github.vhorvath2010.missilewars.utilities.ConfigUtils;
import org.bukkit.ChatColor;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.enchantments.Enchantment;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataType;

import java.util.ArrayList;
import java.util.List;

/** A class to manage Deck generation and selection. */
public class DeckManager {

    /** The default deck for players. */
    private final Deck defaultDeck;

    /** Set up the DeckManager with loaded decks. */
    public DeckManager() {
        // Setup gear items
        List<ItemStack> gear = new ArrayList<>();
        ItemStack bow = createUtilityItem("sentinel_bow");
        bow.addUnsafeEnchantment(Enchantment.ARROW_KNOCKBACK, 1);
        bow.addUnsafeEnchantment(Enchantment.ARROW_DAMAGE, 1);
        bow.addUnsafeEnchantment(Enchantment.ARROW_FIRE, 1);
        bow.addUnsafeEnchantment(Enchantment.DAMAGE_ALL, 4);
        gear.add(bow);

        // Setup pool items
        ItemStack arrows = new ItemStack(Material.ARROW, 3);
        ItemStack guardian = createSchematicItem("guardian");
        ItemStack buster = createSchematicItem("shieldbuster");
        ItemStack tomahawk = createSchematicItem("tomahawk");
        ItemStack juggernaut = createSchematicItem("juggernaut");
        ItemStack lightning = createSchematicItem("lightning");
        ItemStack shield = createSchematicItem("shield_2");
        ItemStack fireball = createUtilityItem("fireball");
        List<ItemStack> pool = new ArrayList<>(List.of(new ItemStack[]{arrows, guardian, buster, tomahawk, juggernaut,
                lightning, shield}));

        defaultDeck = new Deck("Default", gear, pool);
    }

    /**
     * Obtain and return the default deck.
     *
     * @return the default deck
     */
    public Deck getDefaultDeck() {
        return defaultDeck;
    }

    /**
     * Create an ItemStack that spawns the given schematic on use.
     *
     * @param schematicName the name of the schematic
     * @return an ItemStack that spawns the schematic on use
     */
    private ItemStack createSchematicItem(String schematicName) {
        // Load item data from config
        FileConfiguration itemsConfig = ConfigUtils.getConfigFile(MissileWarsPlugin.getPlugin().getDataFolder()
                .toString(), "items.yml");

        // Setup item
        ItemStack item = new ItemStack(Material.getMaterial(itemsConfig.getString(schematicName + ".item")));
        ItemMeta itemMeta = item.getItemMeta();
        itemMeta.setDisplayName(ChatColor.translateAlternateColorCodes('&',
                itemsConfig.getString(schematicName + ".name")));
        List<String> lore = new ArrayList<>();
        for (String loreString : itemsConfig.getStringList(schematicName + ".lore")) {
            lore.add(ChatColor.translateAlternateColorCodes('&', loreString));
        }
        itemMeta.setLore(lore);
        itemMeta.getPersistentDataContainer().set(new NamespacedKey(MissileWarsPlugin.getPlugin(), "item-structure"),
                PersistentDataType.STRING, schematicName);
        item.setItemMeta(itemMeta);
        return item;
    }

    /**
     * Create an ItemStack for a utility item with no schematic.
     *
     * @param type the type of utility
     * @return an ItemStack that spawns the schematic on use
     */
    private ItemStack createUtilityItem(String type) {
        // Load item data from config
        FileConfiguration itemsConfig = ConfigUtils.getConfigFile(MissileWarsPlugin.getPlugin().getDataFolder()
                .toString(), "items.yml");

        // Setup item
        ItemStack item = new ItemStack(Material.getMaterial(itemsConfig.getString(type + ".item")));
        ItemMeta itemMeta = item.getItemMeta();
        itemMeta.setDisplayName(ChatColor.translateAlternateColorCodes('&',
                itemsConfig.getString(type + ".name")));
        List<String> lore = new ArrayList<>();
        for (String loreString : itemsConfig.getStringList(type + ".lore")) {
            lore.add(ChatColor.translateAlternateColorCodes('&', loreString));
        }
        itemMeta.setLore(lore);
        itemMeta.getPersistentDataContainer().set(new NamespacedKey(MissileWarsPlugin.getPlugin(), "item-utility"),
                PersistentDataType.STRING, type);
        item.setItemMeta(itemMeta);
        return item;
    }

}
