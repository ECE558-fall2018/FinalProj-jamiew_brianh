package edu.pdx.ece558f18.bhenson.finalproj_app;

import android.content.Context;
import android.content.res.AssetManager;
import android.graphics.BitmapFactory;
import android.net.Uri;
import android.os.Bundle;
import android.support.annotation.NonNull;
import android.support.v4.app.Fragment;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.ImageView;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.database.*;

import java.io.*;
import java.util.Arrays;


/**
 * A simple {@link Fragment} subclass.
 * Activities that contain this fragment must implement the
 * {@link CameraFragmentListener} interface
 * to handle interaction events.
 * Use the {@link CameraFragment#newInstance} factory method to
 * create an instance of this fragment.
 */
public class CameraFragment extends Fragment {
    public static final String TAG = "SEC_CameraFragment";

    private CameraFragmentListener mListener;
    private boolean mIsConnected = false;
    private int mCameraState = 0; // not sure this is needed but whatever
    private ImageView mImageView;
    private Button mButtManual;
    private Button mButtHires;
    private Button mButtVoip;
    private Button mButtSave;
    private FirebaseAuth mAuth;
    private DatabaseReference mMyDatabase;


    // TODO: pretty much everything in this file



    public CameraFragment() {
        // Required empty public constructor
    }

    /**
     * Use this factory method to create a new instance of
     * this fragment using the provided parameters.
     *
     * @return A new instance of fragment CameraFragment.
     */
    public static CameraFragment newInstance(boolean isPiConnected) {
        CameraFragment fragment = new CameraFragment();
        Bundle args = new Bundle();
        args.putBoolean(Keys.KEY_ISCONNECTED, isPiConnected);
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
        mMyDatabase = FirebaseDatabase.getInstance().getReference().child("users").child(mAuth.getCurrentUser().getUid());
    }

    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState) {
        Log.d(TAG, "onCreateView(...)");
        // Inflate the layout for this fragment
        View v = inflater.inflate(R.layout.fragment_camera, container, false);

        // fill all the UI variables with their objects
        mImageView = (ImageView) v.findViewById(R.id.imageView);
        mButtManual = (Button) v.findViewById(R.id.butt_manual_capture);
        mButtHires = (Button) v.findViewById(R.id.butt_hires);
        mButtVoip = (Button) v.findViewById(R.id.butt_voip);
        mButtSave = (Button) v.findViewById(R.id.butt_save);

        // TODO: load the imageview with the last image I saved (should exist in local storage) or if there is none then leave blank


        // NOTE: this is for debug/practice only
        try {
            Log.d(TAG, "assets are " + Arrays.toString(getContext().getAssets().list("")));

            // first, read what i put in the assets folder
            InputStream i = getContext().getAssets().open("test_1800x1200.jpg", AssetManager.ACCESS_BUFFER);
            int size = i.available();
            byte[] buffer = new byte[size];
            i.read(buffer);
            i.close();

            // then write that file into the local storage
            FileOutputStream outputStream = getContext().openFileOutput("test_1800x1200.jpg", Context.MODE_PRIVATE);
            outputStream.write(buffer);
            outputStream.close();
        } catch (FileNotFoundException fnfe) {
            Log.d(TAG, "fnfe exception1", fnfe);
        } catch (IOException ioe) {
            Log.d(TAG, "io exception1", ioe);
        }

        // read taht file I just wrote
        File file = new File(getContext().getFilesDir(), "test_1800x1200.jpg");
        // pipe it into the image view
        mImageView.setImageBitmap(BitmapFactory.decodeFile(file.getAbsolutePath()));


        // attach listeners to the buttons
        //mButtSave.setOnClickListener(mSaveClick);
        mButtManual.setOnClickListener(mManualPhotoRequest);
        mButtHires.setOnClickListener(mHiresPhotoRequest);
        mButtVoip.setOnClickListener(mVoipClick);


        // attach the onValueChanged listener
        mMyDatabase.child("camera").child("photo_pipeline_state").addValueEventListener(mOnCameraStateChangeListener);




        // TODO: more init? idk






        setPiConnection(mIsConnected);
        return v;
    }




    // sets the buttons and whatnot to be enabled/disabled as appropriate
    public void setPiConnection(boolean b) {
        Log.d(TAG, "pi_connected state changed, now " + b);
        if(b) {
            // the voip button gets turned on, no matter what state the camera is in
            mButtVoip.setEnabled(true);
            // the others depend on the state
            if(mCameraState == 0) {
                mButtManual.setEnabled(true); mButtHires.setEnabled(false);
            } else if(mCameraState == 3) {
                mButtManual.setEnabled(true); mButtHires.setEnabled(true);
            }
        } else {
            // disable the buttons, regardless of what state the camera is in
            mButtVoip.setEnabled(false);
            mButtManual.setEnabled(false);
            mButtHires.setEnabled(false);
        }
        mIsConnected = b;
    }

