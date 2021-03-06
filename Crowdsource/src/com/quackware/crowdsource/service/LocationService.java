/*******************************************************************************
 * Copyright (c) 2012 Curtis Larson (QuackWare).
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the GNU Public License v3.0
 * which accompanies this distribution, and is available at
 * http://www.gnu.org/licenses/gpl.html
 ******************************************************************************/
package com.quackware.crowdsource.service;

import android.app.ProgressDialog;
import android.app.Service;
import android.content.Intent;
import android.content.SharedPreferences;
import android.location.Location;
import android.os.AsyncTask;
import android.os.Bundle;
import android.os.Handler;
import android.os.IBinder;
import android.os.Message;
import android.os.Messenger;
import android.os.RemoteException;
import android.preference.PreferenceManager;
import android.util.Log;
import android.widget.Toast;

import com.quackware.crowdsource.LocationFinder;
import com.quackware.crowdsource.MyApplication;
import com.quackware.crowdsource.R;
import com.quackware.crowdsource.LocationFinder.LocationResult;
import com.quackware.crowdsource.ui.CrowdSource;
import com.quackware.crowdsource.util.C;

public class LocationService extends Service {

	public static final int MSG_UPDATE_LOCATION = 1;
	public static final int MSG_LAST_KNOWN_LOCATION = 2;
	public static final int TERMINATE_SERVICE = 9;

	private static final String TAG = "LocationService";
	
	private LocationFinder _lf;

	private Messenger _replyTo;

	private static LocationService instance;

	private boolean _isTaskRunning = false;

	final Messenger _messenger = new Messenger(new IncomingHandler());

	@Override
	public void onCreate() {
		super.onCreate();
		Log.i(TAG, "onCreate called");
		instance = this;
		// _handler.sendEmptyMessage(MSG_UPDATE_LOCATION);
	}

	@Override
	public void onDestroy() {
		super.onDestroy();
		Log.i(TAG, "onDestroy called");
		_lf.stopGettingLocation();
	}

	@Override
	public IBinder onBind(Intent intent) {
		Log.i(TAG, "onBind called, returning _binder");
		return _messenger.getBinder();
	}

	class IncomingHandler extends Handler { // Handler of incoming messages from
											// clients.
		@Override
		public void handleMessage(Message msg) {
			Log.i(TAG, "Handling a message with id " + msg.what);
			switch (msg.what) {

			case MSG_UPDATE_LOCATION:
				if (msg.replyTo != null) {
					_replyTo = msg.replyTo;
				}

				if (!_isTaskRunning
						&& !((MyApplication) LocationService.this
								.getApplication()).getServerUtil()
								.isAnonymous()) {
					runLocationFinder(true);
					// new LocationTask().execute();
				}
				// Just send the same message again, minutes will have to be a
				// number retrieved from settings.

				SharedPreferences preferences = PreferenceManager
						.getDefaultSharedPreferences(getApplicationContext());
				int minutes;
				try {
					minutes = Integer.parseInt(preferences.getString(
							"editTextLocationInterval", "10"));
				} catch (Exception ex) {
					Log.e(TAG, "Error retriving minutes preference");
					minutes = 10;
				}
				Message newMsg = new Message();
				newMsg.what = MSG_UPDATE_LOCATION;
				newMsg.replyTo = _replyTo;
				sendMessageDelayed(newMsg, minutes * 60 * 1000);
				break;
			case MSG_LAST_KNOWN_LOCATION:
				runLocationFinder(false);
				break;
			case TERMINATE_SERVICE:
				_lf.stopGettingLocation();
				stopSelf();
				break;
			}
		}
	};

	/*
	 * public class LocalBinder extends Binder { public LocationService
	 * getService() { Log.i(TAG,"LocationService getService() called"); return
	 * LocationService.instance; } }
	 */

