package me.shetj.recorder

import android.media.AudioFormat
import android.media.AudioRecord
import android.media.MediaRecorder
import android.os.Handler
import android.os.Looper
import android.os.Message
import android.os.Process
import android.text.TextUtils
import android.util.Log
import androidx.annotation.IntRange
import me.shetj.player.AudioPlayer
import me.shetj.player.PermissionListener
import me.shetj.player.PlayerListener
import me.shetj.player.RecordListener
import me.shetj.recorder.util.FileUtils
import me.shetj.recorder.util.LameUtils
import java.io.File
import java.io.IOException
import java.util.*


class MP3Recorder : BaseRecorder {


    private val TAG = javaClass.simpleName

    //=======================AudioRecord Default Settings=======================
    //    private static final int DEFAULT_AUDIO_SOURCE = MediaRecorder.AudioSource.VOICE_COMMUNICATION;//**对麦克风中类似ip通话的交流声音进行识别，默认会开启回声消除和自动增益*/
    private var defaultAudioSource = MediaRecorder.AudioSource.MIC

    //======================Lame Default Settings=====================
    private var defaultLameMp3Quality = 5


    private var mAudioRecord: AudioRecord? = null
    private var mEncodeThread: DataEncodeThread? = null
    private var backgroundPlayer: AudioPlayer? = null
    /**
     * 输出的文件
     */
    private var mRecordFile: File? = null
    private var dataList: ArrayList<Short>? = null

    private var mRecordListener: RecordListener? = null
    private var mPermissionListener: PermissionListener? = null

    private var mPCMBuffer: ShortArray? = null
    var isRecording = false
        private set
    private var mSendError: Boolean = false
    var isPause: Boolean = false
    //缓冲数量
    private var mBufferSize: Int = 0
    //最大数量
    private var mMaxSize: Int = 0
    //波形速度
    /**
     * pcm数据的速度，默认300
     * 数据越大，速度越慢
     */
    var waveSpeed = 300
        set(waveSpeed) {
            if (this.waveSpeed <= 0) {
                return
            }
            field = waveSpeed
        }
    //录制时间
    var duration = 0L
        private set
    //最大时间
    private var mMaxTime: Long = 3600000
    //提醒时间
    private var mRemindTime = (3600000 - 10000).toLong()
    //当前状态
    /**
     * 当前录制状态
     * @return
     */
    var state = RecordState.STOPPED
        private set
    //声音增强
    private var wax = 1f

    private var isDebug = false
    private var isContinue = false //是否继续录制
    //背景音乐相关
    private var backgroundMusicIsPlay: Boolean = false //记录是否暂停
    private var backgroundMusicUrl: String? = null
    private var backgroundMusicPlayerListener: PlayerListener? = null

