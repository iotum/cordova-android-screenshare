package com.iotum.cordovaandroidscreenshare;

import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.graphics.Bitmap;
import android.graphics.Bitmap.CompressFormat;
import android.graphics.PixelFormat;
import android.graphics.Point;
import android.hardware.display.DisplayManager;
import android.hardware.display.VirtualDisplay;
import android.media.Image;
import android.media.ImageReader;
import android.media.projection.MediaProjection;
import android.media.projection.MediaProjectionManager;
import android.os.Handler;
import android.os.Looper;
import android.util.DisplayMetrics;
import android.util.Log;
import android.util.Base64;
import android.view.Display;
import android.view.OrientationEventListener;

import java.io.ByteArrayOutputStream;
import java.nio.ByteBuffer;
import java.util.Timer;
import java.util.TimerTask;
import java.lang.Thread;

import org.apache.cordova.CallbackContext;
import org.apache.cordova.CordovaPlugin;
import org.apache.cordova.PluginResult;
import org.apache.cordova.CordovaInterface;
import org.apache.cordova.CordovaWebView;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

public class CordovaAndroidScreenshare extends CordovaPlugin {
  private static final String TAG = CordovaAndroidScreenshare.class.getName();
  private static final int REQUEST_CODE = 100;
  private static final String SCREENCAP_NAME = "screencap";
  // Allows content to be mirrored on private displays when no content is being shown.
  // private static final int VIRTUAL_DISPLAY_FLAGS = DisplayManager.VIRTUAL_DISPLAY_FLAG_AUTO_MIRROR;
  // VIRTUAL_DISPLAY_FLAG_OWN_CONTENT_ONLY is used in conjunction with VIRTUAL_DISPLAY_FLAG_PUBLIC.
  // Ordinarily public virtual displays will automatically mirror the content of the default display
  // if they have no windows of their own. When this flag is specified, the virtual display will only
  // ever show its own content and will be blanked instead if it has no windows.
  private static final int VIRTUAL_DISPLAY_FLAGS = DisplayManager.VIRTUAL_DISPLAY_FLAG_OWN_CONTENT_ONLY
      | DisplayManager.VIRTUAL_DISPLAY_FLAG_PUBLIC;
  private static MediaProjection sMediaProjection;

  private MediaProjectionManager mProjectionManager;
  private ImageReader mImageReader;
  private Handler mHandler;
  private Display mDisplay;
  private VirtualDisplay mVirtualDisplay;
  private int mDensity;
  private int mWidth;
  private int mHeight;
  private int mRotation;
  private OrientationChangeCallback mOrientationChangeCallback;

  private CallbackContext mCallbackContext;

  private Timer mTimer;
  private boolean mReady = true;
  private int mFps;
  private int mCompression;
  private int mPendingFps;
  private int mPendingCompression;

  @Override
  public boolean execute(String action, JSONArray args, final CallbackContext callbackContext) throws JSONException {
    mCallbackContext = callbackContext;
    if (action.equals("startProjection")) {
      int fps = args.isNull(0) ? 5 : args.getInt(0);
      int compression = args.isNull(1) ? 100 : args.getInt(1);
      if (fps <= 0 || fps > 10) {
        fps = 5;
      }
      if (compression <= 0 || compression > 100) {
        compression = 100;
      }
      mPendingFps = fps;
      mPendingCompression = compression;

      startProjection();
      return true;
    } else if (action.equals("stopProjection")) {
      stopProjection();
      return true;
    }
    callbackContext.error("action not found");
    return false;
  }

  private class ImageAvailableListener implements ImageReader.OnImageAvailableListener {
    @Override
    public void onImageAvailable(ImageReader reader) {
      Image image = null;
      Bitmap bitmap = null;

      try {
        image = reader.acquireLatestImage();
        if (mReady) {
          mReady = false;
        } else {
          return;
        }

        if (image != null) {
          final Image.Plane[] planes = image.getPlanes();
          final ByteBuffer buffer = planes[0].getBuffer();
          final int pixelStride = planes[0].getPixelStride();
          final int rowStride = planes[0].getRowStride();
          final int rowPadding = rowStride - pixelStride * mWidth;

          // create bitmap
          bitmap = Bitmap.createBitmap(mWidth + rowPadding / pixelStride, mHeight, Bitmap.Config.ARGB_8888);
          bitmap.copyPixelsFromBuffer(buffer);

          // release the memory
          image.close();
          image = null;

          // crop out any extra padding
          if (rowPadding != 0) {
            final Bitmap cropped = Bitmap.createBitmap(bitmap, 0, 0, mWidth, mHeight);
            bitmap.recycle();
            bitmap = cropped;
          }

          // convert bitmap to jpeg based64 URI
          final ByteArrayOutputStream jpeg_data = new ByteArrayOutputStream();
          if (bitmap.compress(CompressFormat.JPEG, mCompression, jpeg_data)) {
            // release the memory
            bitmap.recycle();
            bitmap = null;

            final byte[] code = jpeg_data.toByteArray();

            final byte[] output = Base64.encode(code, Base64.NO_WRAP);
            final String js_out = "data:image/jpeg;base64," + new String(output);
            final JSONObject jsonRes = new JSONObject();
            jsonRes.put("URI", js_out);
            // send metadata to js
            jsonRes.put("Width", mWidth);
            jsonRes.put("Height", mHeight);
            jsonRes.put("Density", mDensity);
            jsonRes.put("Rotation", mRotation);
            PluginResult result = new PluginResult(PluginResult.Status.OK, jsonRes);
            result.setKeepCallback(true);
            mCallbackContext.sendPluginResult(result);
          } else {
            Log.e(TAG, "Unable to convert bitmap");
          }

          Log.e(TAG, "captured image");
        }

      } catch (Exception e) {
        e.printStackTrace();
      } finally {
        if (bitmap != null) {
          bitmap.recycle();
        }

        if (image != null) {
          image.close();
        }
      }
    }
  }

