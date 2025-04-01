package com.sinetech.latte

import android.app.Dialog
import android.os.Bundle
import android.view.View
import android.widget.EditText
import androidx.appcompat.app.AlertDialog
import androidx.fragment.app.DialogFragment
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import android.view.LayoutInflater
import android.widget.Button
import android.widget.CheckBox
import android.widget.Toast
import com.lagradost.cloudstream3.AcraApplication.Companion.getKey
import com.lagradost.cloudstream3.R

class Settings(private val plugin: IPTVListemPlugin) : DialogFragment() {
    private lateinit var iptvListem: IPTVListemPlugin.IPTVListem

    override fun onCreateDialog(savedInstanceState: Bundle?): Dialog {
        val builder = AlertDialog.Builder(requireContext())
        val inflater = requireActivity().layoutInflater
        val view = inflater.inflate(R.layout.settings_iptv, null)

        val recyclerView = view.findViewById<RecyclerView>(R.id.recycler_view)
        val addButton = view.findViewById<Button>(R.id.add_button)
        val urlInput = view.findViewById<EditText>(R.id.url_input)

        iptvListem = plugin.mainApi as IPTVListemPlugin.IPTVListem
        val lists = iptvListem.getIptvLists()
        val selectedLists = iptvListem.getSelectedLists()

        val adapter = IptvListAdapter(lists.toMutableList(), selectedLists.toMutableList()) { url, isSelected ->
            if (isSelected) {
                if (!selectedLists.contains(url)) {
                    selectedLists.add(url)
                }
            } else {
                selectedLists.remove(url)
            }
            iptvListem.updateSelectedLists(selectedLists)
        }

        recyclerView.layoutManager = LinearLayoutManager(context)
        recyclerView.adapter = adapter

        addButton.setOnClickListener {
            val url = urlInput.text.toString().trim()
            if (url.isNotEmpty()) {
                iptvListem.addIptvList(url)
                adapter.addItem(url)
                urlInput.text.clear()
            } else {
                Toast.makeText(context, "Lütfen bir URL girin", Toast.LENGTH_SHORT).show()
            }
        }

        builder.setView(view)
            .setTitle("IPTV Listeleri")
            .setPositiveButton("Tamam") { _, _ -> }

        return builder.create()
    }
}

class IptvListAdapter(
    private val items: MutableList<String>,
    private val selectedItems: MutableList<String>,
    private val onItemSelected: (String, Boolean) -> Unit
) : RecyclerView.Adapter<IptvListAdapter.ViewHolder>() {

    class ViewHolder(view: View) : RecyclerView.ViewHolder(view) {
        val checkbox: CheckBox = view.findViewById(R.id.checkbox)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_iptv_list, parent, false)
        return ViewHolder(view)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        val item = items[position]
        holder.checkbox.text = item
        holder.checkbox.isChecked = selectedItems.contains(item)

        holder.checkbox.setOnCheckedChangeListener { _, isChecked ->
            onItemSelected(item, isChecked)
        }

        holder.itemView.setOnLongClickListener {
            val context = holder.itemView.context
            AlertDialog.Builder(context)
                .setTitle("Listeyi Sil")
                .setMessage("Bu IPTV listesini silmek istediğinizden emin misiniz?")
                .setPositiveButton("Evet") { _, _ ->
                    val activity = context as? Activity
                    if (activity != null) {
                        val currentFragment = activity.supportFragmentManager
                            .findFragmentByTag("Settings") as? Settings
                        
                        currentFragment?.iptvListem?.removeIptvList(item)
                        items.removeAt(position)
                        notifyItemRemoved(position)
                    }
                }
                .setNegativeButton("Hayır", null)
                .show()
            true
        }
    }

    override fun getItemCount() = items.size

    fun addItem(item: String) {
        items.add(item)
        notifyItemInserted(items.size - 1)
    }
}