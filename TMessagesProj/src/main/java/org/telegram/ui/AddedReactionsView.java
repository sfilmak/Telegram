package org.telegram.ui;

import android.content.Context;
import android.graphics.PorterDuff;
import android.graphics.PorterDuffColorFilter;
import android.graphics.Rect;
import android.graphics.drawable.Drawable;
import android.text.TextUtils;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.core.content.ContextCompat;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import org.telegram.messenger.AndroidUtilities;
import org.telegram.messenger.ChatObject;
import org.telegram.messenger.ContactsController;
import org.telegram.messenger.FileLog;
import org.telegram.messenger.ImageLocation;
import org.telegram.messenger.MessageObject;
import org.telegram.messenger.MessagesController;
import org.telegram.messenger.R;
import org.telegram.tgnet.ConnectionsManager;
import org.telegram.tgnet.TLRPC;
import org.telegram.ui.ActionBar.Theme;
import org.telegram.ui.Components.AvatarDrawable;
import org.telegram.ui.Components.AvatarsImageView;
import org.telegram.ui.Components.BackupImageView;
import org.telegram.ui.Components.FlickerLoadingView;
import org.telegram.ui.Components.HideViewAfterAnimation;
import org.telegram.ui.Components.LayoutHelper;
import org.telegram.ui.Components.ReactionsCounterSpan;
import org.telegram.ui.Components.RecyclerListView;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Objects;
import java.util.stream.Collectors;

public class AddedReactionsView extends FrameLayout {

    public ArrayList<TLRPC.User> allUsersReactions = new ArrayList<>();
    public ArrayList<TLRPC.TL_messageUserReaction> allMessageUserReactions = new ArrayList<>();
    private int reactionsCount = 0;
    private final ArrayList<String> loadedOffsets = new ArrayList<>();

    public ArrayList<TLRPC.User> filteredUsersReactions = new ArrayList<>();
    public ArrayList<TLRPC.TL_messageUserReaction> filteredUserReactions = new ArrayList<>();

    AvatarsImageView avatarsImageView;
    TextView titleView;
    ImageView iconView;
    BackupImageView singleReaction;
    int currentAccount;

    FlickerLoadingView flickerLoadingView;

    private boolean isReactionsAndViewsTogether = false;

    private final int msg_id;
    private final long dialog_id;

    private boolean isLoadingMoreUsingOffset = false;
    private boolean isLoadedEverything = false;

    public AddedReactionsView(@NonNull Context context, int currentAccount, MessageObject messageObject, TLRPC.Chat chat) {
        super(context);

        this.msg_id = messageObject.getId();
        this.dialog_id = messageObject.getDialogId();
        this.currentAccount = currentAccount;

        isReactionsAndViewsTogether = true;

        flickerLoadingView = new FlickerLoadingView(context);
        flickerLoadingView.setColors(Theme.key_actionBarDefaultSubmenuBackground, Theme.key_listSelector, null);
        flickerLoadingView.setViewType(FlickerLoadingView.MESSAGE_SEEN_TYPE);
        flickerLoadingView.setIsSingleCell(false);
        addView(flickerLoadingView, LayoutHelper.createFrame(LayoutHelper.WRAP_CONTENT, LayoutHelper.MATCH_PARENT));

        titleView = new TextView(context);
        titleView.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 16);
        titleView.setLines(1);
        titleView.setEllipsize(TextUtils.TruncateAt.END);

        addView(titleView, LayoutHelper.createFrame(LayoutHelper.WRAP_CONTENT, LayoutHelper.WRAP_CONTENT, Gravity.LEFT | Gravity.CENTER_VERTICAL, 40, 0, 70, 0));

        avatarsImageView = new AvatarsImageView(context, false);
        avatarsImageView.setStyle(AvatarsImageView.STYLE_MESSAGE_SEEN);
        addView(avatarsImageView, LayoutHelper.createFrame(24 + 12 + 12 + 8, LayoutHelper.MATCH_PARENT, Gravity.RIGHT | Gravity.CENTER_VERTICAL, 0, 0, 0, 0));

        titleView.setTextColor(Theme.getColor(Theme.key_actionBarDefaultSubmenuItem));
        avatarsImageView.setAlpha(0);
        titleView.setAlpha(0);

        loadListOfAllReactions(messageObject, context);

        TLRPC.TL_messages_getMessageReadParticipants req2 = new TLRPC.TL_messages_getMessageReadParticipants();
        req2.msg_id = messageObject.getId();
        req2.peer = MessagesController.getInstance(currentAccount).getInputPeer(messageObject.getDialogId());

