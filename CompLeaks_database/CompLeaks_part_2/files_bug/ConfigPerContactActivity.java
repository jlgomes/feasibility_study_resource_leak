package net.everythingandroid.smspopup;

import java.util.List;

import net.everythingandroid.smspopup.preferences.CustomLEDColorListPreference;
import net.everythingandroid.smspopup.preferences.CustomLEDPatternListPreference;
import net.everythingandroid.smspopup.preferences.CustomVibrateListPreference;
import net.everythingandroid.smspopup.preferences.TestNotificationDialogPreference;
import android.content.Intent;
import android.content.SharedPreferences;
import android.database.Cursor;
import android.media.Ringtone;
import android.media.RingtoneManager;
import android.net.Uri;
import android.os.Bundle;
import android.preference.CheckBoxPreference;
import android.preference.Preference;
import android.preference.PreferenceActivity;
import android.preference.PreferenceManager;
import android.preference.RingtonePreference;
import android.preference.Preference.OnPreferenceChangeListener;
import android.provider.Contacts;
import android.view.Menu;
import android.view.MenuItem;

public class ConfigPerContactActivity extends PreferenceActivity {
  private Uri contactUri = null;
  private long contactId = 0;
  private String contactIdString = null;
  private SmsPopupDbAdapter mDbAdapter;
  private static final int REQ_CODE_CHOOSE_CONTACT = 0;
  private static final int MENU_SAVE_ID = Menu.FIRST;
  private static final int MENU_DELETE_ID = Menu.FIRST + 1;
  public static final String EXTRA_CONTACT_ID =
    "net.everythingandroid.smspopuppro.EXTRA_CONTACT_ID";

  @Override
  public void onCreate(Bundle savedInstanceState) {
    super.onCreate(savedInstanceState);
    Log.v("SMSPopupConfigPerContactActivity: onCreate()");
    // addPreferencesFromResource(R.xml.configcontact);

    /*
     * Create database object
     */
    mDbAdapter = new SmsPopupDbAdapter(getApplicationContext());
  }

  @Override
  protected void onResume() {
    super.onResume();
    Log.v("SMSPopupConfigPerContactActivity: onResume()");
    createPreferences();
  }

  @Override
  protected void onPause() {
    super.onPause();
    Log.v("SMSPopupConfigPerContactActivity: onPause()");
    // mDbAdapter.close();
  }

  @Override
  protected void onStop() {
    super.onStop();
    Log.v("SMSPopupConfigPerContactActivity: onStop()");
  }

  @Override
  protected void onDestroy() {
    super.onDestroy();
    Log.v("SMSPopupConfigPerContactActivity: onDestroy()");
    mDbAdapter.close();
  }

  private void selectContact() {
    // Intent i = new Intent(Intent.ACTION_PICK,
    // Uri.parse("content://contacts/people/with_phones_filter/*"));
    // Intent i = new Intent(Intent.ACTION_PICK,
    // Contacts.People.CONTENT_URI.buildUpon().appendEncodedPath("with_phones_filter").build());
    // Contacts.Groups.
    // new Intent(Intent.ACTION_PICK, Contacts.Phones.CONTENT_URI)
    // new Intent(Intent.ACTION_PICK,
    // Uri.withAppendedPath(Contacts.People.CONTENT_URI,"with_phones_filter"))
    // new Intent(Intent.ACTION_PICK,
    // Uri.withAppendedPath(Contacts.Phones.CONTENT_FILTER_URL, "m"))
    // .setType("vnd.android.cursor.dir/phone")
    // .setType("vnd.android.cursor.dir/person")

    // TODO: So ideally we just want to show contacts with phone numbers here
    // but I couldn't
    // work out a way to filter the results using an ACTION_PICK intent
    startActivityForResult(new Intent(Intent.ACTION_PICK, Contacts.People.CONTENT_URI),
        REQ_CODE_CHOOSE_CONTACT);
  }

  @Override
  protected void onActivityResult(int requestCode, int resultCode, Intent data) {
    super.onActivityResult(requestCode, resultCode, data);

    switch (requestCode) {
      case REQ_CODE_CHOOSE_CONTACT:
        if (resultCode == -1) { // Success, contact chosen
          contactUri = data.getData();
          List<String> list = contactUri.getPathSegments();
          Log.v("onActivityResult() - " + data.getDataString() + ", " + list.get(list.size() - 1));
          contactId = Long.parseLong(list.get(list.size() - 1));
          getIntent().putExtra(EXTRA_CONTACT_ID, contactId);
          mDbAdapter.open();
          mDbAdapter.createContact(contactId);
          mDbAdapter.updateContactSummary(contactId);
          mDbAdapter.close();
        } else { // Failed, contact not chosen
          finish();
        }
        break;
    }
  }

