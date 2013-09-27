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
package ca.rmen.android.scrumchatter.meeting.detail;

import android.app.Activity;
import android.content.Context;
import android.database.ContentObserver;
import android.database.Cursor;
import android.net.Uri;
import android.os.AsyncTask;
import android.os.Bundle;
import android.os.Handler;
import android.os.SystemClock;
import android.support.v4.app.FragmentActivity;
import android.support.v4.app.LoaderManager.LoaderCallbacks;
import android.support.v4.app.NavUtils;
import android.support.v4.content.CursorLoader;
import android.support.v4.content.Loader;
import android.text.format.DateUtils;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.View.OnClickListener;
import android.view.ViewGroup;
import android.widget.Chronometer;
import ca.rmen.android.scrumchatter.Constants;
import ca.rmen.android.scrumchatter.R;
import ca.rmen.android.scrumchatter.dialog.DialogFragmentFactory;
import ca.rmen.android.scrumchatter.meeting.Meetings;
import ca.rmen.android.scrumchatter.provider.MeetingColumns;
import ca.rmen.android.scrumchatter.provider.MeetingColumns.State;
import ca.rmen.android.scrumchatter.provider.MeetingMemberColumns;
import ca.rmen.android.scrumchatter.provider.MemberColumns;
import ca.rmen.android.scrumchatter.util.TextUtils;

import com.actionbarsherlock.app.SherlockFragmentActivity;
import com.actionbarsherlock.app.SherlockListFragment;
import com.actionbarsherlock.view.Menu;
import com.actionbarsherlock.view.MenuInflater;
import com.actionbarsherlock.view.MenuItem;

/**
 * Displays the list of members participating in a particular meeting.
 */
public class MeetingFragment extends SherlockListFragment { // NO_UCD (use default)

    private static final String TAG = Constants.TAG + "/" + MeetingFragment.class.getSimpleName();

    private static final int LOADER_ID = 0;
    private static final String EXTRA_MEETING_STATE = MeetingFragment.class.getPackage().getName() + ".meeting_state";
    private static final String EXTRA_MEETING_ID = MeetingFragment.class.getPackage().getName() + ".meeting_id";

    private MeetingCursorAdapter mAdapter;
    private final MeetingObserver mMeetingObserver;
    private View mBtnStopMeeting;
    private View mProgressBarHeader;
    private Chronometer mMeetingChronometer;
    private Meeting mMeeting;
    private Meetings mMeetings;

    public MeetingFragment() {
        super();
        Log.v(TAG, "Constructor");
        setHasOptionsMenu(true);
        mMeetingObserver = new MeetingObserver(new Handler());
    }

