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
package com.extremesolution.esptpcamera;

import android.graphics.Bitmap;
import android.hardware.usb.UsbRequest;
import android.os.Handler;
import android.util.Log;

import com.extremesolution.esptpcamera.AppConfig;
import com.extremesolution.esptpcamera.commands.CloseSessionCommand;
import com.extremesolution.esptpcamera.commands.Command;
import com.extremesolution.esptpcamera.commands.GetDeviceInfoCommand;
import com.extremesolution.esptpcamera.commands.GetDevicePropValueCommand;
import com.extremesolution.esptpcamera.commands.GetObjectHandlesCommand;
import com.extremesolution.esptpcamera.commands.GetStorageInfosAction;
import com.extremesolution.esptpcamera.commands.InitiateCaptureCommand;
import com.extremesolution.esptpcamera.commands.OpenSessionCommand;
import com.extremesolution.esptpcamera.commands.RetrieveImageAction;
import com.extremesolution.esptpcamera.commands.RetrieveImageInfoAction;
import com.extremesolution.esptpcamera.commands.RetrievePictureAction;
import com.extremesolution.esptpcamera.commands.SetDevicePropValueCommand;
import com.extremesolution.esptpcamera.model.DeviceInfo;
import com.extremesolution.esptpcamera.model.DevicePropDesc;
import com.extremesolution.esptpcamera.model.LiveViewData;

import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.Locale;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;

public abstract class PtpCamera implements Camera {

    private static final String TAG = PtpCamera.class.getSimpleName();
    protected final Handler handler = new Handler();
    protected final LinkedBlockingQueue<PtpAction> queue = new LinkedBlockingQueue<PtpAction>();
    protected final Map<Integer, Integer> virtualToPtpProperty = new HashMap<Integer, Integer>();
    protected final Map<Integer, Integer> ptpToVirtualProperty = new HashMap<Integer, Integer>();
    // current property values and descriptions
    protected final Map<Integer, DevicePropDesc> ptpPropertyDesc = new HashMap<Integer, DevicePropDesc>();
    protected final Map<Integer, Integer> ptpProperties = new HashMap<Integer, Integer>();
    protected final Map<Integer, Integer> properties = new HashMap<Integer, Integer>();
    protected final Set<Integer> ptpInternalProperties = new HashSet<Integer>();
    protected final int productId;
    private final WorkerThread workerThread = new WorkerThread();
    private final PtpUsbConnection connection;
    private final Map<Integer, int[]> propertyDescriptions = new HashMap<Integer, int[]>();
    private final int vendorId;
    protected CameraListener listener;
    protected State state;
    protected DeviceInfo deviceInfo;
    protected boolean histogramSupported;
    protected boolean liveViewSupported;
    protected boolean liveViewAfAreaSupported;
    protected boolean liveViewOpen;
    protected boolean bulbSupported;
    protected boolean driveLensSupported;
    protected boolean autoFocusSupported;
    protected boolean cameraIsCapturing;
    private int transactionId;
    private int pictureSampleSize;

    public PtpCamera(PtpUsbConnection connection, CameraListener listener) {
        this.connection = connection;
        this.listener = listener;
        this.pictureSampleSize = 100;
        state = State.Starting;
        vendorId = connection.getVendorId();
        productId = connection.getProductId();
        queue.add(new GetDeviceInfoCommand(this));
        openSession();
        workerThread.start();
        if (AppConfig.LOG) {
            Log.i(TAG, String.format(Locale.US, "Starting session for %04x %04x", vendorId, productId));
        }
    }

    protected void addPropertyMapping(int virtual, int ptp) {
        ptpToVirtualProperty.put(ptp, virtual);
        virtualToPtpProperty.put(virtual, ptp);
    }

    protected void addInternalProperty(int ptp) {
        ptpInternalProperties.add(ptp);
    }

    public void setListener(CameraListener listener) {
        this.listener = listener;
    }

    public void shutdown() {
        state = State.Stoping;
        workerThread.lastEventCheck = System.currentTimeMillis() + 1000000L;
        queue.clear();
        if (liveViewOpen) {
            //TODO
            setLiveView(false);
        }
        closeSession();
    }