    private val handler = object : Handler(Looper.getMainLooper()) {
        override fun handleMessage(msg: Message) {
            super.handleMessage(msg)
            when (msg.what) {
                HANDLER_RECORDING -> {
                    log("msg.what = HANDLER_RECORDING  \n mDuration = ", duration)
                    if (mRecordListener != null) {
                        //录制回调
                        mRecordListener!!.onRecording(duration, realVolume)
                        //提示快到录音时间了
                        if (mMaxTime > 150000 && mMaxTime > duration && duration > mRemindTime) {
                            mRecordListener!!.onRemind(duration)
                        }
                    }
                }
                HANDLER_START -> {
                    log("msg.what = HANDLER_START  \n mDuration = ", duration)
                    if (mRecordListener != null) {
                        mRecordListener!!.onStart()
                    }
                }
                HANDLER_RESUME -> {
                    log("msg.what = HANDLER_RESUME  \n mDuration = ", duration)
                    if (mRecordListener != null) {
                        mRecordListener!!.onResume()
                    }
                }
                HANDLER_COMPLETE -> {
                    log("msg.what = HANDLER_COMPLETE  \n mDuration = ", duration)
                    if (mRecordListener != null) {
                        mRecordListener!!.onSuccess(mRecordFile!!.absolutePath, duration)
                    }
                }
                HANDLER_AUTO_COMPLETE -> {
                    log("msg.what = HANDLER_AUTO_COMPLETE  \n mDuration = ", duration)
                    if (mRecordListener != null) {
                        mRecordListener!!.autoComplete(mRecordFile!!.absolutePath, duration)
                    }
                }
                HANDLER_ERROR -> {
                    log("msg.what = HANDLER_ERROR  \n mDuration = ", duration)
                    if (mRecordListener != null) {
                        mRecordListener!!.onError(Exception("record error!"))
                    }
                }
                HANDLER_PAUSE -> {
                    log("msg.what = HANDLER_PAUSE  \n mDuration = ", duration)
                    if (mRecordListener != null) {
                        mRecordListener!!.onPause()
                    }
                }
                HANDLER_PERMISSION -> {
                    log("msg.what = HANDLER_PERMISSION  \n mDuration = ", duration)
                    if (mPermissionListener != null) {
                        mPermissionListener!!.needPermission()
                    }
                }
                HANDLER_RESET -> {
                    log("msg.what = HANDLER_RESET  \n mDuration = ", duration)
                    if (mRecordListener != null) {
                        mRecordListener!!.onReset()
                    }
                }
                HANDLER_MAX_TIME -> if (mRecordListener != null) {
                    mRecordListener!!.setMaxProgress(mMaxTime)
                }
                else -> {
                }
            }
        }
    }
    /***************************public method  */
    /**
     * 返回背景音乐的播放器
     * @return
     */
    val bgPlayer: AudioPlayer?
        get() {
            initBgMusicPlayer()
            return backgroundPlayer
        }

    /**
     * 获取真实的音量。 [算法来自三星]
     *
     * @return 真实音量
     */
    override val realVolume: Int
        get() = mVolume

    /**
     * 获取相对音量。 超过最大值时取最大值。
     *
     * @return 音量
     */
    val volume: Int
        get() = if (mVolume >= MAX_VOLUME) {
            MAX_VOLUME
        } else mVolume

    /**
     * 根据资料假定的最大值。 实测时有时超过此值。
     *
     * @return 最大音量值。
     */
    val maxVolume: Int
        get() = MAX_VOLUME


    constructor() {}

    constructor(isDebug: Boolean) {
        this.isDebug = isDebug
    }

    /**
     *
     * @param audioSource MediaRecorder.AudioSource.MIC
     * @param isDebug true or false
     */
    constructor(audioSource: Int, isDebug: Boolean) {
        this.defaultAudioSource = audioSource
        this.isDebug = isDebug
    }

    /**
     * 设置录音输出文件
     * @param outputFile
     */
    @JvmOverloads
    fun setOutputFile(outputFile: String, isContinue: Boolean = false): MP3Recorder {
        if (TextUtils.isEmpty(outputFile)) {
            val message = Message.obtain()
            message.what = HANDLER_ERROR
            message.obj = Exception("outputFile is not null")
            handler.sendMessage(message)
        } else {
            setOutputFile(File(outputFile), isContinue)
        }
        return this
    }

    fun setMp3Quality(@IntRange(from = 0, to = 9) mp3Quality: Int): MP3Recorder {
        this.defaultLameMp3Quality = mp3Quality
        return this
    }

    /**
     * 设置录音输出文件
     * @param outputFile
     */
    @JvmOverloads
    fun setOutputFile(outputFile: File, isContinue: Boolean = false): MP3Recorder {
        mRecordFile = outputFile
        this.isContinue = isContinue
        return this
    }

    /**
     * 设置增强系数
     * @param wax
     */
    fun setWax(wax: Float): MP3Recorder {
        this.wax = wax
        return this
    }

    /**
     * 设置回调
     * @param recordListener
     */
    fun setRecordListener(recordListener: RecordListener?): MP3Recorder {
        this.mRecordListener = recordListener
        return this
    }

