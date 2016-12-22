package com.example.stevennl.tastysnake.controller.game;

import android.animation.Animator;
import android.bluetooth.BluetoothDevice;
import android.content.Context;
import android.content.Intent;
import android.content.res.Configuration;
import android.os.Bundle;
import android.os.Handler;
import android.os.Message;
import android.support.annotation.NonNull;
import android.support.annotation.Nullable;
import android.support.v4.app.Fragment;
import android.support.v4.widget.SwipeRefreshLayout;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.ListView;
import android.widget.TextView;

import com.example.stevennl.tastysnake.Config;
import com.example.stevennl.tastysnake.R;
import com.example.stevennl.tastysnake.model.Snake;
import com.example.stevennl.tastysnake.util.CommonUtil;
import com.example.stevennl.tastysnake.util.bluetooth.BluetoothManager;
import com.example.stevennl.tastysnake.util.bluetooth.listener.OnDiscoverListener;
import com.example.stevennl.tastysnake.util.bluetooth.listener.OnErrorListener;
import com.example.stevennl.tastysnake.util.bluetooth.listener.OnStateChangedListener;
import com.example.stevennl.tastysnake.widget.SnakeImage;

import java.lang.ref.WeakReference;
import java.util.ArrayList;

import static android.app.Activity.RESULT_CANCELED;
import static android.app.Activity.RESULT_OK;

/**
 * Device connection page.
 * Author: LCY
 */
public class ConnectFragment extends Fragment {
    private static final String TAG = "ConnectFragment";
    private static final int REQ_BLUETOOTH_ENABLED = 1;
    private static final int REQ_BLUETOOTH_DISCOVERABLE = 2;

    private GameActivity act;
    private Snake.Type type;

    private SafeHandler handler;
    private BluetoothManager manager;
    private OnStateChangedListener stateListener;
    private OnErrorListener errorListener;

    private ArrayList<BluetoothDevice> devices;

    private ViewGroup rootView;
    private TextView titleTxt;
    private ListViewAdapter adapter;
    private SwipeRefreshLayout refreshLayout;
    private SnakeImage mySnakeImg;
    private SnakeImage enemySnakeImg;

    @Override
    public void onAttach(Context context) {
        super.onAttach(context);
        act = (GameActivity)context;
    }

