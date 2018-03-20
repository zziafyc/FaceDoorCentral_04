package com.example.facedoor;

import android.app.Activity;
import android.app.ProgressDialog;
import android.content.DialogInterface;
import android.content.DialogInterface.OnCancelListener;
import android.content.Intent;
import android.content.SharedPreferences;
import android.database.Cursor;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Canvas;
import android.graphics.Matrix;
import android.graphics.Rect;
import android.media.ExifInterface;
import android.media.MediaScannerConnection;
import android.net.Uri;
import android.os.Bundle;
import android.os.Environment;
import android.os.Handler;
import android.os.Message;
import android.provider.MediaStore;
import android.text.TextUtils;
import android.util.Base64;
import android.util.Log;
import android.view.View;
import android.view.View.OnClickListener;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import com.arcsoft.facedetection.AFD_FSDKEngine;
import com.arcsoft.facedetection.AFD_FSDKError;
import com.arcsoft.facedetection.AFD_FSDKFace;
import com.arcsoft.facedetection.AFD_FSDKVersion;
import com.arcsoft.facerecognition.AFR_FSDKEngine;
import com.arcsoft.facerecognition.AFR_FSDKError;
import com.arcsoft.facerecognition.AFR_FSDKFace;
import com.arcsoft.facerecognition.AFR_FSDKVersion;
import com.example.aicsoft.FaceManagerUtils;
import com.example.facedoor.base.BaseAppCompatActivity;
import com.example.facedoor.db.DBUtil;
import com.example.facedoor.model.Group;
import com.example.facedoor.util.ErrorDesc;
import com.example.facedoor.util.ProgressShow;
import com.example.facedoor.util.ToastShow;
import com.guo.android_extend.image.ImageConverter;
import com.iflytek.cloud.ErrorCode;
import com.iflytek.cloud.IdentityListener;
import com.iflytek.cloud.IdentityResult;
import com.iflytek.cloud.IdentityVerifier;
import com.iflytek.cloud.InitListener;
import com.iflytek.cloud.SpeakerVerifier;
import com.iflytek.cloud.SpeechConstant;
import com.iflytek.cloud.SpeechError;
import com.iflytek.cloud.SpeechListener;

import org.json.JSONException;
import org.json.JSONObject;

import java.io.BufferedOutputStream;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.Serializable;
import java.io.UnsupportedEncodingException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Stack;

import rx.Observable;
import rx.Observable.OnSubscribe;
import rx.Subscriber;
import rx.android.schedulers.AndroidSchedulers;
import rx.functions.Action1;
import rx.functions.Func1;
import rx.schedulers.Schedulers;

public class RegisterActivity2 extends BaseAppCompatActivity implements OnClickListener {

    private final static String TAG = RegisterActivity2.class.getSimpleName();
    private final static int MSG_CODE = 0x1000;
    private final static int MSG_EVENT_REG = 0x1001;
    private final static int MSG_EVENT_NO_FACE = 0x1002;
    private final static int MSG_EVENT_NO_FEATURE = 0x1003;
    private final static int MSG_EVENT_FD_ERROR = 0x1004;
    private final static int MSG_EVENT_FR_ERROR = 0x1005;
    private static final int REQUEST_GROUP_CHOOSE = 88;
    // 选择图片后返回
    public static final int REQUEST_PICK_PICTURE = 1;
    // 拍照后返回
    private final static int REQUEST_CAMERA_IMAGE = 2;
    // 裁剪图片成功后返回
    public static final int REQUEST_INTENT_CROP = 3;
    // 模型操作类型
    private int mModelCmd = MODEL_DEL;
    // 删除模型
    private static final int PWD_TYPE_TEXT = 3;
    // 查询模型
    private static final int MODEL_QUE = 0;
    // 删除模型
    private static final int MODEL_DEL = 1;
    // 密码类型
    // 默认为文字密码
    private int mPwdType = PWD_TYPE_TEXT;
    // 文本密码
    private String mTextPwd = "芝麻开门";

    private Toast mToast;
    private ProgressDialog mProDialog;
    private LinearLayout mGroups;
    private View mLayout;

    private File mPictureFile;
    private byte[] mImageData;
    private Bitmap mImageBitmap = null;

    private IdentityVerifier mIdVerifier;
    private String mAuthId;
    private String mGroupId;
    private HashMap<String, String> mName2ID = new HashMap<String, String>();
    private Stack<String> mGroupJoin = new Stack<String>();
    private ArrayList<String> mGroupJoined = new ArrayList<String>();
    private Stack<String> mGroupQuit = new Stack<String>();
    private volatile boolean mIsStaffExist;
    private List<Group> choosedGroups = new ArrayList<>();
    private TextView chooseTv;

    private final static int JOIN_GROUP = 1001;
    private final static int QUIT_GROUP = 1002;
    private String staffID;
    private String staffName;
    private AFR_FSDKFace mAFR_FSDKFace;
    private Handler groupHandler = new Handler() {
        public void handleMessage(android.os.Message msg) {
            switch (msg.what) {
                case JOIN_GROUP:
                    joinGroup(mAuthId, mGroupJoin.peek());
                    break;

                case QUIT_GROUP:
                    deleteUserFromGroup(mAuthId, mGroupId);
                    break;
            }
        }
    };
    //执行声纹识别的模型删除
    private final static int DELETE = 1000;
    private Handler deleteHandler = new Handler() {
        public void handleMessage(android.os.Message msg) {
            switch (msg.what) {
                case DELETE:
                    //执行声纹删除模型
                    // performModelDelete("del");
                    performModelDelete2("delete");
                    break;

                default:
                    break;
            }
        }

        ;
    };
    private Handler mUIHandler = new Handler() {
        @Override
        public void handleMessage(Message msg) {
            super.handleMessage(msg);
            if (msg.what == MSG_CODE) {
                if (msg.arg1 == MSG_EVENT_REG) {
                    Observable.create(new OnSubscribe<Integer>() {
                        @Override
                        public void call(Subscriber<? super Integer> arg0) {
                            String faceInfo = Base64.encodeToString(mAFR_FSDKFace.getFeatureData(), Base64.DEFAULT);
                            Log.e(TAG, "zziafyc123: " + faceInfo);
                            DBUtil dbUtil = new DBUtil(RegisterActivity2.this);
                            dbUtil.insertUser2(staffID, staffName, faceInfo);
                            int userID = dbUtil.queryUserID(staffID);
                            if (userID == -1) {
                                ToastShow.showTip(mToast, "工号长度超出限制！");
                                return;
                            }
                            arg0.onNext(userID);
                        }
                    }).map(new Func1<Integer, String>() {
                        @Override
                        public String call(final Integer userID) {
                            //加入到本地数据库
                            DBUtil dbUtil = new DBUtil(RegisterActivity2.this);
                            dbUtil.insertUserGroup(userID, mGroupJoined);
                            return "注册成功，已加入所有指定组";

                        }
                    }).subscribeOn(Schedulers.newThread())
                            .observeOn(AndroidSchedulers.mainThread())
                            .subscribe(new Action1<String>() {
                                @Override
                                public void call(String s) {
                                    ProgressShow.stop(mProDialog);
                                    ToastShow.showTip(RegisterActivity2.this, s);
                                }
                            });
                } else if (msg.arg1 == MSG_EVENT_NO_FEATURE) {
                    Toast.makeText(RegisterActivity2.this, "人脸特征无法检测，请换一张图片", Toast.LENGTH_SHORT).show();
                    ProgressShow.stop(mProDialog);
                } else if (msg.arg1 == MSG_EVENT_NO_FACE) {
                    Toast.makeText(RegisterActivity2.this, "没有检测到人脸，请换一张图片", Toast.LENGTH_SHORT).show();
                    ProgressShow.stop(mProDialog);
                } else if (msg.arg1 == MSG_EVENT_FD_ERROR) {
                    Toast.makeText(RegisterActivity2.this, "FD初始化失败，错误码：" + msg.arg2, Toast.LENGTH_SHORT).show();
                    ProgressShow.stop(mProDialog);
                } else if (msg.arg1 == MSG_EVENT_FR_ERROR) {
                    Toast.makeText(RegisterActivity2.this, "FR初始化失败，错误码：" + msg.arg2, Toast.LENGTH_SHORT).show();
                    ProgressShow.stop(mProDialog);
                }
            }
        }
    };

