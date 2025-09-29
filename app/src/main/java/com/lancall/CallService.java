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

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.InetAddress;
import java.net.ServerSocket;
import java.net.Socket;
import java.net.SocketTimeoutException;
import java.util.UUID;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * خدمة إدارة المكالمات - تعمل في الخلفية لمعالجة المكالمات الصوتية
 * هذه الخدمة هي القلب النابض للتطبيق - تدير جميع عمليات المكالمات
 * تعمل كخدمة في المقدمة (Foreground Service) لضمان استمرار العمل حتى عند إغلاق
 * التطبيق
 * تستقبل المكالمات الواردة، تبدأ المكالمات الصادرة، وتدير تبادل البيانات
 * الصوتية
 * Call Management Service - runs in background to handle voice calls
 */
public class CallService extends Service {

    private static final String TAG = "CallService"; // تسمية للتسجيل في اللوغ - يساعد في تتبع أخطاء هذه الخدمة
    private static final String CHANNEL_ID = "LanCallService"; // معرف قناة الإشعارات - يجمع إشعارات الخدمة في مجموعة
                                                               // واحدة
    private static final int NOTIFICATION_ID = 1001; // رقم فريد للإشعار - يمكن تحديثه أو إلغاؤه باستخدام هذا الرقم

    // إعدادات الصوت - Audio configuration
    private static final int SAMPLE_RATE = 16000; // معدل العينات: 16 ألف عينة في الثانية - جودة عالية للمكالمات الصوتية
    private static final int AUDIO_FORMAT = AudioFormat.ENCODING_PCM_16BIT; // تنسيق الصوت: PCM 16 بت - كل عينة صوتية
                                                                            // تحفظ في 16 بت
    private static final int CHANNEL_CONFIG_IN = AudioFormat.CHANNEL_IN_MONO; // إعداد التسجيل: قناة واحدة (أحادي) -
                                                                              // يوفر مساحة ويكفي للصوت البشري
    private static final int CHANNEL_CONFIG_OUT = AudioFormat.CHANNEL_OUT_MONO; // إعداد التشغيل: قناة واحدة (أحادي) -
                                                                                // مطابق للتسجيل
    private static final int BUFFER_SIZE = AudioRecord.getMinBufferSize(SAMPLE_RATE, CHANNEL_CONFIG_IN, AUDIO_FORMAT); // حجم
                                                                                                                       // الذاكرة
                                                                                                                       // المؤقتة:
                                                                                                                       // الحد
                                                                                                                       // الأدنى
                                                                                                                       // المطلوب
                                                                                                                       // لتسجيل
                                                                                                                       // الصوت
                                                                                                                       // بهذه
                                                                                                                       // المواصفات

    // إعدادات الشبكة - Network configuration
    public static final int SIGNALING_PORT = 10001; // منفذ إشارات التحكم: يستخدم بروتوكول TCP لإرسال أوامر المكالمة
                                                    // (بدء، قبول، رفض، إنهاء)
    public static final int AUDIO_PORT = 10002; // منفذ البيانات الصوتية: يستخدم بروتوكول UDP لنقل الصوت بسرعة عالية

    // حالة الخدمة - Service state
    private boolean isServiceRunning = false; // هل الخدمة تعمل؟ - يتحكم في حلقات الاستماع والمعالجة
    private boolean isInCall = false; // هل توجد مكالمة نشطة؟ - يحدد ما إذا كان هناك تبادل صوتي جاري
    private boolean isMuted = false; // هل الميكروفون مكتوم؟ - يتحكم في إرسال الصوت للطرف الآخر

    // مكونات الشبكة - Network components
    private ServerSocket signalingServer; // خادم TCP: يستمع للاتصالات الواردة على منفذ إشارات التحكم
    private DatagramSocket audioSocket; // مقبس UDP: يرسل ويستقبل حزم البيانات الصوتية
    private ExecutorService executorService; // مدير المهام المتوازية: ينفذ عدة مهام في نفس الوقت (استماع، إرسال،
                                             // استقبال)

    // مكونات الصوت - Audio components
    private AudioRecord audioRecord; // مسجل الصوت: يلتقط الصوت من الميكروفون ويحوله لبيانات رقمية
    private AudioTrack audioTrack; // مشغل الصوت: يحول البيانات الرقمية المستقبلة إلى صوت في السماعة

