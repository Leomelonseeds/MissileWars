package com.leomelonseeds.missilewars.invs.pagination;

import java.util.Comparator;

import org.bukkit.Material;
import org.bukkit.inventory.ItemStack;

public class ItemSort {
    
    private String id;
    private String displayName;
    private Material guiMaterial;
    private Comparator<ItemStack> comparator;
    private boolean reverse; // Descending order

    public ItemSort(String id, String displayName, Material guiMaterial, Comparator<ItemStack> comparator) {
        this(id, displayName, guiMaterial, comparator, false);
    }
    
    public ItemSort(String id, String displayName, Material guiMaterial, Comparator<ItemStack> comparator, boolean reverse) {
        this.id = id;
        this.displayName = displayName;
        this.guiMaterial = guiMaterial;
        this.comparator = comparator;
        this.reverse = reverse;
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
    
    public boolean isReverse() {
        return reverse;
    }
}
