package com.lancall;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.Service;
import android.content.Context;
import android.content.Intent;
import android.media.AudioFormat;
import android.media.AudioManager;
import android.media.AudioRecord;
import android.media.AudioTrack;
import android.media.MediaRecorder;
import android.net.wifi.WifiManager;
import android.os.Binder;
import android.os.Build;
import android.os.Handler;
import android.os.IBinder;
import android.os.Looper;
import android.text.format.Formatter;
import android.util.Log;
import android.widget.Toast;

import androidx.annotation.Nullable;
import androidx.core.app.NotificationCompat;
import androidx.core.app.NotificationManagerCompat;

import java.io.IOException;
import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.InetAddress;
import java.net.ServerSocket;
import java.net.Socket;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * خدمة إدارة المكالمات - تعمل في الخلفية لمعالجة المكالمات الصوتية
 * Call Management Service - runs in background to handle voice calls
 */
public class CallService extends Service {

    private static final String TAG = "CallService";
    private static final String CHANNEL_ID = "LanCallService";
    private static final int NOTIFICATION_ID = 1001;

    // Audio configuration
    private static final int SAMPLE_RATE = 16000;
    private static final int AUDIO_FORMAT = AudioFormat.ENCODING_PCM_16BIT;
    private static final int CHANNEL_CONFIG_IN = AudioFormat.CHANNEL_IN_MONO;
    private static final int CHANNEL_CONFIG_OUT = AudioFormat.CHANNEL_OUT_MONO;
    private static final int BUFFER_SIZE = AudioRecord.getMinBufferSize(SAMPLE_RATE, CHANNEL_CONFIG_IN, AUDIO_FORMAT);

    // Network configuration
    public static final int SIGNALING_PORT = 10001;
    public static final int AUDIO_PORT = 10002;

    // Service state
    private boolean isServiceRunning = false;
    private boolean isInCall = false;
    private boolean isMuted = false;

    // Network components
    private ServerSocket signalingServer;
    private DatagramSocket audioSocket;
    private ExecutorService executorService;

    // Audio components
    private AudioRecord audioRecord;
    private AudioTrack audioTrack;

    // Current call information
    private String remoteIP;
    private CallState currentCallState = CallState.IDLE;

    // Callback interface
    public interface CallServiceCallback {
        void onIncomingCall(String fromIP);

        void onCallConnected();

        void onCallEnded();

        void onCallError(String error);
    }

    private CallServiceCallback callback;

    public enum CallState {
        IDLE, INCOMING, OUTGOING, CONNECTED, ENDED
    }

    public class CallServiceBinder extends Binder {
        public CallService getService() {
            return CallService.this;
        }
    }

    private final IBinder binder = new CallServiceBinder();

    @Nullable
    @Override
    public IBinder onBind(Intent intent) {
        return binder;
    }

    @Override
    public void onCreate() {
        super.onCreate();
        executorService = Executors.newCachedThreadPool();
        createNotificationChannel();
        Log.d(TAG, "CallService created");
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        if (!isServiceRunning) {
            startForegroundService();
            startListening();
        }
        return START_STICKY;
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
        stopService();
        if (executorService != null) {
            executorService.shutdown();
        }
        Log.d(TAG, "CallService destroyed");
    }

