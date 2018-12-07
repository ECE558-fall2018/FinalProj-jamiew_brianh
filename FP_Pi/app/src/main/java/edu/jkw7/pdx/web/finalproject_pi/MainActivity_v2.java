package edu.jkw7.pdx.web.finalproject_pi;

import android.Manifest;
import android.app.Activity;
import android.content.Context;
import android.media.MediaPlayer;
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

//import com.google.android.things.contrib.driver.button.Button;
//import com.google.android.things.contrib.driver.button.ButtonInputDriver;
import com.google.firebase.database.DataSnapshot;
import com.google.firebase.database.DatabaseError;
import com.google.firebase.database.DatabaseException;
import com.google.firebase.database.DatabaseReference;
import com.google.firebase.database.FirebaseDatabase;
//import com.google.firebase.database.ServerValue;
import com.google.firebase.database.ValueEventListener;

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

    // DONE sensor detection & alarm triggering (auto photo & sound)

    // DONE sensor configuration from database

    // TODO range sensor? not likely

    // TESTME make the camera work in low-res mode

    // TESTME take hi-res photos

    // TESTME make the speaker consistiently work (i have some questions)

    // TODO refine the login activity (just replace with newer version of Brian's code)

    // QUESTION re-evaluate whether loopers and threads are needed for anything

    // Tag for logging
    public static final String TAG = "MAIN_ACTIVITY";
    // file names
    public static final String FILE_IMAGE_SMALL =   "img_0640x0480.jpg";
    public static final String FILE_IMAGE_BIG =     "img_3200x2400.jpg";
    public static final String FILE_SOUND_DEFAULT = "default_alarm_sound.3gp";
    public static final String FILE_SOUND_CUSTOM =  "custom_alarm_sound.3gp";
    public static final long TWO_MEGABYTE = 1024 * 1024 * 2;
    public static final int ALARM_SOUND_MAX_LOOPS = 3;


    // true if pullup resistors, false if pulldown resistors
    public static final boolean SENSOR_PULLUP = true;


    // Variables for Firebase Database
    private FirebaseAuth mAuth;
    private String mUID;
    private DatabaseReference mMyDatabase;

    public static final int INTERVAL_BETWEEN_CHECK_ARMED = 10000; // 10 seconds


    // Variables for handling Firebase Cloud Storage
    private StorageReference mMyStorageBucket;


    // Camera junk
    private DoorbellCamera mCamera;
    private Handler mCameraHandler;
    private HandlerThread mCameraThread;

    private File mPlayThisFile = null;
    private MediaPlayer mPlayer;
    private int mSoundLoopCount = 0;
    private boolean mIsAlarming = false;

    private boolean mIsArmed = false;
    private boolean mHasSavedBigImage = false; // indicates that at a big image has been saved at least once since booting
    private boolean mSkipNextImageCapture = false;
    private boolean mIsCalibratingRangeSensor = false;
    private boolean mIsConfiguringGpio = false; // just to be safe, lock out any alarms while fiddling with GPIO


    public static final String INDICATOR_LED1 = "BCM6"; // pin connected to the LED indicator 1
    public static final String INDICATOR_LED2 = "BCM7"; // pin connected to the LED indicator 2
    public static final String SENSOR_TEST_PIN = "BCM8"; // pin for hardcoded contact sensor
    /* NOTE:
    BCM1 does not exist? odd
    BCM2/3 are I2C
    BCM4/5/6/7 are for LED indicators (probably only use 1 tho, maybe 2)
    BCM8+ are planned to be configurable sensor pins.
     */
    private Gpio mSensorPin; // the contact sensor pin for testing, BCM8
    private Gpio mIndicator1; // on BCM6
    private Gpio mIndicator2; // on BCM7
    private Gpio mSensorArray[]; // BCM8+
    PeripheralManager mPeriManager;

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
            Log.d(TAG, "Set pi_connected=true and photo_pipeline_state=0");
        } catch (DatabaseException de) {
            Log.e(TAG, "Exception when accessing database: ", de);
            return; // abort
        }

        //////////////////////////////////////////////////////////////////////////
        // camera setup

        // note, here's the plan for take_picture:
        // take small, when done I begin taking big in parallel with uploading small. then set state=3 when done uploading small.
        // time to get a photo should be <1sec, even the big photo, so it should always be done taking the big photo by the time
        // the upload is done (probably, have mHasSavedBigImage just in case). worst-case, it uploads the previous capture's big version
        // IMPROVEMENT: at beginning of take_picture, set mHasSavedBigImage to false, everything else stays the same. worst-case the
        // user has to press the "download hires" button multiple times, that's not so bad!

        // Check for camera permissions. If not available, send error log
        if (checkSelfPermission(Manifest.permission.CAMERA) != PackageManager.PERMISSION_GRANTED) {
            // A problem occurred auto-granting the permission
            Log.e(TAG, "No permission to access camera");
            return; // abort
        }

        // todo: does this need to be a backgound thread? can't it just be a handler that points to the main thread?
        // Creates new handlers and associated threads for camera and networking operations.
        mCameraThread = new HandlerThread("CameraBackground");
        mCameraThread.start();
        mCameraHandler = new Handler(mCameraThread.getLooper());

        mCamera = DoorbellCamera.getInstance();
        mCamera.initializeCamera(this, mCameraHandler, mOnImageAvailableListener);

        /////////////////////////////////////////////////////////////////////////
        // media player setup

        Log.d(TAG, "Start reading sound data");
        final File soundfile = new File(this.getFilesDir(), FILE_SOUND_CUSTOM);
        if(soundfile.exists()) {
            Log.d(TAG, "Alarm will use pre-downloaded custom sound file");
            mPlayThisFile = soundfile;
        } else {
            Log.d(TAG, "No pre-downloaded custom sound file exists, attempting to download:");
            tryToDownloadSoundFile();
        }

        mPlayer = new MediaPlayer();
        mPlayer.setLooping(true);
        mPlayer.setOnCompletionListener(new MediaPlayer.OnCompletionListener() {
            @Override public void onCompletion(MediaPlayer mp) {
                if(mSoundLoopCount < ALARM_SOUND_MAX_LOOPS) {
                    // increment and let it loop again, no need to prompt it (i hope)
                    mSoundLoopCount++;
                } else {
                    // time to stop!
                    mp.stop(); mSoundLoopCount = 0; mIsAlarming = false;
                }
            }
        });


        ////////////////////////////////////////////////////////////////
        // GPIO setup, for indicator LED, what else????
        // also for hardcoded sensors (testing only)

        // perimanager is first
        try {
            mPeriManager = PeripheralManager.getInstance();
            Log.d(TAG, "Available GPIO: " + mPeriManager.getGpioList());
        } catch(IOException ioe) {
            Log.d(TAG, "failed to create PeriManager", ioe);
            return; // abort
        }

        //next is output LEDs
        try {
            // TODO: assuming that HIGH = LED ON
            // TODO: connect a second LED indicator
            mIndicator1 = mPeriManager.openGpio(INDICATOR_LED1);
            mIndicator1.setDirection(Gpio.DIRECTION_OUT_INITIALLY_LOW);
            mIndicator1.setActiveType(Gpio.ACTIVE_HIGH);
            mIndicator2 = mPeriManager.openGpio(INDICATOR_LED2);
            mIndicator2.setDirection(Gpio.DIRECTION_OUT_INITIALLY_LOW);
            mIndicator2.setActiveType(Gpio.ACTIVE_HIGH);
        } catch (IOException io) {
            Log.e(TAG, "Unable to open led pins!!!!!!!", io);
            return;
        }

        // TODO: set up the range-finder-sensor? not likely, but it would go here


        //////////////////////////////////////////////////////////////////
        // add the listeners for the various states... is it only 4? i thought it would be more
        mMyDatabase.child("pi_armed").addValueEventListener(mDBListenerArmedStatus);
        mMyDatabase.child("sound/new_sound").addValueEventListener(mDBListenerNewSound);
        mMyDatabase.child("camera/photo_pipeline_state").addValueEventListener(mDBListenerCameraUploadDownload);
        mMyDatabase.child("sensor_config/sensor_config_obj").addValueEventListener(mDBListenerSensorConfig);


        ///////////////////////////////////////////////////////////////////////////
        // testing pins & other code
        // TODO: remove thiss when done testing
        try {
            // TODO: remove the testing code here once the Sensor Config block is good
            mSensorPin = mPeriManager.openGpio(SENSOR_TEST_PIN);
            mSensorPin.setDirection(Gpio.DIRECTION_IN);
            mSensorPin.setActiveType(Gpio.ACTIVE_LOW);
            mSensorPin.setEdgeTriggerType(Gpio.EDGE_BOTH);
            mSensorPin.registerGpioCallback(mAlarmEvent);
        } catch (IOException io) {
            Log.e(TAG, "Unable to open pin!!!!!!!", io);
            return;
        }

        mCamera.takePicture();
        DoorbellCamera.dumpFormatInfo(this);



        Log.d(TAG, "End of onCreate ...");
    } // End of onCreate


    // ================================================================================================================



    // done? (pending test)
    private GpioCallback mAlarmEvent = new GpioCallback() {
        @Override public boolean onGpioEdge(Gpio g) {
            // this function is called when a door or vibration sensor is triggered!!
            // return false to unregister this listener (i assume on just this pin) or return true to keep listening
            Log.d(TAG, "sensor tripped! pin="+g.getName()+", mIsArmed="+mIsArmed+", mIsAlarming="+mIsAlarming+", mIsConfiguringGpio="+mIsConfiguringGpio);
            if(mIsArmed && !mIsConfiguringGpio && !mIsAlarming) {
                Log.d(TAG, "~~~~ALARM IS VALID!!!~~~~");
                // disarm myself, local variable and in database
                mIsArmed = false;
                mMyDatabase.child("pi_armed").setValue(false);
                // signal the Functions that an alarm happened
                mMyDatabase.child("pi_triggered").setValue(true);

                // take a photo & automatically upload, proceeds to state 2 when finished (skip state 1)
                // IDEA: mSkipNextPhotoCapture.... why did i think this was necessary? i forget...
                captureSmallPhoto();

                // play the sound (on loop, hopefully) and set mIsAlarming so that it won't be tripped again until it's done looping
                mPlaySoundFile();
            }
            return true;
        }
        @Override public void onGpioError (Gpio g, int error) {
            // according to docs, if this function happens the pin is busted and therefore won't receive any more callbacks
            // so may as well close it
            g.close();
            Log.d(TAG, "serious gpio error! pin=" + g.getName() + ", errnum=" + error);
        }
    };



    // done? pending test
    private void mPlaySoundFile() {
        // this will play the sound on loop 3 times
        if(mPlayThisFile != null) {
            Log.d(TAG, "preparing to play alarm sound!");
            try {
                //Log.d(TAG, "Trying to access file at: " + mPlayThisFile.getAbsolutePath().toString());
                mPlayer.setDataSource(mPlayThisFile.getAbsolutePath());
                mPlayer.prepare(); // might take long! (how long?)
            } catch (IOException ex) {
                Log.e(TAG, "Error with sound shit", ex);
            }
            Log.d(TAG, "starting to play alarm sound!");
            mIsAlarming = true;
            mPlayer.start();
        } else {
            Log.d(TAG, "somehow never set the alarm file, perhaps this was called before the cloud download failed?");
        }
    }


