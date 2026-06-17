package com.example.resqtap.friend;

import android.content.Context;
import android.content.Intent;
import android.net.Uri;
import android.os.Handler;
import android.os.Looper;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.EditText;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.ProgressBar;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import com.example.resqtap.R;
import com.example.resqtap.room.FirebaseRoomClient;
import com.example.resqtap.utils.AvatarUtils;
import com.google.android.material.button.MaterialButton;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;
import com.google.android.material.imageview.ShapeableImageView;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/**
 * FriendsAdapter
 * Adapter RecyclerView untuk senarai rakan dengan sokongan kad boleh kembang (expandable)
 * bagi memaparkan maklumat terperinci seperti jenis darah, alahan, dan nombor telefon.
 */
public class FriendsAdapter extends RecyclerView.Adapter<FriendsAdapter.ViewHolder> {

    public interface OnFriendActionListener {
        void onRemoveFriend(FirebaseFriendClient.FriendInfo friend);
    }

    private static final ConcurrentHashMap<String, String> photoB64Cache = new ConcurrentHashMap<>();
    private static final ConcurrentHashMap<String, String> photoUrlCache = new ConcurrentHashMap<>();
    private static final Set<String> lookupInFlight = ConcurrentHashMap.newKeySet();

    private static final ConcurrentHashMap<String, FirebaseFriendClient.UserDetail> detailCache = new ConcurrentHashMap<>();
    private static final Set<String> detailLookupInFlight = ConcurrentHashMap.newKeySet();

    private static final Handler mainHandler = new Handler(Looper.getMainLooper());

    private final List<FirebaseFriendClient.FriendInfo> allFriends = new ArrayList<>();
    private final List<FirebaseFriendClient.FriendInfo> displayFriends = new ArrayList<>();
    private final Set<String> expandedUids = new HashSet<>();
    private final OnFriendActionListener listener;
    private String currentUid = "";
    private String currentQuery = "";

    public FriendsAdapter(OnFriendActionListener listener) {
        this.listener = listener;
    }

    public void setCurrentUid(String uid) {
        this.currentUid = uid == null ? "" : uid.trim();
    }

    public void setFriends(List<FirebaseFriendClient.FriendInfo> newFriends) {
        allFriends.clear();
        if (newFriends != null) allFriends.addAll(newFriends);
        applyFilter(currentQuery);
    }

    public void filter(String query) {
        currentQuery = query == null ? "" : query.trim().toLowerCase();
        applyFilter(currentQuery);
    }

    private void applyFilter(String query) {
        displayFriends.clear();
        if (query.isEmpty()) {
            displayFriends.addAll(allFriends);
        } else {
            for (FirebaseFriendClient.FriendInfo friend : allFriends) {
                String name = friend.name != null ? friend.name.toLowerCase() : "";
                String nick = friend.nickname != null ? friend.nickname.toLowerCase() : "";
                String pubId = friend.publicId != null ? friend.publicId.toLowerCase() : "";
                if (name.contains(query) || nick.contains(query) || pubId.contains(query)) {
                    displayFriends.add(friend);
                }
            }
        }
        notifyDataSetChanged();
    }

    @NonNull
    @Override
    public ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View v = LayoutInflater.from(parent.getContext()).inflate(R.layout.item_friend, parent, false);
        return new ViewHolder(v);
    }

    @Override
    public void onBindViewHolder(@NonNull ViewHolder holder, int position) {
        FirebaseFriendClient.FriendInfo friend = displayFriends.get(position);
        Context ctx = holder.itemView.getContext();

        String realName = friend.name.isEmpty() ? ctx.getString(R.string.friend_default_name) : friend.name;
        boolean hasNickname = friend.nickname != null && !friend.nickname.trim().isEmpty();

        if (hasNickname) {
            holder.name.setText(friend.nickname.trim());
            if (friend.publicId.isEmpty()) {
                holder.publicId.setText(realName);
            } else {
                holder.publicId.setText(ctx.getString(R.string.friend_nickname_real_name_format, realName, friend.publicId));
            }
        } else {
            holder.name.setText(realName);
            if (friend.publicId.isEmpty()) {
                holder.publicId.setText(ctx.getString(R.string.friend_resqtap_default));
            } else {
                holder.publicId.setText(ctx.getString(R.string.friend_tag_format, friend.publicId));
            }
        }

        // Edit nama panggilan / nickname peribadi
        if (holder.btnEditNickname != null) {
            holder.btnEditNickname.setOnClickListener(v -> {
                int currentPos = holder.getAdapterPosition();
                if (currentPos != RecyclerView.NO_POSITION) {
                    showEditNicknameDialog(ctx, friend, currentPos);
                }
            });
        }

        String b64 = friend.photoB64 != null ? friend.photoB64.trim() : "";
        String url = friend.photoUrl != null ? friend.photoUrl.trim() : "";

        if (b64.isEmpty() && !friend.uid.isEmpty()) {
            String cached = photoB64Cache.get(friend.uid);
            if (cached != null && !cached.isEmpty()) {
                b64 = cached;
                friend.photoB64 = cached;
            }
        }
        if (url.isEmpty() && !friend.uid.isEmpty()) {
            String cached = photoUrlCache.get(friend.uid);
            if (cached != null && !cached.isEmpty()) {
                url = cached;
                friend.photoUrl = cached;
            }
        }

        AvatarUtils.applyAvatar(holder.avatar, b64, url, R.drawable.ic_avatar);

        if (b64.isEmpty() && url.isEmpty() && !friend.uid.isEmpty()) {
            maybeLookupFriendPhoto(friend);
        }

        // Padam rakan (hanya triggered apabila butang padam ditekan)
        holder.btnRemove.setOnClickListener(v -> {
            if (listener != null) listener.onRemoveFriend(friend);
        });

        // Pengendalian kembang/tutup kad (Expand / Collapse)
        boolean isExpanded = expandedUids.contains(friend.uid);
        if (isExpanded) {
            holder.layoutDetails.setVisibility(View.VISIBLE);
            holder.ivChevron.setRotation(180f);
            bindFriendDetails(holder, friend, ctx);
        } else {
            holder.layoutDetails.setVisibility(View.GONE);
            holder.ivChevron.setRotation(0f);
        }

        View.OnClickListener toggleExpand = v -> {
            int currentPos = holder.getAdapterPosition();
            if (currentPos == RecyclerView.NO_POSITION) return;

            if (expandedUids.contains(friend.uid)) {
                expandedUids.remove(friend.uid);
            } else {
                expandedUids.add(friend.uid);
            }
            notifyItemChanged(currentPos);
        };

        holder.itemView.setOnClickListener(toggleExpand);
        if (holder.headerLayout != null) {
            holder.headerLayout.setOnClickListener(toggleExpand);
        }
    }

    private void bindFriendDetails(@NonNull ViewHolder holder, FirebaseFriendClient.FriendInfo friend, Context ctx) {
        if (friend == null || friend.uid == null || friend.uid.isEmpty()) {
            holder.progressDetails.setVisibility(View.GONE);
            holder.layoutContent.setVisibility(View.VISIBLE);
            return;
        }

        FirebaseFriendClient.UserDetail cached = detailCache.get(friend.uid);
        if (cached != null) {
            holder.progressDetails.setVisibility(View.GONE);
            holder.layoutContent.setVisibility(View.VISIBLE);
            populateDetails(holder, cached, ctx);
        } else {
            holder.progressDetails.setVisibility(View.VISIBLE);
            holder.layoutContent.setVisibility(View.GONE);
            loadFriendDetails(friend.uid, holder.getAdapterPosition());
        }
    }
}
