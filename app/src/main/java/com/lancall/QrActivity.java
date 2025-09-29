package com.lancall;

// استيراد المكتبات المطلوبة - Import required libraries
import android.content.ComponentName; // اسم المكون - لربط الخدمات
import android.content.Context; // سياق التطبيق - للوصول لموارد النظام
import android.content.Intent; // النوايا - لفتح الأنشطة وتمرير البيانات
import android.content.ServiceConnection; // واجهة ربط الخدمات - للتفاعل مع CallService
import android.graphics.Bitmap; // صورة رقمية - لعرض رمز QR
import android.net.wifi.WifiInfo; // معلومات الواي فاي - للحصول على عنوان IP
import android.net.wifi.WifiManager; // مدير الواي فاي - لإدارة اتصال الواي فاي
import android.os.Bundle; // حزمة البيانات - لحفظ حالة النشاط
import android.os.Handler; // معالج الرسائل - تنفيذ مهام مؤجلة
import android.os.IBinder; // رابط الخدمة - للتفاعل مع خدمة المكالمات
import android.os.Looper; // حلقة الرسائل - لالخيط الرئيسي
import android.text.format.Formatter;
import android.util.Log;
import android.view.View;
import android.widget.ImageView;
import android.widget.TextView;
import android.widget.Toast;

import androidx.activity.EdgeToEdge;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;

import com.google.android.material.button.MaterialButton;
import com.google.zxing.BarcodeFormat;
import com.google.zxing.MultiFormatWriter;
import com.google.zxing.common.BitMatrix;
import com.journeyapps.barcodescanner.CaptureActivity;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.regex.Pattern;

/**
 * نشاط إدارة رموز QR - QR Code Management Activity
 * هذا النشاط يدير عمليتين مهمتين:
 * 1. عرض رمز QR يحتوي على عنوان IP للجهاز الحالي
 * 2. مسح رموز QR للأجهزة الأخرى للاتصال بها
 * يعمل كمستقبل للمكالمات الواردة عند عرض رمز QR
 * ويبدأ مكالمات صادرة عند مسح رمز QR
 */
public class QrActivity extends AppCompatActivity implements CallService.CallServiceCallback {

    // مُشغِل فحص QR باستخدام Intent مباشر - QR scanner launcher using direct Intent
    // يُستخدم لفتح شاشة مسح QR واستقبال النتيجة
    private ActivityResultLauncher<Intent> qrScannerLauncher;

    // للجهاز المستقبل - الاتصال بـ CallService - For receiving device - connection to CallService
    // يُستخدم للتفاعل مع خدمة المكالمات واستقبال إشعارات المكالمات الواردة
    private CallService callService; // مرجع لخدمة المكالمات
    private boolean isServiceBound = false; // هل تم ربط الخدمة بنجاح؟
    
    // Executor service for background tasks
    private ExecutorService executorService = Executors.newCachedThreadPool();
    
    // UI Components
    private MaterialButton btnScanQr, btnShare, btnCall, btnMessage;
    private TextView tvAddr, connectionStatusText, remoteDeviceText;
    private ImageView connectionStatusIndicator;
    private View connectionStatusCard, scanActionButtonsLayout, connectedActionButtonsLayout;
    
    // Connection information
    private String remoteIpAddress;
    private boolean isConnected = false;

