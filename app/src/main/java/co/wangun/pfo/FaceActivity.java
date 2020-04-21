package co.wangun.pfo;

import android.Manifest;
import android.app.AlertDialog;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.ImageFormat;
import android.graphics.Paint;
import android.graphics.PointF;
import android.graphics.Rect;
import android.graphics.RectF;
import android.graphics.YuvImage;
import android.hardware.Camera;
import android.location.LocationListener;
import android.location.LocationManager;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.StrictMode;
import android.provider.Settings;
import android.util.DisplayMetrics;
import android.util.Log;
import android.util.TypedValue;
import android.view.SurfaceHolder;
import android.view.SurfaceView;
import android.view.View;
import android.view.ViewGroup;
import android.view.WindowManager;
import android.widget.ImageButton;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;
import androidx.core.content.ContextCompat;

import com.luxand.FSDK;

import org.json.JSONException;
import org.json.JSONObject;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.IOException;
import java.util.HashMap;
import java.util.List;

import okhttp3.MediaType;
import okhttp3.MultipartBody;
import okhttp3.RequestBody;
import okhttp3.ResponseBody;
import retrofit2.Call;
import retrofit2.Callback;
import retrofit2.Response;

/**
 * FACE DETECT EVERY FRAME WIL CONVERT TO GRAY BITMAP SO THIS HAS HIGHER PERFORMANCE THAN RGB BITMAP
 * COMPARE FPS (DETECT FRAME PER SECOND) OF 2 METHODs FOR MORE DETAIL
 */

public final class FaceActivity extends AppCompatActivity implements SurfaceHolder.Callback, Camera.PreviewCallback, Camera.ErrorCallback {

    //FaceActivity
    public static final String TAG = FaceActivity.class.getSimpleName();
    private static final int MAX_FACE = 10;

    // fps detect face (not FPS of camera)
    long start, end;
    int counter = 0;
    double fps;
    // Number of Cameras in device.
    private int numberOfCameras;
    private Camera mCamera;
    private int cameraId = 1;
    // Let's keep track of the display rotation and orientation also:
    private int mDisplayRotation;
    private int mDisplayOrientation;
    private int previewWidth;
    private int previewHeight;
    // The surface view for the camera data
    private SurfaceView mView;
    // Draw rectangles and other fancy stuff:
    private FaceOverlayView mFaceView;
    private boolean isThreadWorking = false;
    private Handler handler;
    private FaceDetectThread detectThread = null;
    private int prevSettingWidth;
    private int prevSettingHeight;
    private android.media.FaceDetector fdet;
    private byte[] grayBuff;
    private int bufflen;
    private int[] rgbs;
    private FaceModel[] faces;
    private FaceModel[] faces_previous;
    private int Id = 0;
    private String BUNDLE_CAMERA_ID = "camera";
    private HashMap<Integer, Integer> facesCount = new HashMap<>();

    // TODO: nothing to do, only a bookmark
    String auth;
    String license;
    String path;
    String username;
    String confidence;
    String lat;
    String lng;
    String from;
    String statusText;
    int status;
    int width;
    LocationManager locationManager;
    // update location
    LocationListener locationListener = new LocationListener() {
        @Override
        public void onLocationChanged(android.location.Location location) {
            lat = Double.toString(location.getLatitude());
            lng = Double.toString(location.getLongitude());
            showResult();
        }

        @Override
        public void onStatusChanged(String provider, int status, Bundle extras) {
        }

        @Override
        public void onProviderEnabled(String provider) {
        }

        @Override
        public void onProviderDisabled(String provider) {
        }
    };

    // onCreate
    @Override
    public void onCreate(Bundle icicle) {
        super.onCreate(icicle);
        // set view
        setContentView(R.layout.activity_face);
        StrictMode.ThreadPolicy policy = new StrictMode.ThreadPolicy.Builder().permitAll().build();
        StrictMode.setThreadPolicy(policy);
        mView = findViewById(R.id.surfaceview);
        getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);

        // init values
        path = getApplicationInfo().dataDir + "/.rec";
        username = "pnmuser0"; // TODO: get value from previous activity
        lat = "0.0";
        lng = "0.0";
        auth = "WangunBearerToken 1772170802";
        license = "a0N2w0nUkUFcX3PJbjnYS3WJfcubryh8yva/BMnihBEXYa9oYEEo7xFgZ+zWif+cRvrG7c0BdN8BCAllZy+i5hUkjwmrrN4ryB1ZP+ijlNzwD2KItgTzdmepdda8kMoHTPa+8buMJM2X+UkuEhxxCLGgQ+npeqr1eX477RsOYAk=";
        from = getIntent().getStringExtra("from");
        width = 64;

