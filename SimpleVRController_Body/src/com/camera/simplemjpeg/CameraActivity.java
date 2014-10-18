package com.camera.simplemjpeg;

import java.io.IOException;
import java.net.URI;
import java.net.URISyntaxException;
import java.nio.ByteBuffer;

import org.apache.http.HttpResponse;
import org.apache.http.client.ClientProtocolException;
import org.apache.http.client.methods.HttpGet;
import org.apache.http.impl.client.DefaultHttpClient;
import org.apache.http.params.HttpConnectionParams;
import org.apache.http.params.HttpParams;
import org.java_websocket.client.WebSocketClient;
import org.java_websocket.handshake.ServerHandshake;

import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.hardware.Sensor;
import android.hardware.SensorEvent;
import android.hardware.SensorEventListener;
import android.hardware.SensorManager;
import android.os.AsyncTask;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.Message;
import android.os.SystemClock;
import android.os.Vibrator;
import android.text.StaticLayout;
import android.util.Log;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;
import android.view.View;
import android.view.View.OnClickListener;
import android.view.WindowManager;

import com.camera.simplemjpeg.bluetooth.BT_work;
import com.camera.simplemjpeg.sensor.MagnetSensor;
import com.camera.simplemjpeg.util.BodyNode;
import com.camera.simplemjpeg.util.LoggerConfig;
import com.camera.simplemjpeg.util.Quaternion;

/**
 * Activity handles mjpg streaming and rendering the image side by side, reading
 * and sending head tracking data to Web Socket Listening on onCardboardTrigger
 * events
 */
