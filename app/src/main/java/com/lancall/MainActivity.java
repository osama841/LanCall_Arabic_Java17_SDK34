package com.lancall;

import android.Manifest;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.os.Build;
import android.os.Bundle;
import android.widget.Toast;

import androidx.activity.EdgeToEdge;
import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;

import com.google.android.material.button.MaterialButton;

public class MainActivity extends AppCompatActivity {

    private static final int REQ_PERMS = 1001;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        EdgeToEdge.enable(this);
        setContentView(R.layout.activity_main);

        MaterialButton btnShowQr = findViewById(R.id.btnShowQr);
        MaterialButton btnScanQr = findViewById(R.id.btnScanQr);
        MaterialButton btnHelp = findViewById(R.id.btnHelp);

        btnHelp.setOnClickListener(v -> startActivity(new Intent(this, HelpActivity.class)));

        btnShowQr.setOnClickListener(v -> {
            if (ensurePermissions()) {
                startActivity(new Intent(this, QrActivity.class).putExtra("mode", "show"));
            }
        });

        btnScanQr.setOnClickListener(v -> {
            if (ensurePermissions()) {
                startActivity(new Intent(this, QrActivity.class).putExtra("mode", "scan"));
            }
        });
        
        // Start CallService in background
        Intent serviceIntent = new Intent(this, CallService.class);
        startService(serviceIntent);
    }

    private boolean ensurePermissions() {
        // Camera for QR; mic for future calls; notifications for call alerts
        boolean needCamera = ContextCompat.checkSelfPermission(this, Manifest.permission.CAMERA) != PackageManager.PERMISSION_GRANTED;
        boolean needMic = ContextCompat.checkSelfPermission(this, Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED;
        boolean needNotif = false;
        if (Build.VERSION.SDK_INT >= 33) {
            needNotif = ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED;
        }
        if (needCamera || needMic || needNotif) {
            String[] req;
            if (Build.VERSION.SDK_INT >= 33) {
                req = new String[]{Manifest.permission.CAMERA, Manifest.permission.RECORD_AUDIO, Manifest.permission.POST_NOTIFICATIONS};
            } else {
                req = new String[]{Manifest.permission.CAMERA, Manifest.permission.RECORD_AUDIO};
            }
            ActivityCompat.requestPermissions(this, req, REQ_PERMS);
            return false;
        }
        return true;
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions, @NonNull int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        if (requestCode == REQ_PERMS) {
            boolean all = true;
            for (int g : grantResults) if (g != PackageManager.PERMISSION_GRANTED) { all = false; break; }
            Toast.makeText(this, all ? "تم منح الأذونات" : "بعض الأذونات مرفوضة", Toast.LENGTH_SHORT).show();
        }
    }
}
