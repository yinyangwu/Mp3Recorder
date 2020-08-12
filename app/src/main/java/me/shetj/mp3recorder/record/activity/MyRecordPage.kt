package me.shetj.mp3recorder.record.activity

import android.app.Activity
import android.content.Intent
import android.transition.Scene
import android.transition.TransitionManager
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.RelativeLayout
import androidx.recyclerview.widget.RecyclerView
import com.chad.library.adapter.base.BaseQuickAdapter
import com.chad.library.adapter.base.BaseViewHolder
import me.shetj.audio.record.view.RecordBottomSheetDialog
import me.shetj.base.tools.app.ArmsUtils
import me.shetj.mp3recorder.R
import me.shetj.mp3recorder.record.adapter.RecordAdapter
import me.shetj.mp3recorder.record.bean.Record
import me.shetj.mp3recorder.record.bean.RecordDbUtils
import me.shetj.mp3recorder.record.utils.Callback
import me.shetj.mp3recorder.record.utils.MainThreadEvent
import org.simple.eventbus.EventBus
import org.simple.eventbus.Subscriber
import org.simple.eventbus.ThreadMode
import java.util.*

/**
 */
class MyRecordPage(private val context: Activity, mRoot: ViewGroup, private var callback: Callback) {

    private var root: RelativeLayout? = null
    val scene: Scene
    private var mRecyclerView: RecyclerView? = null
    private var mIvRecordState: ImageView? = null
    private lateinit var recordAdapter: RecordAdapter
    private var mRlRecordView: FrameLayout? = null


    /**
     * 得到当前选中的record
     */
    val curRecord: Record?
        get() = if (recordAdapter.curPosition != -1) {
            recordAdapter.getItem(recordAdapter.curPosition)
        } else null


    init {
        root = LayoutInflater.from(context).inflate(R.layout.page_my_record, null) as RelativeLayout
        scene = Scene(mRoot, root as View)
        initView(root)
        initData()
    }

    private fun initData() {
        val allRecord = RecordDbUtils.getInstance().allRecord
        recordAdapter.setNewData(allRecord)
        checkShow(allRecord)
    }

    /**
     * 判断是不是存在录音
     */
    private fun checkShow(allRecord: List<Record>) {
        TransitionManager.beginDelayedTransition(root)
        if (allRecord.isNotEmpty()) {
            mRlRecordView!!.visibility = View.VISIBLE
        } else {
            mRlRecordView!!.visibility = View.GONE
        }
    }

    private fun initView(view: View?) {
        EventBus.getDefault().register(this)
        //绑定view
        mRecyclerView = view!!.findViewById(R.id.recycler_view)
        mIvRecordState = view.findViewById(R.id.iv_record_state)
        mRlRecordView = view.findViewById(R.id.rl_record_view)
        //设置界面
        recordAdapter = RecordAdapter(ArrayList())
        recordAdapter.bindToRecyclerView(mRecyclerView)
        //设置点击
        recordAdapter.setOnItemClickListener { _, _, position -> recordAdapter.setPlayPosition(position) }
        recordAdapter.setOnItemChildClickListener { adapter, view1, position ->
            if (!recordAdapter.isUploading) {
                when (view1.id) {
                    R.id.tv_more -> {
                        val dialog = showBottomDialog(position, adapter)
                        dialog?.showBottomSheet()
                    }
                    else -> {
                    }
                }
            }
        }
        recordAdapter.setOnItemLongClickListener { adapter, _, position ->
            recordAdapter.setPlayPosition(position)
            val dialog = showBottomDialog(position, adapter)
            dialog?.showBottomSheet()
            true
        }

        //设置空界面
        val emptyView = LayoutInflater.from(context).inflate(R.layout.empty_view, null)
        recordAdapter.emptyView = emptyView
        //空界面点击开启
        emptyView.findViewById<View>(R.id.cd_start_record).setOnClickListener { v ->
            if (!recordAdapter.isUploading) {
                callback.onEvent(0)
            }
        }


        //添加一个head
        val headView = View(context)
        headView.layoutParams = ViewGroup.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ArmsUtils.dip2px(35f))
        recordAdapter.addHeaderView(headView)
        //去录音界面
        mIvRecordState!!.setOnClickListener { v ->
            if (!recordAdapter.isUploading) {
                recordAdapter.setPlayPosition(-1)
                callback.onEvent(0)
            }
        }

    }

    private fun showBottomDialog(position: Int, adapter: BaseQuickAdapter<Any, BaseViewHolder>): RecordBottomSheetDialog? {
        recordAdapter.onPause()
        return recordAdapter.getItem(position)?.let {
            (mRecyclerView!!.findViewHolderForAdapterPosition(position + adapter.headerLayoutCount) as BaseViewHolder).let { it1 ->
                RecordBottomSheetDialog(context, position, it,
                        it1, callback)
            }
        }
    }


    @Subscriber(mode = ThreadMode.MAIN)
    fun refreshData(event: MainThreadEvent<*>) {
        when (event.type) {
            MainThreadEvent.RECORD_REFRESH_MY -> {
                //获取数据库中最后一个数据
                val lastRecord = RecordDbUtils.getInstance().lastRecord
                if (null != lastRecord) {
                    recordAdapter.addData(0, lastRecord)
                    recordAdapter.setPlayPosition(0)
                    mRecyclerView!!.scrollToPosition(0)
                    checkShow(recordAdapter.data)
                }
            }
            MainThreadEvent.RECORD_REFRESH_DEL -> {
                //删除录音
                recordAdapter.setPlayPosition(-1)
                recordAdapter.remove(event.content as Int)
                checkShow(recordAdapter.data)
            }
            MainThreadEvent.RECORD_REFRESH_RECORD -> {
                //继续录制后，保存后刷新
                val i = recordAdapter.data.indexOf(event.content)
                if (i != -1) {
                    recordAdapter.notifyItemChanged(i + recordAdapter.headerLayoutCount)
                }
            }
            MainThreadEvent.RECORD_POST_URL -> {
                //上传录音成功后，通知直接做其他操作
                val mp3Url = event.content!!.toString()
                //如果需要关闭说明是来之发布界面的上传视频
                if (context.intent.getBooleanExtra(MyRecordActivity.NEED_CLOSE, false)) {
                    val intent = Intent()
                    intent.putExtra(MyRecordActivity.POST_URL, mp3Url)
                    context.setResult(Activity.RESULT_OK, intent)
                    context.finish()//需要
                }
            }
        }
    }


    fun onDestroy() {
        EventBus.getDefault().unregister(this)
        recordAdapter.onDestroy()
        root = null
    }

    fun onPause() {
        recordAdapter.onPause()
    }
}