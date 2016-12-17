package com.example.stevennl.tastysnake.util;

import android.animation.ObjectAnimator;
import android.content.Context;
import android.util.Log;
import android.view.View;
import android.widget.Toast;

import com.example.stevennl.tastysnake.Config;
import com.example.stevennl.tastysnake.R;

import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;
import java.util.Random;

/**
 * Commonly used methods.
 */
public class CommonUtil {
    private static final String TAG = "CommonUtil";
    private static final String ATTR_ALPHA = "alpha";
    private static final Random random = new Random();
    private static final SimpleDateFormat dateFormat = new SimpleDateFormat("yyyy/MM/dd HH:mm:ss", Locale.US);

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
     * Show a view gracefully.
     *
     * @param v The view to be shown
     */
    public static void showViewPretty(View v) {
        v.setAlpha(0);
        v.setVisibility(View.VISIBLE);
        ObjectAnimator.ofFloat(v, ATTR_ALPHA, 0, 1)
                .setDuration(Config.DURATION_SHOW_PRETTY).start();
    }

    /**
     * Return a random integer in [0, max).
     */
    public static int randInt(int max) {
        return random.nextInt(max);
    }

    /**
     * Convert date to string.
     */
    public static String formatDate(Date date) {
        return dateFormat.format(date);
    }

    /**
     * Convert string to date.
     */
    public static Date parseDateStr(String dateStr) {
        try {
            return dateFormat.parse(dateStr);
        } catch (ParseException e) {
            Log.e(TAG, "Error:", e);
            return null;
        }
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
