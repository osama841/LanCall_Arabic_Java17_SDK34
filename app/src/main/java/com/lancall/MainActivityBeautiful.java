package com.lancall;

import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.ServiceConnection;
import android.net.wifi.WifiInfo;
import android.net.wifi.WifiManager;
import android.os.Bundle;
import android.os.IBinder;
import android.text.format.Formatter;
import android.widget.TextView;
import android.widget.Toast;

import androidx.activity.EdgeToEdge;
import androidx.appcompat.app.AppCompatActivity;

import com.google.android.material.button.MaterialButton;

/**
 * نشاط رئيسي جميل - Beautiful Main Activity
 * تصميم محسن وجميل لواجهة المستخدم
 */
public class MainActivityBeautiful extends AppCompatActivity implements CallService.CallServiceCallback {

    // UI Components
    private MaterialButton btnShowQr, btnScanQr, btnCall, btnMessaging, btnHelp, btnSettings;
    private TextView localIpText, connectionStatusText;

    // Service connection
    private CallService callService;
    private boolean isServiceBound = false;
    private String localIpAddress;
    private String remoteIpAddress;
    private boolean isConnected = false;

    // Service connection handler
    private ServiceConnection serviceConnection = new ServiceConnection() {
        @Override
        public void onServiceConnected(ComponentName name, IBinder service) {
            CallService.CallServiceBinder binder = (CallService.CallServiceBinder) service;
            callService = binder.getService();
            callService.setCallback(MainActivityBeautiful.this);
            isServiceBound = true;

            // Update connection status
            updateConnectionStatus();
        }

        @Override
        public void onServiceDisconnected(ComponentName name) {
            callService = null;
            isServiceBound = false;
            isConnected = false;

            // Update connection status
            updateConnectionStatus();
        }
    };

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        EdgeToEdge.enable(this);
        setContentView(R.layout.activity_main_beautiful);

        initializeViews();
        setupClickListeners();

        // Get local IP address
        localIpAddress = getLocalIpAddress();
        localIpText.setText("IP: " + localIpAddress);

