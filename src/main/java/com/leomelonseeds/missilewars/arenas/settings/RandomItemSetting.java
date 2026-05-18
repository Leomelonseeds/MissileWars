package com.leomelonseeds.missilewars.arenas.settings;

import java.util.Arrays;
import java.util.function.BiConsumer;
import java.util.function.Function;

import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

import com.leomelonseeds.missilewars.utilities.InventoryUtils;

public enum RandomItemSetting {
    
    WEIGHT(1, IntSettingModifier.create(1, 100, 1), ri -> ri.getWeight(), (ri, val) -> ri.setWeight(val)),
    MAX(1, IntSettingModifier.create(0, 10, 1), ri -> ri.getMax(), (ri, val) -> ri.setMax(val)),
    AMOUNT(1, IntSettingModifier.create(1, 64, 1), ri -> ri.getAmount(), (ri, val) -> ri.setAmount(val)),
    MISSILE_OFFSET_Z(0, IntSettingModifier.create(-4, 4, 1), ri -> getMissileOffset(ri, true), (ri, val) -> setMissileOffset(ri, val, true)),
    MISSILE_OFFSET_Y(0, IntSettingModifier.create(-3, 3, 1), ri -> getMissileOffset(ri, false), (ri, val) -> setMissileOffset(ri, val, false));
    
    private int defaultValue;
    private IntSettingModifier modifier;
    private Function<RandomItem, Integer> getCurrentValue;
    private BiConsumer<RandomItem, Integer> setValue;
    
    private RandomItemSetting(int defaultValue, IntSettingModifier modifier, Function<RandomItem, Integer> getCurrentValue, 
        BiConsumer<RandomItem, Integer> setValue) {
        this.defaultValue = defaultValue;
        this.modifier = modifier;
        this.getCurrentValue = getCurrentValue;
        this.setValue = setValue;
    }
    
    public int getDefaultValue() {
        return defaultValue;
    }
    
    public IntSettingModifier getModifier() {
        return modifier;
    }
    
    public int getCurrentValue(RandomItem ri) {
        return getCurrentValue.apply(ri);
    }
    
    public void setValue(RandomItem ri, int value) {
        setValue.accept(ri, value);
    }
    
    /**
     * @param ri
     * @param z false is y
     */
    private static int getMissileOffset(RandomItem ri, boolean z) {
        return getMissileOffsets(ri)[z ? 0 : 1];
    }
    
    private static void setMissileOffset(RandomItem ri, int value, boolean z) {
        int[] currentOffsets = getMissileOffsets(ri);
        ItemStack item = ri.getModifiableItem();
        ItemMeta meta = item.getItemMeta();
        
        // See if we can remove the offset
        if (value == 0 && (
                (z && currentOffsets[1] == 0) || 
                (!z && currentOffsets[0] == 0)
        )) {
            meta.getPersistentDataContainer().remove(InventoryUtils.CUSTOM_OFFSET_KEY);
            return;
        }
        
        currentOffsets[z ? 0 : 1] = value;
        String valueString = currentOffsets[0] + "," + currentOffsets[1];
        InventoryUtils.setMetaString(meta, InventoryUtils.CUSTOM_OFFSET_KEY, valueString);
        item.setItemMeta(meta);
    }
    
    /**
     * @param ri
     * @return [offsetZ, offsetY]
     */
    private static int[] getMissileOffsets(RandomItem ri) {
        ItemStack item = ri.getModifiableItem();
        String offset = InventoryUtils.getStringFromItemKey(item, InventoryUtils.CUSTOM_OFFSET_KEY);
        if (offset == null) {
            return new int[] {0, 0};
        }
        
        return Arrays.stream(offset.split(",")).mapToInt(Integer::parseInt).toArray();
    }
}