    // Reusable audio buffers for better memory management
    private byte[] audioSendBuffer;
    private byte[] audioReceiveBuffer;

    // معلومات المكالمة الحالية - Current call information
    private String remoteIP; // عنوان IP للجهاز الآخر في المكالمة - يحدد وجهة إرسال البيانات الصوتية
    private CallState currentCallState = CallState.IDLE; // حالة المكالمة الحالية - تبدأ بـ IDLE (خاملة) وتتغير حسب
                                                         // مراحل المكالمة

    // واجهة الاستدعاءات المرتدة - Callback interface
    // تسمح للأنشطة (Activities) بالاستجابة لأحداث المكالمة
    public interface CallServiceCallback {
        void onIncomingCall(String fromIP); // عند وصول مكالمة واردة - يمرر عنوان IP للمتصل

        void onCallConnected(); // عند نجاح الاتصال - يخبر الواجهة أن المكالمة بدأت

        void onCallEnded(); // عند انتهاء المكالمة - يخبر الواجهة أن المكالمة انتهت

        void onCallError(String error); // عند حدوث خطأ - يمرر رسالة الخطأ للواجهة

        void onTextMessageReceived(String fromIP, String message); // عند استقبال رسالة نصية - يمرر عنوان IP للمرسل
                                                                   // والرسالة

        void onConnectionEstablished(String fromIP); // عند تأكيد الاتصال - يمرر عنوان IP للجهاز المتصل

        void onMessageSendFailed(String error); // عند فشل إرسال رسالة - يمرر رسالة الخطأ

        void onConnectionStatusChanged(String status); // عند تغيير حالة الاتصال - يمرر حالة الاتصال الجديدة
    }

    private CallServiceCallback callback;

    // حالات المكالمة المختلفة - Different call states
    public enum CallState {
        IDLE, // خاملة - لا توجد مكالمة
        INCOMING, // واردة - يتم استقبال مكالمة
        OUTGOING, // صادرة - يتم إجراء مكالمة
        CONNECTED, // متصلة - المكالمة نشطة وجارية
        ENDED // منتهية - المكالمة انتهت
    }

    /**
     * فئة ربط الخدمة - تربط الأنشطة بالخدمة
     * تسمح للأنشطة بالوصول لدوال ومتغيرات الخدمة
     * Service binder class - connects activities to the service
     */
    public class CallServiceBinder extends Binder {
        /**
         * حصول على مرجع للخدمة - Get service reference
         * يمكن الأنشطة من استخدام دوال الخدمة مباشرة
         */
        public CallService getService() {
            return CallService.this; // إرجاع مرجع لهذه الخدمة
        }
    }

    private final IBinder binder = new CallServiceBinder(); // رابط الخدمة - يسمح للأنشطة بالاتصال بالخدمة

    /**
     * ربط الخدمة بالأنشطة - Bind service to activities
     * يستدعى عندما يريد نشاط الاتصال بالخدمة
     * يعيد مرجعاً للرابط ليتمكن النشاط من استخدام دوال الخدمة
     */
    @Nullable
    @Override
    public IBinder onBind(Intent intent) {
        return binder; // إرجاع رابط الخدمة للنشاط الطالب
    }

    /**
     * إنشاء الخدمة - Service creation
     * يستدعى مرة واحدة عند إنشاء الخدمة لأول مرة
     * يقوم بإعداد الموارد الأساسية وقناة الإشعارات
     */
    @Override
    public void onCreate() {
        super.onCreate();
        executorService = Executors.newCachedThreadPool(); // إنشاء مجموعة خيوط مرنة - تنشئ خيوط حسب الحاجة وتغلقها عند
                                                           // عدم الاستخدام
        createNotificationChannel(); // إنشاء قناة الإشعارات - ضروري لعرض إشعارات الخدمة في Android 8.0+
        Log.d(TAG, "CallService created"); // تسجيل إنشاء الخدمة في اللوغ للمتابعة

        // Initialize reusable audio buffers
        initializeAudioBuffers();
    }

