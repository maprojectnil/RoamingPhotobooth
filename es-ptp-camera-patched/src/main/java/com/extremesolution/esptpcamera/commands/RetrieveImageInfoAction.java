/**
 * Copyright 2013 Nils Assbeck, Guersel Ayaz and Michael Zoech
 * <p>
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 * <p>
 * http://www.apache.org/licenses/LICENSE-2.0
 * <p>
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.extremesolution.esptpcamera.commands;

import android.graphics.Bitmap;
import android.util.Log;

import com.extremesolution.esptpcamera.Camera.RetrieveImageInfoListener;
import com.extremesolution.esptpcamera.PtpAction;
import com.extremesolution.esptpcamera.PtpCamera;
import com.extremesolution.esptpcamera.PtpCamera.IO;
import com.extremesolution.esptpcamera.PtpConstants;
import com.extremesolution.esptpcamera.PtpConstants.Response;

import java.util.Locale;
import com.extremesolution.esptpcamera.model.ObjectInfo;

public class RetrieveImageInfoAction implements PtpAction {

    private final PtpCamera camera;
    private final int objectHandle;
    private final RetrieveImageInfoListener listener;

    public RetrieveImageInfoAction(PtpCamera camera, RetrieveImageInfoListener listener, int objectHandle) {
        this.camera = camera;
        this.listener = listener;
        this.objectHandle = objectHandle;
    }

    @Override
    public void exec(IO io) {
        long start = System.currentTimeMillis();
        Log.i("RetrieveImageInfo", "exec START handle=" + objectHandle);

        GetObjectInfoCommand getInfo = new GetObjectInfoCommand(camera, objectHandle);
        io.handleCommand(getInfo);
        Log.i("RetrieveImageInfo", "GetObjectInfo took " + (System.currentTimeMillis() - start) + "ms");

        if (getInfo.getResponseCode() != Response.Ok) {
            camera.onPictureRetrievalFailed(objectHandle, 
                    String.format(Locale.US, "GetObjectInfo failed: %s", 
                            PtpConstants.responseToString(getInfo.getResponseCode())));
            return;
        }

        ObjectInfo objectInfo = getInfo.getObjectInfo();
        if (objectInfo == null) {
            camera.onPictureRetrievalFailed(objectHandle, "ObjectInfo is null");
            return;
        }

        Bitmap thumbnail = null;
        if (objectInfo.thumbFormat == PtpConstants.ObjectFormat.JFIF
                || objectInfo.thumbFormat == PtpConstants.ObjectFormat.EXIF_JPEG) {
            long thumbStart = System.currentTimeMillis();
            GetThumb getThumb = new GetThumb(camera, objectHandle);
            io.handleCommand(getThumb);
            Log.i("RetrieveImageInfo", "GetThumb took " + (System.currentTimeMillis() - thumbStart) + "ms");
            if (getThumb.getResponseCode() == Response.Ok) {
                thumbnail = getThumb.getBitmap();
            }
        }
        long exifStart = System.currentTimeMillis();
        GetObjectExifCommand getObjectExif = new GetObjectExifCommand(this.camera, this.objectHandle);
        io.handleCommand(getObjectExif);
        Log.i("RetrieveImageInfo", "GetExif took " + (System.currentTimeMillis() - exifStart) + "ms");
        if (getObjectExif.getResponseCode() != 8193) {
            getObjectExif.objectOrientation = -1;
        }
        objectInfo.orientation = getObjectExif.objectOrientation;

        Log.i("RetrieveImageInfo", "exec TOTAL took " + (System.currentTimeMillis() - start) + "ms, calling listener");
        listener.onImageInfoRetrieved(objectHandle, getInfo.getObjectInfo(), thumbnail);
    }

    @Override
    public void reset() {
    }
}
