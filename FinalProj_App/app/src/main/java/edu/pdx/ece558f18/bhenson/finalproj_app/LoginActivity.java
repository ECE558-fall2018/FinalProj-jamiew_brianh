package edu.pdx.ece558f18.bhenson.finalproj_app;

import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.support.annotation.NonNull;
import android.support.v7.app.AppCompatActivity;
import android.util.Log;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.Toast;
import com.google.android.gms.tasks.OnCompleteListener;
import com.google.android.gms.tasks.Task;
import com.google.firebase.auth.AuthResult;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;
import com.google.firebase.database.DatabaseReference;
import com.google.firebase.database.FirebaseDatabase;
import com.google.firebase.iid.FirebaseInstanceId;
import com.google.firebase.iid.InstanceIdResult;

public class LoginActivity extends AppCompatActivity {
    public static final String TAG = "SECURITY_Login:";
    public static final boolean AUTOLOGIN = false;

    // references to relevant UI elements
    private Button mSubmitButton;
    private EditText mUsernameBox;
    private EditText mPasswordBox;

    // TODO: anything needed for authentication, if i ever get to it
    private FirebaseAuth mAuth;
    private DatabaseReference mDatabase;


    @Override
    protected void onCreate(Bundle saved) {
        super.onCreate(saved);
        setContentView(R.layout.activity_login);
        Log.d(TAG, "onCreate()");

        // 1: grab & store UI references
        mSubmitButton = (Button) findViewById(R.id.login_submit_button);
        mUsernameBox = (EditText) findViewById(R.id.usernamebox);
        mPasswordBox = (EditText) findViewById(R.id.passwordbox);
        // TODO: something with the progressbar? or just delete it?

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
        // be handled here. If you want a different intent fired, set the click_action
        // field of the notification message to the desired intent. The launcher intent
        // is used when no click_action is specified.
        //
        // Handle possible data accompanying notification message.
        // [START handle_data_extras]
        if (getIntent().getExtras() != null) {
            for (String key : getIntent().getExtras().keySet()) {
                Object value = getIntent().getExtras().get(key);
                Log.d(TAG, "Key: " + key + " Value: " + value);
            }
        }
        // [END handle_data_extras]


        // TODO: determine if this is actually needed or not???
        Intent i = new Intent(this, MyFirebaseMessagingService.class);
        startService(i);




        // manually get the token
        // TODO: remove this block
        FirebaseInstanceId.getInstance().getInstanceId().addOnCompleteListener(new OnCompleteListener<InstanceIdResult>() {
            @Override public void onComplete(@NonNull Task<InstanceIdResult> task) {
                if (!task.isSuccessful()) {
                    Log.w(TAG, "getInstanceId failed", task.getException());
                    return;
                }

                // Get new Instance ID token
                String token = task.getResult().getToken();

                // Log and toast
                //String msg = getString(R.string.msg_token_fmt, token);
                Log.d(TAG, token);
                Toast.makeText(LoginActivity.this, token, Toast.LENGTH_SHORT).show();
            }
        });

        // uncomment this function to always skip the login page
        // causes very slight hitch but whatever
        //proceedToApp();

    }


    /*
    behavior:
    launch
    if there are shared preferences saved, get them and try to log in with them
    if successful(?) then pass handle to the next activity(????) and launch it and finish myself
    else if there are no preferences, wait for user to enter stuff
    when button pressed, do the above stuff
    if there is an error, then clear the boxes(?) and display toast with error
     */


    // function: launch next activity
    private void proceedToApp() {
        Intent next = new Intent(LoginActivity.this, PagerActivity.class);
        // add some extras?
        startActivity(next);
        finish();
        return;
    }


    // function: attempt login, if successful then save the user/pass
    private void attemptLogin(String username, String pass) {
        // these need to be final so they can be accessed by the inner class, apparently
        final String username_f = username;
        final String pass_f = pass;

        // firebase requires that it be a plausible "email" rather than just a username
        String email = username + Keys.EMAIL_SUFFIX; // this needs to actually look like an email for some reason

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

        // actually perform the credential check
        mAuth.signInWithEmailAndPassword(email, pass).addOnCompleteListener(this, new OnCompleteListener<AuthResult>() {
            @Override public void onComplete(@NonNull Task<AuthResult> task) {
                if (task.isSuccessful()) {
                    // Sign in success, update UI with the signed-in user's information
                    Log.d(TAG, "signInWithEmail:success");
                    Log.d(TAG, "UUID = " + mAuth.getCurrentUser().getUid());

                    // save successful username and pass to sharedprefs, for future logins
                    SharedPreferences prefs = getSharedPreferences(Keys.FILE_PREFS, Context.MODE_PRIVATE);
                    SharedPreferences.Editor e = prefs.edit();
                    e.putString(Keys.KEY_PASS, pass_f);
                    e.putString(Keys.KEY_USER, username_f);
                    e.apply();
                    // once I have logged in and know my UUID then I should try to send the MessagingService token to the database
                    // manually get the token
                    FirebaseInstanceId.getInstance().getInstanceId().addOnCompleteListener(new OnCompleteListener<InstanceIdResult>() {
                        @Override public void onComplete(@NonNull Task<InstanceIdResult> task) {
                            if (!task.isSuccessful()) {
                                Log.w(TAG, "getInstanceId failed", task.getException());
                                return;
                            }

                            // Get the Instance ID token
                            String token = task.getResult().getToken();
                            Log.d(TAG, "token= " + token);

                            // store the token in the database, at the path "users/<uuid>/apptoken"
                            DatabaseReference temp1 = mDatabase.child("users");
                            DatabaseReference temp2 = temp1.child(mAuth.getCurrentUser().getUid());
                            DatabaseReference temp3 = temp2.child("apptoken");
                            temp3.setValue(token);

                            // NOW i'm finally done and ready to move on
                            proceedToApp();
                        }
                    });
                } else {
                    // If sign in fails, display a message to the user.
                    Log.w(TAG, "signInWithEmail:failure", task.getException());
                    // TODO: make this in strings.xml
                    Toast.makeText(LoginActivity.this, R.string.login_failure, Toast.LENGTH_SHORT).show();
                }

                // re-enable these elements
                mSubmitButton.setEnabled(true);
                mUsernameBox.setEnabled(true);
                mPasswordBox.setEnabled(true);
            }
        });
    }




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
        // TODO: make autologin a user-configurable option?
        if(AUTOLOGIN) {
            FirebaseUser currentUser = mAuth.getCurrentUser();
            if (currentUser != null) {
                // somehow already logged in, not sure how but I'll accept it!
                Log.d(TAG, "FirebaseAuth still valid, proceeding to pager");
                proceedToApp();
                return;
            }
            // if not still logged in, check the shared preferences
            SharedPreferences prefs = getSharedPreferences(Keys.FILE_PREFS, Context.MODE_PRIVATE);
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
