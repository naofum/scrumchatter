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
package ca.rmen.android.scrumchatter.adapter;

import android.content.Context;
import android.database.Cursor;
import android.support.v4.widget.CursorAdapter;
import android.text.format.DateUtils;
import android.view.LayoutInflater;
import android.view.View;
import android.view.View.OnClickListener;
import android.view.ViewGroup;
import android.widget.TextView;
import ca.rmen.android.scrumchatter.R;
import ca.rmen.android.scrumchatter.provider.MeetingColumns;
import ca.rmen.android.scrumchatter.provider.MeetingColumns.State;
import ca.rmen.android.scrumchatter.provider.MeetingCursorWrapper;

/**
 * Adapter for the list of meetings.
 */
public class MeetingsCursorAdapter extends CursorAdapter {
	private final OnClickListener mOnClickListener;

	public MeetingsCursorAdapter(Context context,
			OnClickListener onClickListener) {
		super(context, null, true);
		mOnClickListener = onClickListener;
	}

	@Override
	public void bindView(View view, Context context, Cursor cursor) {
		fillView(context, view, cursor);
	}

	@Override
	public View newView(Context context, Cursor cursor, ViewGroup viewGroup) {
		LayoutInflater inflater = (LayoutInflater) context
				.getSystemService(Context.LAYOUT_INFLATER_SERVICE);
		View view = inflater.inflate(R.layout.meeting_list_item, null);
		fillView(context, view, cursor);
		return view;
	}

	/**
	 * Fill the view's fields with data from the given meeting.
	 * 
	 * @param view
	 *            a recently created view, or a recycled view
	 * @param cursor
	 *            a row for one meeting
	 */
	private void fillView(Context context, View view, Cursor cursor) {
		// Get the data from the cursor
		@SuppressWarnings("resource")
		MeetingCursorWrapper cursorWrapper = new MeetingCursorWrapper(cursor);
		long id = cursorWrapper.getId();
		String date = DateUtils.formatDateTime(mContext,
				cursorWrapper.getMeetingDate(), DateUtils.FORMAT_SHOW_DATE
						| DateUtils.FORMAT_SHOW_TIME);
		String duration = DateUtils.formatElapsedTime(cursorWrapper
				.getDuration());
		MeetingColumns.State state = cursorWrapper.getState();

		// TODO cache the meeting state names
		String[] meetingStates = context.getResources().getStringArray(
				R.array.meeting_states);
		String stateName = meetingStates[state.ordinal()];

		// Find the views we need to set up.
		TextView tvDate = (TextView) view.findViewById(R.id.tv_meeting_date);
		TextView tvDuration = (TextView) view
				.findViewById(R.id.tv_meeting_duration);
		View btnDelete = view.findViewById(R.id.btn_delete);

		// Fill the date view.
		tvDate.setText(date);

		// Fill the duration view. We will only show the duration if
		// the meeting is finished. For not-started or in-progress
		// meetings, we show the state.
		if (state == State.FINISHED)
			tvDuration.setText(duration);
		else
			tvDuration.setText(stateName);

		// Forward clicks to our OnClickListener. We put the cache in the tag
		// so the listener can have access to data it needs to display
		// (showing the meeting date in the confirmation dialog to delete
		// a meeting).
		MeetingItemCache cache = new MeetingItemCache(id, date);
		btnDelete.setTag(cache);
		btnDelete.setOnClickListener(mOnClickListener);
		tvDate.setTag(cache);
		tvDate.setOnClickListener(mOnClickListener);
	}

	/**
	 * This is not a real cache yet: we just store the minimum data here needed
	 * by the OnClickListener.
	 */
	public static class MeetingItemCache {
		public final long id;
		public final String date;

		private MeetingItemCache(long id, String date) {
			this.id = id;
			this.date = date;
		}
	}
}
