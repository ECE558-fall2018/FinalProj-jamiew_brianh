package edu.pdx.ece558f18.bhenson.finalproj_app;

import android.Manifest;
import android.content.Context;
import android.content.pm.PackageManager;
import android.graphics.BitmapFactory;
import android.media.MediaRecorder;
import android.os.Bundle;
import android.os.Environment;
import android.os.Handler;
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
import android.widget.ProgressBar;
import android.widget.Toast;
import com.google.android.gms.tasks.OnFailureListener;
import com.google.android.gms.tasks.OnSuccessListener;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.database.*;
import com.google.firebase.storage.FirebaseStorage;
import com.google.firebase.storage.StorageReference;
import com.google.firebase.storage.UploadTask;

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
    public static final String TAG = "SEC_CameraFrag";
    public static final String TAG2 = "_VOIP";
    public static final String TAG3 = "_PHOTO";
    public static final long TWO_MEGABYTE = 1024 * 1024 * 2;

    private CameraFragmentListener mListener;
    private boolean mIsConnected = false;
    private int mCameraState = 0; // not sure this is needed but whatever
    private ImageView mImageView;
    private ImageView mRecordIndicator;
    private Button mButtManual;
    private Button mButtHires;
    private Button mButtRecord;
    private ProgressBar mDownloadProgress;
    private ProgressBar mRecordProgress;
    private FirebaseAuth mAuth;
    private DatabaseReference mMyDatabase;
    private StorageReference mMyStorageBucket;
    private int mAttemptCount;

    private boolean mIsRecording;
    private MediaRecorder mRecorder;
    private File mSoundFile;

    // voip stuff
//    public SipManager mSipManager = null;
//    public SipProfile mSipProfile = null;
//    public boolean mVoipTalking = false;
//    public SipAudioCall mCall = null;
//    public String mRemoteUri = null;

    public Handler mHandler = new Handler();

    // TODO: separate into 3 files: one for camera pipeline, one for voip pipeline, and one for the UI
    // (lowest priority tho, doesn't actually help anything and might just make stuff more confusing)

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
        mMyStorageBucket = FirebaseStorage.getInstance().getReference().child(Keys.STORAGE_TOPFOLDER).child(mAuth.getCurrentUser().getUid());

