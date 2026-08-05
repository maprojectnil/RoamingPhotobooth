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

import com.extremesolution.esptpcamera.PacketUtil;
import com.extremesolution.esptpcamera.PtpCamera;
import com.extremesolution.esptpcamera.PtpCamera.IO;
import com.extremesolution.esptpcamera.PtpConstants;

import java.nio.ByteBuffer;

public class GetStorageIdsCommand extends Command {

    private int[] storageIds;

    public GetStorageIdsCommand(PtpCamera camera) {
        super(camera);
    }

    public int[] getStorageIds() {
        if (storageIds == null) {
            return new int[0];
        }
        return storageIds;
    }

    @Override
    public void exec(IO io) {
        io.handleCommand(this);
    }

    @Override
    public void encodeCommand(ByteBuffer b) {
        super.encodeCommand(b, PtpConstants.Operation.GetStorageIDs);
    }

    @Override
    protected void decodeData(ByteBuffer b, int length) {
        storageIds = PacketUtil.readU32Array(b);
    }
}