    public void shutdownHard() {
        state = State.Stopped;
        synchronized (workerThread) {
            workerThread.stop = true;
        }
        if (connection != null) {
            connection.close();
            //TODO possible NPE, need to join workerThread
            //connection = null;
        }
    }

    public State getState() {
        return state;
    }

    public int nextTransactionId() {
        return transactionId++;
    }

    public int currentTransactionId() {
        return transactionId;
    }

    public void resetTransactionId() {
        transactionId = 0;
    }

    public int getProductId() {
        return productId;
    }

    public void enqueue(final Command cmd, int delay) {
        handler.postDelayed(new Runnable() {
            @Override
            public void run() {
                if (state == State.Active) {
                    queue.add(cmd);
                }
            }
        }, delay);
    }

    /**
     * Deriving classes should override this method to get the set of supported
     * operations of the camera. Based on this information functionality has to
     * be enabled/disabled.
     */
    protected abstract void onOperationCodesReceived(Set<Integer> operations);

    public int getPtpProperty(int property) {
        Integer value = ptpProperties.get(property);
        return value != null ? value : 0;
    }

    public void onSessionOpened() {
        state = State.Active;
        handler.post(new Runnable() {
            @Override
            public void run() {
                if (listener != null) {
                    listener.onCameraStarted(PtpCamera.this);
                }
            }
        });
    }

    public void onSessionClosed() {
        shutdownHard();
        handler.post(new Runnable() {
            @Override
            public void run() {
                if (listener != null) {
                    listener.onCameraStopped(PtpCamera.this);
                }
            }
        });
    }

    public void onPropertyChanged(int property, final int value) {
        Log.i(TAG, "p " + property + " " + value);
        ptpProperties.put(property, value);
        final Integer virtual = ptpToVirtualProperty.get(property);
        if (AppConfig.LOG) {
            Log.d(TAG, String.format(Locale.US, "onPropertyChanged %s %s(%d)", PtpConstants.propertyToString(property),
                    virtual != null ? propertyToString(virtual, value) : "", value));
        }
        if (virtual != null) {
            handler.post(new Runnable() {
                @Override
                public void run() {
                    properties.put(virtual, value);
                    if (listener != null) {
                        listener.onPropertyChanged(virtual, value);
                    }
                }
            });
        }
    }

    public void onPropertyDescChanged(int property, final int[] values) {
        //if (BuildConfig.LOG) {
        Log.d(TAG,
                String.format(Locale.US, "onPropertyDescChanged %s:\n%s", PtpConstants.propertyToString(property),
                        Arrays.toString(values)));
        //}
        final Integer virtual = ptpToVirtualProperty.get(property);
        if (virtual != null) {
            handler.post(new Runnable() {
                @Override
                public void run() {
                    propertyDescriptions.put(virtual, values);
                    if (listener != null) {
                        listener.onPropertyDescChanged(virtual, values);
                    }
                }
            });
        }
    }

    public void onPropertyDescChanged(int property, DevicePropDesc desc) {
        ptpPropertyDesc.put(property, desc);
        onPropertyDescChanged(property, desc.description);
    }

    public void onLiveViewStarted() {
        liveViewOpen = true;
        handler.post(new Runnable() {
            @Override
            public void run() {
                if (listener != null) {
                    listener.onLiveViewStarted();
                }
            }
        });
    }

    public void onLiveViewRestarted() {
        liveViewOpen = true;
        handler.post(new Runnable() {
            @Override
            public void run() {
                if (listener != null) {
                    listener.onLiveViewStarted();
                }
            }
        });
    }

    public void onLiveViewStopped() {
        liveViewOpen = false;
        handler.post(new Runnable() {
            @Override
            public void run() {
                if (listener != null) {
                    listener.onLiveViewStopped();
                }
            }
        });
    }

    public void onLiveViewReceived(final LiveViewData data) {
        handler.post(new Runnable() {
            @Override
            public void run() {
                if (listener != null) {
                    listener.onLiveViewData(data);
                }
            }
        });
    }

    public void onPictureReceived(final int objectHandle, final String filename, final Bitmap thumbnail,
                                  final Bitmap bitmap, final int orientation) {
        handler.post(new Runnable() {
            @Override
            public void run() {
                if (listener != null) {
                    listener.onCapturedPictureReceived(objectHandle, filename, thumbnail, bitmap, orientation);
                }
            }
        });
    }

