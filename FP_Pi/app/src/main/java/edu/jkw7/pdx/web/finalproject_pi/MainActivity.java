package edu.pdx.ece558f18.bhenson.finalproj_app;

import android.Manifest;
import android.app.Activity;
import android.content.Context;
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
import android.Manifest;
import android.content.Context;
import android.content.pm.PackageManager;
import android.graphics.BitmapFactory;
import android.media.MediaRecorder;
import android.os.Bundle;
import android.os.Environment;
import android.os.Handler;
import android.support.annotation.NonNull;
import android.support.v4.app.ActivityCompat;
import android.support.v4.app.Fragment;
import android.support.v4.content.ContextCompat;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.ImageView;
import android.widget.ProgressBar;
import android.widget.Toast;
import com.google.android.gms.tasks.OnFailureListener;
import com.google.android.gms.tasks.OnSuccessListener;
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



    // Variables for handling Pi Camera
    private DoorbellCamera mCamera;
    private Handler mCameraHandler;
    private HandlerThread mCameraThread;

    // Variables for handling Firebase Cloud Storage
    private FirebaseStorage mStorage = FirebaseStorage.getInstance();
    private StorageReference mMyStorageBucket = mStorage.getReference().child("users").child(UID);
    private Handler mCloudHandler;
    private HandlerThread mCloudThread;

    private boolean haveHiResImage = false;
    private static final int IMAGE_HEIGHT = 480;

    File mPlayThisFile = null;
    public static final long TWO_MEGABYTE = 1024 * 1024 * 2;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(com.google.firebase.database.R.layout.activity_main);


        Log.d(TAG, "Made it to main activity");

        // Get access to Firebase Database
        try {
            UID = mAuth.getCurrentUser().getUid();
            Log.d(TAG, "Current user is: " + UID);
            //mDatabase.child(DATA_PI2APP).child(IN_ADC3).setValue(new_adc1);

            piConnectRef = mDatabase.child("users").child(UID).child("pi_connected");
            piConnectRef.onDisconnect().setValue(false);
            piConnectRef.setValue(true);
            Log.d(TAG, "Set value of pi_connected to true to show app status");
        } catch (DatabaseException de) {
            Log.e(TAG, "Exception when accessing database: ", de);
        }

        // Check for camera permissions. If not available, send error log
        if (checkSelfPermission(Manifest.permission.CAMERA)
                != PackageManager.PERMISSION_GRANTED) {
            // A problem occurred auto-granting the permission
            Log.e(TAG, "No permission to access camera");
        }

        File file = new File(this.getFilesDir(), "custom_alarm_sound.3gp");
        if (file.exists()) {
            // pipe it into the image view
            mPlayThisFile = file;
        } else {
            tryToDownloadSoundFile();
        }

        Log.d(TAG, "Creating handlers ...");
        // Creates new handlers and associated threads for camera and Firebase Cloud Storage operations.
        mCameraThread = new HandlerThread("CameraBackground");
        mCameraThread.start();
        mCameraHandler = new Handler(mCameraThread.getLooper());

        mCloudThread = new HandlerThread("CloudThread");
        mCloudThread.start();
        mCloudHandler = new Handler(mCloudThread.getLooper());

        // Initialize camera from open-source class
        Log.d(TAG, "Initializing camera");
        mCamera = DoorbellCamera.getInstance();
        mCamera.initializeCamera(this, mCameraHandler, mOnImageAvailableListener, mOnImageAvailableListenerHR);

        Log.d(TAG, "Taking picture...");
        mCamera.takePicture();
        uploadHiResImage();

    }


    protected ValueEventListener mDBListenerSoundStatus = new ValueEventListener() {
        @Override public void onDataChange(@NonNull DataSnapshot ds) {
            Log.d(TAG, "sound status changed");
            boolean b = false;
            try {
                b = ds.getValue(Boolean.class);
            } catch (NullPointerException npe) {
                Log.d(TAG, "error: bad data when getting initial values", npe);
                return;
            } catch(DatabaseException de) {
                Log.d(TAG, "error: something bad", de);
                return;
            }

            if(b) {
                tryToDownloadSoundFile();
            } else {
                // do nothing
            }
        }
        @Override public void onCancelled(@NonNull DatabaseError de) {
            // Failed to read value, not sure how or what to do about it
            Log.d(TAG, "firebase error: failed to get snapshot??", de.toException());
        }
    };

    private void tryToDownloadSoundFile() {
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
            Log.d(TAG, "Image Reader Listener");
            Image image = reader.acquireLatestImage();
            int imgHeight = image.getHeight();
            // get image bytes
            ByteBuffer imageBuf = image.getPlanes()[0].getBuffer();
            final byte[] imageBytes = new byte[imageBuf.remaining()];
            imageBuf.get(imageBytes);
            image.close();
            // Low res version
            onPictureTaken(imageBytes);
        }

    };

    private ImageReader.OnImageAvailableListener mOnImageAvailableListenerHR =
            new ImageReader.OnImageAvailableListener() {
                @Override
                public void onImageAvailable(ImageReader reader) {
                    Log.d(TAG, "Image Reader Listener");
                    Image image = reader.acquireLatestImage();
                    int imgHeight = image.getHeight();
                    // get image bytes
                    ByteBuffer imageBuf = image.getPlanes()[0].getBuffer();
                    final byte[] imageBytes = new byte[imageBuf.remaining()];
                    imageBuf.get(imageBytes);
                    image.close();

                    haveHiResImage = true;
                    saveImageToLocal(imageBytes, "hiresimage.jpg");

                }
            };


    //TODO: what input?
    private void uploadHiResImage() {
        // find the existing image file
        File mfile = new File(this.getFilesDir(), "hiresimage.jpg");
        Log.d(TAG, "Trying to upload HiResImage");
        if (mfile.exists()) {
            Log.d(TAG, "HiResImage exists ...");
            mStorage.getReference().child("users").child(UID).child("img_3200x2400.jpg").putFile(android.net.Uri.fromFile(mfile)).addOnFailureListener(new OnFailureListener() {
                @Override public void onFailure(@NonNull Exception e) {
                    // Handle unsuccessful uploads
                    Log.e(TAG, "failed to upload", e);
                }
            }).addOnSuccessListener(new OnSuccessListener<UploadTask.TaskSnapshot>() {
                @Override public void onSuccess(UploadTask.TaskSnapshot taskSnapshot) {
                    // taskSnapshot.getMetadata() contains file metadata such as size, content-type, etc.
                    Log.d(TAG, "successfully uploaded the file!!! size=" + taskSnapshot.getBytesTransferred());
                    // when done uploading, then i set the field to TRUE to signal the pi to start downloading
                    mMyDatabase.child("camera/photo_pipeline_state").setValue(5);
                }
            });

        }
        else {
            Log.e(TAG, "Failed to upload hi res image");
        }
    };


    /**
     * Upload image data to Firebase as a doorbell event.
     */
    // TODO: add stuff here, i guess
    private void onPictureTaken(final byte[] imageBytes) {
        if (imageBytes != null) {
            //final DatabaseReference log = mDatabase.getReference("logs").push();
            final StorageReference imageRef = mStorage.getReference().child("users").child(UID).child("img_0640x0480.jpg");
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
                    mMyDatabase.child("camera/photo_pipeline_state").setValue(5);
                }
            }).addOnFailureListener(new OnFailureListener() {
                @Override
                public void onFailure(@NonNull Exception e) {
                    // clean up this entry
                    Log.e(TAG, "Unable to upload image to Firebase");
                    //log.removeValue();
                }
            });
        }
    }


    // Override onDestroy to correctly shut down Camera and I/O
    // TODO: flesh this out with GPIO
    @Override
    protected void onDestroy() {
        super.onDestroy();
        mCamera.shutDown();

        mCameraThread.quitSafely();
        mCloudThread.quitSafely();
        /*
        try {
            mButtonInputDriver.close();
        } catch (IOException e) {
            Log.e(TAG, "button driver error", e);
        }
        */
    }

}
