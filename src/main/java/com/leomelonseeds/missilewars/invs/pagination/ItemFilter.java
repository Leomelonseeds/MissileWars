package com.leomelonseeds.missilewars.invs.pagination;

import java.util.function.Predicate;

import org.bukkit.Material;
import org.bukkit.inventory.ItemStack;

public class ItemFilter {
    
    private String id;
    private String displayName;
    private Material guiMaterial;
    private Predicate<ItemStack> filter;
    
    public ItemFilter(String id, String displayName, Material guiMaterial, Predicate<ItemStack> filter) {
        this.id = id;
        this.displayName = displayName;
        this.guiMaterial = guiMaterial;
        this.filter = filter;
    }

    public String getId() {
        return id;
    }

    public String getDisplayName() {
        return displayName;
    }

    public Material getGuiMaterial() {
        return guiMaterial;
    }
    
    public Predicate<ItemStack> getPredicate() {
        return filter;
    }
}
