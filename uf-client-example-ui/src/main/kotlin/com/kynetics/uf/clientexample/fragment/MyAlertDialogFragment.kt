/*
 * Copyright © 2017-2023  Kynetics  LLC
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.kynetics.uf.clientexample.fragment

import android.app.Dialog
import android.os.Bundle
import androidx.appcompat.app.AlertDialog
import com.kynetics.uf.clientexample.activity.MainActivity

class MyAlertDialogFragment : androidx.fragment.app.DialogFragment() {

    override fun onCreateDialog(savedInstanceState: Bundle?): Dialog {
        val dialogType = requireArguments().getString(ARG_DIALOG_TYPE)
        val titleResource = resources.getIdentifier(String.format("%s_%s", dialogType?.lowercase(), "title"),
            "string", requireActivity().packageName)
        val contentResource = resources.getIdentifier(String.format("%s_%s", dialogType?.lowercase(), "content"),
            "string", requireActivity().packageName)

        return AlertDialog.Builder(requireActivity())
            // .setIcon(R.drawable.alert_dialog_icon)
            .setTitle(titleResource)
            .setMessage(contentResource)
            .setPositiveButton(android.R.string.ok
            ) { _, _ -> (activity as MainActivity).sendPermissionResponse(true) }
            .setNegativeButton(android.R.string.cancel
            ) { _, _ -> (activity as MainActivity).sendPermissionResponse(false) }
            .create()
    }

    companion object {
        private val ARG_DIALOG_TYPE = "DIALOG_TYPE"
        fun newInstance(dialogType: String): MyAlertDialogFragment {
            val frag = MyAlertDialogFragment()
            val args = Bundle()
            args.putString(ARG_DIALOG_TYPE, dialogType)
            frag.arguments = args
            frag.isCancelable = false
            return frag
        }
    }
}
