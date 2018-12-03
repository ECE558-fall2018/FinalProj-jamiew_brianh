package edu.pdx.ece558f18.bhenson.finalproj_app;

import android.util.Log;
import com.fasterxml.jackson.core.JsonParseException;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonMappingException;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.io.IOException;

/* NOTE:
in the app-level build.gradle file, I added under dependencies:
    implementation 'com.fasterxml.jackson.core:jackson-core:2.9.7'
    implementation 'com.fasterxml.jackson.core:jackson-annotations:2.9.7'
    implementation 'com.fasterxml.jackson.core:jackson-databind:2.9.7'
*/
public class SensorListObj implements java.io.Serializable {
    public String[] mNameList;
    public int[] mTypeList;
    // for each GPIO, there are 3 possible states: 0=disconnected, 1=contact sensor, 2=vibration sensor

    public SensorListObj() {}

    // create object with given size
    public SensorListObj(int number) {
        mNameList = new String[number];
        mTypeList = new int[number];
        for(int i = 0; i < number; i++) {
            mNameList[i] = "gpio" + i;
            mTypeList[i] = 0;
        }
    }

    // copy constructor
    public SensorListObj(SensorListObj slo) {
        int s = slo.mNameList.length;
        mNameList = new String[s];
        mTypeList = new int[s];
        for(int i = 0; i < s; i++) {
            // doesn't matter if the strings are both pointing to the same memory, they don't change so that's fine
            mNameList[i] = slo.mNameList[i];
            mTypeList[i] = slo.mTypeList[i];
        }
    }

    // create object from json string
    public SensorListObj(String json) {
        ObjectMapper mapper = new ObjectMapper();
        SensorListObj slo = new SensorListObj();
        try {
            slo = mapper.readValue(json, SensorListObj.class);
            this.mNameList = slo.mNameList;
            this.mTypeList = slo.mTypeList;
        } catch(JsonParseException jpe) {
            Log.d("SEC_SensrListObj", "error1: failed to turn string into sensor list!", jpe );
        } catch(JsonMappingException jme) {
            Log.d("SEC_SensrListObj", "error2: failed to turn string into sensor list!", jme );
        } catch(IOException ioe) {
            Log.d("SEC_SensrListObj", "error3: failed to turn string into sensor list!", ioe );
        }
    }


    // create json string from object
    @Override public String toString() {
        ObjectMapper mapper = new ObjectMapper();
        try {
            return mapper.writeValueAsString(this);
        } catch (JsonProcessingException jpe) {
            Log.d("SEC_SensrListObj", "error: failed to turn sensor list into a string!", jpe );
            return null;
        }
    }

    @Override
    public boolean equals(Object o) {
        if (!(o instanceof SensorListObj)) {
            return false;
        } else {
            SensorListObj slo = (SensorListObj) o;
            if (slo.mNameList.length != mNameList.length) return false;
            for (int i = 0; i < mNameList.length; i++) {
                if (!slo.mNameList[i].equals(mNameList[i])) return false;
            }
            if (slo.mTypeList.length != mTypeList.length) return false;
            for (int i = 0; i < mTypeList.length; i++) {
                if (slo.mTypeList[i] != mTypeList[i]) return false;
            }
            //Log.d("SEC_SensrListObj", "return true");
            return true;
        }
    }
}
