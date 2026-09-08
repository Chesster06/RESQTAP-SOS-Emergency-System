package com.example.resqtap.friend;

import android.os.Handler;
import android.os.Looper;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import com.example.resqtap.R;
import com.example.resqtap.room.FirebaseRoomClient;
import com.example.resqtap.utils.AvatarUtils;
import com.google.android.material.imageview.ShapeableImageView;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/**
 * FriendRequestsAdapter
 * Adapter untuk card friend request dengan sokongan paparan avatar pemohon.
 */
public class FriendRequestsAdapter extends RecyclerView.Adapter<FriendRequestsAdapter.ViewHolder> {

    public interface OnRequestActionListener {
        void onAccept(FirebaseFriendClient.FriendRequest request);
        void onReject(FirebaseFriendClient.FriendRequest request);
    }

    private static final ConcurrentHashMap<String, String> photoB64Cache = new ConcurrentHashMap<>();
    private static final ConcurrentHashMap<String, String> photoUrlCache = new ConcurrentHashMap<>();
    private static final Set<String> lookupInFlight = ConcurrentHashMap.newKeySet();
    private static final Handler mainHandler = new Handler(Looper.getMainLooper());

    private final List<FirebaseFriendClient.FriendRequest> requests = new ArrayList<>();
    private final OnRequestActionListener listener;

    public FriendRequestsAdapter(OnRequestActionListener listener) {
        this.listener = listener;
    }

    public void setRequests(List<FirebaseFriendClient.FriendRequest> newRequests) {
        requests.clear();
        if (newRequests != null) requests.addAll(newRequests);
        notifyDataSetChanged();
    }

    @NonNull
    @Override
    public ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View v = LayoutInflater.from(parent.getContext()).inflate(R.layout.item_friend_request, parent, false);
        return new ViewHolder(v);
    }

    @Override
    public void onBindViewHolder(@NonNull ViewHolder holder, int position) {
        FirebaseFriendClient.FriendRequest request = requests.get(position);
        android.content.Context ctx = holder.itemView.getContext();
        holder.name.setText(request.fromName.isEmpty() ? ctx.getString(R.string.friend_user_default) : request.fromName);
        holder.subtext.setText(request.fromPublicId.isEmpty()
                ? ctx.getString(R.string.friend_incoming_request_subtext)
                : ctx.getString(R.string.friend_incoming_request_user_subtext, request.fromPublicId));

        String b64 = request.fromPhotoB64 != null ? request.fromPhotoB64.trim() : "";
        String url = request.fromPhotoUrl != null ? request.fromPhotoUrl.trim() : "";

        if (b64.isEmpty() && !request.fromUid.isEmpty()) {
            String cached = photoB64Cache.get(request.fromUid);
            if (cached != null && !cached.isEmpty()) {
                b64 = cached;
                request.fromPhotoB64 = cached;
            }
        }
        if (url.isEmpty() && !request.fromUid.isEmpty()) {
            String cached = photoUrlCache.get(request.fromUid);
            if (cached != null && !cached.isEmpty()) {
                url = cached;
                request.fromPhotoUrl = cached;
            }
        }

        AvatarUtils.applyAvatar(holder.avatar, b64, url, R.drawable.ic_avatar);

        if (b64.isEmpty() && url.isEmpty() && !request.fromUid.isEmpty()) {
            maybeLookupSenderPhoto(request);
        }

        holder.btnAccept.setOnClickListener(v -> {
            if (listener != null) listener.onAccept(request);
        });
        holder.btnReject.setOnClickListener(v -> {
            if (listener != null) listener.onReject(request);
        });
    }

    private void maybeLookupSenderPhoto(FirebaseFriendClient.FriendRequest req) {
        if (req == null || req.fromUid == null || req.fromUid.isEmpty()) return;
        final String fromUid = req.fromUid;
        if (!lookupInFlight.add(fromUid)) return;

        FirebaseRoomClient.fetchUserPhotoB64Queued(fromUid, (id, fetchedB64) -> {
            String cleanB64 = fetchedB64 == null ? "" : fetchedB64.trim();
            if (!cleanB64.isEmpty()) {
                lookupInFlight.remove(fromUid);
                photoB64Cache.put(fromUid, cleanB64);
                req.fromPhotoB64 = cleanB64;
                mainHandler.post(() -> {
                    int pos = requests.indexOf(req);
                    if (pos != -1) notifyItemChanged(pos);
                });
                return;
            }

            FirebaseRoomClient.fetchUserPhotoUrlQueued(fromUid, (id2, fetchedUrl) -> {
                lookupInFlight.remove(fromUid);
                String cleanUrl = fetchedUrl == null ? "" : fetchedUrl.trim();
                if (!cleanUrl.isEmpty()) {
                    photoUrlCache.put(fromUid, cleanUrl);
                    req.fromPhotoUrl = cleanUrl;
                    mainHandler.post(() -> {
                        int pos = requests.indexOf(req);
                        if (pos != -1) notifyItemChanged(pos);
                    });
                }
            });
        });
    }

    @Override
    public int getItemCount() {
        return requests.size();
    }

    static class ViewHolder extends RecyclerView.ViewHolder {
        final ShapeableImageView avatar;
        final TextView name;
        final TextView subtext;
        final Button btnAccept;
        final Button btnReject;

        ViewHolder(@NonNull View itemView) {
            super(itemView);
            avatar = itemView.findViewById(R.id.req_avatar);
            name = itemView.findViewById(R.id.req_name);
            subtext = itemView.findViewById(R.id.req_subtext);
            btnAccept = itemView.findViewById(R.id.btn_accept_request);
            btnReject = itemView.findViewById(R.id.btn_reject_request);
        }
    }
}
