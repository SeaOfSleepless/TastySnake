package com.example.stevennl.tastysnake.util;

import android.content.Context;
import android.widget.Toast;

import com.example.stevennl.tastysnake.R;

import java.util.Random;

/**
 * Commonly used methods.
 */
public class CommonUtil {
    private static final String TAG = "CommonUtil";
    private static final Random random = new Random();

    /**
     * Show a Toast message
     * @param context The context
     * @param strId The string resource id
     */
    public static void showToast(Context context, int strId) {
        showToast(context, context.getString(strId));
    }

    /**
     * Show a Toast message
     * @param context The context
     * @param str The string content
     */
    public static void showToast(Context context, String str) {
        if (context != null) {
            Toast.makeText(context, str, Toast.LENGTH_SHORT).show();
        }
    }

    /**
     * Return a random integer in [0, max).
     */
    public static int randInt(int max) {
        return random.nextInt(max);
    }

    /**
     * Return winner or loser string.
     *
     * @param context The context
     * @param win If true, return winner string, otherwise return loser string.
     */
    public static String getWinLoseStr(Context context, boolean win) {
        return win ? context.getString(R.string.win) : context.getString(R.string.lose);
    }

    /**
     * Return role string (attacker or defender).
     *
     * @param context The context
     * @param attack If true, return attacker string, otherwise return defender string.
     */
    public static String getAttackStr(Context context, boolean attack) {
        return attack ? context.getString(R.string.role_attacker) : context.getString(R.string.role_defender);
    }

    /**
     * Return role info string.
     *
     * @param context The context
     * @param attack If true, return attack info string, otherwise return defend info string.
     */
    public static String getAttackInfoStr(Context context, boolean attack) {
        return attack ? context.getString(R.string.attack_turn)
                : context.getString(R.string.defend_turn);
    }
}
