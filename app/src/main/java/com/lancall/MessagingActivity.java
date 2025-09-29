package com.lancall;

import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.ServiceConnection;
import android.os.Bundle;
import android.os.IBinder;
import android.widget.Toast;

import androidx.activity.EdgeToEdge;
import androidx.appcompat.app.AppCompatActivity;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.google.android.material.button.MaterialButton;
import com.google.android.material.textfield.TextInputEditText;

import java.net.NetworkInterface;
import java.net.SocketException;
import java.util.Collections;
import java.util.List;

/**
 * Activity for direct messaging between two devices on the local network
 */
public class MessagingActivity extends AppCompatActivity {
    private RecyclerView messagesRecyclerView;
    private TextInputEditText messageInput;
    private MaterialButton sendButton;

    private MessageAdapter messageAdapter;
    private CallService callService;
    private boolean serviceBound = false;

    private String localIpAddress;

    // Service connection to bind to CallService
    private ServiceConnection serviceConnection = new ServiceConnection() {
        @Override
        public void onServiceConnected(ComponentName name, IBinder service) {
            CallService.CallServiceBinder binder = (CallService.CallServiceBinder) service;
            callService = binder.getService();
            serviceBound = true;

            // Set callback for receiving messages
            callService.setCallback(new CallService.CallServiceCallback() {
                @Override
                public void onIncomingCall(String fromIP) {
                    // Not used in messaging activity
                }

                @Override
                public void onCallConnected() {
                    // Not used in messaging activity
                }

                @Override
                public void onCallEnded() {
                    // Not used in messaging activity
                }

                @Override
                public void onCallError(String error) {
                    runOnUiThread(
                            () -> Toast.makeText(MessagingActivity.this, "خطأ: " + error, Toast.LENGTH_SHORT).show());
                }

                @Override
                public void onTextMessageReceived(String fromIP, String message) {
                    runOnUiThread(() -> {
                        Message msg = new Message(fromIP, message, System.currentTimeMillis());
                        messageAdapter.addMessage(msg);
                        messagesRecyclerView.scrollToPosition(messageAdapter.getItemCount() - 1);
                    });
                }
            });
        }

        @Override
        public void onServiceDisconnected(ComponentName name) {
            serviceBound = false;
        }
    };

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        EdgeToEdge.enable(this);
        setContentView(R.layout.activity_messaging);

        initViews();
        setupRecyclerView();
        bindToService();
        localIpAddress = getLocalIpAddress();
    }

    private void initViews() {
        messagesRecyclerView = findViewById(R.id.messagesRecyclerView);
        messageInput = findViewById(R.id.messageInput);
        sendButton = findViewById(R.id.sendButton);

        sendButton.setOnClickListener(v -> sendMessage());
    }

    private void setupRecyclerView() {
        localIpAddress = getLocalIpAddress();
        messageAdapter = new MessageAdapter(localIpAddress);
        messagesRecyclerView.setLayoutManager(new LinearLayoutManager(this));
        messagesRecyclerView.setAdapter(messageAdapter);
    }

    private void bindToService() {
        Intent intent = new Intent(this, CallService.class);
        bindService(intent, serviceConnection, Context.BIND_AUTO_CREATE);
    }

    private void sendMessage() {
        String messageText = messageInput.getText().toString().trim();
        if (messageText.isEmpty()) {
            Toast.makeText(this, "الرجاء إدخال رسالة", Toast.LENGTH_SHORT).show();
            return;
        }

        if (!serviceBound || callService == null) {
            Toast.makeText(this, "الخدمة غير متصلة", Toast.LENGTH_SHORT).show();
            return;
        }

        // Get remote IP from service
        String remoteIP = callService.getRemoteIP();
        if (remoteIP == null || remoteIP.isEmpty()) {
            Toast.makeText(this, "لا يوجد اتصال بجهاز آخر", Toast.LENGTH_SHORT).show();
            return;
        }

        // Send message through CallService
        callService.sendTextMessage(messageText);

        // Add message to local UI
        Message message = new Message(localIpAddress, messageText, System.currentTimeMillis());
        messageAdapter.addMessage(message);
        messagesRecyclerView.scrollToPosition(messageAdapter.getItemCount() - 1);

        // Clear input
        messageInput.setText("");
    }

    private String getLocalIpAddress() {
        try {
            List<NetworkInterface> interfaces = Collections.list(NetworkInterface.getNetworkInterfaces());
            for (NetworkInterface intf : interfaces) {
                List<java.net.InetAddress> addrs = Collections.list(intf.getInetAddresses());
                for (java.net.InetAddress addr : addrs) {
                    if (!addr.isLoopbackAddress() && addr instanceof java.net.Inet4Address) {
                        return addr.getHostAddress();
                    }
                }
            }
        } catch (SocketException e) {
            e.printStackTrace();
        }
        return "غير معروف";
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        if (serviceBound) {
            unbindService(serviceConnection);
            serviceBound = false;
        }
    }
}