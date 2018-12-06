/*
 * Copyright 2016, The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package edu.pdx.ece558f18.bhenson.finalproj_app;
/*
 * This class was taken from the following website on 12/5/2018
 * https://github.com/androidthings/doorbell/blob/master/app/src/main/java/com/example/androidthings/doorbell/DoorbellCamera.java
 */

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
import android.util.Log;
import android.util.Size;

import java.util.Collections;

import static android.content.Context.CAMERA_SERVICE;

/**
 * Helper class to deal with methods to deal with images from the camera.
 */
public class DoorbellCamera {
    private static final String TAG = DoorbellCamera.class.getSimpleName();

    //private static final int IMAGE_WIDTH = 320;
    //private static final int IMAGE_HEIGHT = 240;
    private static final int IMAGE_WIDTH = 640;
    private static final int IMAGE_HEIGHT = 480;
    private static final int IMAGE_WIDTH_HR = 3200;
    private static final int IMAGE_HEIGHT_HR = 2400;
    private static final int MAX_IMAGES = 1;

    private CameraDevice mCameraDevice;

    private CameraCaptureSession mCaptureSession;
    private CameraCaptureSession mCaptureSessionHR;

    /**
     * An {@link ImageReader} that handles still image capture.
     */
    private ImageReader mImageReader;
    private ImageReader mImageReaderHR;

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
        Log.d(TAG, "In function initializeCamera ");
        Log.d(TAG, "Context is: " + context.toString());
        CameraManager manager = (CameraManager) context.getSystemService(CAMERA_SERVICE);
        Log.d(TAG, "Tried to set up camera manager ... ");
        String[] camIds = {};
        try {
            camIds = manager.getCameraIdList();
            Log.d(TAG, "Here is the camera ID list: " + camIds[0].toString() +".");
        } catch (CameraAccessException e) {
            Log.e(TAG, "Cam access exception getting IDs", e);
        }
        if (camIds.length < 1) {
            Log.e(TAG, "No cameras found");
            return;
        }
        String id = camIds[0];
        Log.d(TAG, "Using camera id " + id);

        // Initialize the image processor
        mImageReader = ImageReader.newInstance(IMAGE_WIDTH, IMAGE_HEIGHT,
                ImageFormat.JPEG, MAX_IMAGES);
        mImageReader.setOnImageAvailableListener(
                imageAvailableListener, backgroundHandler);

        // Create HiRes image processor

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
        Log.d(TAG, "End of initialize Camera method");
    }

    /**
     * Callback handling device state changes
     */
    private final CameraDevice.StateCallback mStateCallback = new CameraDevice.StateCallback() {
        @Override
        public void onOpened(CameraDevice cameraDevice) {
            Log.d(TAG, "Opened camera.");
            if(cameraDevice == null) {
                Log.e(TAG, "Camera device is being set to null!");
            }
            mCameraDevice = cameraDevice;
        }

        @Override
        public void onDisconnected(CameraDevice cameraDevice) {
            Log.d(TAG, "Camera disconnected, closing.");
            cameraDevice.close();
        }

        @Override
        public void onError(CameraDevice cameraDevice, int i) {
            Log.d(TAG, "Camera device error, closing.");
            cameraDevice.close();
        }

        @Override
        public void onClosed(CameraDevice cameraDevice) {
            Log.d(TAG, "Closed camera, releasing");
            mCameraDevice = null;
        }
    };

    /**
     * Begin a still image capture
     */
    public void takePicture() {
        Log.d(TAG, "Taking a picture ...");
        if (mCameraDevice == null) {
            Log.e(TAG, "Cannot capture image. Camera not initialized.");
            return;
        }

        // Here, we create a CameraCaptureSession for capturing still images.
        try {
            mCameraDevice.createCaptureSession(
                    Collections.singletonList(mImageReader.getSurface()),
                    mSessionCallback,
                    null);
            mCameraDevice.createCaptureSession(
                    Collections.singletonList(mImageReaderHR.getSurface()),
                    mSessionCallbackHR,
                    null);
        } catch (CameraAccessException cae) {
            Log.e(TAG, "access exception while preparing pic", cae);
        }
    }

    /**
     * Callback handling session state changes
     */
    private CameraCaptureSession.StateCallback mSessionCallback = new CameraCaptureSession.StateCallback() {
        @Override public void onConfigured(CameraCaptureSession cameraCaptureSession) {
            // The camera is already closed
            if (mCameraDevice == null) { return; }

            // When the session is ready, we start capture.
            mCaptureSession = cameraCaptureSession;
            triggerImageCapture(false);
        }

        @Override public void onConfigureFailed(CameraCaptureSession cameraCaptureSession) { Log.e(TAG, "Failed to configure camera"); }
    };

    private CameraCaptureSession.StateCallback mSessionCallbackHR = new CameraCaptureSession.StateCallback() {
        @Override public void onConfigured(CameraCaptureSession cameraCaptureSession) {
            // The camera is already closed
            if (mCameraDevice == null) { return; }

            // When the session is ready, we start capture.
            mCaptureSessionHR = cameraCaptureSession;
            triggerImageCapture(true);
        }

        @Override public void onConfigureFailed(CameraCaptureSession cameraCaptureSession) { Log.e(TAG, "Failed to configure camera"); }
    };

    /**
     * Execute a new capture request within the active session
     */
    private void triggerImageCapture(boolean is_hires) {
        try {
            final CaptureRequest.Builder captureBuilder =
                    mCameraDevice.createCaptureRequest(CameraDevice.TEMPLATE_STILL_CAPTURE);
            if(is_hires)
                captureBuilder.addTarget(mImageReader.getSurface());
            else
                captureBuilder.addTarget(mImageReaderHR.getSurface());
            captureBuilder.set(CaptureRequest.CONTROL_AE_MODE, CaptureRequest.CONTROL_AE_MODE_ON);
            Log.d(TAG, "Session initialized.");
            if(is_hires)
                mCaptureSession.capture(captureBuilder.build(), mCaptureCallback, null);
            else
                mCaptureSessionHR.capture(captureBuilder.build(), mCaptureCallback, null);
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
                public void onCaptureProgressed(CameraCaptureSession session,
                                                CaptureRequest request,
                                                CaptureResult partialResult) {
                    Log.d(TAG, "Partial result");
                }

                @Override
                public void onCaptureCompleted(CameraCaptureSession session,
                                               CaptureRequest request,
                                               TotalCaptureResult result) {
                    if (session != null) {
                        session.close();
                        //mCaptureSession = null;
                        Log.d(TAG, "CaptureSession closed");
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