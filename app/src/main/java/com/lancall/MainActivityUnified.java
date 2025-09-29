package com.lancall;

import android.Manifest;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.ServiceConnection;
import android.content.pm.PackageManager;
import android.graphics.Bitmap;
import android.net.wifi.WifiInfo;
import android.net.wifi.WifiManager;
import android.os.Build;
import android.os.Bundle;
import android.os.IBinder;
import android.text.format.Formatter;
import android.util.Log;
import android.view.View;
import android.widget.ImageView;
import android.widget.TextView;
import android.widget.Toast;

import androidx.activity.EdgeToEdge;
import androidx.annotation.NonNull;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.google.android.material.button.MaterialButton;
import com.google.android.material.textfield.TextInputEditText;
import com.google.zxing.BarcodeFormat;
import com.google.zxing.MultiFormatWriter;
import com.google.zxing.common.BitMatrix;
import com.journeyapps.barcodescanner.CaptureActivity;

import java.net.NetworkInterface;
import java.util.Collections;
import java.util.List;

/**
 * النشاط الرئيسي الموحد - Unified Main Activity
 * يجمع جميع الوظائف في شاشة واحدة:
 * 1. المراسلة المباشرة
 * 2. المكالمات الصوتية
 * 3. مسح ومشاركة رموز QR
 * 4. البحث عن الأجهزة في الشبكة
 */
public class MainActivityUnified extends AppCompatActivity implements CallService.CallServiceCallback {

    private static final String TAG = "MainActivityUnified";
    private static final int REQ_PERMS = 1001;

    // UI Components
    private MaterialButton btnCall, btnQrScanner, btnSearch, btnShowQr;
    private MaterialButton btnClearChat, btnShareQr;
    private MaterialButton sendButton;
    private TextInputEditText messageInput;
    private RecyclerView messagesRecyclerView;
    private TextView localIpText, remoteDeviceText, connectionStatusText;
    private ImageView connectionStatusIndicator;

    // Adapters and Services
    private MessageAdapter messageAdapter;
    private CallService callService;
    private boolean isServiceBound = false;

    // Connection info
    private String localIpAddress;
    private String remoteIpAddress;
    private boolean isConnected = false;

    // Service connection
    private ServiceConnection serviceConnection = new ServiceConnection() {
        @Override
        public void onServiceConnected(ComponentName name, IBinder service) {
            CallService.CallServiceBinder binder = (CallService.CallServiceBinder) service;
            callService = binder.getService();
            callService.setCallback(MainActivityUnified.this);
            isServiceBound = true;

            Log.d(TAG, "CallService connected");

            // Update UI with connection status
            updateConnectionStatus();
        }

        @Override
        public void onServiceDisconnected(ComponentName name) {
            callService = null;
            isServiceBound = false;
            isConnected = false;
            Log.d(TAG, "CallService disconnected");

            // Update UI with connection status
            updateConnectionStatus();
        }
    };

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        EdgeToEdge.enable(this);
        setContentView(R.layout.activity_main_unified_improved);

        initializeViews();
        setupClickListeners();
        initializeRecyclerView();

        // Get local IP address
        localIpAddress = getLocalIpAddress();
        localIpText.setText("IP المحلي: " + localIpAddress);

        // Start and bind to CallService
        Intent serviceIntent = new Intent(this, CallService.class);
        startService(serviceIntent);
        bindService(serviceIntent, serviceConnection, Context.BIND_AUTO_CREATE);

        // Request permissions
        ensurePermissions();
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
        // Top toolbar buttons
        btnCall = findViewById(R.id.btnCall);
        btnQrScanner = findViewById(R.id.btnQrScanner);
        btnSearch = findViewById(R.id.btnSearch);
        btnShowQr = findViewById(R.id.btnShowQr);

        // Floating action buttons
        btnClearChat = findViewById(R.id.btnClearChat);
        btnShareQr = findViewById(R.id.btnShareQr);

        // Message input
        messageInput = findViewById(R.id.messageInput);
        sendButton = findViewById(R.id.sendButton);

        // RecyclerView
        messagesRecyclerView = findViewById(R.id.messagesRecyclerView);

