package com.leomelonseeds.missilewars.arenas.settings;

public final class IntSettingModifier {
    
    private final int min;
    private final int max;
    private final int change;
    
    private IntSettingModifier(int min, int max, int change) {
        this.min = min;
        this.max = max;
        this.change = change;
    }
    
    public int getMin() {
        return min;
    }
    
    public int getMax() {
        return max;
    }
    
    public int getChange() {
        return change;
    }
    
    public static IntSettingModifier create(int min, int max, int change) {
        return new IntSettingModifier(min, max, change);
    }
}
