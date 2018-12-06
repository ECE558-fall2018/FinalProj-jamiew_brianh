package edu.jkw7.pdx.web.finalproject_pi;

import android.Manifest;
import android.app.Activity;
import android.content.Context;
import android.media.AudioManager;
import android.media.MediaPlayer;
import android.support.v4.content.ContextCompat;
import android.content.pm.PackageManager;
import android.content.res.AssetManager;
import android.media.Image;
import android.media.ImageReader;
import android.os.Bundle;
import android.os.Handler;
import android.os.HandlerThread;
import android.support.annotation.NonNull;
import android.util.Log;

// *****
import com.google.android.gms.tasks.OnFailureListener;
import com.google.android.gms.tasks.OnSuccessListener;
import com.google.android.things.pio.PeripheralManager;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.database.*;
import com.google.firebase.storage.FirebaseStorage;
import com.google.firebase.storage.StorageReference;
import com.google.firebase.storage.UploadTask;
// *******************

import com.google.android.gms.tasks.OnFailureListener;
import com.google.android.gms.tasks.OnSuccessListener;
import com.google.firebase.auth.FirebaseAuth;
//import com.google.android.things.contrib.driver.button.Button;
//import com.google.android.things.contrib.driver.button.ButtonInputDriver;
import com.google.firebase.database.DataSnapshot;
import com.google.firebase.database.DatabaseError;
import com.google.firebase.database.DatabaseException;
import com.google.firebase.database.DatabaseReference;
import com.google.firebase.database.FirebaseDatabase;
//import com.google.firebase.database.ServerValue;
import com.google.firebase.database.ValueEventListener;
import com.google.firebase.storage.FirebaseStorage;
import com.google.firebase.storage.StorageReference;
import com.google.firebase.storage.UploadTask;

//import java.io.IOException;
import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.ByteBuffer;
//import java.util.Map;

/**
 * Skeleton of an Android Things activity.
 * <p>
 * Android Things peripheral APIs are accessible through the class
 * PeripheralManagerService. For example, the snippet below will open a GPIO pin and
 * set it to HIGH:
 *
 * <pre>{@code
 * PeripheralManagerService service = new PeripheralManagerService();
 * mLedGpio = service.openGpio("BCM6");
 * mLedGpio.setDirection(Gpio.DIRECTION_OUT_INITIALLY_LOW);
 * mLedGpio.setValue(true);
 * }</pre>
 * <p>
 * For more complex peripherals, look for an existing user-space driver, or implement one if none
 * is available.
 *
 * @see <a href="https://github.com/androidthings/contrib-drivers#readme">https://github.com/androidthings/contrib-drivers#readme</a>
 */

/*
 * Portions of this code were taken from an open-source android activity found at this website on 12/5/2018
 * https://github.com/androidthings/doorbell/blob/master/app/src/main/java/com/example/androidthings/doorbell/DoorbellActivity.java
 */
public class MainActivity extends Activity {

    // Tag for logging
    public static final String TAG = "MAIN_ACTIVITY";

    // Variables for Firebase Database
    private FirebaseAuth mAuth = FirebaseAuth.getInstance();
    private String UID = mAuth.getCurrentUser().getUid();
    private DatabaseReference mDatabase = FirebaseDatabase.getInstance().getReference();
    private DatabaseReference piConnectRef;
    private DatabaseReference mMyDatabase = mDatabase.child("users").child(UID);

    public static final int INTERVAL_BETWEEN_CHECK_ARMED = 10000; // 10 seconds
    public static final String INDICATOR_LED = "BCM8"; // pin connected to the LED indicator


    // Variables for handling Firebase Cloud Storage
    private FirebaseStorage mStorage = FirebaseStorage.getInstance();
    private StorageReference mMyStorageBucket = mStorage.getReference().child("users").child(UID);


    // Camera junk
    private DoorbellCamera mCamera;
    private Handler mCameraHandler;
    private HandlerThread mCameraThread;

    // Alarm thread
    private Handler mAlarmHandler;
    private HandlerThread mAlarmThread;

    File mPlayThisFile = null;
    public static final long TWO_MEGABYTE = 1024 * 1024 * 2;

    private boolean isArmed = false;

