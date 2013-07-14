/**
 * Copyright 2013 Carmen Alvarez
 *
 * This file is part of Scrum Chatter.
 *
 * Scrum Chatter is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 2 of the License, or
 * (at your option) any later version.
 *
 * Scrum Chatter is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with Scrum Chatter. If not, see <http://www.gnu.org/licenses/>.
 */
package ca.rmen.android.scrumchatter;

import java.io.File;
import java.util.Locale;

import android.app.Activity;
import android.app.ProgressDialog;
import android.content.DialogInterface;
import android.content.DialogInterface.OnClickListener;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.SharedPreferences.OnSharedPreferenceChangeListener;
import android.database.ContentObserver;
import android.net.Uri;
import android.os.AsyncTask;
import android.os.Bundle;
import android.preference.PreferenceManager;
import android.support.v4.app.Fragment;
import android.support.v4.app.FragmentManager;
import android.support.v4.app.FragmentPagerAdapter;
import android.support.v4.app.FragmentTransaction;
import android.support.v4.view.ViewPager;
import android.text.TextUtils;
import android.util.Log;
import android.widget.Toast;
import ca.rmen.android.scrumchatter.export.DBExport;
import ca.rmen.android.scrumchatter.export.FileExport;
import ca.rmen.android.scrumchatter.export.MeetingsExport;
import ca.rmen.android.scrumchatter.provider.DBImport;
import ca.rmen.android.scrumchatter.provider.TeamColumns;
import ca.rmen.android.scrumchatter.ui.MeetingsListFragment;
import ca.rmen.android.scrumchatter.ui.MembersListFragment;
import ca.rmen.android.scrumchatter.ui.ScrumChatterDialog;
import ca.rmen.android.scrumchatter.ui.Teams;
import ca.rmen.android.scrumchatter.ui.Teams.Team;

import com.actionbarsherlock.app.ActionBar;
import com.actionbarsherlock.app.SherlockFragmentActivity;
import com.actionbarsherlock.view.Menu;
import com.actionbarsherlock.view.MenuItem;

/**
 * The main screen of the app. Part of this code was generated by the ADT
 * plugin.
 */
public class MainActivity extends SherlockFragmentActivity implements ActionBar.TabListener { // NO_UCD (use default)

    private static final String TAG = Constants.TAG + "/" + MainActivity.class.getSimpleName();
    private static final int ACTIVITY_REQUEST_CODE_IMPORT = 1;
    /**
     * The {@link android.support.v4.view.PagerAdapter} that will provide
     * fragments for each of the sections. We use a {@link android.support.v4.app.FragmentPagerAdapter} derivative, which
     * will keep every loaded fragment in memory. If this becomes too memory
     * intensive, it may be best to switch to a {@link android.support.v4.app.FragmentStatePagerAdapter}.
     */
    private SectionsPagerAdapter mSectionsPagerAdapter;

    /**
     * The {@link ViewPager} that will host the section contents.
     */
    private ViewPager mViewPager;

    private Teams mTeams = new Teams(this);
    private Team mTeam = null;
    private int mTeamCount = 0;


    @Override
    protected void onCreate(Bundle savedInstanceState) {
        Log.v(TAG, "onCreate");
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        //StrictMode.setThreadPolicy(new ThreadPolicy.Builder().detectDiskReads().detectDiskWrites().penaltyLog().penaltyDeath().build());
        // Set up the action bar.
        final ActionBar actionBar = getSupportActionBar();
        actionBar.setNavigationMode(ActionBar.NAVIGATION_MODE_TABS);

        // Create the adapter that will return a fragment for each of the three
        // primary sections of the app.
        mSectionsPagerAdapter = new SectionsPagerAdapter(getSupportFragmentManager());

        // Set up the ViewPager with the sections adapter.
        mViewPager = (ViewPager) findViewById(R.id.pager);
        mViewPager.setAdapter(mSectionsPagerAdapter);

        // When swiping between different sections, select the corresponding
        // tab. We can also use ActionBar.Tab#select() to do this if we have
        // a reference to the Tab.
        mViewPager.setOnPageChangeListener(new ViewPager.SimpleOnPageChangeListener() {
            @Override
            public void onPageSelected(int position) {
                actionBar.setSelectedNavigationItem(position);
            }
        });

        // For each of the sections in the app, add a tab to the action bar.
        for (int i = 0; i < mSectionsPagerAdapter.getCount(); i++) {
            // Create a tab with text corresponding to the page title defined by
            // the adapter. Also specify this Activity object, which implements
            // the TabListener interface, as the callback (listener) for when
            // this tab is selected.
            actionBar.addTab(actionBar.newTab().setText(mSectionsPagerAdapter.getPageTitle(i)).setTabListener(this));
        }

        onTeamChanged();
        // If our activity was opened by choosing a file from a mail attachment, file browser, or other program, 
        // import the database from this file.
        Intent intent = getIntent();
        if (intent != null) {
            if (Intent.ACTION_VIEW.equals(intent.getAction())) importDB(intent.getData());
        }
    }