    public void onPictureRetrievalFailed(final int objectHandle, final String reason) {
        handler.post(new Runnable() {
            @Override
            public void run() {
                if (listener != null) {
                    listener.onPictureRetrievalFailed(objectHandle, reason);
                }
            }
        });
    }

    public void onEventCameraCapture(boolean started) {
        cameraIsCapturing = started;
        if (isBulbCurrentShutterSpeed()) {
            handler.post(new Runnable() {
                @Override
                public void run() {
                    if (listener != null) {
                        if (cameraIsCapturing) {
                            listener.onBulbStarted();
                        } else {
                            listener.onBulbStopped();
                        }
                    }
                }
            });
        }
    }

    public void onEventDevicePropChanged(int property) {
        if ((ptpToVirtualProperty.containsKey(property) || ptpInternalProperties.contains(property))
                && ptpPropertyDesc.containsKey(property)) {
            DevicePropDesc desc = ptpPropertyDesc.get(property);
            queue.add(new GetDevicePropValueCommand(this, property, desc.datatype));
        }
    }

    public void onEventObjectAdded(final int handle, final int format) {
        handler.post(new Runnable() {
            @Override
            public void run() {
                if (listener != null) {
                    listener.onObjectAdded(handle, format);
                }
            }
        });
    }

//    public void onBulbExposureTime(final int seconds) {
//        if (seconds >= 0 && seconds <= 360000) {
//            handler.post(new Runnable() {
//                @Override
//                public void run() {
//                    if (listener != null) {
//                        listener.onBulbExposureTime(seconds);
//                    }
//                }
//            });
//        }
//    }

//    public void onFocusStarted() {
//        handler.post(new Runnable() {
//            @Override
//            public void run() {
//                if (listener != null) {
//                    listener.onFocusStarted();
//                }
//            }
//        });
//    }
//
//    public void onFocusEnded(final boolean hasFocused) {
//        handler.post(new Runnable() {
//            @Override
//            public void run() {
//                if (listener != null) {
//                    listener.onFocusEnded(hasFocused);
//                }
//            }
//        });
//    }

    public void onDeviceBusy(PtpAction action, boolean requeue) {
        if (AppConfig.LOG) {
            Log.i(TAG, "onDeviceBusy, sleeping a bit");
        }
        if (requeue) {
            action.reset();
            queue.add(action);
        }
        try {
            Thread.sleep(200);
        } catch (InterruptedException e) {
            // nop
        }
    }

    public void onPtpWarning(final String message) {
        if (AppConfig.LOG) {
            Log.i(TAG, "onPtpWarning: " + message);
        }
    }

    public void onPtpError(final String message) {
        if (AppConfig.LOG) {
            Log.e(TAG, "onPtpError: " + message);
        }
        state = State.Error;
        if (state == State.Active) {
            shutdown();
        } else {
            shutdownHard();
        }
        handler.post(new Runnable() {
            @Override
            public void run() {
                if (listener != null) {
                    listener.onError(message);
                }
            }
        });
    }

    private void onUsbError(final String message) {
        if (AppConfig.LOG) {
            Log.e(TAG, "onUsbError: " + message);
        }
        queue.clear();
        shutdownHard();
        state = State.Error;
        handler.post(() -> {
            if (listener != null) {
                listener.onError(String.format(Locale.US, "Error in USB communication: %s", message));
            }
        });
    }

    protected abstract void queueEventCheck();

    protected abstract boolean isBulbCurrentShutterSpeed();

    protected void openSession() {
        queue.add(new OpenSessionCommand(this));
    }

    protected void closeSession() {
        queue.add(new CloseSessionCommand(this));
    }


    @Override
    public String getDeviceName() {
        return deviceInfo != null ? deviceInfo.model : "";
    }

    @Override
    public boolean isSessionOpen() {
        return state == State.Active;
    }

    @Override
    public int getProperty(final int property) {
        if (properties.containsKey(property)) {
            return properties.get(property);
        }
        return 0x7fffffff;
    }

    @Override
    public boolean getPropertyEnabledState(int property) {
        // TODO Auto-generated method stub
        return false;
    }

    @Override
    public int[] getPropertyDesc(int property) {
        if (propertyDescriptions.containsKey(property)) {
            return propertyDescriptions.get(property);
        }
        return new int[0];
    }

