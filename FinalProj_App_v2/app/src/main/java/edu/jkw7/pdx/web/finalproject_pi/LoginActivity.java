package edu.jkw7.pdx.web.finalproject_pi;

import android.animation.Animator;
import android.animation.AnimatorListenerAdapter;
import android.annotation.TargetApi;
import android.app.Activity;
import android.app.LoaderManager.LoaderCallbacks;
import android.content.CursorLoader;
import android.content.Intent;
import android.content.Loader;
import android.database.Cursor;
import android.net.Uri;
import android.os.AsyncTask;
import android.os.Build;
import android.os.Bundle;
import android.provider.ContactsContract;
import android.support.annotation.NonNull;
import android.text.TextUtils;
import android.util.Log;
import android.view.KeyEvent;
import android.view.View;
import android.view.View.OnClickListener;
import android.view.inputmethod.EditorInfo;
import android.widget.Button;
import android.widget.EditText;
import android.widget.TextView;
import android.widget.Toast;

import com.google.android.gms.tasks.OnCompleteListener;
import com.google.android.gms.tasks.Task;
import com.google.firebase.auth.AuthResult;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;
import com.google.firebase.database.*;
import com.google.firebase.iid.FirebaseInstanceId;
import com.google.firebase.iid.InstanceIdResult;

import java.util.ArrayList;
import java.util.List;

/**
 * Activity to log in to Google Firebase real-time database.
 *      -Created from the Android Studio standard "LoginActivity"
 * Jamie Williams - updated 12/2/2018
 */
public class LoginActivity extends Activity implements LoaderCallbacks<Cursor> {

    // Tag for logging
    public static final String TAG = "LOGIN_ACTIVITY";

    // Cheat email and password
    private String cheat_email = "admin@pdx.edu";
    private String cheat_password = "password";

    // String for shared preferences
    public static final String FILE_PREFS = "edu.jkw7.pdx.web.finalproject_pi" + "sharedprefs";
    // Firebase References: Database and user
    private FirebaseAuth mAuth;
    private DatabaseReference mDatabase;

    /**
     * Id to identity READ_CONTACTS permission request.
     */
    private static final int REQUEST_READ_CONTACTS = 0;


     /* Keep track of the login task to ensure we can cancel it if requested.
     */
    private UserLoginTask mAuthTask = null;

