package com.leomelonseeds.missilewars.arenas.teams;

public enum TeamName {
    RED("red"),
    BLUE("blue"),
    NONE("no team");
    
    private String id;
    
    private TeamName(String id) {
        this.id = id;
    }
    
    @Override
    public String toString() {
        return id;
    }
    
    public TeamName getOpposite() {
        switch (this) {
            case RED:
                return BLUE;
            case BLUE:
                return RED;
            default:
                return NONE;
        }
    }
    
    public String getColor() {
        switch (this) {
            case RED:
                return "&c";
            case BLUE:
                return "&9";
            default:
                return "&f";
        }
    }
}
