package com.lancall;

// استيراد المكتبات المطلوبة - Import required libraries
import android.Manifest; // أذونات الأندرويد - مثل الكاميرا والميكروفون
import android.content.Intent; // النوايا - لفتح الأنشطة والخدمات
import android.content.pm.PackageManager; // مدير الحزم - للتحقق من الأذونات المُمنوحة
import android.os.Build; // معلومات البناء - للتحقق من إصدار الأندرويد
import android.os.Bundle; // حزمة البيانات - لحفظ واستعادة حالة النشاط
import android.widget.Toast; // رسائل منبثقة قصيرة للمستخدم

import androidx.activity.EdgeToEdge; // عرض ملء الشاشة
import androidx.annotation.NonNull; // تعليق توضيحي يشير أن المتغير لا يمكن أن يكون null
import androidx.appcompat.app.AppCompatActivity; // فئة النشاط الأساسية مع دعم للميزات الحديثة
import androidx.core.app.ActivityCompat; // مساعد طلب الأذونات
import androidx.core.content.ContextCompat; // مساعد التحقق من الأذونات

import com.google.android.material.button.MaterialButton; // أزرار بتصميم Material Design

/**
 * النشاط الرئيسي - Main Activity
 * هذا هو النشاط الأول الذي يراه المستخدم عند فتح التطبيق
 * يحتوي على الواجهة الرئيسية مع ثلاثة أزرار أساسية
 * مسؤول عن طلب الأذونات اللازمة وبدء الخدمات الأساسية
 */
public class MainActivity extends AppCompatActivity {

    // رقم طلب الأذونات - Request code for permissions
    // يُستخدم لتمييز طلب الأذونات عن طلبات أخرى في النشاط
    private static final int REQ_PERMS = 1001;

    /**
     * إنشاء النشاط - Activity creation
     * يُستدعى عند فتح النشاط لأول مرة أو عند إعادة إنشائه
     * يقوم بتهيئة الواجهة وربط الأزرار بالوظائف وبدء الخدمات
     */
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState); // استدعاء الدالة الأساسية
        EdgeToEdge.enable(this); // تفعيل عرض ملء الشاشة - يستغل كامل مساحة الشاشة
        setContentView(R.layout.activity_main); // ربط النشاط بملف التخطيط XML

        // ربط الأزرار بمتغيرات للتحكم بها برمجياً - Link buttons to variables for programmatic control
        MaterialButton btnShowQr = findViewById(R.id.btnShowQr); // زر عرض رمز QR للجهاز الحالي
        MaterialButton btnScanQr = findViewById(R.id.btnScanQr); // زر مسح رمز QR لجهاز آخر
        MaterialButton btnHelp = findViewById(R.id.btnHelp); // زر المساعدة والتعليمات

        // ربط زر المساعدة بوظيفته - Link help button to its function
        btnHelp.setOnClickListener(v -> 
            startActivity(new Intent(this, HelpActivity.class))); // فتح شاشة المساعدة عند الضغط

        // ربط زر عرض QR بوظيفته - Link show QR button to its function
        btnShowQr.setOnClickListener(v -> {
            if (ensurePermissions()) { // التأكد من وجود الأذونات المطلوبة
                // فتح نشاط QR في وضع العرض - معطي "mode" = "show"
                startActivity(new Intent(this, QrActivity.class).putExtra("mode", "show"));
            }
        });

        // ربط زر مسح QR بوظيفته - Link scan QR button to its function
        btnScanQr.setOnClickListener(v -> {
            if (ensurePermissions()) { // التأكد من وجود الأذونات المطلوبة
                // فتح نشاط QR في وضع المسح - معطي "mode" = "scan"
                startActivity(new Intent(this, QrActivity.class).putExtra("mode", "scan"));
            }
        });
        
        // بدء خدمة المكالمات في الخلفية - Start call service in background
        // هذا يضمن أن الجهاز جاهز لاستقبال المكالمات حتى لو أغلق المستخدم التطبيق
        Intent serviceIntent = new Intent(this, CallService.class);
        startService(serviceIntent); // بدء الخدمة
    }

    /**
     * ضمان وجود الأذونات المطلوبة - Ensure required permissions
     * يتحقق من وجود جميع الأذونات اللازمة لعمل التطبيق
     * إذا لم تكن موجودة، يطلبها من المستخدم
     * 
     * @return true إذا كانت جميع الأذونات متوفرة، false إذا تم طلب أذونات جديدة
     */
    private boolean ensurePermissions() {
        // التحقق من إذن الكاميرا - مطلوب لمسح رموز QR
        boolean needCamera = ContextCompat.checkSelfPermission(this, Manifest.permission.CAMERA) 
                != PackageManager.PERMISSION_GRANTED;
        
        // التحقق من إذن الميكروفون - مطلوب لتسجيل الصوت أثناء المكالمات
        boolean needMic = ContextCompat.checkSelfPermission(this, Manifest.permission.RECORD_AUDIO) 
                != PackageManager.PERMISSION_GRANTED;
        
        // التحقق من إذن الإشعارات - مطلوب في Android 13+ لعرض إشعارات المكالمات الواردة
        boolean needNotif = false;
        if (Build.VERSION.SDK_INT >= 33) { // Android 13 والأحدث
            needNotif = ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS) 
                    != PackageManager.PERMISSION_GRANTED;
        }
        
        // إذا كان هناك أذونات مفقودة، اطلبها من المستخدم
        if (needCamera || needMic || needNotif) {
            String[] req; // مصفوفة الأذونات المطلوبة
            if (Build.VERSION.SDK_INT >= 33) {
                // في Android 13+، أضف إذن الإشعارات
                req = new String[]{Manifest.permission.CAMERA, 
                                 Manifest.permission.RECORD_AUDIO, 
                                 Manifest.permission.POST_NOTIFICATIONS};
            } else {
                // في الإصدارات الأقدم، الكاميرا والميكروفون فقط
                req = new String[]{Manifest.permission.CAMERA, 
                                 Manifest.permission.RECORD_AUDIO};
            }
            // طلب الأذونات من المستخدم
            ActivityCompat.requestPermissions(this, req, REQ_PERMS);
            return false; // الأذونات غير مكتملة حتى الآن
        }
        return true; // جميع الأذونات متوفرة
    }

    /**
     * معالج نتيجة طلب الأذونات - Permission request result handler
     * يُستدعى تلقائياً عندما يرد المستخدم على طلب الأذونات
     * يتحقق من النتيجة ويعرض رسالة للمستخدم
     * 
     * @param requestCode رمز الطلب - يحدد أي طلب أذونات تم الرد عليه
     * @param permissions مصفوفة الأذونات التي تم طلبها
     * @param grantResults مصفوفة النتائج - مُمنوح أم مرفوض لكل إذن
     */
    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions, @NonNull int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        
        if (requestCode == REQ_PERMS) { // التأكد أن هذا هو طلب الأذونات الخاص بتطبيقنا
            boolean all = true; // متغير لتتبع ما إذا كانت جميع الأذونات مُمنوحة
            
            // فحص كل نتيجة في المصفوفة
            for (int g : grantResults) {
                if (g != PackageManager.PERMISSION_GRANTED) { // إذا كان أي إذن مرفوض
                    all = false; // ليس جميع الأذونات مُمنوحة
                    break; // إيقاف الفحص
                }
            }
            
            // عرض رسالة للمستخدم حسب النتيجة
            Toast.makeText(this, 
                    all ? "تم منح الأذونات" : "بعض الأذونات مرفوضة", 
                    Toast.LENGTH_SHORT).show();
        }
    }
}
