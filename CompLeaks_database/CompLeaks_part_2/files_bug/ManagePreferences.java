package net.everythingandroid.smspopup;

import android.content.Context;
import android.content.SharedPreferences;
import android.database.Cursor;
import android.database.SQLException;
import android.preference.PreferenceManager;

public class ManagePreferences {
  private String contactId;
  private Context context;
  private Cursor contactCursor;
  private boolean useDatabase;
  private SharedPreferences mPrefs;
  private static final String one = "1";
  private SmsPopupDbAdapter mDbAdapter;

  public ManagePreferences(Context _context, String _contactId) {
    contactId = _contactId;
    context = _context;
    useDatabase = false;

    if (Log.DEBUG) Log.v("contactId = " + contactId);
    long contactIdLong;
    try {
      contactIdLong = Long.parseLong(contactId);
    } catch (NumberFormatException e) {
      contactIdLong = 0;
    }

    if (contactIdLong > 0) {
      mDbAdapter = new SmsPopupDbAdapter(context);
      try {
        mDbAdapter.open(true); // Open database read-only
        contactCursor = mDbAdapter.fetchContactSettings(contactIdLong);
        if (contactCursor != null) {
          if (Log.DEBUG) Log.v("Contact found - using database");
          useDatabase = true;
        }
        mDbAdapter.close();
      } catch (SQLException e) {
        if (Log.DEBUG) Log.v("Error opening or creating database");
        useDatabase = false;
      }
    } else {
      if (Log.DEBUG) Log.v("Contact NOT found - using prefs");
    }

    mPrefs = PreferenceManager.getDefaultSharedPreferences(context);
  }

  public boolean getBoolean(int resPrefId, int resDefaultId, int dbColumnNum) {
    if (useDatabase) {
      return one.equals(contactCursor.getString(dbColumnNum));
    } else {
      return mPrefs.getBoolean(context.getString(resPrefId),
          Boolean.parseBoolean(context.getString(resDefaultId)));
    }
  }

  public boolean getBoolean(int resPrefId, int resDefaultId) {
    return mPrefs.getBoolean(context.getString(resPrefId),
        Boolean.parseBoolean(context.getString(resDefaultId)));
  }

  public String getString(int resPrefId, int resDefaultId, int dbColumnNum) {
    if (useDatabase) {
      return contactCursor.getString(dbColumnNum);
    } else {
      return mPrefs.getString(context.getString(resPrefId), context.getString(resDefaultId));
    }
  }

  public String getString(int resPrefId, int resDefaultId) {
    return mPrefs.getString(context.getString(resPrefId), context.getString(resDefaultId));
  }

  public String getString(int resPrefId, String defaultVal, int dbColumnNum) {
    if (useDatabase) {
      return contactCursor.getString(dbColumnNum);
    } else {
      return mPrefs.getString(context.getString(resPrefId), defaultVal);
    }
  }

  public void putString(int resPrefId, String newVal, String dbColumnNum) {
    if (useDatabase) {
      mDbAdapter.open(); // Open write
      mDbAdapter.updateContact(Long.valueOf(contactId), dbColumnNum, newVal);
      mDbAdapter.close();
    } else {
      SharedPreferences.Editor settings = mPrefs.edit();
      settings.putString(context.getString(resPrefId), newVal);
      settings.commit();
    }
  }

  public int getInt(String pref, int defaultVal) {
    return mPrefs.getInt(pref, defaultVal);
  }

  @Override
  protected void finalize() throws Throwable {
    close();
    super.finalize();
  }

  public void close() {
    if (contactCursor != null) {
      contactCursor.close();
    }
  }
}