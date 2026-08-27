package com.example.resqtap.friend;

import android.content.Context;
import android.util.Log;

import com.example.resqtap.R;
import com.example.resqtap.notification.NotificationUtils;
import com.example.resqtap.room.FirebaseRoomClient;
import com.google.android.gms.tasks.Task;
import com.google.android.gms.tasks.Tasks;
import com.google.firebase.database.DataSnapshot;
import com.google.firebase.database.DatabaseReference;
import com.google.firebase.database.FirebaseDatabase;
import com.google.firebase.database.ServerValue;

import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.TimeUnit;


/**
 * FirebaseFriendClient
 * Friend Helper: cari friend guna Tag ID (contoh: User#4091), hantar & accept friend request.
 */
public class FirebaseFriendClient {
    private static final String TAG = "FirebaseFriendClient";

    private static DatabaseReference db() {
        return FirebaseDatabase.getInstance(FirebaseRoomClient.DATABASE_URL).getReference();
    }

    public static class FriendInfo {
        public String uid = "";
        public String publicId = "";
        public String name = "";
        public String nickname = "";
        public String photoUrl = "";
        public String photoB64 = "";
        public long addedAt = 0L;

        public static FriendInfo from(DataSnapshot snap) {
            if (snap == null || !snap.exists()) return null;
            FriendInfo info = new FriendInfo();
            info.uid = snap.getKey() == null ? "" : snap.getKey().trim();
            info.publicId = String.valueOf(snap.child("publicId").getValue() == null ? "" : snap.child("publicId").getValue()).trim();
            info.name = String.valueOf(snap.child("name").getValue() == null ? "" : snap.child("name").getValue()).trim();
            info.nickname = String.valueOf(snap.child("nickname").getValue() == null ? "" : snap.child("nickname").getValue()).trim();
            info.photoUrl = String.valueOf(snap.child("photoUrl").getValue() == null ? "" : snap.child("photoUrl").getValue()).trim();
            info.photoB64 = String.valueOf(snap.child("photoB64").getValue() == null ? "" : snap.child("photoB64").getValue()).trim();
            Object at = snap.child("addedAt").getValue();
            if (at instanceof Number) {
                info.addedAt = ((Number) at).longValue();
            }
            return info;
        }
    }

    public static class FriendRequest {
        public String fromUid = "";
        public String fromName = "";
        public String fromPublicId = "";
        public String fromPhotoUrl = "";
        public String fromPhotoB64 = "";
        public String targetUid = "";
        public String status = "pending";
        public long createdAt = 0L;

        public static FriendRequest from(DataSnapshot snap) {
            if (snap == null || !snap.exists()) return null;
            FriendRequest req = new FriendRequest();
            req.fromUid = String.valueOf(snap.child("fromUid").getValue() == null ? "" : snap.child("fromUid").getValue()).trim();
            if (req.fromUid.isEmpty()) req.fromUid = snap.getKey() == null ? "" : snap.getKey().trim();
            req.fromName = String.valueOf(snap.child("fromName").getValue() == null ? "" : snap.child("fromName").getValue()).trim();
            req.fromPublicId = String.valueOf(snap.child("fromPublicId").getValue() == null ? "" : snap.child("fromPublicId").getValue()).trim();
            req.fromPhotoUrl = String.valueOf(snap.child("fromPhotoUrl").getValue() == null ? "" : snap.child("fromPhotoUrl").getValue()).trim();
            req.fromPhotoB64 = String.valueOf(snap.child("fromPhotoB64").getValue() == null ? "" : snap.child("fromPhotoB64").getValue()).trim();
            req.targetUid = String.valueOf(snap.child("targetUid").getValue() == null ? "" : snap.child("targetUid").getValue()).trim();
            req.status = String.valueOf(snap.child("status").getValue() == null ? "pending" : snap.child("status").getValue()).trim();
            Object at = snap.child("createdAt").getValue();
            if (at instanceof Number) {
                req.createdAt = ((Number) at).longValue();
            }
            return req;
        }
    }

    private static <T> T await(Task<T> task) throws Exception {
        return Tasks.await(task, 15, TimeUnit.SECONDS);
    }

