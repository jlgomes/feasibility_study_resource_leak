package com.knirirr.beecount;

import android.annotation.SuppressLint;
import android.app.Activity;
import android.app.AlertDialog;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.os.Environment;
import android.preference.PreferenceManager;
import android.util.Log;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.widget.LinearLayout;
import android.widget.Toast;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;


public class WelcomeActivity extends Activity implements SharedPreferences.OnSharedPreferenceChangeListener
{

  private static String TAG = "BeeCountWelcomeActivity";
  BeeCountApplication beeCount;
  SharedPreferences prefs;

  // import/export stuff
  File infile;
  File outfile;
  boolean mExternalStorageAvailable = false;
  boolean mExternalStorageWriteable = false;
  String state = Environment.getExternalStorageState();
  AlertDialog alert;

  @Override
  protected void onCreate(Bundle savedInstanceState)
  {
    super.onCreate(savedInstanceState);
    setContentView(R.layout.activity_welcome);

    beeCount = (BeeCountApplication) getApplication();
    prefs = BeeCountApplication.getPrefs();
    prefs.registerOnSharedPreferenceChangeListener(this);

    /*
    String backgroundPref = prefs.getString("pref_back", "default");
    Boolean fontPref = prefs.getBoolean("pref_font", true);
    String pictPref = prefs.getString("imagePath", "");
    */

    LinearLayout baseLayout = (LinearLayout) findViewById(R.id.baseLayout);
    baseLayout.setBackgroundDrawable(beeCount.getBackground());

  }


  @Override
  public boolean onCreateOptionsMenu(Menu menu)
  {
      // Inflate the menu; this adds items to the action bar if it is present.
      getMenuInflater().inflate(R.menu.welcome, menu);
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
    else if (id == R.id.exportMenu)
    {
      exportDb();
      return true;
    }
    else if (id == R.id.importMenu)
    {
      importDb();
      return true;
    }
    return super.onOptionsItemSelected(item);
  }

