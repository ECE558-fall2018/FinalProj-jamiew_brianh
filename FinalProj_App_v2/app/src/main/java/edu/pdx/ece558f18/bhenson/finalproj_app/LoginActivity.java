package edu.pdx.ece558f18.bhenson.finalproj_app;

import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.os.Handler;
import android.support.annotation.NonNull;
import android.support.v7.app.AppCompatActivity;
import android.util.Log;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ProgressBar;
import android.widget.Toast;
import com.google.android.gms.tasks.OnCompleteListener;
import com.google.android.gms.tasks.Task;
import com.google.firebase.auth.AuthResult;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;
import com.google.firebase.database.*;
import com.google.firebase.iid.FirebaseInstanceId;
import com.google.firebase.iid.InstanceIdResult;

public class LoginActivity extends AppCompatActivity {
    public static final String TAG = "SEC_Login";

    // references to relevant UI elements
    private Button mSubmitButton;
    private EditText mUsernameBox;
    private EditText mPasswordBox;
    private ProgressBar mProgressBar;

    public Handler mHandler = new Handler();

    private FirebaseAuth mAuth;
    private DatabaseReference mDatabase;

    private int mNextPage = 1;


    @Override
    protected void onCreate(Bundle saved) {
        super.onCreate(saved);
        setContentView(R.layout.activity_login);
        Log.d(TAG, "onCreate()");

        // 1: grab & store UI references
        mSubmitButton = (Button) findViewById(R.id.login_submit_button);
        mUsernameBox = (EditText) findViewById(R.id.usernamebox);
        mPasswordBox = (EditText) findViewById(R.id.passwordbox);
        mProgressBar = (ProgressBar) findViewById(R.id.login_progress);

        // 2: attach button listeners
        // attach a listener to the login button that launches the pager activity, or just jump to it immediately?
        mSubmitButton.setOnClickListener(new View.OnClickListener() {
            @Override public void onClick(View v) {
                String username = mUsernameBox.getText().toString();
                String pass = mPasswordBox.getText().toString();
                attemptLogin(username, pass);
            }
        });


        // 3: other init!
        mAuth = FirebaseAuth.getInstance();
        mDatabase = FirebaseDatabase.getInstance().getReference();



        // If a notification message is tapped, any data accompanying the notification
        // message is available in the intent extras. In this sample the launcher
        // intent is fired when the notification is tapped, so any accompanying data would
        // be handled here.
        //
        // [START handle_data_extras]
        if (getIntent().getExtras() != null) {
            for (String key : getIntent().getExtras().keySet()) {
                Object value = getIntent().getExtras().get(key);
                Log.d(TAG, "Key: " + key + " Value: " + value);
            }
            int z = -1;
            // Keys.KEY_GOTOPAGE is the only extra i care about
            // it may hold an int(created by clicking a notification i created)
            // it may hold an int disguised as a string (created by notification that was received while app was inactive)
            // it may not exist (not started by clicking on a notification)
            try {
                z = Integer.parseInt(getIntent().getStringExtra(Keys.KEY_GOTOPAGE));
            } catch(NumberFormatException nfe) {
                Log.d(TAG, "couldn't parse as a string, trying as int");
                z = getIntent().getIntExtra(Keys.KEY_GOTOPAGE, -1);
            }
            Log.d(TAG, "z=" + z);
            if(z >= 0 && z <= 2) {
                // if there was a value, then save it (will be put into the intent handed to the pager activity)
                mNextPage = z;
            }
        }
        // [END handle_data_extras]

    }


    // function: launch next activity
    private void proceedToApp() {
        Intent next = new Intent(LoginActivity.this, PagerActivity.class);
        // add the extra I got from the launching intent
        next.putExtra(Keys.KEY_GOTOPAGE, mNextPage);
        startActivity(next);
        // finish myself so that the back button won't return to this login page (want this whole app to work without any back-stack nonsense)
        finish();
        return;
    }

    // NOTE: any calls to the gui either do nothing or actually crash when made inside the sign-in and get-token listeners (apparently thats a separate thread? idk)
    // however putting those gui updates into simple Runnables that immediately run on the main thread will work
    // so that's why i have these ugly things
    // NOTE2: the Database callback functions do support UI calls (usually)
    private Runnable gui50 = new Runnable() {@Override public void run() { mProgressBar.setProgress(50); }};
    private Runnable gui75 = new Runnable() {@Override public void run() { mProgressBar.setProgress(75); }};
    private Runnable gui99 = new Runnable() {@Override public void run() { mProgressBar.setProgress(99); }};
    private Runnable gui1 = new Runnable() {@Override public void run() { mProgressBar.setVisibility(View.INVISIBLE); }};


