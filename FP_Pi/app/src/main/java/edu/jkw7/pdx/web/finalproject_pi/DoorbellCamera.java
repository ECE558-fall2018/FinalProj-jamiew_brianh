package edu.jkw7.pdx.web.finalproject_pi;

import android.content.Context;
import android.graphics.ImageFormat;
import android.hardware.camera2.CameraAccessException;
import android.hardware.camera2.CameraCaptureSession;
import android.hardware.camera2.CameraCharacteristics;
import android.hardware.camera2.CameraDevice;
import android.hardware.camera2.CameraManager;
import android.hardware.camera2.CaptureRequest;
import android.hardware.camera2.CaptureResult;
import android.hardware.camera2.TotalCaptureResult;
import android.hardware.camera2.params.StreamConfigurationMap;
import android.media.ImageReader;
import android.os.Handler;
import android.support.annotation.NonNull;
import android.util.Log;
import android.util.Size;

import java.util.Arrays;
import java.util.Collections;

import static android.content.Context.CAMERA_SERVICE;

/**
 * Helper class to deal with methods to deal with images from the camera.
 */
public class DoorbellCamera {
    private static final String TAG = "SEC_Camera";

    private static final int IMAGE_WIDTH = 640;
    private static final int IMAGE_HEIGHT = 480;
    private static final int IMAGE_WIDTH_HR = 3280;
    private static final int IMAGE_HEIGHT_HR = 2464;
    private static final int MAX_IMAGES = 1;

    private CameraDevice mCameraDevice;

    private CameraCaptureSession mCaptureSession;

    /**
     * An {@link ImageReader} that handles still image capture.
     */
    private ImageReader mImageReader;
    private ImageReader mImageReaderHR;
    private Handler mHandler;

    // Lazy-loaded singleton, so only one instance of the camera is created.
    private DoorbellCamera() {
    }

    private static class InstanceHolder {
        private static DoorbellCamera mCamera = new DoorbellCamera();
    }

    public static DoorbellCamera getInstance() {
        return InstanceHolder.mCamera;
    }

    /**
     * Initialize the camera device
     */
    public void initializeCamera(Context context,
                                 Handler backgroundHandler,
                                 ImageReader.OnImageAvailableListener imageAvailableListener,
                                 ImageReader.OnImageAvailableListener imageAvailableListenerHR) {
        // Discover the camera instance
        CameraManager manager = (CameraManager) context.getSystemService(CAMERA_SERVICE);
        String[] camIds = {};
        try {
            camIds = manager.getCameraIdList();
            Log.d(TAG, "list of cameras: " + Arrays.toString(camIds));
        } catch (CameraAccessException e) {
            Log.e(TAG, "Cam access exception getting IDs", e);
        }
        if (camIds.length < 1) {
            Log.e(TAG, "No cameras found");
            return;
        }
        String id = camIds[0];
        Log.d(TAG, "Using camera id " + id);

        mHandler = backgroundHandler;
        // Initialize the image processor for small
        mImageReader = ImageReader.newInstance(IMAGE_WIDTH, IMAGE_HEIGHT,
                ImageFormat.JPEG, MAX_IMAGES);
        mImageReader.setOnImageAvailableListener(
                imageAvailableListener, backgroundHandler);

        // initialize the image processor for big
        mImageReaderHR = ImageReader.newInstance(IMAGE_WIDTH_HR, IMAGE_HEIGHT_HR,
                ImageFormat.JPEG, MAX_IMAGES);
        mImageReaderHR.setOnImageAvailableListener(
                imageAvailableListenerHR, backgroundHandler);

        // Open the camera resource
        try {
            manager.openCamera(id, mStateCallback, backgroundHandler);
        } catch (CameraAccessException cae) {
            Log.d(TAG, "Camera access exception", cae);
        }
    }

    /**
     * Callback handling device state changes
     */
    private CameraDevice.StateCallback mStateCallback = new CameraDevice.StateCallback() {
        @Override public void onOpened(CameraDevice cameraDevice) {
            Log.d(TAG, "Opened camera.");
            mCameraDevice = cameraDevice;
        }

        @Override public void onDisconnected(CameraDevice cameraDevice) {
            Log.d(TAG, "Camera disconnected, closing.");
            cameraDevice.close();
        }

        @Override public void onError(CameraDevice cameraDevice, int i) {
            Log.d(TAG, "Camera device error, closing.");
            cameraDevice.close();
        }

        @Override public void onClosed(CameraDevice cameraDevice) {
            Log.d(TAG, "Closed camera, releasing");
            mCameraDevice = null;
        }
    };

    /**
     * Begin a still image capture
     */
    public void takePicture(boolean isHR) {
        Log.d(TAG, "Inside take picture func, HR="+isHR);
        if (mCameraDevice == null) {
            Log.e(TAG, "Cannot capture image. Camera not initialized.");
            return;
        }

        // Here, we create a CameraCaptureSession for capturing still images.

        try {
            if(isHR) {
                // TODO: might be able to get multiple images from one session by adding to this singletonList? but there's only one listener for an output? probably won't work
                // TODO: if we did pursue this we should make 2 onImageAvailable listeners tho
                mCameraDevice.createCaptureSession(
                        Collections.singletonList(mImageReaderHR.getSurface()),
                        mSessionCallbackHR,
                        null);
            } else {
                mCameraDevice.createCaptureSession(
                        Collections.singletonList(mImageReader.getSurface()),
                        mSessionCallback,
                        null);
            }
        } catch (CameraAccessException cae) {
            Log.e(TAG, "access exception while preparing pic", cae);
        }
    }

