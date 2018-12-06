package edu.jkw7.pdx.web.finalproject_pi;

import android.Manifest;
import android.app.Activity;
import android.content.pm.PackageManager;
import android.media.Image;
import android.media.ImageReader;
import android.net.Uri;
import android.os.Bundle;
import android.os.Handler;
import android.os.HandlerThread;
import android.support.annotation.NonNull;
import android.util.Log;
//import android.view.KeyEvent;


import com.google.android.gms.tasks.OnFailureListener;
import com.google.android.gms.tasks.OnSuccessListener;
import com.google.firebase.auth.FirebaseAuth;
//import com.google.android.things.contrib.driver.button.Button;
//import com.google.android.things.contrib.driver.button.ButtonInputDriver;
import com.google.firebase.database.DatabaseException;
import com.google.firebase.database.DatabaseReference;
import com.google.firebase.database.FirebaseDatabase;
//import com.google.firebase.database.ServerValue;
import com.google.firebase.storage.FirebaseStorage;
import com.google.firebase.storage.StorageReference;
import com.google.firebase.storage.UploadTask;

//import java.io.IOException;
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
    private String UID;
    private DatabaseReference mDatabase = FirebaseDatabase.getInstance().getReference();
    private DatabaseReference piConnectRef;
    private FirebaseAuth mAuth = FirebaseAuth.getInstance();

    // Variables for handling Pi Camera
    private DoorbellCamera mCamera;
    private Handler mCameraHandler;
    private HandlerThread mCameraThread;

    // Variables for handling Firebase Cloud Storage
    private FirebaseStorage mStorage = FirebaseStorage.getInstance();
    private Handler mCloudHandler;
    private HandlerThread mCloudThread;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);


        Log.d(TAG, "Made it to main activity");

        // Get access to Firebase Database
        try {
            UID = mAuth.getCurrentUser().getUid();
            Log.d(TAG, "Current user is: " + UID);
            //mDatabase.child(DATA_PI2APP).child(IN_ADC3).setValue(new_adc1);
            UID = mAuth.getCurrentUser().getUid();
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
        mCamera.initializeCamera(this, mCameraHandler, mOnImageAvailableListener);

        Log.d(TAG, "Taking picture...");
        mCamera.takePicture();
    }









    /**
     * Listener for new camera images.
     */
    private ImageReader.OnImageAvailableListener mOnImageAvailableListener =
            new ImageReader.OnImageAvailableListener() {
        @Override
        public void onImageAvailable(ImageReader reader) {
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
    // TODO: add stuff here, i guess
    private void onPictureTaken(final byte[] imageBytes) {
        if (imageBytes != null) {
            //final DatabaseReference log = mDatabase.getReference("logs").push();
            final StorageReference imageRef = mStorage.getReference().child("users").child(UID).child("test_pi_image.jpeg");

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
