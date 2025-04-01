package com.sinetech.latte

import android.app.AlertDialog
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.EditText
import androidx.fragment.app.Fragment
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.lagradost.cloudstream3.AcraApplication.Companion.getKey
import com.lagradost.cloudstream3.AcraApplication.Companion.setKey

class SettingsFragment : Fragment() {    
    private val IPTV_LISTS_KEY = "iptv_lists"
    private val SELECTED_LISTS_KEY = "selected_iptv_lists"

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        return inflater.inflate(R.layout.spor_kanallari_settings, container, false)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        val addListButton = view.findViewById<Button>(R.id.add_list_button)
        val recyclerView = view.findViewById<RecyclerView>(R.id.iptv_lists_recycler)

        recyclerView.layoutManager = LinearLayoutManager(context)
        val adapter = IptvListAdapter(getIptvLists().toMutableList()) { url, isSelected ->
            updateSelectedList(url, isSelected)
        }
        recyclerView.adapter = adapter

        addListButton.setOnClickListener {
            showAddListDialog(adapter)
        }
    }

    private fun showAddListDialog(adapter: IptvListAdapter) {
        val input = EditText(context)
        AlertDialog.Builder(context)
            .setTitle("IPTV Listesi Ekle")
            .setMessage("IPTV listesinin URL'sini girin:")
            .setView(input)
            .setPositiveButton("Ekle") { _, _ ->
                val url = input.text.toString().trim()
                if (url.isNotEmpty()) {
                    addIptvList(url)
                    adapter.updateLists(getIptvLists())
                }
            }
            .setNegativeButton("İptal", null)
            .show()
    }

    private fun getIptvLists(): List<String> {
        return getKey<List<String>>(IPTV_LISTS_KEY) ?: emptyList()
    }

    private fun addIptvList(url: String) {
        val currentLists = getIptvLists().toMutableList()
        if (!currentLists.contains(url)) {
            currentLists.add(url)
            setKey(IPTV_LISTS_KEY, currentLists)
        }
    }

    private fun updateSelectedList(url: String, isSelected: Boolean) {
        val selectedLists = getKey<List<String>>(SELECTED_LISTS_KEY)?.toMutableList() ?: mutableListOf()
        if (isSelected && !selectedLists.contains(url)) {
            selectedLists.add(url)
        } else if (!isSelected) {
            selectedLists.remove(url)
        }
        setKey(SELECTED_LISTS_KEY, selectedLists)
    }

    class IptvListAdapter(
        private var lists: MutableList<String>,
        private val onItemSelected: (String, Boolean) -> Unit
    ) : RecyclerView.Adapter<IptvListAdapter.ViewHolder>() {

        class ViewHolder(view: View) : RecyclerView.ViewHolder(view)

        fun updateLists(newLists: List<String>) {
            lists.clear()
            lists.addAll(newLists)
            notifyDataSetChanged()
        }

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
            val view = LayoutInflater.from(parent.context)
                .inflate(android.R.layout.simple_list_item_multiple_choice, parent, false)
            return ViewHolder(view)
        }

        override fun onBindViewHolder(holder: ViewHolder, position: Int) {
            val url = lists[position]
            (holder.itemView as android.widget.CheckedTextView).apply {
                text = url
                setOnClickListener {
                    isChecked = !isChecked
                    onItemSelected(url, isChecked)
                }
            }
        }

        override fun getItemCount() = lists.size
    }
}