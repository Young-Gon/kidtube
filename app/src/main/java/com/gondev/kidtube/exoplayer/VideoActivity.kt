/*
 * Copyright 2018 Google LLC. All rights reserved.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.gondev.kidtube.exoplayer

import android.content.Context
import android.content.Intent
import android.media.AudioManager
import android.os.Build
import android.os.Bundle
import android.support.v4.media.MediaDescriptionCompat
import android.support.v4.media.session.MediaSessionCompat
import android.support.v7.app.AppCompatActivity
import android.support.v7.widget.LinearLayoutManager
import android.support.v7.widget.RecyclerView
import android.support.v7.widget.SimpleItemAnimator
import android.util.Log
import android.view.View
import android.view.ViewGroup
import android.widget.ImageButton
import com.bumptech.glide.Glide
import com.gondev.kidstube.adapter.ArrayListRecyclerViewAdapter
import com.gondev.kidstube.adapter.ViewBinder
import com.gondev.kidtube.R
import com.gondev.kidtube.VideoClipData
import com.google.android.exoplayer2.Player
import com.google.android.exoplayer2.ext.mediasession.MediaSessionConnector
import com.google.android.exoplayer2.ext.mediasession.TimelineQueueNavigator
import kotlinx.android.synthetic.main.activity_video.*
import kotlinx.android.synthetic.main.item_video_clip_for_player.view.*
import org.jetbrains.anko.AnkoLogger
import org.jetbrains.anko.sdk25.coroutines.onClick


/**
 * Allows playback of videos that are in a playlist, using [PlayerHolder] to load the and render
 * it to the [com.google.android.exoplayer2.ui.PlayerView] to render the video output. Supports
 * [MediaSessionCompat] and picture in picture as well.
 */

fun Context.startVideoActivity(videoClip: ArrayList<VideoClipData>, position:Int =0) =
        startActivity(Intent(this, VideoActivity::class.java)
                .putExtra(INTENT_VIDEO_CLIP, videoClip)
                .putExtra(INTENT_VIDEO_POSITION, position))

private const val INTENT_VIDEO_CLIP = "video_clip"
private const val INTENT_VIDEO_POSITION = "video_position"

class VideoActivity : AppCompatActivity(), AnkoLogger {
    private val mediaSession: MediaSessionCompat by lazy { createMediaSession() }
    private val mediaSessionConnector: MediaSessionConnector by lazy {
        createMediaSessionConnector()
    }
    private val playerState by lazy { PlayerState() }
    private lateinit var playerHolder: PlayerHolder
    private val mediaCatalog: MutableList<MediaDescriptionCompat> = mutableListOf()
    private lateinit var recyclerView:RecyclerView

