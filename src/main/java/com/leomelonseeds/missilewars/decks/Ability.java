package com.leomelonseeds.missilewars.decks;

/**
 * Storage for passives and global passives
 */
public enum Ability {
    // Vanguard
    ADRENALINE("adrenaline", Type.PASSIVE, DeckStorage.VANGUARD),
    BUNNY("bunny", Type.PASSIVE, DeckStorage.VANGUARD),
    ENDER_SPLASH("lavasplash", Type.PASSIVE, DeckStorage.VANGUARD),
    
    // Sentinel
    IMPACT_TRIGGER("impacttrigger", Type.PASSIVE, DeckStorage.SENTINEL),
    LONGSHOT("longshot", Type.PASSIVE, DeckStorage.SENTINEL),
    EXOTIC_ARROWS("exoticarrows", Type.PASSIVE, DeckStorage.SENTINEL),
    
    // Berserker
    SPIKED_QUIVER("slownessarrows", Type.PASSIVE, DeckStorage.BERSERKER),
    ROCKETEER("boosterball", Type.PASSIVE, DeckStorage.BERSERKER),
    CREEPERSHOT("creepershot", Type.PASSIVE, DeckStorage.BERSERKER),
    
    // Architect
    PRICKLY_PROJECTILES("prickly", Type.PASSIVE, DeckStorage.ARCHITECT),
    POKEMISSILES("poke", Type.PASSIVE, DeckStorage.ARCHITECT),
    DECONSTRUCTOR("deconstructor", Type.PASSIVE, DeckStorage.ARCHITECT),
    
    // Global
    HOARDER("hoarder", Type.GPASSIVE, null),
    MISSILE_SPEC("missilespec", Type.GPASSIVE, null),
    UTILITY_SPEC("utilityspec", Type.GPASSIVE, null);
    
    
    private String id;
    private Type type; // Either "passive" or "gpassive"
    private DeckStorage deck;
    
    private Ability(String id, Type type, DeckStorage deck) {
        this.id = id;
        this.type = type;
        this.deck = deck;
    }
    
    @Override
    public String toString() {
        return id;
    }
    
    public Type getType() {
        return type;
    }
    
    public DeckStorage getDeck() {
        return deck;
    }
    
    public static Ability fromString(String s) {
        for (Ability p : Ability.values()) {
            if (s.equals(p.toString())) {
                return p;
            }
        }
        
        return null;
    }
    
    public enum Stat {
        AMPLIFIER("amplifier"),
        PLUS("plus"),
        MAX("max"),
        CUTOFF("cutoff"),
        DURATION("duration"),
        MULTIPLIER("multiplier"),
        PERCENTAGE("percentage"),
        UPERCENTAGE("upercentage"),
        MPERCENTAGE("mpercentage");
        
        private String id;
        
        private Stat(String id) {
            this.id = id;
        }
        
        @Override
        public String toString() {
            return id;
        }
    }

    public enum Type {
        PASSIVE("passive"),
        GPASSIVE("gpassive");
        
        private String name;
        
        private Type(String name) {
            this.name = name;
        }
        
        @Override
        public String toString() {
            return name;
        }
    }
}
