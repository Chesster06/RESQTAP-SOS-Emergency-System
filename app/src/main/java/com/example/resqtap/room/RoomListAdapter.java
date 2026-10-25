package com.example.resqtap.room;
import com.example.resqtap.R;

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageButton;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import java.util.ArrayList;


/**
 * RoomListAdapter
 * Adapter RecyclerView untuk list bilik.
 */
public class RoomListAdapter extends RecyclerView.Adapter<RoomListAdapter.VH> {
    public interface Listener {
        void onView(FirebaseRoomClient.RoomInfo info);
        void onEdit(FirebaseRoomClient.RoomInfo info);
        void onDelete(FirebaseRoomClient.RoomInfo info);
    }

    private final ArrayList<FirebaseRoomClient.RoomInfo> items = new ArrayList<>();
    private final Listener listener;

    public RoomListAdapter(Listener listener) {
        this.listener = listener;
    }

    public void submit(ArrayList<FirebaseRoomClient.RoomInfo> list) {
        items.clear();
        if (list != null) items.addAll(list);
        notifyDataSetChanged();
    }

    @NonNull
    /** Inflate susun atur XML untuk item ViewHolder. */
    @Override
    public VH onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View v = LayoutInflater.from(parent.getContext()).inflate(R.layout.item_room, parent, false);
        return new VH(v);
    }

    /** Bind data ke elemen paparan item. */
    @Override
    public void onBindViewHolder(@NonNull VH h, int position) {
        FirebaseRoomClient.RoomInfo info = items.get(position);
        String code = info == null ? "" : String.valueOf(info.code);
        String role = info == null ? "" : String.valueOf(info.role);
        String name = info == null ? "" : String.valueOf(info.name);

        if (h.memberChip != null) {
            int count = info == null ? -1 : info.memberCount;
            if (count >= 0) {
                h.memberChip.setText(h.itemView.getContext().getString(R.string.room_members_count, count));
            } else {
                h.memberChip.setText(R.string.room_members_title);
            }
        }

        String label = (name == null || name.trim().isEmpty()) ? code : name.trim();
        h.code.setText(label.isEmpty() ? "-" : label);
        String roleLabel = "creator".equalsIgnoreCase(role) ? "Creator" : "Joined";
        h.role.setText(code.isEmpty() ? roleLabel : (roleLabel + " • " + code));

        boolean isCreator = "creator".equalsIgnoreCase(role);
        h.edit.setVisibility(View.VISIBLE);

        h.itemView.setOnClickListener(v -> {
            if (listener != null && info != null) listener.onView(info);
        });
        h.edit.setOnClickListener(v -> {
            if (listener != null && info != null) listener.onEdit(info);
        });
    }

    @Override
    public int getItemCount() {
        return items.size();
    }

    static final class VH extends RecyclerView.ViewHolder {
        final TextView code;
        final TextView role;
        final TextView memberChip;
        final ImageButton edit;

        VH(@NonNull View itemView) {
            super(itemView);
            code = itemView.findViewById(R.id.room_label);
            role = itemView.findViewById(R.id.room_code);
            memberChip = itemView.findViewById(R.id.tv_member_chip);
            edit = itemView.findViewById(R.id.btn_edit);
        }
    }
}