    // function: attempt login, if successful then save the user/pass
    private void attemptLogin(String username, String pass) {
        // these need to be final so they can be accessed by the inner class, apparently
        final String username_f = username;
        final String pass_f = pass;

        // firebase requires that it be a plausible "email" rather than just a username
        String email = username + Keys.EMAIL_SUFFIX;

        // firebase requires that passwords be 6 chars or longer
        if(pass.length() < 6) {
            Toast.makeText(LoginActivity.this, "Password too short, must be >= 6 chars", Toast.LENGTH_SHORT).show();
            return;
        }

        Log.d(TAG, "signing in with email=" + email + ", pass=" + pass);
        // disable the UI elements for the moment it takes to check the credentials
        mSubmitButton.setEnabled(false);
        mUsernameBox.setEnabled(false);
        mPasswordBox.setEnabled(false);
        // enable the progress bar
        mProgressBar.setVisibility(View.VISIBLE);
        mProgressBar.setProgress(25);

        // actually perform the credential check
        mAuth.signInWithEmailAndPassword(email, pass).addOnCompleteListener(this, new OnCompleteListener<AuthResult>() {
            @Override public void onComplete(@NonNull Task<AuthResult> task) {
                if (task.isSuccessful()) {
                    //mProgressBar.setProgress(50);
                    mHandler.post(gui50);
                    // Sign in success, update UI with the signed-in user's information
                    Log.d(TAG, "signInWithEmail:success");
                    Log.d(TAG, "UUID = " + mAuth.getCurrentUser().getUid());

                    // save successful username and pass to sharedprefs, for future logins
                    SharedPreferences prefs = getSharedPreferences(Keys.FILE_PREFS, Context.MODE_PRIVATE);
                    SharedPreferences.Editor e = prefs.edit();
                    e.putString(Keys.KEY_PASS, pass_f);
                    e.putString(Keys.KEY_USER, username_f);
                    e.apply();

                    DatabaseReference userNode = mDatabase.child(Keys.DB_TOPFOLDER).child(mAuth.getCurrentUser().getUid());
                    userNode.addListenerForSingleValueEvent(mDBVerifyNodeExistsAndCreateIfMissing);
                } else {
                    // If sign in fails, display a message to the user.
                    Log.w(TAG, "signInWithEmail:failure", task.getException());
                    Toast.makeText(LoginActivity.this, R.string.login_failure, Toast.LENGTH_SHORT).show();
                    //mProgressBar.setVisibility(View.INVISIBLE);
                    mHandler.post(gui1);
                }

                // re-enable these elements
                mSubmitButton.setEnabled(true);
                mUsernameBox.setEnabled(true);
                mPasswordBox.setEnabled(true);
            }
        });
    }


    private ValueEventListener mDBVerifyNodeExistsAndCreateIfMissing = new ValueEventListener() {
        @Override public void onDataChange(@NonNull DataSnapshot ds) {
            //mProgressBar.setProgress(75);
            mHandler.post(gui75);
            // verify that the node exists... if it doesn't exist, then create default fields for everything it will need!
            // if the 'email' field is null then assume the whole thing is missing
            if(ds.child(Keys.DB_EMAIL).getValue() == null) {
                initDBNode();
            }

            // once I have logged in and know my UUID then I should try to send the MessagingService token to the database
            // manually get the token

            // in the app, use:
            FirebaseInstanceId.getInstance().getInstanceId().addOnCompleteListener(mGetTokenListener);
            // in the pi, use:
            //proceedToApp();
            // re-enable these elements
            mSubmitButton.setEnabled(true);
            mUsernameBox.setEnabled(true);
            mPasswordBox.setEnabled(true);
            //mProgressBar.setVisibility(View.INVISIBLE);

        }
        @Override public void onCancelled(@NonNull DatabaseError de) {
            // Failed to read value, not sure how or what to do about it
            Log.d(TAG, "firebase error: failed to get snapshot??", de.toException());
        }
    };

