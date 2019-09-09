package me.shetj.mp3recorder.record.utils

import android.media.MediaRecorder
import me.shetj.base.tools.app.ArmsUtils
import me.shetj.base.tools.file.FileUtils
import me.shetj.base.tools.file.SDCardUtils
import me.shetj.base.tools.json.EmptyUtils
import me.shetj.mp3recorder.BuildConfig
import me.shetj.player.AudioPlayer
import me.shetj.player.PermissionListener
import me.shetj.player.PlayerListener
import me.shetj.player.RecordListener
import me.shetj.recorder.MP3Recorder
import me.shetj.recorder.RecordState
import java.io.File

class RecordUtils(private val callBack: RecordCallBack?
) : RecordListener, PermissionListener {
    val isRecording: Boolean
        get() {
            return if (mRecorder !=null) {
                mRecorder?.isRecording!!
            }else{
                false
            }
        }
    fun hasRecord(): Boolean {
        return  if (mRecorder !=null) {
            mRecorder?.duration!! - startTime > 0 && mRecorder!!.state != RecordState.STOPPED
        }else{
            false
        }
    }

    init {
        initRecorder()
    }

    private var startTime: Int = 0
    private var mRecorder: MP3Recorder? = null
    private var saveFile = ""


    @JvmOverloads
    fun startOrPause(file :String = "",isContinue :Boolean = false) {
        if (mRecorder == null) {
            initRecorder()
        }
        when {
            mRecorder?.state == RecordState.STOPPED -> {
                if (EmptyUtils.isEmpty(file)) {
                    val mRecordFile = SDCardUtils.getPath("record") + "/" + System.currentTimeMillis() + ".mp3"
                    this.saveFile = mRecordFile
                }else{
                    this.saveFile = file
                }
                mRecorder?.setOutputFile(saveFile,isContinue)
                mRecorder?.start()
            }
            mRecorder?.state == RecordState.PAUSED->{
                mRecorder?.onResume()
            }
            mRecorder?.state == RecordState.RECORDING ->{
                mRecorder?.onPause()
            }
        }
    }

    /**
     * VOICE_COMMUNICATION 消除回声和噪声问题
     * MIC 麦克风- 因为有噪音问题
     */
    private fun initRecorder() {
        mRecorder = MP3Recorder(MediaRecorder.AudioSource.VOICE_COMMUNICATION, BuildConfig.DEBUG)
                .setMaxTime(1800 * 1000)
                .setPermissionListener(this)
                .setRecordListener(this)
                .setWax(2f) //声音增强处理 默认
    }

    fun setBackgroundMusic(url:String){
        mRecorder?.setBackgroundMusic(url)
    }

    fun setBackgroundPlayerListener(listener : PlayerListener) {
        mRecorder?.setBackgroundMusicPlayerListener(listener)
    }

    fun getBgPlayer(): AudioPlayer {
        return mRecorder!!.bgPlayer!!
    }

    fun  pause(){
        mRecorder?.onPause()
    }
    fun clear() {
        mRecorder?.onDestroy()
    }

    fun reset() {
        mRecorder?.onReset()
    }

    /**
     * 设置开始录制时间
     * @param startTime 已经录制的时间
     */
    fun setTime(startTime: Int) {
        this.startTime = startTime
        callBack?.onRecording(startTime  ,0)
    }

    /**
     * 设置最大录制时间
     */
    fun setMaxTime(maxTime: Int) {
        mRecorder?.setMaxTime(maxTime*1000)
    }
    /**
     * 录音异常
     */
    private fun resolveError() {
        FileUtils.deleteFile(File(saveFile))
        if (mRecorder != null && mRecorder!!.isRecording) {
            mRecorder!!.stop()
        }
    }

    /**
     * 停止录音
     */
    fun stopFullRecord() {
        mRecorder?.stop()
    }

    override fun needPermission() {
        callBack?.needPermission()
    }

    override fun onStart() {
        callBack?.start()
    }

    override fun onResume() {
        callBack?.start()
    }

    override fun onReset() {
        callBack?.start()
    }

    override fun onRecording(time: Long, volume: Int) {
        callBack?.onRecording(startTime + (time/1000).toInt(),volume)
    }

    override fun onPause() {
        callBack?.pause()
    }

    override fun onRemind(mDuration: Long) {
        ArmsUtils.makeText("已录制" +  mDuration/60000 + "分钟，本条录音还可以继续录制10秒")
    }

    override fun onSuccess(file: String, time: Long) {
        callBack?.onSuccess(file, (time/1000).toInt())
    }

    override fun setMaxProgress(time: Long) {
        callBack?.onMaxProgress((time/1000).toInt())
    }

    override fun onError(e: Exception) {
        resolveError()
        callBack?.onError(e)
    }

    override fun autoComplete(file: String, time: Long) {
        callBack?.autoComplete(file,  (time/1000).toInt())
    }


}
