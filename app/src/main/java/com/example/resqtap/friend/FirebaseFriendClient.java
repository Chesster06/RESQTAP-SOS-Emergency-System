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

    public static void acceptFriendRequest(Context context, String currentUid, String currentName, String currentPublicId, FriendRequest request) throws Exception {
        if (request == null) throw new RuntimeException("invalid_request");
        String cUid = String.valueOf(currentUid == null ? "" : currentUid).trim();
        String fUid = String.valueOf(request.fromUid == null ? "" : request.fromUid).trim();
        if (cUid.isEmpty() || fUid.isEmpty() || !QrCodeUtils.isValidFirebaseKey(cUid) || !QrCodeUtils.isValidFirebaseKey(fUid)) throw new RuntimeException("invalid_uids");

        DataSnapshot fromUserSnap = await(db().child("users").child(fUid).get());
        String fName = request.fromName;
        String fPhoto = request.fromPhotoUrl;
        String fB64 = request.fromPhotoB64;
        String fPublicId = request.fromPublicId;
        if (fromUserSnap != null && fromUserSnap.exists()) {
            if (fName.isEmpty()) fName = String.valueOf(fromUserSnap.child("name").getValue() == null ? "" : fromUserSnap.child("name").getValue()).trim();
            if (fPhoto.isEmpty()) fPhoto = String.valueOf(fromUserSnap.child("photoUrl").getValue() == null ? "" : fromUserSnap.child("photoUrl").getValue()).trim();
            if (fB64.isEmpty()) fB64 = String.valueOf(fromUserSnap.child("photoB64").getValue() == null ? "" : fromUserSnap.child("photoB64").getValue()).trim();
            if (fPublicId.isEmpty()) fPublicId = String.valueOf(fromUserSnap.child("publicId").getValue() == null ? "" : fromUserSnap.child("publicId").getValue()).trim();
        }

        DataSnapshot currentUserSnap = await(db().child("users").child(cUid).get());
        String cName = String.valueOf(currentName == null ? "" : currentName).trim();
        String cPhoto = "";
        String cB64 = "";
        String cPublicId = String.valueOf(currentPublicId == null ? "" : currentPublicId).trim();
        if (context != null) {
            cB64 = com.example.resqtap.utils.UserPrefs.getPhotoB64(context);
            cPhoto = com.example.resqtap.utils.UserPrefs.getPhotoUrl(context);
        }
        if (currentUserSnap != null && currentUserSnap.exists()) {
            if (cName.isEmpty()) cName = String.valueOf(currentUserSnap.child("name").getValue() == null ? "" : currentUserSnap.child("name").getValue()).trim();
            if (cPhoto == null || cPhoto.isEmpty()) cPhoto = String.valueOf(currentUserSnap.child("photoUrl").getValue() == null ? "" : currentUserSnap.child("photoUrl").getValue()).trim();
            if (cB64 == null || cB64.isEmpty()) cB64 = String.valueOf(currentUserSnap.child("photoB64").getValue() == null ? "" : currentUserSnap.child("photoB64").getValue()).trim();
            if (cPublicId.isEmpty()) cPublicId = String.valueOf(currentUserSnap.child("publicId").getValue() == null ? "" : currentUserSnap.child("publicId").getValue()).trim();
        }

        Map<String, Object> friendA = new HashMap<>();
        friendA.put("uid", fUid);
        friendA.put("name", fName);
        friendA.put("publicId", fPublicId);
        friendA.put("photoUrl", fPhoto == null ? "" : fPhoto);
        friendA.put("photoB64", fB64 == null ? "" : fB64);
        friendA.put("addedAt", ServerValue.TIMESTAMP);

        Map<String, Object> friendB = new HashMap<>();
        friendB.put("uid", cUid);
        friendB.put("name", cName);
        friendB.put("publicId", cPublicId);
        friendB.put("photoUrl", cPhoto == null ? "" : cPhoto);
        friendB.put("photoB64", cB64 == null ? "" : cB64);
        friendB.put("addedAt", ServerValue.TIMESTAMP);

        Map<String, Object> updates = new HashMap<>();
        updates.put("userFriends/" + cUid + "/" + fUid, friendA);
        updates.put("userFriends/" + fUid + "/" + cUid, friendB);

        updates.put("friendRequests/" + cUid + "/" + fUid, null);
        updates.put("sentRequests/" + fUid + "/" + cUid, null);

        String notifId = "friend_accepted_" + System.currentTimeMillis();
        String notifTitle = context != null ? context.getString(R.string.notif_friend_accepted_title) : "Friend Request Accepted";
        String who = cName.isEmpty() ? (context != null ? context.getString(R.string.friend_user_default) : "User") : cName;
        String notifMsg = context != null ? context.getString(R.string.notif_friend_accepted_msg, who) : who + " accepted your friend request!";

        Map<String, Object> notif = new HashMap<>();
        notif.put("id", notifId);
        notif.put("title", notifTitle);
        notif.put("message", notifMsg);
        notif.put("createdAt", ServerValue.TIMESTAMP);
        updates.put("userNotifications/" + fUid + "/" + notifId, notif);

        await(db().updateChildren(updates));
    }

    /** Simpan atau kemaskini gambar profil kawan dalam userFriends (latar belakang) */
    public static void backfillFriendPhotoQueued(String currentUid, String friendUid, String photoB64, String photoUrl) {
        try {
            String cUid = String.valueOf(currentUid == null ? "" : currentUid).trim();
            String fUid = String.valueOf(friendUid == null ? "" : friendUid).trim();
            if (cUid.isEmpty() || fUid.isEmpty() || !QrCodeUtils.isValidFirebaseKey(cUid) || !QrCodeUtils.isValidFirebaseKey(fUid)) return;
            Map<String, Object> updates = new HashMap<>();
            if (photoB64 != null && !photoB64.trim().isEmpty()) {
                updates.put("photoB64", photoB64.trim());
            }
            if (photoUrl != null && !photoUrl.trim().isEmpty()) {
                updates.put("photoUrl", photoUrl.trim());
            }
            if (!updates.isEmpty()) {
                db().child("userFriends").child(cUid).child(fUid).updateChildren(updates);
            }
        } catch (Exception ignored) {}
    }

    public static void rejectFriendRequest(String currentUid, String fromUid) throws Exception {
        String cUid = String.valueOf(currentUid == null ? "" : currentUid).trim();
        String fUid = String.valueOf(fromUid == null ? "" : fromUid).trim();
        if (cUid.isEmpty() || fUid.isEmpty() || !QrCodeUtils.isValidFirebaseKey(cUid) || !QrCodeUtils.isValidFirebaseKey(fUid)) return;

        Map<String, Object> updates = new HashMap<>();
        updates.put("friendRequests/" + cUid + "/" + fUid, null);
        updates.put("sentRequests/" + fUid + "/" + cUid, null);
        await(db().updateChildren(updates));
    }

    /** Padam atau bersihkan Friend. */
    public static void removeFriend(String currentUid, String friendUid) throws Exception {
        String cUid = String.valueOf(currentUid == null ? "" : currentUid).trim();
        String fUid = String.valueOf(friendUid == null ? "" : friendUid).trim();
        if (cUid.isEmpty() || fUid.isEmpty() || !QrCodeUtils.isValidFirebaseKey(cUid) || !QrCodeUtils.isValidFirebaseKey(fUid)) return;

        Map<String, Object> updates = new HashMap<>();
        updates.put("userFriends/" + cUid + "/" + fUid, null);
        updates.put("userFriends/" + fUid + "/" + cUid, null);
        await(db().updateChildren(updates));
    }

    /** Kemaskini nama samaran (nickname) rakan untuk paparan peribadi pengguna. */
    public static void updateFriendNickname(String currentUid, String friendUid, String newNickname) {
        String cUid = String.valueOf(currentUid == null ? "" : currentUid).trim();
        String fUid = String.valueOf(friendUid == null ? "" : friendUid).trim();
        if (cUid.isEmpty() || fUid.isEmpty() || !QrCodeUtils.isValidFirebaseKey(cUid) || !QrCodeUtils.isValidFirebaseKey(fUid)) return;

        String safeNick = newNickname == null ? "" : newNickname.trim();
        db().child("userFriends").child(cUid).child(fUid).child("nickname").setValue(safeNick);
    }

    public static class UserDetail {
        public String uid = "";
        public String name = "";
        public String email = "";
        public String phone = "";
        public String bloodType = "";
        public String allergies = "";
        public String existingConditions = "";
        public String gender = "";
        public String dob = "";
        public String address = "";

        public static UserDetail from(DataSnapshot snap) {
            if (snap == null || !snap.exists()) return null;
            UserDetail d = new UserDetail();
            d.uid = snap.getKey() == null ? "" : snap.getKey().trim();
            d.name = String.valueOf(snap.child("name").getValue() == null ? "" : snap.child("name").getValue()).trim();
            if (d.name.isEmpty()) {
                d.name = String.valueOf(snap.child("fullName").getValue() == null ? "" : snap.child("fullName").getValue()).trim();
            }
            d.email = String.valueOf(snap.child("email").getValue() == null ? "" : snap.child("email").getValue()).trim();
            d.phone = String.valueOf(snap.child("phone").getValue() == null ? "" : snap.child("phone").getValue()).trim();
            if (d.phone.isEmpty()) {
                d.phone = String.valueOf(snap.child("phoneNumber").getValue() == null ? "" : snap.child("phoneNumber").getValue()).trim();
            }
            d.bloodType = String.valueOf(snap.child("bloodType").getValue() == null ? "" : snap.child("bloodType").getValue()).trim();
            d.allergies = String.valueOf(snap.child("allergies").getValue() == null ? "" : snap.child("allergies").getValue()).trim();
            d.existingConditions = String.valueOf(snap.child("existingConditions").getValue() == null ? "" : snap.child("existingConditions").getValue()).trim();
            if (d.existingConditions.isEmpty()) {
                d.existingConditions = String.valueOf(snap.child("medications").getValue() == null ? "" : snap.child("medications").getValue()).trim();
            }
            d.gender = String.valueOf(snap.child("gender").getValue() == null ? "" : snap.child("gender").getValue()).trim();
            d.dob = String.valueOf(snap.child("dob").getValue() == null ? "" : snap.child("dob").getValue()).trim();
            if (d.dob.isEmpty()) {
                d.dob = String.valueOf(snap.child("dateOfBirth").getValue() == null ? "" : snap.child("dateOfBirth").getValue()).trim();
            }
            d.address = String.valueOf(snap.child("address").getValue() == null ? "" : snap.child("address").getValue()).trim();
            return d;
        }
    }

    public interface UserDetailCallback {
        void onLoaded(UserDetail detail);
    }

    /** Muat turun profil kawan daripada users/{uid} untuk paparan kad terperinci */
    public static void fetchUserDetailQueued(String uid, UserDetailCallback callback) {
        if (uid == null || uid.trim().isEmpty()) {
            if (callback != null) callback.onLoaded(null);
            return;
        }
        db().child("users").child(uid.trim()).get().addOnCompleteListener(task -> {
            if (task.isSuccessful() && task.getResult() != null && task.getResult().exists()) {
                UserDetail d = UserDetail.from(task.getResult());
                if (callback != null) callback.onLoaded(d);
            } else {
                if (callback != null) callback.onLoaded(null);
            }
        });
    }
}

