package com.leomelonseeds.missilewars.arenas;

public enum ArenaGamemode {
    CLASSIC("&aClassic");
    
    private String displayName;
    
    private ArenaGamemode(String displayName) {
        this.displayName = displayName;
    }
    
    @Override
    public String toString() {
        return super.toString().toLowerCase();
    }
    
    public String getDisplayName() {
        return displayName;
    }
}
