package com.rama.tui

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.Build
import android.os.IBinder
import android.graphics.drawable.Icon
import androidx.core.content.ContextCompat
import com.rama.tui.activities.MainActivity
import com.rama.tui.managers.MusicManager
import com.rama.bohio.R as BohioR

class MediaPlaybackService : Service() {

    companion object {
        const val CHANNEL_ID = "tui_playback"
        const val NOTIFICATION_ID = 1

        const val ACTION_PLAY_PAUSE = "com.rama.tui.PLAY_PAUSE"
        const val ACTION_NEXT = "com.rama.tui.NEXT"
        const val ACTION_PREV = "com.rama.tui.PREV"
        const val ACTION_STOP = "com.rama.tui.STOP"

        fun start(context: Context) {
            val intent = Intent(context, MediaPlaybackService::class.java)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                context.startForegroundService(intent)
            } else {
                context.startService(intent)
            }
        }

        fun stop(context: Context) {
            context.stopService(Intent(context, MediaPlaybackService::class.java))
        }
    }

    override fun onBind(intent: Intent?): IBinder? = null

    private val controlReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            when (intent.action) {
                ACTION_PLAY_PAUSE -> MusicManager.togglePlayPause()
                ACTION_NEXT -> MusicManager.next()
                ACTION_PREV -> MusicManager.prev()
                ACTION_STOP -> {
                    MusicManager.release()
                    stopSelf()
                    return
                }
            }
            updateNotification()
        }
    }

    override fun onCreate() {
        super.onCreate()
        createNotificationChannel()
        MusicManager.onNotificationChanged = { updateNotification() }

        val filter = IntentFilter().apply {
            addAction(ACTION_PLAY_PAUSE)
            addAction(ACTION_NEXT)
            addAction(ACTION_PREV)
            addAction(ACTION_STOP)
        }

        ContextCompat.registerReceiver(
            this,
            controlReceiver,
            filter,
            ContextCompat.RECEIVER_NOT_EXPORTED
        )

        startForeground(NOTIFICATION_ID, buildNotification())
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        return START_STICKY
    }

    override fun onDestroy() {
        unregisterReceiver(controlReceiver)
        MusicManager.onNotificationChanged = null
        super.onDestroy()
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                "Music Playback",
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = "Shows currently playing track"
                setShowBadge(false)
            }
            getSystemService(NotificationManager::class.java).createNotificationChannel(channel)
        }
    }

    private fun buildNotification(): Notification {
        val track = MusicManager.currentTrack
        val isPlaying = MusicManager.isPlaying

        val pendingFlags = PendingIntent.FLAG_UPDATE_CURRENT or
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) PendingIntent.FLAG_IMMUTABLE else 0

        // Tap notification open app
        val openAppIntent = PendingIntent.getActivity(
            this, 0,
            Intent(this, MainActivity::class.java).apply {
                flags = Intent.FLAG_ACTIVITY_SINGLE_TOP
            },
            pendingFlags
        )

        fun actionIntent(action: String, requestCode: Int) = PendingIntent.getBroadcast(
            this, requestCode,
            Intent(action).apply { setPackage(packageName) },
            pendingFlags
        )

        fun addNotificationAction(
            builder: Notification.Builder,
            iconRes: Int,
            title: CharSequence,
            intent: PendingIntent
        ) {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                builder.addAction(
                    Notification.Action.Builder(
                        Icon.createWithResource(this, iconRes),
                        title,
                        intent
                    ).build()
                )
            } else {
                @Suppress("DEPRECATION")
                builder.addAction(iconRes, title, intent)
            }
        }

        val builder = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            Notification.Builder(this, CHANNEL_ID)
        } else {
            @Suppress("DEPRECATION")
            Notification.Builder(this)
        }

        builder
            .setSmallIcon(R.drawable.ic_launcher_foreground)
        builder.setColor(ContextCompat.getColor(this, BohioR.color.media_background))
            .setContentTitle(track?.title ?: "Not playing")
            .setContentText(track?.displayArtists?.ifEmpty { null } ?: "")
            .setContentIntent(openAppIntent)
            .setOngoing(true)
            .setShowWhen(false)

        addNotificationAction(builder, BohioR.drawable.px_prev, "Previous", actionIntent(ACTION_PREV, 1))
        addNotificationAction(
            builder,
            if (isPlaying) BohioR.drawable.px_pause else BohioR.drawable.px_play,
            if (isPlaying) "Pause" else "Play",
            actionIntent(ACTION_PLAY_PAUSE, 2)
        )
        addNotificationAction(builder, BohioR.drawable.px_next, "Next", actionIntent(ACTION_NEXT, 3))

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
            builder.setStyle(
                Notification.MediaStyle()
                    .setShowActionsInCompactView(0, 1, 2)
                    .setMediaSession(MusicManager.getSessionToken())
            )
            builder.setVisibility(Notification.VISIBILITY_PUBLIC)
        }

        return builder.build()
    }

    fun updateNotification() {
        val nm = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        nm.notify(NOTIFICATION_ID, buildNotification())
    }
}
