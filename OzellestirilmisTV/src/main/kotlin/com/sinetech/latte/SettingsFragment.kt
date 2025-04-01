package com.sinetech.latte

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.*
import androidx.fragment.app.Fragment
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.lagradost.cloudstream3.R
import android.content.Context
import android.content.SharedPreferences
import android.webkit.URLUtil

class SettingsFragment : Fragment() {
    private lateinit var playlistUrlInput: EditText
    private lateinit var addPlaylistButton: Button
    private lateinit var playlistRecyclerView: RecyclerView
    private lateinit var playlistAdapter: PlaylistAdapter
    private lateinit var settingsManager: OzellestirilmisTV.SettingsManager

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        return inflater.inflate(R.layout.settings_layout, container, false)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        val sharedPref = context?.getSharedPreferences("OzellestirilmisTV", Context.MODE_PRIVATE)
        settingsManager = OzellestirilmisTV.SettingsManager(sharedPref)

        playlistUrlInput = view.findViewById(R.id.playlistUrlInput)
        addPlaylistButton = view.findViewById(R.id.addPlaylistButton)
        playlistRecyclerView = view.findViewById(R.id.playlistRecyclerView)

        setupRecyclerView()
        setupAddButton()
    }

    private fun setupRecyclerView() {
        playlistAdapter = PlaylistAdapter(
            settingsManager.getPlaylistUrls().toMutableList(),
            settingsManager.getEnabledPlaylists(),
            object : PlaylistAdapter.PlaylistActionListener {
                override fun onPlaylistEnabled(url: String, enabled: Boolean) {
                    settingsManager.setPlaylistEnabled(url, enabled)
                }

                override fun onPlaylistRemoved(url: String) {
                    settingsManager.removePlaylistUrl(url)
                    playlistAdapter.removePlaylist(url)
                }
            }
        )

        playlistRecyclerView.apply {
            layoutManager = LinearLayoutManager(context)
            adapter = playlistAdapter
        }
    }

    private fun setupAddButton() {
        addPlaylistButton.setOnClickListener {
            val url = playlistUrlInput.text.toString().trim()
            if (url.isEmpty()) {
                Toast.makeText(context, "URL boş olamaz", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }

            if (!URLUtil.isValidUrl(url)) {
                Toast.makeText(context, "Geçersiz URL formatı", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }

            if (settingsManager.getPlaylistUrls().contains(url)) {
                Toast.makeText(context, "Bu URL zaten eklenmiş", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }

            settingsManager.savePlaylistUrl(url)
            settingsManager.setPlaylistEnabled(url, true)
            playlistAdapter.addPlaylist(url)
            playlistUrlInput.text.clear()
        }
    }
}

class PlaylistAdapter(
    private val playlists: MutableList<String>,
    private val enabledPlaylists: Set<String>,
    private val listener: PlaylistActionListener
) : RecyclerView.Adapter<PlaylistAdapter.ViewHolder>() {

    interface PlaylistActionListener {
        fun onPlaylistEnabled(url: String, enabled: Boolean)
        fun onPlaylistRemoved(url: String)
    }

    class ViewHolder(view: View) : RecyclerView.ViewHolder(view) {
        val checkbox: CheckBox = view.findViewById(R.id.playlistEnabledCheckbox)
        val urlText: TextView = view.findViewById(R.id.playlistUrlText)
        val removeButton: ImageButton = view.findViewById(R.id.removePlaylistButton)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.playlist_item, parent, false)
        return ViewHolder(view)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        val url = playlists[position]
        holder.urlText.text = url
        holder.checkbox.isChecked = enabledPlaylists.contains(url)

        holder.checkbox.setOnCheckedChangeListener { _, isChecked ->
            listener.onPlaylistEnabled(url, isChecked)
        }

        holder.removeButton.setOnClickListener {
            listener.onPlaylistRemoved(url)
        }
    }

    override fun getItemCount() = playlists.size

    fun addPlaylist(url: String) {
        if (!playlists.contains(url)) {
            playlists.add(url)
            notifyItemInserted(playlists.size - 1)
        }
    }

    fun removePlaylist(url: String) {
        val position = playlists.indexOf(url)
        if (position != -1) {
            playlists.removeAt(position)
            notifyItemRemoved(position)
        }
    }
}