package org.telegram.ui.Cells;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Typeface;
import android.text.TextUtils;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.View;
import android.view.accessibility.AccessibilityNodeInfo;
import android.widget.FrameLayout;
import android.widget.TextView;

import org.telegram.messenger.AndroidUtilities;
import org.telegram.messenger.LocaleController;
import org.telegram.messenger.R;
import org.telegram.ui.ActionBar.Theme;
import org.telegram.ui.Components.LayoutHelper;
import org.telegram.ui.Components.Switch;

public class ReactionsEnabledCell extends FrameLayout {

    private TextView textView;
    private Switch checkBox;
    private boolean needDivider;
    private boolean isMultiline;

    public ReactionsEnabledCell(Context context) {
        super(context);

        textView = new TextView(context);
        textView.setTextColor(Theme.getColor(Theme.key_windowBackgroundWhiteBlackText));
        textView.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 16);
        textView.setLines(1);
        textView.setMaxLines(1);
        textView.setSingleLine(true);
        textView.setGravity((LocaleController.isRTL ? Gravity.RIGHT : Gravity.LEFT) | Gravity.CENTER_VERTICAL);
        textView.setEllipsize(TextUtils.TruncateAt.END);
        addView(textView, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, LayoutHelper.MATCH_PARENT, (LocaleController.isRTL ? Gravity.RIGHT : Gravity.LEFT) | Gravity.TOP, LocaleController.isRTL ? 64 : 21, 0, LocaleController.isRTL ? 21 : 64, 0));

        checkBox = new Switch(context);
        checkBox.setColors(Theme.key_switchTrack, Theme.key_switchTrackChecked, Theme.key_windowBackgroundWhite, Theme.key_windowBackgroundWhite);
        addView(checkBox, LayoutHelper.createFrame(37, 50, (LocaleController.isRTL ? Gravity.LEFT : Gravity.RIGHT) | Gravity.CENTER_VERTICAL, 22, 0, 22, 0));
        checkBox.setFocusable(true);
    }

    @Override
    protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
        if (isMultiline) {
            super.onMeasure(View.MeasureSpec.makeMeasureSpec(View.MeasureSpec.getSize(widthMeasureSpec), View.MeasureSpec.EXACTLY), View.MeasureSpec.makeMeasureSpec(0, View.MeasureSpec.UNSPECIFIED));
        } else {
            super.onMeasure(View.MeasureSpec.makeMeasureSpec(View.MeasureSpec.getSize(widthMeasureSpec), View.MeasureSpec.EXACTLY), View.MeasureSpec.makeMeasureSpec(AndroidUtilities.dp(/*valueTextView.getVisibility() == VISIBLE ? 64 : */50) + (needDivider ? 1 : 0), View.MeasureSpec.EXACTLY));
        }
    }

    public void setTextAndCheck(String text, boolean checked, boolean divider) {
        textView.setText(text);
        isMultiline = false;
        checkBox.setChecked(checked, false);
        needDivider = divider;
        FrameLayout.LayoutParams layoutParams = (FrameLayout.LayoutParams) textView.getLayoutParams();
        layoutParams.height = FrameLayout.LayoutParams.MATCH_PARENT;
        layoutParams.topMargin = 0;
        textView.setLayoutParams(layoutParams);
        textView.setTypeface(null, Typeface.BOLD);
        textView.setPadding(10, 10, 10, 10);
        textView.setTextColor(Theme.MSG_OUT_COLOR_WHITE);
        setWillNotDraw(!divider);
    }

    @Override
    public void setEnabled(boolean value) {
        super.setEnabled(value);
        if (value) {
            textView.setAlpha(1.0f);
            checkBox.setAlpha(1.0f);

            this.setBackgroundColor(Theme.getColor(Theme.key_avatar_backgroundSaved));
        } else {
            checkBox.setAlpha(0.5f);
            textView.setAlpha(0.5f);

            this.setBackgroundColor(Theme.getColor(Theme.key_dialogBackgroundGray));
        }
    }

    public void setChecked(boolean checked) {
        checkBox.setChecked(checked, true);
        if (checked) {
            this.setBackgroundColor(0xff579ed8);
        } else {
            this.setBackgroundColor(0xff9da7b1);
        }
    }

    public void setIcon(int icon) {
        checkBox.setIcon(icon);
    }

    public boolean hasIcon() {
        return checkBox.hasIcon();
    }

    public boolean isChecked() {
        return checkBox.isChecked();
    }

    @Override
    protected void onDraw(Canvas canvas) {
        if (needDivider) {
            canvas.drawLine(LocaleController.isRTL ? 0 : AndroidUtilities.dp(20), getMeasuredHeight() - 1, getMeasuredWidth() - (LocaleController.isRTL ? AndroidUtilities.dp(20) : 0), getMeasuredHeight() - 1, Theme.dividerPaint);
        }
    }

    @Override
    public void onInitializeAccessibilityNodeInfo(AccessibilityNodeInfo info) {
        super.onInitializeAccessibilityNodeInfo(info);
        info.setClassName("android.widget.Switch");
        info.setCheckable(true);
        info.setChecked(checkBox.isChecked());
        info.setContentDescription(checkBox.isChecked() ? LocaleController.getString("NotificationsOn", R.string.NotificationsOn) : LocaleController.getString("NotificationsOff", R.string.NotificationsOff));
    }

}