public class CameraActivity extends Activity implements OnClickListener,
		SensorEventListener, MagnetSensor.OnCardboardTriggerListener {
	private static final boolean DEBUG = false;
	private static final String TAG = "MJPEG";
	protected static final byte STOP_SIGN = (byte) 0xFF;
	/** number of external body motion tracking nodes */
	private static final int NODES_COUNTS = 1;
	/**
	 * bytes array for the websocket data, for each Quaternion, there are 4
	 * bytes, x,y,z,w First head quaternion, then body nodes quaternions
	 */
	private static final int BYTES_ARRAY_LENGTH = 4 * (1 + NODES_COUNTS);
	/** Single frame structure: id, x, y, z, w, end */
	private static final int SINGLE_FRAME_LENGTH = 6;
	public static BodyNode[] trackingNodes;
	public static BodyNode headNode;
	private static BT_work theBt_work;
	CameraActivity the_app;
	/**
	 * Used to temporarily hold data received from the remote process. Allocated
	 * once and used permanently to minimize heap thrashing.
	 */
	private byte[] mReceiveBuffer;

	private MjpegView mv = null;
	String URL;
	// WebSocket URL
	String WSURL;// example:"ws://192.168.2.74:8080"

	// for settings (network and resolution)
	private static final int REQUEST_SETTINGS = 0;

	private int width = 320;
	private int height = 240;

	private int ip_ad1 = 192;
	private int ip_ad2 = 168;
	private int ip_ad3 = 2;
	private int ip_ad4 = 74;

	/*
	 * private int ip_ad1 = 10; private int ip_ad2 = 20; private int ip_ad3 =
	 * 99; private int ip_ad4 = 124;
	 */
	private int ip_port = 8088;
	/**
	 * WebSocket port CubieBoard is setup to 8089 When testing on my PC change
	 * it to 8089
	 */
	private int ws_port = 8089;
	private String ip_command = "?action=stream";

	private boolean suspending = false;

	final Handler handler = new Handler();
	/** Handler to timeout and get back to immersive mode */
	private Handler fullScreenModeHandler;
	// WebSocket and device orientation
	private WebSocketClient mWebSocketClient;
	SensorManager sm;
	private static long timeStamp = 0;
	private static boolean webSocketIsConnected = false;

	private MagnetSensor mMagnetSensor;
	private Vibrator mVibrator;
	Runnable myTask = new Runnable() {
		@Override
		public void run() {
			toggleHideyBar(false);
			// do work
			// mHandler.postDelayed(this, 1000);
		}
	};

	public void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);
		// fulscreen
		setFullScreen();
		fullScreenModeHandler = new Handler();
		SharedPreferences preferences = getSharedPreferences("SAVED_VALUES",
				MODE_PRIVATE);
		width = preferences.getInt("width", width);
		height = preferences.getInt("height", height);
		ip_ad1 = preferences.getInt("ip_ad1", ip_ad1);
		ip_ad2 = preferences.getInt("ip_ad2", ip_ad2);
		ip_ad3 = preferences.getInt("ip_ad3", ip_ad3);
		ip_ad4 = preferences.getInt("ip_ad4", ip_ad4);
		ip_port = preferences.getInt("ip_port", ip_port);
		ip_command = preferences.getString("ip_command", ip_command);

		StringBuilder sb = new StringBuilder();
		String s_http = "http://";
		String s_dot = ".";
		String s_colon = ":";
		String s_slash = "/";
		sb.append(s_http);
		sb.append(ip_ad1);
		sb.append(s_dot);
		sb.append(ip_ad2);
		sb.append(s_dot);
		sb.append(ip_ad3);
		sb.append(s_dot);
		sb.append(ip_ad4);
		sb.append(s_colon);
		sb.append(ip_port);
		sb.append(s_slash);
		sb.append(ip_command);
		URL = new String(sb);
		// build WebSocket URL
		String s_ws = "ws://";
		sb = new StringBuilder();
		sb.append(s_ws);
		sb.append(ip_ad1);
		sb.append(s_dot);
		sb.append(ip_ad2);
		sb.append(s_dot);
		sb.append(ip_ad3);
		sb.append(s_dot);
		sb.append(ip_ad4);
		sb.append(s_colon);
		sb.append(ws_port);
		WSURL = new String(sb);

		setContentView(R.layout.main);
		// keep screen on for this activity
		getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
		// setup Orientation sensors and Webscoket
		initOrientationSensors();
		connectWebSocket();
		// setup mjpeg view
		mv = (MjpegView) findViewById(R.id.mv);
		mv.setOnClickListener(this);
		if (mv != null) {
			mv.setResolution(width, height);
		}

		mMagnetSensor = new MagnetSensor(this);
		mMagnetSensor.setOnCardboardTriggerListener(this);
		mVibrator = (Vibrator) getSystemService(Context.VIBRATOR_SERVICE);
		setTitle(R.string.title_connecting);
		new DoReadLeft().execute(URL);
		// -----set up bluetooth related stuff---
		the_app = this;

		mReceiveBuffer = new byte[4 * 1024];
		theBt_work = new BT_work(the_app);
		theBt_work.start();
		// --initialize tracking nodes
		trackingNodes = new BodyNode[NODES_COUNTS];
		for (int i = 0; i < trackingNodes.length; i++) {
			trackingNodes[i] = new BodyNode(false);
		}
		headNode = new BodyNode(true);
	}

	private void setFullScreen() {

		View decorView = getWindow().getDecorView();
		// Hide the status bar.
		int uiOptions = View.SYSTEM_UI_FLAG_LAYOUT_STABLE
				| View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION
				| View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN
				| View.SYSTEM_UI_FLAG_HIDE_NAVIGATION
				| View.SYSTEM_UI_FLAG_FULLSCREEN
				| View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY;
		decorView.setSystemUiVisibility(uiOptions);
		// Remember that you should never show the action bar if the
		// status bar is hidden, so hide that too if necessary.
		// ActionBar actionBar = getActionBar();
		// actionBar.hide();

	}

	public void onResume() {
		if (DEBUG)
			Log.d(TAG, "onResume()");
		super.onResume();
		if (mv != null) {
			if (suspending) {
				new DoReadLeft().execute(URL);
				suspending = false;
			}
		}
		sm = (SensorManager) getSystemService(Context.SENSOR_SERVICE);
		// check if the ACCELEROMETER exist
		initOrientationSensors();
		mMagnetSensor.start();

	}

	public void onStart() {
		if (DEBUG)
			Log.d(TAG, "onStart()");
		super.onStart();
	}

	public void onPause() {
		if (DEBUG)
			Log.d(TAG, "onPause()");
		super.onPause();
		if (mv != null) {
			if (mv.isStreaming()) {
				mv.stopPlayback();
				suspending = true;
			}
		}
		sm.unregisterListener(this);
		mMagnetSensor.stop();
		fullScreenModeHandler.removeCallbacks(myTask);
	}

	public void onStop() {
		if (DEBUG)
			Log.d(TAG, "onStop()");
		sm.unregisterListener(this);
		fullScreenModeHandler.removeCallbacks(myTask);
		BT_work.stop();
		super.onStop();
	}

	public void onDestroy() {
		if (DEBUG)
			Log.d(TAG, "onDestroy()");

		if (mv != null) {
			mv.freeCameraMemory();
		}

		super.onDestroy();
		// Don't forget to shutdown!
		BT_work.socketclosed = true;
		super.onDestroy();
		// android.os.Process.killProcess(android.os.Process.myPid());
	}

	@Override
	public boolean onCreateOptionsMenu(Menu menu) {
		MenuInflater inflater = getMenuInflater();
		inflater.inflate(R.layout.option_menu, menu);
		return true;
	}

	@Override
	public boolean onOptionsItemSelected(MenuItem item) {
		switch (item.getItemId()) {
		case R.id.settings:
			Intent settings_intent = new Intent(CameraActivity.this,
					SettingsActivity.class);
			settings_intent.putExtra("width", width);
			settings_intent.putExtra("height", height);
			settings_intent.putExtra("ip_ad1", ip_ad1);
			settings_intent.putExtra("ip_ad2", ip_ad2);
			settings_intent.putExtra("ip_ad3", ip_ad3);
			settings_intent.putExtra("ip_ad4", ip_ad4);
			settings_intent.putExtra("ip_port", ip_port);
			settings_intent.putExtra("ip_command", ip_command);
			startActivityForResult(settings_intent, REQUEST_SETTINGS);
			return true;
		}
		return false;
	}

	public void onActivityResult(int requestCode, int resultCode, Intent data) {
		switch (requestCode) {
		case REQUEST_SETTINGS:
			setFullScreen();
			if (resultCode == Activity.RESULT_OK) {
				width = data.getIntExtra("width", width);
				height = data.getIntExtra("height", height);
				ip_ad1 = data.getIntExtra("ip_ad1", ip_ad1);
				ip_ad2 = data.getIntExtra("ip_ad2", ip_ad2);
				ip_ad3 = data.getIntExtra("ip_ad3", ip_ad3);
				ip_ad4 = data.getIntExtra("ip_ad4", ip_ad4);
				ip_port = data.getIntExtra("ip_port", ip_port);
				ip_command = data.getStringExtra("ip_command");

				if (mv != null) {
					mv.setResolution(width, height);
				}
				SharedPreferences preferences = getSharedPreferences(
						"SAVED_VALUES", MODE_PRIVATE);
				SharedPreferences.Editor editor = preferences.edit();
				editor.putInt("width", width);
				editor.putInt("height", height);
				editor.putInt("ip_ad1", ip_ad1);
				editor.putInt("ip_ad2", ip_ad2);
				editor.putInt("ip_ad3", ip_ad3);
				editor.putInt("ip_ad4", ip_ad4);
				editor.putInt("ip_port", ip_port);
				editor.putString("ip_command", ip_command);

				editor.commit();

				new RestartApp().execute();
			}
			break;
		default:
			BT_work.onactivityresult(requestCode, resultCode, data);
			break;
		}
	}

	public void setImageError() {
		handler.post(new Runnable() {
			@Override
			public void run() {
				setTitle(R.string.title_imageerror);
				return;
			}
		});
	}

	public class DoReadLeft extends AsyncTask<String, Void, MjpegInputStream> {
		protected MjpegInputStream doInBackground(String... url) {
			// TODO: if camera has authentication deal with it and don't just
			// not work
			HttpResponse res = null;
			DefaultHttpClient httpclient = new DefaultHttpClient();
			HttpParams httpParams = httpclient.getParams();
			HttpConnectionParams.setConnectionTimeout(httpParams, 5 * 1000);
			HttpConnectionParams.setSoTimeout(httpParams, 5 * 1000);
			if (DEBUG)
				Log.d(TAG, "1. Sending http request");
			try {
				res = httpclient.execute(new HttpGet(URI.create(url[0])));
				if (DEBUG)
					Log.d(TAG, "2. Request finished, status = "
							+ res.getStatusLine().getStatusCode());
				if (res.getStatusLine().getStatusCode() == 401) {
					// You must turn off camera User Access Control before this
					// will work
					return null;
				}
				return new MjpegInputStream(res.getEntity().getContent());
			} catch (ClientProtocolException e) {
				if (DEBUG) {
					e.printStackTrace();
					Log.d(TAG, "Request failed-ClientProtocolException", e);
				}
				// Error connecting to camera
			} catch (IOException e) {
				if (DEBUG) {
					e.printStackTrace();
					Log.d(TAG, "Request failed-IOException", e);
				}
				// Error connecting to camera
			}
			return null;
		}

		protected void onPostExecute(MjpegInputStream result) {
			mv.setSource(result);
			if (result != null) {
				result.setSkip(1);
				setTitle(R.string.app_name);
			} else {
				setTitle(R.string.title_disconnected);
			}
			mv.setDisplayMode(MjpegView.SIZE_FULLSCREEN);
			mv.showFps(true);
		}
	}

	public class RestartApp extends AsyncTask<Void, Void, Void> {
		protected Void doInBackground(Void... v) {
			CameraActivity.this.finish();
			return null;
		}

		protected void onPostExecute(Void v) {
			startActivity((new Intent(CameraActivity.this, CameraActivity.class)));
		}
	}

	@Override
	public void onClick(View v) {
		switch (v.getId()) {
		case R.id.mv:
			toggleHideyBar(true);
			fullScreenModeHandler.postDelayed(myTask, 3000);
			break;

		default:
			break;
		}

	}

	@Override
	public void onWindowFocusChanged(boolean hasFocus) {
		super.onWindowFocusChanged(hasFocus);
		if (hasFocus) {
			getWindow().getDecorView().setSystemUiVisibility(
					View.SYSTEM_UI_FLAG_LAYOUT_STABLE
							| View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION
							| View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN
							| View.SYSTEM_UI_FLAG_HIDE_NAVIGATION
							| View.SYSTEM_UI_FLAG_FULLSCREEN
							| View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY);
		}
	}

	/**
	 * Detects and toggles immersive mode (also known as "hidey bar" mode).
	 */
	public void toggleHideyBar(boolean show) {

		// BEGIN_INCLUDE (get_current_ui_flags)
		// The UI options currently enabled are represented by a bitfield.
		// getSystemUiVisibility() gives us that bitfield.
		int uiOptions = getWindow().getDecorView().getSystemUiVisibility();
		int newUiOptions = uiOptions;
		// END_INCLUDE (get_current_ui_flags)
		// BEGIN_INCLUDE (toggle_ui_flags)
		boolean isImmersiveModeEnabled = ((uiOptions | View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY) == uiOptions);
		if (isImmersiveModeEnabled) {
			Log.i(TAG, "Turning immersive mode mode off. ");
		} else {
			Log.i(TAG, "Turning immersive mode mode on.");
		}
		if (show) {

			// Navigation bar hiding: Backwards compatible to ICS.
			if (Build.VERSION.SDK_INT >= 14) {
				newUiOptions = newUiOptions
						& ~View.SYSTEM_UI_FLAG_HIDE_NAVIGATION;
			}

			// Status bar hiding: Backwards compatible to Jellybean
			if (Build.VERSION.SDK_INT >= 16) {
				newUiOptions = newUiOptions & ~View.SYSTEM_UI_FLAG_FULLSCREEN;
			}

			// Immersive mode: Backward compatible to KitKat.
			// Note that this flag doesn't do anything by itself, it only
			// augments the behavior
			// of HIDE_NAVIGATION and FLAG_FULLSCREEN. For the purposes of this
			// sample
			// all three flags are being toggled together.
			// Note that there are two immersive mode UI flags, one of which is
			// referred to as "sticky".
			// Sticky immersive mode differs in that it makes the navigation and
			// status bars
			// semi-transparent, and the UI flag does not get cleared when the
			// user interacts with
			// the screen.
			if (Build.VERSION.SDK_INT >= 18) {
				newUiOptions = newUiOptions
						& ~View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY;
			}
		} else {

			// Navigation bar hiding: Backwards compatible to ICS.
			if (Build.VERSION.SDK_INT >= 14) {
				newUiOptions |= View.SYSTEM_UI_FLAG_HIDE_NAVIGATION;
			}

			// Status bar hiding: Backwards compatible to Jellybean
			if (Build.VERSION.SDK_INT >= 16) {
				newUiOptions |= View.SYSTEM_UI_FLAG_FULLSCREEN;
			}
			if (Build.VERSION.SDK_INT >= 18) {
				newUiOptions |= View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY;
			}

		}
		getWindow().getDecorView().setSystemUiVisibility(newUiOptions);
		// END_INCLUDE (set_ui_flags)
	}

	private void connectWebSocket() {
		URI uri;
		try {
			uri = new URI(WSURL);
		} catch (URISyntaxException e) {
			e.printStackTrace();
			return;
		}

		mWebSocketClient = new WebSocketClient(uri) {
			@Override
			public void onOpen(ServerHandshake serverHandshake) {
				Log.i("Websocket", "Opened");
				webSocketIsConnected = true;
				/*
				 * mWebSocketClient.send("Hello from " + Build.MANUFACTURER +
				 * " " + Build.MODEL);
				 */
			}

			@Override
			public void onMessage(String s) {
				// final String message = s;
				runOnUiThread(new Runnable() {
					@Override
					public void run() {
						// TextView textView = (TextView)
						// findViewById(R.id.messages);
						// textView.setText(textView.getText() + "\n" +
						// message);
					}
				});
			}

			@Override
			public void onClose(int i, String s, boolean b) {
				webSocketIsConnected = false;
				Log.i("Websocket", "Closed " + s);
			}

			@Override
			public void onError(Exception e) {
				Log.i("Websocket", "Error " + e.getMessage());
			}
		};
		mWebSocketClient.connect();
	}

	private void initOrientationSensors() {
		sm = (SensorManager) getSystemService(Context.SENSOR_SERVICE);
		if (sm.getSensorList(Sensor.TYPE_GAME_ROTATION_VECTOR).size() != 0) {
			Sensor s = sm.getDefaultSensor(Sensor.TYPE_GAME_ROTATION_VECTOR);
			sm.registerListener(this, s, SensorManager.SENSOR_DELAY_GAME);
		}
	}

	@Override
	public void onSensorChanged(SensorEvent event) {
		float quaternion[];
		switch (event.sensor.getType()) {
		case Sensor.TYPE_GAME_ROTATION_VECTOR:
			// x,y,z,w,estimated heading Accuracy (in radians) (-1 if
			// unavailable)
			quaternion = new float[4];
			for (int i = 0; i < quaternion.length; i++) {
				quaternion[i] = event.values[i];
			}
			headNode.setRawQuaternion(quaternion);
			headNode.logTest("HeadAlignedQ");
			sendTrackingPackageToWebSocket();
			break;
		default:
			break;
		}// switch case ends

	}

	private void sendTrackingPackageToWebSocket() {
		long currentTime = SystemClock.elapsedRealtime();
		// limit the sending rate to be less than 50Hz
		if (currentTime - timeStamp > 20) {
			if (webSocketIsConnected) {
				mWebSocketClient.send(makeBytesArrayFromQuaternions());
			}
			timeStamp = currentTime;
		}

	}

	/** make bytes array from quaternions for the websocket */
	private byte[] makeBytesArrayFromQuaternions() {
		ByteBuffer buffer = ByteBuffer.allocate(BYTES_ARRAY_LENGTH);
		buffer.put(headNode.getBytesArrayFromAlignedQuaternion());
		for (int i = 0; i < trackingNodes.length; i++) {
			buffer.put(trackingNodes[i].getBytesArrayFromAlignedQuaternion());
		}
		byte[] bytesArray = buffer.array();
		return bytesArray;
	}

	@Override
	public void onAccuracyChanged(Sensor sensor, int accuracy) {
	}

	@Override
	public void onCardboardTrigger() {
		// Always give user feedback
		Log.d(TAG, "onCardboardTrigger()");
		headNode.setRawAsInitial();
		for (int i = 0; i < trackingNodes.length; i++) {
			trackingNodes[i].setRawAsInitial();
		}

		mVibrator.vibrate(50);

	}

	/**
	 * Look for new input from the mByteQueue.
	 */
	private String update() {
		int bytesAvailable = theBt_work.getBytesAvailable();
		int bytesToRead = Math.min(bytesAvailable, mReceiveBuffer.length);
		int bytesRead = theBt_work.read(mReceiveBuffer, 0, bytesToRead);
		String stringRead = new String(mReceiveBuffer, 0, bytesRead);
		readQuaternionFromBuffer(bytesToRead);
		//Log.d(TAG, "stringRead: " + stringRead);
		return stringRead;
	}

	/**
	 * Read mReceiveBuffer and parse the quaternion data.
	 */
	private void readQuaternionFromBuffer(int bytesToRead) {
		for (int i = 0; i < bytesToRead; i++) {
			// a usable stop sign
			// Single frame structure:id,x,y,z,w,end
			if (mReceiveBuffer[i] == STOP_SIGN && i >= SINGLE_FRAME_LENGTH - 1) {
				int id = mReceiveBuffer[i - SINGLE_FRAME_LENGTH + 1] & 0xFF;
				if (id < NODES_COUNTS && id >= 0) {
					boolean unReadChange = trackingNodes[id].setValueByBytes(mReceiveBuffer[i - 4],
							mReceiveBuffer[i - 3], mReceiveBuffer[i - 2],
							mReceiveBuffer[i - 1]);

					//check data change and queue new data to Websocket send
					if(unReadChange){
						sendTrackingPackageToWebSocket();
						trackingNodes[id].logTest("bodyAlignedQ");
					}
				} else {
					if (LoggerConfig.ON) {
						Log.d(TAG, "Body tracker id<" + id
								+ "> out of range, ignored.");
					}
				}
			}
		}

	}

	public Handler mHandler = new Handler() {
		public void handleMessage(Message msg) {

			if (msg.what == BT_work.CONNECTION_SUCCESS) {
				// textViewBTConnection.setText("Success");
			} else if (msg.what == BT_work.MESSAGE_REV) {
				String string = update();
			}

		}
	};
}
