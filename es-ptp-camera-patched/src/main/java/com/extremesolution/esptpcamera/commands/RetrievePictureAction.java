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

import com.extremesolution.esptpcamera.PtpAction;
import com.extremesolution.esptpcamera.PtpCamera;
import com.extremesolution.esptpcamera.PtpCamera.IO;
import com.extremesolution.esptpcamera.PtpConstants;
import com.extremesolution.esptpcamera.PtpConstants.Response;

import java.util.Locale;
import com.extremesolution.esptpcamera.model.ObjectInfo;
import com.extremesolution.esptpcamera.ImageUtil;

public class RetrievePictureAction implements PtpAction {

    private final PtpCamera camera;
    private final int objectHandle;
    private final int sampleSize;

    public RetrievePictureAction(PtpCamera camera, int objectHandle, int sampleSize) {
        this.camera = camera;
        this.objectHandle = objectHandle;
        this.sampleSize = sampleSize;
    }

    @Override
    public void exec(IO io) {
        GetObjectInfoCommand getInfo = new GetObjectInfoCommand(camera, objectHandle);
        io.handleCommand(getInfo);

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

        boolean isRawImage = ImageUtil.isRawImage(objectInfo.filename);
        if (isRawImage) {
            camera.onPictureRetrievalFailed(objectHandle, "RAW image format not supported: " + objectInfo.filename);
            return;
        }

        Bitmap thumbnail = null;
        if (objectInfo.thumbFormat == PtpConstants.ObjectFormat.JFIF
                || objectInfo.thumbFormat == PtpConstants.ObjectFormat.EXIF_JPEG) {
            GetThumb getThumb = new GetThumb(camera, objectHandle);
            io.handleCommand(getThumb);
            if (getThumb.getResponseCode() == Response.Ok) {
                thumbnail = getThumb.getBitmap();
            }
        }

        GetObjectCommand getObject = new GetObjectCommand(camera, objectHandle, sampleSize);
        io.handleCommand(getObject);

        if (getObject.getResponseCode() != Response.Ok) {
            camera.onPictureRetrievalFailed(objectHandle, 
                    String.format(Locale.US, "GetObject failed: %s", 
                            PtpConstants.responseToString(getObject.getResponseCode())));
            return;
        }
        if (getObject.getBitmap() == null) {
            if (getObject.isOutOfMemoryError()) {
                camera.onPictureRetrievalFailed(objectHandle, "Out of memory decoding image");
            } else {
                camera.onPictureRetrievalFailed(objectHandle, "Failed to decode image bitmap");
            }
            return;
        }


        GetObjectExifCommand getObjectExif = new GetObjectExifCommand(this.camera, this.objectHandle);
        io.handleCommand(getObjectExif);
        if (getObjectExif.getResponseCode() != 8193) {
            getObjectExif.objectOrientation = -1;
        }
        objectInfo.orientation = getObjectExif.objectOrientation;

        int orientation = 0;

        switch (objectInfo.orientation) {
            case 1:
                orientation = 0;
                break;
            case 3:
                orientation = 180;
                break;

            case 6:
                orientation = 90;
                break;

            case 8:
                orientation = 270;
        }
        camera.onPictureReceived(objectHandle, getInfo.getObjectInfo().filename, thumbnail, getObject.getBitmap(), orientation);

    }

    @Override
    public void reset() {
    }
}
