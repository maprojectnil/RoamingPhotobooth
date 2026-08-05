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

import com.extremesolution.esptpcamera.PtpCamera;
import com.extremesolution.esptpcamera.PtpCamera.IO;
import com.extremesolution.esptpcamera.PtpConstants;
import com.extremesolution.esptpcamera.model.StorageInfo;

import java.nio.ByteBuffer;

public class GetStorageInfoCommand extends Command {

    private final int storageId;
    private StorageInfo storageInfo;

    public GetStorageInfoCommand(PtpCamera camera, int storageId) {
        super(camera);
        this.storageId = storageId;
    }

    public StorageInfo getStorageInfo() {
        return storageInfo;
    }

    @Override
    public void exec(IO io) {
        io.handleCommand(this);
    }

    @Override
    public void encodeCommand(ByteBuffer b) {
        super.encodeCommand(b, PtpConstants.Operation.GetStorageInfo, storageId);
    }

    @Override
    protected void decodeData(ByteBuffer b, int length) {
        storageInfo = new StorageInfo(b, length);
    }
}