    fun setPermissionListener(permissionListener: PermissionListener?): MP3Recorder {
        this.mPermissionListener = permissionListener
        return this
    }

    /**
     * 设置最大录制时间
     * @param mMaxTime 最大录制时间  默认一个小时？
     * 提示时间时10秒前
     */
    fun setMaxTime(mMaxTime: Int): MP3Recorder {
        this.mMaxTime = mMaxTime.toLong()
        this.mRemindTime = (mMaxTime - 10000).toLong()
        handler.sendEmptyMessage(HANDLER_MAX_TIME)
        return this
    }

    /**
     * Start recording. Create an encoding thread. Start record from this
     */
    fun start() {
        if (isRecording) {
            return
        }
        // 提早，防止init或startRecording被多次调用
        isRecording = true
        //初始化
        duration = 0
        try {
            initAudioRecorder()
            mAudioRecord!!.startRecording()
        } catch (ex: Exception) {
            if (mRecordListener != null) {
                mRecordListener!!.onError(ex)
            }
            onError()
            handler.sendEmptyMessage(HANDLER_PERMISSION)
            return
        }

        object : Thread() {
            var isError = false
            //PCM文件大小 = 采样率采样时间采样位深 / 8*通道数（Bytes）
            var bytesPerSecond =
                mAudioRecord!!.sampleRate * mapFormat(mAudioRecord!!.audioFormat) / 8 * mAudioRecord!!.channelCount

            override fun run() {
                //设置线程权限
                Process.setThreadPriority(Process.THREAD_PRIORITY_URGENT_AUDIO)
                onStart()
                while (isRecording) {
                    val readSize = mAudioRecord!!.read(mPCMBuffer!!, 0, mBufferSize)
                    if (readSize == AudioRecord.ERROR_INVALID_OPERATION || readSize == AudioRecord.ERROR_BAD_VALUE) {
                        if (!mSendError) {
                            mSendError = true
                            handler.sendEmptyMessage(HANDLER_PERMISSION)
                            onError()
                            isError = true
                        }
                    } else {
                        if (readSize > 0) {
                            if (isPause) {
                                continue
                            }
                            val readTime = 1000.0 * readSize.toDouble() * 2.0 / bytesPerSecond
                            mEncodeThread!!.addTask(mPCMBuffer!!, readSize)
                            calculateRealVolume(mPCMBuffer!!, readSize)
                            //short 是2个字节 byte 是1个字节8位
                            onRecording(readTime)
                            sendData(mPCMBuffer, readSize)
                        } else {
                            if (!mSendError) {
                                mSendError = true
                                handler.sendEmptyMessage(HANDLER_PERMISSION)
                                onError()
                                isError = true
                            }
                        }
                    }
                }
                try {
                    mAudioRecord!!.stop()
                    mAudioRecord!!.release()
                    mAudioRecord = null
                } catch (ex: Exception) {
                    ex.printStackTrace()
                }

                if (isError) {
                    mEncodeThread!!.sendErrorMessage()
                } else {
                    mEncodeThread!!.sendStopMessage()
                }
            }

        }.start()
    }

    fun setBackgroundMusic(url: String) {
        this.backgroundMusicUrl = url
    }

    fun setBackgroundMusicPlayerListener(listener: PlayerListener) {
        this.backgroundMusicPlayerListener = listener
    }

    fun stop() {
        if (state !== RecordState.STOPPED) {
            isPause = false
            isRecording = false
            handler.sendEmptyMessage(HANDLER_COMPLETE)
            state = RecordState.STOPPED
            backgroundMusicIsPlay = false
            bgPlayer!!.stopPlay()
        }
    }

    /**
     * 重新开始
     */
    fun onResume() {
        if (state === RecordState.PAUSED) {
            isPause = false
            state = RecordState.RECORDING
            handler.sendEmptyMessage(HANDLER_RESUME)
            if (backgroundMusicIsPlay) {
                bgPlayer!!.resume()
            }
        }
    }

