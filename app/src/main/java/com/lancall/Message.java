package com.lancall;

import java.util.UUID;

/**
 * Model class for a text message
 */
public class Message {
    private String id;
    private String senderIp;
    private String text;
    private long timestamp;
    private MessageStatus status;

    public enum MessageStatus {
        SENDING, SENT, DELIVERED, FAILED
    }

    public Message(String senderIp, String text, long timestamp) {
        this.id = UUID.randomUUID().toString(); // Unique ID for each message
        this.senderIp = senderIp;
        this.text = text;
        this.timestamp = timestamp;
        this.status = MessageStatus.SENDING; // Default status
    }

    public String getId() {
        return id;
    }

    public void setId(String id) {
        this.id = id;
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

    public MessageStatus getStatus() {
        return status;
    }

    public void setStatus(MessageStatus status) {
        this.status = status;
    }
}