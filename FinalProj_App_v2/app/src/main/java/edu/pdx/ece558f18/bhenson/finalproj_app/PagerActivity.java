package edu.pdx.ece558f18.bhenson.finalproj_app;

import android.app.NotificationManager;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.os.Bundle;
import android.support.annotation.NonNull;
import android.support.v4.app.Fragment;
import android.support.v4.app.FragmentManager;
import android.support.v4.app.FragmentPagerAdapter;
import android.support.v4.view.ViewPager;
import android.support.v7.app.AppCompatActivity;
import android.util.Log;
import android.util.SparseArray;
import android.view.ViewGroup;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;
import com.google.firebase.database.*;

public class PagerActivity extends AppCompatActivity
        implements  ControlFragment.ControlFragmentListener,
                    CameraFragment.CameraFragmentListener {

    public static final String TAG = "SEC_Pager";

    /**
     * The {@link android.support.v4.view.PagerAdapter} that will provide
     * fragments for each of the sections. We use a
     * {@link FragmentPagerAdapter} derivative, which will keep every
     * loaded fragment in memory. If this becomes too memory intensive, it
     * may be best to switch to a
     * {@link android.support.v4.app.FragmentStatePagerAdapter}.
     */
    private SectionsPagerAdapter mSectionsPagerAdapter;

    private ViewPager mViewPager;
    private FirebaseAuth mAuth;
    private DatabaseReference mMyDatabase;
    public boolean mPiIsConnected = false;

    @Override
    protected void onCreate(Bundle saved) {
        super.onCreate(saved);
        setContentView(R.layout.activity_pager);
        Log.d(TAG, "onCreate()");

        // Create the adapter that will return a fragment for each of the three
        // primary sections of the activity.
        mSectionsPagerAdapter = new SectionsPagerAdapter(getSupportFragmentManager(), this);

        // Set up the ViewPager with the sections adapter.
        mViewPager = (ViewPager) findViewById(R.id.pager);
        mViewPager.setAdapter(mSectionsPagerAdapter);
        int z = getIntent().getIntExtra(Keys.KEY_GOTOPAGE, -1);
        if(z >= 0 && z <= 2) {
            // should never be 0 but it won't crash so i'll allow it
            mViewPager.setCurrentItem(z, false);
        } else {
            mViewPager.setCurrentItem(1, false);
        }

        mViewPager.addOnPageChangeListener(mPageChangeListener);

        // get instance of the authentication object, hopefully already logged in
        mAuth = FirebaseAuth.getInstance();

        // get instance of the database (see proj3 example for more)
        mMyDatabase = FirebaseDatabase.getInstance().getReference().child(Keys.DB_TOPFOLDER).child(mAuth.getCurrentUser().getUid());



    }


    private ViewPager.OnPageChangeListener mPageChangeListener = new  ViewPager.OnPageChangeListener() {
        @Override public void onPageScrolled(int i, float v, int i1) { }// dont care, do nothing
        @Override public void onPageScrollStateChanged(int i) { }// dont care, do nothing
        @Override public void onPageSelected(int i) {
            NotificationManager nm = (NotificationManager) getSystemService(Context.NOTIFICATION_SERVICE);
            try {
                if(i == 2) {
                    // when moving to camera page, clear the alarm notification
                    nm.cancel(Keys.ID_NOTIFY_ACTIVE);
                }
                if(i == 1) {
                    // when moving to control page, clear the disconnect notification
                    nm.cancel(Keys.ID_NOTIFY_DISCONNECT);
                }
            } catch (NullPointerException npe) {
                Log.d(TAG, "notificaiton manager somehow failed, not sure how", npe);
            }

            // when moving to control or sensor page, if VOIP is active, hang up
            if(i != 2) {
                if(mSectionsPagerAdapter.getRegisteredFragment(2) != null) {
                    CameraFragment f = (CameraFragment) mSectionsPagerAdapter.getRegisteredFragment(2);
                    f.stopRecording(false);
                }
            }
        }
    };

    // inital read of database, and also fires each time it changes
    // set the member variable to be used in teh constructor if this fires before the fragments get made
    private ValueEventListener mDBListenerPiConnected = new ValueEventListener() {
        @Override public void onDataChange(@NonNull DataSnapshot ds) {
            boolean b;
            try {
                b = ds.getValue(Boolean.class);
            } catch (NullPointerException npe) {
                Log.d(TAG, "error: field doesnt exist or has bad data, " + ds.toString(), npe);
                return;
            } catch(DatabaseException de) {
                Log.d(TAG, "error: something bad", de);
                return;
            }
            Log.d(TAG, "firebase: pi_connected state changed, now " + b);
            mPiIsConnected = b;
            // if the fragments DO exist, then call the functions to enable/disable their buttons
            // note: if the fragment is STOPPED, these functions don't crash but they do nothing
            // therefore each fragment also calls these in onStart
            if(mSectionsPagerAdapter.getRegisteredFragment(0) != null) {
                SensorListFragment f = (SensorListFragment) mSectionsPagerAdapter.getRegisteredFragment(0);
                f.updatePiConnectionState(mPiIsConnected);
            }
            if(mSectionsPagerAdapter.getRegisteredFragment(1) != null) {
                ControlFragment f = (ControlFragment) mSectionsPagerAdapter.getRegisteredFragment(1);
                f.updatePiConnectionState(mPiIsConnected);
            }
            if(mSectionsPagerAdapter.getRegisteredFragment(2) != null) {
                CameraFragment f = (CameraFragment) mSectionsPagerAdapter.getRegisteredFragment(2);
                f.updatePiConnectionState(mPiIsConnected);
            }
        }

        @Override public void onCancelled(@NonNull DatabaseError de) {
            // Failed to read value, not sure how or what to do about it
            Log.d(TAG, "firebase error: failed to get snapshot??", de.toException());
        }
    };



    // ===========================================================================================================
    // these callbacks are used by the fragments, these are needed by the interfaces

    @Override public void onRequestPermissionsResult(int requestCode, @NonNull String permissions[], @NonNull int[] grantResults) {
        switch (requestCode) {
            case Keys.PERM_REQ_WRITE_EXTERNAL: {
                // If request is cancelled, the result arrays are empty.
                if (grantResults.length > 0 && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                    // permission was granted, yay! Proceed with the thing
                    Log.d(TAG, "permission accepted for WRITE_EXTRNAL");
                    if(mSectionsPagerAdapter.getRegisteredFragment(2) != null) {
                        ((CameraFragment) mSectionsPagerAdapter.getRegisteredFragment(2)).attemptToDownloadHires();
                    }
                } else {
                    // permission denied, boo! do nothing I suppose
                    Log.d(TAG, "permission denied for WRITE_EXTRNAL");
                }
                return;
            }

            case Keys.PERM_REQ_RECORD_AUDIO: {
                // If request is cancelled, the result arrays are empty.
                if (grantResults.length > 0 && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                    // permission was granted, yay!
                    Log.d(TAG, "permission accepted for RECORD_AUDIO");
                    if(mSectionsPagerAdapter.getRegisteredFragment(2) != null) {
                        ((CameraFragment) mSectionsPagerAdapter.getRegisteredFragment(2)).attemptToRecord();
                    }
                } else {
                    // permission denied, boo! do nothing I suppose
                    Log.d(TAG, "permission denied for RECORD_AUDIO");
                }
                return;
            }

            /*
            case Keys.PERM_REQ_USE_SIP: {
                // If request is cancelled, the result arrays are empty.
                if (grantResults.length > 0 && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                    // permission was granted, yay!
                    Log.d(TAG, "permission accepted for USE_SIP");
                    if(mSectionsPagerAdapter.getRegisteredFragment(2) != null) {
                        ((CameraFragment) mSectionsPagerAdapter.getRegisteredFragment(2)).attemptToCall();
                    }
                } else {
                    // permission denied, boo! do nothing I suppose
                    Log.d(TAG, "permission denied for USE_SIP");
                }
                return;
            }

            case Keys.PERM_REQ_INTERNET: {
                // If request is cancelled, the result arrays are empty.
                if (grantResults.length > 0 && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                    // permission was granted, yay!
                    Log.d(TAG, "permission accepted for INTERNET");
                    if(mSectionsPagerAdapter.getRegisteredFragment(2) != null) {
                        ((CameraFragment) mSectionsPagerAdapter.getRegisteredFragment(2)).attemptToCall();
                    }
                } else {
                    // permission denied, boo! do nothing I suppose
                    Log.d(TAG, "permission denied for INTERNET");
                }
                return;
            }

            case Keys.PERM_REQ_ACCESS_WIFI_STATE: {
                // If request is cancelled, the result arrays are empty.
                if (grantResults.length > 0 && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                    // permission was granted, yay!
                    Log.d(TAG, "permission accepted for ACCESS_WIFI_STATE");
                    if(mSectionsPagerAdapter.getRegisteredFragment(2) != null) {
                        ((CameraFragment) mSectionsPagerAdapter.getRegisteredFragment(2)).attemptToCall();
                    }
                } else {
                    // permission denied, boo! do nothing I suppose
                    Log.d(TAG, "permission denied for ACCESS_WIFI_STATE");
                }
                return;
            }

            case Keys.PERM_REQ_WAKE_LOCK: {
                // If request is cancelled, the result arrays are empty.
                if (grantResults.length > 0 && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                    // permission was granted, yay!
                    Log.d(TAG, "permission accepted for WAKE_LOCK");
                    if(mSectionsPagerAdapter.getRegisteredFragment(2) != null) {
                        ((CameraFragment) mSectionsPagerAdapter.getRegisteredFragment(2)).attemptToCall();
                    }
                } else {
                    // permission denied, boo! do nothing I suppose
                    Log.d(TAG, "permission denied for WAKE_LOCK");
                }
                return;
            }

            case Keys.PERM_REQ_MODIFY_AUDIO_SETTINGS: {
                // If request is cancelled, the result arrays are empty.
                if (grantResults.length > 0 && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                    // permission was granted, yay!
                    Log.d(TAG, "permission accepted for MODIFY_AUDIO_SETTINGS");
                    if(mSectionsPagerAdapter.getRegisteredFragment(2) != null) {
                        ((CameraFragment) mSectionsPagerAdapter.getRegisteredFragment(2)).attemptToCall();
                    }
                } else {
                    // permission denied, boo! do nothing I suppose
                    Log.d(TAG, "permission denied for MODIFY_AUDIO_SETTINGS");
                }
                return;
            }
            */
            // other 'case' lines to check for other
            // permissions this app might request.
        }

    }

    @Override
    public void returnToLogin() {
        // this does the whole logout operation

        // TODO: delete apptoken from the Database? otherwise I will continue to receive notifications after I log out
        // even if I sign in as a different user, i will receive notifications for the first user

        // TODO: consider deleting the local storage, too? so the next user won't see the last picture taken by the prev user?
        // sign out from firebase
        mAuth.signOut();
        // erase stored username and password from sharedprefs
        SharedPreferences prefs = getSharedPreferences(Keys.FILE_PREFS, Context.MODE_PRIVATE);
        SharedPreferences.Editor e = prefs.edit();
        e.remove(Keys.KEY_USER);
        e.remove(Keys.KEY_PASS);
        e.commit(); // wiser to commit it immediately instead of whenver it feels like it

        // this simply kills the pageractivity and launches the login activity
        Intent next = new Intent(PagerActivity.this, LoginActivity.class);
        // add some extras?
        startActivity(next);
        finish();
        return;
    }


    // ===========================================================================================================
    /**
     * A {@link FragmentPagerAdapter} that returns a fragment corresponding to
     * one of the sections/tabs/pages.
     */
    public class SectionsPagerAdapter extends FragmentPagerAdapter {
        // this array holds either 'null' or a reference to the active fragment, super handy!
        SparseArray<Fragment> mRegisteredFragments = new SparseArray<Fragment>();
        public final String[] mPageTitles;

        public SectionsPagerAdapter(FragmentManager fm, Context c) {
            super(fm);
            // context is used to get resources from the activity, i guess?
            mPageTitles = c.getResources().getStringArray(R.array.page_title_short);
        }

        @Override public @NonNull Object instantiateItem(@NonNull ViewGroup container, int position) {
            Fragment fragment = (Fragment) super.instantiateItem(container, position);
            mRegisteredFragments.put(position, fragment);
            return fragment;
        }
        @Override public void destroyItem(@NonNull ViewGroup container, int position, @NonNull Object object) {
            mRegisteredFragments.remove(position);
            super.destroyItem(container, position, object);
        }

        // returns null if it is unregistered, nonnull if it exists
        public Fragment getRegisteredFragment(int position) { return mRegisteredFragments.get(position); }

        @Override public Fragment getItem(int position) {
            // getItem is called to instantiate the fragment for the given page.
            Fragment f;
            switch(position) {
                case 0:
                    f = SensorListFragment.newInstance(mPiIsConnected);
                    Log.d(TAG, "creating sensor list fragment");
                    break;
                case 1:
                    f = ControlFragment.newInstance(mPiIsConnected);
                    Log.d(TAG, "creating control fragment");
                    break;
                case 2:
                    f = CameraFragment.newInstance(mPiIsConnected);
                    Log.d(TAG, "creating camera fragment");
                    break;
                default:
                    Log.d(TAG, "err: invalid page was requested by pager adapter");
                    return null;
            }
            return f;
        }

        @Override public CharSequence getPageTitle(int position) { return mPageTitles[position]; }

        @Override public int getCount() { return 3; }
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
        FirebaseUser currentUser = mAuth.getCurrentUser();
        if(currentUser == null) {
            Log.d(TAG, "ERROR: lost the login credentials!");
        }
        try {
            // when opened, close either/both of the notificaiton messages
            NotificationManager nm = (NotificationManager) getSystemService(Context.NOTIFICATION_SERVICE);
            nm.cancel(Keys.ID_NOTIFY_ACTIVE);
            nm.cancel(Keys.ID_NOTIFY_DISCONNECT);
        } catch (NullPointerException npe) {
            Log.d(TAG, "notificaiton manager somehow failed, not sure how", npe);
        }
        mMyDatabase.child(Keys.DB_CONNECTED).addValueEventListener(mDBListenerPiConnected);

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
        mMyDatabase.child(Keys.DB_CONNECTED).removeEventListener(mDBListenerPiConnected);
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
