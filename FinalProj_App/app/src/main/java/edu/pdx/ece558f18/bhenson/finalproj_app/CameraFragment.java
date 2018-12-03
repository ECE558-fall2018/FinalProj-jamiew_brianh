package edu.pdx.ece558f18.bhenson.finalproj_app;

import android.Manifest;
import android.content.Context;
import android.content.pm.PackageManager;
import android.graphics.BitmapFactory;
import android.net.Uri;
import android.os.Bundle;
import android.os.Environment;
import android.support.annotation.NonNull;
import android.support.v4.app.ActivityCompat;
import android.support.v4.app.Fragment;
import android.support.v4.content.ContextCompat;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.ImageView;
import android.widget.Toast;
import com.google.android.gms.tasks.OnFailureListener;
import com.google.android.gms.tasks.OnSuccessListener;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.database.*;
import com.google.firebase.storage.FirebaseStorage;
import com.google.firebase.storage.StorageReference;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;


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
    public static final long TWO_MEGABYTE = 1024 * 1024 * 2;

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
    private StorageReference mMyImages;
    private int mAttemptCount;


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
        mMyDatabase = FirebaseDatabase.getInstance().getReference().child(Keys.DB_TOPFOLDER).child(mAuth.getCurrentUser().getUid());
        mMyImages = FirebaseStorage.getInstance().getReference().child(Keys.STORAGE_TOPFOLDER).child(mAuth.getCurrentUser().getUid());
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

