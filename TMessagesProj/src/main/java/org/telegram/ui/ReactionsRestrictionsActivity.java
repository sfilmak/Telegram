package org.telegram.ui;

import android.content.Context;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.LinearLayout;
import android.widget.ScrollView;

import org.telegram.messenger.AccountInstance;
import org.telegram.messenger.AndroidUtilities;
import org.telegram.messenger.R;
import org.telegram.tgnet.TLRPC;
import org.telegram.ui.ActionBar.ActionBar;
import org.telegram.ui.ActionBar.BaseFragment;
import org.telegram.ui.ActionBar.Theme;
import org.telegram.ui.Cells.HeaderCell;
import org.telegram.ui.Cells.ReactionsEnabledCell;
import org.telegram.ui.Cells.TextCheckCellReaction;
import org.telegram.ui.Cells.TextInfoPrivacyCell;
import org.telegram.ui.Components.LayoutHelper;
import org.telegram.ui.Components.SizeNotifierFrameLayout;

import java.util.ArrayList;
import java.util.stream.Collectors;

public class ReactionsRestrictionsActivity extends BaseFragment {

    private TLRPC.ChatFull info;
    private boolean enableReaction = true;
    private TLRPC.Chat currentChat;
    private ArrayList<String> allowedReactions_Settings = new ArrayList<>();
    private ArrayList<String> allPossibleReactions = new ArrayList<>();
    private ArrayList<TextCheckCellReaction> cellsWithReactions = new ArrayList<>();

    public ReactionsRestrictionsActivity(TLRPC.Chat currentChat) {
        this.currentChat = currentChat;

        if(ChatActivity.allAvailableReactions != null) {
            allPossibleReactions = ChatActivity.allAvailableReactions.stream()
                    .map(i -> i.reaction)
                    .collect(Collectors.toCollection(ArrayList::new));
        }
    }

    @Override
    public View createView(Context context) {

        actionBar.setBackButtonImage(R.drawable.ic_ab_back);
        actionBar.setAllowOverlayTitle(true);

        actionBar.setActionBarMenuOnItemClick(new ActionBar.ActionBarMenuOnItemClick() {
            @Override
            public void onItemClick(int id) {
                if (id == -1) {
                    finishFragment();
                }
            }
        });

        SizeNotifierFrameLayout sizeNotifierFrameLayout = new SizeNotifierFrameLayout(context) {

            private boolean ignoreLayout;

            @Override
            protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
                int widthSize = MeasureSpec.getSize(widthMeasureSpec);
                int heightSize = MeasureSpec.getSize(heightMeasureSpec);

                setMeasuredDimension(widthSize, heightSize);

                measureChildWithMargins(actionBar, widthMeasureSpec, 0, heightMeasureSpec, 0);

                int keyboardSize = measureKeyboardHeight();
                if (keyboardSize > AndroidUtilities.dp(20)) {
                    ignoreLayout = true;
                    ignoreLayout = false;
                }

                int childCount = getChildCount();
                for (int i = 0; i < childCount; i++) {
                    View child = getChildAt(i);
                    if (child == null || child.getVisibility() == GONE || child == actionBar) {
                        continue;
                    }
                    measureChildWithMargins(child, widthMeasureSpec, 0, heightMeasureSpec, 0);
                }
            }

            @Override
            protected void onLayout(boolean changed, int l, int t, int r, int b) {
                final int count = getChildCount();

                int paddingBottom = 0;
                setBottomClip(paddingBottom);

                for (int i = 0; i < count; i++) {
                    final View child = getChildAt(i);
                    if (child.getVisibility() == GONE) {
                        continue;
                    }
                    final LayoutParams lp = (LayoutParams) child.getLayoutParams();

                    final int width = child.getMeasuredWidth();
                    final int height = child.getMeasuredHeight();

                    int childLeft;
                    int childTop;

                    int gravity = lp.gravity;
                    if (gravity == -1) {
                        gravity = Gravity.TOP | Gravity.LEFT;
                    }

                    final int absoluteGravity = gravity & Gravity.HORIZONTAL_GRAVITY_MASK;
                    final int verticalGravity = gravity & Gravity.VERTICAL_GRAVITY_MASK;

                    switch (absoluteGravity & Gravity.HORIZONTAL_GRAVITY_MASK) {
                        case Gravity.CENTER_HORIZONTAL:
                            childLeft = (r - l - width) / 2 + lp.leftMargin - lp.rightMargin;
                            break;
                        case Gravity.RIGHT:
                            childLeft = r - width - lp.rightMargin;
                            break;
                        case Gravity.LEFT:
                        default:
                            childLeft = lp.leftMargin;
                    }

                    switch (verticalGravity) {
                        case Gravity.TOP:
                            childTop = lp.topMargin + getPaddingTop();
                            break;
                        case Gravity.CENTER_VERTICAL:
                            childTop = ((b - paddingBottom) - t - height) / 2 + lp.topMargin - lp.bottomMargin;
                            break;
                        case Gravity.BOTTOM:
                            childTop = ((b - paddingBottom) - t) - height - lp.bottomMargin;
                            break;
                        default:
                            childTop = lp.topMargin;
                    }
                    child.layout(childLeft, childTop, childLeft + width, childTop + height);
                }

                notifyHeightChanged();
            }

            @Override
            public void requestLayout() {
                if (ignoreLayout) {
                    return;
                }
                super.requestLayout();
            }
        };

