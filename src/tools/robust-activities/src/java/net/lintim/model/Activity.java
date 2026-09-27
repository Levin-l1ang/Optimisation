package net.lintim.model;

public class Activity {

    private int index;
    private String type;
    private int fromEvent;
    private int toEvent;
    private int lowerBound;
    private int upperBound;
    private int buffer;
    private double bufferWeight;
    private double passenger;

    public Activity(int index, String type, int fromEvent, int toEvent, int lowerBound, int upperBound, int buffer, double bufferWeight, double passenger) {
        super();
        this.index = index;
        this.type = type;
        this.fromEvent = fromEvent;
        this.toEvent = toEvent;
        this.lowerBound = lowerBound;
        this.upperBound = upperBound;
        this.buffer = buffer;
        this.bufferWeight = bufferWeight;
        this.passenger = passenger;
    }

    public int getIndex() {
        return index;
    }

    public void setIndex(int index) {
        this.index = index;
    }

    public String getType() {
        return type;
    }

    public void setType(String type) {
        this.type = type;
    }

    public int getFromEvent() {
        return fromEvent;
    }

    public void setFromEvent(int fromEvent) {
        this.fromEvent = fromEvent;
    }

    public int getToEvent() {
        return toEvent;
    }

    public void setToEvent(int toEvent) {
        this.toEvent = toEvent;
    }

    public int getLowerBound() {
        return lowerBound;
    }

    public void setLowerBound(int lowerBound) {
        this.lowerBound = lowerBound;
    }

    public int getUpperBound() {
        return upperBound;
    }

    public void setUpperBound(int upperBound) {
        this.upperBound = upperBound;
    }

    public int getBuffer() {
        return buffer;
    }

    public void setBuffer(int buffer) {
        this.buffer = buffer;
    }

    public double getBufferWeight() {
        return bufferWeight;
    }

    public void setBufferWeight(double bufferWeight) {
        this.bufferWeight = bufferWeight;
    }

    public double getPassenger() {
        return passenger;
    }

    public void setPassenger(double passenger) {
        this.passenger = passenger;
    }

}
