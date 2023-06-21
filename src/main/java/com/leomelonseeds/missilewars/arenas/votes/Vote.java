package com.leomelonseeds.missilewars.arenas.votes;

public class Vote {
    
    private String map;
    private int amount;
    
    public Vote(String map, int amount) {
        this.map = map;
        this.amount = amount;
    }

    public String getMap() {
        return map;
    }
    
    public int getAmount() {
        return amount;
    }
}
