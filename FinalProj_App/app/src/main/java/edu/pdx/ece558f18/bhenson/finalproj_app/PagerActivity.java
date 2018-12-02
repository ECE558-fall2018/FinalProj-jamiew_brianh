package edu.pdx.ece558f18.bhenson.finalproj_app;

import android.content.Context;
import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.support.v4.app.Fragment;
import android.support.v4.app.FragmentManager;
import android.support.v4.app.FragmentPagerAdapter;
import android.support.v4.view.ViewPager;
import android.support.v7.app.AppCompatActivity;
import android.util.Log;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;

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


        // get instance of a "cloud storage bucket"
        // FirebaseStorage storage = FirebaseStorage.getInstance();


        // get instance of the database (see proj3 example for more)
        // FirebaseDatabase database = FirebaseDatabase.getInstance();



    }

    // ===========================================================================================================

    @Override
    public void onFragmentInteraction(Uri uri) {

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

        public final String[] page_titles_short;
        public SectionsPagerAdapter(FragmentManager fm, Context c) {
            super(fm);
            // context is used to get resources from the activity, i guess?
            page_titles_short = getResources().getStringArray(R.array.page_title_short);



            // probably to get more stuff too
        }

        @Override public Fragment getItem(int position) {
            // getItem is called to instantiate the fragment for the given page.
            Fragment f = null;
            switch(position) {
                case 0:
                    f = SensorListFragment.newInstance("","");
                    break;
                case 1:
                    f = ControlFragment.newInstance("","");
                    break;
                case 2:
                    f = CameraFragment.newInstance("","");
                    break;
                default:
                    Log.d(TAG, "err: invalid page was requested by pager adapter");
            }
            return f;
        }

        @Override public CharSequence getPageTitle(int position) {
            return page_titles_short[position];
        }

        @Override public int getCount() {
            // Show 3 total pages.
            return 3;
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
