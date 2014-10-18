package com.camera.simplemjpeg.bluetooth;

import java.util.ArrayList;
import java.util.List;

import android.app.Activity;
import android.app.ProgressDialog;
import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.os.Bundle;
import android.util.Log;
import android.view.KeyEvent;
import android.view.MenuInflater;
import android.view.MenuItem;
import android.view.View;
import android.widget.AdapterView;
import android.widget.AdapterView.OnItemClickListener;
import android.widget.ArrayAdapter;
import android.widget.ListView;

import com.camera.simplemjpeg.R;

public class DiscoveryActivity  extends Activity implements OnItemClickListener
{
	ArrayAdapter<String> listAdapter;
	ListView listView;
	private static final String TAG="DiscoveryActivity";
	/** get default BT Adapter*/
	private BluetoothAdapter _bluetooth = BluetoothAdapter.getDefaultAdapter();
	
	/**List to display discovered BT devices*/
	private List<BluetoothDevice> _devices = new ArrayList<BluetoothDevice>();
	public static String EXTRA_DEVICE_ADDRESS = "device_address";

	ProgressDialog SearchDialog;
	/** boolean: if discovery is Finished */
	private volatile boolean _discoveryFinished=false;
	private Runnable _discoveryWorkder = new Runnable() {
		public void run() 
		{
			/* start scanning BT devices */
			_bluetooth.startDiscovery();
			Log.v(TAG, "Runnable--startDiscovery");
			for (;;)//loop until discovery is Finished 
			{
				if ((_discoveryFinished)||(!SearchDialog.isShowing()))
				{
					Log.v(TAG, "Runnable--cancelDiscovery");
					SearchDialog.cancel();
					_bluetooth.cancelDiscovery();
					break;
				}
				try 
				{
					Thread.sleep(20);
				} 
				catch (InterruptedException e){}
			}
		}
	};
	/**Receiver Called when discovered a BT device */
	private BroadcastReceiver _foundReceiver = new BroadcastReceiver() {
		public void onReceive(Context context, Intent intent) {
			/* get the discovery result from intent*/
			BluetoothDevice device = intent
					.getParcelableExtra(BluetoothDevice.EXTRA_DEVICE);
			/* add the search result to the list */
			_devices.add(device);
			/* display the discovered device in list */
			showDevices(device);
		}
	};
	/**Receiver Called when Finished discovering BT devices */
	private BroadcastReceiver _discoveryReceiver = new BroadcastReceiver() {

		@Override
		public void onReceive(Context context, Intent intent) 
		{
			/* unregister the two Receivers */
			unregisterReceiver(_foundReceiver);
			unregisterReceiver(this);
			_discoveryFinished = true;
			Log.d(TAG, "unregisterReceiver");
		}
	};
	 @Override
	public void onCreate(Bundle savedInstanceState)
	{
		super.onCreate(savedInstanceState);
		Log.d(TAG, "onCreate");
		setContentView(R.layout.discovery);
		listView = (ListView) findViewById(R.id.lvDiscovery);
		listView.setOnItemClickListener(this);
		listAdapter = new ArrayAdapter<String>(this,
				android.R.layout.simple_list_item_1, 0);
		listView.setAdapter(listAdapter);
		/* if BT is not enabled */
		if (!_bluetooth.isEnabled())
		{
			Log.d(TAG, "BT not enabled-->finish in onCreate");
			finish();
			return;
		}		
		regReceivers();	
		/* Display the Progress dialog--searching for devices */
        SearchDialog = new ProgressDialog (DiscoveryActivity.this);
        SearchDialog.setMessage("Searching for Devices");
        SearchDialog.show();
        new Thread(_discoveryWorkder).start();
	}
	 /** register the two Receivers */
	private void regReceivers() {
		// TODO Auto-generated method stub
		IntentFilter discoveryFilter = new IntentFilter(BluetoothAdapter.ACTION_DISCOVERY_FINISHED);
		registerReceiver(_discoveryReceiver, discoveryFilter);
		IntentFilter foundFilter = new IntentFilter(BluetoothDevice.ACTION_FOUND);
		registerReceiver(_foundReceiver, foundFilter);
	}

	/**display the discovered device in the list
	 * @param device */
	protected void showDevices(BluetoothDevice device)
	{

		StringBuilder b = new StringBuilder();
		b.append(device.getAddress());
		b.append('\n');
		b.append(device.getName());
		String s = b.toString();
		listAdapter.add(s);

	}
	
	@Override
	public boolean onKeyDown(int keyCode, KeyEvent event) {
		// TODO Auto-generated method stub
		 switch (keyCode)
		 {
		 case KeyEvent.KEYCODE_BACK://the back is pressed

			if (_discoveryFinished) {				
				setResult(RESULT_CANCELED);
				Log.d(TAG, "user want--Exit");
				finish();
			} 
		 }
		return false;
	}

	@Override
	public boolean onCreateOptionsMenu(android.view.Menu menu) {
		// TODO Auto-generated method stub
		super.onCreateOptionsMenu(menu);
		MenuInflater blowUp = getMenuInflater();
		blowUp.inflate(R.menu.discovery_menu, menu);
		return true;
	}
	
	@Override
	public boolean onOptionsItemSelected(MenuItem item) {
		// TODO Auto-generated method stub
		switch (item.getItemId()) {
		case R.id.Rescan:
			//clear list
			_devices.clear();
			listAdapter.clear();
			regReceivers();	//register the two Receivers
			SearchDialog.setMessage("Rescanning Devices");
			SearchDialog.show();
			_discoveryFinished=false;
			new Thread(_discoveryWorkder).start();
			break;
		case R.id.Default:

			break;
		}
		return false;
	}

	public void onItemClick(AdapterView<?> arg0, View arg1, int arg2, long arg3) {
		// TODO Auto-generated method stub
		Intent result = new Intent();
		result.putExtra(BluetoothDevice.EXTRA_DEVICE, _devices.get(arg2));
		setResult(RESULT_OK, result);
		finish();
		Log.d(TAG, "Return device result");
	}
}

