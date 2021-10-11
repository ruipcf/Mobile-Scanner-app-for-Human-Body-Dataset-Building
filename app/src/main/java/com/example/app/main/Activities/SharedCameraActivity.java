package com.example.app.main.Activities;

import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.graphics.ImageFormat;
import android.graphics.SurfaceTexture;
import android.hardware.camera2.CameraAccessException;
import android.hardware.camera2.CameraCaptureSession;
import android.hardware.camera2.CameraCharacteristics;
import android.hardware.camera2.CameraDevice;
import android.hardware.camera2.CameraManager;
import android.hardware.camera2.CaptureFailure;
import android.hardware.camera2.CaptureRequest;
import android.hardware.camera2.TotalCaptureResult;
import android.hardware.camera2.params.StreamConfigurationMap;
import android.media.Image;
import android.media.ImageReader;
import android.opengl.GLES20;
import android.opengl.GLSurfaceView;
import android.os.Build;
import android.os.Bundle;
import android.os.ConditionVariable;
import android.os.Handler;
import android.os.HandlerThread;
import android.util.Log;
import android.util.Size;
import android.util.SparseIntArray;
import android.view.Surface;
import android.view.View;
import android.widget.ImageButton;
import android.widget.Toast;

import androidx.activity.result.ActivityResult;
import androidx.activity.result.ActivityResultCallback;
import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;

import com.example.app.R;
import com.example.app.main.Utils.ImageData;
import com.example.app.main.Utils.ImageUtil;
import com.example.app.main.Utils.sensorData;
import com.google.ar.core.ArCoreApk;
import com.google.ar.core.Camera;
import com.google.ar.core.Config;
import com.google.ar.core.Frame;
import com.google.ar.core.Session;
import com.google.ar.core.SharedCamera;

import com.example.app.main.helpers.CameraPermissionHelper;
import com.example.app.main.helpers.DepthSettings;
import com.example.app.main.helpers.DisplayRotationHelper;
import com.example.app.main.helpers.FullScreenHelper;
import com.example.app.main.helpers.SnackbarHelper;
import com.example.app.main.helpers.TrackingStateHelper;
import com.example.app.main.rendering.BackgroundRenderer;

import com.google.ar.core.TrackingState;
import com.google.ar.core.exceptions.CameraNotAvailableException;
import com.google.ar.core.exceptions.UnavailableException;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.EnumSet;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;

import javax.microedition.khronos.egl.EGLConfig;
import javax.microedition.khronos.opengles.GL10;

