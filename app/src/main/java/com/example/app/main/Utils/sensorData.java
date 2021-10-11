package com.example.app.main.Utils;

import android.content.Context;
import android.hardware.Sensor;
import android.hardware.SensorEvent;
import android.hardware.SensorEventListener;
import android.hardware.SensorManager;

public class sensorData implements SensorEventListener {

    // Sensors Settings
    public SensorManager sensorManager;
    public Sensor mSensorMagneticField;
    public Sensor mSensorAccelerometer;
    public Sensor mSensorGyroscope;
    public Sensor mSensorRotationVector;
    public Sensor mSensorGravity;
    public Sensor mSensorLinearAcceleration;
    public Sensor mSensorProximity;

    // Sensors variables
    public float[] mag = new float[3];
    public float[] acc = new float[3];
    public float[] gyro = new float[3];
    public float[] rotv = new float[4];
    public float[] grav = new float[3];
    public float[] line = new float[3];
    public double prox = 0.0;
    public double[] angle = new double[3];

    public String data = "";


    public sensorData( Context context) {
        sensorManager = (SensorManager) context.getSystemService(Context.SENSOR_SERVICE);

        mSensorMagneticField = sensorManager.getDefaultSensor(Sensor.TYPE_MAGNETIC_FIELD);
        mSensorAccelerometer = sensorManager.getDefaultSensor(Sensor.TYPE_ACCELEROMETER);
        mSensorGyroscope = sensorManager.getDefaultSensor(Sensor.TYPE_GYROSCOPE);
        mSensorRotationVector = sensorManager.getDefaultSensor(Sensor.TYPE_ROTATION_VECTOR);
        mSensorGravity = sensorManager.getDefaultSensor(Sensor.TYPE_GRAVITY);
        mSensorLinearAcceleration = sensorManager.getDefaultSensor(Sensor.TYPE_LINEAR_ACCELERATION);
        mSensorProximity = sensorManager.getDefaultSensor(Sensor.TYPE_PROXIMITY);
    }

    public void onResume(){
        sensorManager.registerListener(this, mSensorMagneticField, SensorManager.SENSOR_DELAY_NORMAL);
        sensorManager.registerListener(this, mSensorAccelerometer, SensorManager.SENSOR_DELAY_NORMAL);
        sensorManager.registerListener(this, mSensorGyroscope, SensorManager.SENSOR_DELAY_NORMAL);
        sensorManager.registerListener(this, mSensorRotationVector, SensorManager.SENSOR_DELAY_NORMAL);
        sensorManager.registerListener(this, mSensorGravity, SensorManager.SENSOR_DELAY_NORMAL);
        sensorManager.registerListener(this, mSensorLinearAcceleration, SensorManager.SENSOR_DELAY_NORMAL);
        sensorManager.registerListener(this, mSensorProximity, SensorManager.SENSOR_DELAY_NORMAL);
    }

    public void onPause(){
        sensorManager.unregisterListener((SensorEventListener) this);
    }

    @Override
    public void onSensorChanged(SensorEvent sensorEvent) {
        Sensor sensor = sensorEvent.sensor;
        switch (sensor.getType()) {
            case (Sensor.TYPE_MAGNETIC_FIELD):
                mag = sensorEvent.values;
                computeOrientation();
                break;
            case (Sensor.TYPE_ACCELEROMETER):
                acc = sensorEvent.values;
                computeOrientation();
                break;
            case (Sensor.TYPE_GYROSCOPE):
                gyro = sensorEvent.values;
                break;
            case (Sensor.TYPE_ROTATION_VECTOR):
                rotv = sensorEvent.values;
                break;
            case (Sensor.TYPE_GRAVITY):
                grav = sensorEvent.values;
                break;
            case (Sensor.TYPE_LINEAR_ACCELERATION):
                line = sensorEvent.values;
                break;
            case (Sensor.TYPE_PROXIMITY):
                prox = Double.valueOf(sensorEvent.values[0]);
                break;
            default:
                break;
        }
        data = String.format("M %f %f %f A %f %f %f G %f %f %f R %f %f %f %f g %f %f %f L %f %f %f P %f D %f %f %fE",
                mag[0],mag[1],mag[2],acc[0],acc[1],acc[2],gyro[0],gyro[1],gyro[2],rotv[0],rotv[1], rotv[2],rotv[3],
                grav[0],grav[1],grav[2],line[0],line[1],line[2],prox,angle[0],angle[1],angle[2]);
    }

    @Override
    public void onAccuracyChanged(Sensor sensor, int i) {    }


    // function to compute Orientation(pitch,roll,yaw) using accelerometer and magnetic field
    public void computeOrientation() {

        float[] rotationMatrix = new float[9];
        SensorManager.getRotationMatrix(rotationMatrix, null, acc, mag);

        float[] orientationAngles = new float[3];
        float[] radian = SensorManager.getOrientation(rotationMatrix, orientationAngles);

        // Convert angles from radians to degree
        angle[0] = radian[0] * 180 / 3.14;
        angle[1] = radian[1] * 180 / 3.14;
        angle[2] = radian[2] * 180 / 3.14;
    }

}
