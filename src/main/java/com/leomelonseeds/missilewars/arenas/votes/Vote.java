package com.leomelonseeds.missilewars.arenas.votes;

import java.util.Objects;

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

    @Override
    public int hashCode() {
        return Objects.hash(amount, map);
    }

    @Override
    public boolean equals(Object obj) {
        if (this == obj)
            return true;
        if (obj == null)
            return false;
        if (getClass() != obj.getClass())
            return false;
        Vote other = (Vote) obj;
        return amount == other.amount && Objects.equals(map, other.map);
    }
}
