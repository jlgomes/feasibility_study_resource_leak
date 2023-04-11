package com.dougkeen.bart;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.InputStream;

import org.apache.commons.io.IOUtils;
import org.apache.commons.lang3.ObjectUtils;

import android.app.AlarmManager;
import android.app.Application;
import android.content.Context;
import android.media.MediaPlayer;
import android.os.Parcel;
import android.util.Log;

import com.dougkeen.bart.model.Constants;
import com.dougkeen.bart.model.Departure;

public class BartRunnerApplication_09_10_2012 extends Application {
	private static final int FIVE_MINUTES = 5 * 60 * 1000;

	private static final String CACHE_FILE_NAME = "lastBoardedDeparture";

	private Departure mBoardedDeparture;

	private boolean mPlayAlarmRingtone;

	private boolean mAlarmSounding;

	private MediaPlayer mAlarmMediaPlayer;

	private static Context context;

	@Override
	public void onCreate() {
		super.onCreate();
		context = getApplicationContext();
	}

	public static Context getAppContext() {
		return context;
	}

	public boolean shouldPlayAlarmRingtone() {
		return mPlayAlarmRingtone;
	}

	public void setPlayAlarmRingtone(boolean playAlarmRingtone) {
		this.mPlayAlarmRingtone = playAlarmRingtone;
	}

	public Departure getBoardedDeparture() {
		if (mBoardedDeparture == null) {
			// see if there's a saved one
			File cachedDepartureFile = new File(getCacheDir(), CACHE_FILE_NAME);
			if (cachedDepartureFile.exists()) {
				InputStream inputStream = null;
				try {
					inputStream = new FileInputStream(cachedDepartureFile);
					byte[] byteArray = IOUtils.toByteArray(inputStream);
					Parcel parcel = Parcel.obtain();
					parcel.unmarshall(byteArray, 0, byteArray.length);
					parcel.setDataPosition(0);
					Departure lastBoardedDeparture = Departure.CREATOR
							.createFromParcel(parcel);

					/*
					 * Check if the cached one is relatively recent. If so,
					 * restore that to the application context
					 */
					long now = System.currentTimeMillis();
					if (lastBoardedDeparture.getEstimatedArrivalTime() >= now
							- FIVE_MINUTES
							|| lastBoardedDeparture.getMeanEstimate() >= now
									- 2 * FIVE_MINUTES) {
						mBoardedDeparture = lastBoardedDeparture;
					}
				} catch (Exception e) {
					Log.w(Constants.TAG,
							"Couldn't read or unmarshal lastBoardedDeparture file",
							e);
					try {
						cachedDepartureFile.delete();
					} catch (SecurityException anotherException) {
						Log.w(Constants.TAG,
								"Couldn't delete lastBoardedDeparture file",
								anotherException);
					}
				} finally {
					IOUtils.closeQuietly(inputStream);
				}
			}
		}
		if (mBoardedDeparture != null && mBoardedDeparture.hasExpired()) {
			setBoardedDeparture(null);
		}
		return mBoardedDeparture;
	}

	public void setBoardedDeparture(Departure boardedDeparture) {
		if (!ObjectUtils.equals(boardedDeparture, mBoardedDeparture)
				|| ObjectUtils.compare(mBoardedDeparture, boardedDeparture) != 0) {
			if (this.mBoardedDeparture != null) {
				this.mBoardedDeparture.getAlarmLeadTimeMinutesObservable()
						.unregisterAllObservers();
				this.mBoardedDeparture.getAlarmPendingObservable()
						.unregisterAllObservers();

				// Cancel any pending alarms for the current departure
				if (this.mBoardedDeparture.isAlarmPending()) {
					this.mBoardedDeparture
							.cancelAlarm(
									this,
									(AlarmManager) getSystemService(Context.ALARM_SERVICE));
				}
			}

			this.mBoardedDeparture = boardedDeparture;

			File cachedDepartureFile = new File(getCacheDir(), CACHE_FILE_NAME);
			if (mBoardedDeparture == null) {
				try {
					cachedDepartureFile.delete();
				} catch (SecurityException anotherException) {
					Log.w(Constants.TAG,
							"Couldn't delete lastBoardedDeparture file",
							anotherException);
				}
			} else {
				FileOutputStream fileOutputStream = null;
				try {
					fileOutputStream = new FileOutputStream(cachedDepartureFile);
					Parcel parcel = Parcel.obtain();
					mBoardedDeparture.writeToParcel(parcel, 0);
					fileOutputStream.write(parcel.marshall());
				} catch (Exception e) {
					Log.w(Constants.TAG,
							"Couldn't write last boarded departure cache file",
							e);
				} finally {
					IOUtils.closeQuietly(fileOutputStream);
				}
			}
		}
	}

	public boolean isAlarmSounding() {
		return mAlarmSounding;
	}

	public void setAlarmSounding(boolean alarmSounding) {
		this.mAlarmSounding = alarmSounding;
	}

	public MediaPlayer getAlarmMediaPlayer() {
		return mAlarmMediaPlayer;
	}

	public void setAlarmMediaPlayer(MediaPlayer alarmMediaPlayer) {
		this.mAlarmMediaPlayer = alarmMediaPlayer;
	}
}