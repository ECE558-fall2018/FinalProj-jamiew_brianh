package edu.jkw7.pdx.web.finalproject_pi;

import android.app.Activity;
import android.os.Bundle;
import android.util.Log;

import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.database.DatabaseException;
import com.google.firebase.database.DatabaseReference;
import com.google.firebase.database.FirebaseDatabase;

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
public class MainActivity extends Activity {

    // Tag for logging
    public static final String TAG = "MAIN_ACTIVITY";

    private String UID;

    private DatabaseReference mDatabase;
    private DatabaseReference piConnectRef;
    private FirebaseAuth mAuth;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        Log.d(TAG, "Made it to main activity");

        try{
            //Log.d(TAG, "Inside of try block ...");
            mDatabase = FirebaseDatabase.getInstance().getReference();
            //Log.d(TAG, "Got reference to Firebase");
            mAuth = FirebaseAuth.getInstance();
            //Log.d(TAG, "Set up Firebase Authorization");
            UID = mAuth.getCurrentUser().getUid();
            Log.d(TAG, "Current user is: " + UID);
            //mDatabase.child(DATA_PI2APP).child(IN_ADC3).setValue(new_adc1);
            UID = mAuth.getCurrentUser().getUid();
            piConnectRef = mDatabase.child("users").child(UID).child("pi_connected");
            piConnectRef.onDisconnect().setValue(false);
            piConnectRef.setValue(true);
            Log.d(TAG, "Set value of pi_connected to true to show app status");


        }
        catch (DatabaseException de) {
            Log.e(TAG, "Exception when accessing database: ", de);
        }


    }
}