    // UI references.
    private EditText mEmailView;
    private EditText mPasswordView;
    private View mProgressView;
    private View mLoginFormView;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_login);
        // Set up the login form.
        mEmailView = (EditText) findViewById(R.id.email);
        //populateAutoComplete(); TODO: remove

        // Create references to Firebase database
        mAuth = FirebaseAuth.getInstance();
        mDatabase = FirebaseDatabase.getInstance().getReference();

        mPasswordView = (EditText) findViewById(R.id.password);
        mPasswordView.setOnEditorActionListener(new TextView.OnEditorActionListener() {
            @Override
            public boolean onEditorAction(TextView textView, int id, KeyEvent keyEvent) {
                if (id == EditorInfo.IME_ACTION_DONE || id == EditorInfo.IME_NULL) {
                    attemptLogin();
                    return true;
                }
                return false;
            }
        });

        Button mEmailSignInButton = (Button) findViewById(R.id.email_sign_in_button);
        mEmailSignInButton.setOnClickListener(new OnClickListener() {
            @Override
            public void onClick(View view) {
                attemptLogin();
            }
        });

        mLoginFormView = findViewById(R.id.login_form);
        mProgressView = findViewById(R.id.login_progress);

        //TODO: And here we cheat:
        //TODO:***********************
        mAuthTask = new UserLoginTask(cheat_email, cheat_password);
        mAuthTask.execute((Void) null);
    }

    // function: launch next activity
    // TODO: is this right?
    private void proceedToApp() {
        Intent next = new Intent(LoginActivity.this, MainActivity.class);
        // add some extras?
        startActivity(next);
        finish();
    }

    /**
     * Attempts to sign in or register the account specified by the login form.
     * If there are form errors (invalid email, missing fields, etc.), the
     * errors are presented and no actual login attempt is made.
     */
    private void attemptLogin() {
        if (mAuthTask != null) {
            return;
        }

        // Reset errors.
        mEmailView.setError(null);
        mPasswordView.setError(null);

        // Store values at the time of the login attempt.
        String email = mEmailView.getText().toString();
        String password = mPasswordView.getText().toString();

        boolean cancel = false;
        View focusView = null;

        // Check for a valid password, if the user entered one.
        if (!TextUtils.isEmpty(password) && !isPasswordValid(password)) {
            mPasswordView.setError(getString(R.string.error_invalid_password));
            focusView = mPasswordView;
            cancel = true;
        }

        // Check for a valid email address.
        if (TextUtils.isEmpty(email)) {
            mEmailView.setError(getString(R.string.error_field_required));
            focusView = mEmailView;
            cancel = true;
        } else if (!isEmailValid(email)) {
            mEmailView.setError(getString(R.string.error_invalid_email));
            focusView = mEmailView;
            cancel = true;
        }

        if (cancel) {
            // There was an error; don't attempt login and focus the first
            // form field with an error.
            focusView.requestFocus();
        } else {
            // Show a progress spinner, and kick off a background task to
            // perform the user login attempt.
            Log.d(TAG, "signing in with email=" + email + ", pass=" + password);
            showProgress(true);
            mAuthTask = new UserLoginTask(email, password);
            mAuthTask.execute((Void) null);

            // TODO: Or do I add my completion code here?
        }
    }

    private boolean isEmailValid(String email) {
        //TODO: Replace this with your own logic
        return email.contains("@");
    }

    private boolean isPasswordValid(String password) {
        //TODO: Replace this with your own logic
        return password.length() > 5;
    }

    /**
     * Shows the progress UI and hides the login form.
     */
    @TargetApi(Build.VERSION_CODES.HONEYCOMB_MR2)
    private void showProgress(final boolean show) {
        // On Honeycomb MR2 we have the ViewPropertyAnimator APIs, which allow
        // for very easy animations. If available, use these APIs to fade-in
        // the progress spinner.
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.HONEYCOMB_MR2) {
            int shortAnimTime = getResources().getInteger(android.R.integer.config_shortAnimTime);

            mLoginFormView.setVisibility(show ? View.GONE : View.VISIBLE);
            mLoginFormView.animate().setDuration(shortAnimTime).alpha(
                    show ? 0 : 1).setListener(new AnimatorListenerAdapter() {
                @Override
                public void onAnimationEnd(Animator animation) {
                    mLoginFormView.setVisibility(show ? View.GONE : View.VISIBLE);
                }
            });

            mProgressView.setVisibility(show ? View.VISIBLE : View.GONE);
            mProgressView.animate().setDuration(shortAnimTime).alpha(
                    show ? 1 : 0).setListener(new AnimatorListenerAdapter() {
                @Override
                public void onAnimationEnd(Animator animation) {
                    mProgressView.setVisibility(show ? View.VISIBLE : View.GONE);
                }
            });
        } else {
            // The ViewPropertyAnimator APIs are not available, so simply show
            // and hide the relevant UI components.
            mProgressView.setVisibility(show ? View.VISIBLE : View.GONE);
            mLoginFormView.setVisibility(show ? View.GONE : View.VISIBLE);
        }
    }

    @Override
    public Loader<Cursor> onCreateLoader(int i, Bundle bundle) {
        return new CursorLoader(this,
                // Retrieve data rows for the device user's 'profile' contact.
                Uri.withAppendedPath(ContactsContract.Profile.CONTENT_URI,
                        ContactsContract.Contacts.Data.CONTENT_DIRECTORY), ProfileQuery.PROJECTION,

                // Select only email addresses.
                ContactsContract.Contacts.Data.MIMETYPE +
                        " = ?", new String[]{ContactsContract.CommonDataKinds.Email
                .CONTENT_ITEM_TYPE},

                // Show primary email addresses first. Note that there won't be
                // a primary email address if the user hasn't specified one.
                ContactsContract.Contacts.Data.IS_PRIMARY + " DESC");
    }

    @Override
    public void onLoadFinished(Loader<Cursor> cursorLoader, Cursor cursor) {
        List<String> emails = new ArrayList<>();
        cursor.moveToFirst();
        while (!cursor.isAfterLast()) {
            emails.add(cursor.getString(ProfileQuery.ADDRESS));
            cursor.moveToNext();
        }
    }

    @Override
    public void onLoaderReset(Loader<Cursor> cursorLoader) {

    }


    private interface ProfileQuery {
        String[] PROJECTION = {
                ContactsContract.CommonDataKinds.Email.ADDRESS,
                ContactsContract.CommonDataKinds.Email.IS_PRIMARY,
        };

        int ADDRESS = 0;
        int IS_PRIMARY = 1;
    }

    /**
     * Represents an asynchronous login/registration task used to authenticate
     * the user.
     */
    public class UserLoginTask extends AsyncTask<Void, Void, Boolean> {

        private final String mEmail;
        private final String mPassword;

        UserLoginTask(String email, String password) {
            mEmail = email;
            mPassword = password;
        }

        @Override
        protected Boolean doInBackground(Void... params) {
            // TODO: attempt authentication against a network service.

            try {
                mAuth.signInWithEmailAndPassword(mEmail, mPassword).addOnCompleteListener(new OnCompleteListener<AuthResult>() {
                    @Override public void onComplete(@NonNull Task<AuthResult> task) {
                        if (task.isSuccessful()) {
                            // Sign in success, update UI with the signed-in user's information
                            Log.d(TAG, "signInWithEmail:success");
                            Log.d(TAG, "UUID = " + mAuth.getCurrentUser().getUid());

                            // save successful username and pass to sharedprefs, for future logins
                            /* TODO: Optional implementation feature - not finished
                            SharedPreferences prefs = getSharedPreferences(FILE_PREFS, Context.MODE_PRIVATE);
                            SharedPreferences.Editor e = prefs.edit();
                            e.putString(Keys.KEY_PASS, pass_f);
                            e.putString(Keys.KEY_USER, username_f);
                            e.apply();
                            */

                            DatabaseReference userNode = mDatabase.child("users").child(mAuth.getCurrentUser().getUid());
                            userNode.addListenerForSingleValueEvent(new ValueEventListener() {
                                @Override public void onDataChange(@NonNull DataSnapshot ds) {
                                    // verify that the node exists... if it doesn't exist, then create default fields for everything it will need!
                                    // if the 'email' field is null then assume the whole thing is missing
                                    if(ds.child("email").getValue() == null) {
                                        // create the node with default values
                                        DatabaseReference userNode = mDatabase.child("users").child(mAuth.getCurrentUser().getUid());
                                        // dont need to create email or apptoken, those created below
                                        //userNode.child("email");
                                        //userNode.child("apptoken");
                                        // TODO: replace these with constants in Keys file
                                        /* TODO: ARE these wrong??
                                        userNode.child("pi_timestamp").setValue("err");
                                        userNode.child("pi_armed").setValue(false);
                                        userNode.child("pi_connected").setValue(false);
                                        userNode.child("timeout_threshold").setValue(10);
                                        userNode.child("control").child("toggle_pi_armed").setValue(false);
                                        userNode.child("camera").child("photo_pipeline_state").setValue(0);
                                        // TODO: decide how sound communication is structured
                                        userNode.child("sound").child("done_uploading_new").setValue(false);
                                        userNode.child("sound").child("done_downloading_new").setValue(false);
                                        // TODO: decide how voip is structured, and if using it
                                        userNode.child("voip").child("app_addr").setValue("err");
                                        userNode.child("voip").child("app_username").setValue(false);
                                        userNode.child("voip").child("app_password").setValue(false);
                                        userNode.child("voip").child("pi_addr").setValue("err");
                                        userNode.child("voip").child("pi_username").setValue(false);
                                        userNode.child("voip").child("pi_password").setValue(false);
                                        // TODO: create empty sensor config object here
                                        userNode.child("sensor_config").child("sensor_config_obj").setValue("???");
                                        */
                                    }

                                    // once I have logged in and know my UUID then I should try to send the MessagingService token to the database
                                    // manually get the token
                                    //FirebaseInstanceId.getInstance().getInstanceId().addOnCompleteListener(afterGetToken);
                                    //TODO: Move this to main app
                                    //mDatabase.child("users").child(mAuth.getCurrentUser().getUid()).child("pi_connected").setValue(true);
                                    Log.d(TAG, "Have correctly logged in, moving to main activity");
                                    proceedToApp();
                                }
                                @Override public void onCancelled(@NonNull DatabaseError de) {
                                    // Failed to read value, not sure how or what to do about it
                                    Log.d(TAG, "firebase error: failed to get snapshot??", de.toException());
                                }
                            });
                        } else {
                            // If sign in fails, display a message to the user.
                            Log.w(TAG, "signInWithEmail:failure", task.getException());
                            Toast.makeText(LoginActivity.this, "Failed to log in to Firebase", Toast.LENGTH_SHORT).show();
                        }

                    }
                });
            } catch (Exception ex) {
                Log.e(TAG, "Threw an exception while logging in: ", ex);
                return false;
            }

            /* TODO: delete this
            for (String credential : DUMMY_CREDENTIALS) {
                String[] pieces = credential.split(":");
                if (pieces[0].equals(mEmail)) {
                    // Account exists, return true if the password matches.
                    return pieces[1].equals(mPassword);
                }
            }
            */

            // TODO: register the new account here.
            return true;
        }

        @Override
        protected void onPostExecute(final Boolean success) {
            mAuthTask = null;
            showProgress(false);

            if (success) {
                // TODO: I guess add code to call next activity here?





                finish();
            } else {
                mPasswordView.setError(getString(R.string.error_incorrect_password));
                mPasswordView.requestFocus();
            }
        }

        @Override
        protected void onCancelled() {
            mAuthTask = null;
            showProgress(false);
        }
    } // End of user login class

} // End of login activity class

