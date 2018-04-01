package com.example.facedoor;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.SharedPreferences;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Color;
import android.graphics.ImageFormat;
import android.graphics.Rect;
import android.graphics.YuvImage;
import android.hardware.Camera;
import android.os.Bundle;
import android.os.Environment;
import android.os.Handler;
import android.util.Log;
import android.view.MotionEvent;
import android.view.View;
import android.widget.ImageView;
import android.widget.RelativeLayout;
import android.widget.TextView;

import com.arcsoft.ageestimation.ASAE_FSDKAge;
import com.arcsoft.ageestimation.ASAE_FSDKEngine;
import com.arcsoft.ageestimation.ASAE_FSDKError;
import com.arcsoft.ageestimation.ASAE_FSDKFace;
import com.arcsoft.ageestimation.ASAE_FSDKVersion;
import com.arcsoft.facerecognition.AFR_FSDKEngine;
import com.arcsoft.facerecognition.AFR_FSDKError;
import com.arcsoft.facerecognition.AFR_FSDKFace;
import com.arcsoft.facerecognition.AFR_FSDKMatching;
import com.arcsoft.facerecognition.AFR_FSDKVersion;
import com.arcsoft.facetracking.AFT_FSDKEngine;
import com.arcsoft.facetracking.AFT_FSDKError;
import com.arcsoft.facetracking.AFT_FSDKFace;
import com.arcsoft.facetracking.AFT_FSDKVersion;
import com.arcsoft.genderestimation.ASGE_FSDKEngine;
import com.arcsoft.genderestimation.ASGE_FSDKError;
import com.arcsoft.genderestimation.ASGE_FSDKFace;
import com.arcsoft.genderestimation.ASGE_FSDKGender;
import com.arcsoft.genderestimation.ASGE_FSDKVersion;
import com.example.aicsoft.FaceManagerUtils;
import com.example.aicsoft.model.FaceRegist;
import com.example.facedoor.model.User;
import com.example.facedoor.util.StringUtils;
import com.example.facedoor.util.ToastShow;
import com.guo.android_extend.java.AbsLoop;
import com.guo.android_extend.tools.CameraHelper;
import com.guo.android_extend.widget.CameraFrameData;
import com.guo.android_extend.widget.CameraGLSurfaceView;
import com.guo.android_extend.widget.CameraSurfaceView;
import com.guo.android_extend.widget.CameraSurfaceView.OnCameraListener;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

/**
 * Created by gqj3375 on 2017/4/28.
 */

public class DetecterActivity extends Activity implements OnCameraListener, View.OnTouchListener, Camera.AutoFocusCallback {
    private final String TAG = this.getClass().getSimpleName();
    private final static int FACE_WIDTH = 320;
    private final static int FACE_HEIGHT = 320;
    private String mGroupId;
    List<FaceRegist> mResgist = new ArrayList<>();

    private int mWidth, mHeight, mFormat;
    private CameraSurfaceView mSurfaceView;
    private CameraGLSurfaceView mGLSurfaceView;
    private Camera mCamera;

    AFT_FSDKVersion version = new AFT_FSDKVersion();
    AFT_FSDKEngine engine = new AFT_FSDKEngine();
    ASAE_FSDKVersion mAgeVersion = new ASAE_FSDKVersion();
    ASAE_FSDKEngine mAgeEngine = new ASAE_FSDKEngine();
    ASGE_FSDKVersion mGenderVersion = new ASGE_FSDKVersion();
    ASGE_FSDKEngine mGenderEngine = new ASGE_FSDKEngine();
    List<AFT_FSDKFace> result = new ArrayList<>();
    List<ASAE_FSDKAge> ages = new ArrayList<>();
    List<ASGE_FSDKGender> genders = new ArrayList<>();