    public static String format4DigitId(String rawUid, String rawPubId) {
        if (rawPubId != null && !rawPubId.trim().isEmpty()) {
            String clean = rawPubId.trim().replaceAll("\\D", "");
            if (clean.length() >= 4) {
                return clean.substring(clean.length() - 4);
            } else if (!clean.isEmpty()) {
                try {
                    return String.format(java.util.Locale.US, "%04d", Integer.parseInt(clean));
                } catch (Exception ignored) {}
            }
        }
        if (rawUid != null && !rawUid.isEmpty()) {
            int hash = Math.abs(rawUid.hashCode()) % 10000;
            if (hash < 1000) hash += 1000;
            return String.format(java.util.Locale.US, "%04d", hash);
        }
        return "1000";
    }

    public static FriendInfo findUserByCode(String input) throws Exception {
        if (input == null) return null;
        String raw = input.trim();
        if (raw.isEmpty()) return null;

        String targetName = "";
        String targetTag = "";

        if (raw.contains("#")) {
            String[] parts = raw.split("#", 2);
            targetName = parts[0].trim();
            targetTag = parts[1].trim().replaceAll("\\D", "");
        } else if (raw.matches("^\\d{1,6}$")) {
            targetTag = raw;
        } else {
            targetName = raw;
        }

        // 1. Carian langsung melalui indeks publicIds atau direct UID (Pantas & selamat)
        if (!targetTag.isEmpty()) {
            try {
                DataSnapshot pidSnap = await(db().child("publicIds").child(targetTag).get());
                if (pidSnap != null && pidSnap.exists()) {
                    String foundUid = String.valueOf(pidSnap.getValue() == null ? "" : pidSnap.getValue()).trim();
                    if (!foundUid.isEmpty()) {
                        DataSnapshot uSnap = await(db().child("users").child(foundUid).get());
                        FriendInfo info = buildFriendInfoFromUserSnap(uSnap);
                        if (info != null) return info;
                    }
                }
            } catch (Exception ignored) {}

            try {
                DataSnapshot directSnap = await(db().child("users").child(targetTag).get());
                if (directSnap != null && directSnap.exists()) {
                    FriendInfo info = buildFriendInfoFromUserSnap(directSnap);
                    if (info != null) return info;
                }
            } catch (Exception ignored) {}
        }

        // 2. Fallback carian luas jika dibenarkan kebenaran peranan
        try {
            DatabaseReference usersRef = db().child("users");
            DataSnapshot allUsersSnap = await(usersRef.get());
            if (allUsersSnap != null && allUsersSnap.exists()) {
                for (DataSnapshot child : allUsersSnap.getChildren()) {
                    FriendInfo info = buildFriendInfoFromUserSnap(child);
                    if (info == null) continue;

                    String uName = info.name.trim();
                    String uTag = info.publicId.trim();
                    String uUid = info.uid.trim();

                    if (!targetName.isEmpty() && !targetTag.isEmpty()) {
                        if (uName.equalsIgnoreCase(targetName) && (uTag.equalsIgnoreCase(targetTag) || uTag.endsWith(targetTag))) {
                            return info;
                        }
                    } else if (!targetTag.isEmpty()) {
                        if (uTag.equalsIgnoreCase(targetTag) || uTag.endsWith(targetTag) || uUid.equalsIgnoreCase(targetTag)) {
                            return info;
                        }
                    } else if (!targetName.isEmpty()) {
                        if (uName.equalsIgnoreCase(targetName) || uUid.equalsIgnoreCase(targetName)) {
                            return info;
                        }
                    }
                }
            }
        } catch (Exception ignored) {}

        return null;
    }

    private static FriendInfo buildFriendInfoFromUserSnap(DataSnapshot snap) {
        if (snap == null || !snap.exists()) return null;
        FriendInfo info = new FriendInfo();
        info.uid = snap.getKey() == null ? "" : snap.getKey().trim();
        info.name = String.valueOf(snap.child("name").getValue() == null ? "" : snap.child("name").getValue()).trim();
        if (info.name.isEmpty()) info.name = String.valueOf(snap.child("fullName").getValue() == null ? "" : snap.child("fullName").getValue()).trim();
        if (info.name.isEmpty()) info.name = "User";

        String rawPub = String.valueOf(snap.child("publicId").getValue() == null ? "" : snap.child("publicId").getValue()).trim();
        info.publicId = format4DigitId(info.uid, rawPub);
        info.photoUrl = String.valueOf(snap.child("photoUrl").getValue() == null ? "" : snap.child("photoUrl").getValue()).trim();
        info.photoB64 = String.valueOf(snap.child("photoB64").getValue() == null ? "" : snap.child("photoB64").getValue()).trim();
        return info;
    }

