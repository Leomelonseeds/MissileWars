package com.leomelonseeds.missilewars.invs.pagination;

import java.util.Comparator;

import org.bukkit.Material;
import org.bukkit.inventory.ItemStack;

public class ItemSort {
    
    private String id;
    private String displayName;
    private Material guiMaterial;
    private Comparator<ItemStack> comparator;
    
    public ItemSort(String id, String displayName, Material guiMaterial, Comparator<ItemStack> comparator) {
        this.id = id;
        this.displayName = displayName;
        this.guiMaterial = guiMaterial;
        this.comparator = comparator;
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
    
    public Comparator<ItemStack> getComparator() {
        return comparator;
    }
}