    @Override
    public void setProperty(int property, int value) {
        final Integer ptpProperty = virtualToPtpProperty.get(property);
        if (ptpProperty != null && ptpPropertyDesc.containsKey(ptpProperty)) {
            queue.add(new SetDevicePropValueCommand(this, ptpProperty, value, ptpPropertyDesc.get(ptpProperty).datatype));
        }
    }

    @Override
    public String propertyToString(int property, int value) {
        Integer ptpProperty = virtualToPtpProperty.get(property);
        if (ptpProperty != null) {
            String text = PtpPropertyHelper.mapToString(productId, ptpProperty, value);
            return text != null ? text : "?";
        } else {
            return "";
        }
    }


    @Override
    public String getBiggestPropertyValue(int property) {
        Integer ptpProperty = virtualToPtpProperty.get(property);
        if (ptpProperty != null) {
            return PtpPropertyHelper.getBiggestValue(ptpProperty);
        } else {
            return "";
        }
    }

    @Override
    public void capture() {
        queue.add(new InitiateCaptureCommand(this));
    }

    @Override
    public boolean isAutoFocusSupported() {
        return autoFocusSupported;
    }

    @Override
    public boolean isLiveViewSupported() {
        return liveViewSupported;
    }

    @Override
    public boolean isLiveViewAfAreaSupported() {
        return liveViewAfAreaSupported;
    }

    @Override
    public boolean isHistogramSupported() {
        return histogramSupported;
    }

    @Override
    public boolean isLiveViewOpen() {
        return liveViewOpen;
    }

    @Override
    public boolean isDriveLensSupported() {
        return driveLensSupported;
    }

    @Override
    public String getDeviceInfo() {
        return deviceInfo != null ? deviceInfo.toString() : "unknown";
    }

    public void setDeviceInfo(DeviceInfo deviceInfo) {
        if (AppConfig.LOG) {
            Log.i(TAG, deviceInfo.toString());
        }

        this.deviceInfo = deviceInfo;

        Set<Integer> operations = new HashSet<Integer>();
        for (int i = 0; i < deviceInfo.operationsSupported.length; ++i) {
            operations.add(deviceInfo.operationsSupported[i]);
        }

        onOperationCodesReceived(operations);
    }

    @Override
    public void writeDebugInfo(File out) {
        try {
            FileWriter writer = new FileWriter(out);
            writer.append(deviceInfo.toString());
            writer.close();
        } catch (IOException e) {
        }
    }

    @Override
    public void retrievePicture(int objectHandle) {
        queue.add(new RetrievePictureAction(this, objectHandle, pictureSampleSize));
    }

    @Override
    public void retrieveStorages(StorageInfoListener listener) {
        queue.add(new GetStorageInfosAction(this, listener));
    }

    @Override
    public void retrieveImageHandles(StorageInfoListener listener, int storageId, int objectFormat) {
        queue.add(new GetObjectHandlesCommand(this, listener, storageId, objectFormat));
    }

    @Override
    public void retrieveImageInfo(RetrieveImageInfoListener listener, int objectHandle) {
        queue.add(new RetrieveImageInfoAction(this, listener, objectHandle));
    }

    @Override
    public void retrieveImage(RetrieveImageListener listener, int objectHandle) {
        queue.add(new RetrieveImageAction(this, listener, objectHandle, pictureSampleSize));
    }

    @Override
    public void setCapturedPictureSampleSize(int sampleSize) {
        this.pictureSampleSize = sampleSize;
    }

    enum State {
        // initial state
        Starting,
        // open session
        Active,
        // someone has asked to close session
        Stoping,
        // thread has stopped
        Stopped,
        // error happened
        Error
    }

    public interface IO {
        void handleCommand(Command command);
    }

    private class WorkerThread extends Thread implements IO {
        private final int bigInSize = 0x4000;
        public boolean stop;
        private int maxPacketOutSize;
        private int maxPacketInSize;
        private long lastEventCheck;
        private UsbRequest r1;
        private UsbRequest r2;
        private UsbRequest r3;
        // buffers for async data io, size bigInSize
        private ByteBuffer bigIn1;
        private ByteBuffer bigIn2;
        private ByteBuffer bigIn3;
        // buffer for small packets like command and response
        private ByteBuffer smallIn;
        // buffer containing full data out packet for processing
        private int fullInSize = 0x4000;
        private ByteBuffer fullIn;

