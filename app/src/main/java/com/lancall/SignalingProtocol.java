package com.lancall;

import com.google.gson.Gson;
import com.google.gson.JsonSyntaxException;

/**
 * نظام بروتوكول الإشارات للتواصل بين الأجهزة
 * Signaling Protocol for inter-device communication
 */
public class SignalingProtocol {

    public static final String MESSAGE_TYPE_CALL_REQUEST = "CALL_REQUEST";
    public static final String MESSAGE_TYPE_CALL_ACCEPT = "CALL_ACCEPT";
    public static final String MESSAGE_TYPE_CALL_DECLINE = "CALL_DECLINE";
    public static final String MESSAGE_TYPE_CALL_END = "CALL_END";
    public static final String MESSAGE_TYPE_AUDIO_DATA = "AUDIO_DATA";
    public static final String MESSAGE_TYPE_KEEP_ALIVE = "KEEP_ALIVE";
    public static final String MESSAGE_TYPE_ERROR = "ERROR";
    public static final String MESSAGE_TYPE_TEXT_MESSAGE = "TEXT_MESSAGE"; // New message type for text messaging
    public static final String MESSAGE_TYPE_CONNECTION_REQUEST = "CONNECTION_REQUEST"; // New message type for
                                                                                       // connection request
    public static final String MESSAGE_TYPE_CONNECTION_ACK = "CONNECTION_ACK"; // New message type for connection
                                                                               // acknowledgment

    private static final Gson gson = new Gson();

    /**
     * رسالة أساسية للبروتوكول
     * Base message class for protocol
     */
    public static class Message {
        public String type;
        public String fromIp;
        public long timestamp;
        public Object data;

        public Message(String type, String fromIp, Object data) {
            this.type = type;
            this.fromIp = fromIp;
            this.timestamp = System.currentTimeMillis();
            this.data = data;
        }
    }

    /**
     * بيانات طلب المكالمة
     * Call request data
     */
    public static class CallRequestData {
        public String callerName;
        public String callerIp;
        public int audioPort;

        public CallRequestData(String callerName, String callerIp, int audioPort) {
            this.callerName = callerName;
            this.callerIp = callerIp;
            this.audioPort = audioPort;
        }
    }

    /**
     * بيانات قبول المكالمة
     * Call accept data
     */
    public static class CallAcceptData {
        public String receiverIp;
        public int audioPort;

        public CallAcceptData(String receiverIp, int audioPort) {
            this.receiverIp = receiverIp;
            this.audioPort = audioPort;
        }
    }

    /**
     * بيانات الصوت
     * Audio data
     */
    public static class AudioData {
        public byte[] audioBytes;
        public long sequenceNumber;

        public AudioData(byte[] audioBytes, long sequenceNumber) {
            this.audioBytes = audioBytes;
            this.sequenceNumber = sequenceNumber;
        }
    }

    /**
     * بيانات الرسائل النصية
     * Text message data
     */
    public static class TextMessageData {
        public String message;
        public long timestamp;

        public TextMessageData(String message) {
            this.message = message;
            this.timestamp = System.currentTimeMillis();
        }
    }

    /**
     * بيانات طلب الاتصال
     * Connection request data
     */
    public static class ConnectionRequestData {
        public String requesterId;
        public long timestamp;

        public ConnectionRequestData(String requesterId) {
            this.requesterId = requesterId;
            this.timestamp = System.currentTimeMillis();
        }
    }

    /**
     * بيانات الخطأ
     * Error data
     */
    public static class ErrorData {
        public String errorCode;
        public String errorMessage;

        public ErrorData(String errorCode, String errorMessage) {
            this.errorCode = errorCode;
            this.errorMessage = errorMessage;
        }
    }

    /**
     * تحويل الرسالة إلى JSON
     * Convert message to JSON
     */
    public static String messageToJson(Message message) {
        try {
            return gson.toJson(message);
        } catch (Exception e) {
            return null;
        }
    }

    /**
     * تحويل JSON إلى رسالة
     * Convert JSON to message
     */
    public static Message jsonToMessage(String json) {
        try {
            return gson.fromJson(json, Message.class);
        } catch (JsonSyntaxException e) {
            return null;
        }
    }

    /**
     * إنشاء رسالة طلب مكالمة
     * Create call request message
     */
    public static Message createCallRequest(String fromIp, String callerName, int audioPort) {
        CallRequestData data = new CallRequestData(callerName, fromIp, audioPort);
        return new Message(MESSAGE_TYPE_CALL_REQUEST, fromIp, data);
    }

    /**
     * إنشاء رسالة قبول المكالمة
     * Create call accept message
     */
    public static Message createCallAccept(String fromIp, int audioPort) {
        CallAcceptData data = new CallAcceptData(fromIp, audioPort);
        return new Message(MESSAGE_TYPE_CALL_ACCEPT, fromIp, data);
    }

    /**
     * إنشاء رسالة رفض المكالمة
     * Create call decline message
     */
    public static Message createCallDecline(String fromIp) {
        return new Message(MESSAGE_TYPE_CALL_DECLINE, fromIp, null);
    }

    /**
     * إنشاء رسالة إنهاء المكالمة
     * Create call end message
     */
    public static Message createCallEnd(String fromIp) {
        return new Message(MESSAGE_TYPE_CALL_END, fromIp, null);
    }

    /**
     * إنشاء رسالة بيانات صوتية
     * Create audio data message
     */
    public static Message createAudioData(String fromIp, byte[] audioBytes, long sequenceNumber) {
        AudioData data = new AudioData(audioBytes, sequenceNumber);
        return new Message(MESSAGE_TYPE_AUDIO_DATA, fromIp, data);
    }

    /**
     * إنشاء رسالة نصية
     * Create text message
     */
    public static Message createTextMessage(String fromIp, String message) {
        TextMessageData data = new TextMessageData(message);
        return new Message(MESSAGE_TYPE_TEXT_MESSAGE, fromIp, data);
    }

    /**
     * إنشاء رسالة طلب اتصال
     * Create connection request message
     */
    public static Message createConnectionRequest(String fromIp) {
        ConnectionRequestData data = new ConnectionRequestData(fromIp);
        return new Message(MESSAGE_TYPE_CONNECTION_REQUEST, fromIp, data);
    }

    /**
     * إنشاء رسالة تأكيد اتصال
     * Create connection acknowledgment message
     */
    public static Message createConnectionAck(String fromIp) {
        return new Message(MESSAGE_TYPE_CONNECTION_ACK, fromIp, null);
    }

    /**
     * إنشاء رسالة خطأ
     * Create error message
     */
    public static Message createError(String fromIp, String errorCode, String errorMessage) {
        ErrorData data = new ErrorData(errorCode, errorMessage);
        return new Message(MESSAGE_TYPE_ERROR, fromIp, data);
    }

    /**
     * إنشاء رسالة keep alive
     * Create keep alive message
     */
    public static Message createKeepAlive(String fromIp) {
        return new Message(MESSAGE_TYPE_KEEP_ALIVE, fromIp, null);
    }
}