package me.shetj.mixRecorder

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.media.AudioManager
import android.util.Log
import java.util.concurrent.atomic.AtomicBoolean


class PlugConfigs(val context: Context, var connected: Boolean = false) {

    private val isRegister = AtomicBoolean(false)
    private val audioManager = context.getSystemService(Context.AUDIO_SERVICE) as AudioManager
    private val mReceiver: BroadcastReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            val action = intent.action
            if (action == Intent.ACTION_HEADSET_PLUG) {
                if (intent.hasExtra("state")) {
                    if (intent.getIntExtra("state", 0) == 0) {
                        getInstance(context).connected = false
                    } else if (intent.getIntExtra("state", 0) == 1) {
                        getInstance(context).connected = true
                    }
                }
            }
        }
    }
    private val intentFilter = IntentFilter(Intent.ACTION_HEADSET_PLUG)

    fun registerReceiver() {
        if (isRegister.compareAndSet(false,true)) {
            connected = audioManager.isWiredHeadsetOn
            context.registerReceiver(mReceiver, intentFilter)
        }
    }

    fun unregisterReceiver() {
        if(isRegister.compareAndSet(true,false)) {
            context.unregisterReceiver(mReceiver)
        }
    }

    companion object {
        @Volatile
        private var sInstance: PlugConfigs? = null

        fun getInstance(context: Context): PlugConfigs {
            return sInstance ?: synchronized(PlugConfigs::class.java) {
                return PlugConfigs(context).also {
                    it.connected = it.audioManager.isWiredHeadsetOn
                    sInstance = it
                }
            }
        }

    }

}