        @Override
        public void run() {


            maxPacketOutSize = connection.getMaxPacketOutSize();
            maxPacketInSize = connection.getMaxPacketInSize();

            if (maxPacketOutSize <= 0 || maxPacketOutSize > 0xffff) {
                onUsbError(String.format(Locale.US, "Usb initialization error: out size invalid %d", maxPacketOutSize));
                return;
            }

            if (maxPacketInSize <= 0 || maxPacketInSize > 0xffff) {
                onUsbError(String.format(Locale.US, "USB initialization error: in size invalid %d", maxPacketInSize));
                return;
            }

            smallIn = ByteBuffer.allocate(Math.max(maxPacketInSize, maxPacketOutSize));
            smallIn.order(ByteOrder.LITTLE_ENDIAN);

            bigIn1 = ByteBuffer.allocate(bigInSize);
            bigIn1.order(ByteOrder.LITTLE_ENDIAN);
            bigIn2 = ByteBuffer.allocate(bigInSize);
            bigIn2.order(ByteOrder.LITTLE_ENDIAN);
            bigIn3 = ByteBuffer.allocate(bigInSize);
            bigIn3.order(ByteOrder.LITTLE_ENDIAN);

            fullIn = ByteBuffer.allocate(fullInSize);
            fullIn.order(ByteOrder.LITTLE_ENDIAN);

            r1 = connection.createInRequest();
            r2 = connection.createInRequest();
            r3 = connection.createInRequest();

            while (true) {
                synchronized (this) {
                    if (stop) {
                        break;
                    }
                }

                if (lastEventCheck + AppConfig.EVENTCHECK_PERIOD < System.currentTimeMillis()) {
                    lastEventCheck = System.currentTimeMillis();
                    PtpCamera.this.queueEventCheck();
                }

                PtpAction action = null;
                try {
                    action = queue.poll(1000, TimeUnit.MILLISECONDS);
                } catch (InterruptedException e) {
                }

                if (action != null) {
                    try {
                        action.exec(this);
                    } catch (Exception e) {
                        Log.e(TAG, "Action " + action.getClass().getSimpleName() + " threw exception: " + e.getMessage(), e);
                        // Don't crash the worker thread - notify listener and continue processing queue
                        onPtpError("Command failed: " + action.getClass().getSimpleName() + " - " + e.getMessage());
                    }
                }
            }
            r3.close();
            r2.close();
            r1.close();

        }

