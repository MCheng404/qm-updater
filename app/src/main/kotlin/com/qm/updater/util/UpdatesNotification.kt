package com.qm.updater.util

import android.Manifest
import android.annotation.SuppressLint
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import androidx.activity.compose.ManagedActivityResultLauncher
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import com.qm.updater.R
import com.qm.updater.ui.activity.MainActivity

class UpdatesNotification(private val context: Context) {

    companion object {
        const val UpdateAction = "updateAction"

        /** 更新发现通知的固定 id。 */
        private const val UPDATE_ID = 42

        /** 进度通知 id 基数：每项安装用 `PROGRESS_BASE + update.id` 区分，避免并发互相覆盖。 */
        private const val PROGRESS_BASE = 1000
    }

    private val notificationManager get() = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
    private val channelId = context.getString(R.string.notification_channel_id)
    private val channelName = context.getString(R.string.notification_channel_name)
    private val updateTitle = context.getString(R.string.notification_update_title)

    @SuppressLint("MissingPermission")
    fun showUpdateNotification(num: Int) {
        // Intent for the notification click
        val intent = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_REORDER_TO_FRONT
            action = UpdateAction
        }

        val builder = NotificationCompat.Builder(context, channelId)
            .setSmallIcon(R.drawable.ic_install)
            .setContentTitle(updateTitle)
            .setContentText(context.resources.getQuantityString(R.plurals.notification_update_description, num, num))
            .setPriority(NotificationCompat.PRIORITY_DEFAULT)
            .setContentIntent(PendingIntent.getActivity(context, 0, intent, PendingIntent.FLAG_UPDATE_CURRENT + PendingIntent.FLAG_IMMUTABLE))
            .setAutoCancel(true)

        createNotificationChannel()
        if (areNotificationsEnabled()) {
            NotificationManagerCompat.from(context).notify(UPDATE_ID, builder.build())
        }
    }

    /**
     * 更新/安装进度通知（下载或安装阶段）。
     *
     * - 同一安装项复用同一通知 id（PROGRESS_BASE + id），多次调用原地更新进度条；
     * - `setOnlyAlertOnce` 保证进度刷新不反复响铃震动；
     * - total <= 0（Content-Length 未知）时显示不定进度条。
     */
    @SuppressLint("MissingPermission")
    fun showProgress(id: Int, appName: String, written: Long, total: Long) {
        if (!areNotificationsEnabled()) return
        createNotificationChannel()
        val pct = if (total > 0) ((written * 100) / total).coerceIn(0L, 100L).toInt() else -1
        val text = if (pct >= 0) {
            context.getString(R.string.notification_progress_pct, appName, pct)
        } else {
            context.getString(R.string.notification_progress_unknown, appName)
        }
        val builder = NotificationCompat.Builder(context, channelId)
            .setSmallIcon(R.drawable.ic_install)
            .setContentTitle(updateTitle)
            .setContentText(text)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .setOnlyAlertOnce(true)
            .setOngoing(true)
        if (total > 0) {
            builder.setProgress(100, pct, false)
        } else {
            builder.setProgress(0, 0, true)
        }
        NotificationManagerCompat.from(context).notify(PROGRESS_BASE + id, builder.build())
    }

    /**
     * 收尾一条进度通知：成功时移除，失败时改写为带原因的失败通知。
     * 幂等，可安全重复调用。
     */
    @SuppressLint("MissingPermission")
    fun finishProgress(id: Int, success: Boolean, message: String?) {
        val notificationId = PROGRESS_BASE + id
        if (!areNotificationsEnabled()) return
        if (success) {
            NotificationManagerCompat.from(context).cancel(notificationId)
            return
        }
        createNotificationChannel()
        val builder = NotificationCompat.Builder(context, channelId)
            .setSmallIcon(R.drawable.ic_install)
            .setContentTitle(context.getString(R.string.notification_install_failed))
            .setContentText(message ?: context.getString(R.string.notification_install_failed_unknown))
            .setPriority(NotificationCompat.PRIORITY_DEFAULT)
            .setAutoCancel(true)
        NotificationManagerCompat.from(context).notify(notificationId, builder.build())
    }

    fun checkNotificationPermission(launcher: ManagedActivityResultLauncher<String, Boolean>) {
        if (Build.VERSION.SDK_INT >= 33) {
            if (!areNotificationsEnabled()) {
                launcher.launch(Manifest.permission.POST_NOTIFICATIONS)
            }
        }
    }

    private fun areNotificationsEnabled() = NotificationManagerCompat
        .from(context)
        .areNotificationsEnabled()

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(channelId, channelName, NotificationManager.IMPORTANCE_DEFAULT).apply {
                description = context.getString(R.string.notification_channel_description)
            }
            notificationManager.createNotificationChannel(channel)
        }
    }

}
