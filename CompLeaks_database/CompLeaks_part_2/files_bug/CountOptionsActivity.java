package com.knirirr.beecount;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.util.Log;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewManager;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.Toast;

import com.knirirr.beecount.database.Alert;
import com.knirirr.beecount.database.AlertDataSource;
import com.knirirr.beecount.database.Count;
import com.knirirr.beecount.database.CountDataSource;
import com.knirirr.beecount.widgets.AddAlertWidget;
import com.knirirr.beecount.widgets.AlertCreateWidget;
import com.knirirr.beecount.widgets.OptionsWidget;

import org.apache.commons.lang3.StringUtils;

import java.util.ArrayList;
import java.util.List;


public class CountOptionsActivity extends Activity implements SharedPreferences.OnSharedPreferenceChangeListener
{
  private static String TAG = "BeeCountCountOptionsActivity";
  BeeCountApplication beeCountApplication;
  SharedPreferences prefs;

  private Count count;
  private long count_id;
  private List<Alert> alerts;
  private CountDataSource countDataSource;
  private AlertDataSource alertDataSource;
  private AlertDialog.Builder are_you_sure;
  private View markedForDelete;
  private long deleteAnAlert;

  LinearLayout static_widget_area;
  LinearLayout dynamic_widget_area;
  OptionsWidget ar_value_widget;
  OptionsWidget ar_level_widget;
  OptionsWidget curr_val_widget;
  AddAlertWidget aa_widget;