    @Override
    protected void onResume() {
        Log.v(TAG, "onResume");
        super.onResume();
        getContentResolver().registerContentObserver(TeamColumns.CONTENT_URI, true, mContentObserver);
        PreferenceManager.getDefaultSharedPreferences(this).registerOnSharedPreferenceChangeListener(mSharedPrefsListener);
    }

    @Override
    protected void onPause() {
        Log.v(TAG, "onPause");
        PreferenceManager.getDefaultSharedPreferences(this).unregisterOnSharedPreferenceChangeListener(mSharedPrefsListener);
        getContentResolver().unregisterContentObserver(mContentObserver);
        super.onPause();
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        // Inflate the menu; this adds items to the action bar if it is present.
        getSupportMenuInflater().inflate(R.menu.main, menu);
        return true;
    }

    @Override
    public boolean onPrepareOptionsMenu(Menu menu) {
        Log.v(TAG, "onPrepareOptionsMenu " + menu);
        MenuItem deleteItem = menu.findItem(R.id.action_team_delete);
        deleteItem.setEnabled(mTeamCount > 1);
        if (mTeam != null) {
            deleteItem.setTitle(getString(R.string.action_team_delete_name, mTeam.teamName));
            MenuItem renameItem = menu.findItem(R.id.action_team_rename);
            renameItem.setTitle(getString(R.string.action_team_rename_name, mTeam.teamName));
        }
        return super.onPrepareOptionsMenu(menu);
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        switch (item.getItemId()) {
            case R.id.action_team_switch:
                mTeams.selectTeam(mTeam);
                return true;
            case R.id.action_team_rename:
                mTeams.renameTeam(mTeam);
                return true;
            case R.id.action_team_delete:
                mTeams.deleteTeam(mTeam);
                return true;
            case R.id.action_import:
                Intent importIntent = new Intent(Intent.ACTION_GET_CONTENT);
                importIntent.setType("file/*");
                startActivityForResult(Intent.createChooser(importIntent, getResources().getText(R.string.action_import)), ACTIVITY_REQUEST_CODE_IMPORT);
                return true;
            case R.id.action_share:
                // Build a chooser dialog for the file format.
                ScrumChatterDialog.showChoiceDialog(this, R.string.export_choice_title, R.array.export_choices, new DialogInterface.OnClickListener() {

                    @Override
                    public void onClick(DialogInterface dialog, int which) {
                        String[] exportChoices = getResources().getStringArray(R.array.export_choices);
                        FileExport fileExport = null;
                        if (getString(R.string.export_format_excel).equals(exportChoices[which])) fileExport = new MeetingsExport(MainActivity.this);
                        else if (getString(R.string.export_format_db).equals(exportChoices[which])) fileExport = new DBExport(MainActivity.this);
                        shareFile(fileExport);
                    }
                });
                return true;
            case R.id.action_about:
                Intent intent = new Intent(this, AboutActivity.class);
                startActivity(intent);
                return true;
        }
        return super.onOptionsItemSelected(item);
    }

    @Override
    public void onTabSelected(ActionBar.Tab tab, FragmentTransaction fragmentTransaction) {
        // When the given tab is selected, switch to the corresponding page in
        // the ViewPager.
        mViewPager.setCurrentItem(tab.getPosition());
    }

    @Override
    public void onTabUnselected(ActionBar.Tab tab, FragmentTransaction fragmentTransaction) {}