    /**
     * رابط الخدمة - Service connection handler
     * يدير الاتصال وانقطاع الاتصال مع خدمة المكالمات
     * يضمن الحصول على مرجع صحيح للخدمة وتسجيل الcallback
     */
    private ServiceConnection serviceConnection = new ServiceConnection() {
        /**
         * يُستدعى عند نجاح ربط الخدمة - Called when service is successfully connected
         */
        @Override
        public void onServiceConnected(ComponentName name, IBinder service) {
            CallService.CallServiceBinder binder = (CallService.CallServiceBinder) service; // تحويل الرابط للنوع الصحيح
            callService = binder.getService(); // الحصول على مرجع الخدمة
            callService.setCallback(QrActivity.this); // تسجيل هذا النشاط كمستقبل لإشعارات المكالمة
            isServiceBound = true; // تعيين حالة الربط كمُكتمل
            Log.d("QrActivity", "CallService connected"); // تسجيل نجاح الربط
        }

        /**
         * يُستدعى عند انقطاع الاتصال مع الخدمة - Called when service connection is lost
         */
        @Override
        public void onServiceDisconnected(ComponentName name) {
            callService = null; // إزالة مرجع الخدمة
            isServiceBound = false; // تعيين حالة الربط كمنقطع
            Log.d("QrActivity", "CallService disconnected"); // تسجيل انقطاع الاتصال
        }
    };

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        EdgeToEdge.enable(this);

        // تهيئة QR scanner launcher
        qrScannerLauncher = registerForActivityResult(
                new ActivityResultContracts.StartActivityForResult(),
                result -> {
                    if (result.getResultCode() == RESULT_OK && result.getData() != null) {
                        handleScanResult(result.getData());
                    } else {
                        Toast.makeText(this, "تم الإلغاء", Toast.LENGTH_SHORT).show();
                    }
                });