    /**
     * 暂停
     */
    fun onPause() {
        if (state === RecordState.RECORDING) {
            isPause = true
            state = RecordState.PAUSED
            handler.sendEmptyMessage(HANDLER_PAUSE)
            backgroundMusicIsPlay = bgPlayer!!.isPlaying
            bgPlayer!!.pause()
        }
    }

    /**
     * 重置
     */
    fun onReset() {
        isRecording = false
        isPause = false
        FileUtils.deleteFile(mRecordFile!!.absolutePath)
        state = RecordState.STOPPED
        duration = 0L
        mRecordFile = null
        backgroundMusicIsPlay = bgPlayer!!.isPlaying
        handler.sendEmptyMessage(HANDLER_RESET)
        bgPlayer!!.stopPlay()
    }


    fun onDestroy() {
        bgPlayer!!.stopPlay()
    }


    /**
     * Initialize audio recorder
     */
    @Throws(IOException::class)
    private fun initAudioRecorder() {
        mBufferSize = AudioRecord.getMinBufferSize(
            DEFAULT_SAMPLING_RATE,
            DEFAULT_CHANNEL_CONFIG, DEFAULT_AUDIO_FORMAT.audioFormat
        )
        val bytesPerFrame = DEFAULT_AUDIO_FORMAT.bytesPerFrame
        /* Get number of samples. Calculate the buffer size
         * (round up to the factor of given frame size)
         * 使能被整除，方便下面的周期性通知
         * */
        var frameSize = mBufferSize / bytesPerFrame
        if (frameSize % FRAME_COUNT != 0) {
            frameSize += FRAME_COUNT - frameSize % FRAME_COUNT
            mBufferSize = frameSize * bytesPerFrame
        }
//        Log.i(TAG, "mBufferSize = $mBufferSize")
        /* Setup audio recorder */
        mAudioRecord = AudioRecord(
            defaultAudioSource,
            DEFAULT_SAMPLING_RATE, DEFAULT_CHANNEL_CONFIG, DEFAULT_AUDIO_FORMAT.audioFormat,
            mBufferSize
        )
        mPCMBuffer = ShortArray(mBufferSize)
        /*
         * Initialize lame buffer
         * mp3 sampling rate is the same as the recorded pcm sampling rate
         * The bit rate is 32kbps
         */
        LameUtils.init(
            DEFAULT_SAMPLING_RATE,
            DEFAULT_LAME_IN_CHANNEL,
            DEFAULT_SAMPLING_RATE,
            DEFAULT_LAME_MP3_BIT_RATE,
            defaultLameMp3Quality
        )
        mEncodeThread = DataEncodeThread(mRecordFile!!, mBufferSize, isContinue)
        mEncodeThread!!.start()
        mAudioRecord!!.setRecordPositionUpdateListener(mEncodeThread, mEncodeThread!!.handler)
        mAudioRecord!!.positionNotificationPeriod = FRAME_COUNT
    }

    private fun sendData(shorts: ShortArray?, readSize: Int) {
        if (dataList != null) {
            val length = readSize / waveSpeed
            var resultMax: Short = 0
            var resultMin: Short = 0
            var i = 0
            var k = 0
            while (i < length) {
                var j = k
                var max: Short = 0
                var min: Short = 1000
                while (j < k + waveSpeed) {
                    if (shorts!![j] > max) {
                        max = shorts[j]
                        resultMax = max
                    } else if (shorts[j] < min) {
                        min = shorts[j]
                        resultMin = min
                    }
                    j++
                }
                if (dataList!!.size > mMaxSize) {
                    dataList!!.removeAt(0)
                }
                dataList!!.add(resultMax)
                i++
                k += waveSpeed.toShort()
            }
        }
    }

