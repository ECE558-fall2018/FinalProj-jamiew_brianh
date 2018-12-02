package edu.pdx.ece558f18.bhenson.finalproj_app;

import android.content.Context;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.support.annotation.NonNull;
import android.support.v4.app.Fragment;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.*;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.database.DatabaseReference;
import com.google.firebase.database.FirebaseDatabase;


/**
 * A simple {@link Fragment} subclass.
 * Activities that contain this fragment must implement the
 * {@link ControlFragmentListener} interface
 * to handle interaction events.
 * Use the {@link ControlFragment#newInstance} factory method to
 * create an instance of this fragment.
 */
public class ControlFragment extends Fragment {
    // TODO: Rename parameter arguments, choose names that match
    // the fragment initialization parameters, e.g. ARG_ITEM_NUMBER
    private static final String ARG_PARAM1 = "param1";
    private static final String ARG_PARAM2 = "param2";
    // TODO: Rename and change types of parameters
    private String mParam1;
    private String mParam2;


    public static final String TAG = "SEC_ControlFragment";
    private ControlFragmentListener mListener;
    private FirebaseAuth mAuth;
    private DatabaseReference mDatabase;

    private CheckBox mCheckBox;
    private Button mLogout;
    private Switch mToggle;


    public ControlFragment() {
        // Required empty public constructor
    }

    /**
     * Use this factory method to create a new instance of
     * this fragment using the provided parameters.
     *
     * @param param1 Parameter 1.
     * @param param2 Parameter 2.
     * @return A new instance of fragment ControlFragment.
     */
    // TODO: Rename and change types and number of parameters
    public static ControlFragment newInstance(String param1, String param2) {
        ControlFragment fragment = new ControlFragment();
        Bundle args = new Bundle();
        args.putString(ARG_PARAM1, param1);
        args.putString(ARG_PARAM2, param2);
        fragment.setArguments(args);
        return fragment;
    }

    @Override
    public void onCreate(Bundle saved) {
        super.onCreate(saved);
        Log.d(TAG, "onCreate(bundle)");
        if (getArguments() != null) {
            mParam1 = getArguments().getString(ARG_PARAM1);
            mParam2 = getArguments().getString(ARG_PARAM2);
        }
        mAuth = FirebaseAuth.getInstance();
        if(mAuth.getCurrentUser() == null) {
            Log.d(TAG, "somehow lost login credentials!");
        }
        mDatabase = FirebaseDatabase.getInstance().getReference();
    }

    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, ViewGroup container, Bundle saved) {
        // Inflate the layout for this fragment
        Log.d(TAG, "onCreateView(...)");
        View v = inflater.inflate(R.layout.fragment_control, container, false);

        // fill all the UI variables with their objects
        TextView whoami = (TextView) v.findViewById(R.id.tv_whoami);
        NumberPicker mNumberPicker = (NumberPicker) v.findViewById(R.id.picker_timeout);
        mCheckBox = (CheckBox) v.findViewById(R.id.cb_autologin);
        mLogout = (Button) v.findViewById(R.id.butt_logout);
        mToggle = (Switch) v.findViewById(R.id.switch_armtoggle);

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


        // armtoggle initial value, from database
        // TODO

        // timeout initial value, from database
        // TODO


        // attach listeners here
        // TODO
        mCheckBox.setOnClickListener(checkboxClickListener);
        mLogout.setOnClickListener(logoutButtonListener);
        // arm/disarm
        // timout select

        mLogout.requestFocus();
        // note: it looks dumb when the NumberPicker has focus as the fragment launches, so either somehow unfocus it or focus on something else
        // note: it's not totally consistient tho, the NumberPicker keeps grabbing focus



        return v;
    }




    View.OnClickListener logoutButtonListener = new View.OnClickListener() {
        @Override public void onClick(View v) {
            Log.d(TAG, "logging out");
            // sign out from firebase
            mAuth.signOut();
            // erase stored username and password from sharedprefs
            SharedPreferences prefs = getContext().getSharedPreferences(Keys.FILE_PREFS, Context.MODE_PRIVATE);
            SharedPreferences.Editor e = prefs.edit();
            e.remove(Keys.KEY_USER);
            e.remove(Keys.KEY_PASS);
            e.commit(); // wiser to commit it immediately instead of whenver it feels like it
            // this part executes in the pager activity
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