    int mCameraID;
    int mCameraRotate;
    boolean mCameraMirror;
    byte[] mImageNV21 = null;
    FRAbsLoop mFRAbsLoop = null;
    AFT_FSDKFace mAFT_FSDKFace = null;
    Handler mHandler;
    private RelativeLayout backRv;
    private TextView mTextView;
    private TextView mTextView1;
    private ImageView mImageView;
    private long lastTime;  //这个时间标记是最后人脸检测的时间
    private int detectTimes;//标记检测出多少次人脸   用来控制检测速度
    private User mUser;  //标记已识别用户的用户号
    // 预览帧数据存储数组和缓存数组
    private byte[] mNV21;
    private byte[] mBuffer;
    // Camera nv21格式预览帧的尺寸，默认设置640*480
    private int PREVIEW_WIDTH = 640;
    private int PREVIEW_HEIGHT = 480;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        lastTime = System.currentTimeMillis();
        mCameraID = getIntent().getIntExtra("Camera", 0) == 0 ? Camera.CameraInfo.CAMERA_FACING_BACK : Camera.CameraInfo.CAMERA_FACING_FRONT;
        mCameraRotate = getIntent().getIntExtra("Camera", 0) == 0 ? 0 : 270;
        mCameraMirror = getIntent().getIntExtra("Camera", 0) == 0 ? false : true;
        mWidth = 640;
        mHeight = 480;
        mFormat = ImageFormat.NV21;
        mHandler = new Handler();

        setContentView(R.layout.activity_camera);
        mGLSurfaceView = (CameraGLSurfaceView) findViewById(R.id.glsurfaceView);
        mGLSurfaceView.setOnTouchListener(this);
        mSurfaceView = (CameraSurfaceView) findViewById(R.id.surfaceView);
        mSurfaceView.setOnCameraListener(this);
        mSurfaceView.setupGLSurafceView(mGLSurfaceView, true, mCameraMirror, mCameraRotate);
        mSurfaceView.debug_print_fps(true, false);

