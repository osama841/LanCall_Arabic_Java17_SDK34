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
import android.widget.ImageView;
import android.widget.TextView;
import android.widget.Toast;

import androidx.activity.EdgeToEdge;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;

import com.google.zxing.BarcodeFormat;
import com.google.zxing.MultiFormatWriter;
import com.google.zxing.common.BitMatrix;
import com.journeyapps.barcodescanner.CaptureActivity;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;

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
                        finish();
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

            setContentView(R.layout.activity_qr);
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
            img.setImageBitmap(renderQr(payload, 800, 800));

            // إظهار رسالة أن الجهاز جاهز لاستقبال المكالمات
            Toast.makeText(this, "جاهز لاستقبال المكالمات \ud83d\udcde", Toast.LENGTH_LONG).show();
        }
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
            finish(); // إغلاق QR activity
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

                Log.d("QrActivity", "Starting call to: " + targetIp + ":" + targetPort);

                // إظهار رسالة التوصيل أولاً
                Toast.makeText(this, "تم التوصيل بينITIZE", Toast.LENGTH_LONG).show();

                // انتظار قصير لإظهار الرسالة ثم بدء المكالمة
                new Handler(Looper.getMainLooper()).postDelayed(() -> {
                    // Start call activity
                    Intent callIntent = new Intent(this, CallActivity.class);
                    callIntent.putExtra("mode", "outgoing");
                    callIntent.putExtra("target_ip", targetIp);
                    callIntent.putExtra("target_port", targetPort);
                    startActivity(callIntent);
                    finish(); // أغلق QR activity
                }, 2000); // انتظار ثانيتين
            } else {
                Toast.makeText(this, getString(R.string.error_invalid_qr), Toast.LENGTH_SHORT).show();
                finish();
            }
        } else {
            Toast.makeText(this, "لم يتم قراءة QR بشكل صحيح - جرب مرة أخرى", Toast.LENGTH_SHORT).show();
            finish();
        }
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
            Bitmap bmp = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888);
            bmp.setPixels(pixels, 0, w, 0, 0, w, h);
            return bmp;
        } catch (Exception e) {
            return null;
        }
    }

    private String getLocalIPv4() {
        try {
            // الطريقة الأولى: استخدام WifiManager
            WifiManager wifiManager = (WifiManager) getApplicationContext().getSystemService(WIFI_SERVICE);
            if (wifiManager != null && wifiManager.isWifiEnabled()) {
                WifiInfo wifiInfo = wifiManager.getConnectionInfo();
                if (wifiInfo != null) {
                    int ip = wifiInfo.getIpAddress();
                    if (ip != 0) {
                        String ipAddress = Formatter.formatIpAddress(ip);
                        // تحقق من أن IP ليس localhost
                        if (!ipAddress.equals("0.0.0.0") && !ipAddress.startsWith("127.")) {
                            return ipAddress;
                        }
                    }
                }
            }

            // الطريقة الثانية: استخدام NetworkInterface (للحالات الأخرى)
            java.util.Enumeration<java.net.NetworkInterface> interfaces = java.net.NetworkInterface
                    .getNetworkInterfaces();
            while (interfaces.hasMoreElements()) {
                java.net.NetworkInterface networkInterface = interfaces.nextElement();
                if (networkInterface.isLoopback() || !networkInterface.isUp()) {
                    continue;
                }

                java.util.Enumeration<java.net.InetAddress> addresses = networkInterface.getInetAddresses();
                while (addresses.hasMoreElements()) {
                    java.net.InetAddress address = addresses.nextElement();
                    if (!address.isLoopbackAddress() && address instanceof java.net.Inet4Address) {
                        String ip = address.getHostAddress();
                        // تحقق من أن IP في شبكة محلية
                        if (ip != null
                                && (ip.startsWith("192.168.") || ip.startsWith("10.") || ip.startsWith("172."))) {
                            return ip;
                        }
                    }
                }
            }

            return null;
        } catch (Exception e) {
            e.printStackTrace();
            return null;
        }
    }
}