  private void createPreferences() {
    /*
     * Ensure contactId was passed, if not, fire up an intent to choose a
     * contact (add)
     */
    contactId = getIntent().getLongExtra(EXTRA_CONTACT_ID, 0);

    if (contactId == 0) {
      /*
       * This will start the "pick contact" intent
       */
      selectContact();
    } else {
      Log.v("contactId = " + contactId);
      contactIdString = String.valueOf(contactId);

      /*
       * Fetch the current user settings from the database
       */
      mDbAdapter.open(true);
      Cursor contact = mDbAdapter.fetchContactSettings(contactId);
      mDbAdapter.close();
      /*
       * If for some reason the contact is not found, get out
       */
      if (contact == null) {
        Log.v("Contact not found???");
        finish();
      }

      /*
       * Retrieve preferences from database
       */
      retrievePreferences(contact);

      /*
       * Add preference layout from XML
       */
      addPreferencesFromResource(R.xml.configcontact);

      /*
       * Customize Activity title + main notif enabled preference summaries
       */
      // TODO: move and parameterize strings to resource file
      String contactName = contact.getString(SmsPopupDbAdapter.KEY_CONTACT_NAME_NUM);
      setTitle("Notifications for " + contactName);

      CheckBoxPreference enabledPref =
        (CheckBoxPreference) findPreference(getString(R.string.c_pref_notif_enabled_key));
      enabledPref.setSummaryOn(getString(R.string.pref_notif_enabled_summaryon) + " for "
          + contactName);
      enabledPref.setSummaryOff(getString(R.string.pref_notif_enabled_summaryoff) + " for "
          + contactName);

      /*
       * Main Prefs
       */
      CheckBoxPreference enableNotifPref =
        (CheckBoxPreference) findPreference(getString(R.string.c_pref_notif_enabled_key));
      enableNotifPref.setOnPreferenceChangeListener(onPrefChangeListener);

      RingtonePreference ringtonePref =
        (RingtonePreference) findPreference(getString(R.string.c_pref_notif_sound_key));
      ringtonePref.setOnPreferenceChangeListener(onPrefChangeListener);
      Uri ringtoneUri = Uri.parse(contact.getString(SmsPopupDbAdapter.KEY_RINGTONE_NUM));
      Ringtone mRingtone = RingtoneManager.getRingtone(this, ringtoneUri);
      ringtonePref.setSummary(mRingtone.getTitle(this));

      TestNotificationDialogPreference testPref =
        (TestNotificationDialogPreference) findPreference(getString(R.string.c_pref_notif_test_key));
      testPref.setContactId(contact.getLong(SmsPopupDbAdapter.KEY_CONTACT_ID_NUM));

      /*
       * Vibrate Prefs
       */
      CheckBoxPreference enableVibratePref =
        (CheckBoxPreference) findPreference(getString(R.string.c_pref_vibrate_key));
      enableVibratePref.setOnPreferenceChangeListener(onPrefChangeListener);

      CustomVibrateListPreference vibratePatternPref =
        (CustomVibrateListPreference) findPreference(getString(R.string.c_pref_vibrate_pattern_key));
      vibratePatternPref.setOnPreferenceChangeListener(onPrefChangeListener);
      vibratePatternPref.setContactId(contactIdString);

      /*
       * LED Prefs
       */
      CheckBoxPreference enableLEDPref =
        (CheckBoxPreference) findPreference(getString(R.string.c_pref_flashled_key));
      enableLEDPref.setOnPreferenceChangeListener(onPrefChangeListener);

      CustomLEDColorListPreference ledColorPref =
        (CustomLEDColorListPreference) findPreference(getString(R.string.c_pref_flashled_color_key));
      ledColorPref.setOnPreferenceChangeListener(onPrefChangeListener);
      ledColorPref.setContactId(contactIdString);

      CustomLEDPatternListPreference ledPatternPref =
        (CustomLEDPatternListPreference) findPreference(getString(R.string.c_pref_flashled_pattern_key));
      ledPatternPref.setOnPreferenceChangeListener(onPrefChangeListener);
      ledPatternPref.setContactId(contactIdString);

      /*
       * Close up database cursor
       */
      contact.close();
    }
  }

  /*
   * All preferences will trigger this when changed
   */
  private OnPreferenceChangeListener onPrefChangeListener = new OnPreferenceChangeListener() {

    public boolean onPreferenceChange(Preference preference, Object newValue) {
      Log.v("onPreferenceChange - " + newValue);
      return storePreferences(preference, newValue);
    }
  };