    private void initDBNode() {
        Log.d(TAG, "creating default database structures");
        // create the node with default values
        DatabaseReference userNode = mDatabase.child(Keys.DB_TOPFOLDER).child(mAuth.getCurrentUser().getUid());
        // dont need to create email or apptoken, those created below
        //userNode.child(Keys.DB_EMAIL);
        //userNode.child(Keys.DB_APPTOKEN);
        //userNode.child(Keys.DB_TIMESTAMP).setValue("err");
        userNode.child(Keys.DB_ARMED).setValue(false);
        userNode.child(Keys.DB_TRIGGERED).setValue(false);
        userNode.child(Keys.DB_CONNECTED).setValue(false);
        //userNode.child(Keys.DB_TIMEOUT).setValue(10);
        userNode.child(Keys.DB_CAMERA_STATE).setValue(0);
        userNode.child(Keys.DB_VOIP_REMOTE_URI).setValue("-");
        SensorListObj slo = new SensorListObj();
        userNode.child(Keys.DB_SENSOR_CONFIG).setValue(slo.toString());
        userNode.child(Keys.DB_SOUND).setValue(false);
    }

    private OnCompleteListener<InstanceIdResult> mGetTokenListener = new OnCompleteListener<InstanceIdResult>() {
        @Override public void onComplete(@NonNull Task<InstanceIdResult> task) {
            if (!task.isSuccessful()) {
                Log.w(TAG, "getInstanceId failed", task.getException());
                return;
            }
            //mProgressBar.setProgress(99);
            mHandler.post(gui99);
            // Get the Instance ID token
            String token = task.getResult().getToken();
            Log.d(TAG, "token= " + token);

            // store the token in the database, at the path "users/<uuid>/apptoken"
            DatabaseReference userNode = mDatabase.child(Keys.DB_TOPFOLDER).child(mAuth.getCurrentUser().getUid());
            DatabaseReference temp3 = userNode.child(Keys.DB_APPTOKEN);
            temp3.setValue(token);
            DatabaseReference temp4 = userNode.child(Keys.DB_EMAIL);
            temp4.setValue(mAuth.getCurrentUser().getEmail());

            // NOW i'm finally done and ready to move on
            proceedToApp();
        }

    };


    // ===========================================================================================================
    // override non-lifecycle functions, actually do stuff here?

    @Override
    protected void onSaveInstanceState(Bundle outState) {
        super.onSaveInstanceState(outState);
        Log.d(TAG, "onSaveInstanceState(bundle)");
    }
    @Override
    protected void onRestoreInstanceState(Bundle savedInstanceState) {
        super.onRestoreInstanceState(savedInstanceState);
        Log.d(TAG, "onRestoreInstanceState(bundle)");
    }
    @Override
    public void onBackPressed() {
        super.onBackPressed();
        Log.d(TAG, "onBackPressed()");
    }
    // ===========================================================================================================
    // override critical lifecycle functions, mostly for logging

    @Override
    protected void onStart() {
        super.onStart();
        Log.d(TAG, "onStart()");
        SharedPreferences prefs = getSharedPreferences(Keys.FILE_PREFS, Context.MODE_PRIVATE);
        boolean autologin = prefs.getBoolean(Keys.KEY_AUTOLOGIN, Keys.DEFAULT_AUTOLOGIN);
        // has the user configured it so they automatically log in if possible?
        if(autologin) {
            FirebaseUser currentUser = mAuth.getCurrentUser();
            if (currentUser != null) {
                // somehow already logged in, not sure how but I'll accept it!
                // no need to validate stuff, if they're already logged in they went thru that whole chain once
                Log.d(TAG, "FirebaseAuth still valid, proceeding to pager");
                proceedToApp();
                return;
            }
            // if not still logged in, check the shared preferences
            String muser = prefs.getString(Keys.KEY_USER, null);
            String mpass = prefs.getString(Keys.KEY_PASS, null);
            // if I managed to find some sharedPrefs user and password, then attempt login with those!
            if(muser != null && mpass != null) {
                Log.d(TAG, "SharedPreferences contained a username and password, attempting login");
                attemptLogin(muser, mpass);
            }
        }
        // otherwise, do nothing and let the user enter what they want in human time
    }
    @Override
    protected void onRestart() {
        super.onRestart();
        Log.d(TAG, "onRestart()");
    }
    @Override
    protected void onStop() {
        super.onStop();
        Log.d(TAG, "onStop()");
    }
    @Override
    protected void onDestroy() {
        super.onDestroy();
        Log.d(TAG, "onDestroy()");
    }
    @Override
    protected void onPause() {
        super.onPause();
        Log.d(TAG, "onPause()");
    }
    @Override
    protected void onResume() {
        super.onResume();
        Log.d(TAG, "onResume()");
    }

}
