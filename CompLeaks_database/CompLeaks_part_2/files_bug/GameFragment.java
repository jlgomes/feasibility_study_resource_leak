package tof.cv.mpp;


import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.graphics.drawable.Drawable;
import android.location.Location;
import android.location.LocationListener;
import android.location.LocationManager;
import android.net.Uri;
import android.os.AsyncTask;
import android.os.Bundle;
import android.os.Handler;
import android.preference.PreferenceManager;
import android.provider.Settings;
import android.support.v4.view.ViewPager;
import android.text.format.DateUtils;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewGroup;
import android.widget.GridView;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;

import com.google.android.gms.common.ConnectionResult;
import com.google.android.gms.common.api.GoogleApiClient;
import com.google.android.gms.common.api.ResultCallback;
import com.google.android.gms.common.images.ImageManager;
import com.google.android.gms.games.Games;
import com.google.android.gms.games.Player;
import com.google.android.gms.games.achievement.Achievement;
import com.google.android.gms.games.achievement.Achievements;
import com.google.android.gms.games.leaderboard.LeaderboardScore;
import com.google.android.gms.games.leaderboard.LeaderboardScoreBuffer;
import com.google.android.gms.games.leaderboard.LeaderboardVariant;
import com.google.android.gms.games.leaderboard.Leaderboards;
import com.google.gson.Gson;

import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.Reader;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Iterator;

import de.keyboardsurfer.android.widget.crouton.Configuration;
import de.keyboardsurfer.android.widget.crouton.Crouton;
import de.keyboardsurfer.android.widget.crouton.Style;
import tof.cv.mpp.Utils.BaseGameFragment;
import tof.cv.mpp.Utils.MyPagerAdapter;
import tof.cv.mpp.Utils.MyStaggeredGridView;
import tof.cv.mpp.Utils.PagerSlidingTabStrip;
import tof.cv.mpp.Utils.Utils;
import tof.cv.mpp.Utils.UtilsWeb;
import tof.cv.mpp.adapter.AchievementAdapter;
import tof.cv.mpp.adapter.HighScoreAdapter;


/**
 * Created by CVE on 30/01/14.
 */
