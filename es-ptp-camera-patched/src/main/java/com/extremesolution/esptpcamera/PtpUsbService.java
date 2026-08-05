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

import android.app.PendingIntent;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.hardware.usb.UsbConstants;
import android.hardware.usb.UsbDevice;
import android.hardware.usb.UsbDeviceConnection;
import android.hardware.usb.UsbEndpoint;
import android.hardware.usb.UsbInterface;
import android.hardware.usb.UsbManager;
import android.os.Handler;
import android.util.Log;

import androidx.core.content.ContextCompat;

import com.extremesolution.esptpcamera.AppConfig;
import com.extremesolution.esptpcamera.Camera.CameraListener;
import com.extremesolution.esptpcamera.PtpCamera.State;

import java.util.Map;

public class PtpUsbService implements PtpService {

    private static final String ACTION_USB_PERMISSION = "com.android.example.USB_PERMISSION";
    private final String TAG = PtpUsbService.class.getSimpleName();
    private final Handler handler = new Handler();
    private final UsbManager usbManager;
    private PtpCamera camera;
    Runnable shutdownRunnable = new Runnable() {
        @Override
        public void run() {
            shutdown();
        }
    };
    private CameraListener listener;
    public final BroadcastReceiver permissionReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            String action = intent.getAction();
            if (ACTION_USB_PERMISSION.equals(action)) {
                unregisterPermissionReceiver(context);
                // --- ADD THIS DETAILED LOGGING ---
                boolean granted = intent.getBooleanExtra(UsbManager.EXTRA_PERMISSION_GRANTED, false);
                Log.d(TAG, "### USB PERMISSION RESPONSE ###");
                Log.d(TAG, "Permission Granted: " + granted);
                if (intent.getExtras() != null) {
                    for (String key : intent.getExtras().keySet()) {
                        Log.d(TAG, "Extra -> Key: " + key + ", Value: " + intent.getExtras().get(key));
                    }
                }
                // --- END OF LOGGING ---
                synchronized (this) {
                    UsbDevice device = intent.getParcelableExtra(UsbManager.EXTRA_DEVICE);
                    if (granted) {
                        Log.d(TAG, "Permission was granted. Calling connect().");
                        connect(context, device);
                    } else {
                        Log.e(TAG, "Permission was denied by the system.");
                        if (listener != null) {
                            listener.onError("USB permission denied");
                        }
                    }
                }
            }
        }
    };

    public PtpUsbService(Context context) {
        this.usbManager = (UsbManager) context.getSystemService(Context.USB_SERVICE);
    }

    @Override
    public void setCameraListener(CameraListener listener) {
        this.listener = listener;
        if (camera != null) {
            camera.setListener(listener);
        }
    }

    @Override
    public void initialize(Context context, Intent intent, Boolean cameraActivity) {
        handler.removeCallbacks(shutdownRunnable);
        if (camera != null) {
            if (AppConfig.LOG) {
                Log.i(TAG, "initialize: camera available");
            }
            if (camera.getState() == State.Active) {
                if (listener != null) {
                    listener.onCameraStarted(camera);
                }
                return;
            }
            if (AppConfig.LOG) {
                Log.i(TAG, "initialize: camera not active, satet " + camera.getState());
            }
            camera.shutdownHard();
        }
        UsbDevice device = intent.getParcelableExtra(UsbManager.EXTRA_DEVICE);
        if (device != null) {
            if (AppConfig.LOG) {
                Log.i(TAG, "initialize: got device through intent");
            }
            connect(context, device);
        } else {
            if (AppConfig.LOG) {
                Log.i(TAG, "initialize: looking for compatible camera");
            }
            device = lookupCompatibleDevice(usbManager);
            if (device != null) {
                if (cameraActivity) {
                    registerPermissionReceiver(context);
                    int flags;
                    // Add FLAG_MUTABLE for modern Android versions using the bitwise OR operator
                    flags = PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_MUTABLE;

                    Intent usbIntent = new Intent(ACTION_USB_PERMISSION);
                    usbIntent.setPackage(context.getPackageName());

                    PendingIntent mPermissionIntent = PendingIntent.getBroadcast(context, 0, usbIntent, flags);
                    usbManager.requestPermission(device, mPermissionIntent);
                }
                listener.onCameraFound(device);
            } else {
                listener.onNoCameraFound();
            }
        }
    }

    @Override
    public void shutdown() {
        if (AppConfig.LOG) {
            Log.i(TAG, "shutdown");
        }
        if (camera != null) {
            camera.shutdown();
            camera = null;
        }
    }

    @Override
    public void lazyShutdown() {
        if (AppConfig.LOG) {
            Log.i(TAG, "lazy shutdown");
        }
        handler.postDelayed(shutdownRunnable, 4000);
    }

    private void registerPermissionReceiver(Context context) {
        if (AppConfig.LOG) {
            Log.i(TAG, "register permission receiver");
        }
        IntentFilter filter = new IntentFilter(ACTION_USB_PERMISSION);
        ContextCompat.registerReceiver(context, permissionReceiver, filter, ContextCompat.RECEIVER_NOT_EXPORTED);
    }

    private void unregisterPermissionReceiver(Context context) {
        if (AppConfig.LOG) {
            Log.i(TAG, "unregister permission receiver");
        }
        context.unregisterReceiver(permissionReceiver);
    }

    private UsbDevice lookupCompatibleDevice(UsbManager manager) {
        Map<String, UsbDevice> deviceList = manager.getDeviceList();
        for (Map.Entry<String, UsbDevice> e : deviceList.entrySet()) {
            UsbDevice d = e.getValue();
            if (d.getVendorId() == PtpConstants.CanonVendorId || d.getVendorId() == PtpConstants.NikonVendorId) {
                return d;
            }
        }
        return null;
    }

    private boolean connect(Context context, UsbDevice device) {
        if (camera != null) {
            camera.shutdown();
            camera = null;
        }
        for (int i = 0, n = device.getInterfaceCount(); i < n; ++i) {
            UsbInterface intf = device.getInterface(i);

            if (intf.getEndpointCount() != 3) {
                continue;
            }

            UsbEndpoint in = null;
            UsbEndpoint out = null;

            for (int e = 0, en = intf.getEndpointCount(); e < en; ++e) {
                UsbEndpoint endpoint = intf.getEndpoint(e);
                if (endpoint.getType() == UsbConstants.USB_ENDPOINT_XFER_BULK) {
                    if (endpoint.getDirection() == UsbConstants.USB_DIR_IN) {
                        in = endpoint;
                    } else if (endpoint.getDirection() == UsbConstants.USB_DIR_OUT) {
                        out = endpoint;
                    }
                }
            }

            if (in == null || out == null) {
                continue;
            }

            if (AppConfig.LOG) {
                Log.i(TAG, "Found compatible USB interface");
                Log.i(TAG, "Interface class " + intf.getInterfaceClass());
                Log.i(TAG, "Interface subclass " + intf.getInterfaceSubclass());
                Log.i(TAG, "Interface protocol " + intf.getInterfaceProtocol());
                Log.i(TAG, "Bulk out max size " + out.getMaxPacketSize());
                Log.i(TAG, "Bulk in max size " + in.getMaxPacketSize());
            }

            // Open the device connection and store the result in a local variable.
            UsbDeviceConnection deviceConnection = usbManager.openDevice(device);

            // Check for a null connection.
            if (deviceConnection == null) {
                // Permission was likely denied or the device was unplugged.
                Log.e(TAG, "Failed to open USB device connection. Permission may have been denied.");
                if (listener != null) {
                    listener.onError("Failed to connect to the camera.");
                }
                return false; // Exit the method as we can't proceed.
            }

            // If the connection is successful, create the PtpUsbConnection and the camera.
            if (device.getVendorId() == PtpConstants.CanonVendorId) {
                PtpUsbConnection connection = new PtpUsbConnection(usbManager.openDevice(device), in, out,
                        device.getVendorId(), device.getProductId());
                camera = new EosCamera(connection, listener);
            } else if (device.getVendorId() == PtpConstants.NikonVendorId) {
                PtpUsbConnection connection = new PtpUsbConnection(usbManager.openDevice(device), in, out,
                        device.getVendorId(), device.getProductId());
                camera = new NikonCamera(connection, listener);
            }

            return true;
        }

        if (listener != null) {
            listener.onError("No compatible camera found");
        }

        return false;
    }
}
