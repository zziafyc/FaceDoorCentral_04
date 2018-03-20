package com.example.aicsoft;

import android.app.Activity;
import android.util.Base64;
import android.util.Log;

import com.arcsoft.facerecognition.AFR_FSDKEngine;
import com.arcsoft.facerecognition.AFR_FSDKError;
import com.arcsoft.facerecognition.AFR_FSDKFace;
import com.arcsoft.facerecognition.AFR_FSDKVersion;
import com.example.aicsoft.model.FaceRegist;
import com.example.facedoor.db.DBUtil;
import com.example.facedoor.model.User;
import com.example.facedoor.util.StringUtils;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.List;

/**
 * Created by fyc on 2018/3/16.
 */

public class FaceManagerUtils implements Serializable {
    private final String TAG = this.getClass().toString();
    public static String appid = "9qDtYPA7pTbPgyNF7r75VR64RjbEoCL6nog2hgTUkjmS";
    public static String ft_key = "Gk9iHEuAVrtipvT1CbLDAHWcyUMKCgoxk29TVmBpvLNb";
    public static String fd_key = "Gk9iHEuAVrtipvT1CbLDAHWk8scWr7tHbfgUxn32KCzB";
    public static String fr_key = "Gk9iHEuAVrtipvT1CbLDAHX7d5Q18oszHPY7sdem1Ypw";
    public static String age_key = "Gk9iHEuAVrtipvT1CbLDAHXV7HBU5h9PBcV9Jo67ajyc";
    public static String gender_key = "Gk9iHEuAVrtipvT1CbLDAHXcGgSg1KSg9THE86wQqVy7";
    public FaceManagerUtils instance;
    List<FaceRegist> mRegister;
    AFR_FSDKEngine mFREngine;
    AFR_FSDKVersion mFRVersion;
    boolean mUpgrade;

    public FaceManagerUtils() {
        mRegister = new ArrayList<>();
        mFRVersion = new AFR_FSDKVersion();
        mUpgrade = false;
        mFREngine = new AFR_FSDKEngine();
        AFR_FSDKError error = mFREngine.AFR_FSDK_InitialEngine(appid, fr_key);
        if (error.getCode() != AFR_FSDKError.MOK) {
            Log.e(TAG, "AFR_FSDK_InitialEngine fail! error code :" + error.getCode());
        } else {
            mFREngine.AFR_FSDK_GetVersion(mFRVersion);
            Log.d(TAG, "AFR_FSDK_GetVersion=" + mFRVersion.toString());
        }
    }

    public FaceManagerUtils getInstance() {
        if (instance == null) {
            return new FaceManagerUtils();
        }
        return instance;
    }

    //获得每一个组下的所有注册的成员
    public List<FaceRegist> getAllFaceByGroup(String groupId, Activity activity) {
        DBUtil dbUtil = new DBUtil(activity);
        List<User> users = dbUtil.queryAllUserByGroup(groupId);
        if (users != null && users.size() > 0) {
            for (User user : users) {
                List<AFR_FSDKFace> mFaceList = new ArrayList<>();
                if (!StringUtils.isEmpty(user.getFaceInfo())){
                    mFaceList.add(new AFR_FSDKFace(Base64.decode(user.getFaceInfo(), Base64.DEFAULT)));
                }
                mRegister.add(new FaceRegist(user, mFaceList));
            }
        }
        return mRegister;
    }
}
