package edu.pdx.ece558f18.bhenson.finalproj_app;

import android.content.Intent;
import android.support.v7.app.AppCompatActivity;
import android.os.Bundle;
import android.util.Log;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;

public class LoginActivity extends AppCompatActivity {
    public static final String TAG = "SECURITY_Login:";

    // references to relevant UI elements
    private Button mSubmitButton;
    private EditText mUsernameBox;
    private EditText mPasswordBox;

    // TODO: anything needed for authentication, if i ever get to it



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
                if(attemptLogin(username, pass)) {
                    proceedToApp();
                }
            }
        });


        // 3: other init!

        // read the sharedprefs
        // if they exist, then try to login

        // uncomment this function to always skip the login page
        // causes very slight hitch but whatever
        proceedToApp();

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
    // TODO: if authentication is implemented, somehow get the login token into the pager activity
    private void proceedToApp() {
        Intent next = new Intent(LoginActivity.this, PagerActivity.class);
        // add some extras?
        startActivity(next);
        finish();
        return;
    }


    // function: attempt login, if successful then save the user/pass
    // TODO: make this work better if I actually implement authentication
    private boolean attemptLogin(String username, String pass) {
        // set up the stuff needed to try firebase authentication
        // actually perform the credential check
        // question: where is a new account actually created? here/pi/online/??

        if(true) {
            // somehow store login token/handle/reference/whatever
            // save successful username and pass to sharedprefs
            return true;
        } else {
            // login failure:
            // consider erasing the username/pass?
            // definitely create some kind of toast
            return false;
        }
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
