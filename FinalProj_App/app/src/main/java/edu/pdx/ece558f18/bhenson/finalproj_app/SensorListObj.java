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
//    public class SensorObj implements java.io.Serializable {
//        public String gpio_name = "";
//        public int sensor_type = 0;
//        // for each GPIO, there a 3 possible states: 0=disconnected, 1=door sensor, 2=vibration sensor
//        public SensorObj() {}
//        public SensorObj(String s, int i) { gpio_name = s;sensor_type = i; }
//    }

    public String[] mNameList;
    public int[] mTypeList;

    public SensorListObj() {}

    public SensorListObj(int number) {
        mNameList = new String[number];
        mTypeList = new int[number];
        for(int i = 0; i < number; i++) {
            mNameList[i] = "gpio" + i;
            mTypeList[i] = 0;
        }
    }

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

    @Override
    public String toString() {
        ObjectMapper mapper = new ObjectMapper();
        try {
            return mapper.writeValueAsString(this);
        } catch (JsonProcessingException jpe) {
            Log.d("SEC_SensrListObj", "error: failed to turn sensor list into a string!", jpe );
            return null;
        }
    }


}
