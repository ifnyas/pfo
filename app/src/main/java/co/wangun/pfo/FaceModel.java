package co.wangun.pfo;

import android.graphics.PointF;

public class FaceModel {

    PointF midEye;
    float eyeDist;
    float confidence;
    float pose;
    int id;
    long time;

    public FaceModel() {
        id = 0;
        midEye = new PointF(0.0f, 0.0f);
        eyeDist = 0.0f;
        confidence = 0.4f;
        pose = 0.0f;
        time = System.currentTimeMillis();
    }


    public void setFace(int id, PointF midEye, float eyeDist, float confidence, float pose, long time) {
        set(id, midEye, eyeDist, confidence, pose, time);
    }

    public void clear() {
        set(0, new PointF(0.0f, 0.0f), 0.0f, 0.4f, 0.0f, System.currentTimeMillis());
    }

    public synchronized void set(int id, PointF midEye, float eyeDist, float confidence, float pose, long time) {
        this.id = id;
        this.midEye.set(midEye);
        this.eyeDist = eyeDist;
        this.confidence = confidence;
        this.pose = pose;
        this.time = time;
    }

    public float eyesDistance() {
        return eyeDist;
    }

    public void setEyeDist(float eyeDist) {
        this.eyeDist = eyeDist;
    }

    public void getMidPoint(PointF pt) {
        pt.set(midEye);
    }

    public PointF getMidEye() {
        return midEye;
    }

    public void setMidEye(PointF midEye) {
        this.midEye = midEye;
    }

    public float getConfidence() {
        return confidence;
    }

    public void setConfidence(float confidence) {
        this.confidence = confidence;
    }

    public float getPose() {
        return pose;
    }

    public void setPose(float pose) {
        this.pose = pose;
    }

    public int getId() {
        return id;
    }

    public void setId(int id) {
        this.id = id;
    }

    public long getTime() {
        return time;
    }

    public void setTime(long time) {
        this.time = time;
    }
}