    /**
     * Callback handling session state changes
     */
    private CameraCaptureSession.StateCallback mSessionCallback = new CameraCaptureSession.StateCallback() {
        @Override public void onConfigured(@NonNull CameraCaptureSession cameraCaptureSession) {
            // The camera is already closed
            if (mCameraDevice == null) { Log.e(TAG, "Camera Device is null in config!"); return; }

            // When the session is ready, we start capture.
            mCaptureSession = cameraCaptureSession;
            triggerImageCapture(false);
        }

        @Override public void onConfigureFailed(@NonNull CameraCaptureSession cameraCaptureSession) {
            Log.e(TAG, "Failed to configure camera");
        }
    };

    private CameraCaptureSession.StateCallback mSessionCallbackHR = new CameraCaptureSession.StateCallback() {
        @Override public void onConfigured(@NonNull CameraCaptureSession cameraCaptureSession) {
            // The camera is already closed
            if (mCameraDevice == null) { Log.e(TAG, "Camera Device is null in config-HR!"); return; }

            // When the session is ready, we start capture.
            Log.d(TAG, "Trigger image capture for HiRes");
            mCaptureSession = cameraCaptureSession;
            triggerImageCapture(true);
        }

        @Override public void onConfigureFailed(@NonNull CameraCaptureSession cameraCaptureSession) {
            Log.e(TAG, "Failed to configure camera");
        }
    };

    /**
     * Execute a new capture request within the active session
     */
    private void triggerImageCapture(boolean isHR) {
        Log.d(TAG, "Inside trigger image capture");
        try {
            CaptureRequest.Builder captureBuilder =
                    mCameraDevice.createCaptureRequest(CameraDevice.TEMPLATE_STILL_CAPTURE);
            // TODO: can add multiple targets, must be surfaces taht were added in the config stage... maybe we can do double sizes after all?
            captureBuilder.addTarget(mImageReader.getSurface());
            captureBuilder.set(CaptureRequest.CONTROL_AE_MODE, CaptureRequest.CONTROL_AE_MODE_ON);
            Log.d(TAG, "Session initialized.");
            mCaptureSession.capture(captureBuilder.build(), mCaptureCallback, mHandler);
            Log.d(TAG, "Session init, pt 2");
        } catch (CameraAccessException cae) {
            Log.e(TAG, "camera capture exception", cae);
        }
    }

    /**
     * Callback handling capture session events
     */
    private final CameraCaptureSession.CaptureCallback mCaptureCallback =
            new CameraCaptureSession.CaptureCallback() {

                @Override
                public void onCaptureProgressed(@NonNull CameraCaptureSession session,
                                                @NonNull CaptureRequest request,
                                                @NonNull CaptureResult partialResult) {
                    Log.d(TAG, "Partial result?");
                }

                @Override
                public void onCaptureCompleted(@NonNull CameraCaptureSession session,
                                               @NonNull CaptureRequest request,
                                               @NonNull TotalCaptureResult result) {
                    if (session != null) {
                        session.close();
                        mCaptureSession = null;
                        Log.d(TAG, "onCaptureCompleted!");
                    } else {
                        Log.e(TAG, "Session is unexpectedly null!");
                    }
                }
            };


    /**
     * Close the camera resources
     */
    public void shutDown() {
        if (mCameraDevice != null) {
            mCameraDevice.close();
        }
    }

    /**
     * Helpful debugging method:  Dump all supported camera formats to log.  You don't need to run
     * this for normal operation, but it's very helpful when porting this code to different
     * hardware.
     */
    public static void dumpFormatInfo(Context context) {
        CameraManager manager = (CameraManager) context.getSystemService(CAMERA_SERVICE);
        String[] camIds = {};
        try {
            camIds = manager.getCameraIdList();
        } catch (CameraAccessException e) {
            Log.d(TAG, "Cam access exception getting IDs");
        }
        if (camIds.length < 1) {
            Log.d(TAG, "No cameras found");
        }
        String id = camIds[0];
        Log.d(TAG, "Using camera id " + id);
        try {
            CameraCharacteristics characteristics = manager.getCameraCharacteristics(id);
            StreamConfigurationMap configs = characteristics.get(
                    CameraCharacteristics.SCALER_STREAM_CONFIGURATION_MAP);
            for (int format : configs.getOutputFormats()) {
                Log.d(TAG, "Getting sizes for format: " + format);
                for (Size s : configs.getOutputSizes(format)) {
                    Log.d(TAG, "\t" + s.toString());
                }
            }
            int[] effects = characteristics.get(CameraCharacteristics.CONTROL_AVAILABLE_EFFECTS);
            for (int effect : effects) {
                Log.d(TAG, "Effect available: " + effect);
            }
        } catch (CameraAccessException e) {
            Log.d(TAG, "Cam access exception getting characteristics.");
        }
    }
}