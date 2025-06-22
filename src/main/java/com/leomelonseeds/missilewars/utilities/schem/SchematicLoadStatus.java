package com.leomelonseeds.missilewars.utilities.schem;

/** Mainly denotes the various errors that are possible when spawning a schematic */
public enum SchematicLoadStatus {
    
    NONE(""),
    IN_LOBBY("How did you get that here?"),
    OUT_OF_BOUNDS("This location is out of bounds!"),
    FILE_MISSING("The file for this structure is missing! Please contact an admin."),
    IN_OWN_BASE("You cannot spawn missiles inside your base!"),
    UNBREAKABLE_BLOCKS("You cannot spawn structures inside unbreakable blocks!"),
    SUCCESS("");
    
    private String message;
    
    private SchematicLoadStatus(String message) {
        this.message = message;
    }
    
    public String getMessage() {
        return message;
    }
}
