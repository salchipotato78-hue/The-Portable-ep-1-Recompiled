package com.s4ep1recomp.sonic4;

import android.content.pm.ActivityInfo;
import android.graphics.drawable.Drawable;
import android.os.Bundle;
import android.view.Gravity;
import android.view.MotionEvent;
import android.view.ViewGroup;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.TextView;

import org.libsdl.app.SDLActivity;

public class Sonic4Activity extends SDLActivity {
    private static final int BUTTON_SOUTH = 0;
    private static final int BUTTON_EAST = 1;
    private static final int BUTTON_WEST = 2;
    private static final int BUTTON_NORTH = 3;
    private static final int BUTTON_START = 6;
    private static final int BUTTON_DPAD_UP = 11;
    private static final int BUTTON_DPAD_DOWN = 12;
    private static final int BUTTON_DPAD_LEFT = 13;
    private static final int BUTTON_DPAD_RIGHT = 14;
    private static final int AXIS_LEFTX = 0;
    private static final int AXIS_LEFTY = 1;

    private static final int DIR_NONE = -1;
    private static final int DIR_UP = 0;
    private static final int DIR_DOWN = 1;
    private static final int DIR_LEFT = 2;
    private static final int DIR_RIGHT = 3;

    private static final float FACE_ALPHA = 0.7f;

    @Override
    protected String[] getLibraries() {
        // SDL's JNI entry points are linked into librexruntime. libmain holds SDL_main.
        return new String[] {"c++_shared", "rexruntime", "main"};
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        setRequestedOrientation(ActivityInfo.SCREEN_ORIENTATION_SENSOR_LANDSCAPE);
        super.onCreate(savedInstanceState);
        addTouchControls();
    }

    @Override
    public void setOrientationBis(int w, int h, boolean resizable, String hint) {
        setRequestedOrientation(ActivityInfo.SCREEN_ORIENTATION_SENSOR_LANDSCAPE);
    }

    private void addTouchControls() {
        if (mLayout == null) {
            return;
        }
        FrameLayout overlay = new FrameLayout(this);
        mLayout.addView(overlay, new ViewGroup.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT));

        addDPad(overlay);

        // Face buttons (bottom right), same layout as the previous version.
        addFace(overlay, Gravity.END | Gravity.BOTTOM, 36, 48, 72, BUTTON_SOUTH, "A", "icon_sonic");
        addFace(overlay, Gravity.END | Gravity.BOTTOM, 132, 96, 72, BUTTON_EAST, "B", null);
        addFace(overlay, Gravity.END | Gravity.BOTTOM, 132, 180, 72, BUTTON_WEST, "X", null);
        addFace(overlay, Gravity.END | Gravity.BOTTOM, 228, 132, 72, BUTTON_NORTH, "Y", null);

