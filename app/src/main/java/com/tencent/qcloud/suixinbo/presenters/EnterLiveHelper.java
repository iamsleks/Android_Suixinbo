package com.tencent.qcloud.suixinbo.presenters;

import android.content.Context;
import android.content.Intent;
import android.os.AsyncTask;
import android.text.TextUtils;
import android.util.Log;
import android.view.View;
import android.widget.Toast;

import com.tencent.TIMCallBack;
import com.tencent.TIMConversationType;
import com.tencent.TIMGroupManager;
import com.tencent.TIMManager;
import com.tencent.TIMValueCallBack;
import com.tencent.av.sdk.AVContext;
import com.tencent.av.sdk.AVRoom;
import com.tencent.av.sdk.AVRoomMulti;
import com.tencent.qcloud.suixinbo.R;
import com.tencent.qcloud.suixinbo.avcontrollers.AvConstants;
import com.tencent.qcloud.suixinbo.avcontrollers.QavsdkControl;
import com.tencent.qcloud.suixinbo.model.CurLiveInfo;
import com.tencent.qcloud.suixinbo.model.LiveInfoJson;
import com.tencent.qcloud.suixinbo.model.MySelfInfo;
import com.tencent.qcloud.suixinbo.presenters.viewinface.EnterQuiteRoomView;
import com.tencent.qcloud.suixinbo.utils.Constants;

import org.json.JSONException;
import org.json.JSONObject;

import java.util.ArrayList;


/**
 * 进出房间Presenter
 */
public class EnterLiveHelper extends Presenter {
    private EnterQuiteRoomView mStepInOutView;
    private Context mContext;
    private static final String TAG = EnterLiveHelper.class.getSimpleName();
    private static boolean isInChatRoom = false;
    private static boolean isInAVRoom = false;
    private LiveHelper mLiveHelper;
    private ArrayList<String> video_ids = new ArrayList<String>();

    private static final int TYPE_MEMBER_CHANGE_IN = 1;//进入房间事件。
    private static final int TYPE_MEMBER_CHANGE_OUT = 2;//退出房间事件。
    private static final int TYPE_MEMBER_CHANGE_HAS_CAMERA_VIDEO = 3;//有发摄像头视频事件。
    private static final int TYPE_MEMBER_CHANGE_NO_CAMERA_VIDEO = 4;//无发摄像头视频事件。
    private static final int TYPE_MEMBER_CHANGE_HAS_AUDIO = 5;//有发语音事件。
    private static final int TYPE_MEMBER_CHANGE_NO_AUDIO = 6;//无发语音事件。
    private static final int TYPE_MEMBER_CHANGE_HAS_SCREEN_VIDEO = 7;//有发屏幕视频事件。
    private static final int TYPE_MEMBER_CHANGE_NO_SCREEN_VIDEO = 8;//无发屏幕视频事件。


    public EnterLiveHelper(Context context, EnterQuiteRoomView view) {
        mContext = context;
        mStepInOutView = view;
    }


    /**
     * 进入一个直播房间流程
     */
    public void startEnterRoom() {
        if (MySelfInfo.getInstance().getIdStatus() == Constants.HOST) {
            createLive();
        } else {
            Log.i(TAG, "joinLiveRoom startEnterRoom ");
            joinLive(CurLiveInfo.getRoomNum());
        }

    }


    /**
     * 房间回调
     */
    private AVRoomMulti.Delegate mRoomDelegate = new AVRoomMulti.Delegate() {
        // 创建房间成功回调
        public void onEnterRoomComplete(int result) {
            Log.i(TAG, "createlive joinLiveRoom createAVRoom callback " + result);
            if (result == 0) {
                //只有进入房间后才能初始化AvView
                isInAVRoom = true;
                initAudioService();
                mStepInOutView.EnterRoomComplete(MySelfInfo.getInstance().getIdStatus(), true);
            } else {
                mStepInOutView.EnterRoomComplete(MySelfInfo.getInstance().getIdStatus(), false);
            }

        }

        // 离开房间成功回调
        public void onExitRoomComplete(int result) {
            isInAVRoom = false;
            Log.d(TAG, "WL_DEBUG mRoomDelegate.onExitRoomComplete result = " + result);

        }

        //房间成员变化回调
        public void onEndpointsUpdateInfo(int eventid, String[] updateList) {
            Log.d(TAG, "WL_DEBUG onEndpointsUpdateInfo. eventid = " + eventid);

            switch (eventid) {
                case TYPE_MEMBER_CHANGE_IN:
                    mStepInOutView.memberJoinLive(updateList);
//                    for (String id : updateList) {
//                        String host = CurLiveInfo.getHostID();
//                        if (id.equals(host)) {
////                            mContext.sendBroadcast(new Intent(AvConstants.ACTION_HOST_ENTER));
//                        }
//                    }
                    break;
                case TYPE_MEMBER_CHANGE_HAS_CAMERA_VIDEO:
                    video_ids.clear();
                    for (String id : updateList) {
                        video_ids.add(id);
//                        String host = CurLiveInfo.getHostID();
//                        if (id.equals(host)) {
//                            mContext.sendBroadcast(new Intent(AvConstants.ACTION_HOST_ENTER));
//                        } else {

//                        }
                    }
                    Intent intent = new Intent(AvConstants.ACTION_CAMERA_OPEN_IN_LIVE);
                    intent.putStringArrayListExtra("ids", video_ids);
                    mContext.sendBroadcast(intent);
                    break;
                case TYPE_MEMBER_CHANGE_HAS_AUDIO:
                    break;

                case TYPE_MEMBER_CHANGE_OUT:
                    mStepInOutView.memberQuiteLive(updateList);

            }

//            //用户
//            for (String member : updateList) {
//                Log.i(TAG, " onEndpoints id " + member);
//                if (member.equals(CurLiveInfo.getHostID())) {
//
//                }
//
//            }
        }

        public void OnPrivilegeDiffNotify(int privilege) {
            Log.d(TAG, "OnPrivilegeDiffNotify. privilege = " + privilege);
        }
    };