    @Override
    protected int getContentViewLayoutID() {
        return R.layout.activity_register;
    }

    @Override
    protected void initViewsAndEvents() {
        Button btnReg = (Button) findViewById(R.id.online_reg);
        Button btnDelete = (Button) findViewById(R.id.online_delete);
        btnReg.setOnClickListener(this);
        btnDelete.setOnClickListener(this);
        findViewById(R.id.online_pick).setOnClickListener(this);
        findViewById(R.id.online_camera).setOnClickListener(this);
        chooseTv = (TextView) findViewById(R.id.groupChoose);
        chooseTv.setOnClickListener(this);

        SharedPreferences config = getSharedPreferences(MyApp.CONFIG, MODE_PRIVATE);
        String dbIP = config.getString(MyApp.DBIP_KEY, "");
        if (TextUtils.isEmpty(dbIP)) {
            btnReg.setEnabled(false);
            btnDelete.setEnabled(false);
        }

        mToast = Toast.makeText(this, "", Toast.LENGTH_SHORT);
        mGroups = (LinearLayout) findViewById(R.id.online_groups);
        mLayout = findViewById(R.id.register_layout);

        mProDialog = new ProgressDialog(this);
        mProDialog.setCancelable(true);
        mProDialog.setTitle("请稍候");
        // cancel进度框时，取消正在进行的操作
        mProDialog.setOnCancelListener(new OnCancelListener() {

            @Override
            public void onCancel(DialogInterface dialog) {
                if (null != mIdVerifier) {
                    mIdVerifier.cancel();
                }
            }
        });
        mVerifier = SpeakerVerifier.createVerifier(RegisterActivity2.this, null);
        mIdVerifier = IdentityVerifier.createVerifier(this, new InitListener() {

            @Override
            public void onInit(int errorCode) {
                // TODO Auto-generated method stub
                if (errorCode == ErrorCode.SUCCESS) {
                    // ToastShow.showTip(mToast, "引擎初始化成功");
                } else {
                    ToastShow.showTip(mToast, "引擎初始化失败。错误码：" + errorCode);
                }
            }
        });
        mToast = Toast.makeText(RegisterActivity2.this, "", Toast.LENGTH_SHORT);

        // do not put it in onResume(), cropPicture() cause quickly switch from onResume() to onPause()
        // at that time, mName2ID and mGroups are still empty in onPause()
        Observable.create(new OnSubscribe<ArrayList<String>>() {
            @Override
            public void call(Subscriber<? super ArrayList<String>> arg0) {
                DBUtil dbUtil = new DBUtil(RegisterActivity2.this);
                ArrayList<String> id = new ArrayList<String>();
                ArrayList<String> name = new ArrayList<String>();
                dbUtil.queryGroups(id, name);
                int length = id.size();
                for (int i = 0; i < length; i++) {
                    mName2ID.put(name.get(i), id.get(i));
                }
                arg0.onNext(name);
            }
        })
                .subscribeOn(Schedulers.newThread())
                .observeOn(AndroidSchedulers.mainThread())
                .subscribe(new Action1<ArrayList<String>>() {
                    @Override
                    public void call(ArrayList<String> name) {
                      /*  int length = name.size();
                        for (int i = 0; i < length; i++) {
                            CheckBox checkBox = new CheckBox(RegisterActivity.this);
                            checkBox.setBackgroundColor(ContextCompat.getColor(RegisterActivity.this, R.color.white));
                            checkBox.setTextColor(ContextCompat.getColor(RegisterActivity.this, R.color.black));
                            checkBox.setText(name.get(i));
                            mGroups.addView(checkBox);
                        }*/
                    }
                });
    }

    @Override
    protected void onResume() {
        super.onResume();
    }

    @Override
    protected void onPause() {
        super.onPause();
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        if (null != mIdVerifier) {
            mIdVerifier.destroy();
            mIdVerifier = null;
        }
        mName2ID.clear();
        mGroups.removeAllViews();
    }

    @Override
    public void onClick(View arg0) {
        switch (arg0.getId()) {
            case R.id.groupChoose:
                Intent intentChoose = new Intent(this, ChooseGroupActivity.class);
                Bundle bundle = new Bundle();
                bundle.putSerializable("choosedGroups", (Serializable) choosedGroups);
                intentChoose.putExtras(bundle);
                startActivityForResult(intentChoose, REQUEST_GROUP_CHOOSE);
                break;
            case R.id.online_pick:
                Intent intent = new Intent();
                intent.setType("image/*");
                intent.setAction(Intent.ACTION_PICK);
                startActivityForResult(intent, REQUEST_PICK_PICTURE);
                break;
            case R.id.online_camera:
                // 设置相机拍照后照片保存路径
                mPictureFile = new File(Environment.getExternalStorageDirectory(),
                        "picture" + System.currentTimeMillis() / 1000 + ".jpg");
                // 启动拍照,并保存到临时文件
                Intent mIntent = new Intent();
                mIntent.setAction(MediaStore.ACTION_IMAGE_CAPTURE);
                mIntent.putExtra(MediaStore.EXTRA_OUTPUT, Uri.fromFile(mPictureFile));
                mIntent.putExtra(MediaStore.Images.Media.ORIENTATION, 0);
                startActivityForResult(mIntent, REQUEST_CAMERA_IMAGE);
                break;
            case R.id.online_reg:
                // 人脸注册
                faceRegister2();
                break;
            case R.id.online_delete:
                executeModelCommand("delete");
                break;
        }
    }

