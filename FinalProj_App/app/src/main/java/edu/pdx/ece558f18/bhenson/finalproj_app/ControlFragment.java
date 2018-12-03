package edu.pdx.ece558f18.bhenson.finalproj_app;

import android.content.Context;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.os.Handler;
import android.support.annotation.NonNull;
import android.support.v4.app.Fragment;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.*;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.database.*;


/**
 * A simple {@link Fragment} subclass.
 * Activities that contain this fragment must implement the
 * {@link ControlFragmentListener} interface
 * to handle interaction events.
 * Use the {@link ControlFragment#newInstance} factory method to
 * create an instance of this fragment.
 */
public class ControlFragment extends Fragment {
    public static final String TAG = "SEC_ControlFragment";
    private ControlFragmentListener mListener;
    private FirebaseAuth mAuth;
    private DatabaseReference mMyDatabase;

    private CheckBox mCheckBox;
    private Button mLogout;
    private Switch mToggle;
    private NumberPicker mNumberPicker;
    private TextView mStatus;

    private boolean mIsConnected;

    private Handler mHandler = new Handler();




    public ControlFragment() {
        // Required empty public constructor
    }

    /**
     * Use this factory method to create a new instance of
     * this fragment using the provided parameters.
     *
     * @return A new instance of fragment ControlFragment.
     */
    public static ControlFragment newInstance(boolean piIsConnected) {
        ControlFragment fragment = new ControlFragment();
        Bundle args = new Bundle();

        args.putBoolean(Keys.KEY_ISCONNECTED, piIsConnected);
        fragment.setArguments(args);
        return fragment;
    }