        String mode = getIntent().getStringExtra("mode");
        if ("scan".equals(mode)) {
            scan();
        } else {
            // وضع عرض QR - ربط بـ CallService لاستقبال المكالمات
            Intent serviceIntent = new Intent(this, CallService.class);
            startService(serviceIntent);
            bindService(serviceIntent, serviceConnection, Context.BIND_AUTO_CREATE);

            setContentView(R.layout.activity_qr_beautiful);
            initializeViews();
            setupClickListeners();
            
            TextView tvAddr = findViewById(R.id.tvAddr);
            ImageView img = findViewById(R.id.imgQr);
            String ipv4 = getLocalIPv4();
            if (ipv4 == null) {
                tvAddr.setText(getString(R.string.qr_ipv4_missing));
                Toast.makeText(this, getString(R.string.qr_ipv4_missing), Toast.LENGTH_LONG).show();
                return;
            }
            String payload = "lancall://" + ipv4 + ":10001";
            tvAddr.setText(payload);
            Bitmap qrBitmap = renderQr(payload, 800, 800);
            if (qrBitmap != null) {
                img.setImageBitmap(qrBitmap);
            } else {
                Toast.makeText(this, "فشل في إنشاء رمز QR", Toast.LENGTH_LONG).show();
                Log.e("QrActivity", "Failed to generate QR code for payload: " + payload);
            }

            // إظهار رسالة أن الجهاز جاهز لاستقبال المكالمات
            Toast.makeText(this, "جاهز لاستقبال المكالمات \ud83d\udcde", Toast.LENGTH_LONG).show();
        }
    }

    private void initializeViews() {
        // Main views
        tvAddr = findViewById(R.id.tvAddr);
        connectionStatusText = findViewById(R.id.connectionStatusText);
        remoteDeviceText = findViewById(R.id.remoteDeviceText);
        connectionStatusIndicator = findViewById(R.id.connectionStatusIndicator);
        connectionStatusCard = findViewById(R.id.connectionStatusCard);
        scanActionButtonsLayout = findViewById(R.id.scanActionButtonsLayout);
        connectedActionButtonsLayout = findViewById(R.id.connectedActionButtonsLayout);
        
        // Action buttons
        btnScanQr = findViewById(R.id.btnScanQr);
        btnShare = findViewById(R.id.btnShare);
        btnCall = findViewById(R.id.btnCall);
        btnMessage = findViewById(R.id.btnMessage);
    }

    private void setupClickListeners() {
        // Scan mode buttons
        btnScanQr.setOnClickListener(v -> scan());
        btnShare.setOnClickListener(v -> shareQrCode());
        
        // Connected mode buttons
        btnCall.setOnClickListener(v -> startCall());
        btnMessage.setOnClickListener(v -> startMessaging());
    }

    private void shareQrCode() {
        // For now, just show a toast
        Toast.makeText(this, "وظيفة مشاركة رمز QR قيد التطوير", Toast.LENGTH_SHORT).show();
    }
    
    private void startCall() {
        if (!isConnected || remoteIpAddress == null) {
            Toast.makeText(this, "لا يوجد اتصال بجهاز آخر", Toast.LENGTH_SHORT).show();
            return;
        }
        
        Toast.makeText(this, "بدء مكالمة مع: " + remoteIpAddress, Toast.LENGTH_SHORT).show();
        // Start call activity
        Intent callIntent = new Intent(this, CallActivity.class);
        callIntent.putExtra("mode", "outgoing");
        callIntent.putExtra("target_ip", remoteIpAddress);
        callIntent.putExtra("target_port", CallService.SIGNALING_PORT);
        startActivity(callIntent);
    }
    
    private void startMessaging() {
        if (!isConnected || remoteIpAddress == null) {
            Toast.makeText(this, "لا يوجد اتصال بجهاز آخر", Toast.LENGTH_SHORT).show();
            return;
        }
        
        Toast.makeText(this, "فتح المراسلة مع: " + remoteIpAddress, Toast.LENGTH_SHORT).show();
        // Start messaging activity
        Intent messageIntent = new Intent(this, MessagingActivity.class);
        messageIntent.putExtra("target_ip", remoteIpAddress);
        startActivity(messageIntent);
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        if (isServiceBound) {
            callService.setCallback(null);
            unbindService(serviceConnection);
            isServiceBound = false;
        }
        if (executorService != null) {
            executorService.shutdown();
        }
    }

    // CallService.CallServiceCallback implementation
    @Override
    public void onIncomingCall(String fromIP) {
        runOnUiThread(() -> {
            Log.d("QrActivity", "Incoming call from: " + fromIP);
            Toast.makeText(this, "تم التوصيل! مكالمة واردة من: " + fromIP, Toast.LENGTH_LONG).show();

            // بدء CallActivity للمكالمة الواردة
            Intent callIntent = new Intent(this, CallActivity.class);
            callIntent.putExtra("mode", "incoming");
            callIntent.putExtra("target_ip", fromIP);
            callIntent.putExtra("target_port", 10001);
            startActivity(callIntent);
        });
    }

    @Override
    public void onCallConnected() {
        // لا نحتاج هذه في QrActivity
    }

    @Override
    public void onCallEnded() {
        // لا نحتاج هذه في QrActivity
    }

    @Override
    public void onCallError(String error) {
        runOnUiThread(() -> {
            Toast.makeText(this, "خطأ في المكالمة: " + error, Toast.LENGTH_LONG).show();
        });
    }

    @Override
    public void onTextMessageReceived(String fromIP, String message) {
        runOnUiThread(() -> {
            Log.d("QrActivity", "Received text message: " + message);
            // For now, we'll just show a toast with the received message
            // In a more complete implementation, we would display the message in a chat view
            String toastMessage = getString(R.string.message_received, fromIP, message);
            Toast.makeText(this, toastMessage, Toast.LENGTH_LONG).show();
        });
    }
    
    // New callback methods for connection handling
    @Override
    public void onConnectionEstablished(String fromIP) {
        runOnUiThread(() -> {
            Log.d("QrActivity", "Connection established with: " + fromIP);
            remoteIpAddress = fromIP;
            isConnected = true;
            
            // Update UI to show connection
            updateConnectionStatus();
            
            Toast.makeText(this, "تم الاتصال مع: " + fromIP, Toast.LENGTH_LONG).show();
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
            Log.d("QrActivity", "Connection status changed: " + status);
        });
    }
    
    private void updateConnectionStatus() {
        if (isConnected && remoteIpAddress != null) {
            connectionStatusText.setText("متصل");
            remoteDeviceText.setText("الجهاز الآخر: " + remoteIpAddress);
            connectionStatusIndicator.setBackgroundColor(0xFF00FF00); // Green
            connectionStatusCard.setVisibility(View.VISIBLE);
            scanActionButtonsLayout.setVisibility(View.GONE);
            connectedActionButtonsLayout.setVisibility(View.VISIBLE);
        } else {
            connectionStatusCard.setVisibility(View.GONE);
            scanActionButtonsLayout.setVisibility(View.VISIBLE);
            connectedActionButtonsLayout.setVisibility(View.GONE);
        }
    }

    private void scan() {
        Intent intent = new Intent(this, CaptureActivity.class);
        intent.putExtra("PROMPT_MESSAGE", "وجّه الكاميرا نحو QR");
        qrScannerLauncher.launch(intent);
    }

    // معالجة نتائج QR scan باستخدام Intent مباشر
    private void handleScanResult(Intent data) {
        // جرب مفاتيح مختلفة للحصول على نتيجة المسح
        String qrContent = data.getStringExtra("SCAN_RESULT");
        if (qrContent == null) {
            qrContent = data.getStringExtra("SCAN_RESULT_FORMAT");
        }
        if (qrContent == null) {
            // جرب الحصول على البيانات من زاوية أخرى
            android.os.Bundle extras = data.getExtras();
            if (extras != null) {
                qrContent = extras.getString("SCAN_RESULT");
                if (qrContent == null) {
                    // طباعة جميع المفاتيح المتاحة للتشخيص
                    Log.d("QrActivity", "Available keys: " + extras.keySet().toString());
                    for (String key : extras.keySet()) {
                        Object value = extras.get(key);
                        Log.d("QrActivity", "Key: " + key + ", Value: " + value);
                        if (value instanceof String && ((String) value).startsWith("lancall://")) {
                            qrContent = (String) value;
                            break;
                        }
                    }
                }
            }
        }

        Log.d("QrActivity", "Scanned QR content: " + qrContent);

        if (qrContent != null && !qrContent.isEmpty()) {
            Toast.makeText(this, "تم مسح رمز QR بنجاح ✅", Toast.LENGTH_SHORT).show();

            // Parse lancall://ipv4:10001 format
            if (isValidLanCallUrl(qrContent)) {
                String[] parts = qrContent.replace("lancall://", "").split(":");
                String targetIp = parts[0];
                int targetPort = Integer.parseInt(parts[1]);

                Log.d("QrActivity", "Starting connection to: " + targetIp + ":" + targetPort);

                // إرسال إشارة اتصال أولاً
                sendConnectionRequest(targetIp, targetPort);
            } else {
                Toast.makeText(this, getString(R.string.error_invalid_qr), Toast.LENGTH_SHORT).show();
            }
        } else {
            Toast.makeText(this, "لم يتم قراءة QR بشكل صحيح - جرب مرة أخرى", Toast.LENGTH_SHORT).show();
        }
    }

    // إرسال طلب اتصال أولاً
    private void sendConnectionRequest(String targetIp, int targetPort) {
        executorService.execute(() -> {
            try {
                // Show connecting dialog
                runOnUiThread(() -> {
                    Toast.makeText(this, "جاري الاتصال...", Toast.LENGTH_SHORT).show();
                });
                
                // Create connection request message
                String localIP = getLocalIPv4();
                SignalingProtocol.Message request = SignalingProtocol.createConnectionRequest(localIP);
                String jsonMessage = SignalingProtocol.messageToJson(request);
                
                if (jsonMessage != null) {
                    // Send connection request via TCP socket
                    java.net.Socket signalingSocket = new java.net.Socket(targetIp, targetPort);
                    Log.d("QrActivity", "Connected to target signaling server");
                    
                    // Set timeout for the socket
                    signalingSocket.setSoTimeout(10000); // 10 seconds timeout
                    
                    signalingSocket.getOutputStream().write(jsonMessage.getBytes("UTF-8"));
                    signalingSocket.getOutputStream().flush();
                    Log.d("QrActivity", "Sent connection request to " + targetIp);
                    
                    // Wait for acknowledgment
                    java.io.InputStream inputStream = signalingSocket.getInputStream();
                    java.io.ByteArrayOutputStream buffer = new java.io.ByteArrayOutputStream();
                    byte[] data = new byte[1024];
                    int nRead;
                    
                    // Read all available data
                    while ((nRead = inputStream.read(data, 0, data.length)) != -1) {
                        buffer.write(data, 0, nRead);
                        // Check if we have a complete JSON message
                        String currentData = buffer.toString("UTF-8");
                        if (currentData.trim().endsWith("}")) {
                            break; // We have a complete JSON message
                        }
                    }
                    
                    buffer.flush();
                    String jsonResponse = buffer.toString("UTF-8");
                    Log.d("QrActivity", "Received response: " + jsonResponse);
                    
                    // Parse the response
                    SignalingProtocol.Message response = SignalingProtocol.jsonToMessage(jsonResponse);
                    
                    if (response != null && SignalingProtocol.MESSAGE_TYPE_CONNECTION_ACK.equals(response.type)) {
                        // Connection acknowledged, update UI but don't navigate away
                        runOnUiThread(() -> {
                            Toast.makeText(this, "تم التوصيل بين الأجهزة", Toast.LENGTH_LONG).show();
                            remoteIpAddress = targetIp;
                            isConnected = true;
                            updateConnectionStatus();
                        });
                    } else {
                        runOnUiThread(() -> {
                            Toast.makeText(this, "فشل في الاتصال: لم يتم تأكيد الاتصال", Toast.LENGTH_LONG).show();
                        });
                    }
                    
                    signalingSocket.close();
                } else {
                    runOnUiThread(() -> {
                        Toast.makeText(this, "فشل في إعداد الاتصال", Toast.LENGTH_LONG).show();
                    });
                }
            } catch (Exception e) {
                Log.e("QrActivity", "Error sending connection request: " + e.getMessage(), e);
                runOnUiThread(() -> {
                    Toast.makeText(this, "فشل في الاتصال: " + e.getMessage(), Toast.LENGTH_LONG).show();
                });
            }
        });
    }

    private boolean isValidLanCallUrl(String url) {
        if (!url.startsWith("lancall://")) {
            return false;
        }

        String address = url.replace("lancall://", "");
        String[] parts = address.split(":");

        if (parts.length != 2) {
            return false;
        }

        // Validate IP address format
        String ip = parts[0];
        Pattern ipPattern = Pattern.compile(
                "^((25[0-5]|2[0-4][0-9]|[01]?[0-9][0-9]?)\\.){3}" +
                        "(25[0-5]|2[0-4][0-9]|[01]?[0-9][0-9]?)$");

        if (!ipPattern.matcher(ip).matches()) {
            return false;
        }

        // Validate port number
        try {
            int port = Integer.parseInt(parts[1]);
            return port > 0 && port <= 65535;
        } catch (NumberFormatException e) {
            return false;
        }
    }

    private static Bitmap renderQr(String text, int width, int height) {
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
            Bitmap bitmap = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888);
            bitmap.setPixels(pixels, 0, w, 0, 0, w, h);
            return bitmap;
        } catch (Exception e) {
            return null;
        }
    }

    private String getLocalIPv4() {
        try {
            WifiManager wifiManager = (WifiManager) getApplicationContext().getSystemService(Context.WIFI_SERVICE);
            if (wifiManager != null) {
                WifiInfo wifiInfo = wifiManager.getConnectionInfo();
                int ipAddress = wifiInfo.getIpAddress();
                return Formatter.formatIpAddress(ipAddress);
            }
        } catch (Exception e) {
            Log.e("QrActivity", "Error getting local IP: " + e.getMessage(), e);
        }
        return null;
    }
}