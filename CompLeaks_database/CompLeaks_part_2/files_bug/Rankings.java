/*
 * Pixel Dungeon
 * Copyright (C) 2012-2014  Oleg Dolya
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program.  If not, see <http://www.gnu.org/licenses/>
 */
package com.shatteredpixel.shatteredpixeldungeon;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;

import com.shatteredpixel.shatteredpixeldungeon.actors.hero.Hero;
import com.watabou.noosa.Game;
import com.shatteredpixel.shatteredpixeldungeon.actors.hero.HeroClass;
import com.shatteredpixel.shatteredpixeldungeon.utils.Utils;
import com.watabou.utils.Bundlable;
import com.watabou.utils.Bundle;
import com.watabou.utils.SystemTime;

public enum Rankings {
	
	INSTANCE;
	
	public static final int TABLE_SIZE	= 11;
	
	public static final String RANKINGS_FILE = "rankings.dat";
	public static final String DETAILS_FILE = "game_%d.dat";
	
	public ArrayList<Record> records;
	public int lastRecord;
	public int totalNumber;
	
	public void submit( boolean win ) {

		load();
		
		Record rec = new Record();
		
		rec.info	= Dungeon.resultDescription;
		rec.win		= win;
		rec.heroClass	= Dungeon.hero.heroClass;
		rec.armorTier	= Dungeon.hero.tier();
		rec.herolevel	= Dungeon.hero.lvl;
		rec.depth		= Dungeon.depth;
		rec.score	= score( win );
		
		String gameFile = Utils.format( DETAILS_FILE, SystemTime.now );
		try {
			Dungeon.saveGame( gameFile );
			rec.gameFile = gameFile;
		} catch (IOException e) {
			rec.gameFile = "";
		}
		
		records.add( rec );
		
		Collections.sort( records, scoreComparator );
		
		lastRecord = records.indexOf( rec );
		int size = records.size();
		while (size > TABLE_SIZE) {
			
			Record removedGame;
			if (lastRecord == size - 1) {
				removedGame = records.remove( size - 2 );
				lastRecord--;
			} else {
				removedGame = records.remove( size - 1 );
			}
			
			if (removedGame.gameFile.length() > 0) {
				Game.instance.deleteFile( removedGame.gameFile );
			}

			size = records.size();
		}
		
		totalNumber++;
		
		Badges.validateGamesPlayed();
		
		save();
	}
	
	private int score( boolean win ) {
		return (Statistics.goldCollected + Dungeon.hero.lvl * (win ? 26 : Dungeon.depth ) * 100) * (win ? 2 : 1);
	}
	
	private static final String RECORDS	= "records";
	private static final String LATEST	= "latest";
	private static final String TOTAL	= "total";
	
	public void save() {
		Bundle bundle = new Bundle();
		bundle.put( RECORDS, records );
		bundle.put( LATEST, lastRecord );
		bundle.put( TOTAL, totalNumber );
		
		try {
			OutputStream output = Game.instance.openFileOutput( RANKINGS_FILE, Game.MODE_PRIVATE );
			Bundle.write( bundle, output );
			output.close();
		} catch (IOException e) {
		}
	}
	
	public void load() {
		
		if (records != null) {
			return;
		}
		
		records = new ArrayList<Rankings.Record>();
		
		try {
			InputStream input = Game.instance.openFileInput( RANKINGS_FILE );
			Bundle bundle = Bundle.read( input );
			input.close();
			
			for (Bundlable record : bundle.getCollection( RECORDS )) {
				records.add( (Record)record );
			}			
			lastRecord = bundle.getInt( LATEST );
			
			totalNumber = bundle.getInt( TOTAL );
			if (totalNumber == 0) {
				totalNumber = records.size();
			}

		} catch (IOException e) {
		}
	}
	
	public static class Record implements Bundlable {
		
		private static final String REASON	= "reason";
		private static final String WIN		= "win";
		private static final String SCORE	= "score";
		private static final String TIER	= "tier";
		private static final String LEVEL	= "level";
		private static final String DEPTH	= "depth";
		private static final String GAME	= "gameFile";
		
		public String info;
		public boolean win;
		
		public HeroClass heroClass;
		public int armorTier;
		public int herolevel;
		public int depth;
		
		public int score;
		
		public String gameFile;
		
		@Override
		public void restoreFromBundle( Bundle bundle ) {
			
			info	= bundle.getString( REASON );
			win		= bundle.getBoolean( WIN );
			score	= bundle.getInt( SCORE );
			
			heroClass	= HeroClass.restoreInBundle( bundle );
			armorTier	= bundle.getInt( TIER );
			
			gameFile	= bundle.getString( GAME );

			//for pre 0.2.3 saves
			if (!bundle.contains(LEVEL)){
				try {
					depth = Integer.parseInt(info.replaceAll("[\\D]", ""));
				} catch (Exception e) {
					depth = 0;
				}
				info = info.split("on level")[0].trim();
				try {
					/* This commented code remains here as it is the single worst thing I have added to this codebase,
					   and I must be shamed for doing something so stupid. May I remember these two lines
					   and as a result never make the same mistake again.
					Dungeon.loadGame(gameFile);
					herolevel = Dungeon.hero.lvl;
					*/
					InputStream input = Game.instance.openFileInput( gameFile );
					Bundle gameBundle = Bundle.read( input );
					input.close();

					herolevel = ((Hero)gameBundle.get( "hero" )).lvl;
				} catch (Exception e){
					herolevel = 0;
				}
			} else {
				depth = bundle.getInt( DEPTH );
				herolevel = bundle.getInt( LEVEL );
			}
		}
		
		@Override
		public void storeInBundle( Bundle bundle ) {
			
			bundle.put( REASON, info );
			bundle.put( WIN, win );
			bundle.put( SCORE, score );
			
			heroClass.storeInBundle( bundle );
			bundle.put( TIER, armorTier );
			bundle.put( LEVEL, herolevel );
			bundle.put( DEPTH, depth );
			
			bundle.put( GAME, gameFile );
		}
	}

	private static final Comparator<Record> scoreComparator = new Comparator<Rankings.Record>() {
		@Override
		public int compare( Record lhs, Record rhs ) {
			return (int)Math.signum( rhs.score - lhs.score );
		}
	};
}