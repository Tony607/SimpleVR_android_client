package com.camera.simplemjpeg.bluetooth;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.util.Timer;
import java.util.UUID;

import android.app.Activity;
import android.app.ProgressDialog;
import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.bluetooth.BluetoothSocket;
import android.content.Intent;
import android.os.Handler;
import android.os.Message;
import android.util.Log;
import android.widget.Toast;

import com.camera.simplemjpeg.CameraActivity;

public class BT_work {

	static CameraActivity the_app;
	public static ByteQueue mByteQueue;
	BluetoothAdapter BTadapter;
	ProgressDialog SearchDialog;
	static BluetoothDevice device;
	static BluetoothSocket socket;
	String revMsg = "";
	static String display = "";
	String ReadplotData = "";
	static int wait = 5;
	/**
	 * sycn check box state
	 */
	public static boolean sync = false;
	/**
	 * auto timer
	 */
	public Timer timer = new Timer();

	public static Boolean socketclosed = true;
	private static Thread myThread = null;

	public static final String PLOTNOW = "android.intent.action.CHAPTER10ACTIVITY";

	static Handler msgHandler = new Handler() {
		public void handleMessage(Message msg) {
			super.handleMessage(msg);
			switch (msg.what) {
			case MESSAGE_REV:

				// showMsg.append("receive\n");
				// Log.i(TAG, "handleMessage--MESSAGE_REV");
				display = "";
				break;
			case MESSAGE_SEND:
				// showMsg.append(">"+handleMsg);
				break;
			}
		}
	};
	static BufferedReader MsgReader;
	static OutputStream MsgWriter;
	private static final String TAG = "BT_work";
	private static final int REQUEST_ENABLE_BT = 1;
	private static final int REQUEST_DISCOVERY = 2;
	public static final int MESSAGE_REV = 1;
	private static final int MESSAGE_SEND = 2;
	public static final int CONNECTION_SUCCESS = 0;
	protected static final byte STOP_SIGN = (byte) 0xFF;
	static byte[] bufferbytes = new byte[4];

	private static Runnable MsgRun = new Runnable() {

		public void run() {
			Log.i(TAG, "BEGIN mConnectedThread");
			int length;

			// Keep listening to the InputStream while connected
			while (true) {
				try {
					if (socketclosed == true) {

						Log.d(TAG, "socketclosed break");
						break;
					} else if (socket.getInputStream() != null) {
						byte[] tmp = new byte[1024];

						length = socket.getInputStream().read(tmp);
						if (length > 0) {

							byte[] buf = new byte[length];
							for (int i = 0; i < length; i++) {
								buf[i] = tmp[i];
							}
							for (byte b : buf) {
								if (b == STOP_SIGN) {
									the_app.mHandler.obtainMessage(MESSAGE_REV)
											.sendToTarget();
									break;
								}
							}
							try {
								mByteQueue.write(buf, 0, length);

							} catch (InterruptedException e) {
							}
						}
					}
				} catch (IOException e) {
					Log.e(TAG, "disconnected", e);
					break;
				}
			}
			Log.d(TAG, "Thread loop Breaked");
		}
	};

	public BT_work(CameraActivity app) {
		super();
		the_app = app;
		mByteQueue = new ByteQueue(4 * 1024);
	}

	public synchronized void start() {
		BTadapter = BluetoothAdapter.getDefaultAdapter();
		if (!BTadapter.isEnabled()) // isEnabled check if BT is opened
		{
			Log.d(TAG, "onCreate--BT not Enabled-->REQUEST_ENABLE_BT");
			Intent enableBtIntent = new Intent(
					BluetoothAdapter.ACTION_REQUEST_ENABLE);
			the_app.startActivityForResult(enableBtIntent, REQUEST_ENABLE_BT);
			Log.d(TAG, "startActivityForResult-->REQUEST_ENABLE_BT");
		} else {
			Log.d(TAG, "onCreate--BT Enabled-->REQUEST_DISCOVERY");
			Intent discoverrs = new Intent(the_app, DiscoveryActivity.class);
			the_app.startActivityForResult(discoverrs, REQUEST_DISCOVERY);
			Log.d(TAG, "startActivityForResult-->REQUEST_DISCOVERY");
		}

	}

