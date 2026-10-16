package app.iptvtest

import android.app.AlarmManager
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.os.Build
import androidx.core.app.NotificationCompat

/** Um jogo que a pessoa pediu para lembrar ("Lembrar" no pop-up). */
class Reminder(
    val gameKey: String,
    /** "Flamengo x Palmeiras" */
    val title: String,
    val league: String,
    val startMs: Long,
    val homeId: String,
    val awayId: String,
    val homeName: String,
    val awayName: String,
    /** Já mostramos o aviso "começa em 10 min" na tela? */
    val seen: Boolean = false,
) {
    fun withSeen(v: Boolean) = Reminder(gameKey, title, league, startMs, homeId, awayId, homeName, awayName, v)
}

/** Avisa 10 minutos antes do jogo, mesmo com o app fechado (onde a TV mostra notificações). */
object ReminderAlarms {

    const val LEAD_MS = 10L * 60_000L
    private const val CHANNEL = "lembretes"

    private fun pending(ctx: Context, r: Reminder): PendingIntent {
        val i = Intent(ctx, ReminderReceiver::class.java)
        i.putExtra("title", r.title)
        i.putExtra("league", r.league)
        i.putExtra("start", r.startMs)
        val flags = PendingIntent.FLAG_UPDATE_CURRENT or (if (Build.VERSION.SDK_INT >= 23) PendingIntent.FLAG_IMMUTABLE else 0)
        return PendingIntent.getBroadcast(ctx, r.gameKey.hashCode(), i, flags)
    }

    fun schedule(ctx: Context, r: Reminder) {
        try {
            val am = ctx.getSystemService(Context.ALARM_SERVICE) as AlarmManager
            val at = r.startMs - LEAD_MS
            if (at <= System.currentTimeMillis()) return
            if (Build.VERSION.SDK_INT >= 23) {
                am.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, at, pending(ctx, r))
            } else {
                am.set(AlarmManager.RTC_WAKEUP, at, pending(ctx, r))
            }
        } catch (e: Exception) {
            // sem alarme o aviso dentro do app continua funcionando
        }
    }

    fun cancel(ctx: Context, r: Reminder) {
        try {
            val am = ctx.getSystemService(Context.ALARM_SERVICE) as AlarmManager
            am.cancel(pending(ctx, r))
        } catch (e: Exception) {
            // nada a cancelar
        }
    }

    fun notify(ctx: Context, title: String, league: String) {
        try {
            val nm = ctx.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            if (Build.VERSION.SDK_INT >= 26) {
                nm.createNotificationChannel(NotificationChannel(CHANNEL, "Lembretes de jogos", NotificationManager.IMPORTANCE_HIGH))
            }
            val open = Intent(ctx, MainActivity::class.java)
            open.flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP
            val flags = PendingIntent.FLAG_UPDATE_CURRENT or (if (Build.VERSION.SDK_INT >= 23) PendingIntent.FLAG_IMMUTABLE else 0)
            val pi = PendingIntent.getActivity(ctx, 0, open, flags)
            val n = NotificationCompat.Builder(ctx, CHANNEL)
                .setSmallIcon(android.R.drawable.ic_dialog_info)
                .setContentTitle("Começa em 10 min: $title")
                .setContentText(league)
                .setAutoCancel(true)
                .setContentIntent(pi)
                .setPriority(NotificationCompat.PRIORITY_HIGH)
                .build()
            nm.notify(title.hashCode(), n)
        } catch (e: Exception) {
            // TV sem notificações ou sem permissão: o aviso dentro do app cobre
        }
    }
}

class ReminderReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val title = intent.getStringExtra("title") ?: return
        val league = intent.getStringExtra("league") ?: ""
        ReminderAlarms.notify(context, title, league)
    }
}
