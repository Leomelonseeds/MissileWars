package com.leomelonseeds.missilewars.arenas;

/**
 * A more granular enum to separate the different types of arena objects initializable
 */
public enum ArenaType {
    CLASSIC,
    TOURNEY,
    CUSTOM,
    TUTORIAL(true),
    TRAINING(true);
    
    private boolean isSpecial;
    
    private ArenaType(boolean isSpecial) {
        this.isSpecial = isSpecial;
    }
    
    private ArenaType() {}
    
    /**
     * Special arenas are arenas where only 1 copy exists
     * 
     * @return
     */
    public boolean isSpecial() {
        return isSpecial;
    }
}
