package com.leomelonseeds.missilewars.arenas.settings.enums;

import org.bukkit.Difficulty;

public enum ArenaWorldDifficulty {
    EASY(Difficulty.EASY),
    NORMAL(Difficulty.NORMAL),
    HARD(Difficulty.HARD);
    
    private Difficulty difficulty;
    
    private ArenaWorldDifficulty(Difficulty difficulty) {
        this.difficulty = difficulty;
    }
    
    public Difficulty getDifficulty() {
        return difficulty;
    }
}
