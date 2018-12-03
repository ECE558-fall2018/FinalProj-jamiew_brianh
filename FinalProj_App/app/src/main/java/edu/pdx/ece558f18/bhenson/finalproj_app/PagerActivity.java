package edu.pdx.ece558f18.bhenson.finalproj_app;

import android.content.Context;
import android.content.Intent;
import android.net.Uri;
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
                    CameraFragment.CameraFragmentListener,
                    SensorListFragment.SensorListFragmentListener {

    public static final String TAG = "SEC_PagerActivity";

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
    private boolean mPiIsConnected = false;

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
        mViewPager.setCurrentItem(1, false);

        // get instance of the authentication object, hopefully already logged in
        mAuth = FirebaseAuth.getInstance();

        // get instance of the database (see proj3 example for more)
        mMyDatabase = FirebaseDatabase.getInstance().getReference().child("users").child(mAuth.getCurrentUser().getUid());
        mMyDatabase.child("pi_connected").addValueEventListener(mDisconnectListener);




        // get instance of a "cloud storage bucket"
        // FirebaseStorage storage = FirebaseStorage.getInstance();




    }


    // inital read of database, and also fires each time it changes
    // set the member variable to be used in teh constructor if this fires before the fragments get made
    private ValueEventListener mDisconnectListener = new ValueEventListener() {
        @Override public void onDataChange(@NonNull DataSnapshot ds) {
            boolean b;
            try {
                b = (Boolean) ds.getValue();
            } catch (NullPointerException npe) {
                Log.d(TAG, "error: field doesnt exist or has bad data, " + ds.toString(), npe);
                return;
            }
            Log.d(TAG, "firebase: pi_connected state changed, now " + b);
            mPiIsConnected = b;
            // if the fragments DO exist, then call the functions to enable/disable their buttons
            if(mSectionsPagerAdapter.getRegisteredFragment(0) != null) {
                SensorListFragment f = (SensorListFragment) mSectionsPagerAdapter.getRegisteredFragment(0);
                f.setPiConnection(b);
            }
            if(mSectionsPagerAdapter.getRegisteredFragment(1) != null) {
                ControlFragment f = (ControlFragment) mSectionsPagerAdapter.getRegisteredFragment(1);
                f.setPiConnection(b);
            }
            if(mSectionsPagerAdapter.getRegisteredFragment(2) != null) {
                CameraFragment f = (CameraFragment) mSectionsPagerAdapter.getRegisteredFragment(2);
                f.setPiConnection(b);
            }
        }

        @Override public void onCancelled(@NonNull DatabaseError de) {
            // Failed to read value, not sure how or what to do about it
            Log.d(TAG, "firebase error: failed to get snapshot??", de.toException());
        }
    };



    // ===========================================================================================================
    // these callbacks are used by the fragments, these are needed by the interfaces

    @Override
    public void onFragmentInteraction(Uri uri) {
        // stub
    }

    @Override
    public void returnToLogin() {
        // this does only what must be done at the activity level, the ControlFragment does more stuff before calling this
        // this simply kills the pageractivity and launches the login activity
        Intent next = new Intent(PagerActivity.this, LoginActivity.class);
        // add some extras?
        startActivity(next);
        finish();
        return;
    }


//            TextView textView = (TextView) rootView.findViewById(R.id.section_label);
//            textView.setText(getString(R.string.section_format, getArguments().getInt(ARG_SECTION_NUMBER)));

    // ===========================================================================================================
    /**
     * A {@link FragmentPagerAdapter} that returns a fragment corresponding to
     * one of the sections/tabs/pages.
     */
    public class SectionsPagerAdapter extends FragmentPagerAdapter {
        // 1 if it exists, null if it doesnt
        SparseArray<Fragment> registeredFragments = new SparseArray<Fragment>();
        public final String[] page_titles_short;


        public SectionsPagerAdapter(FragmentManager fm, Context c) {
            super(fm);
            // context is used to get resources from the activity, i guess?
            page_titles_short = getResources().getStringArray(R.array.page_title_short);



            // probably to get more stuff too
        }

        @Override public @NonNull Object instantiateItem(@NonNull ViewGroup container, int position) {
            Fragment fragment = (Fragment) super.instantiateItem(container, position);
            registeredFragments.put(position, fragment);
            return fragment;
        }
        @Override public void destroyItem(@NonNull ViewGroup container, int position, @NonNull Object object) {
            registeredFragments.remove(position);
            super.destroyItem(container, position, object);
        }
        // returns null if it is unregistered, nonnull if it exists
        public Fragment getRegisteredFragment(int position) { return registeredFragments.get(position); }

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

        @Override public CharSequence getPageTitle(int position) { return page_titles_short[position]; }

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