  /*
   * Store a single preference back to the database
   */
  private boolean storePreferences(Preference preference, Object newValue) {
    Log.v("storePrefs()");
    String key = preference.getKey();
    String column = null;

    if (key.equals(getString(R.string.c_pref_notif_enabled_key))) {
      column = SmsPopupDbAdapter.KEY_ENABLED;
    } else if (key.equals(getString(R.string.c_pref_notif_sound_key))) {
      column = SmsPopupDbAdapter.KEY_RINGTONE;
    } else if (key.equals(getString(R.string.c_pref_vibrate_key))) {
      column = SmsPopupDbAdapter.KEY_VIBRATE_ENABLED;
    } else if (key.equals(getString(R.string.c_pref_vibrate_pattern_key))) {
      column = SmsPopupDbAdapter.KEY_VIBRATE_PATTERN;
    } else if (key.equals(getString(R.string.c_pref_vibrate_pattern_custom_key))) {
      column = SmsPopupDbAdapter.KEY_VIBRATE_PATTERN_CUSTOM;
    } else if (key.equals(getString(R.string.c_pref_flashled_key))) {
      column = SmsPopupDbAdapter.KEY_LED_ENABLED;
    } else if (key.equals(getString(R.string.c_pref_flashled_color_key))) {
      column = SmsPopupDbAdapter.KEY_LED_COLOR;
    } else if (key.equals(getString(R.string.c_pref_flashled_color_custom_key))) {
      column = SmsPopupDbAdapter.KEY_LED_COLOR_CUSTOM;
    } else if (key.equals(getString(R.string.c_pref_flashled_pattern_key))) {
      column = SmsPopupDbAdapter.KEY_LED_PATTERN;
    } else if (key.equals(getString(R.string.c_pref_flashled_pattern_custom_key))) {
      column = SmsPopupDbAdapter.KEY_LED_PATTERN_CUSTOM;
    } else {
      return false;
    }

    mDbAdapter.open();
    boolean success = mDbAdapter.updateContact(contactId, column, newValue);
    mDbAdapter.updateContactSummary(contactId);
    mDbAdapter.close();

    return success;
  }

  /*
   * Retrieve all preferences from the database into preferences
   */
  private void retrievePreferences(Cursor c) {
    String one = "1";
    Log.v("retrievePrefs()");
    SharedPreferences myPrefs = PreferenceManager.getDefaultSharedPreferences(this);
    Log.v("ringtone = " + c.getString(SmsPopupDbAdapter.KEY_RINGTONE_NUM));
    Log.v("enabled = " + c.getString(SmsPopupDbAdapter.KEY_ENABLED_NUM));
    SharedPreferences.Editor editor = myPrefs.edit();
    // editor.putBoolean(getString(R.string.c_pref_notif_enabled_key),
    // Boolean.parseBoolean(c.getString(ContactsDbAdapter.KEY_ENABLED_NUM)));

    /*
     * Fetch Main Prefs
     */
    editor.putBoolean(getString(R.string.c_pref_notif_enabled_key), one.equals(c
        .getString(SmsPopupDbAdapter.KEY_ENABLED_NUM)));
    editor.putString(getString(R.string.c_pref_notif_sound_key), c
        .getString(SmsPopupDbAdapter.KEY_RINGTONE_NUM));

    /*
     * Fetch Vibrate prefs
     */
    editor.putBoolean(getString(R.string.c_pref_vibrate_key), one.equals(c
        .getString(SmsPopupDbAdapter.KEY_VIBRATE_ENABLED_NUM)));
    editor.putString(getString(R.string.c_pref_vibrate_pattern_key), c
        .getString(SmsPopupDbAdapter.KEY_VIBRATE_PATTERN_NUM));
    editor.putString(getString(R.string.c_pref_vibrate_pattern_custom_key), c
        .getString(SmsPopupDbAdapter.KEY_VIBRATE_PATTERN_CUSTOM_NUM));

    /*
     * Fetch LED prefs
     */
    editor.putBoolean(getString(R.string.c_pref_flashled_key), one.equals(c
        .getString(SmsPopupDbAdapter.KEY_LED_ENABLED_NUM)));
    editor.putString(getString(R.string.c_pref_flashled_color_key), c
        .getString(SmsPopupDbAdapter.KEY_LED_COLOR_NUM));
    editor.putString(getString(R.string.c_pref_flashled_color_custom_key), c
        .getString(SmsPopupDbAdapter.KEY_LED_COLOR_NUM_CUSTOM));
    editor.putString(getString(R.string.c_pref_flashled_pattern_key), c
        .getString(SmsPopupDbAdapter.KEY_LED_PATTERN_NUM));
    editor.putString(getString(R.string.c_pref_flashled_pattern_custom_key), c
        .getString(SmsPopupDbAdapter.KEY_LED_PATTERN_NUM_CUSTOM));

    // Commit prefs
    editor.commit();
  }

  /*
   * (non-Javadoc)
   * 
   * @see android.app.Activity#onCreateOptionsMenu(android.view.Menu)
   */
  @Override
  public boolean onCreateOptionsMenu(Menu menu) {

    MenuItem saveItem =
      menu
      .add(Menu.NONE, MENU_SAVE_ID, Menu.NONE, getString(R.string.contact_customization_save));
    MenuItem deleteItem =
      menu.add(Menu.NONE, MENU_DELETE_ID, Menu.NONE,
          getString(R.string.contact_customization_remove));

    saveItem.setIcon(android.R.drawable.ic_menu_save);
    deleteItem.setIcon(android.R.drawable.ic_menu_delete);

    return true;
  }

  /*
   * (non-Javadoc)
   * 
   * @see android.app.Activity#onOptionsItemSelected(android.view.MenuItem)
   */
  @Override
  public boolean onOptionsItemSelected(MenuItem item) {
    switch (item.getItemId()) {
      case MENU_SAVE_ID:
        finish();
        return true;
      case MENU_DELETE_ID:
        mDbAdapter.open();
        mDbAdapter.deleteContact(contactId);
        mDbAdapter.close();
        finish();
        return true;
    }
    return false;
  }

}