  public void newProject(View view)
  {
    startActivity(new Intent(this, NewProjectActivity.class).addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP));
  }

  public void viewProjects(View view)
  {
    startActivity(new Intent(this, ListProjectActivity.class).addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP));
  }

  public void onSharedPreferenceChanged(SharedPreferences prefs, String key)
  {
    LinearLayout baseLayout = (LinearLayout) findViewById(R.id.baseLayout);
    baseLayout.setBackgroundDrawable(beeCount.setBackground());
  }

  /*
   * The three activities below are for exporting and importing the database. They've been put here because
   * no database should be open at this point.
   */

  @SuppressLint("SdCardPath")
  public void exportDb()
  {
    boolean mExternalStorageAvailable = false;
    boolean mExternalStorageWriteable = false;
    String state = Environment.getExternalStorageState();
    File outfile = new File(Environment.getExternalStorageDirectory() + "/beecount.db");
    String destPath = "/data/data/com.knirirr.beecount/files";
    try
    {
      destPath = getFilesDir().getPath();
    }
    catch (Exception e)
    {
      Log.e(TAG,"destPath error: " + e.toString());
    }
    destPath = destPath.substring(0, destPath.lastIndexOf("/")) + "/databases";
    File infile = new File(destPath + "/beecount.db");

    if (Environment.MEDIA_MOUNTED.equals(state))
    {
      // We can read and write the media
      mExternalStorageAvailable = mExternalStorageWriteable = true;
    }
    else if (Environment.MEDIA_MOUNTED_READ_ONLY.equals(state))
    {
      // We can only read the media
      mExternalStorageAvailable = true;
      mExternalStorageWriteable = false;
    }
    else
    {
      // Something else is wrong. It may be one of many other states, but all we need
      //  to know is we can neither read nor write
      mExternalStorageAvailable = mExternalStorageWriteable = false;
    }

    if ((mExternalStorageAvailable == false) || ( mExternalStorageWriteable == false))
    {
      Log.e(TAG,"No sdcard access");
      Toast.makeText(this, getString(R.string.noCard), Toast.LENGTH_SHORT).show();
      return;
    }
    else
    {
      // export the db
      try
      {
        copy(infile, outfile);
        Toast.makeText(this,getString(R.string.saveWin),Toast.LENGTH_SHORT).show();
        return;
      }
      catch (IOException e)
      {
        Log.e(TAG,"Failed to copy database");
        Toast.makeText(this,getString(R.string.saveFail),Toast.LENGTH_SHORT).show();
        return;
      }

    }
  }

  @SuppressLint("SdCardPath")
  public void importDb()
  {
    infile = new File(Environment.getExternalStorageDirectory() + "/beecount.db");
    //outfile = new File("/data/data/com.knirirr.beecount/databases/beecount.db");
    String destPath = "/data/data/com.knirirr.beecount/files";
    try
    {
      destPath = getFilesDir().getPath();
    }
    catch (Exception e)
    {
      Log.e(TAG,"destPath error: " + e.toString());
    }
    destPath = destPath.substring(0, destPath.lastIndexOf("/")) + "/databases";
    //File infile = new File(destPath + "/beecount.db");
    //outfile = new File(this.getFilesDir().getPath() + "/data/com.knirirr.beecount/databases/beecount.db");
    outfile = new File(destPath + "/beecount.db");
    if(!(infile.exists()))
    {
      Toast.makeText(this,getString(R.string.noDb),Toast.LENGTH_SHORT).show();
      return;
    }

    // a confirm dialogue before anything else takes place
    // http://developer.android.com/guide/topics/ui/dialogs.html#AlertDialog
    // could make the dialog central in the popup - to do later
    AlertDialog.Builder builder = new AlertDialog.Builder(this);
    builder.setIcon(android.R.drawable.ic_dialog_alert);
    builder.setMessage(R.string.confirmImport).setCancelable(false).setPositiveButton(R.string.importButton, new DialogInterface.OnClickListener()
    {
      public void onClick(DialogInterface dialog, int id)
      {
        // START
        // replace this with another function rather than this lazy c&p
        if (Environment.MEDIA_MOUNTED.equals(state))
        {
          // We can read and write the media
          mExternalStorageAvailable = mExternalStorageWriteable = true;
        } else if (Environment.MEDIA_MOUNTED_READ_ONLY.equals(state))
        {
          // We can only read the media
          mExternalStorageAvailable = true;
          mExternalStorageWriteable = false;
        } else
        {
          // Something else is wrong. It may be one of many other states, but all we need
          //  to know is we can neither read nor write
          mExternalStorageAvailable = mExternalStorageWriteable = false;
        }


        if ((mExternalStorageAvailable == false) || (mExternalStorageWriteable == false))
        {
          Log.e(TAG, "No sdcard access");
          Toast.makeText(getApplicationContext(), getString(R.string.noCard), Toast.LENGTH_SHORT).show();
          return;
        } else
        {
          try
          {
            copy(infile, outfile);
            Toast.makeText(getApplicationContext(), getString(R.string.importWin), Toast.LENGTH_SHORT).show();
          } catch (IOException e)
          {
            Log.e(TAG, "Failed to import database");
            Toast.makeText(getApplicationContext(), getString(R.string.importFail), Toast.LENGTH_SHORT).show();
            return;
          }
        }

        // END
      }
    }).setNegativeButton(R.string.importCancelButton, new DialogInterface.OnClickListener()
    {
      public void onClick(DialogInterface dialog, int id)
      {
        dialog.cancel();
      }
    });
    alert = builder.create();
    alert.show();
  }

  // http://stackoverflow.com/questions/9292954/how-to-make-a-copy-of-a-file-in-android
  public void copy(File src, File dst) throws IOException
  {
    FileInputStream in = new FileInputStream(src);
    FileOutputStream out = new FileOutputStream(dst);

    // Transfer bytes from in to out
    byte[] buf = new byte[1024];
    int len;
    while ((len = in.read(buf)) > 0)
    {
      out.write(buf, 0, len);
    }
    in.close();
    out.close();
  }

}