    @Override
    public void onCreate(Bundle saved) {
        super.onCreate(saved);
        Log.d(TAG, "onCreate(bundle)");
        if (getArguments() != null) {
            mIsConnected = getArguments().getBoolean(Keys.KEY_ISCONNECTED);
        }

        mAuth = FirebaseAuth.getInstance();
        if(mAuth.getCurrentUser() == null) {
            Log.d(TAG, "somehow lost login credentials!");
        }
        mMyDatabase = FirebaseDatabase.getInstance().getReference().child(Keys.DB_TOPFOLDER).child(mAuth.getCurrentUser().getUid());
    }

    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, ViewGroup container, Bundle saved) {
        // Inflate the layout for this fragment
        Log.d(TAG, "onCreateView(...)");
        View v = inflater.inflate(R.layout.fragment_control, container, false);

        // fill all the UI variables with their objects
        TextView whoami = (TextView) v.findViewById(R.id.tv_whoami);
        mNumberPicker = (NumberPicker) v.findViewById(R.id.picker_timeout);
        mCheckBox = (CheckBox) v.findViewById(R.id.cb_autologin);
        mLogout = (Button) v.findViewById(R.id.butt_logout);
        mToggle = (Switch) v.findViewById(R.id.switch_armtoggle);
        mStatus = (TextView) v.findViewById(R.id.tv_connection_status);

        // initialize all UI elements that need it
        // whoami text
        whoami.setText(getString(R.string.whoami_label, Keys.stripEmailSuffix(mAuth.getCurrentUser().getEmail())) );

        // numberpicker
        // TODO: numberpicker is really hard to work with (how do i reverse the order? how do I resize the center bit????) so consider replacing with a spinner or direct input
        // TODO: specify the values that are displayd (1/2/3/5/10/15/20, perhaps?)
        mNumberPicker.setMaxValue(100);
        mNumberPicker.setMinValue(0);
        mNumberPicker.setWrapSelectorWheel(false);

        // autologin initial value, from sharedprefs
        SharedPreferences prefs = getContext().getSharedPreferences(Keys.FILE_PREFS, Context.MODE_PRIVATE);
        mCheckBox.setChecked(prefs.getBoolean(Keys.KEY_AUTOLOGIN, Keys.DEFAULT_AUTOLOGIN));


        // get initial values for armtoggle and timeout from database
        // note: this only needs to get the value for the timeout, the other listener gets the armed status
        mMyDatabase.addListenerForSingleValueEvent(mInitValuesListener);

        mMyDatabase.child(Keys.DB_ARMED).addValueEventListener(mArmedChangedListener);


        // attach listeners here
        mCheckBox.setOnClickListener(checkboxClickListener);
        mLogout.setOnClickListener(logoutButtonListener);
        mToggle.setOnClickListener(armedClickListener);
        // TODO
        // timout select

        mLogout.requestFocus();
        // note: it looks dumb when the NumberPicker has focus as the fragment launches, so either somehow unfocus it or focus on something else
        // note: it's not totally consistient tho, the NumberPicker keeps grabbing focus


        setPiConnection(mIsConnected);
        return v;
    }

    // sets the buttons and whatnot to be enabled/disabled as appropriate
    public void setPiConnection(boolean b) {
        Log.d(TAG, "pi_connected state changed, now " + b);
        if(b) {
            mToggle.setEnabled(true);
            mStatus.setText(R.string.status_connected);
        } else {
            mToggle.setEnabled(false);
            mStatus.setText(R.string.status_disconnected);
        }
        mIsConnected = b;
    }


    protected ValueEventListener mArmedChangedListener = new ValueEventListener() {
        @Override public void onDataChange(@NonNull DataSnapshot ds) {
            Log.d(TAG, "armed value changed");
            try {
                mToggle.setChecked(ds.getValue(Boolean.class));
            } catch (NullPointerException npe) {
                Log.d(TAG, "error: bad data when getting initial values", npe);
                return;
            }
        }
        @Override public void onCancelled(@NonNull DatabaseError de) {
            // Failed to read value, not sure how or what to do about it
            Log.d(TAG, "firebase error: failed to get snapshot??", de.toException());
        }
    };

    private ValueEventListener mInitValuesListener = new ValueEventListener() {
        @Override public void onDataChange(@NonNull DataSnapshot ds) {
            Log.d(TAG, "got the initial values");
            try {
                //mToggle.setChecked(ds.child(Keys.DB_ARMED).getValue(Boolean.class));
                int fromdb = ds.child(Keys.DB_TIMEOUT).getValue(Integer.class);
                // TODO: convert # of seconds back into index
                mNumberPicker.setValue(2);

            } catch (NullPointerException npe) {
                Log.d(TAG, "error: bad data when getting initial values", npe);
                return;
            }
        }
        @Override public void onCancelled(@NonNull DatabaseError de) {
            // Failed to read value, not sure how or what to do about it
            Log.d(TAG, "firebase error: failed to get snapshot??", de.toException());
        }
    };

    View.OnClickListener armedClickListener = new View.OnClickListener() {
        @Override public void onClick(View v) {
            // send new value to database
            mMyDatabase.child(Keys.DB_ARMED).setValue( mToggle.isChecked() );
            // disable, but re-enable after 1 second
            mToggle.setEnabled(false);
            mHandler.postDelayed(reEnableToggle, 1000);
        }
    };

    private Runnable reEnableToggle = new Runnable() {@Override public void run() { mToggle.setEnabled(true); }};

    View.OnClickListener logoutButtonListener = new View.OnClickListener() {
        @Override public void onClick(View v) {
            Log.d(TAG, "logging out");
            // the whole operation executes in the pager activity
            mListener.returnToLogin();
        }
    };



    View.OnClickListener checkboxClickListener = new View.OnClickListener() {
        @Override public void onClick(View v) {
            Log.d(TAG, "toggling autologin preference");
            // whenever this is toggled, change the sharedpreferences accordingly
            SharedPreferences prefs = getContext().getSharedPreferences(Keys.FILE_PREFS, Context.MODE_PRIVATE);
            SharedPreferences.Editor e = prefs.edit();
            e.putBoolean(Keys.KEY_AUTOLOGIN, mCheckBox.isChecked());
            e.apply();
        }
    };





    /**
     * This interface must be implemented by activities that contain this
     * fragment to allow an interaction in this fragment to be communicated
     * to the activity and potentially other fragments contained in that
     * activity.
     * <p>
     * See the Android Training lesson <a href=
     * "http://developer.android.com/training/basics/fragments/communicating.html"
     * >Communicating with Other Fragments</a> for more information.
     */
    public interface ControlFragmentListener {
        void returnToLogin();
    }

    // ===========================================================================================================
    // override critical lifecycle functions, mostly for logging

    @Override
    public void onAttach(Context context) {
        super.onAttach(context);
        Log.d(TAG, "onAttach(context)");
        if (context instanceof ControlFragmentListener) {
            mListener = (ControlFragmentListener) context;
        } else {
            throw new RuntimeException(context.toString() + " must implement ControlFragmentListener");
        }
    }

    @Override
    public void onDetach() {
        super.onDetach();
        mListener = null;
        Log.d(TAG, "onDetatch()");

    }
    @Override
    public void onStart() {
        super.onStart();
        Log.d(TAG, "onStart()");
    }
    @Override
    public void onStop() {
        super.onStop();
        Log.d(TAG, "onStop()");
    }
    @Override
    public void onDestroy() {
        super.onDestroy();
        Log.d(TAG, "onDestroy()");
    }
    @Override
    public void onPause() {
        super.onPause();
        Log.d(TAG, "onPause()");
    }
    @Override
    public void onResume() {
        super.onResume();
        Log.d(TAG, "onResume()");
    }


}
