package com.example.stevennl.tastysnake.controller.game;

import android.content.Context;
import android.os.Bundle;
import android.os.Handler;
import android.os.Message;
import android.support.annotation.Nullable;
import android.support.v4.app.Fragment;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import com.example.stevennl.tastysnake.Config;
import com.example.stevennl.tastysnake.R;
import com.example.stevennl.tastysnake.controller.game.thread.SendThread;
import com.example.stevennl.tastysnake.model.Direction;
import com.example.stevennl.tastysnake.model.Map;
import com.example.stevennl.tastysnake.model.Packet;
import com.example.stevennl.tastysnake.model.Pos;
import com.example.stevennl.tastysnake.model.Snake;
import com.example.stevennl.tastysnake.util.CommonUtil;
import com.example.stevennl.tastysnake.util.bluetooth.BluetoothManager;
import com.example.stevennl.tastysnake.util.bluetooth.listener.OnDataReceiveListener;
import com.example.stevennl.tastysnake.util.bluetooth.listener.OnErrorListener;
import com.example.stevennl.tastysnake.util.sensor.SensorController;
import com.example.stevennl.tastysnake.widget.DrawableGrid;

import java.lang.ref.WeakReference;
import java.util.Timer;
import java.util.TimerTask;

/**
 * Game battle page.
 * Author: LCY
 */
public class BattleFragment extends Fragment {
    private static final String TAG = "BattleFragment";

    private GameActivity act;

    private Timer timer;
    private SafeHandler handler;
    private BluetoothManager manager;
    private SensorController sensorCtrl;

    private SendThread sendThread;

    private DrawableGrid grid;
    private TextView timeTxt;
    private TextView roleTxt;
    private TextView infoTxt;

    private Map map;
    private Snake mySnake;
    private Snake enemySnake;
    private Snake.Type type = Snake.Type.CLIENT;  // Distinguish server/client

    private boolean gameStarted = false;
    private int timeRemain;
    private boolean attack;
    private Snake.Type nextAttacker = Snake.Type.CLIENT;

    // Debug fields
    private int recvCnt = 0;

    /**
     * Create a {@link BattleFragment} with a given snake type.
     */
    public static BattleFragment newInstance(Snake.Type type) {
        BattleFragment fragment = new BattleFragment();
        fragment.type = type;
        return fragment;
    }

    @Override
    public void onAttach(Context context) {
        super.onAttach(context);
        act = (GameActivity)context;
    }

