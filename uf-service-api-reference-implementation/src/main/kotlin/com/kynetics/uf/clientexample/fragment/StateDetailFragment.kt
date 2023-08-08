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

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ArrayAdapter
import com.kynetics.uf.android.api.v1.UFServiceMessageV1
import com.kynetics.uf.clientexample.data.MessageHistory
import com.kynetics.uf.clientexample.data.format
import com.kynetics.uf.clientexample.data.percentFormat
import com.kynetics.uf.clientexample.databinding.StateDetailBinding
import kotlin.math.pow

/**
 * A fragment representing a single State detail screen.
 * This fragment is either contained in a [StateListActivity]
 * in two-pane mode (on tablets) or a [StateDetailActivity]
 * on handsets.
 */
class StateDetailFragment : androidx.fragment.app.Fragment(), UFServiceInteractionFragment {

    /**
     * The dummy content this fragment is presenting.
     */
    private var item: MessageHistory.StateEntry? = null
    private var adapter: ArrayAdapter<MessageHistory.EventEntry>? = null
    private var binding: StateDetailBinding? = null
    private var stateDetail: StateDetail? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        arguments?.let {
            if (it.containsKey(ARG_ITEM_ID)) {
                // Load the dummy content specified by the fragment
                // arguments. In a real-world scenario, use a Loader
                // to load content from a content provider.
                item = MessageHistory.ITEM_MAP[it.getLong(ARG_ITEM_ID)]
            }
        }
    }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        binding = StateDetailBinding.inflate(inflater, container, false)
        val view = binding!!.root
        // here data must be an instance of the class MarsDataProvider
        if (item != null) {
            binding!!.data = item!!
            adapter = ArrayAdapter(requireContext(), android.R.layout.simple_list_item_1, item!!.events)
            binding!!.eventsList.adapter = adapter
        }
        binding!!.detailsList.adapter
        customizeCardView()
        return view
    }

    class StateDetail(downloading: UFServiceMessageV1.State.Downloading) {
        val details: List<FileDownloading>
        private val detailMap: Map<String, FileDownloading>

        init {
            detailMap = downloading.artifacts.map {
                it.name to FileDownloading(it.name, it.size, 0.0)
            }.toMap()
            details = detailMap.values.toList()
        }

        fun updateDetail(key: String, value: Double) {
            this.detailMap[key]?.percent = value
        }

        fun containsKey(key: String): Boolean {
            return detailMap.containsKey(key)
        }

        data class FileDownloading(val fileName: String, val size: Long, var percent: Double = 0.0) {
            override fun toString(): String {
                return "$fileName (${(size / 2.0.pow(20.0)).format(2)} MB) - Downloaded ${percent.percentFormat()}"
            }
        }
    }

    private fun customizeCardView() {
        when (item!!.state) {
            is UFServiceMessageV1.State.Downloading -> {
                stateDetail = StateDetail(item!!.state as UFServiceMessageV1.State.Downloading)
                binding?.detailsTitle?.text = "Files to download:"
                binding?.detailsTitle?.visibility = View.VISIBLE
                binding?.detailsList?.visibility = View.VISIBLE
                binding?.detailsList?.adapter =
                    ArrayAdapter(requireContext(), android.R.layout.simple_list_item_1, stateDetail!!.details)

                item!!.events.forEach {
                    onMessageReceived(it.event)
                }
            }

            else -> {
                binding?.detailsList?.adapter =
                    ArrayAdapter(requireContext(), android.R.layout.simple_list_item_1,
                        listOf<String>())
            }
        }
    }

    private fun updateDetails(key: String, percent: Double) {
        if (stateDetail?.containsKey(key) == true) {
            stateDetail?.updateDetail(key, percent)
            (binding?.detailsList?.adapter as ArrayAdapter<*>).notifyDataSetChanged()
        }
    }

    companion object {
        /**
         * The fragment argument representing the item ID that this fragment
         * represents.
         */
        const val ARG_ITEM_ID = "item_id"
    }

    // todo replace with object observer
    override fun onMessageReceived(message: UFServiceMessageV1) {
        if (message is UFServiceMessageV1.Event) {
            item?.unread = 0
            adapter?.notifyDataSetChanged()
            val itemStateIsDownloading = item?.state is UFServiceMessageV1.State.Downloading
            when {
                message is UFServiceMessageV1.Event.StartDownloadFile && itemStateIsDownloading ->
                    updateDetails(message.fileName, 0.0)
                message is UFServiceMessageV1.Event.DownloadProgress && itemStateIsDownloading ->
                    updateDetails(message.fileName, message.percentage)
                message is UFServiceMessageV1.Event.FileDownloaded && itemStateIsDownloading ->
                    updateDetails(message.fileDownloaded, 1.0)
                else -> {}
            }
        }
    }
}