        sizeNotifierFrameLayout.setOnTouchListener((v, event) -> true);
        fragmentView = sizeNotifierFrameLayout;
        fragmentView.setBackgroundColor(Theme.getColor(Theme.key_windowBackgroundGray));

        ScrollView scrollView = new ScrollView(context);
        scrollView.setFillViewport(true);
        sizeNotifierFrameLayout.addView(scrollView, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, LayoutHelper.MATCH_PARENT));

        LinearLayout linearLayout1 = new LinearLayout(context);
        scrollView.addView(linearLayout1, new ScrollView.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));

        linearLayout1.setOrientation(LinearLayout.VERTICAL);

        actionBar.setTitle("Reactions");

        LinearLayout linearLayout2 = new LinearLayout(context);

        ReactionsEnabledCell enabledCell = new ReactionsEnabledCell(context);
        enabledCell.setTextAndCheck("Enable reactions", enableReaction, false);
        enabledCell.setBackgroundColor(Theme.getColor(Theme.key_avatar_backgroundSaved));
        linearLayout1.addView(enabledCell);
        enabledCell.setOnClickListener(v -> {
            enableReaction = !enableReaction;
            ((ReactionsEnabledCell) v).setChecked(enableReaction);

            if(enableReaction) {
                linearLayout2.setVisibility(View.VISIBLE);

                //TODO
                AccountInstance.getInstance(currentAccount).getMessagesController().setChatAvailableReactions(currentChat, allPossibleReactions);
                cellsWithReactions.stream().forEach(i -> i.setChecked(true));
            } else {
                linearLayout2.setVisibility(View.GONE);
                AccountInstance.getInstance(currentAccount).getMessagesController().setChatAvailableReactions(currentChat, new ArrayList<>());
            }
        });

        TextInfoPrivacyCell privacyCell = new TextInfoPrivacyCell(context);
        privacyCell.setText("Allow subscribers to react to channel posts.");
        linearLayout1.addView(privacyCell);

        linearLayout1.addView(linearLayout2, new ScrollView.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));

        linearLayout2.setOrientation(LinearLayout.VERTICAL);

        if(info != null) {
            enableReaction = !info.available_reactions.isEmpty();

            if(enableReaction) {
                linearLayout2.setVisibility(View.VISIBLE);
            } else {
                linearLayout2.setVisibility(View.GONE);
            }

            enabledCell.setChecked(enableReaction);
        }

        HeaderCell headerCell = new HeaderCell(context);
        headerCell.setText("Available reactions");
        headerCell.setBackgroundColor(Theme.getColor(Theme.key_windowBackgroundWhite));
        headerCell.setHeight(43);
        linearLayout2.addView(headerCell);

        if(info != null) {
            allowedReactions_Settings = info.available_reactions;
        }

        if(ChatActivity.allAvailableReactions != null && info != null) {
            for(TLRPC.TL_availableReaction availableReaction: ChatActivity.allAvailableReactions) {

                TextCheckCellReaction checkCell = new TextCheckCellReaction(context);
                checkCell.setTextAndCheck(availableReaction.title, availableReaction.reaction, info.available_reactions.stream().anyMatch(ti -> ti.equals(availableReaction.reaction)), true, availableReaction.static_icon);
                checkCell.setIcon(0);
                checkCell.setBackgroundColor(Theme.getColor(Theme.key_windowBackgroundWhite));
                checkCell.setOnClickListener(v -> {
                    checkCell.setChecked();
                    if(checkCell.isChecked()) {
                        allowedReactions_Settings.add(((TextCheckCellReaction) v).getReaction());
                    } else {
                        allowedReactions_Settings.remove(((TextCheckCellReaction) v).getReaction());
                    }

                    AccountInstance.getInstance(currentAccount).getMessagesController().setChatAvailableReactions(currentChat, allowedReactions_Settings);
                });

                linearLayout2.addView(checkCell);

                cellsWithReactions.add(checkCell);
            }
        }

        return fragmentView;
    }

    @Override
    public boolean onFragmentCreate() {
        return super.onFragmentCreate();
    }

    public void setInfo(TLRPC.ChatFull chatFull) {
        info = chatFull;
    }
}