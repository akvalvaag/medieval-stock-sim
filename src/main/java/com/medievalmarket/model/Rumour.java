package com.medievalmarket.model;

public class Rumour {
    private final String id;
    private final String text;
    private final String eventKey;
    private final boolean isTrue;
    private int ticksRemaining;
    private String tipResult; // null, "RELIABLE", or "DUBIOUS"
    private boolean confirmed = false;

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
    public String getTipResult() { return tipResult; }
    public void setTipResult(String tipResult) { this.tipResult = tipResult; }
    public boolean isConfirmed() { return confirmed; }
    public void setConfirmed(boolean confirmed) { this.confirmed = confirmed; }
}
