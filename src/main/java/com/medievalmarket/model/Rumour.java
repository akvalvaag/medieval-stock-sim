package com.medievalmarket.model;

public class Rumour {
    private final String id;
    private final String text;
    private final String eventKey;
    private final boolean isTrue;
    private int ticksRemaining;

    public Rumour(String id, String text, String eventKey, boolean isTrue, int ticksRemaining) {
        this.id = id;
        this.text = text;
        this.eventKey = eventKey;
        this.isTrue = isTrue;
        this.ticksRemaining = ticksRemaining;
    }

    public String getId() { return id; }
    public String getText() { return text; }
    public String getEventKey() { return eventKey; }
    public boolean isTrue() { return isTrue; }
    public int getTicksRemaining() { return ticksRemaining; }
    public void decrementTick() { ticksRemaining--; }
}
