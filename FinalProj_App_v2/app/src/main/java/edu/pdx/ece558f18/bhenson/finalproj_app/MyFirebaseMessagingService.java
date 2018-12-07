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
    public static final String TAG = "SEC_MyMsgService";

    public MyFirebaseMessagingService() {
        super();
        //Log.d(TAG,"constructed messaging service");
    }

    @Override
    public void onMessageReceived(RemoteMessage remoteMessage) {
        // CRITICAL NOTE: this function only triggers if the app is open & active when the notificaiton is received!
        super.onMessageReceived(remoteMessage);
        Log.d(TAG, "received something From: " + remoteMessage.getFrom());
        int z = 1;
        // Check if message contains a data payload.
        if (remoteMessage.getData().size() > 0) {
            Log.d(TAG, "Message data payload: " + remoteMessage.getData().toString());
            String ias = remoteMessage.getData().get(Keys.KEY_GOTOPAGE);
            if (ias != null) {
                try {
                    // should be 1 or 2
                    z = Integer.parseInt(ias);
                } catch(NumberFormatException nfe) {
                    Log.d(TAG, "couldn't parse gotopage, assuming its a disconnect alarm");
                }
            }
        }

        // Check if message contains a notification payload.
        RemoteMessage.Notification r = remoteMessage.getNotification();
        if (r != null) {
            Log.d(TAG, "Message Notification content: " + r.getTitle() + "," + r.getBody() + "," +
                    r.getTag() + "," + r.getIcon() + "," + r.getSound());
        }

        // Also if you intend on generating your own notifications as a result of a received FCM
        // message, here is where that should be initiated. See sendNotification method below.

        // generate a toast if the message is received while the app is open
        sendNotification(r.getTitle(), r.getBody(), z);
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
     */
    private void sendNotification( String title, String body, int whichpage ) {

        String messageTitle = title;
        String messageBody = body;
        int messageID;
        Intent intent = new Intent(this, LoginActivity.class);
        // these flags clear the existing activities and launch a fresh one... still no back stack! pefect!
        intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
        if(whichpage == 2) {
            intent.putExtra(Keys.KEY_GOTOPAGE, 2);
            //messageTitle = Keys.FCM_ACTIVE_TITLE;
            //messageBody = Keys.FCM_ACTIVE_MESSAGE;
            messageID = Keys.ID_NOTIFY_ACTIVE;
        } else {
            //messageTitle = Keys.FCM_DISCONNECT_TITLE;
            //messageBody = Keys.FCM_DISCONNECT_MESSAGE;
            messageID = Keys.ID_NOTIFY_DISCONNECT;
        }
        PendingIntent pendingIntent = PendingIntent.getActivity(this, 55 /* Request code, what is this? */, intent, PendingIntent.FLAG_ONE_SHOT);

        String channelId = getString(R.string.default_notification_channel_id);
        Uri defaultSoundUri = RingtoneManager.getDefaultUri(RingtoneManager.TYPE_NOTIFICATION);
        NotificationCompat.Builder notificationBuilder =
                new NotificationCompat.Builder(this, channelId)
                        .setSmallIcon(R.mipmap.spyglass_trans_v1)
                        .setContentTitle(messageTitle)
                        .setContentText(messageBody)
                        .setAutoCancel(true)
                        .setSound(defaultSoundUri)
                        .setPriority(NotificationManager.IMPORTANCE_HIGH) // this may work or may crash, dunno
                        .setContentIntent(pendingIntent);

        NotificationManager nm = (NotificationManager) getSystemService(Context.NOTIFICATION_SERVICE);

        try {
            // Since android Oreo notification channel is needed.
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                NotificationChannel channel = new NotificationChannel(channelId,
                        getString(R.string.default_notification_channel_id_human_readable),
                        NotificationManager.IMPORTANCE_HIGH);
                nm.createNotificationChannel(channel);
            }

            nm.notify(messageID, notificationBuilder.build());
        } catch (NullPointerException npe) {
            Log.d(TAG, "somehow got a null NotificationMangager? probably a rotation thing", npe);
        }
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