public class SharedCameraActivity extends AppCompatActivity
    implements GLSurfaceView.Renderer,
        ImageReader.OnImageAvailableListener,
        SurfaceTexture.OnFrameAvailableListener {

  private static final String TAG = SharedCameraActivity.class.getSimpleName();

  private static final SparseIntArray ORIENTATIONS = new SparseIntArray();
  static {
    ORIENTATIONS.append(Surface.ROTATION_0, 90);
    ORIENTATIONS.append(Surface.ROTATION_90, 0);
    ORIENTATIONS.append(Surface.ROTATION_180, 270);
    ORIENTATIONS.append(Surface.ROTATION_270, 180);
  }
  private int SensorOrientation;

  // Whether the surface texture has been attached to the GL context.
  boolean isGlAttached;

  // GL Surface used to draw camera preview image
  private GLSurfaceView surfaceView;

  private final DepthSettings depthSettings = new DepthSettings();

  // ARCore session
  private Session sharedSession;

  // Camera capture session. Used by both non-AR and AR modes
  private CameraCaptureSession captureSession;

  // Reference to the camera system service
  private CameraManager cameraManager;

  // A list of CaptureRequest keys that can cause delays when switching between AR and non-AR modes
  private List<CaptureRequest.Key<?>> keysThatCanCauseCaptureDelaysWhenModified;

  // Camera device. Used by both non-AR and AR modes
  private CameraDevice cameraDevice;

  // Looper handler thread
  private HandlerThread backgroundThread;

  // Looper handler
  private Handler backgroundHandler;

  // ARCore shared camera instance
  private SharedCamera sharedCamera;

  // Camera ID for the camera used by ARCore
  private String cameraId;

  // Ensure GL surface draws only occur when new frames are available
  private final AtomicBoolean shouldUpdateSurfaceTexture = new AtomicBoolean(false);

  // Whether ARCore is currently active
  private boolean arcoreActive;

  // Whether the GL surface has been created
  private boolean surfaceCreated;

  private boolean errorCreatingSession = false;

  // Camera preview capture request builder
  private CaptureRequest.Builder previewCaptureRequestBuilder;

  // Camera capture request builder
  private CaptureRequest.Builder captureRequestBuilder;

  // Image reader that continuously processes CPU images
  private ImageReader cpuImageReader;
  private ImageReader reader;

  private final SnackbarHelper messageSnackbarHelper = new SnackbarHelper();
  private DisplayRotationHelper displayRotationHelper;
  private final TrackingStateHelper trackingStateHelper = new TrackingStateHelper(this);

  // Renderers, see hello_ar_java sample to learn more
  private final BackgroundRenderer backgroundRenderer = new BackgroundRenderer();

  // Prevent any changes to camera capture session after CameraManager.openCamera() is called, but
  // before camera device becomes active
  private boolean captureSessionChangesPossible = true;

  // A check mechanism to ensure that the camera closed properly so that the app can safely exit
  private final ConditionVariable safeToExitApp = new ConditionVariable();

  // variables
  private com.example.app.main.Utils.sensorData sensorData;
  private Image imageToSent = null;
  private Image imageDepthToSent = null;

  private int lastDepthPixelInfo = 0;

  private String IP_address = "192.168.0.1";
  private int IP_port = 12345;

  // Camera device state callback
  private final CameraDevice.StateCallback cameraDeviceCallback =
      new CameraDevice.StateCallback() {
        @Override
        public void onOpened(@NonNull CameraDevice cameraDevice) {
          Log.d(TAG, "Camera device ID " + cameraDevice.getId() + " opened.");
          SharedCameraActivity.this.cameraDevice = cameraDevice;
          createCameraPreviewSession();
        }

        @Override
        public void onClosed(@NonNull CameraDevice cameraDevice) {
          Log.d(TAG, "Camera device ID " + cameraDevice.getId() + " closed.");
          SharedCameraActivity.this.cameraDevice = null;
          safeToExitApp.open();
        }

        @Override
        public void onDisconnected(@NonNull CameraDevice cameraDevice) {
          Log.w(TAG, "Camera device ID " + cameraDevice.getId() + " disconnected.");
          cameraDevice.close();
          SharedCameraActivity.this.cameraDevice = null;
        }

        @Override
        public void onError(@NonNull CameraDevice cameraDevice, int error) {
          Log.e(TAG, "Camera device ID " + cameraDevice.getId() + " error " + error);
          cameraDevice.close();
          SharedCameraActivity.this.cameraDevice = null;
          // Fatal error. Quit application.
          finish();
        }
      };


  // Repeating camera capture session state callback
  CameraCaptureSession.StateCallback cameraSessionStateCallback =
          new CameraCaptureSession.StateCallback() {
        // Called when the camera capture session is first configured after the app
        // is initialized, and again each time the activity is resumed.
        @Override
        public void onConfigured(@NonNull CameraCaptureSession session) {
          Log.d(TAG, "Camera capture session configured.");
          captureSession = session;
          setRepeatingCaptureRequest();
        }

        @Override
        public void onSurfacePrepared(
            @NonNull CameraCaptureSession session, @NonNull Surface surface) {
          Log.d(TAG, "Camera capture surface prepared.");
        }

        @Override
        public void onReady(@NonNull CameraCaptureSession session) {
          Log.d(TAG, "Camera capture session ready.");
        }

        @Override
        public void onActive(@NonNull CameraCaptureSession session) {
          Log.d(TAG, "Camera capture session active.");
          if (!arcoreActive) {
            resumeARCore();
          }
          synchronized (SharedCameraActivity.this) {
            captureSessionChangesPossible = true;
            SharedCameraActivity.this.notify();
          }
        }

        @Override
        public void onCaptureQueueEmpty(@NonNull CameraCaptureSession session) {
          Log.w(TAG, "Camera capture queue empty.");
        }

        @Override
        public void onClosed(@NonNull CameraCaptureSession session) {
          Log.d(TAG, "Camera capture session closed.");
        }

        @Override
        public void onConfigureFailed(@NonNull CameraCaptureSession session) {
          Log.e(TAG, "Failed to configure camera capture session.");
        }
      };


  // Repeating camera capture session capture callback.
  private final CameraCaptureSession.CaptureCallback cameraCaptureCallback =
      new CameraCaptureSession.CaptureCallback() {
        @Override
        public void onCaptureCompleted(
            @NonNull CameraCaptureSession session,
            @NonNull CaptureRequest request,
            @NonNull TotalCaptureResult result) {
          shouldUpdateSurfaceTexture.set(true);
        }

        @Override
        public void onCaptureBufferLost(
            @NonNull CameraCaptureSession session,
            @NonNull CaptureRequest request,
            @NonNull Surface target,
            long frameNumber) {
          Log.e(TAG, "onCaptureBufferLost: " + frameNumber);
        }

        @Override
        public void onCaptureFailed(
            @NonNull CameraCaptureSession session,
            @NonNull CaptureRequest request,
            @NonNull CaptureFailure failure) {
          Log.e(TAG, "onCaptureFailed: " + failure.getFrameNumber() + " " + failure.getReason());
        }

        @Override
        public void onCaptureSequenceAborted(
            @NonNull CameraCaptureSession session, int sequenceId) {
          Log.e(TAG, "onCaptureSequenceAborted: " + sequenceId + " " + session);
        }
      };


  @Override
  protected void onCreate(Bundle savedInstanceState) {
    super.onCreate(savedInstanceState);
    setContentView(R.layout.activity_main);

    // GL surface view that renders camera preview image.
    surfaceView = findViewById(R.id.glsurfaceview);
    surfaceView.setPreserveEGLContextOnPause(true);
    surfaceView.setEGLContextClientVersion(2);
    surfaceView.setEGLConfigChooser(8, 8, 8, 8, 16, 0);
    surfaceView.setRenderer(this);
    surfaceView.setRenderMode(GLSurfaceView.RENDERMODE_CONTINUOUSLY);

    // Helpers
    displayRotationHelper = new DisplayRotationHelper(this);
    sensorData = new sensorData(this);

    messageSnackbarHelper.setMaxLines(4);

    ImageButton settingsButton = findViewById(R.id.settings_button);
    settingsButton.setOnClickListener(
            new View.OnClickListener() {
              @Override
              public void onClick(View v) {
                openYourActivity();
              }
            });
  }

  // Create launcher variable inside onAttach or onCreate or global
  ActivityResultLauncher<Intent> launchActivity = registerForActivityResult(
          new ActivityResultContracts.StartActivityForResult(),
          new ActivityResultCallback<ActivityResult>() {
            @Override
            public void onActivityResult(ActivityResult result) {
              if (result.getResultCode() == Activity.RESULT_OK) {
                Intent data = result.getData();
                IP_address = data.getStringExtra("IP_address");
                IP_port = data.getIntExtra("IP_port",0);
              }
            }
          });

  public void openYourActivity() {
    Intent intent = new Intent(this, ActivitySettings.class);
    intent.putExtra("IP_address", IP_address);
    intent.putExtra("IP_port", IP_port);
    launchActivity.launch(intent);
  }

  @Override
  protected void onDestroy() {
    if (sharedSession != null) {
      sharedSession.close();
      sharedSession = null;
    }
    super.onDestroy();
  }


  private synchronized void waitUntilCameraCaptureSessionIsActive() {
    while (!captureSessionChangesPossible) {
      try {
        this.wait();
      } catch (InterruptedException e) {
        Log.e(TAG, "Unable to wait for a safe time to make changes to the capture session", e);
      }
    }
  }


  @Override
  protected void onResume() {
    super.onResume();
    waitUntilCameraCaptureSessionIsActive();
    startBackgroundThread();
    surfaceView.onResume();

    if (surfaceCreated) {
      openCamera();
    }

    displayRotationHelper.onResume();
    sensorData.onResume();
  }


  @Override
  public void onPause() {
    shouldUpdateSurfaceTexture.set(false);
    surfaceView.onPause();
    waitUntilCameraCaptureSessionIsActive();
    displayRotationHelper.onPause();

    pauseARCore();

    closeCamera();
    stopBackgroundThread();
    sensorData.onPause();
    super.onPause();
  }


  private void resumeARCore() {
    if (sharedSession == null) {
      return;
    }

    if (!arcoreActive) {
      try {
        // To avoid flicker when resuming ARCore mode inform the renderer to not suppress rendering
        // of the frames with zero timestamp.
        backgroundRenderer.suppressTimestampZeroRendering(false);
        sharedSession.resume();
        arcoreActive = true;
        sharedCamera.setCaptureCallback(cameraCaptureCallback, backgroundHandler);

      } catch (Exception e) {
        e.printStackTrace();
      }
    }
  }


  private void pauseARCore() {
    if (arcoreActive) {
      sharedSession.pause();
      arcoreActive = false;
    }
  }


  private void setRepeatingCaptureRequest() {
    // Called when starting non-AR mode or switching to non-AR mode.
    // Also called when app starts in AR mode, or resumes in AR mode
    try {
      captureSession.setRepeatingRequest(
              previewCaptureRequestBuilder.build(), cameraCaptureCallback, backgroundHandler);
    } catch (CameraAccessException e) {
      Log.e(TAG, "Failed to set repeating request", e);
    }
  }


  private void createCameraPreviewSession() {
    try {
      // Note that isGlAttached will be set to true in AR mode in onDrawFrame()
      sharedSession.setCameraTextureName(backgroundRenderer.getTextureId());
      sharedCamera.getSurfaceTexture().setOnFrameAvailableListener(this);

      // Create an ARCore compatible capture request using `TEMPLATE_RECORD`
      previewCaptureRequestBuilder =
              cameraDevice.createCaptureRequest(CameraDevice.TEMPLATE_RECORD);

      // Build surfaces list, starting with ARCore provided surfaces
      List<Surface> surfaceList = sharedCamera.getArCoreSurfaces();

      // Add a CPU image reader surface. On devices that don't support CPU image access, the image
      // may arrive significantly later, or not arrive at all
      surfaceList.add(cpuImageReader.getSurface());
      //surfaceList.add(reader.getSurface());

      // Surface list should now contain three surfaces:
      // 0. sharedCamera.getSurfaceTexture()
      // 1. …
      // 2. cpuImageReader.getSurface()

      // Add ARCore surfaces and CPU image surface targets
      for (Surface surface : surfaceList) {
        previewCaptureRequestBuilder.addTarget(surface);
      }

      // Wrap our callback in a shared camera callback
      CameraCaptureSession.StateCallback wrappedCallback =
              sharedCamera.createARSessionStateCallback(cameraSessionStateCallback, backgroundHandler);

      // Create camera capture session for camera preview using ARCore wrapped callback.
      cameraDevice.createCaptureSession(surfaceList, wrappedCallback, backgroundHandler);
    } catch (CameraAccessException e) {
      e.printStackTrace();
    }
  }


  private void startBackgroundThread() {
    backgroundThread = new HandlerThread("sharedCameraBackground");
    backgroundThread.start();
    backgroundHandler = new Handler(backgroundThread.getLooper());
  }


  private void stopBackgroundThread() {
    if (backgroundThread != null) {
      backgroundThread.quitSafely();
      try {
        backgroundThread.join();
        backgroundThread = null;
        backgroundHandler = null;
      } catch (InterruptedException e) {
        Log.e(TAG, "Interrupted while trying to join background handler thread", e);
      }
    }
  }


  private void openCamera() {
    if (cameraDevice != null) {
      return;
    }

    if (!CameraPermissionHelper.hasCameraPermission(this)) {
      CameraPermissionHelper.requestCameraPermission(this);
      return;
    }

    if (!isARCoreSupportedAndUpToDate()) {
      return;
    }

    if (sharedSession == null) {
      try {
        sharedSession = new Session(this, EnumSet.of(Session.Feature.SHARED_CAMERA));
      } catch (Exception e) {
        errorCreatingSession = true;
        messageSnackbarHelper.showError(
            this, "Failed to create ARCore session that supports camera sharing");
        Log.e(TAG, "Failed to create ARCore session that supports camera sharing", e);
        return;
      }

      errorCreatingSession = false;

      Config config = sharedSession.getConfig();
      config.setFocusMode(Config.FocusMode.AUTO);
      boolean isDepthSupported = sharedSession.isDepthModeSupported(Config.DepthMode.AUTOMATIC);
      if (isDepthSupported) {
        config.setDepthMode(Config.DepthMode.AUTOMATIC);
      }
      sharedSession.configure(config);
    }

    // Store the ARCore shared camera reference.
    sharedCamera = sharedSession.getSharedCamera();
    // Store the ID of the camera used by ARCore.
    cameraId = sharedSession.getCameraConfig().getCameraId();

    // Use the currently configured CPU image size.
    //Size desiredCpuImageSize = sharedSession.getCameraConfig().getImageSize();

    cpuImageReader =
        ImageReader.newInstance(
                1920,
                1080,
                ImageFormat.YUV_420_888,
                1);

    cpuImageReader.setOnImageAvailableListener(this, backgroundHandler);

    // When ARCore is running, make sure it also updates our CPU image surface.
    sharedCamera.setAppSurfaces(this.cameraId, Arrays.asList(cpuImageReader.getSurface()));

    try {
      // Wrap our callback in a shared camera callback.
      CameraDevice.StateCallback wrappedCallback =
          sharedCamera.createARDeviceStateCallback(cameraDeviceCallback, backgroundHandler);

      // Store a reference to the camera system service.
      cameraManager = (CameraManager) this.getSystemService(Context.CAMERA_SERVICE);

      // Get the characteristics for the ARCore camera.
      CameraCharacteristics characteristics = cameraManager.getCameraCharacteristics(this.cameraId);
      SensorOrientation = characteristics.get(CameraCharacteristics.SENSOR_ORIENTATION);

      // On Android P and later, get list of keys that are difficult to apply per-frame and can
      // result in unexpected delays when modified during the capture session lifetime.
      if (Build.VERSION.SDK_INT >= 28) {
        keysThatCanCauseCaptureDelaysWhenModified = characteristics.getAvailableSessionKeys();
        if (keysThatCanCauseCaptureDelaysWhenModified == null) {
          // Initialize the list to an empty list if getAvailableSessionKeys() returns null.
          keysThatCanCauseCaptureDelaysWhenModified = new ArrayList<>();
        }
      }

      // Prevent app crashes due to quick operations on camera open / close by waiting for the
      // capture session's onActive() callback to be triggered.
      captureSessionChangesPossible = false;

      // Open the camera device using the ARCore wrapped callback.
      cameraManager.openCamera(cameraId, wrappedCallback, backgroundHandler);
    } catch (CameraAccessException | IllegalArgumentException | SecurityException e) {
      Log.e(TAG, "Failed to open camera", e);
    }
  }


  private <T> boolean checkIfKeyCanCauseDelay(CaptureRequest.Key<T> key) {
    if (Build.VERSION.SDK_INT >= 28) {
      // On Android P and later, return true if key is difficult to apply per-frame.
      return keysThatCanCauseCaptureDelaysWhenModified.contains(key);
    } else {
      // On earlier Android versions, log a warning since there is no API to determine whether
      // the key is difficult to apply per-frame. Certain keys such as CONTROL_AE_TARGET_FPS_RANGE
      // are known to cause a noticeable delay on certain devices.
      // If avoiding unexpected capture delays when switching between non-AR and AR modes is
      // important, verify the runtime behavior on each pre-Android P device on which the app will
      // be distributed. Note that this device-specific runtime behavior may change when the
      // device's operating system is updated.
      Log.w(
          TAG,
          "Changing "
              + key
              + " may cause a noticeable capture delay. Please verify actual runtime behavior on"
              + " specific pre-Android P devices that this app will be distributed on.");
      // Allow the change since we're unable to determine whether it can cause unexpected delays.
      return false;
    }
  }


  private void closeCamera() {
    if (captureSession != null) {
      captureSession.close();
      captureSession = null;
    }
    if (cameraDevice != null) {
      waitUntilCameraCaptureSessionIsActive();
      safeToExitApp.close();
      cameraDevice.close();
      safeToExitApp.block();
    }
    if (cpuImageReader != null) {
      cpuImageReader.close();
      cpuImageReader = null;
    }
    if (reader != null) {
      reader.close();
      reader = null;
    }
  }


  @Override
  public void onFrameAvailable(SurfaceTexture surfaceTexture) {
    // Log.d(TAG, "onFrameAvailable()");
  }


  interface RGBImageCapDelegate {
    void onFinished();
  }


  public void getPreviewImage(RGBImageCapDelegate delegate){
    cpuImageReader.setOnImageAvailableListener(
      new ImageReader.OnImageAvailableListener() {
        @Override
        public void onImageAvailable(ImageReader imageReader) {
          Image image = imageReader.acquireLatestImage();
          if (image!=null) {
            //byte[] jpegData = ImageUtil.imageToByteArray(image);
            //ImageData.sendImagePreview(jpegData);
            imageToSent = image;
            delegate.onFinished();
            imageToSent.close();
            image.close();
        }
      }
      },backgroundHandler);
  }


  @Override
  public void onImageAvailable(ImageReader imageReader) {
    Image image = imageReader.acquireLatestImage();
    if (image == null) {
      Log.w(TAG, "onImageAvailable: Skipping null image.");
      return;
    }
    //Log.e("tagee", String.valueOf(image.getWidth()));

    imageToSent = image;
    image.close();
  }


  @Override
  public void onRequestPermissionsResult(int requestCode, String[] permissions, int[] results) {
    super.onRequestPermissionsResult(requestCode, permissions, results);
    if (!CameraPermissionHelper.hasCameraPermission(this)) {
      Toast.makeText(
              getApplicationContext(),
              "Camera permission is needed to run this application",
              Toast.LENGTH_LONG)
          .show();
      if (!CameraPermissionHelper.shouldShowRequestPermissionRationale(this)) {
        // Permission denied with checking "Do not ask again".
        CameraPermissionHelper.launchPermissionSettings(this);
      }
      finish();
    }
  }


  @Override
  public void onWindowFocusChanged(boolean hasFocus) {
    super.onWindowFocusChanged(hasFocus);
    FullScreenHelper.setFullScreenOnWindowFocusChanged(this, hasFocus);
  }


  @Override
  public void onSurfaceCreated(GL10 gl, EGLConfig config) {
    surfaceCreated = true;

    // Set GL clear color to black.
    GLES20.glClearColor(0f, 0f, 0f, 1.0f);

    // Prepare the rendering objects. This involves reading shaders, so may throw an IOException.
    try {
      // Create the camera preview image texture. Used in non-AR and AR mode
      backgroundRenderer.createOnGlThread(this);

      openCamera();
    } catch (IOException e) {
      Log.e(TAG, "Failed to read an asset file", e);
    }
  }


  @Override
  public void onSurfaceChanged(GL10 gl, int width, int height) {
    GLES20.glViewport(0, 0, width, height);
    displayRotationHelper.onSurfaceChanged(width, height);
  }


  @Override
  public void onDrawFrame(GL10 gl) {
    // Use the cGL clear color specified in onSurfaceCreated() to erase the GL surface.
    GLES20.glClear(GLES20.GL_COLOR_BUFFER_BIT | GLES20.GL_DEPTH_BUFFER_BIT);

    if (!shouldUpdateSurfaceTexture.get()) {
      return;
    }

    // Handle display rotations.
    displayRotationHelper.updateSessionIfNeeded(sharedSession);

    try {
      onDrawFrameARCore();
    } catch (CameraNotAvailableException e) {
      e.printStackTrace();
    }
  }


  public void onDrawFrameARCore() throws CameraNotAvailableException {
    if (!arcoreActive) {
      return;
    }

    if (errorCreatingSession) {
      return;
    }

    Frame frame = sharedSession.update();
    Camera camera = frame.getCamera();

    if (camera.getTrackingState() == TrackingState.TRACKING
            && (depthSettings.useDepthForOcclusion()
            || depthSettings.depthColorVisualizationEnabled())) {
      try (Image depthImage = frame.acquireDepthImage()) {

        // send data
        //shotPhoto();
        imageDepthToSent = frame.acquireDepthImage();
        sendInfo(depthImage,sensorData.data);

      } catch (Exception e) {
        e.printStackTrace();
      }
    }
    // ARCore attached the surface to GL context using the texture ID we provided
    // in createCameraPreviewSession() via sharedSession.setCameraTextureName(…).
    isGlAttached = true;
    // If frame is ready, render camera preview image to the GL surface.
    backgroundRenderer.draw(frame);

    // Keep the screen unlocked while tracking, but allow it to lock when tracking stops.
    trackingStateHelper.updateKeepScreenOnFlag(camera.getTrackingState());
  }


  private boolean isARCoreSupportedAndUpToDate() {
    // Make sure ARCore is installed and supported on this device.
    ArCoreApk.Availability availability = ArCoreApk.getInstance().checkAvailability(this);
    switch (availability) {
      case SUPPORTED_INSTALLED:
        break;
      case SUPPORTED_APK_TOO_OLD:
      case SUPPORTED_NOT_INSTALLED:
        try {
          // Request ARCore installation or update if needed.
          ArCoreApk.InstallStatus installStatus =
              ArCoreApk.getInstance().requestInstall(this, /*userRequestedInstall=*/ true);
          switch (installStatus) {
            case INSTALL_REQUESTED:
              Log.e(TAG, "ARCore installation requested.");
              return false;
            case INSTALLED:
              break;
          }
        } catch (UnavailableException e) {
          Log.e(TAG, "ARCore not installed", e);
          runOnUiThread(
              () ->
                  Toast.makeText(
                          getApplicationContext(), "ARCore not installed\n" + e, Toast.LENGTH_LONG)
                      .show());
          finish();
          return false;
        }
        break;
      case UNKNOWN_ERROR:
      case UNKNOWN_CHECKING:
      case UNKNOWN_TIMED_OUT:
      case UNSUPPORTED_DEVICE_NOT_CAPABLE:
        Log.e(TAG,
                "ARCore is not supported on this device, ArCoreApk.checkAvailability() returned "
                + availability);
        runOnUiThread(() -> Toast.makeText(
                        getApplicationContext(),
                        "ARCore is not supported on this device, "
                            + "ArCoreApk.checkAvailability() returned "
                            + availability,
                        Toast.LENGTH_LONG)
                .show());
        return false;
    }
    return true;
  }


  private int getOrientation(int rotation) {
    // Sensor orientation is 90 for most devices, or 270 for some devices (eg. Nexus 5X)
    // We have to take that into account and rotate JPEG properly.
    // For devices with orientation of 90, we simply return our mapping from ORIENTATIONS.
    // For devices with orientation of 270, we need to rotate the JPEG 180 degrees.
    return (ORIENTATIONS.get(rotation) + SensorOrientation + 270) % 360;
  }


  public void shotPhoto() {
    try {
      // Get the characteristics for the ARCore camera.
      CameraCharacteristics characteristics = cameraManager.getCameraCharacteristics(this.cameraId);

      // Get the higher resolution for image capture
      StreamConfigurationMap streamConfigurationMap = characteristics.get(CameraCharacteristics.SCALER_STREAM_CONFIGURATION_MAP);
      Size[] sizes = streamConfigurationMap.getOutputSizes(SurfaceTexture.class);
      int width = sizes[0].getWidth();
      int height = sizes[0].getHeight();

      reader = ImageReader.newInstance(
                      width,
                      height,
                      ImageFormat.JPEG,
                      2);
      reader.setOnImageAvailableListener(this, backgroundHandler);

      sharedCamera.setAppSurfaces(this.cameraId, Arrays.asList(reader.getSurface()));

      CaptureRequest.Builder captureRequestBuilder = cameraDevice.createCaptureRequest(cameraDevice.TEMPLATE_STILL_CAPTURE);
      captureRequestBuilder.addTarget(reader.getSurface());
      captureRequestBuilder.set(CaptureRequest.JPEG_ORIENTATION, SensorOrientation);
      CaptureRequest captureRequest = captureRequestBuilder.build();
      captureSession.capture(captureRequest, cameraCaptureCallback, backgroundHandler);

    } catch (CameraAccessException e) {
      e.printStackTrace();
    }
  }


  public void sendInfo(Image depthImage, String dataSensor) {
    // verify if image is ready to be sent
    int testing2 = ImageData.getMillimetersDepth(depthImage, 80, 45);
    if (testing2 == 0)
      return;

    runOnUiThread(new Runnable() {
      @Override
      public void run() {
        getPreviewImage(new RGBImageCapDelegate() {
          @Override
          public void onFinished() {
            if (imageToSent != null) {
              byte[] jpegData = ImageUtil.imageToByteArray(imageToSent);
              Log.e("abc",String.valueOf(ImageData.getMillimetersDepth(imageDepthToSent, 80, 45)));
              Log.e("abcc",String.valueOf(jpegData[1]));
              if (ImageData.getMillimetersDepth(imageDepthToSent, 80, 45) == lastDepthPixelInfo) {
                return;
              }
              ImageData.sendAll(IP_address, IP_port,jpegData,imageDepthToSent,dataSensor);
              lastDepthPixelInfo = ImageData.getMillimetersDepth(imageDepthToSent, 80, 45);
            } else {
              Log.e("tagee", "Image error");
            }
          }
        });
      }
    });
  }

}
