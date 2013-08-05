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
package ca.rmen.android.scrumchatter.ui;

import android.app.Dialog;
import android.app.ProgressDialog;
import android.os.Bundle;
import android.support.v4.app.DialogFragment;
import android.util.Log;
import ca.rmen.android.scrumchatter.Constants;

/**
 * Implements different types of dialog fragments (information, choice, confirmation, progress).
 */
public class ScrumChatterProgressDialogFragment extends DialogFragment {

    private static final String TAG = Constants.TAG + "/" + ScrumChatterProgressDialogFragment.class.getSimpleName();

    public ScrumChatterProgressDialogFragment() {
        super();
    }

    /**
     * @return an indeterminate, non-cancelable, ProgressDialog with a message.
     */
    @Override
    public Dialog onCreateDialog(Bundle savedInstanceState) {
        Log.v(TAG, "onCreateDialog: savedInstanceState = " + savedInstanceState);
        ProgressDialog dialog = new ProgressDialog(getActivity());
        Bundle arguments = getArguments();
        dialog.setMessage(arguments.getString(ScrumChatterDialogFragmentFactory.EXTRA_MESSAGE));
        dialog.setIndeterminate(true);
        dialog.setCancelable(false);
        DialogStyleHacks.styleDialog(getActivity(), dialog);
        return dialog;
    }
}