    /**
     * 1_1 创建一个直播
     */
    private void createLive() {
        createIMChatRoom();

    }

    /**
     * 1_2创建一个IM聊天室
     */
    private void createIMChatRoom() {
        final ArrayList<String> list = new ArrayList<String>();
        final String roomName = "this is a  test";
        Log.i(TAG, "createlive createIMChatRoom " + MySelfInfo.getInstance().getMyRoomNum());
        TIMGroupManager.getInstance().createGroup("ChatRoom", list, roomName, "" + MySelfInfo.getInstance().getMyRoomNum(), new TIMValueCallBack<String>() {
            @Override
            public void onError(int i, String s) {
                Log.i(TAG, "onError " + i + "   " + s);
                //已在房间中,重复进入房间
                if (i == 10025) {
                    isInChatRoom = true;
                    createAVRoom(MySelfInfo.getInstance().getMyRoomNum());
                    return;
                }
                // 创建IM房间失败，提示失败原因，并关闭等待对话框
                Toast.makeText(mContext, " chatroom  error " + s + "i " + i, Toast.LENGTH_SHORT).show();
                quiteLive();
            }

            @Override
            public void onSuccess(String s) {
                isInChatRoom = true;
                //创建AV房间
                createAVRoom(MySelfInfo.getInstance().getMyRoomNum());

            }
        });

    }


    /**
     * 1_3创建一个AV房间
     */
    private void createAVRoom(int roomNum) {
        EnterAVRoom(roomNum);
    }

    /**
     * 初始化Usr
     */
    public void initAvUILayer(View avView) {
        //初始化AVSurfaceView
        if (QavsdkControl.getInstance().getAVContext() != null) {
            QavsdkControl.getInstance().initAvUILayer(mContext.getApplicationContext(), avView);
        }

    }


    /**
     * 1_5上传房间信息
     */
    public void notifyServerCreateRoom() {
        new Thread(new Runnable() {
            @Override
            public void run() {
                JSONObject liveInfo = null;
                try {
                    liveInfo = new JSONObject();
                    if (TextUtils.isEmpty(CurLiveInfo.getTitle())){
                        liveInfo.put("title", mContext.getString(R.string.text_live_default_title));
                    }else {
                        liveInfo.put("title", CurLiveInfo.getTitle());
                    }
                    liveInfo.put("cover", "");
                    liveInfo.put("chatRoomId", CurLiveInfo.getChatRoomId());
                    liveInfo.put("avRoomId", CurLiveInfo.getRoomNum());
                    JSONObject hostinfo = new JSONObject();
                    hostinfo.put("uid", MySelfInfo.getInstance().getId());
                    hostinfo.put("avatar", "");
                    hostinfo.put("username", "");
                    liveInfo.put("host", hostinfo);
                    JSONObject lbs = new JSONObject();
                    lbs.put("longitude", CurLiveInfo.getLong1());
                    lbs.put("latitude", CurLiveInfo.getLat1());
                    if (TextUtils.isEmpty(CurLiveInfo.getAddress())){
                        lbs.put("address", mContext.getString(R.string.text_live_lbs_unknown));
                    }else {
                        lbs.put("address", CurLiveInfo.getAddress());
                    }
                    liveInfo.put("lbs", lbs);

                } catch (JSONException e) {
                    e.printStackTrace();
                }

                if (liveInfo != null)
                    OKhttpHelper.getInstance().notifyServerNewLiveInfo(liveInfo);
            }
        }).start();


    }


    /**
     * 2_1加入一个房间
     */
    private void joinLive(int roomNum) {
        joinIMChatRoom(roomNum);
    }

