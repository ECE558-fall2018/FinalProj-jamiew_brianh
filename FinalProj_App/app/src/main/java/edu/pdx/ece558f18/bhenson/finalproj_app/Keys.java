package edu.pdx.ece558f18.bhenson.finalproj_app;

/**
 * This class is never instantiated anywhere; it simply holds the constants I used for request codes, return codes, and
 * key-strings in the SharedPrefs and SavedInstance bundles, stuff like that. I was sick of remembering which codes
 * should be members of which activity classes, so I just put them all in one place.
 */
public class Keys {
    // because I got sick of remembering what class each const was defined in, I moved them all here
    public static final String PACKAGE = "edu.pdx.ece558f18.bhenson.finalproj_app";
    public static final String FILE_PREFS = PACKAGE + "sharedprefs";
    public static final String EMAIL_SUFFIX = "@pdx.edu";
    public static final String KEY_USER = "username";
    public static final String KEY_PASS = "password";
    public static final String KEY_AUTOLOGIN = "autologin";
    public static final String KEY_ISCONNECTED = "isconnected";


    // file names in local storage will match the file names in the Firebase storage, for simplicity
    public static final String FILE_SMALL = "img_0640x0480.jpg";
    public static final String FILE_MED =   "img_1800x1200.jpg";
    public static final String FILE_BIG =   "img_3200x2400.jpg";
    public static final String DIR_PUBLIC = "SecurityApp";

    public static final int MAX_RETRY_CT = 4;
    public static final int ID_NOTIFY_ACTIVE = 119;
    public static final int ID_NOTIFY_DISCONNECT = 120;
    public static final int WRITE_PERMISSIONS_REQ_CODE = 12345;

    // should be used in the notification code, at both ends
    public static final String FCM_ACTIVE_TITLE = "!!! SecApp Alert !!!";
    public static final String FCM_ACTIVE_MESSAGE = "Base Station has detected an intruder!";
    public static final String FCM_DISCONNECT_TITLE = "SecApp: Warning";
    public static final String FCM_DISCONNECT_MESSAGE = "Unexpectedly lost connection to base station";
    public static final String KEY_GOTOPAGE = "goto_page";



    public static final Boolean DEFAULT_AUTOLOGIN = true;


    // =========================================================================
    // all the database field names go here
    public static final String STORAGE_TOPFOLDER = "users";
    public static final String DB_TOPFOLDER =   "users";

    public static final String DB_EMAIL =       "email";
    public static final String DB_APPTOKEN =    "apptoken";
    public static final String DB_TIMESTAMP =   "pi_timestamp";
    public static final String DB_ARMED =       "pi_armed";
    public static final String DB_TRIGGERED =   "pi_triggered";
    public static final String DB_CONNECTED =   "pi_connected";
    public static final String DB_TIMEOUT =     "timeout_threshold";
    public static final String DB_CAMERA_STATE ="camera/photo_pipeline_state";
    // pending: sound stuff
    // pending: voip stuff
    public static final String DB_SENSOR_CONFIG = "sensor_config/sensor_config_obj";




    /**
     * Simply subtracts the email suffix from the end to get the username back
     * @param email foobar@pdx.edu as returned by the FirebaseUser object
     * @return foobar, what the user actually enters at the login screen
     */
    public static String stripEmailSuffix(String email) {
        return email.substring(0, email.length() - EMAIL_SUFFIX.length());
    }
}
