package com.example.resqtap.room;

import android.content.Context;
import android.text.format.DateFormat;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.LinearLayout;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import com.example.resqtap.R;
import com.example.resqtap.utils.AvatarUtils;
import com.google.android.material.imageview.ShapeableImageView;

import java.util.ArrayList;
import java.util.Calendar;
import java.util.Date;
import java.util.List;

public class RoomChatAdapter extends RecyclerView.Adapter<RoomChatAdapter.MessageViewHolder> {

    private final Context context;
    private final String currentUid;
    private final List<FirebaseRoomClient.RoomMessage> messages = new ArrayList<>();

    public RoomChatAdapter(Context context, String currentUid) {
        this.context = context;
        this.currentUid = currentUid != null ? currentUid : "";
    }

    public void setMessages(List<FirebaseRoomClient.RoomMessage> newMessages) {
        this.messages.clear();
        if (newMessages != null) {
            this.messages.addAll(newMessages);
        }
        notifyDataSetChanged();
    }

    @NonNull
    @Override
    public MessageViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(parent.getContext()).inflate(R.layout.item_room_chat_message, parent, false);
        return new MessageViewHolder(view);
    }

    @Override
    public void onBindViewHolder(@NonNull MessageViewHolder holder, int position) {
        FirebaseRoomClient.RoomMessage msg = messages.get(position);
        boolean isMe = currentUid.equals(msg.senderUid);

        // Date separator logic
        boolean showDate = false;
        if (position == 0) {
            showDate = true;
        } else {
            FirebaseRoomClient.RoomMessage prev = messages.get(position - 1);
            if (!isSameDay(prev.timestamp, msg.timestamp)) {
                showDate = true;
            }
        }

        if (showDate && msg.timestamp > 0) {
            holder.tvDateSeparator.setVisibility(View.VISIBLE);
            holder.tvDateSeparator.setText(formatDateSeparator(msg.timestamp));
        } else {
            holder.tvDateSeparator.setVisibility(View.GONE);
        }

        String timeStr = formatTime(msg.timestamp);

        if (isMe) {
            holder.layoutMe.setVisibility(View.VISIBLE);
            holder.layoutOther.setVisibility(View.GONE);

            holder.tvTextMe.setText(msg.text);
            holder.tvTimeMe.setText(timeStr);
        } else {
            holder.layoutMe.setVisibility(View.GONE);
            holder.layoutOther.setVisibility(View.VISIBLE);

            holder.tvNameOther.setText(msg.senderName != null && !msg.senderName.isEmpty() ? msg.senderName : "User");
            holder.tvTextOther.setText(msg.text);
            holder.tvTimeOther.setText(timeStr);

            AvatarUtils.applyAvatar(holder.ivAvatarOther, "", msg.senderPhotoUrl, R.drawable.ic_avatar);
        }
    }

    @Override
    public int getItemCount() {
        return messages.size();
    }

    private boolean isSameDay(long ts1, long ts2) {
        if (ts1 <= 0 || ts2 <= 0) return true;
        Calendar c1 = Calendar.getInstance();
        c1.setTimeInMillis(ts1);
        Calendar c2 = Calendar.getInstance();
        c2.setTimeInMillis(ts2);
        return c1.get(Calendar.YEAR) == c2.get(Calendar.YEAR)
                && c1.get(Calendar.DAY_OF_YEAR) == c2.get(Calendar.DAY_OF_YEAR);
    }

    private String formatDateSeparator(long timestamp) {
        Calendar today = Calendar.getInstance();
        Calendar msgDate = Calendar.getInstance();
        msgDate.setTimeInMillis(timestamp);

        if (today.get(Calendar.YEAR) == msgDate.get(Calendar.YEAR)) {
            if (today.get(Calendar.DAY_OF_YEAR) == msgDate.get(Calendar.DAY_OF_YEAR)) {
                return context.getString(R.string.room_chat_today);
            }
            if (today.get(Calendar.DAY_OF_YEAR) - msgDate.get(Calendar.DAY_OF_YEAR) == 1) {
                return context.getString(R.string.room_chat_yesterday);
            }
        }
        return DateFormat.format("dd MMM yyyy", new Date(timestamp)).toString();
    }

    private String formatTime(long timestamp) {
        if (timestamp <= 0) return "";
        return DateFormat.format("hh:mm a", new Date(timestamp)).toString();
    }

    static class MessageViewHolder extends RecyclerView.ViewHolder {
        TextView tvDateSeparator;
        LinearLayout layoutMe;
        TextView tvTextMe;
        TextView tvTimeMe;

        LinearLayout layoutOther;
        ShapeableImageView ivAvatarOther;
        TextView tvNameOther;
        TextView tvTextOther;
        TextView tvTimeOther;

        public MessageViewHolder(@NonNull View itemView) {
            super(itemView);
            tvDateSeparator = itemView.findViewById(R.id.tv_date_separator);

            layoutMe = itemView.findViewById(R.id.layout_message_me);
            tvTextMe = itemView.findViewById(R.id.tv_text_me);
            tvTimeMe = itemView.findViewById(R.id.tv_time_me);

            layoutOther = itemView.findViewById(R.id.layout_message_other);
            ivAvatarOther = itemView.findViewById(R.id.iv_avatar_other);
            tvNameOther = itemView.findViewById(R.id.tv_name_other);
            tvTextOther = itemView.findViewById(R.id.tv_text_other);
            tvTimeOther = itemView.findViewById(R.id.tv_time_other);
        }
    }
}