    @Override
    public void onTabReselected(ActionBar.Tab tab, FragmentTransaction fragmentTransaction) {}

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent intent) {
        if (requestCode == ACTIVITY_REQUEST_CODE_IMPORT && resultCode == Activity.RESULT_OK) {
            if (intent.getData() == null) {
                Toast.makeText(this, R.string.import_result_no_file, Toast.LENGTH_SHORT).show();
                return;
            }
            final String filePath = intent.getData().getPath();
            if (TextUtils.isEmpty(filePath)) {
                Toast.makeText(this, R.string.import_result_no_file, Toast.LENGTH_SHORT).show();
                return;
            }
            final File file = new File(filePath);
            if (!file.exists()) {
                Toast.makeText(this, getString(R.string.import_result_file_does_not_exist, file.getName()), Toast.LENGTH_SHORT).show();
                return;
            }
            importDB(Uri.fromFile(file));
        } else {
            super.onActivityResult(requestCode, resultCode, intent);
        }
    }


    /**
     * Import the given database file. This will replace the current database.
     */
    private void importDB(final Uri uri) {
        ScrumChatterDialog.showDialog(this, getString(R.string.import_confirm_title), getString(R.string.import_confirm_message, uri.getEncodedPath()),
                new OnClickListener() {

                    @Override
                    public void onClick(DialogInterface dialog, int which) {
                        if (which == DialogInterface.BUTTON_POSITIVE) {
                            AsyncTask<Void, Void, Boolean> task = new AsyncTask<Void, Void, Boolean>() {
                                private ProgressDialog mProgressDialog;

                                @Override
                                protected void onPreExecute() {
                                    mProgressDialog = ProgressDialog.show(MainActivity.this, null, getString(R.string.progress_dialog_message), true);
                                }

                                @Override
                                protected Boolean doInBackground(Void... params) {
                                    try {
                                        Log.v(TAG, "Importing db from " + uri);
                                        DBImport.importDB(MainActivity.this, uri);
                                    } catch (Exception e) {
                                        Log.e(TAG, "Error importing db: " + e.getMessage(), e);
                                        return false;
                                    }
                                    return true;
                                }

                                @Override
                                protected void onPostExecute(Boolean result) {
                                    mProgressDialog.cancel();
                                    Toast.makeText(MainActivity.this, result ? R.string.import_result_success : R.string.import_result_failed,
                                            Toast.LENGTH_SHORT).show();
                                }


                            };
                            task.execute();
                        }
                    }
                });

    }

    /**
     * Share a file using an intent chooser.
     * 
     * @param fileExport The object responsible for creating the file to share.
     */
    private void shareFile(final FileExport fileExport) {
        final ProgressDialog progressDialog = ProgressDialog.show(this, null, getString(R.string.progress_dialog_message), true);
        AsyncTask<Void, Void, Boolean> asyncTask = new AsyncTask<Void, Void, Boolean>() {

            @Override
            protected Boolean doInBackground(Void... params) {
                return fileExport.export();
            }

            @Override
            protected void onPreExecute() {
                progressDialog.show();
            }

            @Override
            protected void onPostExecute(Boolean success) {
                progressDialog.dismiss();
                if (!success) Toast.makeText(MainActivity.this, R.string.export_error, Toast.LENGTH_LONG).show();
            }
        };
        asyncTask.execute();
    }

    /**
     * Called when the current team was changed.
     */
    private void onTeamChanged() {
        AsyncTask<Void, Void, Void> task = new AsyncTask<Void, Void, Void>() {

            @Override
            protected Void doInBackground(Void... arg0) {
                mTeam = mTeams.getCurrentTeam();
                mTeamCount = mTeams.getTeamCount();
                return null;
            }

            @Override
            protected void onPostExecute(Void result) {
                // If the user has renamed the default team or added other teams, show the current team name in the title
                if (mTeamCount > 1 || !mTeam.teamName.equals(TeamColumns.DEFAULT_TEAM_NAME)) getSupportActionBar().setTitle(mTeam.teamName);
                // otherwise the user doesn't care about team management: just show the app title.
                else
                    getSupportActionBar().setTitle(R.string.app_name);
                supportInvalidateOptionsMenu();
            }
        };
        task.execute();
    }

    /**
     * A {@link FragmentPagerAdapter} that returns a fragment corresponding to
     * one of the sections/tabs/pages.
     */
    private class SectionsPagerAdapter extends FragmentPagerAdapter {

        public SectionsPagerAdapter(FragmentManager fm) {
            super(fm);
        }

        @Override
        public Fragment getItem(int position) {
            // getItem is called to instantiate the fragment for the given page.
            // Return a DummySectionFragment (defined as a static inner class
            // below) with the page number as its lone argument.
            Fragment fragment = null;
            if (position == 1) fragment = new MembersListFragment();
            else
                fragment = new MeetingsListFragment();
            return fragment;
        }

        @Override
        public int getCount() {
            // Show 2 total pages.
            return 2;
        }

        @Override
        public CharSequence getPageTitle(int position) {
            Locale l = Locale.getDefault();
            switch (position) {
                case 0:
                    return getString(R.string.title_section_meetings).toUpperCase(l);
                case 1:
                    return getString(R.string.title_section_team).toUpperCase(l);
            }
            return null;
        }
    }

    private ContentObserver mContentObserver = new ContentObserver(null) {
        @Override
        public void onChange(boolean selfChange) {
            onTeamChanged();
        }
    };

    private OnSharedPreferenceChangeListener mSharedPrefsListener = new OnSharedPreferenceChangeListener() {
        @Override
        public void onSharedPreferenceChanged(SharedPreferences sharedPreferences, String key) {
            onTeamChanged();
        }
    };

}
