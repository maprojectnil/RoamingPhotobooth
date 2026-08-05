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
package com.extremesolution.esptpcamera.model;

import java.util.Locale;

import com.extremesolution.esptpcamera.PacketUtil;
import com.extremesolution.esptpcamera.PtpConstants;

import java.nio.ByteBuffer;

/**
 * Object info data set as defined by the PTP standard.
 */
public class ObjectInfo {
    public int associationType;
    public String captureDate;
    public String filename;
    public int imageBitDepth;
    public int imagePixHeight;
    public int imagePixWidth;
    public int keywords;
    public String modificationDate;
    public int objectCompressedSize;
    public int objectFormat;
    public int orientation;
    public int parentObject;
    public int protectionStatus;
    public int sequenceNumber;
    public int storageId;
    public int thumbCompressedSize;
    public int thumbFormat;
    public int thumbPixHeight;
    public int thumbPixWidth;
    private int associationDesc;

    public ObjectInfo(ByteBuffer b, int length) {
        decode(b, length);
    }

    public void decode(ByteBuffer b, int length) {
        this.storageId = b.getInt();
        this.objectFormat = b.getShort();
        this.protectionStatus = b.getShort();
        this.objectCompressedSize = b.getInt();
        this.thumbFormat = b.getShort();
        this.thumbCompressedSize = b.getInt();
        this.thumbPixWidth = b.getInt();
        this.thumbPixHeight = b.getInt();
        this.imagePixWidth = b.getInt();
        this.imagePixHeight = b.getInt();
        this.imageBitDepth = b.getInt();
        this.parentObject = b.getInt();
        this.associationType = b.getShort();
        this.associationDesc = b.getInt();
        this.sequenceNumber = b.getInt();
        this.filename = PacketUtil.readString(b);
        this.captureDate = PacketUtil.readString(b);
        this.modificationDate = PacketUtil.readString(b);
        this.keywords = b.get();
    }

    public String toString() {
        StringBuilder b = new StringBuilder();
        b.append("ObjectInfo\n");
        b.append("StorageId: ").append(String.format(Locale.US, "0x%08x\n", Integer.valueOf(this.storageId)));
        b.append("ObjectFormat: ").append(PtpConstants.objectFormatToString(this.objectFormat)).append(10);
        b.append("ProtectionStatus: ").append(this.protectionStatus).append(10);
        b.append("ObjectCompressedSize: ").append(this.objectCompressedSize).append(10);
        b.append("ThumbFormat: ").append(PtpConstants.objectFormatToString(this.thumbFormat)).append(10);
        b.append("ThumbCompressedSize: ").append(this.thumbCompressedSize).append(10);
        b.append("ThumbPixWdith: ").append(this.thumbPixWidth).append(10);
        b.append("ThumbPixHeight: ").append(this.thumbPixHeight).append(10);
        b.append("ImagePixWidth: ").append(this.imagePixWidth).append(10);
        b.append("ImagePixHeight: ").append(this.imagePixHeight).append(10);
        b.append("ImageBitDepth: ").append(this.imageBitDepth).append(10);
        b.append("ParentObject: ").append(String.format("0x%08x", Integer.valueOf(this.parentObject))).append(10);
        b.append("AssociationType: ").append(this.associationType).append(10);
        b.append("AssociatonDesc: ").append(this.associationDesc).append(10);
        b.append("Filename: ").append(this.filename).append(10);
        b.append("CaptureDate: ").append(this.captureDate).append(10);
        b.append("ModificationDate: ").append(this.modificationDate).append(10);
        b.append("Keywords: ").append(this.keywords).append(10);
        return b.toString();
    }
}