        // Start and bind to CallService
        Intent serviceIntent = new Intent(this, CallService.class);
        startService(serviceIntent);
        bindService(serviceIntent, serviceConnection, Context.BIND_AUTO_CREATE);
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        if (isServiceBound) {
            callService.setCallback(null);
            unbindService(serviceConnection);
            isServiceBound = false;
        }
    }

    private void initializeViews() {
        // Main action buttons
        btnShowQr = findViewById(R.id.btnShowQr);
        btnScanQr = findViewById(R.id.btnScanQr);
        btnCall = findViewById(R.id.btnCall);
        btnMessaging = findViewById(R.id.btnMessaging);

        // Bottom action buttons
        btnHelp = findViewById(R.id.btnHelp);
        btnSettings = findViewById(R.id.btnSettings);

        // Text views
        localIpText = findViewById(R.id.localIpText);
        connectionStatusText = findViewById(R.id.connectionStatusText);
    }

    private void setupClickListeners() {
        // Main action buttons
        btnShowQr.setOnClickListener(v -> showQrCode());
        btnScanQr.setOnClickListener(v -> scanQrCode());
        btnCall.setOnClickListener(v -> startCall());
        btnMessaging.setOnClickListener(v -> openMessaging());

        // Bottom action buttons
        btnHelp.setOnClickListener(v -> showHelp());
        btnSettings.setOnClickListener(v -> openSettings());
    }

    private void showQrCode() {
        Toast.makeText(this, "عرض رمز QR", Toast.LENGTH_SHORT).show();
        // Start QR activity in display mode
        Intent intent = new Intent(this, QrActivity.class);
        startActivity(intent);
    }

    private void scanQrCode() {
        Toast.makeText(this, "مسح رمز QR", Toast.LENGTH_SHORT).show();
        // Start QR activity in scan mode
        Intent intent = new Intent(this, QrActivity.class);
        intent.putExtra("mode", "scan");
        startActivity(intent);
    }

    private void startCall() {
        if (!isConnected) {
            Toast.makeText(this, "لا يوجد اتصال بجهاز آخر", Toast.LENGTH_SHORT).show();
            return;
        }

        if (callService == null) {
            Toast.makeText(this, "الخدمة غير متصلة", Toast.LENGTH_SHORT).show();
            return;
        }

        Toast.makeText(this, "بدء مكالمة", Toast.LENGTH_SHORT).show();
        // Start call activity
        Intent intent = new Intent(this, CallActivity.class);
        intent.putExtra("mode", "outgoing");
        intent.putExtra("target_ip", remoteIpAddress);
        intent.putExtra("target_port", CallService.SIGNALING_PORT);
        startActivity(intent);
    }

    private void openMessaging() {
        if (!isConnected) {
            Toast.makeText(this, "لا يوجد اتصال بجهاز آخر", Toast.LENGTH_SHORT).show();
            return;
        }

        Toast.makeText(this, "فتح المراسلة", Toast.LENGTH_SHORT).show();
        // Start messaging activity
        Intent intent = new Intent(this, MessagingActivity.class);
        startActivity(intent);
    }

    private void showHelp() {
        Toast.makeText(this, "مساعدة", Toast.LENGTH_SHORT).show();
        // Start help activity
        Intent intent = new Intent(this, HelpActivity.class);
        startActivity(intent);
    }

    private void openSettings() {
        Toast.makeText(this, "إعدادات", Toast.LENGTH_SHORT).show();
        // For now, just show a toast
        Toast.makeText(this, "وظيفة الإعدادات قيد التطوير", Toast.LENGTH_SHORT).show();
    }

    private void updateConnectionStatus() {
        runOnUiThread(() -> {
            if (isConnected && remoteIpAddress != null) {
                connectionStatusText.setText("متصل");
                // Update UI to show connected state
            } else {
                connectionStatusText.setText("غير متصل");
                // Update UI to show disconnected state
            }
        });
    }

    private String getLocalIpAddress() {
        try {
            WifiManager wifiManager = (WifiManager) getApplicationContext().getSystemService(WIFI_SERVICE);
            if (wifiManager != null) {
                WifiInfo wifiInfo = wifiManager.getConnectionInfo();
                int ipAddress = wifiInfo.getIpAddress();
                if (ipAddress != 0) {
                    return Formatter.formatIpAddress(ipAddress);
                }
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
        return "غير معروف";
    }

    // CallService.CallServiceCallback implementation
    @Override
    public void onIncomingCall(String fromIP) {
        runOnUiThread(() -> {
            // Start CallActivity for incoming call
            Intent callIntent = new Intent(this, CallActivity.class);
            callIntent.putExtra("mode", "incoming");
            callIntent.putExtra("target_ip", fromIP);
            callIntent.putExtra("target_port", CallService.SIGNALING_PORT);
            startActivity(callIntent);
        });
    }

    @Override
    public void onCallConnected() {
        runOnUiThread(() -> {
            Toast.makeText(this, "تم الاتصال", Toast.LENGTH_SHORT).show();
        });
    }

    @Override
    public void onCallEnded() {
        runOnUiThread(() -> {
            Toast.makeText(this, "تم إنهاء المكالمة", Toast.LENGTH_SHORT).show();
        });
    }

    @Override
    public void onCallError(String error) {
        runOnUiThread(() -> {
            Toast.makeText(this, "خطأ: " + error, Toast.LENGTH_LONG).show();
        });
    }

    @Override
    public void onTextMessageReceived(String fromIP, String message) {
        runOnUiThread(() -> {
            Toast.makeText(this, "رسالة من " + fromIP + ": " + message, Toast.LENGTH_LONG).show();
        });
    }

    // New callback methods
    @Override
    public void onConnectionEstablished(String fromIP) {
        runOnUiThread(() -> {
            remoteIpAddress = fromIP;
            isConnected = true;
            updateConnectionStatus();
            Toast.makeText(this, "تم الاتصال مع: " + fromIP, Toast.LENGTH_SHORT).show();
        });
    }

    @Override
    public void onMessageSendFailed(String error) {
        runOnUiThread(() -> {
            Toast.makeText(this, "فشل في إرسال الرسالة: " + error, Toast.LENGTH_LONG).show();
        });
    }

    @Override
    public void onConnectionStatusChanged(String status) {
        runOnUiThread(() -> {
            // Handle connection status changes
        });
    }
}