public class GameFragment extends BaseGameFragment implements
        GoogleApiClient.ConnectionCallbacks, GoogleApiClient.OnConnectionFailedListener, ImageManager.OnImageLoadedListener, View.OnClickListener, LocationListener {

    private static final Configuration CONFIGURATION_INFINITE = new Configuration.Builder()
            .setDuration(Configuration.DURATION_INFINITE)
            .build();
    boolean top = false;
    StationList.StationsList list;
    Crouton crouton;
    Location me;
    long newScore;
    //GameHelper mHelper;
    boolean init = false;
    Handler handler = new Handler();
    public final static String LEADER = "CgkI9Y3S0soCEAIQAg";
    public final static String CHECKIN = "CgkI9Y3S0soCEAIQBA";
    public final static String CHECKDELAY = "CgkI9Y3S0soCEAIQBQ";
    public final static String CHECKHUGEDELAY = "CgkI9Y3S0soCEAIQCA";
    public final static String VETERAN = "CgkI9Y3S0soCEAIQCg";

    MyPagerAdapter mPagerAdapter;
    String[] titles;
    StationList.Stationinfo closest;

    LocationManager locationManager;

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container,
                             Bundle savedInstanceState) {
        return inflater.inflate(R.layout.fragment_game, null);
    }

    @Override
    public void onActivityCreated(Bundle savedInstanceState) {
        super.onActivityCreated(savedInstanceState);
        this.getActivity().getActionBar().setSubtitle(null);
        this.getActivity().getActionBar().setIcon(R.drawable.ic_game);

        ViewPager pager = (ViewPager) getView().findViewById(R.id.pager);
        titles = this.getResources().getStringArray(R.array.titles);
        mPagerAdapter = new MyPagerAdapter(titles);
        pager.setAdapter(mPagerAdapter);

        //mPagerAdapter.notifyDataSetChanged();
        PagerSlidingTabStrip tabs = (PagerSlidingTabStrip) getView().findViewById(R.id.tabs);
        tabs.setViewPager(pager);
        pager.setOffscreenPageLimit(2);

        // mHelper = new GameHelper(getActivity(),);
        // mHelper.setup(this);

        try {
            String langue = getActivity().getString(R.string.url_lang);
            if (PreferenceManager.getDefaultSharedPreferences(getActivity()).getBoolean(
                    "prefnl", false))
                langue = "nl";
            if (langue.contentEquals("en"))
                langue = "fr";
            InputStream inputStream = getActivity().getAssets().open("stations" + langue + ".json");
            Reader r = new InputStreamReader(inputStream);
            Gson gson = new Gson();
            list = gson.fromJson(r, StationList.StationsList.class);
        } catch (IOException e) {
            e.printStackTrace();

        }

        me = Utils.getLastLoc(this.getActivity());
        locationManager = (LocationManager) getActivity().getSystemService(Context.LOCATION_SERVICE);

        setHasOptionsMenu(true);

        GoogleApiClient mGoogleApiClient = new GoogleApiClient.Builder(this.getActivity())
                .addApi(Games.API)
                .addScope(Games.SCOPE_GAMES)
                .build();

        if (isSignedIn())
            setupOk();
        else
            beginUserInitiatedSignIn();

    }


    @Override
    public void onCreateOptionsMenu(Menu menu, MenuInflater inflater) {

        menu.add(Menu.NONE, 0, Menu.NONE, "Map")
                .setIcon(android.R.drawable.ic_menu_myplaces)
                .setShowAsAction(MenuItem.SHOW_AS_ACTION_ALWAYS);

    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        switch (item.getItemId()) {
            case (0):
                String uri = "geo:0,0?q=" + closest.locationY + "," + closest.locationX + " (" + closest.getStation() + ")";
                startActivity(new Intent(android.content.Intent.ACTION_VIEW, Uri.parse(uri)));
                return true;
            default:
                return super.onOptionsItemSelected(item);
        }
    }

    @Override
    public void onPause() {
        super.onPause();
        Crouton.cancelAllCroutons();
        locationManager.removeUpdates(this);
    }


    @Override
    public void onResume() {
        super.onResume();
        locationManager.requestLocationUpdates(LocationManager.GPS_PROVIDER, 10 * DateUtils.SECOND_IN_MILLIS, 10, this);

        if (init && !this.isSignedIn())
            this.beginUserInitiatedSignIn();
        init = true;

        String provider = Settings.Secure.getString(getActivity().getContentResolver(),
                Settings.Secure.LOCATION_PROVIDERS_ALLOWED);

        if (!provider.contains(LocationManager.GPS_PROVIDER)) {
            crouton = Crouton.makeText(getActivity(), R.string.no_gps, Style.ALERT).setConfiguration(CONFIGURATION_INFINITE).setOnClickListener(this);
            crouton.show();
        }
    }

    @Override
    public void onStart() {
        super.onStart();
        mHelper.onStart(this.getActivity());
    }

    @Override
    public void onStop() {
        super.onStop();
        mHelper.onStop();
    }

    @Override
    public void onActivityResult(int request, int response, Intent data) {
        super.onActivityResult(request, response, data);
        setupOk();
    }

    private void setupOk() {
        SharedPreferences sp;
        try {
            sp = PreferenceManager.getDefaultSharedPreferences(getActivity());
        } catch (Exception e) {
            e.printStackTrace();
            return;
        }
        int score = sp.getInt("searchGame", 0) + 1;
        boolean chat = sp.getBoolean("chatUnlock", false);
        Log.e("", "chat " + chat);
        try {
            Games.Achievements.setSteps(getApiClient(), PlannerFragment.SEARCH, score);
            if (chat)
                Games.Achievements.unlock(getApiClient(), ChatActivity.ID);
        } catch (Exception e) {
            e.printStackTrace();
        }
        Log.e("", "SETUP");
        try {
            getView().findViewById(R.id.profile).setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    try {
                        getActivity().startActivityForResult(Games.Leaderboards.getLeaderboardIntent(getApiClient(), LEADER), 1);
                    } catch (Exception e) {
                        e.printStackTrace();
                    }
                }
            });
        } catch (Exception e) {
            e.printStackTrace();
        }
        try {//Just to be sure player exists
            Games.Leaderboards.submitScore(getApiClient(), LEADER, newScore);
        } catch (Exception e) {
            e.printStackTrace();
        }

        try {
            Player p = Games.Players.getCurrentPlayer(getApiClient());

            try {
                ((TextView) getView().findViewById(R.id.currentuserName)).setText(p.getDisplayName());
            } catch (Exception e) {
                e.printStackTrace();
            }
            //Picasso.with(this.getActivity()).load(p.getIconImageUri().getPath()).into((ImageView)getView().findViewById(R.id.profilePic));
            ImageManager im = ImageManager.create(this.getActivity());
            im.loadImage(this, p.getHiResImageUri());
            id = p.getPlayerId();

            refreshScores();
        } catch (Exception e) {
            e.printStackTrace();
        }
        try {

            int installed = (int) (System.currentTimeMillis() - getActivity()
                    .getPackageManager()
                    .getPackageInfo(getActivity().getPackageName(), 0)
                    .firstInstallTime) / (int) DateUtils.DAY_IN_MILLIS;
            if (installed > 0)
                Games.Achievements.setSteps(getApiClient(), VETERAN, installed);
            Log.e("", "installed" + installed);
            Games.Achievements.load(getApiClient(), false).setResultCallback(new ResultCallback<Achievements.LoadAchievementsResult>() {
                @Override
                public void onResult(Achievements.LoadAchievementsResult loadAchievementsResult) {

                    Iterator<Achievement> it = loadAchievementsResult.getAchievements().iterator();
                    AchievementAdapter adapter = new AchievementAdapter(GameFragment.this.getActivity(), R.layout.row_achieve, new ArrayList<Achievement>());
                    while (it.hasNext()) {
                        adapter.add(it.next());
                    }
                    ((MyStaggeredGridView) getView().findViewById(R.id.listAchivement)).setAdapter(adapter);

                }
            });


        } catch (Exception e) {
            e.printStackTrace();
        }

        setupClosest();
        startCountDownThread();

    }


    @Override
    public void onConnected(Bundle bundle) {
        setupOk();

    }

    @Override
    public void onConnectionSuspended(int i) {
        Log.e("", "SUSPENDED");
    }

    @Override
    public void onConnectionFailed(ConnectionResult connectionResult) {
        Log.e("", "FAIL");
    }

    @Override
    public void onSignInFailed() {
        Log.e("", "SIGNIN NOK");

    }

    @Override
    public void onSignInSucceeded() {
        Log.e("", "SIGNIN OK");
        setupOk();
    }

    private String station1;
    private String station2;
    private String station3;

    public void setupClosest() {
        try {

            for (StationList.Stationinfo aStation : list.station) {

                double dDis = Utils.distance(
                        Double.valueOf(aStation.locationX),
                        Double.valueOf(aStation.locationY), me.getLongitude(),
                        me.getLatitude());
                aStation.distance = ((int) (dDis * 100)) / 100.0;

            }
            Collections.sort(list.station);
            closest = list.station.get(0);
            ((TextView) getView().findViewById(R.id.closest1Title)).setText(closest
                    .getStation());
            ((TextView) getView().findViewById(R.id.closest1Desc))
                    .setText(closest.getDistance());
            station1 = closest.getStation();
            setupLayout(closest, R.id.closest1Layout);

            StationList.Stationinfo stationinfo = list.station.get(1);
            ((TextView) getView().findViewById(R.id.closest2Title)).setText(stationinfo
                    .getStation());
            ((TextView) getView().findViewById(R.id.closest2Desc))
                    .setText(stationinfo.getDistance());
            station2 = stationinfo.getStation();
            setupLayout(stationinfo, R.id.closest2Layout);

            stationinfo = list.station.get(2);
            ((TextView) getView().findViewById(R.id.closest3Title)).setText(stationinfo
                    .getStation());
            ((TextView) getView().findViewById(R.id.closest3Desc))
                    .setText(stationinfo.getDistance());
            station3 = stationinfo.getStation();
            setupLayout(stationinfo, R.id.closest3Layout);

        } catch (Exception e) {
            e.printStackTrace();
        }

    }

    String id = "";

    private void refreshScores() {
        top = false;

        Games.Leaderboards.loadCurrentPlayerLeaderboardScore(getApiClient(), LEADER, LeaderboardVariant.TIME_SPAN_WEEKLY, LeaderboardVariant.COLLECTION_PUBLIC).setResultCallback(new ResultCallback<Leaderboards.LoadPlayerScoreResult>() {
            @Override
            public void onResult(Leaderboards.LoadPlayerScoreResult loadPlayerScoreResult) {
                if (loadPlayerScoreResult != null && loadPlayerScoreResult.getScore() != null) {
                    ((TextView) getView().findViewById(R.id.currentuserRank)).setText("" + loadPlayerScoreResult.getScore().getDisplayRank());
                    newScore = loadPlayerScoreResult.getScore().getRawScore();
                    ((TextView) getView().findViewById(R.id.currentuserPoints)).setText("" + loadPlayerScoreResult.getScore().getDisplayScore());

                }
            }
        });


        Games.Leaderboards.loadPlayerCenteredScores(getApiClient(), LEADER, LeaderboardVariant.TIME_SPAN_WEEKLY, LeaderboardVariant.COLLECTION_PUBLIC, 25).setResultCallback(new ResultCallback<Leaderboards.LoadScoresResult>() {
            @Override
            public void onResult(Leaderboards.LoadScoresResult loadScoresResult) {
                LeaderboardScoreBuffer leaderboardScores = loadScoresResult.getScores();
                try {
                    Iterator<LeaderboardScore> it = leaderboardScores.iterator();
                    HighScoreAdapter adapter = new HighScoreAdapter(GameFragment.this.getActivity(), R.layout.row_high, new ArrayList<LeaderboardScore>());

                    while (it.hasNext()) {
                        LeaderboardScore temp = it.next();
                        adapter.add(temp);

                    }
                    //  leaderboardScores.close();

                    ((GridView) getView().findViewById(R.id.listHighscore)).setAdapter(adapter);

                } catch (Exception e) {
                    e.printStackTrace();
                }
            }
        });
    }


    @Override
    public void onClick(View v) {
        crouton.cancel();
        Intent intent = new Intent(Settings.ACTION_LOCATION_SOURCE_SETTINGS);
        startActivity(intent);

    }

    @Override
    public void onLocationChanged(Location location) {
        me = location;
        setupClosest();

    }

    @Override
    public void onStatusChanged(String provider, int status, Bundle extras) {

    }

    @Override
    public void onProviderEnabled(String provider) {

    }

    @Override
    public void onProviderDisabled(String provider) {

    }

    @Override
    public void onImageLoaded(Uri uri, Drawable drawable, boolean b) {
        try {
            ((ImageView) getView().findViewById(R.id.currentprofilePic)).setImageDrawable(drawable);
        } catch (Exception e) {
            e.printStackTrace();
        }

    }

    public class StationList {

        public class StationsList {
            public ArrayList<Stationinfo> station;
        }

        public class Stationinfo implements Comparable<Object> {
            public String name;
            public String locationX;
            public String locationY;
            public double distance;

            @Override
            public int compareTo(Object toCompare) {
                return Double.compare(this.distance,
                        ((Stationinfo) (toCompare)).distance);
            }

            public CharSequence getDistance() {
                if (distance > 1)
                    return distance + "km";
                else
                    return distance * 1000 + "m";
            }


            public String getStation() {
                return this.name;
            }
        }
    }

    private void startCountDownThread() {

        final Runnable updater = new Runnable() {
            @Override
            public void run() {
                try {
                    if ((System.currentTimeMillis() < PreferenceManager
                            .getDefaultSharedPreferences(GameFragment.this.getActivity())
                            .getLong("next", 0))) {
                        long delta = PreferenceManager.getDefaultSharedPreferences(
                                GameFragment.this.getActivity()).getLong("next", 0)
                                - System.currentTimeMillis();

                        int minutes = (int) (delta / DateUtils.MINUTE_IN_MILLIS);
                        int secondes = (int) ((delta % DateUtils.MINUTE_IN_MILLIS) / DateUtils.SECOND_IN_MILLIS);

                        GameFragment.this.getActivity().getActionBar().setTitle(
                                getString(R.string.game_cooling) + minutes + "m" + secondes
                                        + "s"
                        );
                        handler.postDelayed(this, 1000);
                    } else
                        GameFragment.this.getActivity().getActionBar().setTitle(
                                getResources().getStringArray(R.array.menu)[6]);
                } catch (Exception e) {
                    e.printStackTrace();
                }
            }
        };
        handler.post(updater);

    }

    public void setupLayout(StationList.Stationinfo station, int id) {
        new DisplayPointsTask(station, ((LinearLayout) getView().findViewById(id)))
                .execute();

    }

    private class DisplayPointsTask extends AsyncTask<Void, Void, UtilsWeb.StationDepartures> {
        private StationList.Stationinfo station;
        private LinearLayout ll;

        public DisplayPointsTask(StationList.Stationinfo station, LinearLayout ll) {
            if (station != null)
                this.station = station;
            else
                this.station = null;
            this.ll = ll;
        }

        @Override
        protected UtilsWeb.StationDepartures doInBackground(Void... arg0) {
            String url = "http://api.irail.be/liveboard.php/?station="
                    + station.getStation().replace(" ", "%20") + "&format=JSON&fast=true";
            // System.out.println("Show station from: " + url);
            int i = 0;
            try {
                // Log.i(TAG, "Json Parser started..");
                Gson gson = new Gson();
                Reader r = new InputStreamReader(UtilsWeb.getJSONData(url,
                        GameFragment.this.getActivity()));
                UtilsWeb.Station station = gson.fromJson(r, UtilsWeb.Station.class);
                return station.getStationDepartures();

            } catch (Exception ex) {
                ex.printStackTrace();
                return null;
            }

        }

        @Override
        protected void onPostExecute(UtilsWeb.StationDepartures stationDepartures) {
            int delay = 0;
            int num = 1;
            if (stationDepartures != null)
                try {
                    for (UtilsWeb.StationDeparture aDeparture : stationDepartures.getStationDeparture()) {
                        if (!aDeparture.getDelay().contentEquals("0")) {
                            delay += Integer.valueOf(aDeparture.getDelay());
                            num += 1;
                        }

                    }
                } catch (Exception e) {
                    e.printStackTrace();
                }
            final int total = num;
            final int i = delay < 60 ? 1 : delay / 60;

            TextView tv = (TextView) ll.findViewById(R.id.closestTime);
            tv.setText("Score: " + num);
            ll.setOnClickListener(new View.OnClickListener() {

                @Override
                public void onClick(View arg0) {

                    if (station.distance > 0.1) {
                        crouton(getString(R.string.toofar) + " " + station.getDistance(), Style.ALERT);
                        return;
                    }
                    if ((System.currentTimeMillis() > PreferenceManager
                            .getDefaultSharedPreferences(GameFragment.this.getActivity())
                            .getLong("next", 0))) {
                        SharedPreferences.Editor e = PreferenceManager
                                .getDefaultSharedPreferences(
                                        GameFragment.this.getActivity()).edit();
                        Games.Achievements.unlock(getApiClient(), CHECKIN);
                        if (i >= 50)
                            Games.Achievements.unlock(getApiClient(), CHECKHUGEDELAY);
                        else if (i >= 20)
                            Games.Achievements.unlock(getApiClient(), CHECKDELAY);

                        newScore += total;
                        e.putLong(
                                "next",
                                (System.currentTimeMillis() + 10 * DateUtils.MINUTE_IN_MILLIS));
                        e.commit();
                        Games.Leaderboards.submitScore(getApiClient(), LEADER, newScore);
                        refreshScores();
                        startCountDownThread();

                    } else
                        croutonWarn(R.string.game_wait);

                }
            });


        }
    }

    private void croutonWarn(int i) {
        croutonWarn(getString(i));
    }

    private void croutonWarn(String string) {
        crouton(string, Style.ALERT);
    }

    public void debug(String s) {
        croutonWarn(s);
    }

    public void crouton(String text, Style style) {
        Crouton.makeText(this.getActivity(), text, style).show();
    }
}