    private void createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            NotificationChannel channel = new NotificationChannel(
                    CHANNEL_ID,
                    "خدمة المكالمات",
                    NotificationManager.IMPORTANCE_LOW);
            channel.setDescription("خدمة معالجة المكالمات المحلية");
            NotificationManager manager = getSystemService(NotificationManager.class);
            if (manager != null) {
                manager.createNotificationChannel(channel);
            }
        }
    }

    private void startForegroundService() {
        Intent notificationIntent = new Intent(this, MainActivity.class);
        PendingIntent pendingIntent = PendingIntent.getActivity(
                this, 0, notificationIntent,
                Build.VERSION.SDK_INT >= Build.VERSION_CODES.M ? PendingIntent.FLAG_IMMUTABLE
                        : PendingIntent.FLAG_UPDATE_CURRENT);

        Notification notification = new NotificationCompat.Builder(this, CHANNEL_ID)
                .setContentTitle("خدمة مكالمات LanCall")
                .setContentText("جاهز لاستقبال المكالمات - المنفذ: " + SIGNALING_PORT)
                .setSmallIcon(R.mipmap.ic_launcher)
                .setContentIntent(pendingIntent)
                .setPriority(NotificationCompat.PRIORITY_LOW)
                .setOngoing(true)
                .build();

        startForeground(NOTIFICATION_ID, notification);
        isServiceRunning = true;
    }

    private void updateNotification(String text) {
        Intent notificationIntent = new Intent(this, MainActivity.class);
        PendingIntent pendingIntent = PendingIntent.getActivity(
                this, 0, notificationIntent,
                Build.VERSION.SDK_INT >= Build.VERSION_CODES.M ? PendingIntent.FLAG_IMMUTABLE
                        : PendingIntent.FLAG_UPDATE_CURRENT);

        Notification notification = new NotificationCompat.Builder(this, CHANNEL_ID)
                .setContentTitle("خدمة مكالمات LanCall")
                .setContentText(text)
                .setSmallIcon(R.mipmap.ic_launcher)
                .setContentIntent(pendingIntent)
                .setPriority(NotificationCompat.PRIORITY_LOW)
                .setOngoing(true)
                .build();

        NotificationManager manager = getSystemService(NotificationManager.class);
        if (manager != null) {
            manager.notify(NOTIFICATION_ID, notification);
        }
    }

    private void startListening() {
        executorService.execute(() -> {
            try {
                // Start signaling server
                signalingServer = new ServerSocket(SIGNALING_PORT);
                Log.d(TAG, "Signaling server started on port " + SIGNALING_PORT);

                while (isServiceRunning && !signalingServer.isClosed()) {
                    try {
                        Log.d(TAG, "Waiting for incoming connections...");
                        Socket clientSocket = signalingServer.accept();
                        Log.d(TAG, "New connection received from: " + clientSocket.getInetAddress().getHostAddress());
                        executorService.execute(() -> handleSignalingConnection(clientSocket));
                    } catch (IOException e) {
                        if (isServiceRunning) {
                            Log.e(TAG, "Error accepting signaling connection", e);
                        }
                    }
                }
            } catch (IOException e) {
                Log.e(TAG, "Error starting signaling server", e);
                if (callback != null) {
                    new Handler(Looper.getMainLooper()).post(() -> callback.onCallError("فشل في بدء الخدمة"));
                }
            }
        });
    }

    private void handleSignalingConnection(Socket clientSocket) {
        Log.d(TAG, "Handling signaling connection from: " + clientSocket.getInetAddress());

        try {
            String fromIP = clientSocket.getInetAddress().getHostAddress();
            remoteIP = fromIP;
            currentCallState = CallState.INCOMING;

            Log.d(TAG, "Incoming call from: " + fromIP);

            // تحديث الإشعار لإظهار المكالمة الواردة
            updateNotification("مكالمة واردة من: " + fromIP);

            // لا نبدأ audio streaming تلقائياً - ننتظر المستخدم يضغط "رد"
            // currentCallState يبقى INCOMING حتى يرد المستخدم

            // إرسال إشعار باتصال وارد
            if (callback != null) {
                new Handler(Looper.getMainLooper()).post(() -> {
                    Log.d(TAG, "Calling callback.onIncomingCall for: " + fromIP);
                    callback.onIncomingCall(fromIP);
                    // لا نرسل onCallConnected تلقائياً - ننتظر المستخدم يضغط "رد"
                });
            } else {
                Log.w(TAG, "No callback set for incoming call!");
            }

        } catch (Exception e) {
            Log.e(TAG, "Error handling signaling connection", e);
        } finally {
            try {
                clientSocket.close();
            } catch (IOException e) {
                Log.e(TAG, "Error closing signaling socket", e);
            }
        }
    }

    public void makeCall(String targetIP, int targetPort) {
        if (isInCall) {
            Log.w(TAG, "Already in call");
            if (callback != null) {
                new Handler(Looper.getMainLooper()).post(() -> callback.onCallError("مكالمة جارية بالفعل"));
            }
            return;
        }

        remoteIP = targetIP;
        currentCallState = CallState.OUTGOING;

        Log.d(TAG, "Making call to: " + targetIP + ":" + targetPort);

        executorService.execute(() -> {
            try {
                // محاولة الاتصال بخادم الإشارات في الجهاز المستهدف
                Socket signalingSocket = new Socket(targetIP, targetPort);

                Log.d(TAG, "Connected to target signaling server");

                // إرسال إشارة بالاتصال
                String localIP = getLocalIPv4();
                Log.d(TAG, "Sending call signal from: " + localIP);

                // إغلاق الاتصال فوراً لتفعيل handleSignalingConnection في الجهة الأخرى
                signalingSocket.close();

                // انتظار قصير ثم اعتبار الاتصال متصل
                Thread.sleep(2000);

                currentCallState = CallState.CONNECTED;
                isInCall = true;

                if (callback != null) {
                    new Handler(Looper.getMainLooper()).post(() -> callback.onCallConnected());
                }

                Log.d(TAG, "Call connected, starting audio streaming");
                startAudioStreaming();

            } catch (Exception e) {
                Log.e(TAG, "Error making call: " + e.getMessage(), e);
                currentCallState = CallState.ENDED;
                if (callback != null) {
                    new Handler(Looper.getMainLooper())
                            .post(() -> callback.onCallError("فشل الاتصال: " + e.getMessage()));
                }
            }
        });
    }

    public void answerCall() {
        if (currentCallState != CallState.INCOMING) {
            Log.w(TAG, "No incoming call to answer");
            return;
        }

        currentCallState = CallState.CONNECTED;
        isInCall = true;

        if (callback != null) {
            callback.onCallConnected();
        }

        startAudioStreaming();
    }

    public void declineCall() {
        if (currentCallState != CallState.INCOMING) {
            Log.w(TAG, "No incoming call to decline");
            return;
        }

        currentCallState = CallState.ENDED;

        if (callback != null) {
            callback.onCallEnded();
        }
    }

    public void endCall() {
        if (!isInCall) {
            Log.w(TAG, "Not in call");
            return;
        }

        isInCall = false;
        currentCallState = CallState.ENDED;

        stopAudioStreaming();

        if (callback != null) {
            callback.onCallEnded();
        }
    }

    public void toggleMute() {
        isMuted = !isMuted;
        Log.d(TAG, "Mute toggled: " + isMuted);
    }

    public boolean isMuted() {
        return isMuted;
    }

    public CallState getCallState() {
        return currentCallState;
    }

    public String getRemoteIP() {
        return remoteIP;
    }

    public void setCallback(CallServiceCallback callback) {
        this.callback = callback;
    }

    private void startAudioStreaming() {
        executorService.execute(() -> {
            try {
                Log.d(TAG, "Starting audio streaming with remote IP: " + remoteIP);

                // Initialize audio components
                audioSocket = new DatagramSocket(AUDIO_PORT);
                Log.d(TAG, "Audio socket created on port: " + AUDIO_PORT);

                // تفعيل وضع المكالمة في مدير الصوت
                AudioManager audioManager = (AudioManager) getSystemService(Context.AUDIO_SERVICE);
                if (audioManager != null) {
                    audioManager.setMode(AudioManager.MODE_IN_COMMUNICATION);
                    audioManager.setSpeakerphoneOn(false); // استخدام سماعة الأذن
                    Log.d(TAG, "Audio manager configured for call mode");
                }

                // Start audio recording
                if (audioRecord == null) {
                    audioRecord = new AudioRecord(
                            MediaRecorder.AudioSource.VOICE_COMMUNICATION, // مصدر أفضل للمكالمات
                            SAMPLE_RATE,
                            CHANNEL_CONFIG_IN,
                            AUDIO_FORMAT,
                            BUFFER_SIZE * 2); // مضاعفة حجم الذاكرة
                }

                // Start audio playback
                if (audioTrack == null) {
                    audioTrack = new AudioTrack(
                            AudioManager.STREAM_VOICE_CALL,
                            SAMPLE_RATE,
                            CHANNEL_CONFIG_OUT,
                            AUDIO_FORMAT,
                            BUFFER_SIZE * 2, // مضاعفة حجم الذاكرة
                            AudioTrack.MODE_STREAM);
                }

                audioRecord.startRecording();
                audioTrack.play();

                // Start audio streaming threads
                executorService.execute(this::audioSendingLoop);
                executorService.execute(this::audioReceivingLoop);

                Log.d(TAG, "Audio streaming started");

            } catch (Exception e) {
                Log.e(TAG, "Error starting audio streaming", e);
                if (callback != null) {
                    new Handler(Looper.getMainLooper()).post(() -> callback.onCallError("فشل في بدء الصوت"));
                }
            }
        });
    }

    private void audioSendingLoop() {
        byte[] buffer = new byte[BUFFER_SIZE];
        long sequenceNumber = 0;
        Log.d(TAG, "Audio sending loop started, sending to: " + remoteIP + ":" + AUDIO_PORT);

        while (isInCall && audioRecord != null) {
            try {
                int bytesRead = audioRecord.read(buffer, 0, buffer.length);

                if (bytesRead > 0 && !isMuted) {
                    // Create audio packet and send to remote
                    DatagramPacket packet = new DatagramPacket(
                            buffer, bytesRead,
                            InetAddress.getByName(remoteIP),
                            AUDIO_PORT);

                    if (audioSocket != null) {
                        audioSocket.send(packet);
                        if (sequenceNumber % 100 == 0) { // طباعة كل 100 حزمة
                            Log.d(TAG, "Sent audio packet " + sequenceNumber + ", bytes: " + bytesRead);
                        }
                    }

                    sequenceNumber++;
                }

                Thread.sleep(20); // ~50 FPS audio

            } catch (Exception e) {
                Log.e(TAG, "Error in audio sending loop: " + e.getMessage(), e);
                break;
            }
        }
        Log.d(TAG, "Audio sending loop ended");
    }

    private void audioReceivingLoop() {
        byte[] buffer = new byte[BUFFER_SIZE];
        long receivedPackets = 0;
        Log.d(TAG, "Audio receiving loop started, listening on port: " + AUDIO_PORT);

        while (isInCall && audioTrack != null) {
            try {
                DatagramPacket packet = new DatagramPacket(buffer, buffer.length);

                if (audioSocket != null) {
                    audioSocket.receive(packet);

                    // Play received audio
                    int written = audioTrack.write(packet.getData(), 0, packet.getLength());

                    receivedPackets++;
                    if (receivedPackets % 100 == 0) { // طباعة كل 100 حزمة
                        Log.d(TAG, "Received audio packet " + receivedPackets + ", bytes: " + packet.getLength()
                                + ", written: " + written);
                    }
                }

            } catch (Exception e) {
                Log.e(TAG, "Error in audio receiving loop: " + e.getMessage(), e);
                break;
            }
        }
        Log.d(TAG, "Audio receiving loop ended");
    }

    private void stopAudioStreaming() {
        try {
            if (audioRecord != null) {
                audioRecord.stop();
                audioRecord.release();
                audioRecord = null;
            }

            if (audioTrack != null) {
                audioTrack.stop();
                audioTrack.release();
                audioTrack = null;
            }

            if (audioSocket != null) {
                audioSocket.close();
                audioSocket = null;
            }

            Log.d(TAG, "Audio streaming stopped");

        } catch (Exception e) {
            Log.e(TAG, "Error stopping audio streaming", e);
        }
    }

    private void stopService() {
        isServiceRunning = false;
        isInCall = false;

        try {
            if (signalingServer != null) {
                signalingServer.close();
            }
        } catch (IOException e) {
            Log.e(TAG, "Error closing signaling server", e);
        }

        stopAudioStreaming();
        stopForeground(true);
    }

    private String getLocalIPv4() {
        try {
            WifiManager wm = (WifiManager) getApplicationContext().getSystemService(WIFI_SERVICE);
            if (wm == null)
                return null;
            int ip = wm.getConnectionInfo().getIpAddress();
            if (ip == 0)
                return null;
            return Formatter.formatIpAddress(ip);
        } catch (Exception e) {
            return null;
        }
    }
}