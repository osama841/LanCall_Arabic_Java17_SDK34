package com.lancall;

import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.ServiceConnection;
import android.media.AudioManager;
import android.os.Bundle;
import android.os.Handler;
import android.os.IBinder;
import android.os.Looper;
import android.util.Log;
import android.widget.EditText;
import android.widget.TextView;
import android.widget.Toast;

import androidx.activity.EdgeToEdge;
import androidx.appcompat.app.AppCompatActivity;

import com.google.android.material.button.MaterialButton;

import java.util.Locale;

/**
 * نشاط إدارة المكالمة - يُفتح بعد الرد على المكالمة
 * Call Management Activity - Opens after answering a call
 */
public class CallManagementActivity extends AppCompatActivity implements CallService.CallServiceCallback {

    private static final String TAG = "CallManagementActivity";

    // UI Components
    private TextView tvCallStatus;
    private TextView tvRemoteAddress;
    private TextView tvCallDuration;
    private MaterialButton btnEndCall;
    private MaterialButton btnMute;
    private MaterialButton btnSpeaker;
    private EditText etMessage;
    private MaterialButton btnSend;

    // Service connection
    private CallService callService;
    private boolean isServiceBound = false;

    // Call state
    private String remoteIP;
    private long callStartTime = 0;
    private Handler durationHandler = new Handler(Looper.getMainLooper());
    private Runnable durationUpdateRunnable;

    private ServiceConnection serviceConnection = new ServiceConnection() {
        @Override
        public void onServiceConnected(ComponentName name, IBinder service) {
            CallService.CallServiceBinder binder = (CallService.CallServiceBinder) service;
            callService = binder.getService();
            callService.setCallback(CallManagementActivity.this);
            isServiceBound = true;

            Log.d(TAG, "CallService connected");

            // Initialize UI with current call state
            updateUIFromService();
        }

        @Override
        public void onServiceDisconnected(ComponentName name) {
            callService = null;
            isServiceBound = false;
            Log.d(TAG, "CallService disconnected");
        }
    };

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        EdgeToEdge.enable(this);
        setContentView(R.layout.activity_call_management);

        // Get remote IP from intent
        remoteIP = getIntent().getStringExtra("remote_ip");

        initializeViews();
        setupClickListeners();

        // Bind to CallService
        Intent serviceIntent = new Intent(this, CallService.class);
        bindService(serviceIntent, serviceConnection, Context.BIND_AUTO_CREATE);

        // Start call duration timer
        callStartTime = System.currentTimeMillis();
        startDurationTimer();
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();

        if (durationUpdateRunnable != null) {
            durationHandler.removeCallbacks(durationUpdateRunnable);
        }

