## 2020年12月15日
- Fix:没有调用stop，就onDestroy 会崩溃的问题
- 新增方法，可以设置提醒快结束的时间

### 2020年11月6日
- 去掉一些非错误日志，如果没有大的错误，这个就不动了


### 2020年10月21日
- 播放支持 Uri，header (同时兼容AndroidQ)

### 2020年9月3日
- 项目进行拆解、拼装
- 知道单独引用Mix 和Sim 录音


### 2020年8月12日
- 添加声音控制：由系统声音更新
- 添加耳机连接控制，进行判断是否写入声音

### 2020年7月24日
- 有应用接我这个库，录制的效果还不错，不过存在一些未考虑场景，修一下
- Fix :start 的时候file =null 引起的崩溃
- Fix "因为是异步的所以存在结束后还会回调一次Recording,Fix了这问题，但是可能时长显示存在20ms的计算时间误差~

### 2020年3月20日
- 提高录音默认的音质，目前录制文件比较大，提高声音还原度高。如果感觉文件比较大，可以修改以下小参数
  ```
    samplingRate: Int = 44100,
    mp3BitRate: Int = 128,//96(高),32（低）
  ```
- 增加在录音前可以设置录音的参数


### 2020年2月13日
- 优化`MP3Recorder`,添加方法`updateDataEncode`，使之中间替换输出文件
- 上述需求背景：希望录音可以变成60秒，一段一段的文件发送出去，所有每60秒我就替换输出文件，同时把上一个60秒音频文件上传



### 2019年10月31日
- 0.0.4
- 优化录音：单双声道 多模式录制


### 2019年10月31日  
- 版本 ：0.0.3
- 优化lame转双声道PCM ,以前的方法存在噪音
```
   if (is2CHANNEL) {
     //双声道
      readSize = buffer.size / 2
      encodedSize = LameUtils.encodeInterleaved(buffer,readSize,mMp3Buffer)
      } else {
      readSize = buffer.size
      encodedSize = LameUtils.encode(buffer, buffer, readSize, mMp3Buffer)
   }
```

### 2019年10月19日
- 尝试修改录制后声音的大小计算


#### 2019年10月15日
- fix 不设置背景音乐录制崩溃问题


#### 2019年10月11日
- 优化`PlayBackMusic`
    - 加入播放状态和进度回调
- 去掉Androidx的注解，方便不是Androidx的项目使用

#### 2019年10月10日
- 优化`MixRecorder`, 修改使用lame 进行边录制变转换，优化默认录制来源
    - 因为lame边录制边转可以支持, 录制中 进行播放试听
    - 因为背景音乐是合成进去的所有，使用VOICE_COMMUNICATION ,使用系统自带的AEC
 

#### 2019年10月9号
- 添加speex 进行去噪音，但是好像效果不佳，
- 最后去掉了


#### 2019年9月29日
- 优化`MixRecorder`,支持背景音乐切换
- 支持`播放中`或者`暂停`切换背景音乐

#### 2019年9月28日
- 优化`MixRecorder` 背景音乐支持循环播放

#### 2019年8月22日
- `Mp3Recorder`可以设置是否是继续录制功能（已完成）
```
setOutputFile(filePath，isContinue) 
```

#### 2019年8月20日
- `Mp3Recorder`通过扬声器器录制，所有暂时只支持录制麦克风 