    /**
     * 设置数据的获取显示，设置最大的获取数，一般都是控件大小/线的间隔offset
     *
     * @param dataList 数据
     * @param maxSize  最大个数
     */
    fun setDataList(dataList: ArrayList<Short>, maxSize: Int) {
        this.dataList = dataList
        this.mMaxSize = maxSize
    }

    /***************************private method  */

    private fun initBgMusicPlayer() {
        if (backgroundPlayer == null) {
            backgroundPlayer = AudioPlayer()
            backgroundPlayer!!.setLoop(true)
        }
    }


    private fun onStart() {
        if (state !== RecordState.RECORDING) {
            handler.sendEmptyMessage(HANDLER_START)
            state = RecordState.RECORDING
            duration = 0L
            if (backgroundMusicIsPlay) {
                bgPlayer!!.playNoStart(backgroundMusicUrl, backgroundMusicPlayerListener)
            }
        }
    }


    private fun onError() {
        isPause = false
        isRecording = false
        handler.sendEmptyMessage(HANDLER_ERROR)
        state = RecordState.STOPPED
        backgroundMusicIsPlay = false
        bgPlayer!!.stopPlay()
    }


    /**
     * 计算时间
     * @param readTime
     */
    private fun onRecording(readTime: Double) {
        duration += readTime.toLong()
        handler.sendEmptyMessageDelayed(HANDLER_RECORDING, waveSpeed.toLong())
        if (mMaxTime in 1..duration) {
            autoStop()
        }
    }

    private fun autoStop() {
        if (state !== RecordState.STOPPED) {
            isPause = false
            isRecording = false
            handler.sendEmptyMessageDelayed(HANDLER_AUTO_COMPLETE, waveSpeed.toLong())
            state = RecordState.STOPPED
            backgroundMusicIsPlay = false
            bgPlayer!!.stopPlay()
        }
    }

    private fun log(s: String, mDuration: Long) {
        if (isDebug) {
            Log.d(TAG, s + mDuration)
        }
    }

    private fun mapFormat(format: Int): Int {
        return when (format) {
            AudioFormat.ENCODING_PCM_8BIT -> 8
            AudioFormat.ENCODING_PCM_16BIT -> 16
            else -> 0
        }
    }

    companion object {

        private val HANDLER_RECORDING = 101 //正在录音
        private val HANDLER_START = 102//开始了
        private val HANDLER_COMPLETE = 103//完成
        private val HANDLER_AUTO_COMPLETE = 104//最大时间完成
        private val HANDLER_ERROR = 105//错误
        private val HANDLER_PAUSE = 106//暂停
        private val HANDLER_PERMISSION = 107//需要权限
        private val HANDLER_RESUME = 108//暂停后开始
        private val HANDLER_RESET = 109//暂停
        private val HANDLER_MAX_TIME = 110//设置了最大时间
        /**
         * 以下三项为默认配置参数。Google Android文档明确表明只有以下3个参数是可以在所有设备上保证支持的。
         */
        private val DEFAULT_SAMPLING_RATE = 44100
        private val DEFAULT_CHANNEL_CONFIG = AudioFormat.CHANNEL_IN_MONO
        /**
         * 下面是对此的封装
         * private static final int DEFAULT_AUDIO_FORMAT = AudioFormat.ENCODING_PCM_16BIT;
         */

        private val DEFAULT_AUDIO_FORMAT = PCMFormat.PCM_16BIT
        /**
         * 与DEFAULT_CHANNEL_CONFIG相关，因为是mono单声，所以是1
         */
        private val DEFAULT_LAME_IN_CHANNEL = 1
        /**
         * Encoded bit rate. MP3 file will be encoded with bit rate 32kbps
         */
        private val DEFAULT_LAME_MP3_BIT_RATE = 32

        //==================================================================

        /**
         * 自定义 每160帧作为一个周期，通知一下需要进行编码
         */
        private val FRAME_COUNT = 160

        private val MAX_VOLUME = 2000


    }


}