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

    private void populateDetails(@NonNull ViewHolder holder, FirebaseFriendClient.UserDetail detail, Context ctx) {
        // Jenis Darah
        if (detail.bloodType != null && !detail.bloodType.trim().isEmpty()) {
            holder.tvBlood.setText(detail.bloodType.trim());
        } else {
            holder.tvBlood.setText("-");
        }

        // Alahan
        if (detail.allergies != null && !detail.allergies.trim().isEmpty()) {
            holder.tvAllergies.setText(detail.allergies.trim());
        } else {
            holder.tvAllergies.setText(ctx.getString(R.string.friend_details_not_provided));
        }

        // Keadaan Kesihatan
        if (detail.existingConditions != null && !detail.existingConditions.trim().isEmpty()) {
            holder.tvConditions.setText(detail.existingConditions.trim());
        } else {
            holder.tvConditions.setText(ctx.getString(R.string.friend_details_not_provided));
        }

        // Nombor Telefon & Panggilan Terus
        if (detail.phone != null && !detail.phone.trim().isEmpty()) {
            final String rawPhone = detail.phone.trim();
            holder.tvPhone.setText(rawPhone);
            holder.btnCall.setVisibility(View.VISIBLE);
            holder.btnCall.setOnClickListener(v -> dialPhone(ctx, rawPhone));
        } else {
            holder.tvPhone.setText(ctx.getString(R.string.friend_details_no_phone));
            holder.btnCall.setVisibility(View.GONE);
            holder.btnCall.setOnClickListener(null);
        }

        // Emel
        if (detail.email != null && !detail.email.trim().isEmpty()) {
            holder.tvEmail.setText(detail.email.trim());
        } else {
            holder.tvEmail.setText(ctx.getString(R.string.friend_details_no_email));
        }

        // Info Tambahan (Jantina / Tarikh Lahir / Alamat)
        StringBuilder sb = new StringBuilder();
        if (detail.gender != null && !detail.gender.trim().isEmpty()) {
            sb.append(detail.gender.trim());
        }
        if (detail.dob != null && !detail.dob.trim().isEmpty()) {
            if (sb.length() > 0) sb.append(" • ");
            sb.append(detail.dob.trim());
        }
        if (detail.address != null && !detail.address.trim().isEmpty()) {
            if (sb.length() > 0) sb.append(" • ");
            sb.append(detail.address.trim());
        }

        if (sb.length() > 0) {
            holder.rowExtra.setVisibility(View.VISIBLE);
            holder.tvExtra.setText(sb.toString());
        } else {
            holder.rowExtra.setVisibility(View.GONE);
        }
    }

    private void dialPhone(Context ctx, String phone) {
        if (ctx == null || phone == null || phone.trim().isEmpty()) return;
        try {
            Intent intent = new Intent(Intent.ACTION_DIAL);
            intent.setData(Uri.parse("tel:" + phone.trim()));
            ctx.startActivity(intent);
        } catch (Exception ignored) {}
    }

    private void loadFriendDetails(String friendUid, int adapterPos) {
        if (friendUid == null || friendUid.isEmpty()) return;
        if (!detailLookupInFlight.add(friendUid)) return;

        FirebaseFriendClient.fetchUserDetailQueued(friendUid, detail -> {
            detailLookupInFlight.remove(friendUid);
            if (detail != null) {
                detailCache.put(friendUid, detail);
            }
            mainHandler.post(() -> {
                int pos = -1;
                for (int i = 0; i < displayFriends.size(); i++) {
                    if (friendUid.equals(displayFriends.get(i).uid)) {
                        pos = i;
                        break;
                    }
                }
                if (pos != -1) {
                    notifyItemChanged(pos);
                }
            });
        });
    }

    private void maybeLookupFriendPhoto(FirebaseFriendClient.FriendInfo friend) {
        if (friend == null || friend.uid == null || friend.uid.isEmpty()) return;
        final String fUid = friend.uid;
        if (!lookupInFlight.add(fUid)) return;

        FirebaseRoomClient.fetchUserPhotoB64Queued(fUid, (id, fetchedB64) -> {
            String cleanB64 = fetchedB64 == null ? "" : fetchedB64.trim();
            if (!cleanB64.isEmpty()) {
                lookupInFlight.remove(fUid);
                photoB64Cache.put(fUid, cleanB64);
                friend.photoB64 = cleanB64;
                if (!currentUid.isEmpty()) {
                    FirebaseFriendClient.backfillFriendPhotoQueued(currentUid, fUid, cleanB64, "");
                }
                mainHandler.post(() -> {
                    int pos = displayFriends.indexOf(friend);
                    if (pos != -1) notifyItemChanged(pos);
                });
                return;
            }

            FirebaseRoomClient.fetchUserPhotoUrlQueued(fUid, (id2, fetchedUrl) -> {
                lookupInFlight.remove(fUid);
                String cleanUrl = fetchedUrl == null ? "" : fetchedUrl.trim();
                if (!cleanUrl.isEmpty()) {
                    photoUrlCache.put(fUid, cleanUrl);
                    friend.photoUrl = cleanUrl;
                    if (!currentUid.isEmpty()) {
                        FirebaseFriendClient.backfillFriendPhotoQueued(currentUid, fUid, "", cleanUrl);
                    }
                    mainHandler.post(() -> {
                        int pos = displayFriends.indexOf(friend);
                        if (pos != -1) notifyItemChanged(pos);
                    });
                }
            });
        });
    }

    @Override
    public int getItemCount() {
        return displayFriends.size();
    }

    public int getTotalCount() {
        return allFriends.size();
    }

    /** Dialog untuk menetapkan atau mengemaskini nama panggilan (nickname) peribadi. */
    private void showEditNicknameDialog(Context ctx, FirebaseFriendClient.FriendInfo friend, int position) {
        if (friend == null || ctx == null) return;

        EditText input = new EditText(ctx);
        input.setSingleLine(true);
        input.setHint(R.string.friend_nickname_hint);
        if (friend.nickname != null && !friend.nickname.trim().isEmpty()) {
            input.setText(friend.nickname.trim());
            input.setSelection(input.getText().length());
        }

        FrameLayout container = new FrameLayout(ctx);
        int px = (int) (22 * ctx.getResources().getDisplayMetrics().density);
        FrameLayout.LayoutParams lp = new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
        );
        lp.leftMargin = px;
        lp.rightMargin = px;
        lp.topMargin = (int) (10 * ctx.getResources().getDisplayMetrics().density);
        lp.bottomMargin = (int) (4 * ctx.getResources().getDisplayMetrics().density);
        input.setLayoutParams(lp);
        container.addView(input);

        MaterialAlertDialogBuilder builder = new MaterialAlertDialogBuilder(ctx)
                .setTitle(R.string.friend_nickname_dialog_title)
                .setMessage(R.string.friend_nickname_dialog_desc)
                .setView(container)
                .setNegativeButton(R.string.cancel, (dialog, which) -> dialog.dismiss())
                .setPositiveButton(R.string.friend_nickname_save, (dialog, which) -> {
                    String entered = input.getText().toString().trim();
                    friend.nickname = entered;
                    FirebaseFriendClient.updateFriendNickname(currentUid, friend.uid, entered);
                    notifyItemChanged(position);
                    Toast.makeText(ctx, R.string.friend_nickname_updated, Toast.LENGTH_SHORT).show();
                });

        if (friend.nickname != null && !friend.nickname.trim().isEmpty()) {
            builder.setNeutralButton(R.string.friend_nickname_reset, (dialog, which) -> {
                friend.nickname = "";
                FirebaseFriendClient.updateFriendNickname(currentUid, friend.uid, "");
                notifyItemChanged(position);
                Toast.makeText(ctx, R.string.friend_nickname_updated, Toast.LENGTH_SHORT).show();
            });
        }

        builder.show();
    }

    static class ViewHolder extends RecyclerView.ViewHolder {
        final View headerLayout;
        final ShapeableImageView avatar;
        final TextView name;
        final ImageView btnEditNickname;
        final TextView publicId;
        final MaterialButton btnRemove;
        final ImageView ivChevron;

        final View layoutDetails;
        final ProgressBar progressDetails;
        final View layoutContent;
        final TextView tvBlood;
        final TextView tvAllergies;
        final TextView tvConditions;
        final View rowPhone;
        final TextView tvPhone;
        final MaterialButton btnCall;
        final View rowEmail;
        final TextView tvEmail;
        final View rowExtra;
        final TextView tvExtra;

        ViewHolder(@NonNull View itemView) {
            super(itemView);
            headerLayout = itemView.findViewById(R.id.layout_friend_header);
            avatar = itemView.findViewById(R.id.friend_avatar);
            name = itemView.findViewById(R.id.friend_name);
            btnEditNickname = itemView.findViewById(R.id.btn_edit_nickname);
            publicId = itemView.findViewById(R.id.friend_public_id);
            btnRemove = itemView.findViewById(R.id.btn_remove_friend);
            ivChevron = itemView.findViewById(R.id.iv_expand_chevron);

            layoutDetails = itemView.findViewById(R.id.layout_friend_details);
            progressDetails = itemView.findViewById(R.id.progress_friend_details);
            layoutContent = itemView.findViewById(R.id.layout_details_content);
            tvBlood = itemView.findViewById(R.id.tv_friend_blood);
            tvAllergies = itemView.findViewById(R.id.tv_friend_allergies);
            tvConditions = itemView.findViewById(R.id.tv_friend_conditions);
            rowPhone = itemView.findViewById(R.id.row_friend_phone);
            tvPhone = itemView.findViewById(R.id.tv_friend_phone);
            btnCall = itemView.findViewById(R.id.btn_call_friend);
            rowEmail = itemView.findViewById(R.id.row_friend_email);
            tvEmail = itemView.findViewById(R.id.tv_friend_email);
            rowExtra = itemView.findViewById(R.id.row_friend_extra);
            tvExtra = itemView.findViewById(R.id.tv_friend_extra);
        }
    }
}
