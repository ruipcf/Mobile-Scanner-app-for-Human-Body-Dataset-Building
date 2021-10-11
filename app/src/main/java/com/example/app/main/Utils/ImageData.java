package com.example.app.main.Utils;

import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.media.Image;
import android.util.Log;

import java.io.ByteArrayOutputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.net.Socket;
import java.net.UnknownHostException;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;

public class ImageData {

    // function to send depth image and sensor values
    static public void sendDepthSensor(String host, int port, Image depthImage, String dataSensor) {
        // connection
        try {
            Socket clientSocket = new Socket(host, port);
            final DataOutputStream ToServer = new DataOutputStream(clientSocket.getOutputStream());

            // send width and height first
            ToServer.writeShort(depthImage.getWidth());
            ToServer.writeShort(depthImage.getHeight());

            for (int j = 0; j < depthImage.getHeight(); j++) {
                for (int i = 0; i < depthImage.getWidth(); i++) {
                    short aux = (short) (getMillimetersDepth(depthImage, i, j));
                    ToServer.writeShort(aux);
                }
            }
            depthImage.close();

            // send the data from sensors
            ToServer.writeBytes(dataSensor);

            ToServer.close();
            clientSocket.close();
        } catch (UnknownHostException e) {
            e.printStackTrace();
        } catch (IOException e) {
            e.printStackTrace();
        }
    }


    // function to convert the value of a single pixel in a value representing the distance from camera to the object
    static public int getMillimetersDepth(Image depthImage, int x, int y) {
        // The depth image has a single plane, which stores depth for each
        // pixel as 16-bit unsigned integers.
        Image.Plane plane = depthImage.getPlanes()[0];
        int byteIndex = x * plane.getPixelStride() + y * plane.getRowStride();
        ByteBuffer buffer = plane.getBuffer().order(ByteOrder.nativeOrder());
        short depthSample = buffer.getShort(byteIndex);
        // Only the lowest 13 bits are used to represent depth in millimeters.
        return (depthSample & 0x1FFF);
    }


    // function to convert an Image into a Bitmap
    static public Bitmap convertImageProxyToBitmap(Image image) {
        ByteBuffer byteBuffer = image.getPlanes()[0].getBuffer();
        byteBuffer.rewind();
        byte[] bytes = new byte[byteBuffer.capacity()];
        byteBuffer.get(bytes);
        byte[] clonedBytes = bytes.clone();
        return BitmapFactory.decodeByteArray(clonedBytes, 0, clonedBytes.length);
    }


    // function just for debug to send only an Image converting a bitmap on an encoded image
    static public void sendImage(String host, int port, Bitmap bitmap){

        // convert bitmap into PNG image
        ByteArrayOutputStream stream = new ByteArrayOutputStream();
        bitmap.compress(Bitmap.CompressFormat.PNG, 100, stream);
        bitmap.recycle();
        byte[] byteArray = stream.toByteArray();

        // just for debug print the length of the array byte
        Log.e("tagee", String.valueOf(byteArray.length));

        // connection
        try {
            Socket clientSocket = new Socket(host, port);
            final DataOutputStream ToServer = new DataOutputStream(clientSocket.getOutputStream());

            // send the length of de data first and then send the data
            ToServer.writeInt(byteArray.length);
            ToServer.write(byteArray);

            ToServer.close();
            clientSocket.close();
        } catch (UnknownHostException e) {
            e.printStackTrace();
        } catch (IOException e) {
            e.printStackTrace();
        }
    }


    // function to send only the image preview
    static public void sendImagePreview(String host, int port, byte[] data){
        // connection
        try {
            Socket clientSocket = new Socket(host, port);
            final DataOutputStream ToServer = new DataOutputStream(clientSocket.getOutputStream());

            // send the length of de data first and then send the data
            ToServer.writeInt(data.length);
            ToServer.write(data);

            // just for debug print the length of the array data
            Log.e("tageee", String.valueOf(data.length));

            ToServer.close();
            clientSocket.close();
        } catch (UnknownHostException e) {
            e.printStackTrace();
        } catch (IOException e) {
            e.printStackTrace();
        }
    }


    // function to send depth image, rgb image and sensor data
    static public void sendAll(String host, int port,byte[] data, Image depthImage, String dataSensor) {
        // connection
        try {
            Socket clientSocket = new Socket(host, port);
            final DataOutputStream ToServer = new DataOutputStream(clientSocket.getOutputStream());

            // send width and height first
            ToServer.writeShort(depthImage.getWidth());
            ToServer.writeShort(depthImage.getHeight());

            for (int j = 0; j < depthImage.getHeight(); j++) {
                for (int i = 0; i < depthImage.getWidth(); i++) {
                    short aux = (short) (ImageData.getMillimetersDepth(depthImage, i, j));
                    ToServer.writeShort(aux);
                }
            }
            depthImage.close();

            // send the data from sensors
            ToServer.writeBytes(dataSensor);

            // send the length of de data first and then send the data
            Log.e("tageee", String.valueOf(data.length));
            ToServer.writeInt(data.length);
            ToServer.write(data);

            ToServer.close();
            clientSocket.close();
        } catch (UnknownHostException e) {
            e.printStackTrace();
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

}
