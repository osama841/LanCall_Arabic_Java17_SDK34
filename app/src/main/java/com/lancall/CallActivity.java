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
import android.view.View;
import android.widget.TextView;
import android.widget.Toast;

import androidx.activity.EdgeToEdge;
import androidx.appcompat.app.AppCompatActivity;

import com.google.android.material.button.MaterialButton;

import java.util.Locale;

/**
 * شاشة المكالمة الرئيسية - تدير واجهة المستخدم أثناء المكالمة
 * Main call activity - manages UI during call
 */
public class CallActivity extends AppCompatActivity implements CallService.CallServiceCallback {

    private static final String TAG = "CallActivity";

    // UI Components
    private TextView tvCallStatus;
    private TextView tvRemoteAddress;
    private TextView tvCallDuration;
    private MaterialButton btnAnswer;
    private MaterialButton btnDecline;
    private MaterialButton btnEndCall;
    private MaterialButton btnMute;
    private MaterialButton btnSpeaker;
    private View layoutIncomingCall;
    private View layoutCallControls;

    // Service connection
    private CallService callService;
    private boolean isServiceBound = false;

    // Call state
    private String mode; // "incoming" or "outgoing"
    private String targetIP;
    private int targetPort;
    private long callStartTime = 0;
    private Handler durationHandler = new Handler(Looper.getMainLooper());
    private Runnable durationUpdateRunnable;

    private ServiceConnection serviceConnection = new ServiceConnection() {
        @Override
        public void onServiceConnected(ComponentName name, IBinder service) {
            CallService.CallServiceBinder binder = (CallService.CallServiceBinder) service;
            callService = binder.getService();
            callService.setCallback(CallActivity.this);
            isServiceBound = true;

            Log.d(TAG, "CallService connected");

            // Initialize call based on mode
            if ("outgoing".equals(mode)) {
                initiateOutgoingCall();
            }
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
        setContentView(R.layout.activity_call);

        // Get intent extras
        mode = getIntent().getStringExtra("mode");
        targetIP = getIntent().getStringExtra("target_ip");
        targetPort = getIntent().getIntExtra("target_port", CallService.SIGNALING_PORT);

        initializeViews();
        setupClickListeners();

        // Start and bind to CallService
        Intent serviceIntent = new Intent(this, CallService.class);
        startService(serviceIntent);
        bindService(serviceIntent, serviceConnection, Context.BIND_AUTO_CREATE);

        // Update UI based on mode
        if ("incoming".equals(mode)) {
            showIncomingCallUI();
        } else {
            showOutgoingCallUI();
        }
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
        btnAnswer = findViewById(R.id.btnAnswer);
        btnDecline = findViewById(R.id.btnDecline);
        btnEndCall = findViewById(R.id.btnEndCall);
        btnMute = findViewById(R.id.btnMute);
        btnSpeaker = findViewById(R.id.btnSpeaker);
        layoutIncomingCall = findViewById(R.id.layoutIncomingCall);
        layoutCallControls = findViewById(R.id.layoutCallControls);

        // Set remote address
        if (targetIP != null) {
            tvRemoteAddress.setText("lancall://" + targetIP + ":" + targetPort);
        }
    }

    private void setupClickListeners() {
        btnAnswer.setOnClickListener(v -> answerCall());
        btnDecline.setOnClickListener(v -> declineCall());
        btnEndCall.setOnClickListener(v -> endCall());
        btnMute.setOnClickListener(v -> toggleMute());
        btnSpeaker.setOnClickListener(v -> toggleSpeaker());
    }

    private void showIncomingCallUI() {
        tvCallStatus.setText(R.string.call_incoming);
        layoutIncomingCall.setVisibility(View.VISIBLE);
        layoutCallControls.setVisibility(View.GONE);
        tvCallDuration.setVisibility(View.GONE);
    }

    private void showOutgoingCallUI() {
        tvCallStatus.setText(R.string.call_connecting);
        layoutIncomingCall.setVisibility(View.GONE);
        layoutCallControls.setVisibility(View.GONE);
        tvCallDuration.setVisibility(View.GONE);
    }

    private void showConnectedCallUI() {
        tvCallStatus.setText(R.string.call_connected);
        layoutIncomingCall.setVisibility(View.GONE);
        layoutCallControls.setVisibility(View.VISIBLE);
        tvCallDuration.setVisibility(View.VISIBLE);

        // تهيئة أزرار التحكم
        updateControlButtons();

        // Start duration timer
        callStartTime = System.currentTimeMillis();
        startDurationTimer();
    }

    private void updateControlButtons() {
        if (callService != null) {
            // تحديث زر الكتم
            boolean isMuted = callService.isMuted();
            btnMute.setText(isMuted ? "🔇" : "🎤");

            // تحديث زر السماعة
            AudioManager audioManager = (AudioManager) getSystemService(Context.AUDIO_SERVICE);
            if (audioManager != null) {
                boolean isSpeakerOn = audioManager.isSpeakerphoneOn();
                btnSpeaker.setText(isSpeakerOn ? "🔊" : "📢");
            }

            // تحديث زر إنهاء المكالمة
            btnEndCall.setText("✆️");

            Log.d(TAG, "Control buttons updated - Muted: " + isMuted);
        }
    }

    private void initiateOutgoingCall() {
        if (callService != null && targetIP != null) {
            Log.d(TAG, "Initiating call to: " + targetIP + ":" + targetPort);
            callService.makeCall(targetIP, targetPort);
        } else {
            Toast.makeText(this, R.string.error_connection_failed, Toast.LENGTH_SHORT).show();
            finish();
        }
    }

    private void answerCall() {
        if (callService != null) {
            Log.d(TAG, "Answering call");
            callService.answerCall();

            // فتح شاشة إدارة المكالمة بعد الرد
            Intent callManagementIntent = new Intent(this, CallManagementActivity.class);
            callManagementIntent.putExtra("remote_ip", targetIP != null ? targetIP : callService.getRemoteIP());
            startActivity(callManagementIntent);

            // إغلاق شاشة المكالمة الحالية
            finish();
        }
    }

    private void declineCall() {
        if (callService != null) {
            Log.d(TAG, "Declining call");
            callService.declineCall();
        }
        finish();
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

                // تحديث نص الزر
                btnSpeaker.setText(!isSpeakerOn ? "🔊" : "📢");

                String message = !isSpeakerOn ? "تم تفعيل السماعة الخارجية" : "تم إيقاف السماعة الخارجية";
                Toast.makeText(this, message, Toast.LENGTH_SHORT).show();

                Log.d(TAG, "Speaker toggled: " + !isSpeakerOn);
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
        runOnUiThread(() -> {
            Log.d(TAG, "Incoming call from: " + fromIP);
            targetIP = fromIP;
            tvRemoteAddress.setText("lancall://" + fromIP + ":" + CallService.SIGNALING_PORT);
            showIncomingCallUI();
        });
    }

    @Override
    public void onCallConnected() {
        runOnUiThread(() -> {
            Log.d(TAG, "Call connected");

            // فتح شاشة إدارة المكالمة عند الاتصال
            Intent callManagementIntent = new Intent(this, CallManagementActivity.class);
            callManagementIntent.putExtra("remote_ip",
                    targetIP != null ? targetIP : (callService != null ? callService.getRemoteIP() : null));
            startActivity(callManagementIntent);

            // إغلاق شاشة المكالمة الحالية
            finish();
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
    }
}