        // Connection info
        localIpText = findViewById(R.id.localIpText);
        remoteDeviceText = findViewById(R.id.remoteDeviceText);
        connectionStatusText = findViewById(R.id.connectionStatusText);
        connectionStatusIndicator = findViewById(R.id.connectionStatusIndicator);
    }

    private void setupClickListeners() {
        // Top toolbar
        btnCall.setOnClickListener(v -> startCall());
        btnQrScanner.setOnClickListener(v -> scanQrCode());
        btnSearch.setOnClickListener(v -> searchDevices());
        btnShowQr.setOnClickListener(v -> showQrCode());

        // Floating action buttons
        btnClearChat.setOnClickListener(v -> clearChat());
        btnShareQr.setOnClickListener(v -> shareQrCode());

        // Message input
        sendButton.setOnClickListener(v -> sendMessage());
    }

    private void initializeRecyclerView() {
        messageAdapter = new MessageAdapter(localIpAddress);
        messagesRecyclerView.setLayoutManager(new LinearLayoutManager(this));
        messagesRecyclerView.setAdapter(messageAdapter);
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

        // Start call activity
        Intent callIntent = new Intent(this, CallActivity.class);
        callIntent.putExtra("mode", "outgoing");
        callIntent.putExtra("target_ip", remoteIpAddress);
        callIntent.putExtra("target_port", CallService.SIGNALING_PORT);
        startActivity(callIntent);
    }

    private void scanQrCode() {
        if (!ensurePermissions()) {
            return;
        }

        Intent intent = new Intent(this, CaptureActivity.class);
        intent.putExtra("PROMPT_MESSAGE", "وجّه الكاميرا نحو QR");
        startActivityForResult(intent, 1001);
    }

    private void searchDevices() {
        // Show a simple dialog with device discovery options
        new AlertDialog.Builder(this)
                .setTitle("البحث عن الأجهزة")
                .setMessage("وظيفة البحث عن الأجهزة في الشبكة المحلية")
                .setPositiveButton("موافق", (dialog, which) -> dialog.dismiss())
                .show();
    }

    private void showSettings() {
        // Show a simple dialog with settings options
        new AlertDialog.Builder(this)
                .setTitle("الإعدادات")
                .setMessage("وظيفة الإعدادات")
                .setPositiveButton("موافق", (dialog, which) -> dialog.dismiss())
                .show();
    }

    private void clearChat() {
        messageAdapter.clearMessages();
        Toast.makeText(this, "تم مسح المحادثة", Toast.LENGTH_SHORT).show();
    }

    private void shareQrCode() {
        if (localIpAddress == null || localIpAddress.isEmpty()) {
            Toast.makeText(this, "تعذر الحصول على عنوان IP", Toast.LENGTH_SHORT).show();
            return;
        }

        // Generate QR code
        String payload = "lancall://" + localIpAddress + ":" + CallService.SIGNALING_PORT;
        Bitmap qrBitmap = generateQrCode(payload, 400, 400);

        if (qrBitmap != null) {
            // Show QR code in dialog
            AlertDialog.Builder builder = new AlertDialog.Builder(this);
            builder.setTitle("مشاركة رمز QR");

            ImageView qrImageView = new ImageView(this);
            qrImageView.setImageBitmap(qrBitmap);
            builder.setView(qrImageView);

            builder.setPositiveButton("إغلاق", (dialog, which) -> dialog.dismiss());
            builder.show();
        } else {
            Toast.makeText(this, "فشل في إنشاء رمز QR", Toast.LENGTH_SHORT).show();
        }
    }

    private void showQrCode() {
        if (localIpAddress == null || localIpAddress.isEmpty()) {
            Toast.makeText(this, "تعذر الحصول على عنوان IP", Toast.LENGTH_SHORT).show();
            return;
        }

        // Generate QR code
        String payload = "lancall://" + localIpAddress + ":" + CallService.SIGNALING_PORT;
        Bitmap qrBitmap = generateQrCode(payload, 400, 400);

        if (qrBitmap != null) {
            // Show QR code in dialog
            AlertDialog.Builder builder = new AlertDialog.Builder(this);
            builder.setTitle("رمز QR للجهاز");

            ImageView qrImageView = new ImageView(this);
            qrImageView.setImageBitmap(qrBitmap);
            builder.setView(qrImageView);

            builder.setPositiveButton("إغلاق", (dialog, which) -> dialog.dismiss());
            builder.show();
        } else {
            Toast.makeText(this, "فشل في إنشاء رمز QR", Toast.LENGTH_SHORT).show();
        }
    }

    private void sendMessage() {
        String messageText = messageInput.getText().toString().trim();
        if (messageText.isEmpty()) {
            Toast.makeText(this, "الرجاء إدخال رسالة", Toast.LENGTH_SHORT).show();
            return;
        }

        if (!isConnected || remoteIpAddress == null) {
            Toast.makeText(this, "لا يوجد اتصال بجهاز آخر", Toast.LENGTH_SHORT).show();
            return;
        }

        if (callService == null) {
            Toast.makeText(this, "الخدمة غير متصلة", Toast.LENGTH_SHORT).show();
            return;
        }

        // Create message with SENDING status
        Message message = new Message(localIpAddress, messageText, System.currentTimeMillis());
        message.setStatus(Message.MessageStatus.SENDING);

        // Add message to local UI immediately
        messageAdapter.addMessage(message);
        messagesRecyclerView.scrollToPosition(messageAdapter.getItemCount() - 1);

        // Send message through CallService in a background thread
        new Thread(() -> {
            try {
                callService.sendTextMessage(messageText);

                // Update message status to SENT on success
                runOnUiThread(() -> {
                    message.setStatus(Message.MessageStatus.SENT);
                });
            } catch (Exception e) {
                Log.e(TAG, "Failed to send message", e);
                // Update message status to FAILED on error
                runOnUiThread(() -> {
                    message.setStatus(Message.MessageStatus.FAILED);
                });
            }
        }).start();

        // Clear input
        messageInput.setText("");
    }

    private void updateConnectionStatus() {
        runOnUiThread(() -> {
            if (isConnected && remoteIpAddress != null) {
                connectionStatusText.setText("متصل");
                connectionStatusIndicator.setBackgroundColor(0xFF00FF00); // Green
                remoteDeviceText.setText("الجهاز الآخر: " + remoteIpAddress);
            } else {
                connectionStatusText.setText("غير متصل");
                connectionStatusIndicator.setBackgroundColor(0xFFFF0000); // Red
                remoteDeviceText.setText("الجهاز الآخر: --");
            }
        });
    }

    private String getLocalIpAddress() {
        try {
            // Try WiFi first
            WifiManager wifiManager = (WifiManager) getApplicationContext().getSystemService(WIFI_SERVICE);
            if (wifiManager != null) {
                WifiInfo wifiInfo = wifiManager.getConnectionInfo();
                int ipAddress = wifiInfo.getIpAddress();
                if (ipAddress != 0) {
                    return Formatter.formatIpAddress(ipAddress);
                }
            }

            // Fallback to network interfaces
            List<NetworkInterface> interfaces = Collections.list(NetworkInterface.getNetworkInterfaces());
            for (NetworkInterface intf : interfaces) {
                List<java.net.InetAddress> addrs = Collections.list(intf.getInetAddresses());
                for (java.net.InetAddress addr : addrs) {
                    if (!addr.isLoopbackAddress() && addr instanceof java.net.Inet4Address) {
                        return addr.getHostAddress();
                    }
                }
            }
        } catch (Exception e) {
            Log.e(TAG, "Error getting local IP address", e);
        }
        return "غير معروف";
    }

    private Bitmap generateQrCode(String text, int width, int height) {
        try {
            BitMatrix matrix = new MultiFormatWriter().encode(text, BarcodeFormat.QR_CODE, width, height);
            int w = matrix.getWidth(), h = matrix.getHeight();
            int[] pixels = new int[w * h];
            for (int y = 0; y < h; y++) {
                int offset = y * w;
                for (int x = 0; x < w; x++) {
                    pixels[offset + x] = matrix.get(x, y) ? 0xFF000000 : 0xFFFFFFFF;
                }
            }
            Bitmap bmp = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888);
            bmp.setPixels(pixels, 0, w, 0, 0, w, h);
            return bmp;
        } catch (Exception e) {
            Log.e(TAG, "Error generating QR code", e);
            return null;
        }
    }

    private boolean ensurePermissions() {
        boolean needCamera = ContextCompat.checkSelfPermission(this,
                Manifest.permission.CAMERA) != PackageManager.PERMISSION_GRANTED;

        boolean needMic = ContextCompat.checkSelfPermission(this,
                Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED;

        boolean needNotif = false;
        if (Build.VERSION.SDK_INT >= 33) {
            needNotif = ContextCompat.checkSelfPermission(this,
                    Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED;
        }

        if (needCamera || needMic || needNotif) {
            String[] req;
            if (Build.VERSION.SDK_INT >= 33) {
                req = new String[] { Manifest.permission.CAMERA,
                        Manifest.permission.RECORD_AUDIO,
                        Manifest.permission.POST_NOTIFICATIONS };
            } else {
                req = new String[] { Manifest.permission.CAMERA,
                        Manifest.permission.RECORD_AUDIO };
            }
            ActivityCompat.requestPermissions(this, req, REQ_PERMS);
            return false;
        }
        return true;
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);

        if (requestCode == 1001 && resultCode == RESULT_OK && data != null) {
            String qrContent = data.getStringExtra("SCAN_RESULT");
            if (qrContent != null && !qrContent.isEmpty()) {
                Log.d(TAG, "Scanned QR content: " + qrContent);

                // Validate QR code format
                if (qrContent.startsWith("lancall://")) {
                    String[] parts = qrContent.replace("lancall://", "").split(":");
                    if (parts.length == 2) {
                        // Validate IP address format
                        if (isValidIpAddress(parts[0])) {
                            remoteIpAddress = parts[0];

                            Toast.makeText(this, "تم الاتصال بالجهاز: " + remoteIpAddress, Toast.LENGTH_SHORT).show();

                            // Update connection status
                            isConnected = true;
                            updateConnectionStatus();
                            
                            // Set remote IP in CallService for messaging
                            if (callService != null) {
                                callService.setRemoteIPForMessaging(remoteIpAddress);
                            }
                        } else {
                            Toast.makeText(this, "عنوان IP غير صالح", Toast.LENGTH_SHORT).show();
                        }
                    } else {
                        Toast.makeText(this, "رمز QR غير صحيح", Toast.LENGTH_SHORT).show();
                    }
                } else {
                    Toast.makeText(this, "رمز QR غير مدعوم", Toast.LENGTH_SHORT).show();
                }
            } else {
                Toast.makeText(this, "لم يتم قراءة رمز QR بشكل صحيح", Toast.LENGTH_SHORT).show();
            }
        }
    }

    private boolean isValidIpAddress(String ip) {
        // Simple IP validation
        String[] parts = ip.split("\\.");
        if (parts.length != 4)
            return false;
        try {
            for (String part : parts) {
                int num = Integer.parseInt(part);
                if (num < 0 || num > 255)
                    return false;
            }
            return true;
        } catch (NumberFormatException e) {
            return false;
        }
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions,
            @NonNull int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);

        if (requestCode == REQ_PERMS) {
            boolean allGranted = true;
            for (int result : grantResults) {
                if (result != PackageManager.PERMISSION_GRANTED) {
                    allGranted = false;
                    break;
                }
            }

            if (!allGranted) {
                Toast.makeText(this, "بعض الأذونات مرفوضة", Toast.LENGTH_SHORT).show();
            }
        }
    }

    // CallService.CallServiceCallback implementation
    @Override
    public void onIncomingCall(String fromIP) {
        runOnUiThread(() -> {
            Log.d(TAG, "Incoming call from: " + fromIP);

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
            Log.d(TAG, "Call connected");
            Toast.makeText(this, "تم الاتصال", Toast.LENGTH_SHORT).show();
        });
    }

    @Override
    public void onCallEnded() {
        runOnUiThread(() -> {
            Log.d(TAG, "Call ended");
            Toast.makeText(this, "تم إنهاء المكالمة", Toast.LENGTH_SHORT).show();
        });
    }

    @Override
    public void onCallError(String error) {
        runOnUiThread(() -> {
            Log.e(TAG, "Call error: " + error);
            Toast.makeText(this, "خطأ: " + error, Toast.LENGTH_LONG).show();
        });
    }

    @Override
    public void onTextMessageReceived(String fromIP, String message) {
        runOnUiThread(() -> {
            Log.d(TAG, "Received text message from " + fromIP + ": " + message);

            Message msg = new Message(fromIP, message, System.currentTimeMillis());
            msg.setStatus(Message.MessageStatus.DELIVERED);
            messageAdapter.addMessage(msg);
            messagesRecyclerView.scrollToPosition(messageAdapter.getItemCount() - 1);
        });
    }

    // New callback methods
    @Override
    public void onConnectionEstablished(String fromIP) {
        runOnUiThread(() -> {
            Log.d(TAG, "Connection established with: " + fromIP);
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
            Log.d(TAG, "Connection status changed: " + status);
        });
    }
}