    private void faceRegister2() {
        // 人脸注册
        staffID = ((EditText) findViewById(R.id.online_number)).getText().toString();
        if (TextUtils.isEmpty(staffID)) {
            ToastShow.showTip(mToast, "工号不能为空");
            return;
        }

        staffName = ((EditText) findViewById(R.id.online_name)).getText().toString();
        if (TextUtils.isEmpty(staffName)) {
            ToastShow.showTip(mToast, "用户名不能为空");
            return;
        }
        if (mGroupJoined.size() == 0) {
            ToastShow.showTip(mToast, "请勾选组");
            return;
        }
        if (mImageData == null) {
            ToastShow.showTip(mToast, "请选择图片后再注册");
            return;
        }

        mIsStaffExist = false;
        Runnable queryStaffID = new Runnable() {
            public void run() {
                DBUtil dbUtil = new DBUtil(RegisterActivity2.this);
                try {
                    mIsStaffExist = dbUtil.isStaffExist(staffID);
                } catch (Exception e) {
                    e.printStackTrace();
                }
            }
        };
        Thread queryThread = new Thread(queryStaffID);
        queryThread.start();
        try {
            queryThread.join();
        } catch (InterruptedException e) {
            e.printStackTrace();
        }
        if (mIsStaffExist) {
            ToastShow.showTip(mToast, "用户已存在");
            return;
        }
        ProgressShow.show(mProDialog, "注册中...");
        initEngine();
    }