  private class OrientationChangeCallback extends OrientationEventListener {

    OrientationChangeCallback(Context context) {
      super(context);
    }

    @Override
    public void onOrientationChanged(int orientation) {
      final int rotation = mDisplay.getRotation();
      if (rotation != mRotation) {
        mRotation = rotation;
        mTimer.schedule(new TimerTask() {
          @Override
          public void run() {
            // re-create virtual display depending on device width / height
            try {
              // try multiple times, as cordova/webview is slow to change the screen resolution
              for (int l=0; l<=5; l++){
                if (createVirtualDisplay(false)) {
                  return;
                }
                Thread.sleep(200);
              }
            } catch (Exception e) {
              e.printStackTrace();
            }
          }
        }, 500);
      }
    }
  }

  private class MediaProjectionStopCallback extends MediaProjection.Callback {
    @Override
    public void onStop() {
      Log.e("ScreenCapture", "stopping projection.");
      mHandler.post(new Runnable() {
        @Override
        public void run() {
          if (mVirtualDisplay != null)
            mVirtualDisplay.release();
          if (mImageReader != null)
            mImageReader.setOnImageAvailableListener(null, null);
          if (mOrientationChangeCallback != null)
            mOrientationChangeCallback.disable();
          sMediaProjection.unregisterCallback(MediaProjectionStopCallback.this);
          mCallbackContext.success("Stopped");
        }
      });
    }
  }

  /******************************************
   * Activity Lifecycle methods
   ************************/

  @Override
  public void initialize(CordovaInterface cordova, CordovaWebView webView) {
    super.initialize(cordova, webView);

    // call for the projection manager
    mProjectionManager = (MediaProjectionManager) cordova.getActivity()
        .getSystemService(Context.MEDIA_PROJECTION_SERVICE);

    // start capture handling thread
    new Thread() {
      @Override
      public void run() {
        Looper.prepare();
        mHandler = new Handler();
        Looper.loop();
      }
    }.start();
  }

  // TODO: Add logic for rejected permissions
  @Override
  public void onActivityResult(int requestCode, int resultCode, Intent data) {
    if (requestCode == REQUEST_CODE) {
      sMediaProjection = mProjectionManager.getMediaProjection(resultCode, data);

      if (sMediaProjection != null) {
        mReady = true;
        mFps = mPendingFps;
        mCompression = mPendingCompression;

        int interval = 1000 / mFps;
        mTimer = new Timer();
        mTimer.scheduleAtFixedRate(new TimerTask() {
          @Override
          public void run() {
            mReady = true;
          }
        }, interval, interval);

        // display metrics
        DisplayMetrics metrics = cordova.getActivity().getResources().getDisplayMetrics();
        mDensity = metrics.densityDpi;
        mDisplay = cordova.getActivity().getWindowManager().getDefaultDisplay();
        mRotation = mDisplay.getRotation();

        // create virtual display depending on device width / height
        createVirtualDisplay(true);

        // register orientation change callback
        mOrientationChangeCallback = new OrientationChangeCallback(cordova.getActivity().getApplicationContext());
        if (mOrientationChangeCallback.canDetectOrientation()) {
          mOrientationChangeCallback.enable();
        }

        // register media projection stop callback
        sMediaProjection.registerCallback(new MediaProjectionStopCallback(), mHandler);
      }
    }
  }

  @Override
  public void onDestroy() {
    if (sMediaProjection != null) {
      if (mTimer != null) {
        mTimer.cancel();
        mTimer = null;
      }
      sMediaProjection.stop();
    }
  }

  /******************************************
   * UI Widget Callbacks
   *******************************/
  private void startProjection() {
    cordova.setActivityResultCallback(this);
    cordova.startActivityForResult(this, mProjectionManager.createScreenCaptureIntent(), REQUEST_CODE);
  }

  private void stopProjection() {
    if (mTimer != null) {
      mTimer.cancel();
      mTimer = null;
    }
    mHandler.post(new Runnable() {
      @Override
      public void run() {
        if (sMediaProjection != null) {
          sMediaProjection.stop();
        }
      }
    });
  }

  /******************************************
   * Factoring Virtual Display creation
   ****************/
  private boolean createVirtualDisplay(boolean force) {
    // Gets the real size of the display without subtracting any window decor or applying any compatibility scale factors.
    Point size = new Point();
    mDisplay.getRealSize(size);
    if (!force && mWidth == size.x && mHeight == size.y) {
      return false;
    }
    mWidth = size.x;
    mHeight = size.y;

    // clean up
    if (mVirtualDisplay != null)
      mVirtualDisplay.release();
    if (mImageReader != null)
      mImageReader.close();

    // start capture reader
    mImageReader = ImageReader.newInstance(mWidth, mHeight, PixelFormat.RGBA_8888, 2);
    mVirtualDisplay = sMediaProjection.createVirtualDisplay(SCREENCAP_NAME, mWidth, mHeight, mDensity,
        VIRTUAL_DISPLAY_FLAGS, mImageReader.getSurface(), null, mHandler);
    mImageReader.setOnImageAvailableListener(new ImageAvailableListener(), mHandler);
    return true;
  }
}
