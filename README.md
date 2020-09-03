# 录音工具-Mp3Recorder

- 边录边转码MP3,支持暂停，实时返回已录制时长和当前声音大小。
- 可添加背景音乐,可以设置背景音乐声音的大小
- 录制过程中**暂停**,已录制的那段音频是**可以播放**的.
- 可设置最大录制时间
- 录音中途可以替换输出文件，比如每60秒替换一个输出文件，然后发送
- 可以使用耳机配置方式：如果没有连接耳机会只用外放的背景音乐，如果连接上了耳机，会使用写入合成背景音乐的方式
- 其他...

#### 背景音乐相关
  - 录制中可以随时中断、播放、替换背景音乐
  - 如果背景音乐的参数我的库中不一样，需要自行设置参数，如果不一样会让背景音乐拉长或者变快


#### Gradle

Step 1. Add it in your root build.gradle at the end of repositories:

```
allprojects {
    repositories {
        ...
        maven { url 'https://dl.bintray.com/shetiejun/maven' }
        }
}
```

Step 2. Add the dependency[ ![Download](https://api.bintray.com/packages/shetiejun/maven/recorder/images/download.svg) ](https://bintray.com/shetiejun/maven/recorder)

```
implementation 'me.shetj.sdk:recorder:+'   (mix 和Sim)
```

if you only need MixRecorder [ ![Download](https://api.bintray.com/packages/shetiejun/maven/recorder-mix/images/download.svg) ](https://bintray.com/shetiejun/maven/recorder-mix)
```groovy
implementation 'me.shetj.sdk:recorder-mix:+'
```

if you only need SimRecorder [ ![Download](https://api.bintray.com/packages/shetiejun/maven/recorder-sim/images/download.svg) ](https://bintray.com/shetiejun/maven/recorder-sim)
```groovy
implementation 'me.shetj.sdk:recorder-sim:+'
```


#### [demo](https://github.com/SheTieJun/Mp3Recorder/tree/master/app)
- [MixRecordUtils](https://github.com/SheTieJun/Mp3Recorder/blob/master/app/src/main/java/me/shetj/mp3recorder/record/utils/MixRecordUtils.kt)
- [RecordUtils](https://github.com/SheTieJun/Mp3Recorder/blob/master/app/src/main/java/me/shetj/mp3recorder/record/utils/RecordUtils.kt)

<img src="https://github.com/SheTieJun/Mp3Recorder/blob/master/doc/img/recorder.gif" width="35%" height="35%" />


### 缺点

1. 录制声道数设置，因为合成，所有你**需要设置和背景音乐相同的参数**
2. 如果设置单声道，播放的背景是双声道，（MIX）会让音乐拉长；反之双声音合成，背景音乐是单声音，节奏会变快


#### PCM与时间的计算

音频文件大小的计算公式为: 数据量Byte = 采样频率Hz×（采样位数/8）× 声道数 × 时间s

反之：时间s = 数据量Byte / (采样频率Hz×（采样位数/8）× 声道数)

### 初始化
```kotlin
         if (mRecorder == null) {
             mRecorder = mp3Recorder(
                      context,
                      mMaxTime = 3600 * 1000,
                      mp3Quality = 1,
                      isDebug = true,
                      channel = BaseRecorder.AudioChannel.STEREO,
                      recordListener = this,
                      permissionListener = this
                  )
        }
```
#### 1.录音控制（开始/暂停）
``` kotlin
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
```

#### 2. 暂停、重新开始录音

```kotlin
 mRecorder?.onPause() //暂停
 mRecorder?.onResume() //重新开始
 mRecorder?.state     //获取当前录音的状态 3个状态，停止，录音中，暂停
```

#### 3. 背景音乐相关

```kotlin
 mRecorder?.setBackgroundMusic(musicUrl)//设置背景音乐
 mRecorder?.setVolume(volume)//设置背景音乐大小0-1	
 mRecorder?.startPlayMusic() //开始播放背景音乐
 mRecorder?.pauseMusic() //暂停背景音乐
 mRecorder?.isPauseMusic()// 背景音乐是否暂停
 mRecorder?.resumeMusic() //重新开始播放
 mRecorder?.setContextToPlugConfig(context) //设置次方法后，会使用耳机配置方式,只有 【MixRecorder】 有效
 mRecorder?.setContextToVolumeConfig(context) //设置方法后，将会使用系统的播放的音量进行控制
```

> 如果使用耳机配置方式：如果没有连接耳机会只用外放的背景音乐，如果连接上了耳机，会使用写入合成背景音乐的方式

> 如果没有使用耳机，会同时使用外放和写入背景音乐 2 种方法，可能会存在叠音，目前有细微优化，但是不保证兼容所有机型

#### 5. 完成录音（停止录音）

```kotlin
 mRecorder?.stop()  //完成录音
```

#### 6.新增录音参数修改，必须在start()之前调用才有效
```
    //初始Lame录音输出质量
    mRecorder?.setMp3Quality(mp3Quality)
    //设置比特率，关系声音的质量
    mRecorder?.setMp3BitRate(mp3BitRate)
    //设置采样率
    mRecorder?.setSamplingRate(rate)
```



### 1. 录音方式一：[MixRecorder](/doc/MixRecorder.MD)
### 2. 录音方式二： [MP3Recorder](/doc/Mp3Recorder.MD)
### 3. 播放音乐：[AudioPlayer](/doc/AudioPlayer.MD)
### 4. 播放音乐,解码成PCM进行播放：[PlayBackMusic](/doc/PlayBackMusic.MD)
### 5. 播放PCM文件：[AudioTrackManager](/doc/AudioTrackManager.MD)



如果感觉这个库帮助到了你，可以点右上角 "Star" 支持一下 谢谢！ 



### [**Old version**](https://github.com/SheTieJun/Mp3Recorder/tree/master_copy)

### [Update_log](/doc/Update_log.md)

### [License](https://github.com/SheTieJun/Mp3Recorder/blob/master/LICENSE)