        // Pause / start (top right) on the plain ring.
        addFace(overlay, Gravity.END | Gravity.TOP, 24, 24, 72, BUTTON_START, null, "icon_pause");
    }

    private void addDPad(FrameLayout overlay) {
        final ImageView active = new ImageView(this);
        active.setAlpha(0f);
        Drawable activeSkin = skin("dpad_active");
        if (activeSkin != null) {
            active.setImageDrawable(activeSkin);
        }

        FrameLayout pad = new FrameLayout(this);
        ImageView base = new ImageView(this);
        Drawable baseSkin = skin("dpad_base");
        if (baseSkin != null) {
            base.setImageDrawable(baseSkin);
        }
        pad.addView(base, new FrameLayout.LayoutParams(dp(200), dp(200)));
        pad.addView(active, new FrameLayout.LayoutParams(dp(200), dp(200)));

        final int[] current = {DIR_NONE};
        pad.setOnTouchListener((v, e) -> {
            int action = e.getActionMasked();
            int dir = DIR_NONE;
            if (action != MotionEvent.ACTION_UP && action != MotionEvent.ACTION_CANCEL) {
                float cx = v.getWidth() / 2f;
                float cy = v.getHeight() / 2f;
                float dx = e.getX() - cx;
                float dy = e.getY() - cy;
                float deadzone = Math.min(cx, cy) * 0.25f;
                if (Math.hypot(dx, dy) > deadzone) {
                    if (Math.abs(dx) > Math.abs(dy)) {
                        dir = dx < 0 ? DIR_LEFT : DIR_RIGHT;
                    } else {
                        dir = dy < 0 ? DIR_UP : DIR_DOWN;
                    }
                }
            }
            if (dir != current[0]) {
                setDpad(current[0], false);
                setDpad(dir, true);
                current[0] = dir;
                active.setAlpha(dir == DIR_NONE ? 0f : 1f);
            }
            return true;
        });

        FrameLayout.LayoutParams params = new FrameLayout.LayoutParams(dp(200), dp(200), Gravity.START | Gravity.BOTTOM);
        params.leftMargin = dp(16);
        params.bottomMargin = dp(16);
        overlay.addView(pad, params);
    }

    private void addFace(FrameLayout overlay, int gravity, int marginX, int marginY, int sizeDp,
                         int button, String label, String iconName) {
        FrameLayout face = new FrameLayout(this);
        Drawable bg = skin(iconName == null || label != null ? "btn_face" : "btn_ring");
        if (bg != null) {
            ImageView bgView = new ImageView(this);
            bgView.setImageDrawable(bg);
            face.addView(bgView, new FrameLayout.LayoutParams(dp(sizeDp), dp(sizeDp)));
        }

        Drawable icon = iconName == null ? null : skin(iconName);
        if (icon != null) {
            ImageView iconView = new ImageView(this);
            iconView.setImageDrawable(icon);
            int iconSize = sizeDp * 6 / 10;
            face.addView(iconView, new FrameLayout.LayoutParams(dp(iconSize), dp(iconSize), Gravity.CENTER));
        }

        if (label != null && icon == null) {
            TextView text = new TextView(this);
            text.setText(label);
            text.setTextColor(0xFFFFFFFF);
            text.setTextSize(20);
            text.setGravity(Gravity.CENTER);
            face.addView(text, new FrameLayout.LayoutParams(dp(sizeDp), dp(sizeDp), Gravity.CENTER));
        }

        face.setAlpha(FACE_ALPHA);
        face.setOnTouchListener((v, e) -> {
            int action = e.getActionMasked();
            if (action == MotionEvent.ACTION_DOWN) {
                nativeSetButton(button, true);
                v.setAlpha(1f);
                return true;
            }
            if (action == MotionEvent.ACTION_UP || action == MotionEvent.ACTION_CANCEL) {
                nativeSetButton(button, false);
                v.setAlpha(FACE_ALPHA);
                return true;
            }
            return false;
        });

        FrameLayout.LayoutParams params = new FrameLayout.LayoutParams(dp(sizeDp), dp(sizeDp), gravity);
        if ((gravity & Gravity.END) == Gravity.END) {
            params.rightMargin = dp(marginX);
        } else {
            params.leftMargin = dp(marginX);
        }
        if ((gravity & Gravity.TOP) == Gravity.TOP) {
            params.topMargin = dp(marginY);
        } else {
            params.bottomMargin = dp(marginY);
        }
        overlay.addView(face, params);
    }

    private static void setDpad(int dir, boolean down) {
        switch (dir) {
            case DIR_UP:
                nativeSetButton(BUTTON_DPAD_UP, down);
                nativeSetAxis(AXIS_LEFTY, down ? -32767 : 0);
                break;
            case DIR_DOWN:
                nativeSetButton(BUTTON_DPAD_DOWN, down);
                nativeSetAxis(AXIS_LEFTY, down ? 32767 : 0);
                break;
            case DIR_LEFT:
                nativeSetButton(BUTTON_DPAD_LEFT, down);
                nativeSetAxis(AXIS_LEFTX, down ? -32767 : 0);
                break;
            case DIR_RIGHT:
                nativeSetButton(BUTTON_DPAD_RIGHT, down);
                nativeSetAxis(AXIS_LEFTX, down ? 32767 : 0);
                break;
            default:
                break;
        }
    }

    // Skins are looked up by name so this file does not depend on a generated R class:
    // the arm64 packaging script compiles Java against android.jar without R.java.
    private Drawable skin(String name) {
        int id = getResources().getIdentifier(name, "drawable", getPackageName());
        return id == 0 ? null : getResources().getDrawable(id, getTheme());
    }

    private int dp(int value) {
        return Math.round(value * getResources().getDisplayMetrics().density);
    }

    private static native void nativeSetButton(int button, boolean down);

    private static native void nativeSetAxis(int axis, int value);
}