//    View.OnClickListener mSaveClick = new View.OnClickListener() {
//        @Override public void onClick(View v) {
//            // consider deleting this?
//        }
//    };

    View.OnClickListener mVoipClick = new View.OnClickListener() {
        @Override public void onClick(View v) {
            // use the same listener but either start/end call depending on state variables
            // TODO
            // NOTE: one thing to be done in the pager activity is, if the page is changed while in a call, end it
        }
    };


    View.OnClickListener mManualPhotoRequest = new View.OnClickListener() {
        @Override public void onClick(View v) {
            // to request a photo be taken, simply move to state 1
            mMyDatabase.child("camera").child("photo_pipeline_state").setValue(1);
        }
    };

    View.OnClickListener mHiresPhotoRequest = new View.OnClickListener() {
        @Override public void onClick(View v) {
            // to request a download of the hires image, simply move to state 4
            mMyDatabase.child("camera").child("photo_pipeline_state").setValue(4);
        }
    };


    private ValueEventListener mOnCameraStateChangeListener = new ValueEventListener() {
        @Override public void onDataChange(@NonNull DataSnapshot ds) {
            int state;
            try {
                state = ds.getValue(Integer.class);
            } catch (NullPointerException npe) {
                Log.d(TAG, "error: bad data when reading camera state", npe);
                return;
            }
            Log.d(TAG, "new camera state: " + state);

            switch(state) {
                case 0:
                    // system is idle, freshly rebooted, no image in pi's memory (even tho there may be one in app localstorage)
                    // this will ONLY happen on app startup immediately following pi startup

                    // enable manual, disable hires
                    if(mIsConnected) { mButtManual.setEnabled(true); mButtHires.setEnabled(false); }
                    break;
                case 1:
                    // manually requested a photo be taken, pi is currently busy uploading the image
                    // triggered by my button press

                    // both buttons disabled (taken care of in on-click listener AND here)
                    if(mIsConnected) { mButtManual.setEnabled(false); mButtHires.setEnabled(false); }
                    // i did this
                    Log.d(TAG, "1: my write to the database triggered my own listener");
                    break;
                case 2:
                    // pi is done uploading the lores version, now I automatically download & display it

                    // disable both buttons
                    if(mIsConnected) { mButtManual.setEnabled(false); mButtHires.setEnabled(false); }

                    // download (how? async task?)
                    // save to local storage (my sandbox)
                    // display on imageview
                    // enable both buttons at the end (if connected to Pi)
                    // write state 3 to the database when i'm done with all this
                    break;
                case 3:
                    // system is idle, done downloading & showing the lowres version... pi has a hires version available to upload when requested
                    // its very possible that i enter the pipeline here

                    // both buttons ENABLED (if connected to Pi)(taken care of in previous stage AND here)
                    if(mIsConnected) { mButtManual.setEnabled(true); mButtHires.setEnabled(true); }

                    if(mCameraState == 2) Log.d(TAG, "3: my write to the database triggered my own listener"); // i did this
                    if(mCameraState == 0) Log.d(TAG, "3: entered the pipeline at this stage");
                    break;
                case 4:
                    // a hires photo has been requested, it is in the process of being uploaded
                    // triggered by my button press

                    // both buttons disabled (taken care of in on-click listener AND here)
                    if(mIsConnected) { mButtManual.setEnabled(false); mButtHires.setEnabled(false); }
                    // i did this
                    Log.d(TAG, "4: my write to the database triggered my own listener");
                    break;
                case 5:
                    // the hires image is done being uploaded and I should start downloading it

                    // disable both buttons
                    if(mIsConnected) { mButtManual.setEnabled(false); mButtHires.setEnabled(false); }
                    // download (how? async task?)
                    // save to external storage, AKA photo gallery (what file name should I use?)
                    // create toast saying what file name was used
                    // enable both buttons at the end
                    // write state 3 to the database when i'm done with all this
                    break;
                default:
                    Log.d(TAG, "somehow got an invalid camera state, " + state);
                    return;
            }
            // this lets me track what state I moved from
            mCameraState = state;
        }
        @Override public void onCancelled(@NonNull DatabaseError de) {
            // Failed to read value, not sure how or what to do about it
            Log.d(TAG, "firebase error: failed to get snapshot??", de.toException());
        }
    };













    // ===========================================================================================================

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
    public interface CameraFragmentListener {
        // TODO: Update argument type and name
        void onFragmentInteraction(Uri uri);
    }

    // ===========================================================================================================
    // override critical lifecycle functions, mostly for logging

    @Override
    public void onAttach(Context context) {
        super.onAttach(context);
        Log.d(TAG, "onAttach(context)");
        if (context instanceof CameraFragmentListener) {
            mListener = (CameraFragmentListener) context;
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
