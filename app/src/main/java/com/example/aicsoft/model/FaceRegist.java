package com.example.aicsoft.model;

import com.arcsoft.facerecognition.AFR_FSDKFace;
import com.example.facedoor.model.User;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.List;

/**
 * Created by fyc on 2018/3/16.
 */

public class FaceRegist implements Serializable {
    User mUser;
    List<AFR_FSDKFace> mFaceList;

    public FaceRegist(User user) {
        mUser = user;
        mFaceList = new ArrayList<>();
    }

    public FaceRegist(User user, List<AFR_FSDKFace> faceList) {
        mUser = user;
        mFaceList = faceList;
    }

    public User getUser() {
        return mUser;
    }

    public void setUser(User user) {
        mUser = user;
    }

    public List<AFR_FSDKFace> getFaceList() {
        return mFaceList;
    }

    public void setFaceList(List<AFR_FSDKFace> faceList) {
        mFaceList = faceList;
    }
}