        // init title app bar
        if (from == null) {
            from = "Presensi";
        }
        TextView titleView = findViewById(R.id.titleView);
        titleView.setText(from);

        // Create Face Overlay View:
        mFaceView = new FaceOverlayView(this);
        addContentView(mFaceView, new ViewGroup.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT
        ));
        handler = new Handler();
        faces = new FaceModel[MAX_FACE];
        faces_previous = new FaceModel[MAX_FACE];
        for (int i = 0; i < MAX_FACE; i++) {
            faces[i] = new FaceModel();
            faces_previous[i] = new FaceModel();
        }
        if (icicle != null)
            cameraId = icicle.getInt(BUNDLE_CAMERA_ID, 0);

        // init all functions
        initFun();

        // disable if cam btn recently clicked
        ImageButton camBtn = findViewById(R.id.camBtn);
        camBtn.setAlpha(0.3f);
        camBtn.setClickable(false);
        handler.postDelayed(new Runnable() {
            @Override
            public void run() {
                ImageButton camBtn = findViewById(R.id.camBtn);
                camBtn.setAlpha(1f);
                camBtn.setClickable(true);
            }
        }, 4000);
    }

    private void showNoFaceDialog() {
        // show dialog
        AlertDialog.Builder dialog = new AlertDialog.Builder(FaceActivity.this);
        dialog.setMessage("Wajah kamu tidak terdaftar di server.")
                .setPositiveButton("Daftar", new DialogInterface.OnClickListener() {
                    public void onClick(DialogInterface dialog, int which) {
                        Intent intent = new Intent(FaceActivity.this, FaceActivity.class);
                        intent.putExtra("from", "Daftar");
                        startActivity(intent);
                        finish();
                    }
                })
                .setNegativeButton("Keluar", new DialogInterface.OnClickListener() {
                    public void onClick(DialogInterface dialog, int which) {
                        finish();
                    }
                })
                .setNeutralButton("Cek Kembali", new DialogInterface.OnClickListener() {
                    public void onClick(DialogInterface dialog, int which) {
                        downloadFace();
                    }
                })
                .create()
                .show();
    }

    // check if user face already in local storage
    private boolean checkLocalFace() {
        String fileName = "Daftar.jpg";
        File file = new File(path, fileName);
        return file.exists();
    }

    // init buttons function
    private void initBtn() {

        ImageButton backBtn = findViewById(R.id.backBtn);
        ImageButton modBtn = findViewById(R.id.modBtn);
        ImageButton delBtn = findViewById(R.id.delBtn);

        // show button only if user face already in local storage
        if (checkLocalFace()) {
            modBtn.setVisibility(View.VISIBLE);
            delBtn.setVisibility(View.VISIBLE);
        } else {
            modBtn.setVisibility(View.GONE);
            delBtn.setVisibility(View.GONE);
        }
        // //

        backBtn.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(final View v) {
                onBackPressed();
            }
        });

        if (from.equals("Daftar")) {
            delBtn.setVisibility(View.GONE);
        } else {
            delBtn.setVisibility(View.VISIBLE);
        }

        delBtn.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(final View v) {
                File file = new File(path, "Daftar.jpg");
                if (file.exists()) {
                    file.delete();
                }
                recreate();
            }
        });

        if (from.equals("Daftar")) {
            modBtn.setVisibility(View.GONE);
        } else {
            modBtn.setVisibility(View.VISIBLE);
        }

        modBtn.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(final View v) {
                finish();
                Intent intent = new Intent(FaceActivity.this, FaceActivity.class);
                intent.putExtra("from", "Daftar");
                startActivity(intent);
            }
        });

        ImageButton camBtn = findViewById(R.id.camBtn);
        camBtn.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(final View v) {
                if (numberOfCameras == 1) {
                    AlertDialog.Builder builder = new AlertDialog.Builder(FaceActivity.this);
                    builder.setTitle("Pilih Kamera")
                            .setMessage("Device hanya punya satu kamera")
                            .setNeutralButton("OK", null)
                            .create()
                            .show();
                } else {
                    if (cameraId == 0) {
                        cameraId = 1;
                    } else {
                        cameraId = 0;
                    }
                    recreate();
                }
            }
        });
    }

    @Override
    protected void onPostCreate(Bundle savedInstanceState) {
        super.onPostCreate(savedInstanceState);
        // access camera
        SurfaceHolder holder = mView.getHolder();
        holder.addCallback(this);
    }

    // check permissions
    private void checkPermit() {
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.CAMERA) != PackageManager.PERMISSION_GRANTED ||
                ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED ||
                ContextCompat.checkSelfPermission(this, Manifest.permission.INTERNET) != PackageManager.PERMISSION_GRANTED ||
                ContextCompat.checkSelfPermission(this, Manifest.permission.WRITE_EXTERNAL_STORAGE) != PackageManager.PERMISSION_GRANTED
        ) {
            //ask for permission if user did not given
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                requestPermissions(new String[]{
                        Manifest.permission.CAMERA,
                        Manifest.permission.ACCESS_FINE_LOCATION,
                        Manifest.permission.INTERNET,
                        Manifest.permission.WRITE_EXTERNAL_STORAGE
                }, 0);
                recreate();
            }
        }
    }

    // TODO: initFun() bookmark
    private void initFun() {
        // Check for the permissions
        checkPermit();

        // init all buttons function
        initBtn();

        // create .nomedia
        File dir = new File(path);
        dir.mkdirs();
        String fileName = ".nomedia";
        File file = new File(dir, fileName);
        try {
            file.createNewFile();
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    // request location after successfully scan face
    private void reqLoc() {
        locationManager = (LocationManager) getSystemService(Context.LOCATION_SERVICE);
        boolean gpsEnabled = false;
        boolean networkEnabled = false;

        // check if gps enabled
        try {
            gpsEnabled = locationManager.isProviderEnabled(LocationManager.GPS_PROVIDER);
        } catch (SecurityException ex) {
        }

        // check if network enabled
        try {
            networkEnabled = locationManager.isProviderEnabled(LocationManager.NETWORK_PROVIDER);
        } catch (SecurityException ex) {
        }

        if (!gpsEnabled && !networkEnabled) {
            AlertDialog.Builder builder = new AlertDialog.Builder(FaceActivity.this);
            builder.setMessage("GPS atau internet belum aktif. Aktifkan?")
                    .setPositiveButton("Pengaturan", new DialogInterface.OnClickListener() {
                        public void onClick(DialogInterface dialog, int which) {
                            startActivity(new Intent(Settings.ACTION_LOCATION_SOURCE_SETTINGS));
                            finish();
                        }
                    })
                    .setNegativeButton("Batal", new DialogInterface.OnClickListener() {
                        public void onClick(DialogInterface dialog, int which) {
                            finish();
                        }
                    })
                    .create()
                    .show();

        } else {
            try {
                locationManager.requestLocationUpdates(
                        LocationManager.NETWORK_PROVIDER,
                        0L, 0f, locationListener
                );
            } catch (SecurityException ex) {
            }
        }
    }

    /**
     * Restarts the camera.
     */
    @Override
    protected void onResume() {
        super.onResume();

        Log.i(TAG, "onResume");
        startPreview();
    }

    /**
     * Stops the camera.
     */
    @Override
    protected void onPause() {
        super.onPause();
        Log.i(TAG, "onPause");
        if (mCamera != null) {
            mCamera.stopPreview();
        }
    }

    /**
     * Releases the resources associated with the camera source, the associated detector, and the
     * rest of the processing pipeline.
     */
    @Override
    protected void onDestroy() {
        super.onDestroy();
    }

    @Override
    protected void onSaveInstanceState(Bundle outState) {
        super.onSaveInstanceState(outState);
        outState.putInt(BUNDLE_CAMERA_ID, cameraId);
    }

    @Override
    public void surfaceCreated(SurfaceHolder surfaceHolder) {

        numberOfCameras = Camera.getNumberOfCameras();
        Camera.CameraInfo cameraInfo = new Camera.CameraInfo();
        for (int i = 0; i < Camera.getNumberOfCameras(); i++) {
            Camera.getCameraInfo(i, cameraInfo);
            if (cameraInfo.facing == Camera.CameraInfo.CAMERA_FACING_BACK) {
                if (cameraId == 0) cameraId = i;
            }
        }

        mCamera = Camera.open(cameraId);

        Camera.getCameraInfo(cameraId, cameraInfo);
        if (cameraInfo.facing == Camera.CameraInfo.CAMERA_FACING_FRONT) {
            mFaceView.setFront(true);
        }

        try {
            mCamera.setPreviewDisplay(mView.getHolder());
        } catch (Exception e) {
            Log.e(TAG, "Could not preview the image.", e);
        }
    }

    @Override
    public void surfaceChanged(SurfaceHolder surfaceHolder, int format, int width, int height) {
        // We have no surface, return immediately:
        if (surfaceHolder.getSurface() == null) {
            return;
        }
        // Try to stop the current preview:
        try {
            mCamera.stopPreview();
        } catch (Exception e) {
            // Ignore...
        }

        configureCamera(width, height);
        setDisplayOrientation();
        setErrorCallback();

        // Create media.FaceDetector
        float aspect = (float) previewHeight / (float) previewWidth;
        fdet = new android.media.FaceDetector(prevSettingWidth, (int) (prevSettingWidth * aspect), MAX_FACE);

        bufflen = previewWidth * previewHeight;
        grayBuff = new byte[bufflen];
        rgbs = new int[bufflen];

        // Everything is configured! Finally start the camera preview again:
        startPreview();
    }

    private void setErrorCallback() {
        mCamera.setErrorCallback(this);
    }

    private void setDisplayOrientation() {
        // Now set the display orientation:
        mDisplayRotation = FaceUtils.getDisplayRotation(FaceActivity.this);
        mDisplayOrientation = FaceUtils.getDisplayOrientation(mDisplayRotation, cameraId);

        mCamera.setDisplayOrientation(mDisplayOrientation);

        if (mFaceView != null) {
            mFaceView.setDisplayOrientation(mDisplayOrientation);
        }
    }

    private void configureCamera(int width, int height) {
        Camera.Parameters parameters = mCamera.getParameters();
        // Set the PreviewSize and AutoFocus:
        setOptimalPreviewSize(parameters, width, height);
        setAutoFocus(parameters);
        // And set the parameters:
        mCamera.setParameters(parameters);
    }

    private void setOptimalPreviewSize(Camera.Parameters cameraParameters, int width, int height) {
        List<Camera.Size> previewSizes = cameraParameters.getSupportedPreviewSizes();
        float targetRatio = (float) width / height;
        Camera.Size previewSize = FaceUtils.getOptimalPreviewSize(this, previewSizes, targetRatio);
        previewWidth = previewSize.width;
        previewHeight = previewSize.height;

        /**
         * Calculate size to scale full frame bitmap to smaller bitmap
         * Detect face in scaled bitmap have high performance than full bitmap.
         * The smaller image size -> detect faster, but distance to detect face shorter,
         * so calculate the size follow your purpose
         */
        if (previewWidth / 4 > 360) {
            prevSettingWidth = 360;
            prevSettingHeight = 270;
        } else if (previewWidth / 4 > 320) {
            prevSettingWidth = 320;
            prevSettingHeight = 240;
        } else if (previewWidth / 4 > 240) {
            prevSettingWidth = 240;
            prevSettingHeight = 160;
        } else {
            prevSettingWidth = 160;
            prevSettingHeight = 120;
        }

        cameraParameters.setPreviewSize(previewSize.width, previewSize.height);

        mFaceView.setPreviewWidth(previewWidth);
        mFaceView.setPreviewHeight(previewHeight);
    }

    private void setAutoFocus(Camera.Parameters cameraParameters) {
        List<String> focusModes = cameraParameters.getSupportedFocusModes();
        if (focusModes.contains(Camera.Parameters.FOCUS_MODE_CONTINUOUS_PICTURE))
            cameraParameters.setFocusMode(Camera.Parameters.FOCUS_MODE_CONTINUOUS_PICTURE);
    }

    private void startPreview() {
        if (mCamera != null) {
            isThreadWorking = false;
            mCamera.startPreview();
            mCamera.setPreviewCallback(this);
            counter = 0;
        }
    }

    @Override
    public void surfaceDestroyed(SurfaceHolder surfaceHolder) {
        mCamera.setPreviewCallbackWithBuffer(null);
        mCamera.setErrorCallback(null);
        mCamera.release();
        mCamera = null;
    }

    @Override
    public void onPreviewFrame(byte[] _data, Camera _camera) {
        if (!isThreadWorking) {
            if (counter == 0)
                start = System.currentTimeMillis();

            isThreadWorking = true;
            waitForFdetThreadComplete();
            detectThread = new FaceDetectThread(handler, this);
            detectThread.setData(_data);
            detectThread.start();
        }
    }

    private void waitForFdetThreadComplete() {
        if (detectThread == null) {
            return;
        }

        if (detectThread.isAlive()) {
            try {
                detectThread.join();
                detectThread = null;
            } catch (InterruptedException e) {
                e.printStackTrace();
            }
        }
    }

    @Override
    public void onError(int error, Camera camera) {
        Toast.makeText(this,
                "Maaf, device Anda tidak memenuhi syarat",
                Toast.LENGTH_LONG).show();
        finish();
    }

    /**
     * Download face
     */
    private void downloadFace() {

        // init API Service
        FaceApiService apiService = FaceApiClient.getClient().create(FaceApiService.class);

        // Post request
        apiService.getUser(auth, username).enqueue(new Callback<ResponseBody>() {
            @Override
            public void onResponse(Call<ResponseBody> call, Response<ResponseBody> response) {
                if (response.isSuccessful()) {
                    // get results
                    try {
                        JSONObject result = new JSONObject(response.body().string());
                        String message = result.getString("message");
                        if (message.equals("Data fetched successfully")) {
                            JSONObject records = result.getJSONObject("records");
                            String jsonIMG = records.getString("img_url");
                            FaceUtils.downloadImage(jsonIMG, path);
                            // show dialog
                            AlertDialog.Builder builder = new AlertDialog.Builder(FaceActivity.this);
                            builder.setMessage("Aplikasi baru saja mengunduh muka kamu dari server. Sekarang kamu bisa presensi melalui fitur muka.")
                                    .setPositiveButton("Absen", new DialogInterface.OnClickListener() {
                                        public void onClick(DialogInterface dialog, int which) {
                                            recreate();
                                        }
                                    })
                                    .create()
                                    .show();
                        } else {
                            // show dialog
                            showNoFaceDialog();
                        }
                    } catch (JSONException | IOException e) {
                        e.printStackTrace();
                    }
                }
            }

            @Override
            public void onFailure(Call<ResponseBody> call, Throwable t) {
            }
        });
    }

    /**
     * Register face
     */
    // TODO: bookmark register face
    private void registerFace() {

        // init API Service
        FaceApiService apiService = FaceApiClient.getClient().create(FaceApiService.class);

        // init values
        File file = new File(path, "Daftar.jpg");
        RequestBody requestFile = RequestBody
                .create(MediaType.parse("multipart/form-data"), file);
        MultipartBody.Part img = MultipartBody.Part
                .createFormData("img", file.getName(), requestFile);

        // Post request
        apiService.postUser(auth, username, img).enqueue(new Callback<ResponseBody>() {
            @Override
            public void onResponse(Call<ResponseBody> call, Response<ResponseBody> response) {
                if (response.isSuccessful()) {
                    // get results
                    try {
                        JSONObject json = new JSONObject(response.body().string());

                        // show dialog
                        AlertDialog.Builder builder = new AlertDialog.Builder(FaceActivity.this);
                        builder.setMessage("Wajah kamu berhasil terdaftar. Sekarang kamu bisa presensi melalui fitur wajah.")
                                .setPositiveButton("Absen", new DialogInterface.OnClickListener() {
                                    public void onClick(DialogInterface dialog, int which) {
                                        Intent intent = new Intent(FaceActivity.this, FaceActivity.class);
                                        startActivity(intent);
                                        finish();
                                    }
                                })
                                .create()
                                .show();

                    } catch (JSONException | IOException e) {
                        e.printStackTrace();
                    }
                }
            }

            @Override
            public void onFailure(Call<ResponseBody> call, Throwable t) {
            }
        });
    }

    /**
     * Matching faces
     */

    //TODO: Matching Faces bookmark
    private void matchingFaces() {

        // init fsdk
        FSDK.ActivateLibrary(license);
        FSDK.Initialize();

        // init variables
        String path = getApplicationInfo().dataDir + "/.rec";

        // get face 1
        FSDK.HImage face1 = new FSDK.HImage();
        FSDK.LoadImageFromFile(face1, path + "/Daftar.jpg");
        FSDK.FSDK_FaceTemplate faceTemp1 = new FSDK.FSDK_FaceTemplate();
        FSDK.TFacePosition facePosi1 = new FSDK.TFacePosition();
        facePosi1.w = width;
        facePosi1.xc = width / 2;
        facePosi1.yc = width / 2;
        FSDK.GetFaceTemplateInRegion(face1, facePosi1, faceTemp1);

        // get face 2
        FSDK.HImage face2 = new FSDK.HImage();
        FSDK.LoadImageFromFile(face2, path + "/Presensi.jpg");
        FSDK.FSDK_FaceTemplate faceTemp2 = new FSDK.FSDK_FaceTemplate();
        FSDK.TFacePosition facePosi2 = new FSDK.TFacePosition();
        facePosi2.w = width;
        facePosi2.xc = width / 2;
        facePosi2.yc = width / 2;
        FSDK.GetFaceTemplateInRegion(face2, facePosi2, faceTemp2);

        // matching
        float[] similarity = new float[1];
        float[] threshold = new float[1];
        float farValue = 0.05f; // False Acceptance Rate 5%; the less the more secure

        FSDK.GetMatchingThresholdAtFAR(farValue, threshold);
        FSDK.MatchFaces(faceTemp1, faceTemp2, similarity);

        if (similarity[0] > threshold[0]) {
            status = 1;
        } else {
            status = 0;
        }
        confidence = Float.toString(similarity[0]);

        // request loc
        reqLoc();

        // debug
        Log.d("FA", "threshold: " + threshold[0] + " " + "similarity: " + similarity[0]);
    }

    /**
     * Whatever you want to do with the result
     */
    //TODO: RESULT BOOKMARK
    private void showResult() {

        // TODO: Do something with this values
        // Username String
        // Status int
        // Lat String
        // Lng String
        // confidence String (debug)

        if (status == 1) {
            statusText = "SUCCESS";
        } else {
            statusText = "FAILED";
        }
        String msg = "username: " + username + "\nStatus: " + statusText + "\nLat, Lng: " + lat +
                ", " + lng + "\nConfidence (debug): " + confidence;
        Toast.makeText(this, msg, Toast.LENGTH_LONG).show();

        postResult();

        // debug
        Log.d("FaceActivity", path);
    }

    @Override
    public void onBackPressed() {
        if (from.equals("Daftar")) {
            Intent intent = new Intent(FaceActivity.this, FaceActivity.class);
            startActivity(intent);
            finish();
        } else {
            finish();
            android.os.Process.killProcess(android.os.Process.myPid());
        }
    }

    /**
     * Register face
     */
    // TODO: Post Result Bookmark
    private void postResult() {

        // init API Service
        FaceApiService apiService = FaceApiClient.getClient().create(FaceApiService.class);

        // init values
        File file = new File(path, "Presensi.jpg");
        RequestBody requestFile = RequestBody
                .create(MediaType.parse("multipart/form-data"), file);
        MultipartBody.Part img = MultipartBody.Part
                .createFormData("img", file.getName(), requestFile);

        // Post request
        apiService.postResult(auth, username, lat, lng, confidence, status, img)
                .enqueue(new Callback<ResponseBody>() {
                    @Override
                    public void onResponse(Call<ResponseBody> call, Response<ResponseBody> response) {
                        if (response.isSuccessful()) {
                            // get results
                            try {
                                JSONObject json = new JSONObject(response.body().string());

                                // TODO: Start Next Activity and finish FaceActivity
                                // startActivity(intent);
                                // finish();

                            } catch (JSONException | IOException e) {
                                e.printStackTrace();
                            }
                        }
                    }

                    @Override
                    public void onFailure(Call<ResponseBody> call, Throwable t) {
                    }
                });
    }


    /**
     * Do face detect in thread
     */
    private class FaceDetectThread extends Thread {
        private Handler handler;
        private byte[] data = null;
        private Bitmap faceCroped;

        public FaceDetectThread(Handler handler, Context ctx) {
            this.handler = handler;
        }


        public void setData(byte[] data) {
            this.data = data;
        }

        public void run() {

            float aspect = (float) previewHeight / (float) previewWidth;
            int w = prevSettingWidth;
            int h = (int) (prevSettingWidth * aspect);

            Bitmap bitmap = Bitmap.createBitmap(rgbs, previewWidth, previewHeight, Bitmap.Config.RGB_565);

            // start RGB Photos
            YuvImage yuv = new YuvImage(data, ImageFormat.NV21,
                    bitmap.getWidth(), bitmap.getHeight(), null);
            Rect rectImage = new Rect(0, 0, bitmap.getWidth(), bitmap.getHeight());
            ByteArrayOutputStream baout = new ByteArrayOutputStream();
            if (!yuv.compressToJpeg(rectImage, 100, baout)) {
                Log.e("CreateBitmap", "compressToJpeg failed");
            }
            BitmapFactory.Options bfo = new BitmapFactory.Options();
            bfo.inPreferredConfig = Bitmap.Config.RGB_565;
            bitmap = BitmapFactory.decodeStream(
                    new ByteArrayInputStream(baout.toByteArray()), null, bfo);

            if (w % 2 == 1) {
                w -= 1;
            }
            if (h % 2 == 1) {
                h -= 1;
            }

            Bitmap bmp = Bitmap.createScaledBitmap(bitmap, w, h, false);

            float xScale = (float) previewWidth / (float) prevSettingWidth;
            float yScale = (float) previewHeight / (float) h;

            Camera.CameraInfo info = new Camera.CameraInfo();
            Camera.getCameraInfo(cameraId, info);
            int rotate = mDisplayOrientation;
            if (info.facing == Camera.CameraInfo.CAMERA_FACING_FRONT && mDisplayRotation % 180 == 0) {
                if (rotate + 180 > 360) {
                    rotate = rotate - 180;
                } else
                    rotate = rotate + 180;
            }

            switch (rotate) {
                case 90:
                    bmp = FaceUtils.rotate(bmp, 90);
                    xScale = (float) previewHeight / bmp.getWidth();
                    yScale = (float) previewWidth / bmp.getHeight();
                    break;
                case 180:
                    bmp = FaceUtils.rotate(bmp, 180);
                    break;
                case 270:
                    bmp = FaceUtils.rotate(bmp, 270);
                    xScale = (float) previewHeight / (float) h;
                    yScale = (float) previewWidth / (float) prevSettingWidth;
                    break;
            }

            fdet = new android.media.FaceDetector(bmp.getWidth(), bmp.getHeight(), MAX_FACE);

            android.media.FaceDetector.Face[] fullResults = new android.media.FaceDetector.Face[MAX_FACE];
            fdet.findFaces(bmp, fullResults);

            for (int i = 0; i < MAX_FACE; i++) {
                if (fullResults[i] == null) {
                    faces[i].clear();
                } else {
                    PointF mid = new PointF();
                    fullResults[i].getMidPoint(mid);

                    mid.x *= xScale;
                    mid.y *= yScale;

                    float eyesDis = fullResults[i].eyesDistance() * xScale;
                    float confidence = fullResults[i].confidence();
                    float pose = fullResults[i].pose(android.media.FaceDetector.Face.EULER_Y);
                    int idFace = Id;

                    Rect rect = new Rect(
                            (int) (mid.x - eyesDis * 1.20f),
                            (int) (mid.y - eyesDis * 0.55f),
                            (int) (mid.x + eyesDis * 1.20f),
                            (int) (mid.y + eyesDis * 1.85f));

                    // Only detect face size > 20x20
                    if (rect.height() * rect.width() > 20 * 20) {
                        // Check this face and previous face have same ID
                        for (int j = 0; j < MAX_FACE; j++) {
                            float eyesDisPre = faces_previous[j].eyesDistance();
                            PointF midPre = new PointF();
                            faces_previous[j].getMidPoint(midPre);

                            RectF rectCheck = new RectF(
                                    (midPre.x - eyesDisPre * 1.5f),
                                    (midPre.y - eyesDisPre * 1.15f),
                                    (midPre.x + eyesDisPre * 1.5f),
                                    (midPre.y + eyesDisPre * 1.85f));

                            if (rectCheck.contains(mid.x, mid.y) && (System.currentTimeMillis() - faces_previous[j].time) < 1000) {
                                idFace = faces_previous[j].id;
                                break;
                            }
                        }

                        if (idFace == Id) Id++;

                        faces[i].setFace(idFace, mid, eyesDis, confidence, pose, System.currentTimeMillis());

                        faces_previous[i].set(faces[i].id, faces[i].midEye, faces[i].eyesDistance(), faces[i].confidence, faces[i].pose, faces[i].time);

                        // if focus in a face 5 frame -> take picture face display
                        // because of some first frame have low quality
                        if (facesCount.get(idFace) == null) {
                            facesCount.put(idFace, 0);
                        } else {
                            int count = facesCount.get(idFace) + 1;
                            if (count <= 5)
                                facesCount.put(idFace, count);

                            // Crop Face to display in RecylerView
                            if (count == 5) {

                                faceCroped = FaceUtils.cropFace(faces[i], bitmap, rotate);

                                if (faceCroped != null) {

                                    handler.post(new Runnable() {

                                        public void run() {
                                            Bitmap resizedBitmap = Bitmap.createScaledBitmap(
                                                    faceCroped, width, width, false);
                                            FaceUtils.saveImage(resizedBitmap, from, path);

                                            // TODO: bookmark mode
                                            if (from.equals("Presensi")) {
                                                if (checkLocalFace()) {
                                                    matchingFaces();
                                                } else {
                                                    downloadFace();
                                                }
                                            } else {
                                                registerFace();
                                            }
                                        }
                                    });
                                }
                            }
                        }
                    }
                }
            }

            handler.post(new Runnable() {
                public void run() {
                    //send face to FaceView to draw rect
                    mFaceView.setFaces(faces);

                    //Calculate FPS (Detect Frame per Second)
                    end = System.currentTimeMillis();
                    counter++;
                    double time = (double) (end - start) / 1000;
                    if (time != 0)
                        fps = counter / time;

                    mFaceView.setFPS(fps);

                    if (counter == (Integer.MAX_VALUE - 1000))
                        counter = 0;

                    isThreadWorking = false;
                }
            });
        }

        private void gray8toRGB32(byte[] gray8, int width, int height, int[] rgb_32s) {
            final int endPtr = width * height;
            int ptr = 0;
            while (ptr != endPtr) {
                final int Y = gray8[ptr] & 0xff;
                rgb_32s[ptr] = 0xff000000 + (Y << 16) + (Y << 8) + Y;
                ptr++;
            }
        }
    }
}

/**
 * This class is a simple View to display the faces.
 */
class FaceOverlayView extends View {

    private Paint mPaint;
    private Paint mTextPaint;
    private int mDisplayOrientation;
    private int mOrientation;
    private int previewWidth;
    private int previewHeight;
    private FaceModel[] mFaces;
    private double fps;
    private boolean isFront = false;

    public FaceOverlayView(Context context) {
        super(context);
        initialize();
    }

    private void initialize() {
        // We want a white box around the face:
        DisplayMetrics metrics = getResources().getDisplayMetrics();

        int stroke = (int) TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, 2, metrics);
        mPaint = new Paint();
        mPaint.setAntiAlias(true);
        mPaint.setDither(true);
        mPaint.setColor(Color.WHITE);
        mPaint.setStrokeWidth(stroke);
        mPaint.setStyle(Paint.Style.STROKE);

        mTextPaint = new Paint();
        mTextPaint.setAntiAlias(true);
        mTextPaint.setDither(true);
        int size = (int) TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, 15, metrics);
        mTextPaint.setTextSize(size);
        mTextPaint.setColor(Color.WHITE);
        mTextPaint.setStyle(Paint.Style.FILL);
    }

    public void setFPS(double fps) {
        this.fps = fps;
    }

    public void setFaces(FaceModel[] faces) {
        mFaces = faces;
        invalidate();
    }

    public void setOrientation(int orientation) {
        mOrientation = orientation;
    }

    public void setDisplayOrientation(int displayOrientation) {
        mDisplayOrientation = displayOrientation;
        invalidate();
    }

    @Override
    protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);
        if (mFaces != null && mFaces.length > 0) {

            float scaleX = (float) getWidth() / (float) previewWidth;
            float scaleY = (float) getHeight() / (float) previewHeight;

            switch (mDisplayOrientation) {
                case 90:
                case 270:
                    scaleX = (float) getWidth() / (float) previewHeight;
                    scaleY = (float) getHeight() / (float) previewWidth;
                    break;
            }

            canvas.save();
            canvas.rotate(-mOrientation);
            RectF rectF = new RectF();
            for (FaceModel face : mFaces) {
                PointF mid = new PointF();
                face.getMidPoint(mid);

                if (mid.x != 0.0f && mid.y != 0.0f) {
                    float eyesDis = face.eyesDistance();

                    rectF.set(new RectF(
                            (mid.x - eyesDis * 1.2f) * scaleX,
                            (mid.y - eyesDis * 0.65f) * scaleY,
                            (mid.x + eyesDis * 1.2f) * scaleX,
                            (mid.y + eyesDis * 1.75f) * scaleY));
                    if (isFront) {
                        float left = rectF.left;
                        float right = rectF.right;
                        rectF.left = getWidth() - right;
                        rectF.right = getWidth() - left;
                    }
                    canvas.drawRect(rectF, mPaint);
                }
            }
            canvas.restore();
        }
    }


    public void setPreviewWidth(int previewWidth) {
        this.previewWidth = previewWidth;
    }

    public void setPreviewHeight(int previewHeight) {
        this.previewHeight = previewHeight;
    }

    public void setFront(boolean front) {
        isFront = front;
    }
}

