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
import com.google.android.things.pio.Gpio;
import com.google.android.things.pio.GpioCallback;
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

    // TODO sensor detection & alarm triggering (auto photo & sound)

    // TODO sensor configuration from database

    // TODO range sensor? not likely

    // TODO make the camera work in low-res mode

    // TODO take hi-res photos

    // TODO make the speaker consistiently work (i have some questions)

    // Tag for logging
    public static final String TAG = "MAIN_ACTIVITY";
    // file names
    public static final String FILE_IMAGE_SMALL =   "img_0640x0480.jpg";
    public static final String FILE_IMAGE_BIG =     "img_3200x2400.jpg";
    public static final String FILE_SOUND_DEFAULT = "default_alarm_sound.3gp";
    public static final String FILE_SOUND_CUSTOM =  "custom_alarm_sound.3gp";
    public static final long TWO_MEGABYTE = 1024 * 1024 * 2;


    // Variables for Firebase Database
    private FirebaseAuth mAuth;
    private String mUID;
    private DatabaseReference mMyDatabase;

    public static final int INTERVAL_BETWEEN_CHECK_ARMED = 10000; // 10 seconds
    public static final String INDICATOR_LED = "BCM8"; // pin connected to the LED indicator


    // Variables for handling Firebase Cloud Storage
    private StorageReference mMyStorageBucket;


    // Camera junk
    private DoorbellCamera mCamera;
    private Handler mCameraHandler;
    private HandlerThread mCameraThread;

    // Alarm thread
    private Handler mAlarmHandler;
    private HandlerThread mAlarmThread;

    File mPlayThisFile = null;

    private boolean isArmed = false;
    private boolean hasSavedBigImage = false; // indicates that at a big image has been saved at least once since booting

    MediaPlayer mediaPlayer;

    private Gpio mSensorPin;
    private boolean firstTrigger = false;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        Log.d(TAG, "Made it to main activity");

        //////////////////////////////////////////////////////////////////////////////
        // authentication, connect to database, connect to cloud storage, perform wakeup operations
        try {
            mAuth = FirebaseAuth.getInstance();
            mUID = mAuth.getCurrentUser().getUid();
            Log.d(TAG, "Current user is: " + mUID);
        } catch (NullPointerException npe) {
            Log.d(TAG, "Failed to get current user id???", npe);
            return; // abort
        }

        // Get access to Firebase Database
        mMyDatabase = FirebaseDatabase.getInstance().getReference().child("users").child(mUID);
        // Variables for handling Firebase Cloud Storage
        mMyStorageBucket = FirebaseStorage.getInstance().getReference().child("users").child(mUID);

        try {
            // this section does database-related initial startup stuff
            // if i can set this then yes i am connected
            mMyDatabase.child("pi_connected").setValue(true);
            // handy: tell the server to set this to "false" if i ever disconnect
            mMyDatabase.child("pi_connected").onDisconnect().setValue(false);
            // 0 is the state for freshly-booted Pi
            mMyDatabase.child("camera/photo_pipeline_state").setValue(0);
            Log.d(TAG, "Set pi_connected = true to show app status");
        } catch (DatabaseException de) {
            Log.e(TAG, "Exception when accessing database: ", de);
            return; // abort
        }

        //////////////////////////////////////////////////////////////////////////
        // camera setup

        // Check for camera permissions. If not available, send error log
        if (checkSelfPermission(Manifest.permission.CAMERA) != PackageManager.PERMISSION_GRANTED) {
            // A problem occurred auto-granting the permission
            Log.e(TAG, "No permission to access camera");
            return; // abort
        }

        // Creates new handlers and associated threads for camera and networking operations.
        mCameraThread = new HandlerThread("CameraBackground");
        mCameraThread.start();
        mCameraHandler = new Handler(mCameraThread.getLooper());

        mCamera = DoorbellCamera.getInstance();
        mCamera.initializeCamera(this, mCameraHandler, mOnImageAvailableListener);

        // TODO: remove thiss when done testing
        mCamera.takePicture();

        /////////////////////////////////////////////////////////////////////////
        // media player setup

        mediaPlayer = new MediaPlayer();
        Log.d(TAG, "Start reading sound data");
        final File soundfile = new File(this.getFilesDir(), FILE_SOUND_CUSTOM);
        if(soundfile.exists()) {
            Log.d(TAG, "Alarm will use pre-downloaded custom sound file");
            mPlayThisFile = soundfile;
        } else {
            Log.d(TAG, "No pre-downloaded custom sound file exists, attempting to download:");
            tryToDownloadSoundFile();
        }


        // Test Sound Stuff
        Log.d(TAG, "Running sound test...");
        //MediaPlayer mediaPlayer = MediaPlayer.create(context, mPlayThisFile);
        MediaPlayer mediaPlayer = new MediaPlayer();
        //mediaPlayer.setAudioStreamType(AudioManager.STREAM_MUSIC);

        mAlarmThread = new HandlerThread("AlarmBackground");
        mAlarmThread.start();
        mAlarmHandler = new Handler(mAlarmThread.getLooper());

        ////////////////////////////////////////////////////////////////
        // GPIO setup, for indicator LED, what else????
        // also for hardcoded sensors (testing only)

        try {
            PeripheralManager manager = PeripheralManager.getInstance();
            Log.d(TAG, "Available GPIO: " + manager.getGpioList());
            mSensorPin = manager.openGpio(INDICATOR_LED);
            mSensorPin.setDirection(Gpio.DIRECTION_IN);
            mSensorPin.setActiveType(Gpio.ACTIVE_LOW);
            mSensorPin.setEdgeTriggerType(Gpio.EDGE_BOTH);
            mSensorPin.registerGpioCallback(mGpioCallback);
        } catch (IOException io) {
            Log.e(TAG, "Unable to open pin!!!!!!!");
        }


        try {
            mSensorPin.registerGpioCallback(mGpioCallback);
        } catch (IOException ie) {
            Log.e(TAG, "Unable to open pin!!!!!!!");
        }





        //********************************************************
        // Start of main logic
        // Get value of armed , assume false
        // If armed, check if triggered (on armed write)
        // If triggered, sound alarm sound and take picture

        //mAlarmHandler.post(mAlarmRunnable);

        // ************************************


        //////////////////////////////////////////////////////////////////
        // add the listener for the various states... is it only 3? i thought it would be more
        mMyDatabase.child("pi_armed").addValueEventListener(mDBListenerArmedStatus);
        mMyDatabase.child("sound/new_sound").addValueEventListener(mDBListenerNewSound);
        mMyDatabase.child("camera/photo_pipeline_state").addValueEventListener(mDBListenerCameraUploadDownload);
        mMyDatabase.child("sensor_config/sensor_config_obj").addValueEventListener(mDBListenerSensorConfig);




        Log.d(TAG, "End of onCreate ...");
    } // End of onCreate

    private GpioCallback mGpioCallback = new GpioCallback() {
        @Override
        public boolean onGpioEdge(Gpio gpio) {
            try {
                if(true) {
                    // tOdo fill
                    if (!isArmed) {
                        Log.d(TAG, "Runnable but not actually armed");
                        return false;
                    }
                    else{

                        // Valid alarm! Execute alarm stuff
                        mMyDatabase.child("pi_triggered").setValue(true);
                        Log.d(TAG, "Armed and triggered");
                        //mMyDatabase.child
                        mCamera.takePicture();
                        mplayMediaFile();
                        return true;
                    }
                }
                else {
                    //rstTrigger = true;
                    Log.d(TAG, "Level changed, no trigger");
                }
            } catch (Exception ie) {
                Log.e(TAG, "Could not get gpio value");
            }
            return false;
        }
    };


    private void mplayMediaFile () {
        if(mPlayThisFile != null) {
            try {
                //Log.d(TAG, "Trying to access file at: " + mPlayThisFile.getAbsolutePath().toString());
                mediaPlayer.setDataSource(mPlayThisFile.getAbsolutePath());
                mediaPlayer.prepare(); // might take long! (for buffering, etc)
            } catch (IOException ex) {
                Log.e(TAG, "Error with whatever - sound shit");
            }
            mediaPlayer.start();
        } else {
            Log.d(TAG, "somehow never set the alarm file, perhaps this was called before the cloud download failed?");
        }
    }


