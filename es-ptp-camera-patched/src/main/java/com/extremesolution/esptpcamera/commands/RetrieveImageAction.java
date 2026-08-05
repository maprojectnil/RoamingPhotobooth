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

import com.extremesolution.esptpcamera.Camera.RetrieveImageListener;
import com.extremesolution.esptpcamera.PtpAction;
import com.extremesolution.esptpcamera.PtpCamera;
import com.extremesolution.esptpcamera.PtpCamera.IO;
import com.extremesolution.esptpcamera.PtpConstants.Response;

public class RetrieveImageAction implements PtpAction {

    private final PtpCamera camera;
    private final int objectHandle;
    private final RetrieveImageListener listener;
    private final int sampleSize;

    public RetrieveImageAction(PtpCamera camera, RetrieveImageListener listener, int objectHandle, int sampleSize) {
        this.camera = camera;
        this.listener = listener;
        this.objectHandle = objectHandle;
        this.sampleSize = sampleSize;
    }

    @Override
    public void exec(IO io) {
        GetObjectCommand getObject = new GetObjectCommand(camera, objectHandle, sampleSize);
        io.handleCommand(getObject);
        GetObjectExifCommand getObjectExif = new GetObjectExifCommand(this.camera, this.objectHandle);
        io.handleCommand(getObjectExif);

        if (getObject.getResponseCode() != Response.Ok || getObject.getBitmap() == null) {
            listener.onImageRetrieved(0, null, getObjectExif.objectOrientation);
            return;
        }

        listener.onImageRetrieved(objectHandle, getObject.getBitmap(), getObjectExif.objectOrientation);
    }

    @Override
    public void reset() {
    }
}
