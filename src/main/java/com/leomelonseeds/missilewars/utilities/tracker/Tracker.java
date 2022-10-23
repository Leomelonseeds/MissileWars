package com.leomelonseeds.missilewars.utilities.tracker;

import java.util.ArrayList;
import java.util.List;

public class Tracker {
    
    List<Tracked> tracked;
    
    public Tracker() {
        tracked = new ArrayList<>();
    }
    
    public void clear() {
        tracked.clear();
    }
}
