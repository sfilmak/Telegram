/*
 * This is the source code of Telegram for Android v. 5.x.x.
 * It is licensed under GNU GPL v. 2 or later.
 * You should have received a copy of the license in this archive (see LICENSE).
 *
 * Copyright Nikolai Kudashov, 2013-2018.
 */

package org.telegram.ui.Components;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.PorterDuff;
import android.graphics.PorterDuffColorFilter;
import android.graphics.RectF;
import android.graphics.Typeface;
import android.graphics.drawable.Drawable;
import android.text.Layout;
import android.text.StaticLayout;
import android.text.TextPaint;
import android.text.TextUtils;
import android.view.View;
import android.view.accessibility.AccessibilityNodeInfo;

import org.telegram.messenger.AndroidUtilities;
import org.telegram.messenger.ImageLocation;
import org.telegram.messenger.ImageReceiver;
import org.telegram.messenger.R;
import org.telegram.tgnet.TLRPC;
import org.telegram.ui.ActionBar.Theme;

public class ReactionsCounterSpan extends View {

    private static TextPaint textPaint = new TextPaint(Paint.ANTI_ALIAS_FLAG);
    private static Paint backPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private RectF rect = new RectF();
    private ImageReceiver imageReceiver;
    private StaticLayout nameLayout;
    private int textWidth;
    private float textX;
    private ImageLocation imageLocation;
    private Drawable totalReactionsDrawable;
    private boolean isSelected = false;
    private String reaction = "";
    private long number_of_reactions = 0;

    public void setSelected(boolean isSelected) {
        this.isSelected = isSelected;
    }

    public long getNumberOfReactions() {
        return number_of_reactions;
    }

    public String getReaction() {
        return reaction;
    }

    public void setReaction(String reaction, TLRPC.Document static_icon, long number_of_reactions, boolean isSelected) {
        this.isSelected = isSelected;
        this.reaction = reaction;
        this.number_of_reactions = number_of_reactions;
        if (static_icon != null) {
            imageLocation = ImageLocation.getForDocument(static_icon);
            imageReceiver.setImage(imageLocation, "50_50", null, null, null, 1);
        } else {
            totalReactionsDrawable = getResources().getDrawable(R.drawable.msg_reactions_filled);
            totalReactionsDrawable.setColorFilter(new PorterDuffColorFilter(Theme.getColor(Theme.key_avatar_backgroundSaved), PorterDuff.Mode.MULTIPLY));
        }

        int maxNameWidth;
        if (AndroidUtilities.isTablet()) {
            maxNameWidth = AndroidUtilities.dp(530 - 32 - 18 - 57 * 2) / 2;
        } else {
            maxNameWidth = (Math.min(AndroidUtilities.displaySize.x, AndroidUtilities.displaySize.y) - AndroidUtilities.dp(32 + 18 + 57 * 2)) / 2;
        }

        CharSequence name = TextUtils.ellipsize(String.valueOf(number_of_reactions), textPaint, maxNameWidth, TextUtils.TruncateAt.END);
        nameLayout = new StaticLayout(name, textPaint, 700, Layout.Alignment.ALIGN_NORMAL, 0.5f, 0.0f, false);
        if (nameLayout.getLineCount() > 0) {
            textWidth = (int) Math.ceil(nameLayout.getLineWidth(0));
            textX = -nameLayout.getLineLeft(0);
        }
    }

    public ReactionsCounterSpan(Context context) {
        super(context);

        textPaint.setTextSize(AndroidUtilities.dp(16));
        textPaint.setColor(Theme.getColor(Theme.key_avatar_backgroundSaved));
        textPaint.setTypeface(Typeface.create(Typeface.DEFAULT, Typeface.BOLD));

        imageReceiver = new ImageReceiver();
        imageReceiver.setParentView(this);
        imageReceiver.setImageCoords(30, 10, AndroidUtilities.dp(24), AndroidUtilities.dp(24));

        backPaint.setColor(Theme.getColor(Theme.key_avatar_backgroundReactionsBackground));
    }

    @Override
    protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
        setMeasuredDimension(AndroidUtilities.dp(32 + 25) + textWidth, AndroidUtilities.dp(32));
    }

    @Override
    protected void onDraw(Canvas canvas) {
        invalidate();
        canvas.save();
        rect.set(0, 0, getMeasuredWidth(), AndroidUtilities.dp(32));
        canvas.drawRoundRect(rect, AndroidUtilities.dp(16), AndroidUtilities.dp(16), backPaint);
        if (imageLocation != null && totalReactionsDrawable == null) {
            imageReceiver.draw(canvas);
        }

        if (isSelected) {
            rect.set(5, 5, getMeasuredWidth() - 5, AndroidUtilities.dp(30));
            canvas.drawRoundRect(rect, AndroidUtilities.dp(20), AndroidUtilities.dp(20), Theme.chat_selectedReactionRectPaint2);
            canvas.save();
            canvas.rotate(45 * (1.0f), AndroidUtilities.dp(20), AndroidUtilities.dp(20));
            canvas.restore();
            rect.set(0, 0, getMeasuredWidth(), AndroidUtilities.dp(32));
        }

        if (totalReactionsDrawable != null) {
            totalReactionsDrawable.setBounds(AndroidUtilities.dp(10), AndroidUtilities.dp(3), AndroidUtilities.dp(35), AndroidUtilities.dp(30));
            totalReactionsDrawable.draw(canvas);
        }

        canvas.translate(textX + AndroidUtilities.dp(32 + 9), AndroidUtilities.dp(7f));

        nameLayout.draw(canvas);
        canvas.restore();
    }

    @Override
    public void onInitializeAccessibilityNodeInfo(AccessibilityNodeInfo info) {
        super.onInitializeAccessibilityNodeInfo(info);
        info.setText(nameLayout.getText());
    }
}