	// TODO Add some sort of limitation on the first time they retrieve location
	// so that they won't be able to login without retrieving one.
	// We can try coupling it with connecting to server.
	private void runLocationFinder(boolean getRealLocation) {
		_isTaskRunning = true;
		Log.i(TAG, "getLocation called");
		_lf = new LocationFinder();

		if(C.PREV_LOC)
		{
			getRealLocation = false;
		}
		// NOTE, WHEN ACTIVITY IS DESTROYED WE WANT THE BOOLEAN
		// TO BE FALSE.
		boolean canFind = _lf.getLocation(getApplicationContext(),
				locationResult, getRealLocation);
		if (!canFind) {
			// Show some error to the user that we cannot find their location so
			// they cannot participate in a chat.
			Toast.makeText(getApplicationContext(),
					getString(R.string.ERROR_LOCATION), Toast.LENGTH_SHORT)
					.show();
		}
	}

	public LocationResult locationResult = new LocationResult() {
		@Override
		public void gotLocation(Location location) {
			Log.i(TAG, "gotLocation called");
			if (location == null) {
				// Something went wrong
				Log.e("Location",
						"Unable to find location, location returned null");
				Toast.makeText(getApplicationContext(),
						getString(R.string.ERROR_LOCATION), Toast.LENGTH_SHORT)
						.show();
			} else {
				Bundle data = new Bundle();
				data.putParcelable("location", location);
				Message mes = new Message();
				mes.what = MSG_UPDATE_LOCATION;
				mes.setData(data);
				try {
					_replyTo.send(mes);
				} catch (RemoteException e) {
					_replyTo = null;
					e.printStackTrace();
				}
			}
			_isTaskRunning = false;
			/*
			 * if(_locationSpinner != null) { _locationSpinner.dismiss();
			 * _locationSpinner = null; }
			 */
		};

	};

	public class LocationTask extends AsyncTask<Object, Object, Object> {
		// private CrowdSource activity;
		private ProgressDialog _locationSpinner;

		public LocationResult locationResult = new LocationResult() {
			@Override
			public void gotLocation(Location location) {
				Log.i(TAG, "gotLocation called");
				if (location == null) {
					// Something went wrong
					Log.e("Location",
							"Unable to find location, location returned null");
					Toast.makeText(getApplicationContext(),
							getString(R.string.ERROR_LOCATION),
							Toast.LENGTH_SHORT).show();
				} else {
					Bundle data = new Bundle();
					data.putParcelable("location", location);
					Message mes = new Message();
					mes.setData(data);
					try {
						_replyTo.send(mes);
					} catch (RemoteException e) {
						_replyTo = null;
						e.printStackTrace();
					}
				}
				/*
				 * if(_locationSpinner != null) { _locationSpinner.dismiss();
				 * _locationSpinner = null; }
				 */
			};

		};

		public LocationTask() {
			// attach(cs);
		}

		public void attach(CrowdSource cs) {
			// activity = cs;
		}

		public void detatch() {
			// activity = null;
		}

		@Override
		protected Object doInBackground(Object... params) {
			getLocation();
			return null;
		}

		private void getLocation() {
			Log.i(TAG, "getLocation called");
			LocationFinder lf = new LocationFinder();

			// NOTE, WHEN ACTIVITY IS DESTROYED WE WANT THE BOOLEAN
			// TO BE FALSE.
			boolean canFind = lf.getLocation(getApplicationContext(),
					locationResult, true);
			if (!canFind) {
				// Show some error to the user that we cannot find their
				// location so they cannot participate in a chat.
				Toast.makeText(getApplicationContext(),
						getString(R.string.ERROR_LOCATION), Toast.LENGTH_SHORT)
						.show();
			}
		}

		protected void onPostExecute(Boolean result) {
			_isTaskRunning = false;
			/*
			 * if(_locationSpinner != null) { _locationSpinner.dismiss();
			 * _locationSpinner = null; }
			 */
			// activity.removeLocationTask();
		}

		@Override
		protected void onPreExecute() {
			_isTaskRunning = true;
			/*
			 * _locationSpinner = new ProgressDialog(CrowdSource.this);
			 * _locationSpinner.setProgressStyle(ProgressDialog.STYLE_SPINNER);
			 * _locationSpinner.setMessage(getString(R.string.creatingAccount));
			 * _locationSpinner.setCancelable(false);
			 */
		}

	}

	public void startLocationFinder() {

	}

}
