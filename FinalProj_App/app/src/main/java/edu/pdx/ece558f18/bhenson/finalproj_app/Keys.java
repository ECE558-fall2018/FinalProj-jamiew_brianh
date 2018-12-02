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
    public static final String KEY_USER = PACKAGE + "username";
    public static final String KEY_PASS = PACKAGE + "password";
    public static final String KEY_AUTOLOGIN = PACKAGE + "autologin";




    public static final Boolean DEFAULT_AUTOLOGIN = true;


    /**
     * Simply subtracts the email suffix from the end to get the username back
     * @param email foobar@pdx.edu as returned by the FirebaseUser object
     * @return foobar, what the user actually enters at the login screen
     */
    public static String stripEmailSuffix(String email) {
        return email.substring(0, email.length() - EMAIL_SUFFIX.length());
    }
}
