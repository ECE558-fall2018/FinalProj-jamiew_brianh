package edu.pdx.ece558f18.bhenson.finalproj_app;

import android.content.Context;
import android.os.Bundle;
import android.support.annotation.NonNull;
import android.support.v4.app.Fragment;
import android.support.v7.widget.LinearLayoutManager;
import android.support.v7.widget.RecyclerView;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.*;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.database.*;


public class SensorListFragment extends Fragment {
    public static final String TAG = "SEC_SnsrListFragment";

    private boolean mIsConnected;
    private SensorListObj mSensorListObj_master; // holds the obj i got from the database
    private SensorListObj mSensorListObj_temp; // the locally-held copy that im modifying
    private FirebaseAuth mAuth;
    private DatabaseReference mMyDatabase;



    private RecyclerView mRecyclerView;
    private MyAdapter mAdapter;

    private Button mApply;
    private Button mReset;


    public SensorListFragment() {
        // Required empty public constructor
    }

    /**
     * Use this factory method to create a new instance of
     * this fragment using the provided parameters.
     *
     * @return A new instance of fragment SensorListFragment.
     */
    public static SensorListFragment newInstance(boolean piIsConnected) {
        SensorListFragment fragment = new SensorListFragment();
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
    public View onCreateView(@NonNull LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState) {
        Log.d(TAG, "onCreateView(...)");
        // Inflate the layout for this fragment
        View v = inflater.inflate(R.layout.fragment_sensor_list, container, false);

        mRecyclerView = (RecyclerView) v.findViewById(R.id.recycler_view);
        mRecyclerView.setLayoutManager(new LinearLayoutManager(getActivity()));
        mAdapter = new MyAdapter();
        mRecyclerView.setAdapter(mAdapter);



        mApply = (Button) v.findViewById(R.id.butt_apply);
        mReset = (Button) v.findViewById(R.id.butt_reset);

        mApply.setOnClickListener(mApplyListener);
        mReset.setOnClickListener(mResetListener);

        mSensorListObj_master = new SensorListObj(0);
        // create the temp as a copy of the master
        mSensorListObj_temp = new SensorListObj(mSensorListObj_master);

        // get the sensor object from the database, the list is empty until that happens
        mMyDatabase.child(Keys.DB_SENSOR_CONFIG).addListenerForSingleValueEvent(mInitSensConfig);

        return v;

        // TODO: add a "calibrate" button to trigger the range finder setup phase?
    }






    // sets the buttons and whatnot to be enabled/disabled as appropriate
    public void updatePiConnectionState() {
        boolean b = ((PagerActivity)getActivity()).mPiIsConnected;
        // if _temp and _master are the different, then enable both buttons... otherwise, disable both buttons
        if(b != mIsConnected) Log.d(TAG, "pi_connected state changed, now " + b);
        if(!mSensorListObj_master.equals(mSensorListObj_temp) && b) {
            mApply.setEnabled(true); mReset.setEnabled(true);
        } else {
            mApply.setEnabled(false); mReset.setEnabled(false);
        }
        // is there a simple way to disable all of the elements under/inside a view?
        mIsConnected = b;
        mAdapter.notifyDataSetChanged();
    }


    protected ValueEventListener mInitSensConfig = new ValueEventListener() {
        @Override public void onDataChange(@NonNull DataSnapshot ds) {
            Log.d(TAG, "got the initial values");
            try {
                String json = ds.getValue(String.class);
                if(json == null) {
                    Log.d(TAG, "ERROR: got bad data from the firebase");
                    throw new NullPointerException();
                } else {
                    // create master from database resposne
                    mSensorListObj_master = new SensorListObj(json);
                    // create the temp as a copy of the master
                    mSensorListObj_temp = new SensorListObj(mSensorListObj_master);
                    mAdapter.notifyDataSetChanged();
                }
            } catch (NullPointerException npe) {
                Log.d(TAG, "error: bad data when getting initial values", npe);
                return;
            } catch(DatabaseException de) {
                Log.d(TAG, "error: something bad", de);
                return;
            }
        }
        @Override public void onCancelled(@NonNull DatabaseError de) {
            // Failed to read value, not sure how or what to do about it
            Log.d(TAG, "firebase error: failed to get snapshot??", de.toException());
        }
    };


    View.OnClickListener mResetListener = new View.OnClickListener() {
        @Override public void onClick(View v) {
            // to reset, copy the master back onto the local
            mSensorListObj_temp = new SensorListObj(mSensorListObj_master);
            // then update the ui
            mAdapter.notifyDataSetChanged();
            mApply.setEnabled(false); mReset.setEnabled(false);
        }
    };

    View.OnClickListener mApplyListener = new View.OnClickListener() {
        @Override public void onClick(View v) {
            // to apply the changes, first update the master
            mSensorListObj_master = new SensorListObj(mSensorListObj_temp);
            // then send the master to the database
            // remember to .toString() to jsonify it, if I dont the firebase will try to serialize the object and fail cuz its a piece of crap
            mMyDatabase.child(Keys.DB_SENSOR_CONFIG).setValue(mSensorListObj_master.toString());
            mApply.setEnabled(false); mReset.setEnabled(false);
        }
    };






    public class MyViewHolder extends RecyclerView.ViewHolder
            implements AdapterView.OnItemSelectedListener {
        public TextView mTv;
        public Spinner mSpinner;
        public int mPosition;

        public MyViewHolder(View v) {
            // this is basically onCreate
            super(v);
            mTv = (TextView) v.findViewById(R.id.tv_gpio_name);
            mSpinner = (Spinner) v.findViewById(R.id.spinner_gpio_state);

            // set the displayed contents of the spinner
            try {
                // Create an ArrayAdapter using the string array and a default spinner layout
                ArrayAdapter<CharSequence> adapter = ArrayAdapter.createFromResource(getContext(),
                        R.array.sensor_types_array, android.R.layout.simple_spinner_item);
                // Specify the layout to use when the list of choices appears
                adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
                // Apply the adapter to the spinner
                mSpinner.setAdapter(adapter);
            } catch (NullPointerException npe) {
                Log.d(TAG, "somehow lost my context", npe);
            }
            // set listener for the spinner value change
            mSpinner.setOnItemSelectedListener(this);
        }
        public void Bind(int position) {
            // this is basically onCreateView
            mPosition = position;
            mTv.setText(mSensorListObj_temp.mNameList[position]);
            mSpinner.setSelection(mSensorListObj_temp.mTypeList[position]);
            mSpinner.setEnabled(mIsConnected);
        }

        @Override
        public void onItemSelected(AdapterView<?> parent, View view, int position, long id) {
            //Log.d(TAG, "entry " + mPosition + " changed to " + position);
            // if something was selected, then change what's held in mSensorListObj_temp to match
            mSensorListObj_temp.mTypeList[mPosition] = position;
            // if _temp and _master are the different, then enable both buttons... otherwise, disable both buttons
            if(mIsConnected && !mSensorListObj_master.equals(mSensorListObj_temp)) {
                mApply.setEnabled(true); mReset.setEnabled(true);
            } else {
                mApply.setEnabled(false); mReset.setEnabled(false);
            }
        }

        @Override
        public void onNothingSelected(AdapterView<?> parent) {
            // not sure how this can happen, or what do do when it does happen?
            //Log.d(TAG, "entry " + mPosition + " clicked nothing");
        }
    }

    public class MyAdapter extends RecyclerView.Adapter<MyViewHolder> {
        @Override
        public @NonNull MyViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
            LayoutInflater li = LayoutInflater.from(getActivity());
            View v = li.inflate(R.layout.sensor_layout, parent, false);
            return new MyViewHolder(v);
        }
        @Override
        public void onBindViewHolder(@NonNull MyViewHolder holder, int position) {
            holder.Bind(position);
        }
        @Override
        public int getItemCount() {
            return mSensorListObj_temp.mNameList.length;
        }
    }






    // ===========================================================================================================
    // override critical lifecycle functions, mostly for logging


    @Override
    public void onAttach(Context context) {
        super.onAttach(context);
        Log.d(TAG, "onAttach(context)");
    }

    @Override
    public void onDetach() {
        super.onDetach();
        Log.d(TAG, "onDetatch()");

    }
    @Override
    public void onStart() {
        super.onStart();
        Log.d(TAG, "onStart()");
        updatePiConnectionState();
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