/*

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

*/



    // mostly done, just add "take photo" function
    protected ValueEventListener mDBListenerCameraUploadDownload = new ValueEventListener() {
        @Override public void onDataChange(DataSnapshot dataSnapshot) {
            Log.d(TAG, "Logged a data change");
            // Get Post object and use the values to update the UI
            int cameraState = 0;
            try {
                cameraState = (Integer) dataSnapshot.getValue();
            } catch (NullPointerException npe) {
                Log.d(TAG, "error1: bad data when getting initial values", npe);
            } catch(DatabaseException de) {
                Log.d(TAG, "error1: something bad", de);
            }
            Log.d(TAG, "Camera state is: " + cameraState);
            switch(cameraState) {
                case 0:
                    // do nothing
                    // both systems are idle, pi just booted up and has nothing in its memory
                    break;
                case 1:
                    // TODO: try to take a photo
                    // ?
                    // TODO: once image capture is working, this setValue happens in the onSuccess upload listener
                    mMyDatabase.child("camera/photo_pipeline_state").setValue(2);
                    break;
                case 2:
                    // do nothing
                    // app is currently downloading the preview photo
                    break;
                case 3:
                    // do nothing
                    // both systems are idle, but now the Pi has (or will have) a hires photo ready to upload
                    break;
                case 4:
                    // try to upload the hires photo
                    // if it exists, it begins... if it doesnt exist, it sets database to state 3
                    uploadBigImage();
                    // once image capture is working, comment this
                    //mMyDatabase.child("camera/photo_pipeline_state").setValue(5);
                    break;
                case 5:
                    // do nothing
                    // app is currently downloading the hires photo
                    break;
                default:
                    Log.d(TAG, "invalid DB camera state? " + cameraState);
                    return;
            }
        }
        @Override public void onCancelled(DatabaseError databaseError) {
            // Getting boolean failed, log a message
            Log.w(TAG, "int read cancelled1", databaseError.toException());
        }
    };

    // pretty sure this works, but we should re-test
    protected ValueEventListener mDBListenerNewSound = new ValueEventListener() {
        @Override public void onDataChange(DataSnapshot dataSnapshot) {
            Log.d(TAG, "Logged a data change");
            // Get Post object and use the values to update the UI
            boolean isNewFile = false;
            try {
                isNewFile = (Boolean) dataSnapshot.getValue();
            } catch (NullPointerException npe) {
                Log.d(TAG, "error2: bad data when getting initial values", npe);
            } catch(DatabaseException de) {
                Log.d(TAG, "error2: something bad", de);
            }
            if(isNewFile) {
                Log.d(TAG, "New sound file found, downloading");
                tryToDownloadSoundFile();
            } else {
                Log.d(TAG, "No new sound file found");
            }
        }
        @Override public void onCancelled(DatabaseError databaseError) {
            // Getting boolean failed, log a message
            Log.w(TAG, "boolean read cancelled2", databaseError.toException());
        }
    };

    // work in progress
    // TODO
    protected ValueEventListener mDBListenerArmedStatus = new ValueEventListener() {
        @Override public void onDataChange(DataSnapshot dataSnapshot) {
            Log.d(TAG, "Logged a data change");
            // Get Post object and use the values to update the UI
            boolean isCurrentlyArmed = false;
            try {
                isCurrentlyArmed = (Boolean) dataSnapshot.getValue();
                isArmed = isCurrentlyArmed;
            } catch (NullPointerException npe) {
                Log.d(TAG, "error3: bad data when getting initial values", npe);
            } catch(DatabaseException de) {
                Log.d(TAG, "error3: something bad", de);
            }
            Log.d(TAG, "Armed value changed to: " + isCurrentlyArmed);
            if(isArmed) {
                // TODO
                try {
                    mSensorPin.registerGpioCallback(mGpioCallback);
                } catch (IOException ie) {
                    Log.e(TAG, "Unable to register callback!!!!!!!");
                }
            } else {
                // either i set this to false (after an alarm) or the user did...
                // TODO in either case, should disarm here
            }
        }
        @Override public void onCancelled(DatabaseError databaseError) {
            // Getting boolean failed, log a message
            Log.w(TAG, "boolean read cancelled3", databaseError.toException());
        }
    };


    // work in progress
    // TODO
    protected ValueEventListener mDBListenerSensorConfig = new ValueEventListener() {
        @Override public void onDataChange(DataSnapshot dataSnapshot) {
            Log.d(TAG, "Logged a data change");
            // Get Post object and use the values to update the UI
            String json_from_db = null;
            try {
                json_from_db = (String) dataSnapshot.getValue();
                if(json_from_db == null)
                    throw new NullPointerException(); // if this reads a null string, its still a serious error
            } catch (NullPointerException npe) {
                Log.d(TAG, "error4: bad data when getting initial values", npe);
            } catch(DatabaseException de) {
                Log.d(TAG, "error4: something bad", de);
            }

            Log.d(TAG, "got new sensor config from the DB");
            // unpack the json string into an acutal object with actual contents
            SensorListObj slo = new SensorListObj(json_from_db);
            // TODO DO THE THING
        }
        @Override public void onCancelled(DatabaseError databaseError) {
            // Getting boolean failed, log a message
            Log.w(TAG, "string read cancelled4", databaseError.toException());
        }
    };






    // pretty sure this is done, but we should re-test
    private void tryToDownloadSoundFile() {
        Log.d(TAG, "Donwloading sound file");
        mMyStorageBucket.child(FILE_SOUND_CUSTOM).getBytes(TWO_MEGABYTE).addOnSuccessListener(new OnSuccessListener<byte[]>() {
            @Override
            public void onSuccess(byte[] bytes) {
                // Data for "" is returned, use this as needed
                Log.d(TAG, "using freshly downloaded sound as alarm, size = " + bytes.length);
                // indicate to the app that it's done downloading
                mMyDatabase.child("sound/new_sound").setValue(false);

                // part 1: save it into local storage
                saveFileToLocal(bytes, FILE_SOUND_CUSTOM);
                // find the file i just made
                File mfile = new File(MainActivity.this.getFilesDir(), FILE_SOUND_CUSTOM);
                // assign the file i just made to the one that iwll be played
                mPlayThisFile = mfile;
            }
        }).addOnFailureListener(new OnFailureListener() {
            @Override
            public void onFailure(@NonNull Exception e) {
                // Handle any errors
                Log.d(TAG, "failed to download sound file, perhaps it doesn't exist?", e);
                // set this to false just for safety's sake
                mMyDatabase.child("sound/new_sound").setValue(false);
                // use the default sound file (from assets)
                try {
                    InputStream i = getAssets().open(FILE_SOUND_DEFAULT, AssetManager.ACCESS_BUFFER);
                    int size = i.available();
                    byte[] buffer = new byte[size];
                    i.read(buffer);
                    i.close();
                    // copy from assets folder to local storage, because i can
                    saveFileToLocal(buffer, FILE_SOUND_DEFAULT);
                    Log.d(TAG, "using the default assets sound file as the alarm");
                    File mfile = new File(MainActivity.this.getFilesDir(), FILE_SOUND_DEFAULT);
                    // assign the file i just made to the one that iwll be played
                    mPlayThisFile = mfile;
                } catch (IOException ioe) {
                    Log.d(TAG, "somehow failed to read assets!", ioe);
                }
            }
        });
    }

    // done
    private boolean saveFileToLocal(byte[] bytes, String filename) {
        // return false if everything is OK
        // return true if there was some exception
        Log.d(TAG, "Trying to save to local: " + filename);
        try {
            FileOutputStream outputStream = this.openFileOutput(filename, Context.MODE_PRIVATE);
            outputStream.write(bytes);
            outputStream.close();
            Log.d(TAG, "successfully saved local file to local storage, size=" + bytes.length);
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
    // work in progress
    private ImageReader.OnImageAvailableListener mOnImageAvailableListener = new ImageReader.OnImageAvailableListener() {
        @Override public void onImageAvailable(ImageReader reader) {
            Log.d(TAG, "Listener found available image!!!!!!!!");
            Image image = reader.acquireLatestImage();
            // get image bytes
            Log.d(TAG, "image has height=" + image.getHeight());
            Log.d(TAG, "image has #planes=" + image.getPlanes().length);
            for(int i = 0; i < image.getPlanes().length; i++) {
                Log.d(TAG, "image plane " + i + " has #bytes=" + (image.getPlanes()[i].getBuffer().array().length));
            }

            ByteBuffer imageBuf = image.getPlanes()[0].getBuffer();
            final byte[] imageBytes = new byte[imageBuf.remaining()];
            imageBuf.get(imageBytes);
            image.close();

            if(image.getHeight() == 480) {
                uploadSmallimage(imageBytes);
                // TODO: also initiate capture of a second photo in hires mode
            } else if(image.getHeight() == 2400){
                saveBigImage(imageBytes);
            } else {
                Log.d(TAG, "found unexpected image height!");
            }
        }
    };

    // done
    private void saveBigImage(byte[] imageBytes) {
        // this is called as soon as the camera is done taking the hires image
        saveFileToLocal(imageBytes, FILE_IMAGE_BIG);
        hasSavedBigImage = true;
    }

    // done (pending test)
    private void uploadBigImage() {
        // this is called only when the camera pipeline moves to state 4
        if(!hasSavedBigImage) {
            Log.d(TAG, "cannot upload big image until I'm done saving it");
            // set state to 3 (waiting) to undo/abort the user's request for the photo
            mMyDatabase.child("camera/photo_pipeline_state").setValue(3);
        } else {
            Log.d(TAG, "In uploadBIGimage, attemping to do storage write!");

            final File imgfile = new File(this.getFilesDir(), FILE_IMAGE_BIG);
            if(!imgfile.exists()) {
                Log.d(TAG, "hasSavedBigImage=true but the file doesn't exist!!");
                return;
            }
            // upload image to storage
            mMyStorageBucket.child(FILE_IMAGE_BIG).putFile(android.net.Uri.fromFile(imgfile))
                    .addOnSuccessListener(new OnSuccessListener<UploadTask.TaskSnapshot>() {
                @Override public void onSuccess(UploadTask.TaskSnapshot taskSnapshot) {
                    Log.d(TAG, "BIG Image upload successful, bytes transferred=" + taskSnapshot.getBytesTransferred());
                    // move to stage 5 so the app knows to download it
                    mMyDatabase.child("camera/photo_pipeline_state").setValue(5);
                }
            }).addOnFailureListener(new OnFailureListener() {
                @Override
                public void onFailure(@NonNull Exception e) {
                    Log.w(TAG, "Unable to upload BIG image to Firebase", e);
                    // reset to stage 3 cuz something went wrong
                    mMyDatabase.child("camera/photo_pipeline_state").setValue(3);
                }
            });
        }
    }

    // done (pending test)
    private void uploadSmallimage(final byte[] imageBytes) {
        // this is called as soon as the camera is done taking the small image
        Log.d(TAG, "In uploadSmallimage, attemping to do storage write!");
        if (imageBytes != null) {
            Log.d(TAG, "passed bytes were not null ..." + imageBytes.length);
            //final DatabaseReference log = mDatabase.getReference("logs").push();
            //StorageReference imageRef = mMyStorageBucket.child(FILE_IMAGE_SMALL);

            // upload image to storage
            //UploadTask task = imageRef.putBytes(imageBytes);
            mMyStorageBucket.child(FILE_IMAGE_SMALL).putBytes(imageBytes).addOnSuccessListener(new OnSuccessListener<UploadTask.TaskSnapshot>() {
                @Override
                public void onSuccess(UploadTask.TaskSnapshot taskSnapshot) {
                    //Uri downloadUrl = taskSnapshot.getDownloadUrl();
                    // mark image in the database
                    Log.d(TAG, "SMALL Image upload successful, bytes transferred=" + taskSnapshot.getBytesTransferred());
                    //log.child("timestamp").setValue(ServerValue.TIMESTAMP);
                    //log.child("image").setValue(downloadUrl.toString());
                    // process image annotations
                    //annotateImage(log, imageBytes);
                    // move to stage 2 so the app knows to download it
                    mMyDatabase.child("camera/photo_pipeline_state").setValue(2);
                }
            }).addOnFailureListener(new OnFailureListener() {
                @Override
                public void onFailure(@NonNull Exception e) {
                    // clean up this entry
                    Log.w(TAG, "Unable to upload image to Firebase", e);
                    //log.removeValue();
                    // reset to stage 0 cuz something went wrong
                    mMyDatabase.child("camera/photo_pipeline_state").setValue(0);
                }
            });
        }
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        mCamera.shutDown();

        mCameraThread.quitSafely();
        mAlarmThread.quitSafely();
        //mCloudThread.quitSafely();

        if(mSensorPin != null) {
            try {
                mSensorPin.close();
                mSensorPin = null;
            } catch (IOException e) {
                Log.e(TAG, "button driver error", e);
            }
        }
    }

}