        //snap
        backRv = (RelativeLayout) findViewById(R.id.actionbar_back);
        backRv.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                finish();
            }
        });
        mTextView = (TextView) findViewById(R.id.textView);
        mTextView.setText("");
        mTextView1 = (TextView) findViewById(R.id.textView1);
        mTextView1.setText("");

        mImageView = (ImageView) findViewById(R.id.imageView);

        AFT_FSDKError err = engine.AFT_FSDK_InitialFaceEngine(FaceManagerUtils.appid, FaceManagerUtils.ft_key, AFT_FSDKEngine.AFT_OPF_0_HIGHER_EXT, 16, 5);
        Log.d(TAG, "AFT_FSDK_InitialFaceEngine =" + err.getCode());
        err = engine.AFT_FSDK_GetVersion(version);
        Log.d(TAG, "AFT_FSDK_GetVersion:" + version.toString() + "," + err.getCode());

        ASAE_FSDKError error = mAgeEngine.ASAE_FSDK_InitAgeEngine(FaceManagerUtils.appid, FaceManagerUtils.age_key);
        Log.d(TAG, "ASAE_FSDK_InitAgeEngine =" + error.getCode());
        error = mAgeEngine.ASAE_FSDK_GetVersion(mAgeVersion);
        Log.d(TAG, "ASAE_FSDK_GetVersion:" + mAgeVersion.toString() + "," + error.getCode());

        ASGE_FSDKError error1 = mGenderEngine.ASGE_FSDK_InitgGenderEngine(FaceManagerUtils.appid, FaceManagerUtils.gender_key);
        Log.d(TAG, "ASGE_FSDK_InitgGenderEngine =" + error1.getCode());
        error1 = mGenderEngine.ASGE_FSDK_GetVersion(mGenderVersion);
        Log.d(TAG, "ASGE_FSDK_GetVersion:" + mGenderVersion.toString() + "," + error1.getCode());

        mNV21 = new byte[PREVIEW_WIDTH * PREVIEW_HEIGHT * 2];
        mBuffer = new byte[PREVIEW_WIDTH * PREVIEW_HEIGHT * 2];
    }

    Runnable hide = new Runnable() {
        @Override
        public void run() {
           /* mTextView.setAlpha(0.5f);
            mImageView.setImageAlpha(128);*/
            runOnUiThread(new Runnable() {
                @Override
                public void run() {
                    mTextView1.setVisibility(View.GONE);
                    mTextView.setVisibility(View.GONE);
                    mImageView.setVisibility(View.GONE);
                }
            });
        }
    };


    @Override
    protected void onResume() {
        super.onResume();
        SharedPreferences config = getSharedPreferences(MyApp.CONFIG, MODE_PRIVATE);
        String dbip = config.getString(MyApp.DBIP_KEY, "");
        if (StringUtils.isEmpty(dbip)) {
            new AlertDialog.Builder(DetecterActivity.this).setTitle("请设置数据库IP")
                    .setPositiveButton("确定", new DialogInterface.OnClickListener() {
                        @Override
                        public void onClick(DialogInterface dialog, int which) {
                            Intent intent = new Intent(DetecterActivity.this, GroupManageActivity.class);
                            startActivity(intent);
                        }
                    }).show();
        }
        ArrayList<String> groups = MyApp.getDBManage(this).getGroupId();
        if (groups != null && groups.size() > 0) {
            mGroupId = groups.get(0);
        }
        if (mGroupId == null) {
            ToastShow.showTip(getApplicationContext(), "请先建立组");
        }
        //建立线程，访问数据后台
        new Thread(new Runnable() {
            @Override
            public void run() {
                FaceManagerUtils manager = new FaceManagerUtils();
                List<FaceRegist> regists = manager.getAllFaceByGroup(mGroupId, DetecterActivity.this);
                if (null != regists && regists.size() > 0) {
                    mResgist.clear();
                    mResgist.addAll(regists);
                }
            }
        }).start();

        mFRAbsLoop = new FRAbsLoop();
        mFRAbsLoop.start();
    }

    class FRAbsLoop extends AbsLoop {

        AFR_FSDKVersion version = new AFR_FSDKVersion();
        AFR_FSDKEngine engine = new AFR_FSDKEngine();
        AFR_FSDKFace result = new AFR_FSDKFace();
        List<ASAE_FSDKFace> face1 = new ArrayList<>();
        List<ASGE_FSDKFace> face2 = new ArrayList<>();

        @Override
        public void setup() {
            AFR_FSDKError error = engine.AFR_FSDK_InitialEngine(FaceManagerUtils.appid, FaceManagerUtils.fr_key);
            Log.d(TAG, "AFR_FSDK_InitialEngine = " + error.getCode());
            error = engine.AFR_FSDK_GetVersion(version);
            Log.d(TAG, "FR=" + version.toString() + "," + error.getCode()); //(210, 178 - 478, 446), degree = 1　780, 2208 - 1942, 3370
        }

        @Override
        public void loop() {
            if (mImageNV21 != null) {
                long time = System.currentTimeMillis();
                AFR_FSDKError error = engine.AFR_FSDK_ExtractFRFeature(mImageNV21, mWidth, mHeight, AFR_FSDKEngine.CP_PAF_NV21, mAFT_FSDKFace.getRect(), mAFT_FSDKFace.getDegree(), result);
                Log.d(TAG, "AFR_FSDK_ExtractFRFeature cost :" + (System.currentTimeMillis() - time) + "ms");
                Log.d(TAG, "Face=" + result.getFeatureData()[0] + "," + result.getFeatureData()[1] + "," + result.getFeatureData()[2] + "," + error.getCode());
                AFR_FSDKMatching score = new AFR_FSDKMatching();
                float max = 0.0f;
                String name = null;
                if (null != mResgist) {
                    for (FaceRegist fr : mResgist) {
                        for (AFR_FSDKFace face : fr.getFaceList()) {
                            error = engine.AFR_FSDK_FacePairMatching(result, face, score);
                            Log.d(TAG, "Score:" + score.getScore() + ", AFR_FSDK_FacePairMatching=" + error.getCode());
                            if (max < score.getScore()) {
                                max = score.getScore();
                                name = fr.getUser().getName();
                                mUser = fr.getUser();
                            }
                        }
                    }
                }

                //age & gender
                face1.clear();
                face2.clear();
                face1.add(new ASAE_FSDKFace(mAFT_FSDKFace.getRect(), mAFT_FSDKFace.getDegree()));
                face2.add(new ASGE_FSDKFace(mAFT_FSDKFace.getRect(), mAFT_FSDKFace.getDegree()));
                ASAE_FSDKError error1 = mAgeEngine.ASAE_FSDK_AgeEstimation_Image(mImageNV21, mWidth, mHeight, AFT_FSDKEngine.CP_PAF_NV21, face1, ages);
                ASGE_FSDKError error2 = mGenderEngine.ASGE_FSDK_GenderEstimation_Image(mImageNV21, mWidth, mHeight, AFT_FSDKEngine.CP_PAF_NV21, face2, genders);
                Log.d(TAG, "ASAE_FSDK_AgeEstimation_Image:" + error1.getCode() + ",ASGE_FSDK_GenderEstimation_Image:" + error2.getCode());
                Log.d(TAG, "age:" + ages.get(0).getAge() + ",gender:" + genders.get(0).getGender());
                final String age = ages.get(0).getAge() == 0 ? "年龄未知" : ages.get(0).getAge() + "岁";
                final String gender = genders.get(0).getGender() == -1 ? "性别未知" : (genders.get(0).getGender() == 0 ? "男" : "女");

                //crop
                if (mImageNV21 != null) {
                   /* byte[] data = mImageNV21;
                    YuvImage yuv = new YuvImage(data, ImageFormat.NV21, mWidth, mHeight, null);
                    ExtByteArrayOutputStream ops = new ExtByteArrayOutputStream();
                    yuv.compressToJpeg(mAFT_FSDKFace.getRect(), 100, ops);
                    final Bitmap bmp = BitmapFactory.decodeByteArray(ops.getByteArray(), 0, ops.getByteArray().length);
                    try {
                        ops.close();
                    } catch (IOException e) {
                        e.printStackTrace();
                    }
*/
                    synchronized (mImageNV21) {
                        System.arraycopy(mImageNV21, 0, mBuffer, 0, mImageNV21.length);
                    }
                    ByteArrayOutputStream jpeg = nv21ToJPEG(mBuffer, PREVIEW_WIDTH, PREVIEW_HEIGHT);
                    byte[] rawJPEG = jpeg.toByteArray();
                    final Bitmap bmp = BitmapFactory.decodeByteArray(rawJPEG, 0, rawJPEG.length);

                    if (max > 0.6f) {
                        //fr success.
                        final float max_score = max;
                        Log.d(TAG, "fit Score:" + max + ", NAME:" + name);
                        final String mNameShow = name;
                        mHandler.removeCallbacks(hide);
                        mHandler.post(new Runnable() {
                            @Override
                            public void run() {
                                // mTextView.setVisibility(View.VISIBLE);
                                mTextView.setAlpha(1.0f);
                                mTextView.setText(mNameShow);
                                mTextView.setTextColor(Color.RED);
                                //  mTextView1.setVisibility(View.VISIBLE);
                                mTextView1.setText("置信度：" + (float) ((int) (max_score * 1000)) / 1000.0);
                                mTextView1.setTextColor(Color.RED);
                                mImageView.setRotation(mCameraRotate);
                                if (mCameraMirror) {
                                    mImageView.setScaleY(-1);
                                }
                                //    mImageView.setVisibility(View.VISIBLE);
                              //  mImageView.setImageAlpha(255);
                                mImageView.setImageBitmap(bmp);
                            }
                        });
                        detectTimes++;
                        if (detectTimes == 2) {
                            Bitmap cropBitmap = cropWithFace(bmp, mAFT_FSDKFace.getRect());
                            saveBitmapToFile(cropBitmap, "crop.jpg");
                            Intent intent = new Intent(DetecterActivity.this, IdentifyActivity2.class);
                            intent.putExtra("score", (double) ((int) (max_score * 1000)) / 1000.0 * 100);
                            intent.putExtra("user", mUser);
                            startActivity(intent);
                            finish();
                        }
                    } else {
                        final String mNameShow = "未识别";
                        DetecterActivity.this.runOnUiThread(new Runnable() {
                            @Override
                            public void run() {
                                //   mTextView.setVisibility(View.VISIBLE);
                                mTextView.setAlpha(1.0f);
                                //   mTextView1.setVisibility(View.VISIBLE);
                                mTextView1.setText(gender + "," + age);
                                mTextView1.setTextColor(Color.RED);
                                mTextView.setText(mNameShow);
                                mTextView.setTextColor(Color.RED);
                                //mImageView.setImageAlpha(255);
                                mImageView.setRotation(mCameraRotate);
                                if (mCameraMirror) {
                                    mImageView.setScaleY(-1);
                                }
                                //   mImageView.setVisibility(View.VISIBLE);
                                mImageView.setImageBitmap(bmp);
                            }
                        });
                    }
                }
                mImageNV21 = null;
            } else {
                //规定检测时间为：
                SharedPreferences config = getSharedPreferences(MyApp.CONFIG, MODE_PRIVATE);
                String detectTimeValue = config.getString(MyApp.DETECT_TIME_VALUE, "60");
                if (System.currentTimeMillis() - lastTime > Integer.parseInt(detectTimeValue) * 1000) {
                    finish();
                }
            }

        }

        @Override
        public void over() {
            AFR_FSDKError error = engine.AFR_FSDK_UninitialEngine();
            Log.d(TAG, "AFR_FSDK_UninitialEngine : " + error.getCode());
        }
    }

    private void delayedFinish(final int mills) {
        new Thread() {
            @Override
            public void run() {
                super.run();
                try {
                    Thread.sleep(mills);
                } catch (InterruptedException e) {
                    e.printStackTrace();
                }
                 /*   Intent intent = new Intent(IdentifyActivity.this, IndexActivity.class);
                    startActivity(intent);*/
                finish();

            }
        }.start();
    }

    /* (non-Javadoc)
     * @see android.app.Activity#onDestroy()
     */
    @Override
    protected void onDestroy() {
        // TODO Auto-generated method stub
        super.onDestroy();
        if (mFRAbsLoop != null) {
            mFRAbsLoop.shutdown();
        }
        AFT_FSDKError err = engine.AFT_FSDK_UninitialFaceEngine();
        Log.d(TAG, "AFT_FSDK_UninitialFaceEngine =" + err.getCode());

        ASAE_FSDKError err1 = mAgeEngine.ASAE_FSDK_UninitAgeEngine();
        Log.d(TAG, "ASAE_FSDK_UninitAgeEngine =" + err1.getCode());

        ASGE_FSDKError err2 = mGenderEngine.ASGE_FSDK_UninitGenderEngine();
        Log.d(TAG, "ASGE_FSDK_UninitGenderEngine =" + err2.getCode());
    }

    @Override
    public Camera setupCamera() {
        // TODO Auto-generated method stub
        mCamera = Camera.open(mCameraID);
        try {
            Camera.Parameters parameters = mCamera.getParameters();
            parameters.setPreviewSize(mWidth, mHeight);
            parameters.setPreviewFormat(mFormat);

            for (Camera.Size size : parameters.getSupportedPreviewSizes()) {
                Log.d(TAG, "SIZE:" + size.width + "x" + size.height);
            }
            for (Integer format : parameters.getSupportedPreviewFormats()) {
                Log.d(TAG, "FORMAT:" + format);
            }

            List<int[]> fps = parameters.getSupportedPreviewFpsRange();
            for (int[] count : fps) {
                Log.d(TAG, "T:");
                for (int data : count) {
                    Log.d(TAG, "V=" + data);
                }
            }
            //parameters.setPreviewFpsRange(15000, 30000);
            //parameters.setExposureCompensation(parameters.getMaxExposureCompensation());
            //parameters.setWhiteBalance(Camera.Parameters.WHITE_BALANCE_AUTO);
            //parameters.setAntibanding(Camera.Parameters.ANTIBANDING_AUTO);
            //parmeters.setFocusMode(Camera.Parameters.FOCUS_MODE_AUTO);
            //parameters.setSceneMode(Camera.Parameters.SCENE_MODE_AUTO);
            //parameters.setColorEffect(Camera.Parameters.EFFECT_NONE);
            mCamera.setParameters(parameters);
        } catch (Exception e) {
            e.printStackTrace();
        }
        if (mCamera != null) {
            mWidth = mCamera.getParameters().getPreviewSize().width;
            mHeight = mCamera.getParameters().getPreviewSize().height;
        }
        return mCamera;
    }

    @Override
    public void setupChanged(int format, int width, int height) {

    }

    @Override
    public boolean startPreviewLater() {
        // TODO Auto-generated method stub
        return false;
    }

    @Override
    public Object onPreview(byte[] data, int width, int height, int format, long timestamp) {
        AFT_FSDKError err = engine.AFT_FSDK_FaceFeatureDetect(data, width, height, AFT_FSDKEngine.CP_PAF_NV21, result);
        Log.d(TAG, "AFT_FSDK_FaceFeatureDetect =" + err.getCode());
        Log.d(TAG, "Face=" + result.size());
        for (AFT_FSDKFace face : result) {
            Log.d(TAG, "Face:" + face.toString());
        }
        if (mImageNV21 == null) {
            if (!result.isEmpty()) {
                mAFT_FSDKFace = result.get(0).clone();
                mImageNV21 = data.clone();
                //检测到了人脸，记录当前时间
                lastTime = System.currentTimeMillis();
            } else {
                mHandler.postDelayed(hide, 1000);
            }
        }
        //copy rects
        Rect[] rects = new Rect[result.size()];
        for (int i = 0; i < result.size(); i++) {
            rects[i] = new Rect(result.get(i).getRect());
        }
        //clear result.
        result.clear();
        //return the rects for render.
        return rects;
    }

    @Override
    public void onBeforeRender(CameraFrameData data) {

    }

    @Override
    public void onAfterRender(CameraFrameData data) {
        mGLSurfaceView.getGLES2Render().draw_rect((Rect[]) data.getParams(), Color.GREEN, 2);
    }

    @Override
    public boolean onTouch(View v, MotionEvent event) {
        CameraHelper.touchFocus(mCamera, event, v, this);
        return false;
    }

    @Override
    public void onAutoFocus(boolean success, Camera camera) {
        if (success) {
            Log.d(TAG, "Camera Focus SUCCESS!");
        }
    }

    private void saveBitmapToFile(Bitmap bitmap, String fileName) {
        String file_path = getImagePath(fileName);
        File file = new File(file_path);
        FileOutputStream fOut;
        try {
            fOut = new FileOutputStream(file);
            bitmap.compress(Bitmap.CompressFormat.JPEG, 85, fOut);
            fOut.flush();
            fOut.close();
        } catch (FileNotFoundException e) {
            e.printStackTrace();
        } catch (IOException e) {
            e.printStackTrace();
        }

    }

    /**
     * 设置保存图片路径
     *
     * @return
     */
    private String getImagePath(String fileName) {
        String path;
        if (!Environment.getExternalStorageState().equals(Environment.MEDIA_MOUNTED)) {
            return null;
        }
        path = Environment.getExternalStorageDirectory().getAbsolutePath() + "/FaceVocal/";
        File folder = new File(path);
        if (folder != null && !folder.exists()) {
            folder.mkdirs();
        }
        path += fileName;
        return path;
    }

    private Bitmap cropWithFace(Bitmap org, Rect face) {
        int left = face.left;
        int top = face.top;
        int right = face.right;
        int bottom = face.bottom;
        int faceX = right - left;
        int faceY = bottom - top;

        if (faceX >= FACE_WIDTH || faceY >= FACE_HEIGHT) {
            return org;
        }

        int paddingX = (FACE_WIDTH - faceX) / 2 + 1;
        int paddingY = (FACE_HEIGHT - faceY) / 2 + 1;
        left = left - paddingX;
        top = top - paddingY;

        left = left < 0 ? 0 : left;
        top = top < 0 ? 0 : top;
        int width = org.getWidth();
        int height = org.getHeight();
        int cropWidth = left + FACE_WIDTH > width ? width - left : FACE_WIDTH;
        int cropHeight = top + FACE_HEIGHT > height ? height - top : FACE_HEIGHT;

        return Bitmap.createBitmap(org, left, top, cropWidth, cropHeight);

    }

    private ByteArrayOutputStream nv21ToJPEG(byte[] nv21, int width, int height) {
        YuvImage yuv = new YuvImage(nv21, ImageFormat.NV21, width, height, null);
        ByteArrayOutputStream jpeg = new ByteArrayOutputStream();
        Rect rect = new Rect(0, 0, yuv.getWidth(), yuv.getHeight());
        yuv.compressToJpeg(rect, 100, jpeg);
        return jpeg;
    }

}