/*
    this was old plan, new plan is better
    private Runnable mAlarmRunnable = new Runnable() {
        @Override public void run() {
            // if the i2c isn't open somehow, then abort
            if (!mIsArmed) {
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
                        mPlayer.setDataSource(mPlayThisFile.getAbsolutePath());
                        mPlayer.prepare(); // might take long! (for buffering, etc)
                    } catch(IOException ex) {
                        Log.e(TAG, "Error with whatever - sound shit");
                    }
                    mPlayer.start();

                }
                mAlarmHandler.postDelayed(mAlarmRunnable, INTERVAL_BETWEEN_CHECK_ARMED); // 10 sec
            }
        }
    };

*/


    // mostly done, just add "take photo" function
    protected ValueEventListener mDBListenerCameraUploadDownload = new ValueEventListener() {
        @Override public void onDataChange(@NonNull DataSnapshot dataSnapshot) {
            //Log.d(TAG, "Logged a data change");
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
                    // try to take a photo
                    captureSmallPhoto();
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
                    // TODO: once image capture is working, comment this
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
        @Override public void onDataChange(@NonNull DataSnapshot dataSnapshot) {
            //Log.d(TAG, "Logged a data change");
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

    // done (pending test)
    // instead of attaching listeners here, they are always attached, they just sometimes do nothing
    protected ValueEventListener mDBListenerArmedStatus = new ValueEventListener() {
        @Override public void onDataChange(@NonNull DataSnapshot dataSnapshot) {
            //Log.d(TAG, "Logged a data change");
            // Get Post object and use the values to update the UI
            boolean isCurrentlyArmed = false;
            try {
                isCurrentlyArmed = (Boolean) dataSnapshot.getValue();
            } catch (NullPointerException npe) {
                Log.d(TAG, "error3: bad data when getting initial values", npe);
            } catch(DatabaseException de) {
                Log.d(TAG, "error3: something bad", de);
            }
            Log.d(TAG, "Armed value changed to: " + isCurrentlyArmed);
            mIsArmed = isCurrentlyArmed;
        }
        @Override public void onCancelled(DatabaseError databaseError) {
            // Getting boolean failed, log a message
            Log.w(TAG, "boolean read cancelled3", databaseError.toException());
        }
    };

    // done? pushed the work to configSensorsFromSLO() function
    protected ValueEventListener mDBListenerSensorConfig = new ValueEventListener() {
        @Override public void onDataChange(@NonNull DataSnapshot dataSnapshot) {
            //Log.d(TAG, "Logged a data change");
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
            configSensorsFromSLO(slo);
        }
        @Override public void onCancelled(DatabaseError databaseError) {
            // Getting boolean failed, log a message
            Log.w(TAG, "string read cancelled4", databaseError.toException());
        }
    };


    // done? pending test
    private void configSensorsFromSLO(SensorListObj slo) {
        // idea: iterate over the SensorListObj contents and configure each pin as contact/vibration/DC
        // also pause to re-calibrate the range sensor? (would it be here or somewhere else?)(unset local "armed" value while calibrating)
        mIsConfiguringGpio = true;
        if(mSensorArray.length > 0) {
            // first, if the ports have been opened before, close all configurable GPIO ports
            // this is so I know everything is in the same status
            for(Gpio snsr : mSensorArray) {
                try {
                    // TODO: do I need to unregister handlers here? probably not
                    // its worth remembering this if handlers are double- or triple-triggering however
                    snsr.close();
                } catch (IOException ioe) {
                    Log.d(TAG, "closing an already closed pin, dont worry its fiiiiiine");
                }
            }
        } else {
            // forgot to NEW the array if it starts out empty, oops
            mSensorArray = new Gpio[slo.mNameList.length];
        }

        // now iterate over the list and open/configure the pins (and save their references into the mSensorArray)
        for (int i = 0; i < slo.mNameList.length; i++) {
            if(slo.mTypeList[i] != 0) {
                // if this sensor is contact or vibration, then open the pin
                try {
                    mSensorArray[i] = mPeriManager.openGpio(slo.mNameList[i]);
                    mSensorArray[i].setDirection(Gpio.DIRECTION_IN);
                    // note: !!!!! rising edge means false-to-true, not low-to-high
                    mSensorArray[i].setEdgeTriggerType(Gpio.EDGE_RISING);
                    // TODO: decide sensor config! active high/low?
                    // active high (logic true = high voltage) or active low (logic true = low voltage)?
                    // select active level so safe=false and tripped=true
                    // depends on whether they have pullup or pulldown resistors
                    if(slo.mTypeList[i] == 1) {
                        // **** contact sensor: door closed = safe = closed circuit, door open = alarm =open circuit
                        if(SENSOR_PULLUP)
                            mSensorArray[i].setActiveType(Gpio.ACTIVE_LOW);
                        else
                            mSensorArray[i].setActiveType(Gpio.ACTIVE_HIGH);
                    } else if(slo.mTypeList[i] == 2) {
                        // **** vibration sensor: stable = safe = open circuit, vibrating = alarm = closed circuit
                        if(SENSOR_PULLUP)
                            mSensorArray[i].setActiveType(Gpio.ACTIVE_HIGH);
                        else
                            mSensorArray[i].setActiveType(Gpio.ACTIVE_LOW);
                    } else {
                        Log.d(TAG, "ERROR: invalid sensor type from JSON object!? pin '" + slo.mNameList[i] + "'=" + slo.mTypeList[i]);
                    }
                    // probably wise to do this after all the other settings
                    mSensorArray[i].registerGpioCallback(mAlarmEvent);
                    Log.d(TAG, "configured pin '" + slo.mNameList[i] + "'=" + slo.mTypeList[i]);
                } catch (IOException ioe) {
                    Log.d(TAG, "error: failed to open or config:" + slo.mNameList[i] + "'=" + slo.mTypeList[i]);
                }
            }
        }

        mIsConfiguringGpio = false;
        // TODO: should I trigger the range finder calibration here?
        // I don't think so, would probably be done at the same time sensors are configured but not necessarilly
        // in my opinion it should be a separate thing separately triggered by a separate button on the app GUI (if we get to that)
    }



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
                // assign the file i just made to the one that iwll be played
                mPlayThisFile = new File(MainActivity.this.getFilesDir(), FILE_SOUND_CUSTOM);;
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
                    int r = i.read(buffer);
                    if(r != size) {
                        Log.d(TAG, "num bytes read != num bytes available!? in default sound file");
                    }
                    i.close();
                    // copy from assets folder to local storage, because i can
                    saveFileToLocal(buffer, FILE_SOUND_DEFAULT);
                    Log.d(TAG, "using the default assets sound file as the alarm");
                    // assign the file i just made to the one that iwll be played
                    mPlayThisFile = new File(MainActivity.this.getFilesDir(), FILE_SOUND_DEFAULT);;
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


    // WORK-IN-PROGRESS
    // TODO
    private void captureSmallPhoto() {
        // invalidate the previously-held big image (if there was one)
        mHasSavedBigImage = false;
        // turn on LED1
        mIndicator1.setValue(true);
        // this is called when the camera pipeline goes to state 1 (via app)
        // also called when the alarm is triggered
        // trigger the camera
        mCamera.takePicture(false);
    }


    // WORK-IN-PROGRESS
    // TODO
    private void captureBigPhoto() {
        // this is called when the small image is done being captured
        // NOTE: giving up on the "capture one image and reformat it into big & small versions" plan
        // instead just take 2 photos in rapid succession, its not as clean but oh well

        // before camera invocation, turn on LED2
        mIndicator2.setValue(true);

        // trigger the camera with higher resolution, but use the same mOnImageAvailableListener
        // if using the same mOnImageAvailableListener causes issues we'll copy that too... but only if we need to
        mCamera.takePicture(true);
    }




    // work in progress
    private ImageReader.OnImageAvailableListener mOnImageAvailableListener = new ImageReader.OnImageAvailableListener() {
        @Override public void onImageAvailable(ImageReader reader) {
            Log.d(TAG, "Listener found available image!!!!!!!!");
            Image image = reader.acquireLatestImage();
            // get image bytes
            Log.d(TAG, "image has height=" + image.getHeight());
            // im curious about image planes
            Log.d(TAG, "image has #planes=" + image.getPlanes().length);
            for(int i = 0; i < image.getPlanes().length; i++) {
                Log.d(TAG, "image plane " + i + " has #bytes=" + (image.getPlanes()[i].getBuffer().array().length));
            }

            ByteBuffer imageBuf = image.getPlanes()[0].getBuffer();
            final byte[] imageBytes = new byte[imageBuf.remaining()];
            imageBuf.get(imageBytes);
            image.close();

            if(image.getHeight() == 480) {
                // small photo came out of the pipeline!!! now do something with it
                // turn off LED1
                mIndicator1.setValue(false);
                uploadSmallimage(imageBytes);
                // initiate capture of a second photo in hires mode
                captureBigPhoto();
            } else if(image.getHeight() == 2400){
                // turn off LED2
                mIndicator2.setValue(false);
                // big photo came out of the pipeline!!! now do something with it
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
        // indicate that the big image file is valid
        mHasSavedBigImage = true;
    }

    // done (pending test)
    private void uploadBigImage() {
        // this is called only when the camera pipeline moves to state 4
        if(!mHasSavedBigImage) {
            Log.d(TAG, "cannot upload big image until I'm done saving it");
            // set state to 3 (waiting) to undo/abort the user's request for the photo
            mMyDatabase.child("camera/photo_pipeline_state").setValue(3);
        } else {
            Log.d(TAG, "In uploadBIGimage, attemping to do storage write!");

            final File imgfile = new File(this.getFilesDir(), FILE_IMAGE_BIG);
            if(!imgfile.exists()) {
                Log.d(TAG, "mHasSavedBigImage=true but the file doesn't exist!!");
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



    // ================================================================================================================

    // note: single-activity embedded system will only hit onDestroy on unexpected power-down (or installing new version of an app, maybe?)
    // so releasing resources here isn't strictly necessary
    // but its good policy
    @Override protected void onDestroy() {
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