    /**
     * بدء الخدمة - Service start command
     * يستدعى عندما يطلب التطبيق بدء الخدمة
     * يبدأ الخدمة في المقدمة ويبدأ الاستماع للمكالمات
     */
    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        if (!isServiceRunning) { // إذا لم تكن الخدمة تعمل بعد
            startForegroundService(); // بدء الخدمة في المقدمة - يظهر إشعار دائم ويمنع النظام من إيقاف الخدمة
            startListening(); // بدء الاستماع للمكالمات الواردة - فتح خادم TCP على منفذ الإشارات
        }
        return START_STICKY; // إعادة بدء الخدمة تلقائياً إذا أوقفها النظام لتوفير الذاكرة
    }

    /**
     * تدمير الخدمة - Service destruction
     * يستدعى عند إغلاق الخدمة أو عند إغلاق التطبيق
     * ينظف الموارد ويغلق الاتصالات المفتوحة
     */
    @Override
    public void onDestroy() {
        super.onDestroy();
        stopService(); // إيقاف جميع عمليات الخدمة وتنظيف الموارد
        if (executorService != null) { // إذا كان مدير المهام موجوداً
            executorService.shutdown(); // إغلاق مدير المهام وإنهاء جميع الخيوط العاملة
        }
        Log.d(TAG, "CallService destroyed"); // تسجيل تدمير الخدمة في اللوغ
    }

    /**
     * إنشاء قناة الإشعارات - Create notification channel
     * ضروري في Android 8.0+ لعرض إشعارات الخدمة
     * يحدد اسم ووصف وأهمية الإشعارات
     */
    private void createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) { // فقط في Android 8.0 وما فوق
            NotificationChannel channel = new NotificationChannel(
                    CHANNEL_ID, // معرف القناة - يجمع إشعارات الخدمة معاً
                    "خدمة المكالمات", // اسم القناة الظاهر للمستخدم
                    NotificationManager.IMPORTANCE_LOW); // أهمية منخفضة - لا تصدر صوتاً ولا تهتز الهاتف
            channel.setDescription("خدمة معالجة المكالمات المحلية"); // وصف القناة للمستخدم
            NotificationManager manager = getSystemService(NotificationManager.class); // الحصول على مدير الإشعارات
            if (manager != null) {
                manager.createNotificationChannel(channel); // تسجيل القناة في النظام
            }
        }
    }

    /**
     * بدء الخدمة في المقدمة - Start foreground service
     * يعرض إشعاراً دائماً ويمنع النظام من إيقاف الخدمة
     * ضروري لضمان استمرار استقبال المكالمات
     */
    private void startForegroundService() {
        Intent notificationIntent = new Intent(this, MainActivity.class); // نية فتح الشاشة الرئيسية عند الضغط على
                                                                          // الإشعار
        PendingIntent pendingIntent = PendingIntent.getActivity(
                this, 0, notificationIntent,
                Build.VERSION.SDK_INT >= Build.VERSION_CODES.M ? PendingIntent.FLAG_IMMUTABLE
                        : PendingIntent.FLAG_UPDATE_CURRENT); // إعداد النية المؤجلة حسب إصدار Android

        // بناء الإشعار باستخدام NotificationCompat للتوافق مع جميع إصدارات Android
        Notification notification = new NotificationCompat.Builder(this, CHANNEL_ID)
                .setContentTitle("خدمة مكالمات LanCall") // عنوار الإشعار
                .setContentText("جاهز لاستقبال المكالمات - المنفذ: " + SIGNALING_PORT) // نص الإشعار مع معلومات الحالة
                .setSmallIcon(R.mipmap.ic_launcher) // أيقونة صغيرة في شريط الإشعارات
                .setContentIntent(pendingIntent) // إجراء عند الضغط على الإشعار
                .setPriority(NotificationCompat.PRIORITY_LOW) // أولوية منخفضة - لا يزعج المستخدم
                .setOngoing(true) // إشعار مستمر - لا يمكن مسحه بالسحب
                .build(); // بناء الإشعار النهائي

        startForeground(NOTIFICATION_ID, notification); // بدء الخدمة في المقدمة مع عرض الإشعار
        isServiceRunning = true; // تعيين حالة الخدمة كعاملة
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
            // Set a timeout for reading
            clientSocket.setSoTimeout(5000); // 5 second timeout

            String fromIP = clientSocket.getInetAddress().getHostAddress();

            // Read the incoming message with better handling
            InputStream inputStream = clientSocket.getInputStream();
            ByteArrayOutputStream buffer = new ByteArrayOutputStream();
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
            String jsonMessage = buffer.toString("UTF-8");
            Log.d(TAG, "Received complete message from " + fromIP + ": " + jsonMessage);

            // Parse the message
            SignalingProtocol.Message message = SignalingProtocol.jsonToMessage(jsonMessage);

            if (message != null) {
                Log.d(TAG, "Parsed message type: " + message.type);
                if (SignalingProtocol.MESSAGE_TYPE_CALL_REQUEST.equals(message.type)) {
                    // Handle call request
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
                } else if (SignalingProtocol.MESSAGE_TYPE_TEXT_MESSAGE.equals(message.type)) {
                    // Handle text message
                    SignalingProtocol.TextMessageData textData = (SignalingProtocol.TextMessageData) message.data;
                    Log.d(TAG, "Received text message from " + fromIP + ": " + textData.message);

                    // Notify the callback about the received text message
                    if (callback != null) {
                        new Handler(Looper.getMainLooper()).post(() -> {
                            callback.onTextMessageReceived(fromIP, textData.message);
                        });
                    }
                } else if (SignalingProtocol.MESSAGE_TYPE_CONNECTION_REQUEST.equals(message.type)) {
                    // Handle connection request
                    Log.d(TAG, "Received connection request from: " + fromIP);

                    // Send acknowledgment
                    SignalingProtocol.Message ack = SignalingProtocol.createConnectionAck(getLocalIPv4());
                    String ackJson = SignalingProtocol.messageToJson(ack);

                    if (ackJson != null) {
                        clientSocket.getOutputStream().write(ackJson.getBytes("UTF-8"));
                        clientSocket.getOutputStream().flush();
                        Log.d(TAG, "Sent connection acknowledgment to: " + fromIP);

                        // Notify the callback about the established connection
                        if (callback != null) {
                            new Handler(Looper.getMainLooper()).post(() -> {
                                callback.onConnectionEstablished(fromIP);
                            });
                        }
                    }
                } else {
                    Log.d(TAG, "Received unknown message type: " + message.type);
                }
            } else {
                Log.e(TAG, "Failed to parse incoming message: " + jsonMessage);
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
                // Create call request message
                String localIP = getLocalIPv4();
                SignalingProtocol.Message callRequest = SignalingProtocol.createCallRequest(
                        localIP,
                        "Caller",
                        AUDIO_PORT);
                String jsonMessage = SignalingProtocol.messageToJson(callRequest);

                if (jsonMessage != null) {
                    // محاولة الاتصال بخادم الإشارات في الجهاز المستهدف
                    Socket signalingSocket = new Socket();
                    signalingSocket.connect(new java.net.InetSocketAddress(targetIP, targetPort), 10000); // 10 seconds
                                                                                                          // timeout

                    Log.d(TAG, "Connected to target signaling server");

                    // إرسال إشارة بالاتصال
                    Log.d(TAG, "Sending call signal from: " + localIP);
                    signalingSocket.getOutputStream().write(jsonMessage.getBytes("UTF-8"));
                    signalingSocket.getOutputStream().flush();

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
                } else {
                    Log.e(TAG, "Failed to serialize call request");
                    currentCallState = CallState.ENDED;
                    if (callback != null) {
                        new Handler(Looper.getMainLooper())
                                .post(() -> callback.onCallError("فشل في إعداد المكالمة"));
                    }
                }

            } catch (SocketTimeoutException e) {
                Log.e(TAG, "Connection timeout: " + e.getMessage(), e);
                currentCallState = CallState.ENDED;
                if (callback != null) {
                    new Handler(Looper.getMainLooper())
                            .post(() -> callback.onCallError("انتهت مهلة الاتصال"));
                }
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

    /**
     * إرسال رسالة نصية إلى الجهاز الآخر
     * Send text message to the other device
     */
    public void sendTextMessage(String message) {
        // Allow sending messages even when not in a call, as long as remote IP is set
        if (remoteIP == null || remoteIP.isEmpty()) {
            Log.w(TAG, "Remote IP not set");
            return;
        }

        Log.d(TAG, "Attempting to send text message: " + message + " to " + remoteIP);

        executorService.execute(() -> {
            // Try to send message with retry mechanism
            boolean sent = sendMessageWithRetry(message, 3);

            if (!sent) {
                Log.e(TAG, "Failed to send text message after retries");
                if (callback != null) {
                    new Handler(Looper.getMainLooper()).post(() -> {
                        callback.onMessageSendFailed("فشل في إرسال الرسالة بعد عدة محاولات");
                    });
                }
            } else {
                Log.d(TAG, "Text message sent successfully: " + message);
            }
        });
    }

    /**
     * إرسال رسالة مع إعادة المحاولة
     * Send message with retry mechanism
     */
    private boolean sendMessageWithRetry(String message, int maxRetries) {
        for (int i = 0; i < maxRetries; i++) {
            Socket signalingSocket = null;
            try {
                // Create text message with unique ID
                String localIP = getLocalIPv4();
                String messageId = UUID.randomUUID().toString();
                SignalingProtocol.Message textMessage = SignalingProtocol.createTextMessage(localIP, message);
                String jsonMessage = SignalingProtocol.messageToJson(textMessage);

                if (jsonMessage != null) {
                    Log.d(TAG, "Serialized text message: " + jsonMessage);
                    // Send message via TCP socket
                    signalingSocket = new Socket();
                    signalingSocket.connect(new java.net.InetSocketAddress(remoteIP, SIGNALING_PORT), 5000); // 5
                                                                                                             // seconds
                                                                                                             // timeout
                    Log.d(TAG, "Connected to signaling server at " + remoteIP + ":" + SIGNALING_PORT);

                    // Set timeout for the socket
                    signalingSocket.setSoTimeout(5000);

                    signalingSocket.getOutputStream().write(jsonMessage.getBytes("UTF-8"));
                    signalingSocket.getOutputStream().flush();
                    Log.d(TAG, "Sent text message to " + remoteIP);

                    // Ensure data is sent before closing
                    signalingSocket.shutdownOutput();

                    signalingSocket.close();
                    return true; // Message sent successfully
                } else {
                    Log.e(TAG, "Failed to serialize text message");
                }
            } catch (Exception e) {
                Log.e(TAG, "Error sending text message (attempt " + (i + 1) + "): " + e.getMessage(), e);
                if (i < maxRetries - 1) {
                    try {
                        Thread.sleep(1000); // Wait 1 second before retry
                    } catch (InterruptedException ie) {
                        Thread.currentThread().interrupt();
                        break;
                    }
                }
            } finally {
                // Always close the socket
                if (signalingSocket != null) {
                    try {
                        signalingSocket.close();
                        Log.d(TAG, "Closed signaling socket");
                    } catch (IOException e) {
                        Log.e(TAG, "Error closing signaling socket", e);
                    }
                }
            }
        }
        return false; // Failed to send after all retries
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

    /**
     * Set remote IP for messaging purposes without starting a call
     * @param remoteIP The IP address of the remote device
     */
    public void setRemoteIPForMessaging(String remoteIP) {
        this.remoteIP = remoteIP;
        // We don't set isInCall to true here as this is just for messaging
        // But we need to ensure the service can send messages
        Log.d(TAG, "Remote IP set for messaging: " + remoteIP);
    }

    public void setCallback(CallServiceCallback callback) {
        this.callback = callback;
    }

    private void initializeAudioBuffers() {
        audioSendBuffer = new byte[BUFFER_SIZE];
        audioReceiveBuffer = new byte[BUFFER_SIZE];
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
        long sequenceNumber = 0;
        Log.d(TAG, "Audio sending loop started, sending to: " + remoteIP + ":" + AUDIO_PORT);

        while (isInCall && audioRecord != null) {
            try {
                int bytesRead = audioRecord.read(audioSendBuffer, 0, audioSendBuffer.length);

                if (bytesRead > 0 && !isMuted) {
                    // Create audio packet and send to remote
                    DatagramPacket packet = new DatagramPacket(
                            audioSendBuffer, bytesRead,
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
        long receivedPackets = 0;
        Log.d(TAG, "Audio receiving loop started, listening on port: " + AUDIO_PORT);

        while (isInCall && audioTrack != null) {
            try {
                DatagramPacket packet = new DatagramPacket(audioReceiveBuffer, audioReceiveBuffer.length);

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