        @Override
        public void handleCommand(Command command) {
            if (AppConfig.LOG) {
                Log.i(TAG, "handling command " + command.getClass().getSimpleName());
            }
            ByteBuffer b = smallIn;
            b.position(0);
            command.encodeCommand(b);

            int outLen = b.position();

            int res = connection.bulkTransferOut(b.array(), outLen, AppConfig.USB_TRANSFER_TIMEOUT);
            if (res == -1) {
                onUsbError("USB device disconnected or permission revoked during command send");
                return;
            }
            if (res < outLen) {
                onUsbError(String.format(Locale.US, "Incomplete USB write: sent %d of %d bytes", res, outLen));
                return;
            }

            if (command.hasDataToSend()) {
                b = ByteBuffer.allocate(connection.getMaxPacketOutSize());
                b.order(ByteOrder.LITTLE_ENDIAN);
                command.encodeData(b);
                outLen = b.position();
                res = connection.bulkTransferOut(b.array(), outLen, AppConfig.USB_TRANSFER_TIMEOUT);
                if (res == -1) {
                    onUsbError("USB device disconnected or permission revoked during data send");
                    return;
                }
                if (res < outLen) {
                    onUsbError(String.format(Locale.US, "Incomplete USB data write: sent %d of %d bytes", res, outLen));
                    return;
                }
            }

            while (!command.hasResponseReceived()) {
                int maxPacketSize = maxPacketInSize;
                ByteBuffer in = smallIn;
                in.position(0);

                res = 0;
                int zeroByteAttempts = 0;
                final int MAX_ZERO_BYTE_ATTEMPTS = 10;
                while (res == 0) {
                    res = connection.bulkTransferIn(in.array(), maxPacketSize, AppConfig.USB_TRANSFER_TIMEOUT);
                    if (res == -1) {
                        onUsbError("USB device disconnected or permission revoked during read");
                        return;
                    }
                    if (res == 0 && ++zeroByteAttempts > MAX_ZERO_BYTE_ATTEMPTS) {
                        onUsbError("USB read timed out after " + MAX_ZERO_BYTE_ATTEMPTS + " attempts with zero bytes");
                        return;
                    }
                }
                if (res < 12) {
                    onUsbError(String.format(Locale.US, "Couldn't read header, only %d bytes available", res));
                    return;
                }

                int read = res;
                int length = in.getInt();
                ByteBuffer infull = null;

                if (read < length) {
                    if (length > fullInSize) {
                        fullInSize = (int) (length * 1.5);
                        
                        // Check available memory before allocating
                        Runtime runtime = Runtime.getRuntime();
                        long maxMemory = runtime.maxMemory();
                        long usedMemory = runtime.totalMemory() - runtime.freeMemory();
                        long availableMemory = maxMemory - usedMemory;
                        
                        // If we need more than 80% of available memory, reject the image
                        if (fullInSize > availableMemory * 0.8) {
                            Log.w(TAG, String.format("Memory pressure: need=%dMB, available=%dMB, rejecting image", 
                                fullInSize / (1024 * 1024), availableMemory / (1024 * 1024)));
                            onUsbError("Camera memory full. Please wait for current photos to process, then try again.");
                            return;
                        }
                        
                        try {
                            fullIn = ByteBuffer.allocate(fullInSize);
                        } catch (OutOfMemoryError e) {
                            Log.e(TAG, "Failed to allocate ByteBuffer even with memory check", e);
                            onUsbError("Device memory full. Please restart the app and try again.");
                            return;
                        }
                        fullIn.order(ByteOrder.LITTLE_ENDIAN);
                    }
                    infull = fullIn;
                    infull.position(0);
                    infull.put(in.array(), 0, read);
                    maxPacketSize = bigInSize;

                    int nextSize = Math.min(maxPacketSize, length - read);
                    int nextSize2 = Math.max(0, Math.min(maxPacketSize, length - read - nextSize));
                    int nextSize3 = 0;

                    r1.queue(bigIn1, nextSize);

                    if (nextSize2 > 0) {
                        r2.queue(bigIn2, nextSize2);
                    }

                    while (read < length) {

                        nextSize3 = Math.max(0, Math.min(maxPacketSize, length - read - nextSize - nextSize2));

                        if (nextSize3 > 0) {
                            bigIn3.position(0);
                            r3.queue(bigIn3, nextSize3);
                        }

                        if (nextSize > 0) {
                            connection.requestWait();
                            System.arraycopy(bigIn1.array(), 0, infull.array(), read, nextSize);
                            read += nextSize;
                        }

                        nextSize = Math.max(0, Math.min(maxPacketSize, length - read - nextSize2 - nextSize3));

                        if (nextSize > 0) {
                            bigIn1.position(0);
                            r1.queue(bigIn1, nextSize);
                        }

                        if (nextSize2 > 0) {
                            connection.requestWait();
                            System.arraycopy(bigIn2.array(), 0, infull.array(), read, nextSize2);
                            read += nextSize2;
                        }

                        nextSize2 = Math.max(0, Math.min(maxPacketSize, length - read - nextSize - nextSize3));

                        if (nextSize2 > 0) {
                            bigIn2.position(0);
                            r2.queue(bigIn2, nextSize2);
                        }

                        if (nextSize3 > 0) {
                            connection.requestWait();
                            System.arraycopy(bigIn3.array(), 0, infull.array(), read, nextSize3);
                            read += nextSize3;
                        }
                    }
                } else {
                    infull = in;
                }

                infull.position(0);
                try {
                    command.receivedRead(infull);

                    infull = null;
                } catch (RuntimeException e) {
                    // TODO user could send us some data here
                    if (AppConfig.LOG) {
                        Log.e(TAG, "Exception " + e.getLocalizedMessage());
                        e.printStackTrace();
                    }
                    onPtpError(String.format(Locale.US, "Error parsing %s with length %d", command.getClass().getSimpleName(),
                            length));
                }
            }
        }


    }

}