    /**
     * 2_2加入一个聊天室
     */
    private void joinIMChatRoom(int chatRoomId) {
        Log.i(TAG, "joinLiveRoom joinIMChatRoom " + chatRoomId);
        TIMGroupManager.getInstance().applyJoinGroup("" + chatRoomId, Constants.APPLY_CHATROOM + chatRoomId, new TIMCallBack() {
            @Override
            public void onError(int i, String s) {
                //已经在是成员了
                if (i == Constants.IS_ALREADY_MEMBER) {
                    Log.i(TAG, "joinLiveRoom joinIMChatRoom callback succ ");
                    joinAVRoom(CurLiveInfo.getRoomNum());
                    isInChatRoom = true;
                } else {
                    Toast.makeText(mContext, "join IM room fail " + s + " " + i, Toast.LENGTH_SHORT).show();
                }
            }

            @Override
            public void onSuccess() {
                Log.i(TAG, "joinLiveRoom joinIMChatRoom callback succ ");
                isInChatRoom = true;
                joinAVRoom(CurLiveInfo.getRoomNum());
            }
        });

    }

    /**
     * 2_2加入一个AV房间
     */
    private void joinAVRoom(int avRoomNum) {
//        if (!mQavsdkControl.getIsInEnterRoom()) {
//            initAudioService();
        EnterAVRoom(avRoomNum);
//        }
    }


    /**
     * 退出房间
     */
    public void quiteLive() {
        //退出IM房间
        quiteIMChatRoom();
        //退出AV房间
        quiteAVRoom();
        CurLiveInfo.setCurrentRequestCount(0);
        uninitAudioService();
        //通知结束
        notifyServerLiveEnd();

        mStepInOutView.QuiteRoomComplete(MySelfInfo.getInstance().getIdStatus(), true, null);

    }

    private NotifyServerLiveEnd liveEndTask;

    private void notifyServerLiveEnd() {
        liveEndTask = new NotifyServerLiveEnd();
        liveEndTask.execute(MySelfInfo.getInstance().getId());
    }

    class NotifyServerLiveEnd extends AsyncTask<String, Integer, LiveInfoJson> {

        @Override
        protected LiveInfoJson doInBackground(String... strings) {
            return OKhttpHelper.getInstance().notifyServerLiveStop(strings[0]);
        }

        @Override
        protected void onPostExecute(LiveInfoJson result) {
            mStepInOutView.QuiteRoomComplete(MySelfInfo.getInstance().getIdStatus(), true, result);
        }
    }


    /**
     * 退出一个AV房间
     */
    private void quiteAVRoom() {
        Log.d(TAG, "quiteAVRoom ");
        if (isInAVRoom == true) {
            AVContext avContext = QavsdkControl.getInstance().getAVContext();
            int result = avContext.exitRoom();
        }
    }

    /**
     * 退出IM房间
     */
    private void quiteIMChatRoom() {
        if ((isInChatRoom == true)) {
            //主播解散群
            if (MySelfInfo.getInstance().getIdStatus() == Constants.HOST) {
                TIMGroupManager.getInstance().deleteGroup("" + CurLiveInfo.getRoomNum(), new TIMCallBack() {
                    @Override
                    public void onError(int i, String s) {
                    }

                    @Override
                    public void onSuccess() {
                        isInChatRoom = false;
                    }
                });
                TIMManager.getInstance().deleteConversation(TIMConversationType.Group, "" + MySelfInfo.getInstance().getMyRoomNum());
            } else {
                //成员退出群
                TIMGroupManager.getInstance().quitGroup("" + CurLiveInfo.getRoomNum(), new TIMCallBack() {
                    @Override
                    public void onError(int i, String s) {
                        Log.e(TAG, "onError i: " + i + "  " + s);
                    }

                    @Override
                    public void onSuccess() {
                        Log.i(TAG, "onSuccess ");
                        isInChatRoom = false;
                    }
                });
            }

            //
        }
    }


    /**
     * 通知房间
     */
    private void notifyServerQuit() {

    }

    private void EnterAVRoom(int roomNum) {
        Log.i(TAG, "createlive joinLiveRoom enterAVRoom " + roomNum);
        AVContext avContext = QavsdkControl.getInstance().getAVContext();
        byte[] authBuffer = null;//权限位加密串；TODO：请业务侧填上自己的加密串
        AVRoom.EnterRoomParam enterRoomParam = new AVRoomMulti.EnterRoomParam(roomNum, AvConstants.auth_bits, authBuffer, "", AvConstants.AUDIO_VOICE_CHAT_MODE, true);
        // create room
        int ret = avContext.enterRoom(AVRoom.AV_ROOM_MULTI, mRoomDelegate, enterRoomParam);
        Log.i(TAG, "EnterAVRoom " + ret);
    }


    private void initAudioService() {
        if ((QavsdkControl.getInstance() != null) && (QavsdkControl.getInstance().getAVContext() != null) && (QavsdkControl.getInstance().getAVContext().getAudioCtrl() != null)) {
            QavsdkControl.getInstance().getAVContext().getAudioCtrl().startTRAEService();
        }
    }

    private void uninitAudioService() {
        if ((QavsdkControl.getInstance() != null) && (QavsdkControl.getInstance().getAVContext() != null) && (QavsdkControl.getInstance().getAVContext().getAudioCtrl() != null)) {
            QavsdkControl.getInstance().getAVContext().getAudioCtrl().startTRAEService();
        }
    }

}