	public static void onactivityresult(int requestCode, int resultCode,
			Intent data) {
		switch (requestCode) {
		case REQUEST_ENABLE_BT:
			Log.i(TAG, "onActivityResult--REQUEST_ENABLE_BT");
			if (resultCode == Activity.RESULT_OK) {
				Log.i(TAG, "onActivityResult--BTEnabled=true");
				Toast.makeText(the_app, "BlueTooth Opened", Toast.LENGTH_SHORT)
						.show();
				// REQUEST_DISCOVERY---startActivityForResult
				Intent discoverrs = new Intent(the_app, DiscoveryActivity.class);
				the_app.startActivityForResult(discoverrs, REQUEST_DISCOVERY);
				Log.i(TAG, "startActivityForResult-->REQUEST_DISCOVERY");
			} else {

				Log.d(TAG, "BT is off");
				Toast.makeText(the_app, "Unable to open BlueTooth,Exiting...",
						Toast.LENGTH_SHORT).show();
				the_app.finish();
			}
			break;
		case REQUEST_DISCOVERY:
			// When DeviceListActivity.java returns with a device to connect

			Log.i(TAG, "onActivityResult--REQUEST_DISCOVERY");
			if (resultCode == Activity.RESULT_OK) {
				device = data.getParcelableExtra(BluetoothDevice.EXTRA_DEVICE);
				connect(device);
			} else {
				Log.d(TAG, "onActivityResult--REQUEST_DISCOVERY-->Exit");
				// the_app.finish();
			}
			break;
		}

	}

	/** connect the device */
	protected static void connect(BluetoothDevice device) {

		try {
			if (socket != null) {
				socket.close();
				socketclosed = true;
				Log.d(TAG, "socketclosed->true(connect)");
			}
		} catch (IOException e1) {
			e1.printStackTrace();
		}
		try {

			socket = device.createRfcommSocketToServiceRecord(UUID
					.fromString("00001101-0000-1000-8000-00805F9B34FB"));
			// Connect
			Log.d(TAG, "Connecting to" + device.getName());
			socket.connect();
			socketclosed = false;
			if (socket != null) {
				Toast.makeText(the_app, device.getName() + " Connected!",
						Toast.LENGTH_SHORT).show();
				the_app.mHandler.obtainMessage(CONNECTION_SUCCESS)
						.sendToTarget();
				MsgReader = new BufferedReader(new InputStreamReader(
						socket.getInputStream()));
				Log.i(TAG, "MsgReader");// MsgReader
				MsgWriter = socket.getOutputStream();
				// prompt message of Successful Connection
				Message msg = new Message();
				msg.what = MESSAGE_SEND;
				msgHandler.sendMessage(msg);
				myThread = new Thread(MsgRun);
				myThread.start();

			}

		} catch (IOException e) {
			Log.e(TAG, "", e);
		}
	}

	public static void sendBTString(String str) {
		byte[] msgBuffer = str.getBytes();
		try {
			MsgWriter.write(msgBuffer);// send the data
			MsgWriter.flush();
		} catch (IOException e) {
			e.printStackTrace();
		}
		Message msg = new Message();
		msg.what = MESSAGE_SEND;
		msgHandler.sendMessage(msg);
	}

	public static void sendBTbytes(byte[] arr) {
		try {
			MsgWriter.write(arr);// send the data
			MsgWriter.flush();
		} catch (IOException e) {
			e.printStackTrace();
		}
		Message msg = new Message();
		msg.what = MESSAGE_SEND;
		msgHandler.sendMessage(msg);
	}

	public static void stop() {
		socketclosed = true;
		Log.d(TAG, "bluetooth socket closed");

		try {
			if (socket != null)
				socket.close();
		} catch (IOException e1) {
			e1.printStackTrace();
		}

		Log.d(TAG, "socketclosed->true");
	}

	public static void stopAndRescan() {
		socketclosed = true;
		Log.d(TAG, "Menu Disconnect set socketclosed => true");

		try {
			if (socket != null)
				socket.close();
		} catch (IOException e1) {
			e1.printStackTrace();
		}

		Log.d(TAG, "socketclosed->true");
		// REQUEST_DISCOVERY---startActivityForResult
		Intent discoverrs = new Intent(the_app, DiscoveryActivity.class);
		the_app.startActivityForResult(discoverrs, REQUEST_DISCOVERY);
		Log.i(TAG, "Disconnect and-->REQUEST_DISCOVERY");
	}

	public int getBytesAvailable() {
		// TODO Auto-generated method stub
		return mByteQueue.getBytesAvailable();
	}

	public int read(byte[] mReceiveBuffer, int i, int bytesToRead) {
		// TODO Auto-generated method stub
		try {
			return mByteQueue.read(mReceiveBuffer, 0, bytesToRead);
		} catch (InterruptedException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}
		return 0;
	}

}
