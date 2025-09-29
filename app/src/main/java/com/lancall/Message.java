package com.lancall;

/**
 * Model class for a text message
 */
public class Message {
    private String senderIp;
    private String text;
    private long timestamp;

    public Message(String senderIp, String text, long timestamp) {
        this.senderIp = senderIp;
        this.text = text;
        this.timestamp = timestamp;
    }

    public String getSenderIp() {
        return senderIp;
    }

    public void setSenderIp(String senderIp) {
        this.senderIp = senderIp;
    }

    public String getText() {
        return text;
    }

    public void setText(String text) {
        this.text = text;
    }

    public long getTimestamp() {
        return timestamp;
    }

    public void setTimestamp(long timestamp) {
        this.timestamp = timestamp;
    }
}