        long fromId = 0;
        if (messageObject.messageOwner.from_id != null) {
            fromId = messageObject.messageOwner.from_id.user_id;
        }
        long finalFromId = fromId;
        ConnectionsManager.getInstance(currentAccount).sendRequest(req2, (response, error) -> AndroidUtilities.runOnUIThread(() -> {
            FileLog.e("MessageSeenView request completed");
            if (error == null) {
                TLRPC.Vector vector = (TLRPC.Vector) response;
                ArrayList<Long> unknownUsers = new ArrayList<>();
                HashMap<Long, TLRPC.User> usersLocal = new HashMap<>();
                ArrayList<Long> allPeers = new ArrayList<>();
                for (int i = 0, n = vector.objects.size(); i < n; i++) {
                    Object object = vector.objects.get(i);
                    if (object instanceof Long) {
                        Long peerId = (Long) object;
                        if (finalFromId == peerId) {
                            continue;
                        }
                        TLRPC.User user = MessagesController.getInstance(currentAccount).getUser(peerId);
                        allPeers.add(peerId);
                        if (true || user == null) {
                            unknownUsers.add(peerId);
                        } else {
                            usersLocal.put(peerId, user);
                        }
                    }
                }

                if (unknownUsers.isEmpty()) {
                    for (int i = 0; i < allPeers.size(); i++) {
                        //peerIds.add(allPeers.get(i));
                        int finalI = i;
                        if (usersLocal.get(allPeers.get(finalI)) != null) {
                            if (allUsersReactions.stream().noneMatch(o -> o.id == Objects.requireNonNull(usersLocal.get(allPeers.get(finalI))).id)) {
                                allUsersReactions.add(usersLocal.get(allPeers.get(i)));
                                allMessageUserReactions.add(null);
                            }
                        }
                    }
                    updateView(context);
                } else {
                    if (ChatObject.isChannel(chat)) {
                        TLRPC.TL_channels_getParticipants usersReq = new TLRPC.TL_channels_getParticipants();
                        usersReq.limit = 50;
                        usersReq.offset = 0;
                        usersReq.filter = new TLRPC.TL_channelParticipantsRecent();
                        usersReq.channel = MessagesController.getInstance(currentAccount).getInputChannel(chat.id);
                        ConnectionsManager.getInstance(currentAccount).sendRequest(usersReq, (response1, error1) -> AndroidUtilities.runOnUIThread(() -> {
                            if (response1 != null) {
                                TLRPC.TL_channels_channelParticipants users = (TLRPC.TL_channels_channelParticipants) response1;
                                for (int i = 0; i < users.users.size(); i++) {
                                    TLRPC.User user = users.users.get(i);
                                    MessagesController.getInstance(currentAccount).putUser(user, false);
                                    usersLocal.put(user.id, user);
                                }
                                for (int i = 0; i < allPeers.size(); i++) {
                                    //peerIds.add(allPeers.get(i));
                                    int finalI = i;
                                    if (usersLocal.get(allPeers.get(finalI)) != null) {
                                        if (allUsersReactions.stream().noneMatch(o -> o.id == Objects.requireNonNull(usersLocal.get(allPeers.get(finalI))).id)) {
                                            this.allUsersReactions.add(usersLocal.get(allPeers.get(i)));
                                            allMessageUserReactions.add(null);
                                        }
                                    }
                                }
                            }
                            updateView(context);
                        }));
                    } else {
                        TLRPC.TL_messages_getFullChat usersReq = new TLRPC.TL_messages_getFullChat();
                        usersReq.chat_id = chat.id;
                        ConnectionsManager.getInstance(currentAccount).sendRequest(usersReq, (response1, error1) -> AndroidUtilities.runOnUIThread(() -> {
                            if (response1 != null) {
                                TLRPC.TL_messages_chatFull chatFull = (TLRPC.TL_messages_chatFull) response1;
                                for (int i = 0; i < chatFull.users.size(); i++) {
                                    TLRPC.User user = chatFull.users.get(i);
                                    MessagesController.getInstance(currentAccount).putUser(user, false);
                                    usersLocal.put(user.id, user);
                                }
                                for (int i = 0; i < allPeers.size(); i++) {
                                    int finalI = i;
                                    if (usersLocal.get(allPeers.get(finalI)) != null) {
                                        if (allUsersReactions.stream().noneMatch(o -> o.id == Objects.requireNonNull(usersLocal.get(allPeers.get(finalI))).id)) {
                                            this.allUsersReactions.add(usersLocal.get(allPeers.get(i)));
                                            allMessageUserReactions.add(null);
                                        }
                                    }
                                }
                            }
                            updateView(context);
                        }));
                    }
                }
            } else {
                updateView(context);
            }
        }));