    MediaPlayer mediaPlayer;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);


        Log.d(TAG, "Made it to main activity");

        // Get access to Firebase Database
        try {
            UID = mAuth.getCurrentUser().getUid();
            Log.d(TAG, "Current user is: " + UID);
            piConnectRef = mDatabase.child("users").child(UID).child("pi_connected");
            piConnectRef.onDisconnect().setValue(false);
            piConnectRef.setValue(true);
            Log.d(TAG, "Set value of pi_connected to true to show app status");
        } catch (DatabaseException de) {
            Log.e(TAG, "Exception when accessing database: ", de);
        }

        mMyDatabase.child("pi_armed").addValueEventListener(mPiArmedStatus);

        // Check for camera permissions. If not available, send error log
        if (checkSelfPermission(Manifest.permission.CAMERA)
                != PackageManager.PERMISSION_GRANTED) {
            // A problem occurred auto-granting the permission
            Log.e(TAG, "No permission to access camera");
        }

        // Creates new handlers and associated threads for camera and networking operations.
        mCameraThread = new HandlerThread("CameraBackground");
        mCameraThread.start();
        mCameraHandler = new Handler(mCameraThread.getLooper());

        mCamera = DoorbellCamera.getInstance();
        mCamera.initializeCamera(this, mCameraHandler, mOnImageAvailableListener);

        mediaPlayer = new MediaPlayer();
        Log.d(TAG, "Start reading sound data");
        final File soundfile = new File(this.getFilesDir(), "custom_alarm_sound.3gp");
        if(soundfile.exists()) {
            mPlayThisFile = soundfile;
        } else {
            tryToDownloadSoundFile();
        }

        mCamera.takePicture();
        mMyDatabase.child("sound").child("new_sound").addValueEventListener(mDBListenerSoundStatus);



        // Test Sound Stuff
        Log.d(TAG, "Running sound test...");
        //MediaPlayer mediaPlayer = MediaPlayer.create(context, mPlayThisFile);
        MediaPlayer mediaPlayer = new MediaPlayer();
        //mediaPlayer.setAudioStreamType(AudioManager.STREAM_MUSIC);

        PeripheralManager manager = PeripheralManager.getInstance();
        Log.d(TAG, "Available GPIO: " + manager.getGpioList());


        //********************************************************
        // Start of main logic
        // Get value of armed , assume false
        // If armed, check if triggered (on armed write)
        // If triggered, sound alarm sound and take picture
        mAlarmThread = new HandlerThread("AlarmBackground");
        mAlarmThread.start();
        mAlarmHandler = new Handler(mAlarmThread.getLooper());
        mAlarmHandler.post(mAlarmRunnable);

        // ************************************




        Log.d(TAG, "End of onCreate ...");
    } // End of onCreate

    private Runnable mAlarmRunnable = new Runnable() {
        @Override public void run() {
            // if the i2c isn't open somehow, then abort
            if (!isArmed) {
                Log.d(TAG, "Runnable but not actually armed");
                mAlarmHandler.postDelayed(mAlarmRunnable, INTERVAL_BETWEEN_CHECK_ARMED); // 10 sec
            } else {
                Log.d(TAG, "Runnable and is armed");
                // reschedule it to do all this again in 1/4 second
                //mHandler.postDelayed(mI2cRunnable, INTERVAL_BETWEEN_PIC_READS);

                // Check sensors for if alarm
                boolean foundAlarm = false;

                if (foundAlarm) {
                    // Sensor values have been tripped
                    mMyDatabase.child("pi_triggered").setValue(true);

                    mCamera.takePicture();

                    try {
                        //Log.d(TAG, "Trying to access file at: " + mPlayThisFile.getAbsolutePath().toString());
                        mediaPlayer.setDataSource(mPlayThisFile.getAbsolutePath());
                        mediaPlayer.prepare(); // might take long! (for buffering, etc)
                    } catch(IOException ex) {
                        Log.e(TAG, "Error with whatever - sound shit");
                    }
                    mediaPlayer.start();

                }
                mAlarmHandler.postDelayed(mAlarmRunnable, INTERVAL_BETWEEN_CHECK_ARMED); // 10 sec
            }
        }
    };







    protected ValueEventListener mDBListenerSoundStatus = new ValueEventListener() {
            @Override
            public void onDataChange(DataSnapshot dataSnapshot) {
                Log.d(TAG, "Logged a data change");
                // Get Post object and use the values to update the UI
                boolean isNewFile = false;
                try {
                    isNewFile = (Boolean) dataSnapshot.getValue();
                } catch (NullPointerException npe) {
                    Log.d(TAG, "error: bad data when getting initial values", npe);
                } catch(DatabaseException de) {
                    Log.d(TAG, "error: something bad", de);
                }
                if(isNewFile) {
                    Log.d(TAG, "New sound file found, downloading");
                    tryToDownloadSoundFile();
                } else {
                    Log.d(TAG, "No new sound file found");
                }
            }
            @Override
            public void onCancelled(DatabaseError databaseError) {
                // Getting boolean failed, log a message
                Log.w(TAG, "boolean read cancelled", databaseError.toException());
            }
    };

    protected ValueEventListener mPiArmedStatus = new ValueEventListener() {
        @Override
        public void onDataChange(DataSnapshot dataSnapshot) {
            Log.d(TAG, "Logged a data change");
            // Get Post object and use the values to update the UI
            boolean isCurrentlyArmed = false;
            try {
                isCurrentlyArmed = (Boolean) dataSnapshot.getValue();
                isArmed = isCurrentlyArmed;
            } catch (NullPointerException npe) {
                Log.d(TAG, "error: bad data when getting initial values", npe);
            } catch(DatabaseException de) {
                Log.d(TAG, "error: something bad", de);
            }
        }
        @Override
        public void onCancelled(DatabaseError databaseError) {
            // Getting boolean failed, log a message
            Log.w(TAG, "boolean read cancelled", databaseError.toException());
        }
    };

    private void tryToDownloadSoundFile() {
        Log.d(TAG, "Donwloading sound file");
        mMyStorageBucket.child("custom_alarm_sound.3gp").getBytes(TWO_MEGABYTE).addOnSuccessListener(new OnSuccessListener<byte[]>() {
            @Override
            public void onSuccess(byte[] bytes) {
                // Data for "" is returned, use this as needed
                Log.d(TAG, "done downloading sound file, size = " + bytes.length);
                mMyDatabase.child("sound/new_sound").setValue(false);

                // part 1: save it into local storage
                saveImageToLocal(bytes, "custom_alarm_sound.3gp");
                // find the file i just made
                File mfile = new File(MainActivity.this.getFilesDir(), "custom_alarm_sound.3gp");
                // assign the file i just made to the one that iwll be played
                mPlayThisFile = mfile;
            }
        }).addOnFailureListener(new OnFailureListener() {
            @Override
            public void onFailure(@NonNull Exception e) {
                // Handle any errors
                Log.d(TAG, "failed to download sound file", e);
                mMyDatabase.child("sound/new_sound").setValue(false);
                // use the default sound file (from assets)
                try {
                    InputStream i = getAssets().open("default_alarm_sound.3gp", AssetManager.ACCESS_BUFFER);
                    int size = i.available();
                    byte[] buffer = new byte[size];
                    i.read(buffer);
                    i.close();
                    saveImageToLocal(buffer, "default_alarm_sound.3gp");
                    File mfile = new File(MainActivity.this.getFilesDir(), "default_alarm_sound.3gp");
                    // assign the file i just made to the one that iwll be played
                    mPlayThisFile = mfile;
                } catch (IOException ioe) {
                    Log.d(TAG, "somehow failed to read assets!", ioe);
                }
            }
        });

    }

    private boolean saveImageToLocal(byte[] bytes, String filename) {
        // return false if everything is OK
        // return true if there was some exception
        Log.d(TAG, "Trying to save to local: " + filename);
        try {
            FileOutputStream outputStream = this.openFileOutput(filename, Context.MODE_PRIVATE);
            outputStream.write(bytes);
            outputStream.close();
            Log.d(TAG, "successfully saved local file to local storage");
            return false;
        } catch (FileNotFoundException fnfe) {
            Log.d(TAG, "fnfe exception1", fnfe);
            return true;
        } catch (IOException ioe) {
            Log.d(TAG, "io exception1", ioe);
            return true;
        } catch (NullPointerException npe) {
            Log.d(TAG, "npe exception1, perhaps due to device rotation?", npe);
            return true;
        }
    };






    /**
     * Listener for new camera images.
     */
    private ImageReader.OnImageAvailableListener mOnImageAvailableListener =
            new ImageReader.OnImageAvailableListener() {
                @Override
                public void onImageAvailable(ImageReader reader) {
                    Log.d(TAG, "Listener found available image!!!!!!!!");
                    Image image = reader.acquireLatestImage();
                    // get image bytes
                    ByteBuffer imageBuf = image.getPlanes()[0].getBuffer();
                    final byte[] imageBytes = new byte[imageBuf.remaining()];
                    imageBuf.get(imageBytes);
                    image.close();

                    onPictureTaken(imageBytes);
                }
            };

    /**
     * Upload image data to Firebase as a doorbell event.
     */
    private void onPictureTaken(final byte[] imageBytes) {
        Log.d(TAG, "In onPictureTaken, attemping to do storage write!");
        if (imageBytes != null) {
            Log.d(TAG, "passed bytes were not null ...");
            //final DatabaseReference log = mDatabase.getReference("logs").push();
            final StorageReference imageRef = mMyStorageBucket.child("new_test_image.jpg");

            // upload image to storage
            UploadTask task = imageRef.putBytes(imageBytes);
            task.addOnSuccessListener(new OnSuccessListener<UploadTask.TaskSnapshot>() {
                @Override
                public void onSuccess(UploadTask.TaskSnapshot taskSnapshot) {
                    //Uri downloadUrl = taskSnapshot.getDownloadUrl();
                    // mark image in the database
                    Log.d(TAG, "Image upload successful");
                    //log.child("timestamp").setValue(ServerValue.TIMESTAMP);
                    //log.child("image").setValue(downloadUrl.toString());
                    // process image annotations
                    //annotateImage(log, imageBytes);
                }
            }).addOnFailureListener(new OnFailureListener() {
                @Override
                public void onFailure(@NonNull Exception e) {
                    // clean up this entry
                    Log.w(TAG, "Unable to upload image to Firebase");
                    //log.removeValue();
                }
            });
        }
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        mCamera.shutDown();

        mCameraThread.quitSafely();
        //mCloudThread.quitSafely();
        /*
        try {
            mButtonInputDriver.close();
        } catch (IOException e) {
            Log.e(TAG, "button driver error", e);
        }
        */
    }

}
