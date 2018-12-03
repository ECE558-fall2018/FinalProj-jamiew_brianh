package edu.pdx.ece558f18.bhenson.finalproj_app;

import android.content.Context;
import android.net.Uri;
import android.os.Bundle;
import android.support.annotation.NonNull;
import android.support.v4.app.Fragment;
import android.support.v7.widget.LinearLayoutManager;
import android.support.v7.widget.RecyclerView;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.Spinner;
import android.widget.TextView;


/**
 * A simple {@link Fragment} subclass.
 * Activities that contain this fragment must implement the
 * {@link SensorListFragmentListener} interface
 * to handle interaction events.
 * Use the {@link SensorListFragment#newInstance} factory method to
 * create an instance of this fragment.
 */
public class SensorListFragment extends Fragment {
    public static final String TAG = "SEC_SnsrListFragment";
    private SensorListFragmentListener mListener;

    private boolean mIsConnected;


    private RecyclerView mRecyclerView;
    private MyAdapter mAdapter;

    private SensorListObj mSensorListObj_master;
    private SensorListObj mSensorListObj_temp;

    // TODO: pretty much everything in this file



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
        // note: change the adapter each time the UI updates? what does that mean?

        //mAdapter.notifyDataSetChanged();

        mSensorListObj_master = new SensorListObj(10);
        mSensorListObj_temp = mSensorListObj_master;

        setPiConnection(mIsConnected);
        return v;
    }

    // sets the buttons and whatnot to be enabled/disabled as appropriate
    public void setPiConnection(boolean b) {
        // TODO


        mIsConnected = b;
    }









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
            // TODO: set the displayed contents of the spinner

            // Create an ArrayAdapter using the string array and a default spinner layout
            ArrayAdapter<CharSequence> adapter = ArrayAdapter.createFromResource(getContext(),
                    R.array.sensor_types_array, android.R.layout.simple_spinner_item);
            // Specify the layout to use when the list of choices appears
            adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
            // Apply the adapter to the spinner
            mSpinner.setAdapter(adapter);
            // set listener for the spinner value change
            mSpinner.setOnItemSelectedListener(this);
        }
        public void Bind(int position) {
            // this is basically onCreateView
//            String title = mBookTitles[position];
//            mTitleTextView.setText(title);
            mPosition = position;
            mTv.setText(mSensorListObj_temp.mNameList[position]);
            mSpinner.setSelection(mSensorListObj_temp.mTypeList[position]);
        }

        @Override
        public void onItemSelected(AdapterView<?> parent, View view, int position, long id) {
            Log.d(TAG, "entry " + mPosition + " changed to " + position);
            // if something was selected, then change what's held in mSensorListObj_temp to match
            // if _temp and _master are the same, then disable both buttons
            // if they are different, then enable both buttons

        }

        @Override
        public void onNothingSelected(AdapterView<?> parent) {
            // not sure how this can happen, or what do do when it does happen?
            Log.d(TAG, "entry " + mPosition + " clicked nothing");
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
    public interface SensorListFragmentListener {
        // TODO: Update argument type and name
        void onFragmentInteraction(Uri uri);
    }

    // ===========================================================================================================
    // override critical lifecycle functions, mostly for logging


    @Override
    public void onAttach(Context context) {
        super.onAttach(context);
        Log.d(TAG, "onAttach(context)");
        if (context instanceof SensorListFragmentListener) {
            mListener = (SensorListFragmentListener) context;
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