    //初始化引擎
    public void initEngine() {
        byte[] data = new byte[mImageBitmap.getWidth() * mImageBitmap.getHeight() * 3 / 2];
        ImageConverter convert = new ImageConverter();
        convert.initial(mImageBitmap.getWidth(), mImageBitmap.getHeight(), ImageConverter.CP_PAF_NV21);
        if (convert.convert(mImageBitmap, data)) {
            Log.d(TAG, "convert ok!");
        }
        convert.destroy();
        //初始化引擎
        AFD_FSDKEngine engine = new AFD_FSDKEngine();
        AFD_FSDKVersion version = new AFD_FSDKVersion();
        List<AFD_FSDKFace> result = new ArrayList<>();
        AFD_FSDKError err = engine.AFD_FSDK_InitialFaceEngine(FaceManagerUtils.appid, FaceManagerUtils.fd_key, AFD_FSDKEngine.AFD_OPF_0_HIGHER_EXT, 16, 5);
        Log.d(TAG, "AFD_FSDK_InitialFaceEngine = " + err.getCode());
        if (err.getCode() != AFD_FSDKError.MOK) {
            Message reg = Message.obtain();
            reg.what = MSG_CODE;
            reg.arg1 = MSG_EVENT_FD_ERROR;
            reg.arg2 = err.getCode();
            mUIHandler.sendMessage(reg);
        }
        err = engine.AFD_FSDK_GetVersion(version);
        Log.d(TAG, "AFD_FSDK_GetVersion =" + version.toString() + ", " + err.getCode());
        err = engine.AFD_FSDK_StillImageFaceDetection(data, mImageBitmap.getWidth(), mImageBitmap.getHeight(), AFD_FSDKEngine.CP_PAF_NV21, result);
        Log.d(TAG, "AFD_FSDK_StillImageFaceDetection =" + err.getCode() + "<" + result.size());
        if (!result.isEmpty()) {
            AFR_FSDKVersion version1 = new AFR_FSDKVersion();
            AFR_FSDKEngine engine1 = new AFR_FSDKEngine();
            AFR_FSDKFace result1 = new AFR_FSDKFace();
            AFR_FSDKError error1 = engine1.AFR_FSDK_InitialEngine(FaceManagerUtils.appid, FaceManagerUtils.fr_key);
            Log.d("com.arcsoft", "AFR_FSDK_InitialEngine = " + error1.getCode());
            if (error1.getCode() != AFD_FSDKError.MOK) {
                Message reg = Message.obtain();
                reg.what = MSG_CODE;
                reg.arg1 = MSG_EVENT_FR_ERROR;
                reg.arg2 = error1.getCode();
                mUIHandler.sendMessage(reg);
            }
            error1 = engine1.AFR_FSDK_GetVersion(version1);
            Log.d("com.arcsoft", "FR=" + version.toString() + "," + error1.getCode()); //(210, 178 - 478, 446), degree = 1　780, 2208 - 1942, 3370
            error1 = engine1.AFR_FSDK_ExtractFRFeature(data, mImageBitmap.getWidth(), mImageBitmap.getHeight(), AFR_FSDKEngine.CP_PAF_NV21, new Rect(result.get(0).getRect()), result.get(0).getDegree(), result1);
            Log.d("com.arcsoft", "Face=" + result1.getFeatureData()[0] + "," + result1.getFeatureData()[1] + "," + result1.getFeatureData()[2] + "," + error1.getCode());
            if (error1.getCode() == error1.MOK) {
                mAFR_FSDKFace = result1.clone();
                int width = result.get(0).getRect().width();
                int height = result.get(0).getRect().height();
                Bitmap face_bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.RGB_565);
                Canvas face_canvas = new Canvas(face_bitmap);
                face_canvas.drawBitmap(mImageBitmap, result.get(0).getRect(), new Rect(0, 0, width, height), null);
                Message reg = Message.obtain();
                reg.what = MSG_CODE;
                reg.arg1 = MSG_EVENT_REG;
                reg.obj = face_bitmap;
                mUIHandler.sendMessage(reg);
            } else {
                Message reg = Message.obtain();
                reg.what = MSG_CODE;
                reg.arg1 = MSG_EVENT_NO_FEATURE;
                mUIHandler.sendMessage(reg);
            }
            error1 = engine1.AFR_FSDK_UninitialEngine();
            Log.d("com.arcsoft", "AFR_FSDK_UninitialEngine : " + error1.getCode());
        } else {
            Message reg = Message.obtain();
            reg.what = MSG_CODE;
            reg.arg1 = MSG_EVENT_NO_FACE;
            mUIHandler.sendMessage(reg);
        }
        err = engine.AFD_FSDK_UninitialFaceEngine();
        Log.d(TAG, "AFD_FSDK_UninitialFaceEngine =" + err.getCode());
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        // TODO Auto-generated method stub
        super.onActivityResult(requestCode, resultCode, data);
        if (resultCode != RESULT_OK) {
            return;
        }
        String fileSrc = null;
        if (requestCode == REQUEST_PICK_PICTURE) {
            if ("file".equals(data.getData().getScheme())) {
                // 有些低版本机型返回的Uri模式为file
                fileSrc = data.getData().getPath();
            } else {
                // Uri模型为content
                String[] proj = {MediaStore.Images.Media.DATA};
                Cursor cursor = getContentResolver().query(data.getData(), proj,
                        null, null, null);
                cursor.moveToFirst();
                int idx = cursor.getColumnIndexOrThrow(MediaStore.Images.Media.DATA);
                fileSrc = cursor.getString(idx);
                cursor.close();
            }
            // 跳转到图片裁剪页面
            cropPicture(this, Uri.fromFile(new File(fileSrc)));
        } else if (requestCode == REQUEST_CAMERA_IMAGE) {
            if (null == mPictureFile) {
                ToastShow.showTip(mToast, "拍照失败，请重试");
                return;
            }

            fileSrc = mPictureFile.getAbsolutePath();
            updateGallery(fileSrc);
            // 跳转到图片裁剪页面,需要先进行图片镜像翻转
            Bitmap bitmap = BitmapFactory.decodeFile(fileSrc);
            File file = new File(getImagePath2());//将要保存图片的路径
            try {
                BufferedOutputStream bos = new BufferedOutputStream(new FileOutputStream(file));
                bitmap.compress(Bitmap.CompressFormat.JPEG, 100, bos);
                bos.flush();
                bos.close();
            } catch (IOException e) {
                e.printStackTrace();
            }

            cropPicture(this, Uri.fromFile(new File(getImagePath2())));

        } else if (requestCode == REQUEST_INTENT_CROP) {
            // 获取返回数据
            Bitmap bmp = data.getParcelableExtra("data");

            // 获取裁剪后图片保存路径
            fileSrc = getImagePath();

            // 若返回数据不为null，保存至本地，防止裁剪时未能正常保存
            if (null != bmp) {
                saveBitmapToFile(bmp);
            }

            // 获取图片的宽和高
            BitmapFactory.Options options = new BitmapFactory.Options();
            options.inJustDecodeBounds = true;
            mImageBitmap = BitmapFactory.decodeFile(fileSrc, options);

            // 压缩图片
            options.inSampleSize = Math.max(1, (int) Math.ceil(Math.max(
                    (double) options.outWidth / 1024f,
                    (double) options.outHeight / 1024f)));
            options.inJustDecodeBounds = false;
            mImageBitmap = BitmapFactory.decodeFile(fileSrc, options);

            // 若mImageBitmap为空则图片信息不能正常获取
            if (null == mImageBitmap) {
                ToastShow.showTip(this, "图片信息无法正常获取！");
                return;
            }

            // 部分手机会对图片做旋转，这里检测旋转角度
            int degree = readPictureDegree(fileSrc);
            if (degree != 0) {
                // 把图片旋转为正的方向
                mImageBitmap = rotateImage(degree, mImageBitmap);

            }
            ByteArrayOutputStream baos = new ByteArrayOutputStream();

            //可根据流量及网络状况对图片进行压缩
            mImageBitmap.compress(Bitmap.CompressFormat.JPEG, 80, baos);
            mImageData = baos.toByteArray();

            ((ImageView) findViewById(R.id.online_img)).setImageBitmap(mImageBitmap);

        } else if (requestCode == REQUEST_GROUP_CHOOSE) {
            if (data != null) {
                choosedGroups = (List<Group>) data.getSerializableExtra("choosedGroups");
                if (choosedGroups != null) {
                    chooseTv.setText("已选择" + choosedGroups.size() + "个组,点击选择？");
                    if (choosedGroups.size() > 0) {
                        for (Group group : choosedGroups) {
                            mGroupJoin.push(group.getId());
                            mGroupJoined.add(group.getId());
                        }
                    }
                }
            }
        }
    }

    private void identify() {
        if (mImageData == null) {
            ToastShow.showTip(mToast, "请选择图片后再验证");
            return;
        }
        ArrayList<String> groupIdList = MyApp.getDBManage(this).getGroupId();
        mGroupId = groupIdList.get(0);
        if (mGroupId == null) {
            ToastShow.showTip(mToast, "请先建立组");
            return;
        }
        ProgressShow.show(mProDialog, "鉴别中。。。");

        // 清空参数
        mIdVerifier.setParameter(SpeechConstant.PARAMS, null);
        // 设置业务场景
        mIdVerifier.setParameter(SpeechConstant.MFV_SCENES, "ifr");
        // 设置业务类型
        mIdVerifier.setParameter(SpeechConstant.MFV_SST, "identify");
        // 设置监听器，开始会话
        mIdVerifier.startWorking(mSearchListener);
        // 子业务执行参数，若无可以传空字符传
        StringBuffer params = new StringBuffer();
        params.append(",group_id=" + mGroupId + ",topc=3");
        // 向子业务写入数据，人脸数据可以一次写入
        mIdVerifier.writeData("ifr", params.toString(), mImageData, 0, mImageData.length);
        // 写入完毕
        mIdVerifier.stopWrite("ifr");
    }

    private void verify() {
        // 人脸验证
        String authName = ((EditText) findViewById(R.id.online_name)).getText().toString();
        if (TextUtils.isEmpty(authName)) {
            ToastShow.showTip(mToast, "用户名不能为空");
            return;
        }
        String authNameUTF8 = null;
        try {
            byte[] nameBytes = authName.getBytes("UTF-8");
            authNameUTF8 = new String(nameBytes, "UTF-8");
        } catch (UnsupportedEncodingException e) {
            e.printStackTrace();
        }
        if (mImageData == null) {
            ToastShow.showTip(mToast, "请选择图片后再验证");
            return;
        }
        int userId = MyApp.getDBManage(this).queryUserId(authNameUTF8);
        if (userId == 0) {
            ToastShow.showTip(mToast, "用户不存在");
            return;
        }
        mAuthId = userIdToAuthId(userId);
        ProgressShow.show(mProDialog, "验证中");
        // 设置人脸验证参数
        mIdVerifier.setParameter(SpeechConstant.PARAMS, null);
        // 设置会话场景
        mIdVerifier.setParameter(SpeechConstant.MFV_SCENES, "ifr");
        // 设置会话类型
        mIdVerifier.setParameter(SpeechConstant.MFV_SST, "verify");
        // 设置验证模式，单一验证模式：sin
        mIdVerifier.setParameter(SpeechConstant.MFV_VCM, "sin");
        // 用户id
        mIdVerifier.setParameter(SpeechConstant.AUTH_ID, mAuthId);
        // 设置监听器，开始会话
        mIdVerifier.startWorking(mVerifyListener);
        // 子业务执行参数，若无可以传空字符传
        StringBuffer params = new StringBuffer();
        // 向子业务写入数据，人脸数据可以一次写入
        mIdVerifier.writeData("ifr", params.toString(), mImageData, 0, mImageData.length);
        // 停止写入
        mIdVerifier.stopWrite("ifr");
    }

    private void executeModelCommand(final String cmd) {
        final String staffID = ((EditText) findViewById(R.id.online_number)).getText().toString();
        if (TextUtils.isEmpty(staffID)) {
            ToastShow.showTip(mToast, "工号不能为空");
            return;
        }
        Observable.create(new OnSubscribe<Integer>() {
            @Override
            public void call(Subscriber<? super Integer> arg0) {
                DBUtil dbUtil = new DBUtil(RegisterActivity2.this);
                int userID = dbUtil.queryUserID(staffID);
                arg0.onNext(userID);
            }
        })
                .subscribeOn(Schedulers.newThread())
                .observeOn(AndroidSchedulers.mainThread())
                .subscribe(new Action1<Integer>() {
                    @Override
                    public void call(Integer arg0) {
                        int userID = arg0.intValue();
                        if (userID == -1) {
                            ToastShow.showTip(mToast, "用户不存在");
                            return;
                        }
                        ProgressShow.show(mProDialog, "删除中。。。");
                        mAuthId = userIdToAuthId(userID);
                       /* // 设置人脸模型操作参数
                        // 清空参数
                        mIdVerifier.setParameter(SpeechConstant.PARAMS, null);
                        // 设置会话场景
                        mIdVerifier.setParameter(SpeechConstant.MFV_SCENES, "ifr");
                        // 用户id
                        mIdVerifier.setParameter(SpeechConstant.AUTH_ID, mAuthId);
                        // 设置模型参数，若无可以传空字符传
                        StringBuffer params = new StringBuffer();
                        // 执行模型操作
                        mIdVerifier.execute("ifr", cmd, params.toString(), mModelListener);*/
                        Observable.create(new OnSubscribe<String>() {
                            @Override
                            public void call(Subscriber<? super String> arg0) {
                                int userID = AuthIdToUserId(mAuthId);
                                DBUtil dbUtil = new DBUtil(RegisterActivity2.this);
                                //先退出用户表
                                dbUtil.deleteUser(userID);
                                arg0.onNext(userID + "");
                            }
                        }).subscribeOn(Schedulers.io())
                                .observeOn(Schedulers.newThread())
                                .doOnNext(new Action1<String>() {
                                    @Override
                                    public void call(String userId) {
                                        //然后退出组别表
                                        DBUtil dbUtil = new DBUtil(RegisterActivity2.this);
                                        dbUtil.deleteUserGroup(Integer.parseInt(userId));
                                    }
                                })
                                .observeOn(AndroidSchedulers.mainThread())
                                .subscribe(new Action1<String>() {
                                    public void call(String groupID) {
                                        ProgressShow.stop(mProDialog);
                                        ToastShow.showTip(RegisterActivity2.this, "已删除");
                                    }
                                });
                    }
                });
    }

    private void deleteUserFromGroup(String authId, String groupId) {
        ProgressShow.show(mProDialog, "正在退出组...");
        // sst=add，auth_id=eqhe，group_id=123456，scope=person
        mIdVerifier.setParameter(SpeechConstant.PARAMS, null);
        // 设置会话场景
        mIdVerifier.setParameter(SpeechConstant.MFV_SCENES, "ipt");
        // 用户id
        mIdVerifier.setParameter(SpeechConstant.AUTH_ID, authId);
        // 设置模型参数，若无可以传空字符传
        StringBuffer params2 = new StringBuffer();
        // 删除组中指定auth_id用户
        params2.append("scope=person");
        params2.append(",auth_id=" + authId);
        params2.append(",group_id=" + groupId);
        // 执行模型操作
        mIdVerifier.execute("ipt", "delete", params2.toString(), mDeleteListener);
    }

    private void joinGroup(String authId, String groupId) {
        ProgressShow.show(mProDialog, "正在加入组...");
        // sst=add，auth_id=eqhe，group_id=123456，scope=person
        mIdVerifier.setParameter(SpeechConstant.PARAMS, null);
        // 设置会话场景
        mIdVerifier.setParameter(SpeechConstant.MFV_SCENES, "ipt");
        // 用户id
        mIdVerifier.setParameter(SpeechConstant.AUTH_ID, authId);
        // 设置模型参数，若无可以传空字符传
        StringBuffer params2 = new StringBuffer();
        params2.append("auth_id=" + authId);
        params2.append(",scope=person");
        params2.append(",group_id=" + groupId);
        // 执行模型操作
        mIdVerifier.execute("ipt", "add", params2.toString(), mAddListener);

    }

    /**
     * 人脸鉴别监听器
     */
    private IdentityListener mSearchListener = new IdentityListener() {

        @Override
        public void onResult(IdentityResult result, boolean islast) {
            Log.d(TAG, result.getResultString());

            if (mProDialog != null) {
                ProgressShow.stop(mProDialog);
            }
            identifyResult(result);
        }

        @Override
        public void onEvent(int eventType, int arg1, int arg2, Bundle obj) {
        }

        @Override
        public void onError(SpeechError error) {
            if (mProDialog != null) {
                ProgressShow.stop(mProDialog);
            }
            ToastShow.showTip(mToast, error.getPlainDescription(true));
        }

    };

    /**
     * 人脸验证监听器
     */
    private IdentityListener mVerifyListener = new IdentityListener() {

        @Override
        public void onResult(IdentityResult result, boolean islast) {
            Log.d(TAG, result.getResultString());

            if (null != mProDialog) {
                ProgressShow.stop(mProDialog);
            }

            try {
                JSONObject object = new JSONObject(result.getResultString());
                String decision = object.getString("decision");

                if ("accepted".equalsIgnoreCase(decision)) {
                    ToastShow.showTip(mToast, "通过验证");
                } else {
                    ToastShow.showTip(mToast, "验证失败");
                }
            } catch (JSONException e) {
                e.printStackTrace();
            }
        }

        @Override
        public void onEvent(int eventType, int arg1, int arg2, Bundle obj) {
        }

        @Override
        public void onError(SpeechError error) {
            if (null != mProDialog) {
                ProgressShow.stop(mProDialog);
            }
            ToastShow.showTip(mToast, error.getPlainDescription(true));
        }

    };

    private IdentityListener mDeleteListener = new IdentityListener() {
        @Override
        public void onResult(IdentityResult result, boolean islast) {
            Log.d(TAG, result.getResultString());
            if (null != mProDialog) {
                ProgressShow.stop(mProDialog);
            }
            try {
                JSONObject resObj = new JSONObject(result.getResultString());
                int ret = resObj.getInt("ret");
                if (0 != ret) {
                    onError(new SpeechError(ret));
                    return;
                } else {
                    if (result.getResultString().contains("user")) {
                        String user = resObj.getString("user");
                        //  ToastShow.showTip(mToast, "删除组成员" + user + "成功");
                        mGroupQuit.pop();
                        if (mGroupQuit.size() != 0) {
                            mGroupId = mGroupQuit.peek();
                            groupHandler.sendEmptyMessage(QUIT_GROUP);
                        } else {
                            new Thread() {
                                public void run() {
                                    DBUtil dbUtil = new DBUtil(RegisterActivity2.this);
                                    dbUtil.deleteUserGroup(AuthIdToUserId(mAuthId));
                                }
                            }.start();
                            deleteHandler.sendEmptyMessage(DELETE);
                        }
                    }
                }
            } catch (JSONException e) {
                e.printStackTrace();
            }
        }

        @Override
        public void onEvent(int eventType, int arg1, int arg2, Bundle obj) {

        }

        @Override
        public void onError(SpeechError error) {
            Log.d(TAG, error.getPlainDescription(true));
            if (null != mProDialog) {
                ProgressShow.stop(mProDialog);
            }
            ToastShow.showTip(mToast, ErrorDesc.getDesc(error) + ":" + error.getErrorCode());
            // 1 reason put in onError(): group deleted before user quit(你输入的组尚未创建). Clean data base.
            new Thread() {
                public void run() {
                    DBUtil dbUtil = new DBUtil(RegisterActivity2.this);
                    dbUtil.deleteUserGroup(AuthIdToUserId(mAuthId));
                }
            }.start();
        }
    };

    private void performModelDelete(String operation) {

        mVerifier.setParameter(SpeechConstant.PARAMS, null);
        mVerifier.setParameter(SpeechConstant.ISV_PWDT, "" + mPwdType);

        if (mPwdType == PWD_TYPE_TEXT) {
            mVerifier.setParameter(SpeechConstant.ISV_PWD, mTextPwd);
        }
        mVerifier.sendRequest(operation, mAuthId, listener);
    }

    private SpeechListener listener = new SpeechListener() {

        @Override
        public void onEvent(int arg0, Bundle arg1) {

        }

        @Override
        public void onCompleted(SpeechError error) {
            if (null != error && ErrorCode.SUCCESS != error.getErrorCode()) {
                ToastShow.showTip(mToast, "操作失败：" + error.getPlainDescription(true));
            }
        }

        @Override
        public void onBufferReceived(byte[] buffer) {
            String result = new String(buffer);
            try {
                JSONObject object = new JSONObject(result);
                String cmd = object.getString("cmd");
                int ret = object.getInt("ret");

                if ("del".equals(cmd)) {
                    if (ret == ErrorCode.SUCCESS) {
                        ToastShow.showTip(mToast, "声纹删除成功");
                    } else if (ret == ErrorCode.MSP_ERROR_FAIL) {
                        ToastShow.showTip(mToast, "声纹删除失败，模型不存在");
                    }
                    //fyc声纹删除之后需要finish界面
                    finish();
                }
            } catch (JSONException e) {
                e.printStackTrace();
            }
        }
    };

    //执行删除声纹
    private void performModelDelete2(String cmd) {
        mProDialog.setMessage("声纹删除中...");
        mProDialog.show();
        // 设置声纹模型参数
        // 清空参数
        mIdVerifier.setParameter(SpeechConstant.PARAMS, null);
        // 设置会话场景
        mIdVerifier.setParameter(SpeechConstant.MFV_SCENES, "ivp");
        // 用户id
        mIdVerifier.setParameter(SpeechConstant.AUTH_ID, mAuthId);

        // 子业务执行参数，若无可以传空字符传
        StringBuffer params3 = new StringBuffer();
        // 设置模型操作的密码类型
        params3.append("pwdt=" + mPwdType + ",");
        // 执行模型操作
        mIdVerifier.execute("ivp", cmd, params3.toString(), mDeleteModelListener);

    }

    private IdentityListener mDeleteModelListener = new IdentityListener() {

        @Override
        public void onResult(IdentityResult result, boolean islast) {
            Log.d(TAG, "model operation:" + result.getResultString());

            mProDialog.dismiss();

            JSONObject jsonResult = null;
            int ret = ErrorCode.SUCCESS;
            try {
                jsonResult = new JSONObject(result.getResultString());
                ret = jsonResult.getInt("ret");
            } catch (JSONException e) {
                e.printStackTrace();
            }

            switch (mModelCmd) {
                case MODEL_QUE:
                    if (ErrorCode.SUCCESS == ret) {
                        ToastShow.showTip(RegisterActivity2.this, "模型存在");
                    } else {
                        ToastShow.showTip(RegisterActivity2.this, "模型不存在");
                    }
                    break;
                case MODEL_DEL:
                    if (ErrorCode.SUCCESS == ret) {
                        ToastShow.showTip(RegisterActivity2.this, "模型已删除");
                        finish();
                    } else {
                        ToastShow.showTip(RegisterActivity2.this, "模型删除失败");
                    }
                    break;

                default:
                    break;
            }
        }

        @Override
        public void onEvent(int eventType, int arg1, int arg2, Bundle obj) {
        }

        @Override
        public void onError(SpeechError error) {
            mProDialog.dismiss();
            ToastShow.showTip(RegisterActivity2.this, error.getPlainDescription(true));
        }
    };

    /**
     * 人脸模型操作监听器
     */
    private IdentityListener mModelListener = new IdentityListener() {

        @Override
        public void onResult(IdentityResult result, boolean islast) {
            Log.d(TAG, result.getResultString());

            ProgressShow.stop(mProDialog);
            JSONObject jsonResult = null;
            int ret = ErrorCode.SUCCESS;
            try {
                jsonResult = new JSONObject(result.getResultString());
                ret = jsonResult.getInt("ret");
            } catch (JSONException e) {
                e.printStackTrace();
            }
            // 根据操作类型判断结果类型
            if (ErrorCode.SUCCESS == ret) {
                ToastShow.showTip(mToast, "删除成功");
                Observable.create(new OnSubscribe<String>() {
                    public void call(Subscriber<? super String> arg0) {
                        int userID = AuthIdToUserId(mAuthId);
                        DBUtil dbUtil = new DBUtil(RegisterActivity2.this);
                        dbUtil.deleteUser(userID);
                        ArrayList<String> groupIDs = dbUtil.queryUserGroups(userID);
                        for (int i = 0; i < groupIDs.size(); i++) {
                            mGroupQuit.push(groupIDs.get(i));
                        }
                        String groupID = mGroupQuit.peek();
                        arg0.onNext(groupID);
                    }
                })
                        .subscribeOn(Schedulers.newThread())
                        .observeOn(AndroidSchedulers.mainThread())
                        .subscribe(new Action1<String>() {
                            public void call(String groupID) {
                                deleteUserFromGroup(mAuthId, groupID);
                            }
                        });
            } else {
                ToastShow.showTip(mToast, "删除失败");
            }
        }

        @Override
        public void onEvent(int eventType, int arg1, int arg2, Bundle obj) {
        }

        @Override
        public void onError(SpeechError error) {
            ProgressShow.stop(mProDialog);
            // 弹出错误信息
            ToastShow.showTip(mToast, error.getPlainDescription(true));
        }

    };
    /**
     * 声纹模型操作监听器
     */
    private IdentityListener mModelDeleteListener = new IdentityListener() {

        @Override
        public void onResult(IdentityResult result, boolean islast) {
            Log.e(TAG, "model operation:" + result.getResultString());

            mProDialog.dismiss();

            JSONObject jsonResult = null;
            int ret = ErrorCode.SUCCESS;
            try {
                jsonResult = new JSONObject(result.getResultString());
                ret = jsonResult.getInt("ret");
            } catch (JSONException e) {
                e.printStackTrace();
            }
            switch (mModelCmd) {
                case MODEL_DEL:
                    if (ErrorCode.SUCCESS == ret) {
                        ToastShow.showTip(mToast, "声纹模型已删除");
                    } else {
                        ToastShow.showTip(mToast, "声纹模型删除失败");
                    }
                    break;

                default:
                    break;
            }
        }

        @Override
        public void onEvent(int eventType, int arg1, int arg2, Bundle arg3) {
        }

        @Override
        public void onError(SpeechError error) {
            mProDialog.dismiss();
            ToastShow.showTip(mToast, error.getPlainDescription(true));
        }
    };

    /**
     * 加入组监听器
     */
    private IdentityListener mAddListener = new IdentityListener() {

        @Override
        public void onResult(IdentityResult result, boolean islast) {
            Log.d(TAG, result.getResultString());
            if (null != mProDialog) {
                ProgressShow.stop(mProDialog);
            }
            try {
                JSONObject resObj = new JSONObject(result.getResultString());
                int ret = resObj.getInt("ret");
                if (ret == ErrorCode.SUCCESS) {
                    mGroupJoined.add(mGroupJoin.pop());
                    if (mGroupJoin.size() != 0) {
                        groupHandler.sendEmptyMessage(JOIN_GROUP);
                    } else {
                        //fyc将这个换成了用rxJava来实现
                        Observable.create(new OnSubscribe<String>() {
                            @Override
                            public void call(Subscriber<? super String> subscriber) {
                                int userID = AuthIdToUserId(mAuthId);
                                DBUtil dbUtil = new DBUtil(RegisterActivity2.this);
                                dbUtil.insertUserGroup(userID, mGroupJoined);
                                subscriber.onNext(mAuthId);

                            }
                        })
                                .subscribeOn(Schedulers.newThread())
                                .observeOn(AndroidSchedulers.mainThread())
                                .subscribe(new Subscriber<String>() {

                                    @Override
                                    public void onCompleted() {

                                    }

                                    @Override
                                    public void onError(Throwable throwable) {

                                    }

                                    @Override
                                    public void onNext(String mAuthId) {
                                        ToastShow.showTip(mToast, "人脸注册成功");
                                        SharedPreferences config = getSharedPreferences(MyApp.CONFIG, MODE_PRIVATE);
                                        int faceOnly = config.getInt(MyApp.FACEONLY, 0);
                                        Log.e("zziafyc", faceOnly + "");
                                        if (faceOnly == 0) {
                                            // 跳转到声纹识别界面
                                            Intent intent = new Intent();
                                            intent.putExtra("ID", mAuthId);
                                            intent.setClass(RegisterActivity2.this, IsvDemo2.class);
                                            startActivity(intent);
                                        }
                                        finish();
                                    }
                                });


                    }

                } else {
                    onError(new SpeechError(ret));
                }
            } catch (JSONException e) {
                e.printStackTrace();
            }
        }

        @Override
        public void onEvent(int eventType, int arg1, int arg2, Bundle obj) {
        }

        @Override
        public void onError(SpeechError error) {
            Log.d(TAG, error.getPlainDescription(true));
            if (null != mProDialog) {
                ProgressShow.stop(mProDialog);
            }
            ToastShow.showTip(mToast, ErrorDesc.getDesc(error) + ":" + error.getErrorCode());
            // if encounter an error, insert records before the error
            new Thread() {
                public void run() {
                    int userID = AuthIdToUserId(mAuthId);
                    DBUtil dbUtil = new DBUtil(RegisterActivity2.this);
                    dbUtil.insertUserGroup(userID, mGroupJoined);
                }
            }.start();
        }
    };

    /**
     * 人脸注册监听器
     */
    private IdentityListener mEnrollListener = new IdentityListener() {

        @Override
        public void onResult(IdentityResult result, boolean islast) {
            Log.d(TAG, result.getResultString());

            if (null != mProDialog) {
                ProgressShow.stop(mProDialog);
            }

            try {
                JSONObject object = new JSONObject(result.getResultString());
                int ret = object.getInt("ret");

                if (ErrorCode.SUCCESS == ret) {
                    // join group. we ensure that mGroupJoin is not empty
                    joinGroup(mAuthId, mGroupJoin.peek());
                } else {
                    onError(new SpeechError(ret));
                }
            } catch (JSONException e) {
                e.printStackTrace();
            }
        }

        @Override
        public void onEvent(int eventType, int arg1, int arg2, Bundle obj) {
        }

        @Override
        public void onError(SpeechError error) {
            if (null != mProDialog) {
                mProDialog.dismiss();
            }
            ToastShow.showTip(mToast, error.getPlainDescription(true));
            new Thread() {
                public void run() {
                    int userId = AuthIdToUserId(mAuthId);
                    DBUtil dbUtil = new DBUtil(RegisterActivity2.this);
                    dbUtil.deleteUser(userId);
                }
            }.start();
        }

    };
    private SpeakerVerifier mVerifier;

    /***
     * 裁剪图片
     * @param activity Activity
     * @param uri 图片的Uri
     */
    public void cropPicture(Activity activity, Uri uri) {
        Intent innerIntent = new Intent("com.android.camera.action.CROP");
        innerIntent.setDataAndType(uri, "image/*");
        innerIntent.putExtra("crop", "true");// 才能出剪辑的小方框，不然没有剪辑功能，只能选取图片
        innerIntent.putExtra("aspectX", 1); // 放大缩小比例的X
        innerIntent.putExtra("aspectY", 1);// 放大缩小比例的X   这里的比例为：   1:1
        innerIntent.putExtra("outputX", 320);  //这个是限制输出图片大小
        innerIntent.putExtra("outputY", 320);
        innerIntent.putExtra("return-data", false);
        // 切图大小不足输出，无黑框
        innerIntent.putExtra("scale", true);
        innerIntent.putExtra("scaleUpIfNeeded", true);
        innerIntent.putExtra(MediaStore.EXTRA_OUTPUT, Uri.fromFile(new File(getImagePath())));
        innerIntent.putExtra("outputFormat", Bitmap.CompressFormat.JPEG.toString());
        activity.startActivityForResult(innerIntent, REQUEST_INTENT_CROP);
    }

    /**
     * 设置保存图片路径
     *
     * @return
     */
    private String getImagePath() {
        String path;
        if (!Environment.getExternalStorageState().equals(Environment.MEDIA_MOUNTED)) {
            return null;
        }
        path = Environment.getExternalStorageDirectory().getAbsolutePath() + "/FaceDoor/";
        File folder = new File(path);
        if (folder != null && !folder.exists()) {
            folder.mkdirs();
        }
        path += "crop.jpg";
        return path;
    }

    private String getImagePath2() {
        String path;
        if (!Environment.getExternalStorageState().equals(Environment.MEDIA_MOUNTED)) {
            return null;
        }
        path = Environment.getExternalStorageDirectory().getAbsolutePath() + "/FaceDoor/";
        File folder = new File(path);
        if (folder != null && !folder.exists()) {
            folder.mkdirs();
        }
        path += "flip.jpg";
        return path;
    }

    private void updateGallery(String filename) {
        MediaScannerConnection.scanFile(this, new String[]{filename}, null,
                new MediaScannerConnection.OnScanCompletedListener() {

                    @Override
                    public void onScanCompleted(String path, Uri uri) {

                    }
                });
    }

    /**
     * 保存Bitmap至本地
     *
     * @param
     */
    private void saveBitmapToFile(Bitmap bmp) {
        String file_path = getImagePath();
        File file = new File(file_path);
        FileOutputStream fOut;
        try {
            fOut = new FileOutputStream(file);
            bmp.compress(Bitmap.CompressFormat.JPEG, 85, fOut);
            fOut.flush();
            fOut.close();
        } catch (FileNotFoundException e) {
            e.printStackTrace();
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    /**
     * 读取图片属性：旋转的角度
     *
     * @param path 图片绝对路径
     * @return degree 旋转的角度
     */
    public static int readPictureDegree(String path) {
        int degree = 0;
        try {
            ExifInterface exifInterface = new ExifInterface(path);
            int orientation = exifInterface.getAttributeInt(
                    ExifInterface.TAG_ORIENTATION,
                    ExifInterface.ORIENTATION_NORMAL);
            switch (orientation) {
                case ExifInterface.ORIENTATION_ROTATE_90:
                    degree = 90;
                    break;
                case ExifInterface.ORIENTATION_ROTATE_180:
                    degree = 180;
                    break;
                case ExifInterface.ORIENTATION_ROTATE_270:
                    degree = 270;
                    break;
            }
        } catch (IOException e) {
            e.printStackTrace();
        }
        return degree;
    }

    /**
     * 旋转图片
     *
     * @param angle
     * @param bitmap
     * @return Bitmap
     */
    public static Bitmap rotateImage(int angle, Bitmap bitmap) {
        // 图片旋转矩阵
        Matrix matrix = new Matrix();
        matrix.postRotate(angle);
        // 得到旋转后的图片
        Bitmap resizedBitmap = Bitmap.createBitmap(bitmap, 0, 0,
                bitmap.getWidth(), bitmap.getHeight(), matrix, true);
        return resizedBitmap;
    }

    private String userIdToAuthId(int userId) {
        return "a" + userId;
    }

    private int AuthIdToUserId(String authId) {
        return Integer.parseInt(authId.substring(1));
    }

    private void identifyResult(IdentityResult result) {
        String resultStr = result.getResultString();
        try {
            JSONObject resultJson = new JSONObject(resultStr);
            if (ErrorCode.SUCCESS == resultJson.getInt("ret")) {
                JSONObject candidateOne = resultJson.getJSONObject("ifv_result").getJSONArray("candidates").getJSONObject(0);
                String authId = candidateOne.getString("user");
                int userId = AuthIdToUserId(authId);
                String userName = MyApp.getDBManage(this).queryUserName(userId);
                if (userName == null) {
                    ToastShow.showTip(mToast, "数据库无此用户");
                } else {
                    ToastShow.showTip(mToast, "你好" + userName);
                }
            } else {
                ToastShow.showTip(mToast, "对不起，鉴别失败");
            }
        } catch (JSONException e) {
            e.printStackTrace();
        }
    }

    private Bitmap flipBitmap(Bitmap bmp) {
        int width = bmp.getWidth();
        int height = bmp.getHeight();

        Matrix matrix = new Matrix();
        matrix.postScale(-1, 1);
        Bitmap flip = Bitmap.createBitmap(bmp, 0, 0, width, height, matrix, true);
        return flip;
    }
}
