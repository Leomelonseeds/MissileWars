package com.leomelonseeds.missilewars.arenas.teams;

public enum TeamName {
    RED("red", "&c"),
    BLUE("blue", "&9"),
    NONE("no team", "&f");
    
    private String id;
    private String color;
    
    private TeamName(String id, String color) {
        this.id = id;
        this.color = color;
    }
    
    @Override
    public String toString() {
        return id;
    }
    
    public String getColor() {
        return color;
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
}