    // Android lifecycle hooks.
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_video)
        // While the user is in the app, the volume controls should adjust the music volume.
        volumeControlStream = AudioManager.STREAM_MUSIC

        val decorView = window.decorView
        var uiOption = decorView.systemUiVisibility

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.ICE_CREAM_SANDWICH)
            uiOption = uiOption or View.SYSTEM_UI_FLAG_HIDE_NAVIGATION

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.JELLY_BEAN)
            uiOption = uiOption or View.SYSTEM_UI_FLAG_FULLSCREEN

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.KITKAT)
            uiOption = uiOption or View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY

        decorView.setSystemUiVisibility(uiOption)

        val clipList: ArrayList<VideoClipData> =intent.getParcelableArrayListExtra(com.gondev.kidtube.exoplayer.INTENT_VIDEO_CLIP)
        val clip_position=intent.getIntExtra(com.gondev.kidtube.exoplayer.INTENT_VIDEO_POSITION,0)
        playerState.window=clip_position

        clipList.forEachIndexed { index, videoClipData ->
            mediaCatalog.add(
                    with(MediaDescriptionCompat.Builder()){
                        setDescription("local video")
                        setMediaId(index.toString())
                        setMediaUri(videoClipData.videoUri)
                        setTitle(videoClipData.title)
                        setSubtitle("local video")
                        build()
                    }
            )
        }

        val adapter= VideoClipListAdapter(clipList, clip_position)
        exoplayerview_activity_video.findViewById<ImageButton>(R.id.imageButton).onClick { finish() }
        recyclerView=exoplayerview_activity_video.findViewById(R.id.recyclerview)
        recyclerView.layoutManager=LinearLayoutManager(this,LinearLayoutManager.HORIZONTAL,false)
        recyclerView.addOnScrollListener(object : RecyclerView.OnScrollListener()
        {
            override fun onScrollStateChanged(recyclerView: RecyclerView?, newState: Int) {
                super.onScrollStateChanged(recyclerView, newState)
                exoplayerview_activity_video.showController()
            }

            override fun onScrolled(recyclerView: RecyclerView?, dx: Int, dy: Int) {
                super.onScrolled(recyclerView, dx, dy)
                exoplayerview_activity_video.showController()
            }
        })

        val animator = recyclerView.itemAnimator
        if (animator is SimpleItemAnimator) {
            animator.supportsChangeAnimations = false
        }
        recyclerView.adapter=adapter
        val layoutManager=recyclerView.layoutManager as LinearLayoutManager
        if(layoutManager.findLastVisibleItemPosition()<=adapter.position)
        {
            layoutManager.scrollToPosition(adapter.position)
        }

        createMediaSession()
        createPlayer()
    }

    override fun onStart() {
        super.onStart()
        startPlayer()
        activateMediaSession()
    }

    override fun onStop() {
        super.onStop()
        stopPlayer()
        deactivateMediaSession()
    }

    override fun onDestroy() {
        super.onDestroy()
        releasePlayer()
        releaseMediaSession()
    }

    // MediaSession related functions.
    private fun createMediaSession(): MediaSessionCompat = MediaSessionCompat(this, packageName)

    private fun createMediaSessionConnector(): MediaSessionConnector =
            MediaSessionConnector(mediaSession).apply {
                // If QueueNavigator isn't set, then mediaSessionConnector will not handle following
                // MediaSession actions (and they won't show up in the minimized PIP activity):
                // [ACTION_SKIP_PREVIOUS], [ACTION_SKIP_NEXT], [ACTION_SKIP_TO_QUEUE_ITEM]
                setQueueNavigator(object : TimelineQueueNavigator(mediaSession) {
                    override fun getMediaDescription(windowIndex: Int): MediaDescriptionCompat {
                        return mediaCatalog[windowIndex]
                    }
                })
            }


    // MediaSession related functions.
    private fun activateMediaSession() {
        // Note: do not pass a null to the 3rd param below, it will cause a NullPointerException.
        // To pass Kotlin arguments to Java varargs, use the Kotlin spread operator `*`.
        mediaSessionConnector.setPlayer(playerHolder.audioFocusPlayer, null)
        mediaSession.isActive = true

        playerHolder.audioFocusPlayer.addListener(object : Player.DefaultEventListener()
        {
            var playbackState=Player.STATE_IDLE
            override fun onPlayerStateChanged(playWhenReady: Boolean, playbackState: Int) {
                Log.d("test","playWhenReady=$playWhenReady, playbackState=$playbackState")
                this.playbackState=playbackState
                when (playbackState) {
                    Player.STATE_READY -> when (playWhenReady) {
                        true -> {
                            setAdapterPosition(playerHolder.audioFocusPlayer.currentWindowIndex)
                        }
                    }
                }
            }

            override fun onPositionDiscontinuity(reason: Int) {
                if (reason == Player.DISCONTINUITY_REASON_PERIOD_TRANSITION) {
                    setAdapterPosition(playerHolder.audioFocusPlayer.currentWindowIndex)
                }
            }

            fun setAdapterPosition(position: Int) {
                Log.d("test", "setAdapterPosition(${position})")
                val adapter=recyclerView.adapter as VideoClipListAdapter
                val prevPosition=adapter.position
                adapter.position=position
                adapter.notifyItemChanged(prevPosition)
                adapter.notifyItemChanged(adapter.position)

                val layoutManager=recyclerView.layoutManager as LinearLayoutManager
                if(layoutManager.findLastVisibleItemPosition()<=adapter.position)
                {
                    layoutManager.scrollToPosition(adapter.position)
                }
                else if(layoutManager.findFirstVisibleItemPosition()>=adapter.position)
                {
                    layoutManager.scrollToPosition(adapter.position)
                }
            }
        })
    }

    private fun deactivateMediaSession() {
        mediaSessionConnector.setPlayer(null, null)
        mediaSession.isActive = false
    }

    private fun releaseMediaSession() {
        mediaSession.release()
    }

    // ExoPlayer related functions.
    private fun createPlayer() {
        playerHolder = PlayerHolder(this, playerState, exoplayerview_activity_video,mediaCatalog)
    }

    private fun startPlayer() {
        playerHolder.start()
    }

    private fun stopPlayer() {
        playerHolder.stop()
    }

    private fun releasePlayer() {
        playerHolder.release()
    }

    fun play(position: Int) {
        playerState.window=position
        playerHolder.start()
        exoplayerview_activity_video.showController()
    }
}
class VideoClipListAdapter(itemList: ArrayList<VideoClipData>, var position: Int)
    :ArrayListRecyclerViewAdapter<VidoeClipViewBinder, VideoClipData>(itemList,R.layout.item_video_clip_for_player, VidoeClipViewBinder::class)
{

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): VidoeClipViewBinder {
        return super.onCreateViewHolder(parent, viewType).apply { adapter= this@VideoClipListAdapter}
    }
}

class VidoeClipViewBinder(itemView: View, itemList: ArrayList<VideoClipData>) : ViewBinder<VideoClipData>(itemView, itemList) {
    lateinit var adapter: com.gondev.kidtube.exoplayer.VideoClipListAdapter

    override fun bind(item: VideoClipData, position: Int) {

        Glide.with(itemView.context)
                .asBitmap()
                .load(item.videoUri)
                .into(itemView.imgThumb)

        if(adapter.position==position)
        {
            //itemView.layoutCard.setPadding(4,4,4,4)
            itemView.layoutCard.setCardBackgroundColor(itemView.context.resources.getColor(R.color.colorAccent))
        }
        else
        {
            //itemView.layoutCard.setPadding(0,0,0,0)
            itemView.layoutCard.setCardBackgroundColor(itemView.context.resources.getColor(android.R.color.transparent))
        }

        itemView.setOnClickListener {v->
            (v.context as VideoActivity).play(position)
        }
    }

}