    @Override
    public void onAttach(Activity activity) {
        super.onAttach(activity);
        mMeetings = new Meetings((FragmentActivity) activity);
    }

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState) {
        Log.v(TAG, "onCreateView: savedInstanceState = " + savedInstanceState);
        View view = inflater.inflate(R.layout.meeting_fragment, null);
        mBtnStopMeeting = view.findViewById(R.id.btn_stop_meeting);
        mMeetingChronometer = (Chronometer) view.findViewById(R.id.tv_meeting_duration);
        mProgressBarHeader = view.findViewById(R.id.header_progress_bar);

        mBtnStopMeeting.setOnClickListener(mOnClickListener);
        if (savedInstanceState != null) {
            long meetingId = savedInstanceState.getLong(EXTRA_MEETING_ID);
            Uri uri = Uri.withAppendedPath(MeetingColumns.CONTENT_URI, String.valueOf(meetingId));
            getActivity().getContentResolver().registerContentObserver(uri, false, mMeetingObserver);
        }
        return view;
    }

    @Override
    public void onDestroyView() {
        Log.v(TAG, "onDestroyView");
        if (mMeeting != null) {
            Log.v(TAG, "register observer " + mMeetingObserver);
            getActivity().getContentResolver().unregisterContentObserver(mMeetingObserver);
            getActivity().getContentResolver().registerContentObserver(mMeeting.getUri(), false, mMeetingObserver);
        }
        super.onDestroyView();
    }

    @Override
    public void onSaveInstanceState(Bundle outState) {
        Log.v(TAG, "onSaveInstanceState: outState = " + outState);
        super.onSaveInstanceState(outState);
        if (mMeeting != null) outState.putLong(EXTRA_MEETING_ID, mMeeting.getId());
    }

    @Override
    public void onCreateOptionsMenu(Menu menu, MenuInflater inflater) {
        Log.v(TAG, "onCreateOptionsMenu: mMeeting =" + mMeeting);

        inflater.inflate(R.menu.meeting_menu, menu);
        // Only share finished meetings
        final MenuItem shareItem = menu.findItem(R.id.action_share);
        shareItem.setVisible(mMeeting != null && mMeeting.getState() == State.FINISHED);
        // Delete a meeting in any state.
        final MenuItem deleteItem = menu.findItem(R.id.action_delete_meeting);
        deleteItem.setVisible(mMeeting != null);
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        Log.v(TAG, "onOptionsItemSelected: item = " + item.getItemId() + ": " + item.getTitle());
        if (getActivity().isFinishing()) {
            Log.v(TAG, "User clicked on a menu item while the activity is finishing.  Surely a monkey is involved");
            return true;
        }
        switch (item.getItemId()) {
        // Respond to the action bar's Up/Home button
            case android.R.id.home:
                NavUtils.navigateUpFromSameTask(getActivity());
                return true;
            case R.id.action_share:
                mMeetings.export(mMeeting.getId());
                return true;
            case R.id.action_delete_meeting:
                mMeetings.confirmDelete(mMeeting);
                return true;
            default:
                return super.onOptionsItemSelected(item);
        }
    }


    void loadMeeting(long meetingId) {
        Log.v(TAG, "loadMeeting: current meeting = " + mMeeting + ", new meeting id = " + meetingId);
        if (mMeeting == null || meetingId != mMeeting.getId()) {
            Uri uri = Uri.withAppendedPath(MeetingColumns.CONTENT_URI, String.valueOf(meetingId));
            getActivity().getContentResolver().unregisterContentObserver(mMeetingObserver);
            getActivity().getContentResolver().registerContentObserver(uri, false, mMeetingObserver);
        }

        AsyncTask<Long, Void, Meeting> task = new AsyncTask<Long, Void, Meeting>() {

            @Override
            protected Meeting doInBackground(Long... params) {
                long meetingId = params[0];
                Log.v(TAG, "doInBackground: meetingId = " + meetingId);

                Context context = getActivity();
                if (context == null) {
                    Log.w(TAG, "No longer attached to activity: can't load meeting");
                    return null;
                }
                Meeting meeting = Meeting.read(getActivity(), meetingId);
                return meeting;
            }

            @Override
            protected void onPostExecute(Meeting meeting) {
                Log.v(TAG, "onPostExecute: meeting = " + meeting);
                // Don't do anything if the activity has been closed in the meantime
                SherlockFragmentActivity activity = (SherlockFragmentActivity) getActivity();
                if (activity == null) {
                    Log.w(TAG, "No longer attached to the activity: can't load meeting members");
                    return;
                }
                mMeeting = meeting;
                if (mMeeting == null) {
                    Log.v(TAG, "No more meeting, quitting this activity: finishing=" + activity.isFinishing());
                    if (!activity.isFinishing()) {
                        activity.getSupportLoaderManager().destroyLoader(LOADER_ID);
                        mBtnStopMeeting.setVisibility(View.INVISIBLE);
                        activity.getContentResolver().unregisterContentObserver(mMeetingObserver);
                        activity.finish();
                    }
                    return;
                }
                Bundle bundle = new Bundle(1);
                bundle.putInt(EXTRA_MEETING_STATE, mMeeting.getState().ordinal());
                if (mAdapter == null) {
                    mAdapter = new MeetingCursorAdapter(activity, mOnClickListener);
                    getLoaderManager().initLoader(LOADER_ID, bundle, mLoaderCallbacks);
                } else {
                    getLoaderManager().restartLoader(LOADER_ID, bundle, mLoaderCallbacks);
                }
                activity.supportInvalidateOptionsMenu();
                Log.v(TAG, "meetingState = " + mMeeting.getState());
                // Show the "stop meeting" button if the meeting is not finished.
                mBtnStopMeeting.setVisibility(mMeeting.getState() == State.NOT_STARTED || mMeeting.getState() == State.IN_PROGRESS ? View.VISIBLE
                        : View.INVISIBLE);
                // Only enable the "stop meeting" button if the meeting is in progress.
                mBtnStopMeeting.setEnabled(mMeeting.getState() == State.IN_PROGRESS);
                activity.getSupportActionBar().setTitle(TextUtils.formatDateTime(activity, mMeeting.getStartDate()));

                // Show the horizontal progress bar for in progress meetings
                mProgressBarHeader.setVisibility(mMeeting.getState() == State.IN_PROGRESS ? View.VISIBLE : View.INVISIBLE);

                // Update the chronometer
                if (mMeeting.getState() == State.IN_PROGRESS) {
                    // If the meeting is in progress, show the Chronometer.
                    long timeSinceMeetingStartedMillis = System.currentTimeMillis() - mMeeting.getStartDate();
                    mMeetingChronometer.setBase(SystemClock.elapsedRealtime() - timeSinceMeetingStartedMillis);
                    mMeetingChronometer.start();
                } else if (mMeeting.getState() == State.FINISHED) {
                    // For finished meetings, show the duration we retrieved from the db.
                    mMeetingChronometer.stop();
                    mMeetingChronometer.setText(DateUtils.formatElapsedTime(mMeeting.getDuration()));
                }
            }
        };
        task.execute(meetingId);
    }

    /**
     * Stop the meeting. Set the state to finished, stop the chronometer, hide the "stop meeting" button, persist the meeting duration, and stop the
     * chronometers for all team members who are still talking.
     */
    void stopMeeting() {
        AsyncTask<Meeting, Void, Void> task = new AsyncTask<Meeting, Void, Void>() {

            @Override
            protected Void doInBackground(Meeting... meeting) {
                meeting[0].stop();
                return null;
            }
        };
        task.execute(mMeeting);
    }

    void deleteMeeting() {
        AsyncTask<Meeting, Void, Void> task = new AsyncTask<Meeting, Void, Void>() {

            @Override
            protected Void doInBackground(Meeting... meeting) {
                meeting[0].delete();
                return null;
            }
        };
        task.execute(mMeeting);
    }

    private LoaderCallbacks<Cursor> mLoaderCallbacks = new LoaderCallbacks<Cursor>() {

        @Override
        public Loader<Cursor> onCreateLoader(int loaderId, Bundle bundle) {
            Log.v(TAG, "onCreateLoader, loaderId = " + loaderId + ", bundle = " + bundle);
            State meetingState = State.values()[bundle.getInt(EXTRA_MEETING_STATE, State.NOT_STARTED.ordinal())];
            String selection = null;
            String orderBy = MemberColumns.NAME + " COLLATE NOCASE";
            if (meetingState == State.FINISHED) {
                selection = MeetingMemberColumns.DURATION + ">0";
                orderBy = MeetingMemberColumns.DURATION + " DESC";
            }
            String[] projection = new String[] { MeetingMemberColumns._ID, MemberColumns.NAME, MeetingMemberColumns.DURATION, MeetingColumns.STATE,
                    MeetingMemberColumns.TALK_START_TIME };

            Uri uri = Uri.withAppendedPath(MeetingMemberColumns.CONTENT_URI, String.valueOf(mMeeting.getId()));
            CursorLoader loader = new CursorLoader(getActivity(), uri, projection, selection, null, orderBy);
            return loader;
        }

        @Override
        public void onLoadFinished(Loader<Cursor> loader, Cursor cursor) {
            Log.v(TAG, "onLoadFinished");
            if (getListAdapter() == null) {
                setListAdapter(mAdapter);
                getActivity().findViewById(R.id.progressContainer).setVisibility(View.GONE);
            }
            mAdapter.changeCursor(cursor);
        }

        @Override
        public void onLoaderReset(Loader<Cursor> loader) {
            Log.v(TAG, "onLoaderReset");
            mAdapter.changeCursor(null);
        }
    };

    private class MeetingObserver extends ContentObserver {

        private final String TAG = MeetingFragment.TAG + "/" + MeetingObserver.class.getSimpleName();

        public MeetingObserver(Handler handler) {
            super(handler);
            Log.v(TAG, "Constructor");
        }

        /**
         * Called when a meeting changes. Reload the list of members for this meeting.
         */
        @Override
        public void onChange(boolean selfChange) {
            Log.v(TAG, "MeetingObserver onChange, selfChange: " + selfChange + ", mMeeting = " + mMeeting);
            super.onChange(selfChange);
            loadMeeting(mMeeting.getId());
        }
    };

    /**
     * Manage clicks on items inside the meeting fragment.
     */
    private final OnClickListener mOnClickListener = new OnClickListener() {

        /**
         * Switch a member from the talking to non-talking state:
         * 
         * If they were talking, they will no longer be talking, and their button will go back to a "start" button.
         * 
         * If they were not talking, they will start talking, and their button will be a "stop" button.
         * 
         * @param memberId
         */
        private void toggleTalkingMember(final long memberId) {
            Log.v(TAG, "toggleTalkingMember " + memberId);
            AsyncTask<Meeting, Void, Void> task = new AsyncTask<Meeting, Void, Void>() {

                @Override
                protected Void doInBackground(Meeting... meeting) {
                    if (meeting[0].getState() != State.IN_PROGRESS) meeting[0].start();
                    meeting[0].toggleTalkingMember(memberId);
                    return null;
                }
            };
            task.execute(mMeeting);
        };

        @Override
        public void onClick(View v) {
            Log.v(TAG, "onClick, view: " + v);
            switch (v.getId()) {
            // Start or stop the team member talking
                case R.id.btn_start_stop_member:
                    long memberId = (Long) v.getTag();
                    toggleTalkingMember(memberId);
                    break;
                // Stop the whole meeting.
                case R.id.btn_stop_meeting:
                    // Let's ask him if he's sure.
                    DialogFragmentFactory.showConfirmDialog(getActivity(), getString(R.string.action_stop_meeting), getString(R.string.dialog_confirm),
                            R.id.btn_stop_meeting, null);
                    break;
            }
        }
    };
}