//        try {
//            Log.d(TAG, "assets are " + Arrays.toString(getContext().getAssets().list("")));
//
//            // first, read what i put in the assets folder
//            InputStream i = getContext().getAssets().open("test_1800x1200.jpg", AssetManager.ACCESS_BUFFER);
//            int size = i.available();
//            byte[] buffer = new byte[size];
//            i.read(buffer);
//            i.close();
//
//            // then write that file into the local storage
//            FileOutputStream outputStream = getContext().openFileOutput(Keys.FILE_MED, Context.MODE_PRIVATE);
//            outputStream.write(buffer);
//            outputStream.close();
//        } catch (FileNotFoundException fnfe) {
//            Log.d(TAG, "fnfe exception1", fnfe);
//        } catch (IOException ioe) {
//            Log.d(TAG, "io exception1", ioe);
//        }



        // load the imageview with the last image I saved (should exist in local storage) or if there is none then leave blank
        // set up pointer to the small file location
        try {
            File file = new File(getContext().getFilesDir(), Keys.FILE_SMALL);
            if (file.exists()) {
                // pipe it into the image view
                Log.d(TAG, "initializing imageview with picture from " + file.getAbsolutePath());
                // TODO: is it cleaner to have two versions of displayImage or to put a bunch of code here to turn the File into a byte array?
                displayImage(file.getAbsolutePath());
            }
        } catch (NullPointerException npe) {
            Log.d(TAG, "somehow lost the context, possibly due to rotation?", npe);
        }

        // attach listeners to the buttons
        //mButtSave.setOnClickListener(mSaveClick);
        mButtManual.setOnClickListener(mManualPhotoRequest);
        mButtHires.setOnClickListener(mHiresPhotoRequest);
        mButtVoip.setOnClickListener(mVoipClick);


        // attach the onValueChanged listener
        mMyDatabase.child(Keys.DB_CAMERA_STATE).addValueEventListener(mOnCameraStateChangeListener);

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

    private void displayImage(byte[] bytes) {
        mImageView.setImageBitmap(BitmapFactory.decodeByteArray(bytes, 0, bytes.length));
    }

    private void displayImage(String filename) {
        mImageView.setImageBitmap(BitmapFactory.decodeFile(filename));
    }

    private boolean saveImageToLocal(String name, byte[] bytes) {
        // return false if everything is OK
        // return true if there was some exception
        try {
            FileOutputStream outputStream = getContext().openFileOutput(name, Context.MODE_PRIVATE);
            outputStream.write(bytes);
            outputStream.close();
            Log.d(TAG, "successfully saved lores file to local storage");
            return false;
        } catch (FileNotFoundException fnfe) {
            Log.d(TAG, "fnfe exception1", fnfe);
            return true;
        } catch (IOException ioe) {
            Log.d(TAG, "io exception1", ioe);
            return true;
        } catch (NullPointerException npe) {
            Log.d(TAG, "npe exception1, perhaps due to device rotation?", npe);
            return true;
        }
    }

    private boolean saveImageToExternal(byte[] bytes) {
        // return false if everything is OK
        // return true if there was some exception
        Date dNow = new Date();
        SimpleDateFormat ft = new SimpleDateFormat("yyMMddhhmmssMs", Locale.US);
        String newfilename = ft.format(dNow) + ".jpg";
        try {
            File f = new File(getPublicPicturesDir(), newfilename);
            Log.d(TAG, "saving hires file to " + f.getAbsolutePath());
            FileOutputStream outputStream = new FileOutputStream(f);
            outputStream.write(bytes);
            outputStream.close();
            // part 2: create toast with the file name I used
            Toast.makeText(getContext(), getString(R.string.new_file_toast, newfilename), Toast.LENGTH_SHORT).show();
            return false;
        } catch (FileNotFoundException fnfe) {
            Log.d(TAG, "fnfe exception2", fnfe);
            Toast.makeText(getContext(), getString(R.string.error), Toast.LENGTH_SHORT).show();
            return true;
        } catch (IOException ioe) {
            Log.d(TAG, "io exception2", ioe);
            Toast.makeText(getContext(), getString(R.string.error), Toast.LENGTH_SHORT).show();
            return true;
        }
    }


    private void beginDownloadSmallImage(String s) {
        final String sf = s;
        Log.d(TAG, "beginning to download, firebase path = " + mMyImages.child(sf).getPath());

        // this whole thing runs asynchronously
        mMyImages.child(sf).getBytes(TWO_MEGABYTE).addOnSuccessListener(new OnSuccessListener<byte[]>() {
            @Override
            public void onSuccess(byte[] bytes) {
                // Data for "images/island.jpg" is returned, use this as needed
                Log.d(TAG, "done downloading lores image, size = " + bytes.length);

                // part 1: save it into local storage
                saveImageToLocal(sf, bytes);
                // part 2: display on imageview
                displayImage(bytes);

                // part 3: enable buttons (if connected)
                if (mIsConnected) { mButtManual.setEnabled(true);mButtHires.setEnabled(true); }
                // part 4: move to next camera state
                mMyDatabase.child(Keys.DB_CAMERA_STATE).setValue(3);
                // on success, reset the # of attempts
                mAttemptCount = 0;
            }
        }).addOnFailureListener(new OnFailureListener() {
            @Override
            public void onFailure(@NonNull Exception e) {
                // Handle any errors
                Log.d(TAG, "failed to download lores image", e);
                mAttemptCount += 1;
                if(mAttemptCount > Keys.MAX_RETRY_CT) {
                    Log.d(TAG, "repeatedly failed to download lores image, now giving up");
                    // toast
                    Toast.makeText(getContext(), getString(R.string.small_file_giveup_toast), Toast.LENGTH_SHORT).show();
                    // part 3: enable buttons (if connected)
                    if (mIsConnected) { mButtManual.setEnabled(true);mButtHires.setEnabled(true); }
                    // part 4: move to next camera state
                    mMyDatabase.child(Keys.DB_CAMERA_STATE).setValue(3);
                    // on giving up, reset the # of attempts
                    mAttemptCount = 0;
                } else {
                    // toast?
                    Toast.makeText(getContext(), getString(R.string.small_file_fail_toast, mAttemptCount), Toast.LENGTH_SHORT).show();
                    // retry
                    beginDownloadSmallImage(sf);
                }
            }
        });
    }


    private void beginDownloadBigImage(String s) {
        final String sf = s;
        Log.d(TAG, "beginning to download " + mMyImages.child(sf).getPath());

        // this whole thing runs asynchronously
        mMyImages.child(sf).getBytes(TWO_MEGABYTE).addOnSuccessListener(new OnSuccessListener<byte[]>() {
            @Override
            public void onSuccess(byte[] bytes) {
                // Data for "images/island.jpg" is returned, use this as needed
                Log.d(TAG, "done downloading hires image, size = " + bytes.length);

                // part 1: save it into external storage
                saveImageToExternal(bytes);

                // TODO: i could display the hirez version here if i felt like it

                // part 3: enable buttons (if connected)
                if (mIsConnected) { mButtManual.setEnabled(true);mButtHires.setEnabled(true); }
                // part 4: move to next camera state
                mMyDatabase.child(Keys.DB_CAMERA_STATE).setValue(3);
                // on success, reset the # of attempts
                mAttemptCount = 0;
            }
        }).addOnFailureListener(new OnFailureListener() {
            @Override
            public void onFailure(@NonNull Exception e) {
                // Handle any errors
                Log.d(TAG, "failed to download hires image", e);
                mAttemptCount += 1;
                if(mAttemptCount > Keys.MAX_RETRY_CT) {
                    Log.d(TAG, "repeatedly failed to download lores image, now giving up");
                    // toast?
                    Toast.makeText(getContext(), getString(R.string.big_file_giveup_toast), Toast.LENGTH_SHORT).show();
                    // part 3: enable buttons (if connected)
                    if (mIsConnected) { mButtManual.setEnabled(true);mButtHires.setEnabled(true); }
                    // part 4: move to next camera state
                    mMyDatabase.child(Keys.DB_CAMERA_STATE).setValue(3);
                    // on giving up, reset the # of attempts
                    mAttemptCount = 0;
                } else {
                    // toast
                    Toast.makeText(getContext(), getString(R.string.big_file_fail_toast, mAttemptCount), Toast.LENGTH_SHORT).show();
                    // retry
                    beginDownloadBigImage(sf);
                }
            }
        });
    }


    public File getPublicPicturesDir() {
        // Get the directory for the user's public pictures directory.
        File file = new File(Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_PICTURES), Keys.DIR_PUBLIC);
        if (!file.mkdirs()) {
            Log.d(TAG, "Photos directory not created, " + file.getAbsolutePath());
        }
        return file;
    }


    View.OnClickListener mVoipClick = new View.OnClickListener() {
        @Override public void onClick(View v) {
            // use the same listener but either start/end call depending on state variables
            // TODO: everything to do with VOIP
            // NOTE: one thing to be done in the pager activity is, if the page is changed while in a call, end it
        }
    };


    View.OnClickListener mManualPhotoRequest = new View.OnClickListener() {
        @Override public void onClick(View v) {
            // to request a photo be taken, simply move to state 1
            mMyDatabase.child(Keys.DB_CAMERA_STATE).setValue(1);
        }
    };

    View.OnClickListener mHiresPhotoRequest = new View.OnClickListener() {
        @Override public void onClick(View v) {
            // to request a download of the hires image, simply move to state 4
            if(ContextCompat.checkSelfPermission(getActivity(), Manifest.permission.WRITE_EXTERNAL_STORAGE) != PackageManager.PERMISSION_GRANTED) {
                Log.d(TAG, "need to request write permissions");

                ActivityCompat.requestPermissions(getActivity(),
                        new String[]{Manifest.permission.WRITE_EXTERNAL_STORAGE},
                        Keys.WRITE_PERMISSIONS_REQ_CODE);

                // the result is returned to onRequestPermissionsResult on the activity level

            } else {
                // if I do somehow have permission, then do the thing right now
                triggerDownload();
            }
        }
    };

    public void triggerDownload() {
        // set state to 4 to begin the download operation
        mMyDatabase.child(Keys.DB_CAMERA_STATE).setValue(4);
    }

    protected ValueEventListener mOnCameraStateChangeListener = new ValueEventListener() {
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
                    beginDownloadSmallImage(Keys.FILE_SMALL);
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
                    beginDownloadBigImage(Keys.FILE_MED);
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
        // TODO: Update argument type and name for VOIP callbacks
        void onFragmentInteraction(Uri uri);
        void onRequestPermissionsResult(int requestCode, String permissions[], int[] grantResults);
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