        if (isServiceBound) {
            callService.setCallback(null);
            unbindService(serviceConnection);
            isServiceBound = false;
        }
    }

    private void initializeViews() {
        tvCallStatus = findViewById(R.id.tvCallStatus);
        tvRemoteAddress = findViewById(R.id.tvRemoteAddress);
        tvCallDuration = findViewById(R.id.tvCallDuration);
        btnEndCall = findViewById(R.id.btnEndCall);
        btnMute = findViewById(R.id.btnMute);
        btnSpeaker = findViewById(R.id.btnSpeaker);
        etMessage = findViewById(R.id.etMessage);
        btnSend = findViewById(R.id.btnSend);

        // Set initial values
        tvCallStatus.setText(R.string.call_connected);
        if (remoteIP != null) {
            tvRemoteAddress.setText("lancall://" + remoteIP + ":" + CallService.SIGNALING_PORT);
        }
    }

    private void setupClickListeners() {
        btnEndCall.setOnClickListener(v -> endCall());
        btnMute.setOnClickListener(v -> toggleMute());
        btnSpeaker.setOnClickListener(v -> toggleSpeaker());
        btnSend.setOnClickListener(v -> sendMessage());
    }

    private void updateUIFromService() {
        if (callService != null) {
            // Update remote IP if needed
            if (remoteIP == null) {
                remoteIP = callService.getRemoteIP();
                if (remoteIP != null) {
                    tvRemoteAddress.setText("lancall://" + remoteIP + ":" + CallService.SIGNALING_PORT);
                }
            }

            // Update control buttons
            updateControlButtons();
        }
    }

    private void updateControlButtons() {
        if (callService != null) {
            // تحديث زر الكتم
            boolean isMuted = callService.isMuted();
            btnMute.setText(isMuted ? "🔇" : "🎤");
            btnMute.setBackgroundColor(isMuted ? getColor(R.color.button_active) : getColor(R.color.button_inactive));

            // تحديث زر السماعة
            AudioManager audioManager = (AudioManager) getSystemService(Context.AUDIO_SERVICE);
            if (audioManager != null) {
                boolean isSpeakerOn = audioManager.isSpeakerphoneOn();
                btnSpeaker.setText(isSpeakerOn ? "🔊" : "📢");
                btnSpeaker.setBackgroundColor(
                        isSpeakerOn ? getColor(R.color.button_active) : getColor(R.color.button_inactive));
            }

            Log.d(TAG, "Control buttons updated - Muted: " + isMuted);
        }
    }

    private void endCall() {
        if (callService != null) {
            Log.d(TAG, "Ending call");
            callService.endCall();
        }
        finish();
    }

    private void toggleMute() {
        if (callService != null) {
            callService.toggleMute();
            boolean isMuted = callService.isMuted();

            // تحديث زر الكتم
            btnMute.setText(isMuted ? "🔇" : "🎤");
            btnMute.setBackgroundColor(isMuted ? getColor(R.color.button_active) : getColor(R.color.button_inactive));

            String message = isMuted ? "تم كتم الميكروفون" : "تم إلغاء كتم الميكروفون";
            Toast.makeText(this, message, Toast.LENGTH_SHORT).show();

            Log.d(TAG, "Microphone toggled - Muted: " + isMuted);
        }
    }

    private void toggleSpeaker() {
        if (callService != null) {
            // تبديل وضع السماعة الخارجية
            AudioManager audioManager = (AudioManager) getSystemService(Context.AUDIO_SERVICE);
            if (audioManager != null) {
                boolean isSpeakerOn = audioManager.isSpeakerphoneOn();
                audioManager.setSpeakerphoneOn(!isSpeakerOn);

                // تحديث نص والخلفية الزر
                btnSpeaker.setText(!isSpeakerOn ? "🔊" : "📢");
                btnSpeaker.setBackgroundColor(
                        !isSpeakerOn ? getColor(R.color.button_active) : getColor(R.color.button_inactive));

                String message = !isSpeakerOn ? "تم تفعيل السماعة الخارجية" : "تم إيقاف السماعة الخارجية";
                Toast.makeText(this, message, Toast.LENGTH_SHORT).show();

                Log.d(TAG, "Speaker toggled: " + !isSpeakerOn);
            }
        }
    }

    private void sendMessage() {
        if (callService != null && etMessage != null) {
            String message = etMessage.getText().toString().trim();
            if (!message.isEmpty()) {
                callService.sendTextMessage(message);
                etMessage.setText(""); // Clear the input field
                Toast.makeText(this, R.string.message_sent, Toast.LENGTH_SHORT).show();
            } else {
                Toast.makeText(this, R.string.message_empty, Toast.LENGTH_SHORT).show();
            }
        }
    }

    private void startDurationTimer() {
        durationUpdateRunnable = new Runnable() {
            @Override
            public void run() {
                if (callStartTime > 0) {
                    long duration = System.currentTimeMillis() - callStartTime;
                    String durationText = formatDuration(duration);
                    tvCallDuration.setText(getString(R.string.call_duration, durationText));

                    durationHandler.postDelayed(this, 1000); // Update every second
                }
            }
        };
        durationHandler.post(durationUpdateRunnable);
    }

    private String formatDuration(long durationMs) {
        long seconds = durationMs / 1000;
        long minutes = seconds / 60;
        long hours = minutes / 60;

        seconds = seconds % 60;
        minutes = minutes % 60;

        if (hours > 0) {
            return String.format(Locale.getDefault(), "%02d:%02d:%02d", hours, minutes, seconds);
        } else {
            return String.format(Locale.getDefault(), "%02d:%02d", minutes, seconds);
        }
    }

    // CallService.CallServiceCallback implementation
    @Override
    public void onIncomingCall(String fromIP) {
        // This activity should not receive incoming calls
        Log.w(TAG, "Unexpected incoming call in CallManagementActivity");
    }

    @Override
    public void onCallConnected() {
        runOnUiThread(() -> {
            Log.d(TAG, "Call connected");
            tvCallStatus.setText(R.string.call_connected);
            updateControlButtons();
        });
    }

    @Override
    public void onCallEnded() {
        runOnUiThread(() -> {
            Log.d(TAG, "Call ended");
            tvCallStatus.setText(R.string.call_ended);

            if (durationUpdateRunnable != null) {
                durationHandler.removeCallbacks(durationUpdateRunnable);
            }

            Toast.makeText(this, R.string.call_ended, Toast.LENGTH_SHORT).show();

            // Close activity after short delay
            new Handler(Looper.getMainLooper()).postDelayed(() -> finish(), 2000);
        });
    }

    @Override
    public void onCallError(String error) {
        runOnUiThread(() -> {
            Log.e(TAG, "Call error: " + error);
            Toast.makeText(this, error, Toast.LENGTH_LONG).show();
            finish();
        });
    }

    @Override
    public void onTextMessageReceived(String fromIP, String message) {
        Log.d(TAG, "onTextMessageReceived called with message: " + message + " from " + fromIP);
        runOnUiThread(() -> {
            Log.d(TAG, "Received text message: " + message);
            // For now, we'll just show a toast with the received message
            // In a more complete implementation, we would display the message in a chat
            // view
            String toastMessage = getString(R.string.message_received, fromIP, message);
            Toast.makeText(this, toastMessage, Toast.LENGTH_LONG).show();
        });
    }

    // New callback methods
    @Override
    public void onConnectionEstablished(String fromIP) {
        // Not used in this activity
    }

    @Override
    public void onMessageSendFailed(String error) {
        runOnUiThread(() -> {
            Toast.makeText(this, "فشل في إرسال الرسالة: " + error, Toast.LENGTH_LONG).show();
        });
    }

    @Override
    public void onConnectionStatusChanged(String status) {
        // Not used in this activity
    }

    @Override
    public void onBackPressed() {
        // Prevent accidental call termination
        // User must use End Call button
        Toast.makeText(this, "استخدم زر إنهاء المكالمة للخروج", Toast.LENGTH_SHORT).show();
    }
}