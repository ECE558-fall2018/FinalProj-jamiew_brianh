package edu.pdx.ece558f18.bhenson.finalproj_app;

import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.media.RingtoneManager;
import android.net.Uri;
import android.os.Build;
import android.support.v4.app.NotificationCompat;
import android.util.Log;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;
import com.google.firebase.database.DatabaseReference;
import com.google.firebase.database.FirebaseDatabase;
import com.google.firebase.messaging.FirebaseMessagingService;
import com.google.firebase.messaging.RemoteMessage;

public class MyFirebaseMessagingService extends FirebaseMessagingService {
    public static final String TAG = "SECURITY_MyMsgService";

    public MyFirebaseMessagingService() {
        super();
        Log.d(TAG,"constructed messaging service");
    }

    @Override
    public void onMessageReceived(RemoteMessage remoteMessage) {
        // CRITICAL NOTE: this function only triggers if the app is open & active when the notificaiton is received!
        super.onMessageReceived(remoteMessage);
        Log.d(TAG, "received something From: " + remoteMessage.getFrom());
        // Check if message contains a data payload.
        if (remoteMessage.getData().size() > 0) {
            Log.d(TAG, "Message data payload: " + remoteMessage.getData());
        }

        // Check if message contains a notification payload.
        if (remoteMessage.getNotification() != null) {
            Log.d(TAG, "Message Notification Body: " + remoteMessage.getNotification().getBody());
        }

        // Also if you intend on generating your own notifications as a result of a received FCM
        // message, here is where that should be initiated. See sendNotification method below.
        // TODO: generate a toast if the message is received while the app is open?
        // NOTE: to affect the app UI while the app is running, just send data thru the Database
    }


    @Override
    public void onDeletedMessages() {
        super.onDeletedMessages();
        Log.d(TAG,"deleted something");
        // TODO? i'm supposed to implement this but i really dont think it's needed
    }

    /**
     * Called if InstanceID token is updated. This may occur if the security of
     * the previous token had been compromised. Note that this is called when the InstanceID token
     * is initially generated so this is where you would retrieve the token.
     */
    @Override
    public void onNewToken(String token) {
        super.onNewToken(token);
        Log.d(TAG, "new token: " + token);

        sendRegistrationToServer(token);
    }

    /**
     * Sends the token to be held in the database, i suppose
     * @param token used to identify this app instance
     */
    private void sendRegistrationToServer(String token) {
        // note: i suspect this will usually fail but better safe than sorry
        // I can always get the database reference but I doubt it's possible to be logged in when this function fires
        DatabaseReference mDatabase = FirebaseDatabase.getInstance().getReference();
        FirebaseUser user = FirebaseAuth.getInstance().getCurrentUser();
        if(user != null) {
            DatabaseReference temp1 = mDatabase.child(Keys.DB_TOPFOLDER);
            DatabaseReference temp2 = temp1.child(user.getUid());
            DatabaseReference temp3 = temp2.child(Keys.DB_APPTOKEN);
            temp3.setValue(token);
            Log.d(TAG, "successfully sent new token to the database");
        }
    }


    /**
     * Manually create and show a simple notification containing the received FCM message.
     *
     * @param messageBody FCM message body received.
     */
    private void sendNotification(String messageBody) {
        // TODO: review this and understand it
        Intent intent = new Intent(this, LoginActivity.class);
        intent.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP);
        PendingIntent pendingIntent = PendingIntent.getActivity(this, 0 /* Request code */, intent, PendingIntent.FLAG_ONE_SHOT);

        String channelId = getString(R.string.default_notification_channel_id);
        Uri defaultSoundUri = RingtoneManager.getDefaultUri(RingtoneManager.TYPE_NOTIFICATION);
        NotificationCompat.Builder notificationBuilder =
                new NotificationCompat.Builder(this, channelId)
                        .setSmallIcon(R.mipmap.firebase_icon)
                        .setContentTitle(getString(R.string.fcm_message))
                        .setContentText(messageBody)
                        .setAutoCancel(true)
                        .setSound(defaultSoundUri)
                        .setContentIntent(pendingIntent);

        NotificationManager notificationManager = (NotificationManager) getSystemService(Context.NOTIFICATION_SERVICE);

        // Since android Oreo notification channel is needed.
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            NotificationChannel channel = new NotificationChannel(channelId,
                    "Channel human readable title",
                    NotificationManager.IMPORTANCE_DEFAULT);
            notificationManager.createNotificationChannel(channel);
        }

        notificationManager.notify(0 /* ID of notification */, notificationBuilder.build());
    }




    @Override
    public void onDestroy() {
        super.onDestroy();
        Log.d(TAG, "destroying messaging service");
    }
    @Override
    public void onCreate() {
        super.onDestroy();
        Log.d(TAG, "creating messaging service");
    }
}