    @Override
    public void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        attack = isServer();  // Default attacker is SERVER snake
        initHandler();
        initSnake();
        initManager();
        initSensor();
        initSendThread();
    }

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container,
                             Bundle savedInstanceState) {
        View v = inflater.inflate(R.layout.fragment_battle, container, false);
        initGrid(v);
        initInfoTxt(v);
        initTimeTxt(v);
        initRoleTxt(v);
        handler.postDelayed(new Runnable() {
            @Override
            public void run() {
                prepare();
            }
        }, Config.DELAY_BATTLE);
        return v;
    }

    @Override
    public void onResume() {
        super.onResume();
        sensorCtrl.register();
    }

    @Override
    public void onPause() {
        super.onPause();
        sensorCtrl.unregister();
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
        manager.stopConnect();
        sendThread.quitSafely();
        stopGame(false);
    }

    private void initHandler() {
        handler = new SafeHandler(this);
    }

    private void initSnake() {
        map = Map.gameMap();
        mySnake = new Snake(type, map, Config.COLOR_SNAKE_MY);
        Snake.Type enemyType = isServer() ? Snake.Type.CLIENT : Snake.Type.SERVER;
        enemySnake = new Snake(enemyType, map, Config.COLOR_SNAKE_ENEMY);
    }

    private void initManager() {
        manager = BluetoothManager.getInstance();
        manager.setErrorListener(new OnErrorListener() {
            @Override
            public void onError(int code, Exception e) {
                Log.e(TAG, "Error code: " + code, e);
                if (isAdded()) {
                    handleErr(code);
                }
            }
        });
        manager.setDataListener(new OnDataReceiveListener() {
            @Override
            public void onReceive(int bytesCount, byte[] data) {
                Packet pkt = new Packet(data);
                Log.d(TAG, "Receive packet: " + pkt.toString() + " Cnt: " + (++recvCnt));
                switch (pkt.getType()) {
                    case DIRECTION:
                        Direction direc = pkt.getDirec();
                        Snake.MoveResult res = enemySnake.move(direc);
                        handleMoveResult(enemySnake, res);
                        break;
                    case FOOD_LENGTHEN:
                        map.createFood(pkt.getFoodX(), pkt.getFoodY(), true);
                        break;
                    case FOOD_SHORTEN:
                        map.createFood(pkt.getFoodX(), pkt.getFoodY(), false);
                        break;
                    case RESTART:
                        attack = (type == pkt.getAttacker());
                        handler.obtainMessage(SafeHandler.MSG_RESTART_GAME).sendToTarget();
                        break;
                    case TIME:
                        timeRemain = pkt.getTime();
                        handler.obtainMessage(SafeHandler.MSG_UPDATE_TIME).sendToTarget();
                        break;
                    case WIN:
                        stopGame(false);
                        String infoStr = CommonUtil.getWinLoseStr(act, pkt.getWinner() == type);
                        handler.obtainMessage(SafeHandler.MSG_TOAST, infoStr).sendToTarget();
                    default:
                        break;
                }
            }
        });
    }

    private void initSensor() {
        sensorCtrl = SensorController.getInstance(act);
    }

    private void initSendThread() {
        sendThread = new SendThread();
        sendThread.start();
    }

    private void initGrid(View v) {
        grid = (DrawableGrid) v.findViewById(R.id.battle_grid);
        grid.setVisibility(View.GONE);
        grid.setBgColor(Config.COLOR_MAP_BG);
        grid.setMap(map);
        grid.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                if (isServer() && !gameStarted) {
                    gameStarted = true;
                    sendThread.send(Packet.restart(nextAttacker));
                    attack = (type == nextAttacker);
                    nextAttacker = (nextAttacker == Snake.Type.SERVER
                            ? Snake.Type.CLIENT : Snake.Type.SERVER);
                    restart();
                }
            }
        });
    }

    private void initInfoTxt(View v) {
        infoTxt = (TextView) v.findViewById(R.id.battle_infoTxt);
    }

    private void initTimeTxt(View v) {
        timeTxt = (TextView) v.findViewById(R.id.battle_timeTxt);
        timeRemain = Config.DURATION_ATTACK - 1;
        updateTimeTxt();
    }

    private void initRoleTxt(View v) {
        roleTxt = (TextView) v.findViewById(R.id.battle_roleTxt);
        updateRoleTxt();
    }

    /**
     * Restart the game.
     */
    private void restart() {
        infoTxt.setText("");
        stopTimer();
        initSnake();
        grid.setMap(map);
        timeRemain = Config.DURATION_ATTACK - 1;
        updateTimeTxt();
        updateRoleTxt();
        prepare();
    }

    /**
     * Preparation before starting the game.
     */
    private void prepare() {
        gameStarted = true;
        if (isAdded()) {
            grid.setVisibility(View.VISIBLE);
            infoTxt.setVisibility(View.VISIBLE);
            infoTxt.setText("");
            if (timer == null) {
                timer = new Timer();
            }
            timer.schedule(new TimerTask() {
                private int prepareTimeRemain = Config.DURATION_GAME_PREPARE;
                private final String startStr = getString(R.string.game_start);

                @Override
                public void run() {
                    if (infoTxt.getText().toString().equals(startStr)) {
                        handler.obtainMessage(SafeHandler.MSG_HIDE_INFO).sendToTarget();
                        startGame();
                    } else {
                        String infoStr = (prepareTimeRemain == 0
                                ? startStr : String.valueOf(prepareTimeRemain--));
                        handler.obtainMessage(SafeHandler.MSG_UPDATE_INFO, infoStr).sendToTarget();
                    }
                }
            }, 0, 1000);
        }
    }

    /**
     * Start the game.
     */
    private void startGame() {
        stopTimer();
        if (timer == null) {
            timer = new Timer();
        }
        if (isServer()) {
            startTiming();
        } else {
            startCreateFood();
        }
        startMove();
    }

    /**
     * Start a thread to create food.
     */
    private void startCreateFood() {
        timer.schedule(new TimerTask() {
            private boolean lengthen = false;

            @Override
            public void run() {
                Pos food = map.createFood(lengthen = true);
                sendThread.send(Packet.food(food.getX(), food.getY(), lengthen));
            }
        }, 0, Config.FREQUENCY_FOOD);
    }

    /**
     * Start a thread to calculate remaining time.
     */
    private void startTiming() {
        timer.schedule(new TimerTask() {
            @Override
            public void run() {
                sendThread.send(Packet.time(timeRemain));
                handler.obtainMessage(SafeHandler.MSG_UPDATE_TIME).sendToTarget();
            }
        }, 0, 1000);
    }

    /**
     * Stat a thread to move 'mySnake'.
     */
    private void startMove() {
        timer.schedule(new TimerTask() {
            @Override
            public void run() {
                Direction direc = sensorCtrl.getDirection();
                sendThread.send(Packet.direction(direc));
                Snake.MoveResult res = mySnake.move(direc);
                handleMoveResult(mySnake, res);
            }
        }, 0, Config.FREQUENCY_MOVE);
    }

    /**
     * Handle snake's move result.
     *
     * @param snake The snake who generated the move result
     * @param result The move result.
     */
    private void handleMoveResult(Snake snake, Snake.MoveResult result) {
        if (!isAdded()) {
            return;
        }
        switch (result) {
            case SUC:
                break;
            case SUICIDE:
            case OUT:
                if (isServer()) {
                    Snake.Type winner = (snake == mySnake ? enemySnake.getType() : type);
                    sendThread.send(Packet.win(winner));
                    String infoStr = (type == winner ? getString(R.string.win) : getString(R.string.lose));
                    handler.obtainMessage(SafeHandler.MSG_TOAST, infoStr).sendToTarget();
                }
                stopGame(true);
                break;
            case HIT_ENEMY:
                if (isServer()) {
                    Snake.Type winner = (attack ? type : enemySnake.getType());
                    sendThread.send(Packet.win(winner));
                    String infoStr = (attack ? getString(R.string.win) : getString(R.string.lose));
                    handler.obtainMessage(SafeHandler.MSG_TOAST, infoStr).sendToTarget();
                    stopGame(true);
                }
                break;
            default:
                break;
        }
    }

    /**
     * Stop the game.
     *
     * @param showRestart If true, the restart info will be shown, false otherwise
     */
    private void stopGame(boolean showRestart) {
        gameStarted = false;
        stopTimer();
        if (showRestart && isServer()) {
            handler.obtainMessage(SafeHandler.MSG_UPDATE_INFO,
                    getString(R.string.click_to_restart)).sendToTarget();
        }
    }

    /**
     * Stop the timer.
     */
    private void stopTimer() {
        if (timer != null) {
            timer.cancel();
            timer = null;
        }
    }

    /**
     * Update remaining time TextView.
     */
    private void updateTimeTxt() {
        String timeStr = String.valueOf(timeRemain);
        if (timeRemain / 10 == 0) {
            timeStr = "0" + timeStr;
        }
        timeTxt.setText(String.format(getString(R.string.switch_role_remain), timeStr));
    }

    /**
     * Update the role TextView.
     */
    private void updateRoleTxt() {
        roleTxt.setText(CommonUtil.getAttackStr(act, attack));
    }

    /**
     * Return true if current device is the bluetooth server.
     */
    private boolean isServer() {
        return type == Snake.Type.SERVER;
    }

    /**
     * Handle errors.
     *
     * @param code The error code
     */
    private void handleErr(int code) {
        switch (code) {
            case OnErrorListener.ERR_SOCKET_CLOSE:
            case OnErrorListener.ERR_STREAM_READ:
            case OnErrorListener.ERR_STREAM_WRITE:
                stopGame(false);
                handler.obtainMessage(SafeHandler.MSG_TOAST, getString(R.string.disconnect)).sendToTarget();
                handler.post(new Runnable() {
                    @Override
                    public void run() {
                        if (isAdded()) {
                            act.replaceFragment(new HomeFragment(), true);
                        }
                    }
                });
                break;
            default:
                break;
        }
    }

    /**
     * A safe handler that circumvents memory leaks.
     */
    private static class SafeHandler extends Handler {
        private static final int MSG_TOAST = 1;
        private static final int MSG_RESTART_GAME = 2;
        private static final int MSG_UPDATE_TIME = 3;
        private static final int MSG_UPDATE_ROLE = 4;
        private static final int MSG_UPDATE_INFO = 5;
        private static final int MSG_HIDE_INFO = 6;
        private WeakReference<BattleFragment> fragment;

        private SafeHandler(BattleFragment fragment) {
            this.fragment = new WeakReference<>(fragment);
        }

        @Override
        public void handleMessage(Message msg) {
            final BattleFragment f = fragment.get();
            if (!f.isAdded()) {
                return;
            }
            switch (msg.what) {
                case MSG_TOAST:
                    CommonUtil.showToast(f.act, (String)msg.obj);
                    break;
                case MSG_RESTART_GAME:
                    f.restart();
                    break;
                case MSG_UPDATE_TIME:
                    if (f.timeRemain == -1) {
                        f.attack = !f.attack;
                        f.timeRemain = Config.DURATION_ATTACK - 1;
                        f.updateRoleTxt();
                        if (f.gameStarted) {
                            obtainMessage(MSG_UPDATE_INFO,
                                    CommonUtil.getAttackInfoStr(f.act,f.attack)).sendToTarget();
                            postDelayed(new Runnable() {
                                @Override
                                public void run() {
                                    if (f.isAdded() && f.gameStarted) {
                                        obtainMessage(MSG_HIDE_INFO).sendToTarget();
                                    }
                                }
                            }, Config.DELAY_ROLE_SWITCH_INFO);
                        }
                    }
                    f.updateTimeTxt();
                    if (f.isServer()) {
                        --f.timeRemain;
                    }
                    break;
                case MSG_UPDATE_INFO:
                    f.infoTxt.setVisibility(View.VISIBLE);
                    f.infoTxt.setText((String)msg.obj);
                    break;
                case MSG_HIDE_INFO:
                    f.infoTxt.setVisibility(View.GONE);
                    break;
                case MSG_UPDATE_ROLE:
                    f.updateRoleTxt();
                    break;
                default:
                    break;
            }
        }
    }
}
