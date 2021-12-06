package org.telegram.ui;

import android.content.Context;
import android.os.Handler;
import android.view.ViewGroup;
import android.widget.FrameLayout;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import org.telegram.messenger.AndroidUtilities;
import org.telegram.messenger.ImageLocation;
import org.telegram.tgnet.TLRPC;
import org.telegram.ui.ActionBar.Theme;
import org.telegram.ui.Components.BackupImageView;
import org.telegram.ui.Components.RLottieDrawable;
import org.telegram.ui.Components.RecyclerListView;

import java.util.List;
import java.util.Random;

public class SelectReactionView extends FrameLayout {

    public List<TLRPC.TL_availableReaction> listOfAvailableReactions;

    public SelectReactionView(@NonNull Context context, List<TLRPC.TL_availableReaction> listOfAvailableReactions) {
        super(context);
        this.listOfAvailableReactions = listOfAvailableReactions;

        setBackground(Theme.createRadSelectorDrawable(Theme.getColor(Theme.key_dialogButtonSelector), AndroidUtilities.dp(4), AndroidUtilities.dp(4)));
        setEnabled(false);
    }

    final Handler handler = new Handler();
    final int delay = 500;

    public RecyclerListView createListView() {
        RecyclerListView recyclerListView = new RecyclerListView(getContext());
        recyclerListView.setLayoutManager(new LinearLayoutManager(getContext(), LinearLayoutManager.HORIZONTAL, false));
        recyclerListView.setAdapter(new RecyclerListView.SelectionAdapter() {
            @Override
            public boolean isEnabled(RecyclerView.ViewHolder holder) {
                return true;
            }

            @Override
            public RecyclerView.ViewHolder onCreateViewHolder(ViewGroup parent, int viewType) {
                BackupImageView imageView = new BackupImageView(getContext());
                imageView.setSize(AndroidUtilities.dp(30), AndroidUtilities.dp(30));
                imageView.setLayerNum(1);
                imageView.setAspectFit(true);
                imageView.setLayoutParams(new RecyclerView.LayoutParams(AndroidUtilities.dp(52), AndroidUtilities.dp(52)));
                return new RecyclerListView.Holder(imageView);
            }

            @Override
            public void onBindViewHolder(RecyclerView.ViewHolder holder, int position) {
                BackupImageView imageView = (BackupImageView) holder.itemView;
                TLRPC.TL_availableReaction set = listOfAvailableReactions.get(position);

                TLRPC.Document document = set.select_animation;
                ImageLocation imageLocation =
                        ImageLocation.getForDocument(document);

                ImageLocation imageLocationThumb =
                        ImageLocation.getForDocument(set.static_icon);
                if (imageLocation == null) {
                    return;
                }

                imageView.getImageReceiver().setAutoRepeat(2);
                imageView.setImage(imageLocation, "30_30", imageLocationThumb, "30_30", 0, this);

                handler.postDelayed(new Runnable() {
                    public void run() {
                        Random rand = new Random();
                        int val = rand.nextInt(4) + 1;
                        if (val == 1) {
                            RLottieDrawable drawable = imageView.getImageReceiver().getLottieAnimation();
                            if(drawable != null) {
                                drawable.restart();
                            }
                        }
                        handler.postDelayed(this, delay);
                    }
                }, delay);
            }

            @Override
            public int getItemViewType(int position) {
                return 0;
            }

            @Override
            public int getItemCount() {
                return listOfAvailableReactions.size();
            }
        });

        return recyclerListView;
    }
}