    /** Simpan atau hantar data FriendRequest. */
    public static void sendFriendRequest(Context context, String fromUid, String fromName, String fromPublicId, String targetUid) throws Exception {
        String fUid = String.valueOf(fromUid == null ? "" : fromUid).trim();
        String tUid = String.valueOf(targetUid == null ? "" : targetUid).trim();
        if (fUid.isEmpty() || !QrCodeUtils.isValidFirebaseKey(fUid)) throw new RuntimeException("invalid_from_uid");
        if (tUid.isEmpty() || !QrCodeUtils.isValidFirebaseKey(tUid)) throw new RuntimeException("invalid_target_uid");
        if (fUid.equals(tUid)) throw new RuntimeException("cannot_add_self");

        try {
            DataSnapshot friendCheck = await(db().child("userFriends").child(fUid).child(tUid).get());
            if (friendCheck != null && friendCheck.exists()) {
                throw new RuntimeException("already_friends");
            }
        } catch (Exception e) {
            if ("already_friends".equals(e.getMessage())) throw e;
        }

        try {
            DataSnapshot sentCheck = await(db().child("sentRequests").child(fUid).child(tUid).get());
            if (sentCheck != null && sentCheck.exists()) {
                throw new RuntimeException("request_already_pending");
            }
        } catch (Exception e) {
            if ("request_already_pending".equals(e.getMessage())) throw e;
        }

        try {
            DataSnapshot pendingCheck = await(db().child("friendRequests").child(tUid).child(fUid).get());
            if (pendingCheck != null && pendingCheck.exists()) {
                throw new RuntimeException("request_already_pending");
            }
        } catch (Exception e) {
            if ("request_already_pending".equals(e.getMessage())) throw e;
        }

        String myB64 = "";
        String myUrl = "";
        if (context != null) {
            myB64 = com.example.resqtap.utils.UserPrefs.getPhotoB64(context);
            myUrl = com.example.resqtap.utils.UserPrefs.getPhotoUrl(context);
        }

        Map<String, Object> reqData = new HashMap<>();
        reqData.put("fromUid", fUid);
        reqData.put("fromName", String.valueOf(fromName == null ? "" : fromName).trim());
        reqData.put("fromPublicId", String.valueOf(fromPublicId == null ? "" : fromPublicId).trim());
        reqData.put("fromPhotoB64", String.valueOf(myB64 == null ? "" : myB64).trim());
        reqData.put("fromPhotoUrl", String.valueOf(myUrl == null ? "" : myUrl).trim());
        reqData.put("targetUid", tUid);
        reqData.put("status", "pending");
        reqData.put("createdAt", ServerValue.TIMESTAMP);

        Map<String, Object> updates = new HashMap<>();
        updates.put("friendRequests/" + tUid + "/" + fUid, reqData);
        updates.put("sentRequests/" + fUid + "/" + tUid, reqData);

        String notifId = "friend_req_" + System.currentTimeMillis();
        String notifTitle = context != null ? context.getString(R.string.notif_friend_request_title) : "Friend Request";
        String who = fromName.isEmpty() ? (context != null ? context.getString(R.string.someone) : "Someone") : fromName;
        String notifMsg = context != null ? context.getString(R.string.notif_friend_request_msg, who) : who + " sent you a friend request.";

        Map<String, Object> notif = new HashMap<>();
        notif.put("id", notifId);
        notif.put("title", notifTitle);
        notif.put("message", notifMsg);
        notif.put("createdAt", ServerValue.TIMESTAMP);
        updates.put("userNotifications/" + tUid + "/" + notifId, notif);

        await(db().updateChildren(updates));
    }
}