    @Override
    public void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        initListeners();
        handler = new SafeHandler(this);
        manager = BluetoothManager.getInstance();
        devices = new ArrayList<>();
    }

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container,
                             Bundle savedInstanceState) {
        View v = inflater.inflate(R.layout.fragment_connect, container, false);
        initRootView(v);
        initTitleTxt(v);
        initListView(v);
        initRefreshLayout(v);
        initSnakeImg(v);
        prepare();
        return v;
    }

    @Override
    public void onResume() {
        super.onResume();
        registerDiscoveryReceiver();
    }

    @Override
    public void onPause() {
        super.onPause();
        manager.unregisterDiscoveryReceiver(act);
        manager.cancelDiscovery();
        refreshLayout.setRefreshing(false);
    }

    @Override
    public void onConfigurationChanged(Configuration newConfig) {
        super.onConfigurationChanged(newConfig);
        Log.d(TAG, "onConfigurationChanged() called");
        int orientation = getResources().getConfiguration().orientation;
        if (orientation == Configuration.ORIENTATION_LANDSCAPE) {
            rootView.setVisibility(View.VISIBLE);
        } else if (orientation == Configuration.ORIENTATION_PORTRAIT) {
            rootView.setVisibility(View.INVISIBLE);
        }
    }

    private void initListeners() {
        stateListener = new OnStateChangedListener() {
            @Override
            public void onClientSocketEstablished() {
                Log.d(TAG, "Client socket established.");
                type = Snake.Type.CLIENT;
            }

            @Override
            public void onServerSocketEstablished() {
                Log.d(TAG, "Server socket established.");
                type = Snake.Type.SERVER;
            }

            @Override
            public void onDataChannelEstablished() {
                Log.d(TAG, "Data channel established.");
                manager.stopServer();
                if (isAdded()) {
                    handler.obtainMessage(SafeHandler.MSG_TO_BATTLE).sendToTarget();
                }
            }
        };
        errorListener = new OnErrorListener() {
            @Override
            public void onError(int code, Exception e) {
                Log.e(TAG, "Error code: " + code, e);
                handleErr(code);
            }
        };
    }

    private void initRootView(View v) {
        rootView = (ViewGroup) v.findViewById(R.id.connect_root_view);
    }

    private void initTitleTxt(View v) {
        titleTxt = (TextView) v.findViewById(R.id.connect_titleTxt);
    }

    private void initListView(View v) {
        ListView listView = (ListView) v.findViewById(R.id.connect_device_listView);
        adapter = new ListViewAdapter(act, devices);
        listView.setAdapter(adapter);
        listView.setOnItemClickListener(new AdapterView.OnItemClickListener() {
            @Override
            public void onItemClick(AdapterView<?> parent, View view, int position, long id) {
                if (manager.isClientConnecting()) {
                    CommonUtil.showToast(act, getString(R.string.wait_connecting));
                } else {
                    cancelDiscover();
                    BluetoothDevice device = devices.get(position);
                    titleTxt.setText(getString(R.string.connecting));
                    manager.connectDeviceAsync(device, stateListener, errorListener);
                    refreshLayout.setRefreshing(true);
                }
            }
        });
    }

    private void initRefreshLayout(View v) {
        refreshLayout = (SwipeRefreshLayout) v.findViewById(R.id.connect_swipe_layout);
        refreshLayout.setColorSchemeColors(getResources().getColor(R.color.colorPrimary));
        refreshLayout.setProgressBackgroundColorSchemeColor(Config.COLOR_MAP_BG);
        refreshLayout.setDistanceToTriggerSync(20);
        refreshLayout.setSize(SwipeRefreshLayout.DEFAULT);
        refreshLayout.setNestedScrollingEnabled(true);
        refreshLayout.setOnRefreshListener(new SwipeRefreshLayout.OnRefreshListener() {
            @Override
            public void onRefresh() {
                if (!manager.isEnabled()) {
                    startConnect();
                } else {
                    startDiscover();
                }
            }
        });
    }

    private void initSnakeImg(View v) {
        mySnakeImg = (SnakeImage) v.findViewById(R.id.connect_mySnake_img);
        enemySnakeImg = (SnakeImage) v.findViewById(R.id.connect_enemySnake_img);
    }

    /**
     * Called when the back button is pressed.
     */
    public void onBackPressed() {
        mySnakeImg.cancelAnim();
        enemySnakeImg.cancelAnim();
        mySnakeImg.startExit(null);
        enemySnakeImg.startExit(new SnakeImage.AnimationEndListener() {
            @Override
            public void onAnimationEnd(Animator animation) {
                if (isAdded()) {
                    manager.stopServer();
                    manager.stopConnect();
                    act.replaceFragment(new HomeFragment(), true);
                }
            }
        });
    }

    /**
     * Preparation before connection.
     */
    private void prepare() {
        refreshLayout.setRefreshing(true);
        handler.postDelayed(new Runnable() {
            @Override
            public void run() {
                if (isAdded()) {
                    mySnakeImg.startEnter(null);
                    enemySnakeImg.startEnter(new SnakeImage.AnimationEndListener() {
                        @Override
                        public void onAnimationEnd(Animator animation) {
                            refreshLayout.setRefreshing(false);
                            startConnect();
                        }
                    });
                }
            }
        }, Config.DELAY_CONNECT);
    }

    /**
     * Start connection procedure.
     */
    private void startConnect() {
        if (isAdded()) {
            if (!manager.isEnabled()) {
                startActivityForResult(manager.getEnableIntent(), REQ_BLUETOOTH_ENABLED);
            } else {
                reqDiscoverable();
            }
            refreshLayout.setRefreshing(false);
        }
    }

    /**
     * Request device discoverable.
     */
    private void reqDiscoverable() {
        Intent i = manager.getDiscoverableIntent(Config.BLUETOOTH_DISCOVERABLE_TIME);
        startActivityForResult(i, REQ_BLUETOOTH_DISCOVERABLE);
    }

    @Override
    public void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        switch (requestCode) {
            case REQ_BLUETOOTH_ENABLED:
                if (resultCode == RESULT_OK) {
                    Log.d(TAG, "Bluetooth enabled.");
                    reqDiscoverable();
                } else if (resultCode == RESULT_CANCELED) {
                    Log.d(TAG, "Fail to enable bluetooth.");
                    CommonUtil.showToast(act, getString(R.string.bluetooth_unable));
                }
                break;
            case REQ_BLUETOOTH_DISCOVERABLE:
                if (resultCode == Config.BLUETOOTH_DISCOVERABLE_TIME) {
                    Log.d(TAG, "Your device can be discovered in " + Config.BLUETOOTH_DISCOVERABLE_TIME + " seconds.");
                } else if (resultCode == RESULT_CANCELED) {
                    Log.d(TAG, "Device cannot be discovered.");
                }
                startDiscover();
                break;
            default:
                break;
        }
    }

    /**
     * Start device discovery.
     */
    private void startDiscover() {
        manager.runServerAsync(stateListener, errorListener);
        refreshLayout.setRefreshing(true);
        devices.clear();
        adapter.notifyDataSetChanged();
        manager.cancelDiscovery();
        if (manager.startDiscovery()) {
            Log.d(TAG, "Discovering...");
            scheduleCancelDiscover();
        } else {
            CommonUtil.showToast(act, getString(R.string.device_discover_fail));
            refreshLayout.setRefreshing(false);
        }
    }

    /**
     * Cancel discovery after a period of time.
     */
    private void scheduleCancelDiscover() {
        handler.postDelayed(new Runnable() {
            @Override
            public void run() {
                if (isAdded()) {
                    cancelDiscover();
                }
            }
        }, Config.BLUETOOTH_DISCOVER_TIME);
    }

    /**
     * Cancel bluetooth discovery process.
     */
    private void cancelDiscover() {
        refreshLayout.setRefreshing(titleTxt.getText().toString().equals(getString(R.string.connecting)));
        Log.d(TAG, "Discover finished.");
        manager.cancelDiscovery();
        if (devices.isEmpty()) {
            Log.d(TAG, "No device discovered.");
            if (isAdded()) {
                CommonUtil.showToast(act, getString(R.string.device_discover_empty));
            }
        }
    }

    /**
     * Register device discovery receiver.
     */
    private void registerDiscoveryReceiver() {
        manager.registerDiscoveryReceiver(act, new OnDiscoverListener() {
            @Override
            public void onDiscover(BluetoothDevice device) {
                Log.d(TAG, "Device discovered: " + device.getName() + " " + device.getAddress());
                if (device.getName() != null) {
                    devices.add(device);
                    handler.obtainMessage(SafeHandler.MSG_REFRESH_ADAPTER).sendToTarget();
                }
            }
        });
    }

    /**
     * Handle errors.
     *
     * @param code The error code
     */
    private void handleErr(int code) {
        if (!isAdded()) {
            return;
        }
        switch (code) {
            case OnErrorListener.ERR_SERVER_SOCKET_ACCEPT:
                break;
            case OnErrorListener.ERR_CLIENT_SOCKET_CONNECT:
                handler.obtainMessage(SafeHandler.MSG_ERR,
                        getString(R.string.device_conn_fail)).sendToTarget();
                break;
            case OnErrorListener.ERR_SOCKET_CREATE:
            case OnErrorListener.ERR_STREAM_CREATE:
            case OnErrorListener.ERR_STREAM_READ:
            case OnErrorListener.ERR_STREAM_WRITE:
                handler.obtainMessage(SafeHandler.MSG_ERR,
                        String.format(getString(R.string.connect_exception), code)).sendToTarget();
                break;
            case OnErrorListener.ERR_SOCKET_CLOSE:
                break;
            default:
                break;
        }
    }

    /**
     * A safe handler that circumvents memory leaks.
     */
    private static class SafeHandler extends Handler {
        private static final int MSG_ERR = 1;
        private static final int MSG_REFRESH_ADAPTER = 2;
        private static final int MSG_TO_BATTLE = 3;
        private WeakReference<ConnectFragment> fragment;

        private SafeHandler(ConnectFragment fragment) {
            this.fragment = new WeakReference<>(fragment);
        }

        @Override
        public void handleMessage(Message msg) {
            final ConnectFragment f = fragment.get();
            if (!f.isAdded()) {
                return;
            }
            switch (msg.what) {
                case MSG_ERR:
                    CommonUtil.showToast(f.act, (String)msg.obj);
                    f.refreshLayout.setRefreshing(false);
                    f.titleTxt.setText(f.getString(R.string.select_device));
                    break;
                case MSG_REFRESH_ADAPTER:
                    f.adapter.notifyDataSetChanged();
                    break;
                case MSG_TO_BATTLE:
                    f.mySnakeImg.cancelAnim();
                    f.enemySnakeImg.cancelAnim();
                    f.mySnakeImg.startExit(null);
                    f.enemySnakeImg.startExit(new SnakeImage.AnimationEndListener() {
                        @Override
                        public void onAnimationEnd(Animator animation) {
                            if (f.isAdded()) {
                                f.act.replaceFragment(BattleFragment.newInstance(f.type), true);
                            }
                        }
                    });
                    break;
                default:
                    break;
            }
        }
    }

    /**
     * Device ListView adapter.
     */
    private class ListViewAdapter extends ArrayAdapter<BluetoothDevice> {
        private LayoutInflater inflater;

        private ListViewAdapter(Context context, ArrayList<BluetoothDevice> devList) {
            super(context, 0, devList);
            this.inflater = LayoutInflater.from(context);
        }

        @NonNull
        @Override
        public View getView(int position, View convertView, @NonNull ViewGroup parent) {
            ViewHolder holder;
            if (convertView == null) {
                convertView = inflater.inflate(R.layout.item_list_device, parent, false);
                holder = new ListViewAdapter.ViewHolder();
                holder.devNameTxt = (TextView) convertView.findViewById(R.id.item_list_device_nameTxt);
                convertView.setTag(holder);
            } else {
                holder = (ListViewAdapter.ViewHolder)convertView.getTag();
            }
            BluetoothDevice device = getItem(position);
            if (device != null) {
                holder.devNameTxt.setText(device.getName());
            }
            return convertView;
        }

        private class ViewHolder {
            private TextView devNameTxt;
        }
    }
}
