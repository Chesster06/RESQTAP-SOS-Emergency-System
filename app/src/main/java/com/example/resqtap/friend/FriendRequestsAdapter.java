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
}