//        if (mSipManager == null) {
//            try {
//                mSipManager = SipManager.newInstance(getContext());
//            } catch (NullPointerException npe) {
//                Log.d(TAG, "somehow lost the context, couldn't create SIP manager");
//            }
//        }
    }

    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState) {
        Log.d(TAG, "onCreateView(...)");
        // Inflate the layout for this fragment
        View v = inflater.inflate(R.layout.fragment_camera, container, false);

        // fill all the UI variables with their objects
        mImageView = (ImageView) v.findViewById(R.id.imageView);
        mRecordIndicator = (ImageView) v.findViewById(R.id.imageView_speaker);
        mButtManual = (Button) v.findViewById(R.id.butt_manual_capture);
        mButtHires = (Button) v.findViewById(R.id.butt_hires);
        mButtRecord = (Button) v.findViewById(R.id.butt_record);
        mDownloadProgress = (ProgressBar) v.findViewById(R.id.camera_progress);
        mRecordProgress = (ProgressBar) v.findViewById(R.id.call_progress);

        // load the imageview with the last image I saved (should exist in local storage) or if there is none then leave blank
        // set up pointer to the small file location
        try {
            // set up a pointer to the sound file destination (local storage)
            mSoundFile = new File(getContext().getFilesDir(), Keys.FILE_SOUND);
            // find the existing image file
            File file = new File(getContext().getFilesDir(), mAuth.getCurrentUser().getUid() + Keys.FILE_SMALL);
            if (file.exists()) {
                // pipe it into the image view
                Log.d(TAG, "initializing imageview with picture from " + file.getAbsolutePath());
                displayImage(file.getAbsolutePath());
            }
        } catch (NullPointerException npe) {
            Log.d(TAG, "somehow lost the context, possibly due to rotation?", npe);
        }

        // attach listeners to the buttons
        //mButtSave.setOnClickListener(mSaveClick);
        mButtManual.setOnClickListener(mOnClickManual);
        mButtHires.setOnClickListener(mOnClickHires);
        mButtRecord.setOnClickListener(mOnClickRecord);
        mButtRecord.setText(R.string.begin_record_label);


        return v;
    }




    // sets the buttons and whatnot to be enabled/disabled as appropriate
    protected void updatePiConnectionState(boolean b) {
        if(b != mIsConnected) Log.d(TAG, "pi_connected state changed, now " + b);
        if(b) {
            // the record button enable/disable is independent of the pi connection state
            // mButtRecord.setEnabled(true);
            // the others depend on the state
            if(mCameraState == 0) {
                mButtManual.setEnabled(true); mButtHires.setEnabled(false);
                mDownloadProgress.setVisibility(View.INVISIBLE);
            } else if(mCameraState == 3) {
                mButtManual.setEnabled(true); mButtHires.setEnabled(true);
                mDownloadProgress.setVisibility(View.INVISIBLE);
            } else {
                mButtManual.setEnabled(false); mButtHires.setEnabled(false);
                mDownloadProgress.setVisibility(View.VISIBLE);
            }
        } else {
            // the record button enable/disable is independent of the pi connection state
            // mButtRecord.setEnabled(false);
            // disable the buttons, regardless of what state the camera is in
            mButtManual.setEnabled(false); mButtHires.setEnabled(false);
            mDownloadProgress.setVisibility(View.INVISIBLE);
            mRecordProgress.setVisibility(View.INVISIBLE);
        }
        mIsConnected = b;
    }

    // ===========================================================================================================
    // recording stuff


    private View.OnClickListener mOnClickRecord = new View.OnClickListener() {
        @Override public void onClick(View v) {
            // use the same listener but either start/end call depending on state variables
            if(mIsRecording) {
                // if stopRecording argument is true, it means stop recording and begin upload
                // if stopRecording argument is false, it means abort recording
                stopRecording(true);
            } else {
                attemptToRecord();
            }
        }
    };

    protected void attemptToRecord() {
        // called by the PagerActivity on permissions return, as well as the record button on-click listener
        // stage 1: check permissions
        if(requestPermissionsForRecord()) {
            // if it succeeds, then proceed
            actuallyRecord();
        }
    }

    private boolean requestPermissionsForRecord() {
        // to request a download of the hires image, first need to get permissions
        if (ContextCompat.checkSelfPermission(getActivity(), Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED) {
            Log.d(TAG, "need to request write permissions");
            ActivityCompat.requestPermissions(getActivity(),
                    new String[]{Manifest.permission.RECORD_AUDIO},
                    Keys.PERM_REQ_RECORD_AUDIO);
            // the result is returned to onRequestPermissionsResult on the activity level
        } else {
            return true;
        }
        return false;
    }


    private void actuallyRecord() {
        // first replace the text with "end record"
        // then disp the icon
        // then begin recording, save to ?????
        Log.d(TAG, "starting to record");
        mButtRecord.setText(R.string.end_record_label);
        mRecordIndicator.setVisibility(View.VISIBLE);

        // start a recording
        mRecorder = new MediaRecorder();
        mRecorder.setAudioSource(MediaRecorder.AudioSource.MIC);
        mRecorder.setOutputFormat(MediaRecorder.OutputFormat.THREE_GPP); // dunno what this does
        mRecorder.setOutputFile(mSoundFile.getAbsolutePath());
        mRecorder.setAudioEncoder(MediaRecorder.AudioEncoder.AMR_NB); // dunno what this is

        try {
            mRecorder.prepare();
            mRecorder.start();
        } catch (IOException e) {
            Log.e(TAG, "prepare() failed");
        }
        mIsRecording = true;
    }

    protected void stopRecording(boolean save) {
        // first replace the text with "begin record"
        // then hide the icon
        // then stop the recording
        mIsRecording = false;
        Log.d(TAG, "stopping the recording, bool=" + save);
        mButtRecord.setText(R.string.begin_record_label);
        mRecordIndicator.setVisibility(View.INVISIBLE);

        // stop a recording
        if(mRecorder != null) {
            mRecorder.stop();
            mRecorder.release();
            mRecorder = null;
        }
        if(save) {
            // this only happens when the "stop recording" button is clicked
            // record button gets disabled until the PI is done downloading
            mButtRecord.setEnabled(false);
            mRecordProgress.setVisibility(View.VISIBLE);
            mRecordProgress.setProgress(25);

            // begin uploading
            Log.d(TAG, "beginning the upload");
            mMyStorageBucket.child(Keys.FILE_SOUND).putFile(android.net.Uri.fromFile(mSoundFile)).addOnFailureListener(new OnFailureListener() {
                @Override public void onFailure(@NonNull Exception e) {
                    // Handle unsuccessful uploads
                    Log.d(TAG, "failed to upload", e);
                    mButtRecord.setEnabled(true);
                }
            }).addOnSuccessListener(new OnSuccessListener<UploadTask.TaskSnapshot>() {
                @Override public void onSuccess(UploadTask.TaskSnapshot taskSnapshot) {
                    // taskSnapshot.getMetadata() contains file metadata such as size, content-type, etc.
                    Log.d(TAG, "successfully uploaded the sound file!!! size=" + taskSnapshot.getBytesTransferred());
                    // when done uploading, then i set the field to TRUE to signal the pi to start downloading
                    mMyDatabase.child(Keys.DB_SOUND).setValue(true);
                    mButtRecord.setEnabled(true);
                    mRecordProgress.setVisibility(View.VISIBLE);
                    mRecordProgress.setProgress(60);
                }
            });
        } else {
            // this only happens when the fragment onStop is called, or when moving to a different page
            mRecordProgress.setVisibility(View.INVISIBLE);
            mButtRecord.setEnabled(true);
        }
    }

    private ValueEventListener mDBListenerSoundDownloadStatus = new ValueEventListener() {
        @Override public void onDataChange(@NonNull DataSnapshot ds) {
            // listening to the "photos/signal_new_sound" field
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
            if(b) {
                // i set this to true, ignore it
                mButtRecord.setEnabled(false);
            } else {
                // pi sets it to be false when it is done downloading, so i can re-enable the button and disable any progress bar
                mButtRecord.setEnabled(true);
                mRecordProgress.setVisibility(View.INVISIBLE);
            }
        }

        @Override public void onCancelled(@NonNull DatabaseError de) {
            // Failed to read value, not sure how or what to do about it
            Log.d(TAG, "firebase error: failed to get snapshot??", de.toException());
        }
    };


    // ===========================================================================================================
    // voip stuff

//    /*
//    private View.OnClickListener mOnClickVoip = new View.OnClickListener() {
//        @Override public void onClick(View v) {
//            // use the same listener but either start/end call depending on state variables
//            mButtVoip.setEnabled(false);
//            if(!mVoipTalking) {
//                // placing a call:
//                Log.d(TAG, "starting a call");
//                attemptToCall();
//            } else {
//                // hanging up:
//                Log.d(TAG, "ending the call");
//                actuallyEndTheCall();
//            }
//        }
//    };
//
//    protected void attemptToCall() {
//        // called by the PagerActivity on permissions return
//        // stage 1: check permissions
//        if(requestPermissionsForVoip()) {
//            // stage 2: create a profile
//            createSipProfile();
//            // stage 3: register the profile (the hard part)
//            registerSipProfile();
//            // then goes into listener and the listener asynchronously calls actuallyMakeTheCall() if it actually succeeds
//        }
//    }
//
//    private boolean requestPermissionsForVoip() {
//        // return TRUE if it passes, FALSE otherwise
//        // this is called by voip button click, and its also called by pageractivity
//        // AAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAA I HATE PERMISSIONS
//        if(ContextCompat.checkSelfPermission(getActivity(), Manifest.permission.USE_SIP) != PackageManager.PERMISSION_GRANTED) {
//            Log.d(TAG + TAG2, "need to request sip permissions");
//            ActivityCompat.requestPermissions(getActivity(),
//                    new String[]{Manifest.permission.USE_SIP},
//                    Keys.PERM_REQ_USE_SIP);
//        } else {
//            // if I do somehow have permission, then check the next one
//            if(ContextCompat.checkSelfPermission(getActivity(), Manifest.permission.INTERNET) != PackageManager.PERMISSION_GRANTED) {
//                Log.d(TAG, "need to request internet permissions");
//                ActivityCompat.requestPermissions(getActivity(),
//                        new String[]{Manifest.permission.INTERNET},
//                        Keys.PERM_REQ_INTERNET);
//            } else {
//                // if I do somehow have permission, then check the next one
//                if(ContextCompat.checkSelfPermission(getActivity(), Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED) {
//                    Log.d(TAG, "need to request audio permissions");
//                    ActivityCompat.requestPermissions(getActivity(),
//                            new String[]{Manifest.permission.RECORD_AUDIO},
//                            Keys.PERM_REQ_RECORD_AUDIO);
//                } else {
//                    // if I do somehow have permission, then check the next one
//                    if(ContextCompat.checkSelfPermission(getActivity(), Manifest.permission.ACCESS_WIFI_STATE) != PackageManager.PERMISSION_GRANTED) {
//                        Log.d(TAG, "need to request wifi permissions");
//                        ActivityCompat.requestPermissions(getActivity(),
//                                new String[]{Manifest.permission.ACCESS_WIFI_STATE},
//                                Keys.PERM_REQ_ACCESS_WIFI_STATE);
//                    } else {
//                        // if I do somehow have permission, then check the next one
//                        if(ContextCompat.checkSelfPermission(getActivity(), Manifest.permission.WAKE_LOCK) != PackageManager.PERMISSION_GRANTED) {
//                            Log.d(TAG, "need to request wake permissions");
//                            ActivityCompat.requestPermissions(getActivity(),
//                                    new String[]{Manifest.permission.WAKE_LOCK},
//                                    Keys.PERM_REQ_WAKE_LOCK);
//                        } else {
//                            // if I do somehow have permission, then check the next one
//                            if(ContextCompat.checkSelfPermission(getActivity(), Manifest.permission.MODIFY_AUDIO_SETTINGS) != PackageManager.PERMISSION_GRANTED) {
//                                Log.d(TAG, "need to request modify_audio permissions");
//                                ActivityCompat.requestPermissions(getActivity(),
//                                        new String[]{Manifest.permission.MODIFY_AUDIO_SETTINGS},
//                                        Keys.PERM_REQ_MODIFY_AUDIO_SETTINGS);
//                            } else {
//                                // SUCCESS
//                                // I OFFICIALLY HAVE ALL THE PERMISSIONS I MIGHT POSSIBLY NEED
//                                // FINALLY I CAN DO THE THING
//                                return true;
//                            }
//                        }
//                    }
//                }
//            }
//        }
//        return false;
//    }
//
//
//    private void createSipProfile() {
//        mRecordProgress.setVisibility(View.VISIBLE);
//        mRecordProgress.setProgress(20);
//
//        Log.d(TAG, "CAN WE DO IT " + SipManager.isApiSupported(getContext()) + " AND " + SipManager.isVoipSupported(getContext()));
//
//        // stage 1: build the profile
//        SipProfile.Builder builder;
//        try {
//            builder = new SipProfile.Builder(Keys.SIP_APP_USERNAME, Keys.SIP_DOMAIN);
//        } catch (ParseException pe) {
//            Log.d(TAG, "failed to parse the username and domain", pe);
//            mHandler.post(gui2);
//            return;
//        }
//        builder.setPassword(Keys.SIP_APP_PASSWORD);
//        mSipProfile = builder.build();
//
//        Log.d(TAG, "my uri = " + mSipProfile.getUriString());
//    }
//
//    private void registerSipProfile() {
//        // stage 2: register the profile
//        // stage 2.5: actually do the thing AFTER the listener is set up
//        // stage 3: wait for registration result
//        mRecordProgress.setProgress(30);
//        Log.d(TAG, "beginning registration");
//        try {
//            // this is open for send only
//            mSipManager.open(mSipProfile);
//            Log.d(TAG, "am i opened? " + mSipManager.isOpened(mSipProfile.getUriString()));
//            if(!mSipManager.isRegistered(mSipProfile.getUriString())) {
//                mSipManager.register(mSipProfile, Keys.SIP_TIMEOUT, mSipListenerRegistration);
//            } else {
//                Log.d(TAG, "already registered??? gonna just go on, see what happens");
//                actuallyMakeTheCall();
//            }
//            //mSipManager.setRegistrationListener(mSipProfile.getUriString(), mSipListenerRegistration);
//        } catch (SipException se) {
//            Log.d(TAG, "some unknown SIP exception when opening", se);
//            mHandler.post(gui2);
//            return;
//        }
//    }
//
//
//    private SipRegistrationListener mSipListenerRegistration = new SipRegistrationListener() {
//        // NOTE: for some reason i can't put any UI calls in these
//        @Override public void onRegistering(String localProfileUri) {
//            //updateStatus("Registering with SIP Server...");
//            Log.d(TAG, "+++ now registering");
//            mHandler.post(gui3);
//        }
//
//        @Override public void onRegistrationFailed(String localProfileUri, int errorCode, String errorMessage) {
//            //updateStatus("Registration failed.  Please check settings.");
//            Log.d(TAG, "+++ error with registration: " + errorCode + ", " + errorMessage);
//            mHandler.post(gui2);
//        }
//
//        @Override public void onRegistrationDone(String localProfileUri, long expiryTime) {
//            Log.d(TAG, "+++ DONE REGISTERING!");
//            actuallyMakeTheCall();
//        }
//    };
//
//
//
//    private void actuallyMakeTheCall() {
//        Log.d(TAG, "placing call to base station");
//        mRecordProgress.setProgress(60);
//        try {
//            mCall = mSipManager.makeAudioCall(mSipProfile.getUriString(), mRemoteUri, mSipListenerAudioCall, Keys.SIP_TIMEOUT);
//        } catch (SipException se) {
//            Log.d(TAG, "some unknown SIP exception when placing the call", se);
//        }
//    }
//
//    private SipAudioCall.Listener mSipListenerAudioCall = new SipAudioCall.Listener() {
//        @Override public void onCalling(SipAudioCall call) {
//            super.onCalling(call);
//            mHandler.post(gui4);
//        }
//        @Override public void onCallEstablished(SipAudioCall call) {
//            super.onCallEstablished(call);
//            call.startAudio();
//            call.setSpeakerMode(true);
//            //if(!call.isMuted()) {
//                // if not muted, be muted (does this mute the speaker or the mic? unclear)
//                call.toggleMute();
//            //}
//            mVoipTalking = true;
//            mHandler.post(gui1);
//        }
//        @Override public void onCallEnded(SipAudioCall call) {
//            super.onCallEnded(call);
//            call.close();
//            mHandler.post(gui2);
//        }
//        @Override public void onError(SipAudioCall call, int errorCode, String errorMessage) {
//            super.onError(call, errorCode, errorMessage);
//            Log.d(TAG, "+++ error during the call: " + errorCode + ", " + errorMessage);
//            // idea: if an error happens when trying to connect, it probably won't connect
//            // if an error happens when it is already connected, then the buttons and progress bar will already be like this
//            mHandler.post(gui2);
//        }
//    };
//
//    // NOTE: any calls to the gui either do nothing or actually crash when made inside the SipListeners (apparently thats a separate thread? idk)
//    // however putting those gui updates into simple Runnables that immediately run on the main thread will work
//    // so that's why i have these ugly things
//    // NOTE2: the Database callback functions do support UI calls (usually)
//    private Runnable gui1 = new Runnable() {
//        @Override public void run() {
//            mButtVoip.setEnabled(true);
//            mButtVoip.setText(R.string.end_voip_label);
//            mRecordProgress.setProgress(99);
//            // show the image
//            mRecordIndicator.setVisibility(View.VISIBLE);
//        }
//    };
//    private Runnable gui2 = new Runnable() {@Override public void run() { mButtVoip.setEnabled(mIsConnected && mRemoteUri!=null); mRecordProgress.setVisibility(View.INVISIBLE); }};
//    private Runnable gui3 = new Runnable() {@Override public void run() { mRecordProgress.setProgress(40); }};
//    private Runnable gui4 = new Runnable() {@Override public void run() { mRecordProgress.setProgress(70); }};
//
//    protected void actuallyEndTheCall() {
//        // note: this is called in onStop here and onPageChanged of the PagerActivity
//        mVoipTalking = false;
//        mHandler.post(gui2);
//        mButtVoip.setText(R.string.begin_record_label);
//        // hide the image
//        mRecordIndicator.setVisibility(View.INVISIBLE);
//
//        if (mSipManager == null) { return; } // this should never be hit but let's be extra sure
//
//        if (mCall != null) {
//            Log.d(TAG, "closing the sip call object");
//            try {
//                mCall.endCall();
//                mCall = null; // nullify it to be extra sure
//            } catch (SipException se) {
//                Log.d(TAG, "somehow failed to end the call", se);
//            }
//        }
//        if (mSipProfile != null) {
//            Log.d(TAG, "closing the sip profile object");
//            try {
//                mSipManager.close(mSipProfile.getUriString());
//                mSipProfile = null; // nullify it to be extra sure
//            } catch (SipException se) {
//                Log.d(TAG, "Failed to close local profile.", se);
//            }
//        }
//    }
//
//
//    protected ValueEventListener mDBListenerRemoteURI = new ValueEventListener() {
//        @Override public void onDataChange(@NonNull DataSnapshot ds) {
//            String s = ds.getValue(String.class);
//            if(s == null) {
//                Log.d(TAG, "remote uri node doesn't exist for some reason");
//            } else if(s.equals("-")) {
//                Log.d(TAG, "remote uri node exists but hasn't been filled");
//            } else {
//                Log.d(TAG, "successfully got remote uri");
//                mRemoteUri = s;
//                if(mIsConnected) mButtVoip.setEnabled(true);
//            }
//        }
//        @Override public void onCancelled(@NonNull DatabaseError de) {
//            // Failed to read value, not sure how or what to do about it
//            Log.d(TAG, "firebase error: failed to get snapshot??", de.toException());
//        }
//    };
//    */

    // ===========================================================================================================
    // photo stuff



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
        Log.d(TAG, "beginning to download, firebase path = " + mMyStorageBucket.child(sf).getPath());

        // this whole thing runs asynchronously
        mMyStorageBucket.child(sf).getBytes(TWO_MEGABYTE).addOnSuccessListener(new OnSuccessListener<byte[]>() {
            @Override
            public void onSuccess(byte[] bytes) {
                // Data for "images/island.jpg" is returned, use this as needed
                Log.d(TAG, "done downloading lores image, size = " + bytes.length);
                mDownloadProgress.setProgress(15);

                // part 1: save it into local storage
                saveImageToLocal(mAuth.getCurrentUser().getUid() + sf, bytes);
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
        Log.d(TAG, "beginning to download " + mMyStorageBucket.child(sf).getPath());

        // this whole thing runs asynchronously
        mMyStorageBucket.child(sf).getBytes(TWO_MEGABYTE).addOnSuccessListener(new OnSuccessListener<byte[]>() {
            @Override
            public void onSuccess(byte[] bytes) {
                // Data for "images/island.jpg" is returned, use this as needed
                Log.d(TAG, "done downloading hires image, size = " + bytes.length);
                mDownloadProgress.setProgress(90);

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


    private File getPublicPicturesDir() {
        // Get the directory for the user's public pictures directory.
        File file = new File(Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_PICTURES), Keys.DIR_PUBLIC);
        if (!file.mkdirs()) {
            Log.d(TAG, "Photos directory not created, " + file.getAbsolutePath());
        }
        return file;
    }



    private View.OnClickListener mOnClickManual = new View.OnClickListener() {
        @Override public void onClick(View v) {
            // to request a photo be taken, simply move to state 1
            mMyDatabase.child(Keys.DB_CAMERA_STATE).setValue(1);
            mDownloadProgress.setVisibility(View.VISIBLE);
            mDownloadProgress.setProgress(15);
            mButtManual.setEnabled(false); mButtHires.setEnabled(false);
        }
    };

    private View.OnClickListener mOnClickHires = new View.OnClickListener() {
        @Override public void onClick(View v) {
            attemptToDownloadHires();
        }
    };

    protected void attemptToDownloadHires() {
        // called by PagerActivity in the PermissionsReturn function
        // to request a download of the hires image, simply move to state 4
        if(requestPermissionsForDownload()) {
            // if I do somehow have permission, then do the thing right now
            // set state to 4 to begin the download operation
            mMyDatabase.child(Keys.DB_CAMERA_STATE).setValue(4);
            mDownloadProgress.setVisibility(View.VISIBLE);
            mDownloadProgress.setProgress(15);
            mButtManual.setEnabled(false); mButtHires.setEnabled(false);
        }
    }

    private boolean requestPermissionsForDownload() {
        // to request a download of the hires image, first need to get permissions
        if (ContextCompat.checkSelfPermission(getActivity(), Manifest.permission.WRITE_EXTERNAL_STORAGE) != PackageManager.PERMISSION_GRANTED) {
            Log.d(TAG, "need to request write permissions");
            ActivityCompat.requestPermissions(getActivity(),
                    new String[]{Manifest.permission.WRITE_EXTERNAL_STORAGE},
                    Keys.PERM_REQ_WRITE_EXTERNAL);
            // the result is returned to onRequestPermissionsResult on the activity level
        } else {
            return true;
        }
        return false;
    }

    protected ValueEventListener mDBListenerCameraState = new ValueEventListener() {
        @Override public void onDataChange(@NonNull DataSnapshot ds) {
            int state;
            try {
                state = ds.getValue(Integer.class);
            } catch (NullPointerException npe) {
                Log.d(TAG, "error: bad data when reading camera state", npe);
                return;
            } catch(DatabaseException de) {
                Log.d(TAG, "error: something bad", de);
                return;
            }
            Log.d(TAG, "new camera state: " + state);

            switch(state) {
                case 0:
                    // system is idle, freshly rebooted, no image in pi's memory (even tho there may be one in app localstorage)
                    // this will ONLY happen on app startup immediately following pi startup

                    // enable manual, disable hires
                    if(mIsConnected) { mButtManual.setEnabled(true); mButtHires.setEnabled(false); }
                    mDownloadProgress.setVisibility(View.INVISIBLE);
                    break;
                case 1:
                    // manually requested a photo be taken, pi is currently busy uploading the image
                    // triggered by my button press

                    // both buttons disabled (taken care of in on-click listener AND here)
                    if(mIsConnected) {
                        mButtManual.setEnabled(false); mButtHires.setEnabled(false);
                        mDownloadProgress.setVisibility(View.VISIBLE);
                        mDownloadProgress.setProgress(15);
                    }
                    // i did this
                    Log.d(TAG, "1: my write to the database triggered my own listener");
                    break;
                case 2:
                    // pi is done uploading the lores version, now I automatically download & display it

                    // disable both buttons
                    if(mIsConnected) {
                        mButtManual.setEnabled(false); mButtHires.setEnabled(false);
                        mDownloadProgress.setVisibility(View.VISIBLE);
                        mDownloadProgress.setProgress(60);
                    }

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
                    mDownloadProgress.setVisibility(View.INVISIBLE);

                    if(mCameraState == 2) Log.d(TAG, "3: my write to the database triggered my own listener"); // i did this
                    if(mCameraState == 0) Log.d(TAG, "3: entered the pipeline at this stage");
                    break;
                case 4:
                    // a hires photo has been requested, it is in the process of being uploaded
                    // triggered by my button press

                    // both buttons disabled (taken care of in on-click listener AND here)
                    if(mIsConnected) {
                        mButtManual.setEnabled(false); mButtHires.setEnabled(false);
                        mDownloadProgress.setVisibility(View.VISIBLE);
                        mDownloadProgress.setProgress(15);
                    }
                    // i did this
                    Log.d(TAG, "4: my write to the database triggered my own listener");
                    break;
                case 5:
                    // the hires image is done being uploaded and I should start downloading it

                    // disable both buttons
                    if(mIsConnected) {
                        mButtManual.setEnabled(false); mButtHires.setEnabled(false);
                        mDownloadProgress.setVisibility(View.VISIBLE);
                        mDownloadProgress.setProgress(60);
                    }
                    // download (how? async task?)
                    // save to external storage, AKA photo gallery (what file name should I use?)
                    // create toast saying what file name was used
                    // enable both buttons at the end
                    // write state 3 to the database when i'm done with all this
                    beginDownloadBigImage(Keys.FILE_BIG);
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
        updatePiConnectionState(((PagerActivity)getActivity()).mPiIsConnected);
        // camera state listener
        mMyDatabase.child(Keys.DB_CAMERA_STATE).addValueEventListener(mDBListenerCameraState);
        // recording upload state listener
        mMyDatabase.child(Keys.DB_SOUND).addValueEventListener(mDBListenerSoundDownloadStatus);
    }
    @Override
    public void onStop() {
        super.onStop();
        Log.d(TAG, "onStop()");
        //actuallyEndTheCall();
        // camera state listener
        mMyDatabase.child(Keys.DB_CAMERA_STATE).removeEventListener(mDBListenerCameraState);
        // recording upload state listener
        mMyDatabase.child(Keys.DB_SOUND).removeEventListener(mDBListenerSoundDownloadStatus);
        // call this function whenever the app stops (lock screen for example)
        stopRecording(false);
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
