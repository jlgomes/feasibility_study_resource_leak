/*
 * Copyright 2013 Thomas Hoffmann
 * 
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 * 
 *     http://www.apache.org/licenses/LICENSE-2.0
 * 
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package de.j4velin.pedometer;

import android.content.ContentValues;
import android.content.Context;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.database.sqlite.SQLiteDatabase.CursorFactory;
import android.database.sqlite.SQLiteOpenHelper;

public class Database_03_12_2013 extends SQLiteOpenHelper {

	private SQLiteDatabase database;

	private final static String DB_NAME = "steps";
	private final static int DB_VERSION = 1;

	public Database(final Context context, final String name, final CursorFactory factory, int version) {
		super(context, name, factory, version);
	}

	public Database(final Context context) {
		super(context, DB_NAME, null, DB_VERSION);
	}

	public void open() {
		database = getWritableDatabase();
	}

	public void close() {
		database.close();
	}

	@Override
	public void onCreate(final SQLiteDatabase db) {
		db.execSQL("CREATE TABLE " + DB_NAME + " (date INTEGER PRIMARY KEY, steps INTEGER)");
	}

	@Override
	public void onUpgrade(final SQLiteDatabase db, int oldVersion, int newVersion) {
	}

	void insertDay(final long date, int offset) {
		Cursor c = database.query("steps", new String[] { "date" }, "date = ?", new String[] { String.valueOf(date) }, null,
				null, null);
		if (c.getCount() == 0) {
			ContentValues values = new ContentValues();
			values.put("date", date);
			values.put("steps", offset);
			database.insert(DB_NAME, null, values);
		}
		c.close();
		if (Logger.LOG) {
			Logger.log("insertDay " + date + " / " + offset);
			logState();
		}
	}

	/**
	 * Writes the current steps database to the log
	 */
	void logState() {
		if (Logger.LOG) {
			Cursor c = database.query(DB_NAME, null, null, null, null, null, null);
			Logger.log(c);
			c.close();
		}
	}

	/**
	 * Query the 'steps' table.
	 * Remember to close the cursor!
	 * 
	 * @param columns
	 * @param selection
	 * @param selectionArgs
	 * @param groupBy
	 * @param having
	 * @param orderBy
	 * @return the cursor
	 */
	Cursor query(final String[] columns, final String selection, final String[] selectionArgs, final String groupBy,
			final String having, final String orderBy, final String limit) {
		return database.query(DB_NAME, columns, selection, selectionArgs, groupBy, having, orderBy, limit);
	}

	/**
	 * Adds 'steps' steps to the row for the date 'date'
	 * 
	 * @param date
	 *            the date to update the steps for in millis since 1970
	 * @param steps
	 *            the steps to add to the current steps-value for the date
	 */
	void updateSteps(final long date, int steps) {
		database.execSQL("UPDATE " + DB_NAME + " SET steps = steps + " + steps + " WHERE date = " + date);
		if (Logger.LOG) {
			Logger.log("updateSteps " + date + " / " + steps);
			logState();
		}
	}

	/**
	 * @return number of steps taken, ignoring today
	 */
	int getTotalWithoutToday() {
		Cursor c = database
				.rawQuery("SELECT SUM(steps) FROM " + DB_NAME + " WHERE steps > 0 AND date < " + Util.getToday(), null);
		c.moveToFirst();
		int re = c.getInt(0);
		c.close();
		return re;
	}
	
	/**
	 * @return number of steps taken, with today
	 */
	int getTotal() {
		Cursor c = database
				.rawQuery("SELECT SUM(steps) FROM " + DB_NAME, null);
		c.moveToFirst();
		int re = c.getInt(0) + SensorListener.steps;
		c.close();
		return re;
	}

	/**
	 * @param date
	 *            the date in millis since 1970
	 * @return the steps taken on this date or Integer.MIN_VALUE if date doesn't
	 *         exist in the database
	 */
	int getSteps(final long date) {
		Cursor c = database.query("steps", new String[] { "steps" }, "date = ?", new String[] { String.valueOf(date) }, null,
				null, null);
		c.moveToFirst();
		int re;
		if (c.getCount() == 0)
			re = Integer.MIN_VALUE;
		else
			re = c.getInt(0);
		c.close();
		return re;
	}
}