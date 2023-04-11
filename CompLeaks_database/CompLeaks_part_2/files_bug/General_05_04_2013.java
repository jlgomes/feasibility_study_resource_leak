/*******************************************************************************
 * This file is part of RedReader.
 *
 * RedReader is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * RedReader is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with RedReader.  If not, see <http://www.gnu.org/licenses/>.
 ******************************************************************************/

package org.quantumbadger.redreader.common;

import android.content.Context;
import android.content.res.Configuration;
import android.net.ConnectivityManager;
import android.net.NetworkInfo;
import android.os.Handler;
import android.os.Looper;
import android.os.Message;
import android.util.TypedValue;
import android.widget.Toast;
import org.apache.http.StatusLine;
import org.holoeverywhere.app.AlertDialog;
import org.holoeverywhere.preference.SharedPreferences;
import org.quantumbadger.redreader.R;
import org.quantumbadger.redreader.activities.BugReportActivity;
import org.quantumbadger.redreader.cache.RequestFailureType;
import org.quantumbadger.redreader.reddit.APIResponseHandler;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;

public final class General_05_04_2013 {

	public static Message handlerMessage(int what, Object obj) {
		final Message msg = new Message();
		msg.what = what;
		msg.obj = obj;
		return msg;
	}

	public static void moveFile(final File src, final File dst) throws IOException {

		if(!src.renameTo(dst)) {

			final FileInputStream fis = new FileInputStream(dst);
			final FileOutputStream fos = new FileOutputStream(dst);

			final byte[] buf = new byte[32 * 1024];

			int bytesRead;
			while((bytesRead = fis.read(buf)) > 0) {
				fos.write(buf, 0, bytesRead);
			}

			fis.close();
			fos.close();

			if(!src.delete()) {
				src.deleteOnExit();
			}
		}
	}

	public static File getBestCacheDir(final Context context) {

		final File externalCacheDir = context.getExternalCacheDir();

		if(externalCacheDir != null) {
			return externalCacheDir;
		}

		return context.getCacheDir();
	}

	public static int dpToPixels(final Context context, final float dp) {
		return Math.round(TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, dp, context.getResources().getDisplayMetrics()));
	}

	public static void quickToast(final Context context, final int textRes) {
		quickToast(context, context.getString(textRes));
	}

	public static void quickToast(final Context context, final String text) {
		new Handler(Looper.getMainLooper()).post(new Runnable() {
			public void run() {
				Toast.makeText(context, text, Toast.LENGTH_SHORT).show();
			}
		});
	}

	public static boolean isTablet(final Context context, final SharedPreferences sharedPreferences) {

		final PrefsUtility.AppearanceTwopane pref = PrefsUtility.appearance_twopane(context, sharedPreferences);

		switch(pref) {
			case AUTO:
				return (context.getResources().getConfiguration().screenLayout &
						Configuration.SCREENLAYOUT_SIZE_MASK) ==
						Configuration.SCREENLAYOUT_SIZE_XLARGE;
			case NEVER:
				return false;
			case FORCE:
				return true;
			default:
				BugReportActivity.handleGlobalError(context, "Unknown AppearanceTwopane value " + pref.name());
				return false;
		}
	}

	public static boolean isConnectionWifi(final Context context){
		final ConnectivityManager cm = (ConnectivityManager)context.getSystemService(Context.CONNECTIVITY_SERVICE);
		final NetworkInfo info = cm.getNetworkInfo(ConnectivityManager.TYPE_WIFI);
		return info != null && info.isConnected();
	}

	public static RRError getGeneralErrorForFailure(Context context, RequestFailureType type, Throwable t, StatusLine status) {

		final int title, message;

		switch (type) {
			case CANCELLED:
				title = R.string.error_cancelled_title;
				message = R.string.error_cancelled_message;
				break;
			case PARSE:
				title = R.string.error_parse_title;
				message = R.string.error_parse_message;
				break;
			case CACHE_MISS:
				title = R.string.error_unexpected_cache_title;
				message = R.string.error_unexpected_cache_message;
				break;
			case STORAGE:
				title = R.string.error_unexpected_storage_title;
				message = R.string.error_unexpected_storage_message;
				break;
			case CONNECTION:
				// TODO check network and customise message
				title = R.string.error_connection_title;
				message = R.string.error_connection_message;
				break;
			case REQUEST:

				if(status != null) {
					switch (status.getStatusCode()) {
						case 403:
							title = R.string.error_403_title;
							message = R.string.error_403_message;
							break;
						case 404:
							title = R.string.error_404_title;
							message = R.string.error_404_message;
							break;
						case 502:
						case 503:
						case 504:
							title = R.string.error_redditdown_title;
							message = R.string.error_redditdown_message;
							break;
						default:
							title = R.string.error_unknown_api_title;
							message = R.string.error_unknown_api_message;
							break;
					}
				} else {
					title = R.string.error_unknown_api_title;
					message = R.string.error_unknown_api_message;
				}

				break;

			default:
				title = R.string.error_unknown_title;
				message = R.string.error_unknown_message;
				break;
		}

		return new RRError(context.getString(title), context.getString(message), t, status);
	}

	public static RRError getGeneralErrorForFailure(Context context, final APIResponseHandler.APIFailureType type) {

		final int title, message;

		switch(type) {

			case INVALID_USER:
				title = R.string.error_403_title;
				message = R.string.error_403_title;
				break;

			default:
				title = R.string.error_unknown_api_title;
				message = R.string.error_unknown_api_message;
				break;
		}

		return new RRError(context.getString(title), context.getString(message));
	}

	// TODO add button to show more detail
	public static void showResultDialog(Context context, RRError error) {
		final AlertDialog.Builder alertBuilder = new AlertDialog.Builder(context);
		alertBuilder.setNeutralButton(R.string.dialog_close, null);
		alertBuilder.setTitle(error.title);
		alertBuilder.setMessage(error.message);
		alertBuilder.create().show();
	}
}