        setBackground(Theme.createRadSelectorDrawable(Theme.getColor(Theme.key_dialogButtonSelector), AndroidUtilities.dp(4), AndroidUtilities.dp(4)));
        setEnabled(false);
    }

    public AddedReactionsView(@NonNull Context context, int currentAccount, MessageObject messageObject) {
        super(context);

        this.msg_id = messageObject.getId();
        this.dialog_id = messageObject.getDialogId();
        this.currentAccount = currentAccount;

        flickerLoadingView = new FlickerLoadingView(context);
        flickerLoadingView.setColors(Theme.key_actionBarDefaultSubmenuBackground, Theme.key_listSelector, null);
        flickerLoadingView.setViewType(FlickerLoadingView.MESSAGE_SEEN_TYPE);
        flickerLoadingView.setIsSingleCell(false);
        addView(flickerLoadingView, LayoutHelper.createFrame(LayoutHelper.WRAP_CONTENT, LayoutHelper.MATCH_PARENT));

        titleView = new TextView(context);
        titleView.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 16);
        titleView.setLines(1);
        titleView.setEllipsize(TextUtils.TruncateAt.END);

        addView(titleView, LayoutHelper.createFrame(LayoutHelper.WRAP_CONTENT, LayoutHelper.WRAP_CONTENT, Gravity.LEFT | Gravity.CENTER_VERTICAL, 40, 0, 70, 0));

        avatarsImageView = new AvatarsImageView(context, false);
        avatarsImageView.setStyle(AvatarsImageView.STYLE_MESSAGE_SEEN);
        addView(avatarsImageView, LayoutHelper.createFrame(24 + 12 + 12 + 8, LayoutHelper.MATCH_PARENT, Gravity.RIGHT | Gravity.CENTER_VERTICAL, 0, 0, 0, 0));

        titleView.setTextColor(Theme.getColor(Theme.key_actionBarDefaultSubmenuItem));

        avatarsImageView.setAlpha(0);
        titleView.setAlpha(0);

        loadListOfAllReactions(messageObject, context);

        setBackground(Theme.createRadSelectorDrawable(Theme.getColor(Theme.key_dialogButtonSelector), AndroidUtilities.dp(4), AndroidUtilities.dp(4)));
        setEnabled(false);
    }

    boolean ignoreLayout;

    @Override
    public void requestLayout() {
        if (ignoreLayout) {
            return;
        }
        super.requestLayout();
    }

    @Override
    protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
        if (flickerLoadingView.getVisibility() == View.VISIBLE) {
            ignoreLayout = true;
            flickerLoadingView.setVisibility(View.GONE);
            super.onMeasure(widthMeasureSpec, heightMeasureSpec);
            flickerLoadingView.getLayoutParams().width = getMeasuredWidth();
            flickerLoadingView.setVisibility(View.VISIBLE);
            ignoreLayout = false;
            super.onMeasure(widthMeasureSpec, heightMeasureSpec);
        } else {
            super.onMeasure(widthMeasureSpec, heightMeasureSpec);
        }
    }

    private void updateView(Context context) {
        setEnabled(allUsersReactions.size() > 0);
        for (int i = 0; i < 3; i++) {
            if (i < allUsersReactions.size()) {
                avatarsImageView.setObject(i, currentAccount, allUsersReactions.get(i));
            } else {
                avatarsImageView.setObject(i, currentAccount, null);
            }
        }
        if (allUsersReactions.size() == 1 && !isReactionsAndViewsTogether) {
            avatarsImageView.setTranslationX(AndroidUtilities.dp(24));

            ImageLocation imageLocation = ImageLocation.getForDocument(Objects.requireNonNull(ChatActivity.allAvailableReactions.stream().filter(item -> allMessageUserReactions.get(0).reaction.equals(item.reaction)).findFirst().orElse(null)).static_icon);

            singleReaction = new BackupImageView(context);
            addView(singleReaction, LayoutHelper.createFrame(24, 24, Gravity.LEFT | Gravity.CENTER_VERTICAL, 11, 0, 0, 0));
            singleReaction.setImage(imageLocation, "50_50", getResources().getDrawable(R.drawable.msg_reactions), null);

        } else if (allUsersReactions.size() == 2 && !isReactionsAndViewsTogether) {
            avatarsImageView.setTranslationX(AndroidUtilities.dp(12));

            iconView = new ImageView(context);
            addView(iconView, LayoutHelper.createFrame(24, 24, Gravity.LEFT | Gravity.CENTER_VERTICAL, 11, 0, 0, 0));
            Drawable drawable = ContextCompat.getDrawable(context, R.drawable.msg_reactions).mutate();
            drawable.setColorFilter(new PorterDuffColorFilter(Theme.getColor(Theme.key_actionBarDefaultSubmenuItemIcon), PorterDuff.Mode.MULTIPLY));
            iconView.setImageDrawable(drawable);
        } else {
            avatarsImageView.setTranslationX(0);

            iconView = new ImageView(context);
            addView(iconView, LayoutHelper.createFrame(24, 24, Gravity.LEFT | Gravity.CENTER_VERTICAL, 11, 0, 0, 0));
            Drawable drawable = ContextCompat.getDrawable(context, R.drawable.msg_reactions).mutate();
            drawable.setColorFilter(new PorterDuffColorFilter(Theme.getColor(Theme.key_actionBarDefaultSubmenuItemIcon), PorterDuff.Mode.MULTIPLY));
            iconView.setImageDrawable(drawable);
        }

        avatarsImageView.commitTransition(false);
        if (allUsersReactions.size() == 1 && allUsersReactions.get(0) != null) {
            titleView.setText(ContactsController.formatName(allUsersReactions.get(0).first_name, allUsersReactions.get(0).last_name));
        } else {
            if (isReactionsAndViewsTogether) {
                titleView.setText(reactionsCount + "/" + allUsersReactions.size() + " Reacted");
            } else {
                titleView.setText(reactionsCount + " Reactions");
            }
        }
        titleView.animate().alpha(1f).setDuration(220).start();
        avatarsImageView.animate().alpha(1f).setDuration(220).start();
        flickerLoadingView.animate().alpha(0f).setDuration(220).setListener(new HideViewAfterAnimation(flickerLoadingView)).start();
    }

    private class ReactionsCounters {
        long sum;
        TLRPC.Document static_reaction;
        boolean isSelected = false;
        String reaction;
    }

    public RecyclerListView createListOfReactionsCounters(ArrayList<TLRPC.TL_reactionCount> messageUserReactions2) {
        Collections.sort(messageUserReactions2, Comparator.<TLRPC.TL_reactionCount>comparingInt(s -> s.count).reversed());
        if (reactionsCount < 10) {
            return null;
        }
        RecyclerListView recyclerListView = new RecyclerListView(getContext());
        recyclerListView.setLayoutManager(new LinearLayoutManager(getContext(), LinearLayoutManager.HORIZONTAL, false));
        recyclerListView.setOverScrollMode(View.OVER_SCROLL_NEVER);
        recyclerListView.setItemAnimator(null);

        List<ReactionsCounters> reactionsCounters = new ArrayList<>();
        ReactionsCounters reactionsCounters_Total = new ReactionsCounters();
        reactionsCounters_Total.sum = reactionsCount;
        reactionsCounters_Total.static_reaction = null;
        reactionsCounters_Total.isSelected = true;
        reactionsCounters_Total.reaction = "ALL";
        reactionsCounters.add(reactionsCounters_Total);

        for (int i = 0; i < messageUserReactions2.size(); i++) {
            if (messageUserReactions2.get(i).count > 0) {
                ReactionsCounters reactionsCounters_Obj = new ReactionsCounters();
                int finalI = i;
                reactionsCounters_Obj.static_reaction = Objects.requireNonNull(ChatActivity.allAvailableReactions.stream().filter(item -> messageUserReactions2.get(finalI).reaction.equals(item.reaction)).findFirst().orElse(null)).static_icon;
                reactionsCounters_Obj.sum = messageUserReactions2.get(i).count;
                reactionsCounters_Obj.reaction = messageUserReactions2.get(i).reaction;
                reactionsCounters.add(reactionsCounters_Obj);
            }
        }

        RecyclerListView.SelectionAdapter adapter = new RecyclerListView.SelectionAdapter() {
            @Override
            public boolean isEnabled(RecyclerView.ViewHolder holder) {
                return true;
            }

            @Override
            public RecyclerView.ViewHolder onCreateViewHolder(ViewGroup parent, int viewType) {
                ReactionsCounterSpan userCell = new ReactionsCounterSpan(parent.getContext());
                userCell.setLayoutParams(new RecyclerView.LayoutParams(LayoutHelper.WRAP_CONTENT, LayoutHelper.WRAP_CONTENT));

                MarginLayoutParams params = (MarginLayoutParams) userCell.getLayoutParams();
                params.leftMargin = 10;
                params.topMargin = 10;
                params.bottomMargin = 10;
                params.rightMargin = 10;
                return new RecyclerListView.Holder(userCell);
            }

            @Override
            public void onBindViewHolder(RecyclerView.ViewHolder holder, int position) {
                ReactionsCounterSpan cell = (ReactionsCounterSpan) holder.itemView;
                cell.setReaction(reactionsCounters.get(position).reaction, reactionsCounters.get(position).static_reaction, reactionsCounters.get(position).sum, reactionsCounters.get(position).isSelected);
            }

            @Override
            public int getItemCount() {
                return reactionsCounters.size();
            }
        };

        recyclerListView.setAdapter(adapter);

        recyclerListView.setOnItemClickListener((view1, position) -> {
            TLRPC.TL_messageUserReaction messageUserReaction = allMessageUserReactions.get(position);
            ReactionsCounterSpan cell = (ReactionsCounterSpan) view1;
            cell.setSelected(true);

            if (messageUserReaction == null) {
                return;
            }

            ArrayList<TLRPC.TL_messageUserReaction> customersWithMoreThan100Points = allMessageUserReactions
                    .stream()
                    .filter(c -> c.reaction.equals(cell.getReaction()))
                    .collect(Collectors.toCollection(ArrayList::new));

            ArrayList<TLRPC.User> users = new ArrayList<>();

            for (TLRPC.TL_messageUserReaction mess : customersWithMoreThan100Points) {
                users.add(allUsersReactions.stream().filter(item -> item.id == mess.user_id).findFirst().orElse(null));
            }

            this.allMessageUserReactions = customersWithMoreThan100Points;
            this.allUsersReactions = users;

            adapter.notifyDataSetChanged();
        });

        return recyclerListView;
    }

    public RecyclerListView createListView() {
        RecyclerListView recyclerListView = new RecyclerListView(getContext());
        recyclerListView.setLayoutManager(new LinearLayoutManager(getContext()));
        recyclerListView.addItemDecoration(new RecyclerView.ItemDecoration() {
            @Override
            public void getItemOffsets(@NonNull Rect outRect, @NonNull View view, @NonNull RecyclerView parent, @NonNull RecyclerView.State state) {
                int p = parent.getChildAdapterPosition(view);
                if (p == 0) {
                    outRect.top = AndroidUtilities.dp(4);
                }
                if (p == allUsersReactions.size() - 1) {
                    outRect.bottom = AndroidUtilities.dp(4);
                }
            }
        });
        RecyclerListView.SelectionAdapter adapter = new RecyclerListView.SelectionAdapter() {
            @Override
            public boolean isEnabled(RecyclerView.ViewHolder holder) {
                return true;
            }

            @Override
            public RecyclerView.ViewHolder onCreateViewHolder(ViewGroup parent, int viewType) {
                UserCell userCell = new UserCell(parent.getContext());
                userCell.setLayoutParams(new RecyclerView.LayoutParams(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT));
                return new RecyclerListView.Holder(userCell);
            }

            @Override
            public void onBindViewHolder(RecyclerView.ViewHolder holder, int position) {
                UserCell cell = (UserCell) holder.itemView;
                cell.setUser(allUsersReactions.get(position), allMessageUserReactions.get(position));
            }

            @Override
            public int getItemCount() {
                return allUsersReactions.size();
            }

        };

        recyclerListView.setAdapter(adapter);

        recyclerListView.addOnScrollListener(new RecyclerView.OnScrollListener() {

            @Override
            public void onScrolled(@NonNull RecyclerView recyclerView, int dx, int dy) {
                super.onScrolled(recyclerView, dx, dy);

                if (!recyclerView.canScrollVertically(1) && !isLoadingMoreUsingOffset && !isLoadedEverything) {
                    loadListOfAllReactions(msg_id, dialog_id, getContext(), loadedOffsets.get(loadedOffsets.size() - 1), null, adapter);
                }
            }
        });

        return recyclerListView;
    }

    private static class UserCell extends FrameLayout {

        BackupImageView avatarImageView;
        BackupImageView emojiImageView;
        TextView nameView;
        AvatarDrawable avatarDrawable = new AvatarDrawable();
        AvatarDrawable emojiDrawable = new AvatarDrawable();

        public UserCell(Context context) {
            super(context);

            emojiImageView = new BackupImageView(context);
            addView(emojiImageView, LayoutHelper.createFrame(22, 22, Gravity.RIGHT | Gravity.CENTER_VERTICAL, 0, 0, 13, 0));

            avatarImageView = new BackupImageView(context);
            addView(avatarImageView, LayoutHelper.createFrame(36, 36, Gravity.CENTER_VERTICAL, 13, 0, 0, 0));
            avatarImageView.setRoundRadius(AndroidUtilities.dp(20));
            nameView = new TextView(context);
            nameView.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 16);
            nameView.setLines(1);
            nameView.setEllipsize(TextUtils.TruncateAt.END);
            addView(nameView, LayoutHelper.createFrame(LayoutHelper.WRAP_CONTENT, LayoutHelper.WRAP_CONTENT, Gravity.LEFT | Gravity.CENTER_VERTICAL, 59, 0, 55, 0));

            nameView.setTextColor(Theme.getColor(Theme.key_actionBarDefaultSubmenuItem));
        }

        @Override
        protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
            super.onMeasure(widthMeasureSpec, MeasureSpec.makeMeasureSpec(AndroidUtilities.dp(44), MeasureSpec.EXACTLY));
        }

        public void setUser(TLRPC.User user, TLRPC.TL_messageUserReaction reaction) {
            if (user != null) {
                if (reaction != null) {
                    TLRPC.TL_availableReaction availableReaction
                            = ChatActivity.allAvailableReactions.stream().filter(item -> reaction.reaction.equals(item.reaction)).findFirst().orElse(null);

                    if (availableReaction != null) {
                        emojiDrawable.setInfo(availableReaction);
                    }

                    if (availableReaction != null) {
                        ImageLocation imageLocation2 = ImageLocation.getForDocument(availableReaction.static_icon);
                        emojiImageView.setImage(imageLocation2, "50_50", null, null, null, 1);
                    }
                } else {
                    emojiImageView.setVisibility(View.GONE);
                }

                avatarDrawable.setInfo(user);

                ImageLocation imageLocation = ImageLocation.getForUser(user, ImageLocation.TYPE_SMALL);
                avatarImageView.setImage(imageLocation, "50_50", avatarDrawable, user);
                nameView.setText(ContactsController.formatName(user.first_name, user.last_name));
            }
        }
    }

    private void loadListOfAllReactions(MessageObject messageObject, Context context) {
        loadListOfAllReactions(messageObject.getId(), messageObject.getDialogId(), context, null, null, null);
    }

    private void loadListOfAllReactions(int messageID, long dialogID, Context context, String lastOffset, String selectedReaction, RecyclerListView.SelectionAdapter adapter) {
        if (adapter != null) {
            isLoadingMoreUsingOffset = true;
        }

        TLRPC.TL_messages_getMessageReactionsList req = new TLRPC.TL_messages_getMessageReactionsList();
        req.id = messageID;
        req.peer = MessagesController.getInstance(currentAccount).getInputPeer(dialogID);
        req.limit = 100;
        if (selectedReaction != null) {
            req.flags |= 1;
            req.reaction = selectedReaction;
        }
        if (lastOffset != null) {
            req.flags |= 2;
            req.offset = lastOffset;
        }

        ConnectionsManager.getInstance(currentAccount).sendRequest(req, (response, error) -> AndroidUtilities.runOnUIThread(() -> {
            FileLog.e("Loading of reactions request completed");
            if (error == null) {
                TLRPC.TL_messages_messageReactionsList messageReactionsList = (TLRPC.TL_messages_messageReactionsList) response;

                if (!loadedOffsets.contains(messageReactionsList.next_offset)) {
                    this.allUsersReactions.addAll(messageReactionsList.users);
                    this.allMessageUserReactions.addAll(messageReactionsList.reactions);
                    this.reactionsCount = messageReactionsList.count;

                    if (allMessageUserReactions.size() == reactionsCount) {
                        isLoadedEverything = true;
                    } else if ((messageReactionsList.flags & 1) != 0
                            && messageReactionsList.next_offset != null) {
                        loadedOffsets.add(messageReactionsList.next_offset);
                    } else {
                        isLoadedEverything = true;
                    }

                    updateView(context);
                    if (adapter != null) {
                        adapter.notifyDataSetChanged();
                    }
                }
                isLoadingMoreUsingOffset = false;
            } else {

                updateView(context);
            }
        }));
    }

    private void loadSelectedReactions() {

    }
}