  @Override
  protected void onCreate(Bundle savedInstanceState)
  {
    super.onCreate(savedInstanceState);
    setContentView(R.layout.activity_count_options);

    beeCountApplication = (BeeCountApplication) getApplication();
    prefs = BeeCountApplication.getPrefs();
    prefs.registerOnSharedPreferenceChangeListener(this);

    ScrollView counting_screen = (ScrollView) findViewById(R.id.count_options);
    counting_screen.setBackgroundDrawable(beeCountApplication.getBackground());

    static_widget_area = (LinearLayout) findViewById(R.id.static_widget_area);
    dynamic_widget_area = (LinearLayout) findViewById(R.id.dynamic_widget_area);

    Bundle extras = getIntent().getExtras();
    if(extras !=null)
    {
      count_id = extras.getLong("count_id");
    }

    /*
     * Just in case this activity starts up without an intent having been supplied, e.g. when the user
     * resumes after having done some editing.
     */
    if (count_id == 0)
    {
      startActivity(new Intent(this, WelcomeActivity.class).addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP));
    }

  }

  @Override
  protected void onResume()
  {
    super.onResume();

    // clear any existing views
    static_widget_area.removeAllViews();
    dynamic_widget_area.removeAllViews();

    // get the data sources
    countDataSource = new CountDataSource(this);
    countDataSource.open();
    alertDataSource = new AlertDataSource(this);
    alertDataSource.open();

    count = countDataSource.getCountById(count_id);
    getActionBar().setTitle(count.name);

    List<Alert> alerts = alertDataSource.getAllAlertsForCount(count_id);

    // setup the static widgets in the following order
    // 1. Auto reset value (value at which reset is triggered)
    // 2. Auto reset level (value to which count rests)
    // 3. Current count value
    // 4. Alert add/remove
    ar_value_widget = new OptionsWidget(this,null);
    ar_value_widget.setInstructions(getString(R.string.setResetValue));
    ar_value_widget.setParameterValue(count.auto_reset);
    static_widget_area.addView(ar_value_widget);

    ar_level_widget = new OptionsWidget(this,null);
    ar_level_widget.setInstructions(String.format(getString(R.string.setResetLevel), count.name));
    ar_level_widget.setParameterValue(count.reset_level);
    static_widget_area.addView(ar_level_widget);

    curr_val_widget = new OptionsWidget(this,null);
    curr_val_widget.setInstructions(String.format(getString(R.string.editCountValue), count.name, count.count));
    curr_val_widget.setParameterValue(count.count);
    static_widget_area.addView(curr_val_widget);

    aa_widget = new AddAlertWidget(this,null);
    static_widget_area.addView(aa_widget);

    /*
     * There should be a method to add all counts in order to re-draw when one is deleted.
     */
    for (Alert alert : alerts)
    {
      AlertCreateWidget acw = new AlertCreateWidget(this,null);
      acw.setAlertName(alert.alert_text);
      acw.setAlertValue(alert.alert);
      acw.setAlertId(alert.id);
      dynamic_widget_area.addView(acw);
    }


  }

  @Override
  protected void onPause()
  {
    super.onPause();

    // finally, close the database
    countDataSource.close();
    alertDataSource.close();

  }

  public void saveAndExit(View view)
  {
    saveData();
    super.finish();
  }

  public void saveData()
  {
    Toast.makeText(CountOptionsActivity.this, getString(R.string.projSaving) + " " + count.name + "!", Toast.LENGTH_SHORT).show();
    count.auto_reset = ar_value_widget.getParameterValue();
    count.reset_level = ar_level_widget.getParameterValue();
    count.count = curr_val_widget.getParameterValue();
    countDataSource.saveCount(count);

    /*
     * Get all the alerts from the dynamic_widget_area and save each one.
     * If it has an id value set to anything higher than 0 then it should be an update, if it is 0
     * then it's a new alert and should be created instead.
     */
    int childcount = dynamic_widget_area.getChildCount();
    for (int i=0; i < childcount; i++)
    {
      AlertCreateWidget acw = (AlertCreateWidget) dynamic_widget_area.getChildAt(i);
      if (StringUtils.isNotEmpty(acw.getAlertName()))
      {
        // save or create
        if (acw.getAlertId() == 0)
        {
          alertDataSource.createAlert(count_id, acw.getAlertValue(), acw.getAlertName());
        } else
        {
          alertDataSource.saveAlert(acw.getAlertId(), acw.getAlertValue(), acw.getAlertName());
        }
      }
      else
      {
        Log.i(TAG, "Failed to save alert: " + acw.getAlertId());
      }
    }
  }

  public void addAnAlert(View view)
  {
    AlertCreateWidget acw = new AlertCreateWidget(this,null);
    dynamic_widget_area.addView(acw);
  }

  public void deleteWidget(View view)
  {
    /*
     * These global variables keep a track of the view containing an alert to be deleted and also the id
     * of the alert itself, to make sure that they're available inside the code for the alert dialog by
     * which they will be deleted.
     */
    markedForDelete = view;
    deleteAnAlert = (Long) view.getTag();
    if (deleteAnAlert == 0)
    {
      //Log.i(TAG, "(1) View tag was " + String.valueOf(deleteAnAlert));
      // the actual AlertCreateWidget is two levels up from the button in which it is embedded
      dynamic_widget_area.removeView((AlertCreateWidget) view.getParent().getParent());
    }
    else
    {
      //Log.i(TAG, "(2) View tag was " + String.valueOf(deleteAnAlert));
      // before removing this widget it is necessary to do the following:
      // (1) Check the user is sure they want to delete it and, if so...
      // (2) Delete the associated alert from the database.
      are_you_sure = new AlertDialog.Builder(this);
      are_you_sure.setTitle(getString(R.string.deleteAlert));
      are_you_sure.setMessage(getString(R.string.reallyDeleteAlert));
      are_you_sure.setPositiveButton(R.string.yesDeleteIt, new DialogInterface.OnClickListener()
      {
        public void onClick(DialogInterface dialog, int whichButton)
        {
          // go ahead for the delete
          alertDataSource.deleteAlertById(deleteAnAlert);
          dynamic_widget_area.removeView((AlertCreateWidget) markedForDelete.getParent().getParent());
        }
      });
      are_you_sure.setNegativeButton(R.string.noCancel, new DialogInterface.OnClickListener()
      {
        public void onClick(DialogInterface dialog, int whichButton)
        {
          // Cancelled.
        }
      });
      are_you_sure.show();
    }
  }


  @Override
  public boolean onCreateOptionsMenu(Menu menu)
  {
    // Inflate the menu; this adds items to the action bar if it is present.
    getMenuInflater().inflate(R.menu.count_options, menu);
    return true;
  }

  @Override
  public boolean onOptionsItemSelected(MenuItem item)
  {
    // Handle action bar item clicks here. The action bar will
    // automatically handle clicks on the Home/Up button, so long
    // as you specify a parent activity in AndroidManifest.xml.
    int id = item.getItemId();
    if (id == R.id.action_settings)
    {
      startActivity(new Intent(this, SettingsActivity.class).addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP));
      return true;
    }
    return super.onOptionsItemSelected(item);
  }

  public void onSharedPreferenceChanged(SharedPreferences prefs, String key)
  {
    ScrollView counting_screen = (ScrollView) findViewById(R.id.count_options);
    counting_screen.setBackgroundDrawable(null);
    counting_screen.setBackgroundDrawable(beeCountApplication.setBackground());
  }

}