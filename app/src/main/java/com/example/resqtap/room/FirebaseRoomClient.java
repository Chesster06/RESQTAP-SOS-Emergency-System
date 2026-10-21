package com.example.resqtap.room;

import com.example.resqtap.contacts.EmergencyContact;

import com.google.android.gms.tasks.Task;
import com.google.android.gms.tasks.Tasks;
import com.google.firebase.FirebaseNetworkException;
import com.google.firebase.database.DataSnapshot;
import com.google.firebase.database.DatabaseReference;
import com.google.firebase.database.FirebaseDatabase;
import com.google.firebase.database.ServerValue;
import com.google.firebase.database.ChildEventListener;
import com.google.firebase.database.DatabaseError;
import com.google.firebase.database.Query;
import com.google.firebase.database.ValueEventListener;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.TimeUnit;


/**
 * FirebaseRoomClient
 * Helper Room: create bilik (kod 6 digit), join bilik, dan broadcast SOS bilik.
 */
public final class FirebaseRoomClient {
    private static final int TIMEOUT_SECONDS = 12;

    public static final String DATABASE_URL = com.example.resqtap.config.AppConfig.FIREBASE_DATABASE_URL;

    public static final class Member {
        public final String uid;
        public final String name;
        public final double lat;
        public final double lng;
        public final long updatedAt;
        public final String photoUrl;
        public final String photoB64;
        public final int batteryPct;

        public Member(String uid, String name, double lat, double lng, long updatedAt, String photoUrl, String photoB64, int batteryPct) {
            this.uid = uid;
            this.name = name;
            this.lat = lat;
            this.lng = lng;
            this.updatedAt = updatedAt;
            this.photoUrl = photoUrl == null ? "" : photoUrl;
            this.photoB64 = photoB64 == null ? "" : photoB64;
            this.batteryPct = batteryPct;
        }
    }

    public static final class RoomInfo {
        public final String code;
        public final String role;
        public final String name;
        public final int memberCount;

        public RoomInfo(String code, String role, String name, int memberCount) {
            this.code = code;
            this.role = role;
            this.name = name == null ? "" : name;
            this.memberCount = Math.max(-1, memberCount);
        }
    }

    public static final class RoomPermissions {
        public boolean allowSosAlarm = true;
        public boolean allowTriggerBell = true;
        public boolean allowLocationSharing = true;
        public boolean allowMemberInvite = true;

        public RoomPermissions() {}

        public RoomPermissions(boolean allowSosAlarm, boolean allowTriggerBell, boolean allowLocationSharing, boolean allowMemberInvite) {
            this.allowSosAlarm = allowSosAlarm;
            this.allowTriggerBell = allowTriggerBell;
            this.allowLocationSharing = allowLocationSharing;
            this.allowMemberInvite = allowMemberInvite;
        }

        public Map<String, Object> toMap() {
            Map<String, Object> map = new HashMap<>();
            map.put("allowSosAlarm", allowSosAlarm);
            map.put("allowTriggerBell", allowTriggerBell);
            map.put("allowLocationSharing", allowLocationSharing);
            map.put("allowMemberInvite", allowMemberInvite);
            return map;
        }

        public static RoomPermissions fromSnapshot(DataSnapshot snap) {
            RoomPermissions p = new RoomPermissions();
            if (snap == null || !snap.exists()) return p;
            if (snap.hasChild("allowSosAlarm")) {
                Boolean v = snap.child("allowSosAlarm").getValue(Boolean.class);
                if (v != null) p.allowSosAlarm = v;
            }
            if (snap.hasChild("allowTriggerBell")) {
                Boolean v = snap.child("allowTriggerBell").getValue(Boolean.class);
                if (v != null) p.allowTriggerBell = v;
            }
            if (snap.hasChild("allowLocationSharing")) {
                Boolean v = snap.child("allowLocationSharing").getValue(Boolean.class);
                if (v != null) p.allowLocationSharing = v;
            }
            if (snap.hasChild("allowMemberInvite")) {
                Boolean v = snap.child("allowMemberInvite").getValue(Boolean.class);
                if (v != null) p.allowMemberInvite = v;
            }
            return p;
        }
    }

    public static final class RoomMessage {
        public String id;
        public String senderUid;
        public String senderName;
        public String senderPhotoUrl;
        public String text;
        public long timestamp;

        public RoomMessage() {}

        public RoomMessage(String id, String senderUid, String senderName, String senderPhotoUrl, String text, long timestamp) {
            this.id = id;
            this.senderUid = senderUid == null ? "" : senderUid;
            this.senderName = senderName == null ? "" : senderName;
            this.senderPhotoUrl = senderPhotoUrl == null ? "" : senderPhotoUrl;
            this.text = text == null ? "" : text;
            this.timestamp = timestamp;
        }
    }

    public interface RoomMessagesCallback {
        void onMessagesUpdated(ArrayList<RoomMessage> messages);
    }

    public interface RoomPermissionsCallback {
        void onLoaded(RoomPermissions permissions);
    }

    private FirebaseRoomClient() {
    }

    private static DatabaseReference db() {

        return FirebaseDatabase.getInstance(DATABASE_URL).getReference();
    }

    public interface BellHandler {
        void onBell(String fromUid, String fromDeviceId, String fromName, long atMillis, String id);
    }

    public interface RoomSosHandler {
        void onSos(
                String senderUid,
                String senderName,
                String roomId,
                long createdAtMs,
                String alertId,
                String status
        );
        void onSosCancelled(String senderUid, String roomId, String alertId, long cancelledAtMs);
        default void onSosServed(String senderUid, String roomId, String alertId, String servedByName) {}
        default void onSosResolved(String senderUid, String roomId, String alertId) {}
    }

    public interface RoomSosSendResult {
        void onResult(String sosId);
    }

    public interface ServerTimeResult {
        void onResult(long serverNowMs);
    }

    public interface SosAlertResult {
        void onResult(boolean ok, String reason, String alertId);
    }

    public interface RoomCodeResult {
        void onResult(String roomCode);
    }

    private static String normalizeCode(String code) {
        String in = String.valueOf(code == null ? "" : code).trim().toUpperCase();
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < in.length() && sb.length() < 32; i++) {
            char c = in.charAt(i);
            if ((c >= 'A' && c <= 'Z') || (c >= '0' && c <= '9') || c == '-' || c == '_') sb.append(c);
        }
        return sb.toString();
    }

    private static <T> T await(Task<T> task) throws Exception {
        try {
            return Tasks.await(task, TIMEOUT_SECONDS, TimeUnit.SECONDS);
        } catch (TimeoutException e) {
            throw new RuntimeException("backend_timeout");
        } catch (ExecutionException e) {
            Throwable cause = e.getCause();
            if (cause instanceof FirebaseNetworkException) throw new RuntimeException("backend_unreachable");
            String msg = cause == null ? "" : String.valueOf(cause.getMessage());
            if (msg.toLowerCase().contains("permission") && msg.toLowerCase().contains("denied")) {
                throw new RuntimeException("permission_denied");
            }
            throw e;
        } catch (Exception e) {

            Throwable root = e;
            while (root.getCause() != null && root.getCause() != root) root = root.getCause();
            if (root instanceof FirebaseNetworkException) throw new RuntimeException("backend_unreachable");
            throw e;
        }
    }

    public static void createRoom(String roomCode, String uid, String name, String password) throws Exception {
        String code = normalizeCode(roomCode);
        String u = String.valueOf(uid == null ? "" : uid).trim();
        if (code.length() < 4) throw new RuntimeException("invalid_room_code");
        if (u.isEmpty()) throw new RuntimeException("invalid_uid");

        try { db().child("roomTombstones").child(code).removeValue(); } catch (Exception ignored) {}

        DataSnapshot myRoleSnap = await(db().child("userRooms").child(u).child(code).child("role").get());
        String myRole = (myRoleSnap != null && myRoleSnap.exists() && myRoleSnap.getValue() != null)
                ? String.valueOf(myRoleSnap.getValue())
                : "";
        if (myRole != null && !myRole.trim().isEmpty()) {

            try {
                DataSnapshot roomSnap = await(db().child("rooms").child(code).get());
                if (roomSnap == null || !roomSnap.exists()) {
                    await(db().child("userRooms").child(u).child(code).removeValue());
                } else {
                    throw new RuntimeException("already_joined");
                }
            } catch (RuntimeException re) {

                if ("already_joined".equalsIgnoreCase(String.valueOf(re.getMessage()))) throw re;
                throw re;
            }
        }

        DataSnapshot existingCreator = await(db().child("rooms").child(code).child("creatorUid").get());
        if (existingCreator != null && existingCreator.exists() && existingCreator.getValue() != null) {
            String existingCreatorUid = String.valueOf(existingCreator.getValue()).trim();
            if (!existingCreatorUid.isEmpty()) throw new RuntimeException("room_code_exists");
        }

        String instanceId = db().child("rooms").child(code).child("members").push().getKey();
        if (instanceId == null || instanceId.trim().isEmpty()) instanceId = String.valueOf(System.currentTimeMillis());

        await(db().child("rooms").child(code).child("creatorUid").setValue(u));

        if (password != null && !password.trim().isEmpty()) {
            await(db().child("rooms").child(code).child("password").setValue(password.trim()));
        }

        try {
            await(db().child("rooms").child(code).child("createdAt").setValue(ServerValue.TIMESTAMP));
        } catch (Exception e) {
            throw new RuntimeException("create_room_createdAt_denied");
        }
        try {
            await(db().child("rooms").child(code).child("instanceId").setValue(instanceId));
        } catch (Exception e) {
            throw new RuntimeException("create_room_instanceId_denied");
        }
        try {
            Map<String, Object> member = new HashMap<>();
            member.put("uid", u);
            member.put("name", name == null ? "" : name.trim());
            member.put("instanceId", instanceId);
            member.put("updatedAt", ServerValue.TIMESTAMP);
            await(db().child("rooms").child(code).child("members").child(u).updateChildren(member));
        } catch (Exception e) {
            throw new RuntimeException("create_room_members_denied");
        }
        try {
            Map<String, Object> meta = new HashMap<>();
            meta.put("role", "creator");
            meta.put("createdAt", ServerValue.TIMESTAMP);
            await(db().child("userRooms").child(u).child(code).updateChildren(meta));
        } catch (Exception e) {
            throw new RuntimeException("create_room_userRooms_denied");
        }

        try {
            String notifId = "room_created_" + System.currentTimeMillis();
            Map<String, Object> notif = new HashMap<>();
            notif.put("id", notifId);
            notif.put("title", "Room Created Successfully");
            notif.put("message", "You have successfully created room " + code + ".");
            notif.put("createdAt", ServerValue.TIMESTAMP);
            db().child("userNotifications").child(u).child(notifId).setValue(notif);
        } catch (Exception ignored) {}
    }

    public static String createRoomWithFriends(String roomName, String creatorUid, String creatorName, List<com.example.resqtap.friend.FirebaseFriendClient.FriendInfo> friends) throws Exception {
        String u = String.valueOf(creatorUid == null ? "" : creatorUid).trim();
        if (u.isEmpty()) throw new RuntimeException("invalid_uid");

        String name = String.valueOf(roomName == null ? "" : roomName).trim();
        if (name.isEmpty()) name = "Safety Room";

        String code = normalizeCode("ROOM-" + RoomCodeUtils.generate(6));
        String instanceId = String.valueOf(System.currentTimeMillis());

        Map<String, Object> creatorMember = new HashMap<>();
        creatorMember.put("uid", u);
        creatorMember.put("name", creatorName == null ? "" : creatorName.trim());
        creatorMember.put("role", "creator");
        creatorMember.put("instanceId", instanceId);
        creatorMember.put("updatedAt", ServerValue.TIMESTAMP);

        Map<String, Object> updates = new HashMap<>();
        updates.put("rooms/" + code + "/name", name);
        updates.put("rooms/" + code + "/creatorUid", u);
        updates.put("rooms/" + code + "/createdAt", ServerValue.TIMESTAMP);
        updates.put("rooms/" + code + "/instanceId", instanceId);
        updates.put("rooms/" + code + "/members/" + u, creatorMember);

        Map<String, Object> creatorUserRoom = new HashMap<>();
        creatorUserRoom.put("role", "creator");
        creatorUserRoom.put("name", name);
        creatorUserRoom.put("label", name);
        creatorUserRoom.put("createdAt", ServerValue.TIMESTAMP);
        updates.put("userRooms/" + u + "/" + code, creatorUserRoom);

        if (friends != null) {
            for (com.example.resqtap.friend.FirebaseFriendClient.FriendInfo friend : friends) {
                if (friend == null || friend.uid == null || friend.uid.isEmpty() || friend.uid.equalsIgnoreCase(u)) continue;

                Map<String, Object> memberData = new HashMap<>();
                memberData.put("uid", friend.uid);
                memberData.put("name", friend.name == null ? "" : friend.name.trim());
                memberData.put("role", "member");
                memberData.put("instanceId", instanceId);
                memberData.put("updatedAt", ServerValue.TIMESTAMP);
                updates.put("rooms/" + code + "/members/" + friend.uid, memberData);

                Map<String, Object> friendUserRoom = new HashMap<>();
                friendUserRoom.put("role", "member");
                friendUserRoom.put("name", name);
                friendUserRoom.put("label", name);
                friendUserRoom.put("createdAt", ServerValue.TIMESTAMP);
                updates.put("userRooms/" + friend.uid + "/" + code, friendUserRoom);

                String safeSuffix = friend.uid.length() >= 4 ? friend.uid.substring(0, 4) : friend.uid;
                String notifId = "room_invite_" + System.currentTimeMillis() + "_" + safeSuffix;
                Map<String, Object> notif = new HashMap<>();
                notif.put("id", notifId);
                notif.put("title", "Room Invitation");
                notif.put("message", (creatorName != null && !creatorName.isEmpty() ? creatorName : "Your friend") + " added you to room '" + name + "'.");
                notif.put("createdAt", ServerValue.TIMESTAMP);
                updates.put("userNotifications/" + friend.uid + "/" + notifId, notif);
            }
        }

        await(db().updateChildren(updates));
        return code;
    }

    public static void addFriendToRoom(String roomCode, String roomName, String friendUid, String friendName, String inviterName) throws Exception {
        String code = String.valueOf(roomCode == null ? "" : roomCode).trim();
        String fUid = String.valueOf(friendUid == null ? "" : friendUid).trim();
        if (code.isEmpty() || fUid.isEmpty()) throw new RuntimeException("invalid_params");

        String name = String.valueOf(roomName == null ? "" : roomName).trim();
        if (name.isEmpty()) name = "Safety Room";

        DataSnapshot instanceSnap = await(db().child("rooms").child(code).child("instanceId").get());
        String instanceId = instanceSnap == null ? "" : String.valueOf(instanceSnap.getValue() == null ? "" : instanceSnap.getValue()).trim();

        Map<String, Object> updates = new HashMap<>();

        Map<String, Object> memberData = new HashMap<>();
        memberData.put("uid", fUid);
        memberData.put("name", friendName == null ? "" : friendName.trim());
        memberData.put("role", "member");
        memberData.put("updatedAt", ServerValue.TIMESTAMP);
        memberData.put("instanceId", instanceId);
        updates.put("rooms/" + code + "/members/" + fUid, memberData);

        Map<String, Object> friendUserRoom = new HashMap<>();
        friendUserRoom.put("role", "member");
        friendUserRoom.put("name", name);
        friendUserRoom.put("label", name);
        friendUserRoom.put("createdAt", ServerValue.TIMESTAMP);
        updates.put("userRooms/" + fUid + "/" + code, friendUserRoom);

        String safeSuffix = fUid.length() >= 4 ? fUid.substring(0, 4) : fUid;
        String notifId = "room_invite_" + System.currentTimeMillis() + "_" + safeSuffix;
        Map<String, Object> notif = new HashMap<>();
        notif.put("id", notifId);
        notif.put("title", "Room Invitation");
        notif.put("message", (inviterName != null && !inviterName.isEmpty() ? inviterName : "Your friend") + " added you to room '" + name + "'.");
        notif.put("createdAt", ServerValue.TIMESTAMP);
        updates.put("userNotifications/" + fUid + "/" + notifId, notif);

        await(db().updateChildren(updates));
    }

    public static void joinRoom(String roomCode, String uid, String name, String password) throws Exception {
        String code = normalizeCode(roomCode);
        String u = String.valueOf(uid == null ? "" : uid).trim();
        if (code.length() < 4) throw new RuntimeException("invalid_room_code");
        if (u.isEmpty()) throw new RuntimeException("invalid_uid");

        DataSnapshot roomExists = await(db().child("rooms").child(code).child("creatorUid").get());
        if (roomExists == null || !roomExists.exists() || roomExists.getValue() == null) {
            throw new RuntimeException("invalid_room_code");
        }
        String creatorUid = String.valueOf(roomExists.getValue()).trim();
        if (creatorUid.isEmpty()) throw new RuntimeException("invalid_room_code");
        if (creatorUid.equals(u)) throw new RuntimeException("owner_room");

        DataSnapshot existing = await(db().child("userRooms").child(u).child(code).child("role").get());
        String role = (existing != null && existing.exists() && existing.getValue() != null)
                ? String.valueOf(existing.getValue())
                : "";
        if (role != null && !role.trim().isEmpty()) {
            try {
                DataSnapshot roomSnap = await(db().child("rooms").child(code).get());
                if (roomSnap == null || !roomSnap.exists()) {
                    await(db().child("userRooms").child(u).child(code).removeValue());
                } else {
                    throw new RuntimeException("already_joined");
                }
            } catch (RuntimeException re) {
                if ("already_joined".equalsIgnoreCase(String.valueOf(re.getMessage()))) throw re;
                throw re;
            }
        }

        DataSnapshot passSnap = await(db().child("rooms").child(code).child("password").get());
        String expectedPassword = passSnap == null || !passSnap.exists() || passSnap.getValue() == null ? "" : String.valueOf(passSnap.getValue());
        String providedPassword = password == null ? "" : password.trim();
        if (!expectedPassword.isEmpty() && !expectedPassword.equals(providedPassword)) {
            throw new RuntimeException("incorrect_password");
        }

        DataSnapshot instanceSnap = await(db().child("rooms").child(code).child("instanceId").get());
        String instanceId = instanceSnap == null ? "" : String.valueOf(instanceSnap.getValue() == null ? "" : instanceSnap.getValue()).trim();
        if (instanceId.isEmpty()) throw new RuntimeException("invalid_room_code");

        Map<String, Object> updates = new HashMap<>();

        Map<String, Object> meta = new HashMap<>();
        meta.put("role", "joiner");
        meta.put("createdAt", ServerValue.TIMESTAMP);
        updates.put("userNotifications/" + u + "/room_joined_" + System.currentTimeMillis(), new HashMap<String, Object>() {{
            put("id", "room_joined_" + System.currentTimeMillis());
            put("title", "Joined Room Successfully");
            put("message", "You have successfully joined room " + code + ".");
            put("createdAt", ServerValue.TIMESTAMP);
        }});
        updates.put("userRooms/" + u + "/" + code, meta);

        Map<String, Object> member = new HashMap<>();
        member.put("uid", u);
        member.put("name", name == null ? "" : name.trim());
        member.put("instanceId", instanceId);
        member.put("updatedAt", ServerValue.TIMESTAMP);
        updates.put("rooms/" + code + "/members/" + u, member);

        await(db().updateChildren(updates));
    }

    public static void publishLocation(String roomCode, String uid, String name, String photoUrl, double lat, double lng) throws Exception {
        publishLocation(roomCode, uid, name, photoUrl, "", lat, lng, -1);
    }

    public static void publishLocation(String roomCode, String uid, String name, String photoUrl, String photoB64, double lat, double lng, int batteryPct) throws Exception {
        String code = normalizeCode(roomCode);
        String u = String.valueOf(uid == null ? "" : uid).trim();
        if (code.length() < 4) throw new RuntimeException("invalid_room_code");
        if (u.isEmpty()) throw new RuntimeException("invalid_uid");

        DataSnapshot instanceSnap = await(db().child("rooms").child(code).child("instanceId").get());
        String instanceId = instanceSnap == null ? "" : String.valueOf(instanceSnap.getValue() == null ? "" : instanceSnap.getValue()).trim();
        if (instanceId.isEmpty()) throw new RuntimeException("invalid_room_code");

        Map<String, Object> member = new HashMap<>();
        member.put("uid", u);
        member.put("name", name == null ? "" : name.trim());
        member.put("instanceId", instanceId);
        member.put("photoUrl", photoUrl == null ? "" : photoUrl.trim());
        member.put("photoB64", photoB64 == null ? "" : photoB64.trim());
        member.put("lat", lat);
        member.put("lng", lng);
        if (batteryPct >= 0 && batteryPct <= 100) member.put("batteryPct", batteryPct);
        member.put("updatedAt", ServerValue.TIMESTAMP);

        Map<String, Object> updates = new HashMap<>();
        updates.put("rooms/" + code + "/members/" + u, member);
        await(db().updateChildren(updates));
    }

    public static void publishLocationQueued(String roomCode, String uid, String name, String photoUrl, double lat, double lng) {
        publishLocationQueued(roomCode, uid, name, photoUrl, "", lat, lng, -1);
    }

    public static void publishLocationQueued(String roomCode, String uid, String name, String photoUrl, String photoB64, double lat, double lng, int batteryPct) {
        try {
            String u = String.valueOf(uid == null ? "" : uid).trim();
            if (u.isEmpty()) return;

            String code = normalizeCode(roomCode);
            if (code.length() < 4) return;

            db().child("userRooms").child(u).child(code).get().addOnSuccessListener(userRoomSnap -> {
                if (userRoomSnap == null || !userRoomSnap.exists()) return;
                db().child("rooms").child(code).child("instanceId").get().addOnSuccessListener(snap -> {
                    String instanceId = snap == null ? "" : String.valueOf(snap.getValue() == null ? "" : snap.getValue()).trim();
                    if (instanceId.isEmpty()) return;

                    Map<String, Object> member = new HashMap<>();
                    member.put("uid", u);
                    member.put("name", name == null ? "" : name.trim());
                    member.put("instanceId", instanceId);
                    member.put("photoUrl", photoUrl == null ? "" : photoUrl.trim());
                    member.put("photoB64", photoB64 == null ? "" : photoB64.trim());
                    member.put("lat", lat);
                    member.put("lng", lng);
                    member.put("latitude", lat);
                    member.put("longitude", lng);
                    if (batteryPct >= 0 && batteryPct <= 100) member.put("batteryPct", batteryPct);
                    member.put("updatedAt", ServerValue.TIMESTAMP);

                    db().child("rooms").child(code).child("members").child(u).updateChildren(member);
                }).addOnFailureListener(e -> {
                });
            }).addOnFailureListener(e -> {
            });
        } catch (Exception ignored) {
        }
    }

    public static void upsertMemberPresence(String roomCode, String uid, String name) throws Exception {
        upsertMemberPresence(roomCode, uid, name, "");
    }

    public static void upsertMemberPresence(String roomCode, String uid, String name, String photoUrl) throws Exception {
        upsertMemberPresence(roomCode, uid, name, photoUrl, "", -1);
    }

    public static void upsertMemberPresence(String roomCode, String uid, String name, String photoUrl, String photoB64, int batteryPct) throws Exception {
        String code = normalizeCode(roomCode);
        String u = String.valueOf(uid == null ? "" : uid).trim();
        if (code.length() < 4) throw new RuntimeException("invalid_room_code");
        if (u.isEmpty()) throw new RuntimeException("invalid_uid");

        DataSnapshot instanceSnap = await(db().child("rooms").child(code).child("instanceId").get());
        String instanceId = instanceSnap == null ? "" : String.valueOf(instanceSnap.getValue() == null ? "" : instanceSnap.getValue()).trim();
        if (instanceId.isEmpty()) throw new RuntimeException("invalid_room_code");

        Map<String, Object> member = new HashMap<>();
        member.put("uid", u);
        member.put("name", name == null ? "" : name.trim());
        member.put("instanceId", instanceId);
        member.put("photoUrl", photoUrl == null ? "" : photoUrl.trim());
        member.put("photoB64", photoB64 == null ? "" : photoB64.trim());
        if (batteryPct >= 0 && batteryPct <= 100) member.put("batteryPct", batteryPct);
        member.put("updatedAt", ServerValue.TIMESTAMP);

        await(db().child("rooms").child(code).child("members").child(u).updateChildren(member));
    }

    public static void upsertMemberPresenceQueued(String roomCode, String uid, String name, String photoUrl) {
        upsertMemberPresenceQueued(roomCode, uid, name, photoUrl, "", -1);
    }

    public static void upsertMemberPresenceQueued(String roomCode, String uid, String name, String photoUrl, String photoB64, int batteryPct) {
        try {
            String u = String.valueOf(uid == null ? "" : uid).trim();
            if (u.isEmpty()) return;

            String code = normalizeCode(roomCode);
            if (code.length() < 4) return;

            db().child("userRooms").child(u).child(code).get().addOnSuccessListener(userRoomSnap -> {
                if (userRoomSnap == null || !userRoomSnap.exists()) return;
                db().child("rooms").child(code).child("instanceId").get().addOnSuccessListener(snap -> {
                    String instanceId = snap == null ? "" : String.valueOf(snap.getValue() == null ? "" : snap.getValue()).trim();
                    if (instanceId.isEmpty()) return;
                    Map<String, Object> member = new HashMap<>();
                    member.put("uid", u);
                    member.put("name", name == null ? "" : name.trim());
                    member.put("instanceId", instanceId);
                    member.put("photoUrl", photoUrl == null ? "" : photoUrl.trim());
                    member.put("photoB64", photoB64 == null ? "" : photoB64.trim());
                    if (batteryPct >= 0 && batteryPct <= 100) member.put("batteryPct", batteryPct);
                    member.put("updatedAt", ServerValue.TIMESTAMP);
                    db().child("rooms").child(code).child("members").child(u).updateChildren(member);
                }).addOnFailureListener(e -> {
                });
            }).addOnFailureListener(e -> {
            });
        } catch (Exception ignored) {
        }
    }

    /** Padam atau bersihkan MemberPresenceQueued. */
    public static void removeMemberPresenceQueued(String roomCode, String uid) {
        try {
            String code = normalizeCode(roomCode);
            String u = String.valueOf(uid == null ? "" : uid).trim();
            if (code.length() < 4) return;
            if (u.isEmpty()) return;
            db().child("rooms").child(code).child("members").child(u).removeValue();
        } catch (Exception ignored) {
        }
    }

    public static String fetchCreatorUid(String roomCode) throws Exception {
        String code = normalizeCode(roomCode);
        if (code.length() < 4) throw new RuntimeException("invalid_room_code");
        DataSnapshot snap = await(db().child("rooms").child(code).child("creatorUid").get());
        return snap == null ? "" : String.valueOf(snap.getValue() == null ? "" : snap.getValue()).trim();
    }

    public static void backfillMemberPhotoUrlQueued(String roomCode, String uid, String photoUrl) {
        try {
            String code = normalizeCode(roomCode);
            String u = String.valueOf(uid == null ? "" : uid).trim();
            String url = String.valueOf(photoUrl == null ? "" : photoUrl).trim();
            if (code.length() < 4) return;
            if (u.isEmpty() || url.isEmpty()) return;
            if (!url.startsWith("http") && !url.startsWith("gs://")) return;
            Map<String, Object> updates = new HashMap<>();
            updates.put("photoUrl", url);
            db().child("rooms").child(code).child("members").child(u).updateChildren(updates);
        } catch (Exception ignored) {
        }
    }

    /** Simpan atau hantar data UserPhotoB64Queued. */
    public static void updateUserPhotoB64Queued(String uid, String photoB64) {
        try {
            String u = String.valueOf(uid == null ? "" : uid).trim();
            String b64 = String.valueOf(photoB64 == null ? "" : photoB64).trim();
            if (u.isEmpty() || b64.isEmpty()) return;
            Map<String, Object> updates = new HashMap<>();
            updates.put("photoB64", b64);

            updates.put("photoUrl", "");
            updates.put("photoUri", "");
            updates.put("updatedAt", ServerValue.TIMESTAMP);
        } catch (Exception ignored) {
        }
    }

    /** Padam atau bersihkan UserPhotoQueued. */
    public static void clearUserPhotoQueued(String uid) {
        try {
            String u = String.valueOf(uid == null ? "" : uid).trim();
            if (u.isEmpty()) return;
            Map<String, Object> updates = new HashMap<>();
            updates.put("photoB64", "");
            updates.put("photoUrl", "");
            updates.put("photoUri", "");
            updates.put("updatedAt", ServerValue.TIMESTAMP);

            db().child("userRooms").child(u).get()
                    .addOnSuccessListener(snap -> {
                        if (snap == null || !snap.exists()) return;
                        Map<String, Object> bulk = new HashMap<>();
                        for (DataSnapshot child : snap.getChildren()) {
                            if (child == null) continue;
                            String code = child.getKey() == null ? "" : child.getKey().trim();
                            if (code.isEmpty()) continue;
                            bulk.put("rooms/" + code + "/members/" + u + "/photoB64", "");
                            bulk.put("rooms/" + code + "/members/" + u + "/photoUrl", "");
                        }
                        if (!bulk.isEmpty()) db().updateChildren(bulk);
                    });
        } catch (Exception ignored) {
        }
    }

    public interface PhotoB64Handler {
        void onPhotoB64(String uid, String photoB64);
    }

    public static void fetchUserPhotoB64Queued(String uid, PhotoB64Handler handler) {
        try {
            String u = String.valueOf(uid == null ? "" : uid).trim();
            if (u.isEmpty() || handler == null) return;
            db().child("users").child(u).child("photoB64").get()
                    .addOnSuccessListener(snap -> {
                        String v = (snap != null && snap.exists() && snap.getValue() != null)
                                ? String.valueOf(snap.getValue())
                                : "";
                        handler.onPhotoB64(u, v == null ? "" : v);
                    })
                    .addOnFailureListener(e -> handler.onPhotoB64(u, ""));
        } catch (Exception ignored) {
        }
    }

    public static void backfillMemberPhotoB64Queued(String roomCode, String uid, String photoB64) {
        try {
            String code = normalizeCode(roomCode);
            String u = String.valueOf(uid == null ? "" : uid).trim();
            String b64 = String.valueOf(photoB64 == null ? "" : photoB64).trim();
            if (code.length() < 4) return;
            if (u.isEmpty() || b64.isEmpty()) return;
            Map<String, Object> updates = new HashMap<>();
            updates.put("photoB64", b64);
            db().child("rooms").child(code).child("members").child(u).updateChildren(updates);
        } catch (Exception ignored) {
        }
    }

    /** Simpan atau hantar data BellQueued. */
    public static void sendBellQueued(String roomCode, String fromUid, String toUid) {
        sendBellQueued(roomCode, fromUid, "", toUid);
    }

    /** Simpan atau hantar data BellQueued. */
    public static void sendBellQueued(String roomCode, String fromUid, String fromDeviceId, String toUid) {
        sendBellQueued(roomCode, fromUid, fromDeviceId, "", toUid);
    }

    /** Simpan atau hantar data BellQueued. */
    public static void sendBellQueued(String roomCode, String fromUid, String fromDeviceId, String fromName, String toUid) {
        try {
            String code = normalizeCode(roomCode);
            String from = String.valueOf(fromUid == null ? "" : fromUid).trim();
            String to = String.valueOf(toUid == null ? "" : toUid).trim();
            String fromDev = String.valueOf(fromDeviceId == null ? "" : fromDeviceId).trim();
            String name = String.valueOf(fromName == null ? "" : fromName).trim();
            if (code.length() < 4) return;
            if (from.isEmpty() || to.isEmpty()) return;

            DatabaseReference ref = db().child("rooms").child(code).child("bells").child(to).push();
            Map<String, Object> payload = new HashMap<>();
            payload.put("fromUid", from);
            if (!fromDev.isEmpty()) payload.put("fromDeviceId", fromDev);
            if (!name.isEmpty()) payload.put("fromName", name);
            payload.put("at", ServerValue.TIMESTAMP);
            payload.put("clientAt", System.currentTimeMillis());
            ref.setValue(payload);
        } catch (Exception ignored) {
        }
    }

    /** Simpan atau hantar data RoomSosQueued. */
    public static void sendRoomSosQueued(String roomCode, String fromUid, String fromDeviceId, String fromName) {
        sendRoomSosQueued(roomCode, fromUid, fromDeviceId, fromName, 0, 0, null);
    }

    public static void sendRoomSosQueued(String roomCode, String fromUid, String fromDeviceId, String fromName, RoomSosSendResult cb) {
        sendRoomSosQueued(roomCode, fromUid, fromDeviceId, fromName, 0, 0, cb);
    }

    public static void sendRoomSosQueued(String roomCode, String fromUid, String fromDeviceId, String fromName, double lat, double lng, RoomSosSendResult cb) {
        try {
            String code = normalizeCode(roomCode);
            String from = String.valueOf(fromUid == null ? "" : fromUid).trim();
            String fromDev = String.valueOf(fromDeviceId == null ? "" : fromDeviceId).trim();
            String name = String.valueOf(fromName == null ? "" : fromName).trim();
            if (code.length() < 4) return;
            if (from.isEmpty()) return;

            DatabaseReference ref = db().child("rooms").child(code).child("sosAlerts").push();
            String id = ref.getKey() == null ? "" : String.valueOf(ref.getKey());
            Map<String, Object> payload = new HashMap<>();

            payload.put("senderUid", from);
            if (!name.isEmpty()) payload.put("senderName", name);
            payload.put("roomId", code);
            payload.put("createdAt", ServerValue.TIMESTAMP);
            payload.put("alertId", id);
            payload.put("status", "active");

            payload.put("fromUid", from);
            if (!fromDev.isEmpty()) payload.put("fromDeviceId", fromDev);
            if (!name.isEmpty()) payload.put("fromName", name);
            payload.put("at", ServerValue.TIMESTAMP);
            payload.put("clientAt", System.currentTimeMillis());
            payload.put("ttlMs", 10_000L);

            if (lat != 0 || lng != 0) {
                payload.put("lat", lat);
                payload.put("lng", lng);
                payload.put("latitude", lat);
                payload.put("longitude", lng);

                // Koordinat disimpan dalam rekod alert SOS, bukan node users RTDB
            } else {
                // Sertakan juga koordinat GPS terakhir mangsa daripada profil users/{from} sekiranya ada
                try {
                    db().child("users").child(from).get().addOnSuccessListener(userSnap -> {
                        if (userSnap != null && userSnap.exists()) {
                            Double uLat = userSnap.child("lat").getValue(Double.class);
                            Double uLng = userSnap.child("lng").getValue(Double.class);
                            if (uLat != null && uLng != null && (uLat != 0 || uLng != 0)) {
                                Map<String, Object> locMap = new HashMap<>();
                                locMap.put("lat", uLat);
                                locMap.put("lng", uLng);
                                locMap.put("latitude", uLat);
                                locMap.put("longitude", uLng);
                                ref.updateChildren(locMap);
                            }
                        }
                    });
                } catch (Exception ignored) {}
            }

            ref.setValue(payload).addOnFailureListener(e -> {
                try {
                    android.util.Log.w("ResQTap", "sendRoomSosQueued failed: " + (e == null ? "" : e.getMessage()));
                } catch (Exception ignored) {
                }
                if (cb != null) cb.onResult("");
            }).addOnSuccessListener(v -> {
                if (cb != null) cb.onResult(id);
            });
        } catch (Exception ignored) {
            if (cb != null) cb.onResult("");
        }
    }

    public static void fetchServerNowQueued(ServerTimeResult cb) {
        try {
            db().child(".info").child("serverTimeOffset").get()
                    .addOnSuccessListener(snap -> {
                        long offset = 0L;
                        try {
                            Long v = snap == null ? null : snap.getValue(Long.class);
                            offset = v == null ? 0L : v;
                        } catch (Exception ignored) {
                        }
                        long serverNow = System.currentTimeMillis() + offset;
                        if (cb != null) cb.onResult(serverNow);
                    })
                    .addOnFailureListener(e -> {
                        if (cb != null) cb.onResult(System.currentTimeMillis());
                    });
        } catch (Exception e) {
            if (cb != null) cb.onResult(System.currentTimeMillis());
        }
    }

    public static void cancelRoomSosQueued(String roomCode, String sosId, String fromDeviceId) {
        try {
            String code = normalizeCode(roomCode);
            String id = String.valueOf(sosId == null ? "" : sosId).trim();
            String dev = String.valueOf(fromDeviceId == null ? "" : fromDeviceId).trim();
            if (code.length() < 4) return;
            if (id.isEmpty()) return;
            Map<String, Object> updates = new HashMap<>();
            updates.put("status", "cancelled");
            updates.put("active", false);
            updates.put("cancelledAt", ServerValue.TIMESTAMP);
            updates.put("cancelledClientAt", System.currentTimeMillis());
            if (!dev.isEmpty()) updates.put("cancelledByDeviceId", dev);
            db().child("rooms").child(code).child("sosAlerts").child(id).updateChildren(updates)
                    .addOnFailureListener(e -> {
                        try {
                            android.util.Log.e("ResQTap", "cancelRoomSosQueued failed for room " + code + " id " + id + ": " + (e == null ? "" : e.getMessage()));
                        } catch (Exception ignored) {}
                    });
        } catch (Exception ignored) {
        }
    }

    /** Batalkan semua amaran SOS aktif untuk pengguna ini merentasi semua bilik. */
    public static void cancelAllActiveSosForUser(String uid, String fromDeviceId) {
        cancelAllActiveSosForUser(uid, fromDeviceId, System.currentTimeMillis());
    }

    public static void cancelAllActiveSosForUser(String uid, String fromDeviceId, long beforeTimestampMs) {
        if (uid == null || uid.trim().isEmpty()) return;
        final String u = uid.trim();
        final String dev = String.valueOf(fromDeviceId == null ? "" : fromDeviceId).trim();

        try {
            // Batalkan juga di DIRECT fallback room sekiranya pengguna tiada bilik
            db().child("rooms").child("DIRECT").child("sosAlerts")
                    .get()
                    .addOnSuccessListener(alertSnap -> {
                        if (alertSnap == null || !alertSnap.exists()) return;
                        for (DataSnapshot alertChild : alertSnap.getChildren()) {
                            if (alertChild == null || alertChild.getKey() == null) continue;
                            String senderUid = String.valueOf(alertChild.child("senderUid").getValue() == null
                                    ? alertChild.child("fromUid").getValue() : alertChild.child("senderUid").getValue()).trim();
                            if (!u.equals(senderUid)) continue;

                            String status = String.valueOf(alertChild.child("status").getValue() == null ? "active" : alertChild.child("status").getValue());
                            boolean alreadyCancelled = "cancelled".equalsIgnoreCase(status) || "resolved".equalsIgnoreCase(status) || alertChild.child("cancelledAt").exists() || alertChild.child("resolvedAt").exists();
                            if (!alreadyCancelled) {
                                cancelRoomSosQueued("DIRECT", alertChild.getKey(), dev);
                            }
                        }
                    });

            db().child("userRooms").child(u).get().addOnSuccessListener(snap -> {
                if (snap == null || !snap.exists()) return;
                for (DataSnapshot roomChild : snap.getChildren()) {
                    if (roomChild == null || roomChild.getKey() == null) continue;
                    final String roomCode = roomChild.getKey().trim().toUpperCase(java.util.Locale.ROOT);
                    if (roomCode.length() < 4) continue;

                    db().child("rooms").child(roomCode).child("sosAlerts")
                            .get()
                            .addOnSuccessListener(alertSnap -> {
                                if (alertSnap == null || !alertSnap.exists()) return;
                                for (DataSnapshot alertChild : alertSnap.getChildren()) {
                                    if (alertChild == null || alertChild.getKey() == null) continue;
                                    String senderUid = String.valueOf(alertChild.child("senderUid").getValue() == null
                                            ? alertChild.child("fromUid").getValue() : alertChild.child("senderUid").getValue()).trim();
                                    if (!u.equals(senderUid)) continue;

                                    String status = String.valueOf(alertChild.child("status").getValue() == null ? "active" : alertChild.child("status").getValue());
                                    boolean alreadyCancelled = "cancelled".equalsIgnoreCase(status) || "resolved".equalsIgnoreCase(status) || alertChild.child("cancelledAt").exists() || alertChild.child("resolvedAt").exists();
                                    if (!alreadyCancelled) {
                                        cancelRoomSosQueued(roomCode, alertChild.getKey(), dev);
                                    }
                                }
                            });
                }
            });
        } catch (Exception ignored) {
        }
    }

    public static void createSosAlertOnline(
            String roomCode,
            String fromUid,
            String fromDeviceId,
            String toUid,
            SosAlertResult cb
    ) {
        String code = normalizeCode(roomCode);
        String from = String.valueOf(fromUid == null ? "" : fromUid).trim();
        String to = String.valueOf(toUid == null ? "" : toUid).trim();
        String fromDev = String.valueOf(fromDeviceId == null ? "" : fromDeviceId).trim();
        if (code.length() < 4) {
            if (cb != null) cb.onResult(false, "invalid_room_code", "");
            return;
        }
        if (from.isEmpty() || to.isEmpty()) {
            if (cb != null) cb.onResult(false, "invalid_uid", "");
            return;
        }

        try {
            DatabaseReference root = db();
            try {
                FirebaseDatabase.getInstance(DATABASE_URL).goOnline();
            } catch (Exception ignored) {
            }

            Runnable doSend = () -> {
                DatabaseReference ref = root.child("sos_alerts").push();
                String alertId = ref.getKey() == null ? "" : String.valueOf(ref.getKey());
                Map<String, Object> payload = new HashMap<>();
                payload.put("type", "bell");
                payload.put("roomCode", code);
                payload.put("fromUid", from);
                if (!fromDev.isEmpty()) payload.put("fromDeviceId", fromDev);
                payload.put("toUid", to);
                payload.put("createdAt", ServerValue.TIMESTAMP);
                payload.put("clientAt", System.currentTimeMillis());
                ref.setValue(payload).addOnCompleteListener(t -> {
                    if (cb == null) return;
                    if (t != null && t.isSuccessful()) cb.onResult(true, "", alertId);
                    else cb.onResult(false, (t != null && t.getException() != null) ? String.valueOf(t.getException().getMessage()) : "send_failed", alertId);
                });
            };

            final boolean[] done = new boolean[]{false};
            com.google.firebase.database.ValueEventListener l = new com.google.firebase.database.ValueEventListener() {
                /** Callback apabila data Firebase Realtime Database berubah. */
    @Override
                public void onDataChange(DataSnapshot s) {
                    if (done[0]) return;
                    boolean c = s != null && Boolean.TRUE.equals(s.getValue(Boolean.class));
                    if (!c) return;
                    done[0] = true;
                    try {
                        root.child(".info/connected").removeEventListener(this);
                    } catch (Exception ignored) {
                    }
                    doSend.run();
                }

                /** Callback sekiranya operasi Firebase dibatalkan atau gagal. */
    @Override
                public void onCancelled(com.google.firebase.database.DatabaseError error) {
                    if (done[0]) return;
                    done[0] = true;
                    if (cb != null) cb.onResult(false, "rtdb_disconnected", "");
                }
            };
            root.child(".info/connected").addValueEventListener(l);

            new android.os.Handler(android.os.Looper.getMainLooper()).postDelayed(() -> {
                if (done[0]) return;
                done[0] = true;
                try {
                    root.child(".info/connected").removeEventListener(l);
                } catch (Exception ignored) {
                }
                if (cb != null) cb.onResult(false, "rtdb_disconnected", "");
            }, 8000L);
        } catch (Exception e) {
            if (cb != null) cb.onResult(false, "send_failed", "");
        }
    }

    public interface BellSendResult {
        void onResult(boolean ok, String reason);
    }

    public static void sendBellOnline(
            String roomCode,
            String fromUid,
            String fromDeviceId,
            String toUid,
            BellSendResult cb
    ) {
        String code = normalizeCode(roomCode);
        String from = String.valueOf(fromUid == null ? "" : fromUid).trim();
        String to = String.valueOf(toUid == null ? "" : toUid).trim();
        String fromDev = String.valueOf(fromDeviceId == null ? "" : fromDeviceId).trim();
        if (code.length() < 4) {
            if (cb != null) cb.onResult(false, "invalid_room_code");
            return;
        }
        if (from.isEmpty() || to.isEmpty()) {
            if (cb != null) cb.onResult(false, "invalid_uid");
            return;
        }
        try {
            DatabaseReference root = db();

            try {
                FirebaseDatabase.getInstance(DATABASE_URL).goOnline();
            } catch (Exception ignored) {
            }

            Runnable doSend = () -> {
                DatabaseReference ref = root.child("rooms").child(code).child("bells").child(to).push();
                Map<String, Object> payload = new HashMap<>();
                payload.put("fromUid", from);
                if (!fromDev.isEmpty()) payload.put("fromDeviceId", fromDev);
                payload.put("at", ServerValue.TIMESTAMP);
                payload.put("clientAt", System.currentTimeMillis());
                ref.setValue(payload).addOnCompleteListener(t -> {
                    if (cb == null) return;
                    cb.onResult(t != null && t.isSuccessful(), (t != null && t.getException() != null) ? String.valueOf(t.getException().getMessage()) : "");
                });
            };

            root.child(".info/connected").get().addOnSuccessListener(snap -> {
                boolean connected = snap != null && Boolean.TRUE.equals(snap.getValue(Boolean.class));
                try {
                    android.util.Log.i("ResQTap", "RTDB connected=" + connected);
                } catch (Exception ignored) {
                }
                if (connected) {
                    doSend.run();
                    return;
                }

                final boolean[] done = new boolean[]{false};
                com.google.firebase.database.ValueEventListener l = new com.google.firebase.database.ValueEventListener() {
                    /** Callback apabila data Firebase Realtime Database berubah. */
    @Override
                    public void onDataChange(DataSnapshot s) {
                        if (done[0]) return;
                        boolean c = s != null && Boolean.TRUE.equals(s.getValue(Boolean.class));
                        if (!c) return;
                        done[0] = true;
                        root.child(".info/connected").removeEventListener(this);
                        doSend.run();
                    }

                    /** Callback sekiranya operasi Firebase dibatalkan atau gagal. */
    @Override
                    public void onCancelled(com.google.firebase.database.DatabaseError error) {
                        if (done[0]) return;
                        done[0] = true;
                        if (cb != null) cb.onResult(false, "offline");
                    }
                };
                root.child(".info/connected").addValueEventListener(l);

                new android.os.Handler(android.os.Looper.getMainLooper()).postDelayed(() -> {
                    if (done[0]) return;
                    done[0] = true;
                    try {
                        root.child(".info/connected").removeEventListener(l);
                    } catch (Exception ignored) {
                    }
                    if (cb != null) cb.onResult(false, "offline");
                }, 3000L);
            }).addOnFailureListener(e -> {
                if (cb != null) cb.onResult(false, "offline");
            });
        } catch (Exception e) {
            if (cb != null) cb.onResult(false, "send_failed");
        }
    }

    public static ChildEventListener listenBells(String roomCode, String uid, BellHandler handler) {
        return listenBells(roomCode, uid, "", handler);
    }

    public static ChildEventListener listenBells(String roomCode, String uid, String deviceId, BellHandler handler) {
        String code = normalizeCode(roomCode);
        String u = String.valueOf(uid == null ? "" : uid).trim();
        String dev = String.valueOf(deviceId == null ? "" : deviceId).trim();
        if (code.length() < 4) throw new RuntimeException("invalid_room_code");
        if (u.isEmpty()) throw new RuntimeException("invalid_uid");
        if (handler == null) throw new RuntimeException("invalid_handler");

        DatabaseReference ref = db().child("rooms").child(code).child("bells").child(u);
        try {

            ref.keepSynced(true);
        } catch (Exception ignored) {
        }
        ChildEventListener l = new ChildEventListener() {
            /** Kendalikan rekod baharu yang ditambah ke senarai RTDB. */
    @Override
            public void onChildAdded(DataSnapshot snapshot, String previousChildName) {
                if (snapshot == null || !snapshot.exists()) return;
                String id = snapshot.getKey() == null ? "" : snapshot.getKey();
                String from = String.valueOf(snapshot.child("fromUid").getValue() == null ? "" : snapshot.child("fromUid").getValue());
                String fromDev = String.valueOf(snapshot.child("fromDeviceId").getValue() == null ? "" : snapshot.child("fromDeviceId").getValue());
                String fromName = String.valueOf(snapshot.child("fromName").getValue() == null ? "" : snapshot.child("fromName").getValue());
                Long at = snapshot.child("at").getValue(Long.class);
                long when = at == null ? 0L : at;

                if (!dev.isEmpty()) {
                    try {
                        if (snapshot.child("acks").child(dev).exists()) return;
                    } catch (Exception ignored) {
                    }
                }

                try {
                    if (when > 0L) {
                        long ageMs = Math.abs(System.currentTimeMillis() - when);
                        if (ageMs > 5L * 60L * 1000L) {
                            ackBell(code, u, id, dev);
                            cleanupBell(code, u, id);
                            return;
                        }
                    }
                } catch (Exception ignored) {
                }
                handler.onBell(from, fromDev, fromName, when, id);
            }

            @Override public void onChildChanged(DataSnapshot snapshot, String previousChildName) {}
            @Override public void onChildRemoved(DataSnapshot snapshot) {}
            @Override public void onChildMoved(DataSnapshot snapshot, String previousChildName) {}
            @Override public void onCancelled(DatabaseError error) {}
        };
        ref.addChildEventListener(l);
        return l;
    }

    public static ChildEventListener listenRoomSos(String roomCode, String deviceId, RoomSosHandler handler) {
        return listenRoomSosSince(roomCode, deviceId, 0L, handler);
    }

    public static ChildEventListener listenRoomSosSince(String roomCode, String deviceId, long sinceCreatedAtMs, RoomSosHandler handler) {
        String code = normalizeCode(roomCode);
        if (code.length() < 4) throw new RuntimeException("invalid_room_code");
        if (handler == null) throw new RuntimeException("invalid_handler");

        com.google.firebase.database.Query ref = db().child("rooms").child(code).child("sosAlerts")
                .orderByChild("createdAt");
        long since = Math.max(0L, sinceCreatedAtMs);
        if (since > 0L) ref = ref.startAt(since);
        try {

            db().child("rooms").child(code).child("sosAlerts").keepSynced(true);
        } catch (Exception ignored) {
        }
        ChildEventListener l = new ChildEventListener() {
            /** Kendalikan rekod baharu yang ditambah ke senarai RTDB. */
    @Override
            public void onChildAdded(DataSnapshot snapshot, String previousChildName) {
                if (snapshot == null || !snapshot.exists()) return;
                String id = snapshot.child("alertId").getValue() == null ? "" : String.valueOf(snapshot.child("alertId").getValue());
                if (id.trim().isEmpty()) id = snapshot.getKey() == null ? "" : snapshot.getKey();
                String from = String.valueOf(snapshot.child("senderUid").getValue() == null ? "" : snapshot.child("senderUid").getValue());
                if (from.trim().isEmpty()) from = String.valueOf(snapshot.child("fromUid").getValue() == null ? "" : snapshot.child("fromUid").getValue());
                String fromDev = String.valueOf(snapshot.child("fromDeviceId").getValue() == null ? "" : snapshot.child("fromDeviceId").getValue()).trim();
                String dev = String.valueOf(deviceId == null ? "" : deviceId).trim();
                if (!dev.isEmpty() && !fromDev.isEmpty() && dev.equals(fromDev)) {
                    // Abaikan isyarat penggera yang dihantar oleh peranti ini sendiri
                    return;
                }

                String senderName = String.valueOf(snapshot.child("senderName").getValue() == null ? "" : snapshot.child("senderName").getValue());
                if (senderName.trim().isEmpty()) senderName = String.valueOf(snapshot.child("fromName").getValue() == null ? "" : snapshot.child("fromName").getValue());
                Long createdAt = snapshot.child("createdAt").getValue(Long.class);
                if (createdAt == null) createdAt = snapshot.child("at").getValue(Long.class);
                long when = createdAt == null ? 0L : createdAt;

                if (when <= 0L) {
                    try {
                        Long clientAt = snapshot.child("clientAt").getValue(Long.class);
                        long cAt = clientAt == null ? 0L : clientAt;
                        if (cAt > 0L) when = cAt;
                    } catch (Exception ignored) {
                    }
                }
                String status = String.valueOf(snapshot.child("status").getValue() == null ? "active" : snapshot.child("status").getValue());

                try {
                    boolean cancelled = "cancelled".equalsIgnoreCase(status)
                            || snapshot.child("cancelledAt").exists()
                            || snapshot.child("cancelledClientAt").exists();
                    if (cancelled) return;
                } catch (Exception ignored) {
                }

                handler.onSos(from, senderName, code, when, id, status);
            }

            /** Kendalikan perubahan data pada rekod sedia ada dalam RTDB. */
    @Override
            public void onChildChanged(DataSnapshot snapshot, String previousChildName) {
                if (snapshot == null || !snapshot.exists()) return;
                try {
                    String id = snapshot.child("alertId").getValue() == null ? "" : String.valueOf(snapshot.child("alertId").getValue());
                    if (id.trim().isEmpty()) id = snapshot.getKey() == null ? "" : snapshot.getKey();
                    String status = String.valueOf(snapshot.child("status").getValue() == null ? "" : snapshot.child("status").getValue());
                    Boolean servedVal = snapshot.child("served").getValue(Boolean.class);
                    String servedByVal = snapshot.child("servedBy").getValue(String.class);
                    boolean isServed = (servedVal != null && servedVal) || (servedByVal != null && !servedByVal.trim().isEmpty());

                    Long progressStepVal = snapshot.child("progressStep").getValue(Long.class);
                    long progressStep = progressStepVal == null ? 0L : progressStepVal;

                    boolean isResolved = "resolved".equalsIgnoreCase(status)
                            || snapshot.child("resolvedAt").exists()
                            || progressStep >= 4;

                    if (isResolved) {
                        String from = String.valueOf(snapshot.child("senderUid").getValue() == null ? "" : snapshot.child("senderUid").getValue());
                        if (from.trim().isEmpty()) from = String.valueOf(snapshot.child("fromUid").getValue() == null ? "" : snapshot.child("fromUid").getValue());
                        handler.onSosResolved(from, code, id);
                        return;
                    }

                    boolean cancelled = "cancelled".equalsIgnoreCase(status)
                            || snapshot.child("cancelledByAdmin").exists()
                            || (!isServed && (snapshot.child("cancelledAt").exists() || snapshot.child("cancelledClientAt").exists()));
                    if (cancelled) {
                        String from = String.valueOf(snapshot.child("senderUid").getValue() == null ? "" : snapshot.child("senderUid").getValue());
                        if (from.trim().isEmpty()) from = String.valueOf(snapshot.child("fromUid").getValue() == null ? "" : snapshot.child("fromUid").getValue());
                        Long cancelledAt = snapshot.child("cancelledAt").getValue(Long.class);
                        long cAt = cancelledAt == null ? 0L : cancelledAt;
                        handler.onSosCancelled(from, code, id, cAt);
                        return;
                    }

                    String from = String.valueOf(snapshot.child("senderUid").getValue() == null ? "" : snapshot.child("senderUid").getValue());
                    if (from.trim().isEmpty()) from = String.valueOf(snapshot.child("fromUid").getValue() == null ? "" : snapshot.child("fromUid").getValue());
                    String senderName = String.valueOf(snapshot.child("senderName").getValue() == null ? "" : snapshot.child("senderName").getValue());
                    if (senderName.trim().isEmpty()) senderName = String.valueOf(snapshot.child("fromName").getValue() == null ? "" : snapshot.child("fromName").getValue());
                    Long createdAt = snapshot.child("createdAt").getValue(Long.class);
                    if (createdAt == null) createdAt = snapshot.child("at").getValue(Long.class);
                    long when = createdAt == null ? 0L : createdAt;
                    if (when <= 0L) {
                        try {
                            Long clientAt = snapshot.child("clientAt").getValue(Long.class);
                            long cAt = clientAt == null ? 0L : clientAt;
                            if (cAt > 0L) when = cAt;
                        } catch (Exception ignored) {
                        }
                    }
                    handler.onSos(from, senderName, code, when, id, status == null || status.trim().isEmpty() ? "active" : status);
                } catch (Exception ignored) {
                }
            }
            @Override public void onChildRemoved(DataSnapshot snapshot) {}
            @Override public void onChildMoved(DataSnapshot snapshot, String previousChildName) {}
            @Override public void onCancelled(DatabaseError error) {}
        };
        ref.addChildEventListener(l);
        return l;
    }

    /** Padam atau bersihkan RoomSosListener. */
    public static void removeRoomSosListener(String roomCode, ChildEventListener listener) {
        try {
            String code = normalizeCode(roomCode);
            if (listener == null) return;
            if (code.length() < 4) return;
            db().child("rooms").child(code).child("sosAlerts").removeEventListener(listener);
        } catch (Exception ignored) {
        }
    }

    public static void ackRoomSos(String roomCode, String sosId, String deviceId) {
        try {
            String code = normalizeCode(roomCode);
            String id = String.valueOf(sosId == null ? "" : sosId).trim();
            String dev = String.valueOf(deviceId == null ? "" : deviceId).trim();
            if (code.length() < 4) return;
            if (id.isEmpty()) return;

            if (dev.isEmpty()) {
                cleanupRoomSos(code, id);
                return;
            }
            Map<String, Object> updates = new HashMap<>();
            updates.put("acks/" + dev, ServerValue.TIMESTAMP);
            db().child("rooms").child(code).child("sosAlerts").child(id).updateChildren(updates);
        } catch (Exception ignored) {
        }
    }

    private static void cleanupRoomSos(String normalizedRoomCode, String sosId) {
        try {
            String code = String.valueOf(normalizedRoomCode == null ? "" : normalizedRoomCode).trim();
            String id = String.valueOf(sosId == null ? "" : sosId).trim();
            if (code.length() < 4) return;
            if (id.isEmpty()) return;
            db().child("rooms").child(code).child("sosAlerts").child(id).removeValue();
        } catch (Exception ignored) {
        }
    }

    /** Padam atau bersihkan BellListener. */
    public static void removeBellListener(String roomCode, String uid, ChildEventListener listener) {
        try {
            String code = normalizeCode(roomCode);
            String u = String.valueOf(uid == null ? "" : uid).trim();
            if (listener == null) return;
            if (code.length() < 4) return;
            if (u.isEmpty()) return;
            db().child("rooms").child(code).child("bells").child(u).removeEventListener(listener);
        } catch (Exception ignored) {
        }
    }

    public static void ackBell(String roomCode, String toUid, String bellId, String deviceId) {
        try {
            String code = normalizeCode(roomCode);
            String to = String.valueOf(toUid == null ? "" : toUid).trim();
            String id = String.valueOf(bellId == null ? "" : bellId).trim();
            String dev = String.valueOf(deviceId == null ? "" : deviceId).trim();
            if (code.length() < 4) return;
            if (to.isEmpty() || id.isEmpty()) return;

            if (dev.isEmpty()) {
                cleanupBell(code, to, id);
                return;
            }

            Map<String, Object> updates = new HashMap<>();
            updates.put("acks/" + dev, ServerValue.TIMESTAMP);
            db().child("rooms").child(code).child("bells").child(to).child(id).updateChildren(updates);
        } catch (Exception ignored) {
        }
    }

    private static void cleanupBell(String normalizedRoomCode, String toUid, String bellId) {
        try {
            String code = String.valueOf(normalizedRoomCode == null ? "" : normalizedRoomCode).trim();
            String to = String.valueOf(toUid == null ? "" : toUid).trim();
            String id = String.valueOf(bellId == null ? "" : bellId).trim();
            if (code.length() < 4) return;
            if (to.isEmpty() || id.isEmpty()) return;
            db().child("rooms").child(code).child("bells").child(to).child(id).removeValue();
        } catch (Exception ignored) {
        }
    }

    public static void upsertUserProfile(
            String uid,
            String name,
            String email,
            String bloodType,
            String allergies,
            String existingConditions,
            String photoUrl,
            String publicId,
            String address,
            String religion,
            String phoneNumber,
            String dateOfBirth,
            String ethnicity,
            String gender,
            String weight,
            String height
    ) throws Exception {
        String u = String.valueOf(uid == null ? "" : uid).trim();
        if (u.isEmpty()) throw new RuntimeException("invalid_uid");

        Map<String, Object> user = new HashMap<>();
        user.put("uid", u);
        user.put("name", name == null ? "" : name.trim());
        user.put("email", email == null ? "" : email.trim().toLowerCase(java.util.Locale.ROOT));
        user.put("bloodType", bloodType == null ? "" : bloodType.trim());
        user.put("allergies", allergies == null ? "" : allergies.trim());
        user.put("existingConditions", existingConditions == null ? "" : existingConditions.trim());
        user.put("photoUrl", photoUrl == null ? "" : photoUrl.trim());

        user.put("photoUri", photoUrl == null ? "" : photoUrl.trim());
        user.put("publicId", publicId == null ? "" : publicId.trim().toUpperCase());
        user.put("address", address == null ? "" : address.trim());
        user.put("religion", religion == null ? "" : religion.trim());
        user.put("phoneNumber", phoneNumber == null ? "" : phoneNumber.trim());
        user.put("dateOfBirth", dateOfBirth == null ? "" : dateOfBirth.trim());
        user.put("ethnicity", ethnicity == null ? "" : ethnicity.trim());
        user.put("gender", gender == null ? "" : gender.trim());
        user.put("weight", weight == null ? "" : weight.trim());
        user.put("height", height == null ? "" : height.trim());
        user.put("updatedAt", ServerValue.TIMESTAMP);

        // Simpan profil & kad perubatan secara langsung ke Supabase (tiada di RTDB users)
        try {
            com.example.resqtap.supabase.SupabaseManager.getInstance().upsertProfile(u, email, name, phoneNumber, photoUrl, null);
            com.example.resqtap.supabase.SupabaseManager.getInstance().upsertMedicalCard(u, bloodType, allergies, existingConditions, "", null);
        } catch (Exception ignored) {}
    }

    /** Simpan atau hantar data UserPhotoUrl. */
    public static void updateUserPhotoUrl(String uid, String photoUrl) throws Exception {
        String u = String.valueOf(uid == null ? "" : uid).trim();
        if (u.isEmpty()) throw new RuntimeException("invalid_uid");
        // Gambar disimpan di profil Supabase, bukan node users RTDB
    }

    /** Simpan atau hantar data UserPhotoUrlQueued. */
    public static void updateUserPhotoUrlQueued(String uid, String photoUrl) {
        // Gambar disimpan di profil Supabase, bukan node users RTDB
    }

    /** Simpan atau hantar data UserFcmTokenQueued. */
    public static void updateUserFcmTokenQueued(String uid, String fcmToken) {
        updateUserFcmTokenQueued(uid, "", fcmToken);
    }

    /** Simpan atau hantar data UserFcmTokenQueued. */
    public static void updateUserFcmTokenQueued(String uid, String deviceId, String fcmToken) {
        try {
            String u = String.valueOf(uid == null ? "" : uid).trim();
            String t = String.valueOf(fcmToken == null ? "" : fcmToken).trim();
            if (u.isEmpty() || t.isEmpty()) return;
            com.example.resqtap.supabase.SupabaseManager.getInstance().updateFcmToken(u, t);
        } catch (Exception ignored) {
        }
    }

    public interface PhotoUrlHandler {
        void onPhotoUrl(String uid, String photoUrl);
    }

    public static void fetchUserPhotoUrlQueued(String uid, PhotoUrlHandler handler) {
        if (handler != null) handler.onPhotoUrl(uid, "");
    }

    /** Simpan atau hantar data UserName. */
    public static void updateUserName(String uid, String name) throws Exception {
        String u = String.valueOf(uid == null ? "" : uid).trim();
        if (u.isEmpty()) throw new RuntimeException("invalid_uid");
        // Profil diuruskan oleh Supabase
    }

    public static void upsertEmergencyContact(String uid, EmergencyContact contact) throws Exception {
        String u = String.valueOf(uid == null ? "" : uid).trim();
        if (u.isEmpty()) throw new RuntimeException("invalid_uid");
        if (contact == null) throw new RuntimeException("invalid_contact");
        // Hubungan kecemasan diuruskan oleh Supabase
    }

    /** Padam atau bersihkan EmergencyContact. */
    public static void deleteEmergencyContact(String uid, String contactId) throws Exception {
        String u = String.valueOf(uid == null ? "" : uid).trim();
        if (u.isEmpty()) throw new RuntimeException("invalid_uid");
        // Hubungan kecemasan diuruskan oleh Supabase
    }

    public static ArrayList<Member> fetchMembers(String roomCode, int maxAgeSeconds) throws Exception {
        String code = normalizeCode(roomCode);
        if (code.length() < 4) throw new RuntimeException("invalid_room_code");

        int maxAge = maxAgeSeconds <= 0 ? 0 : Math.max(10, Math.min(maxAgeSeconds, 3600));
        long minUpdatedAt = maxAge <= 0 ? Long.MIN_VALUE : (System.currentTimeMillis() - (maxAge * 1000L));

        DataSnapshot snap = await(db().child("rooms").child(code).child("members").get());
        ArrayList<Member> out = new ArrayList<>();
        if (snap == null || !snap.exists()) return out;

        for (DataSnapshot child : snap.getChildren()) {
            String uid = String.valueOf(child.child("uid").getValue() == null ? "" : child.child("uid").getValue());
            if (uid.trim().isEmpty()) uid = child.getKey() == null ? "" : child.getKey();
            if (uid.trim().isEmpty()) continue;
            String name = String.valueOf(child.child("name").getValue() == null ? "" : child.child("name").getValue());
            String photoUrl = String.valueOf(child.child("photoUrl").getValue() == null ? "" : child.child("photoUrl").getValue());
            String photoB64 = String.valueOf(child.child("photoB64").getValue() == null ? "" : child.child("photoB64").getValue());
            Integer batteryPct = child.child("batteryPct").getValue(Integer.class);
            Double lat = child.child("lat").getValue(Double.class);
            Double lng = child.child("lng").getValue(Double.class);
            Long updatedAt = child.child("updatedAt").getValue(Long.class);
            double la = lat == null ? 0 : lat;
            double ln = lng == null ? 0 : lng;
            long ua = updatedAt == null ? 0 : updatedAt;
            if (maxAge > 0 && ua > 0 && ua < minUpdatedAt) continue;
            int pct = batteryPct == null ? -1 : batteryPct;
            out.add(new Member(uid, name, la, ln, ua, photoUrl, photoB64, pct));
        }
        return out;
    }

    public static ArrayList<RoomInfo> fetchUserRooms(String uid) throws Exception {
        String u = String.valueOf(uid == null ? "" : uid).trim();
        if (u.isEmpty()) throw new RuntimeException("invalid_uid");

        DataSnapshot snap = await(db().child("userRooms").child(u).get());
        ArrayList<RoomInfo> out = new ArrayList<>();
        if (snap == null || !snap.exists()) return out;

        List<DataSnapshot> children = new ArrayList<>();
        for (DataSnapshot child : snap.getChildren()) {
            if (child != null && child.getKey() != null && !child.getKey().trim().isEmpty()) {
                children.add(child);
            }
        }

        // Fire all room detail requests in parallel
        Map<String, Task<DataSnapshot>> roomTasks = new HashMap<>();
        for (DataSnapshot child : children) {
            String code = child.getKey().trim();
            roomTasks.put(code, db().child("rooms").child(code).get());
        }

        // Wait with a small timeout for parallel resolution
        try {
            Tasks.await(Tasks.whenAllComplete(roomTasks.values()), 1800, TimeUnit.MILLISECONDS);
        } catch (Exception ignored) {
        }

        for (DataSnapshot child : children) {
            String code = child.getKey().trim();
            String role = String.valueOf(child.child("role").getValue() == null ? "joiner" : child.child("role").getValue());
            String roomName = String.valueOf(child.child("name").getValue() != null ? child.child("name").getValue() : (child.child("label").getValue() == null ? "" : child.child("label").getValue())).trim();
            int memberCount = -1;

            Task<DataSnapshot> t = roomTasks.get(code);
            if (t != null && t.isSuccessful() && t.getResult() != null) {
                if (!t.getResult().exists()) {
                    try {
                        db().child("userRooms").child(u).child(code).removeValue();
                    } catch (Exception ignored) {
                    }
                    continue;
                }
                DataSnapshot roomSnap = t.getResult();
                if (roomName.isEmpty()) {
                    roomName = String.valueOf(roomSnap.child("name").getValue() == null ? "" : roomSnap.child("name").getValue()).trim();
                }
                try {
                    DataSnapshot members = roomSnap.child("members");
                    if (members != null && members.exists()) {
                        long c = members.getChildrenCount();
                        if (c >= 0 && c <= Integer.MAX_VALUE) memberCount = (int) c;
                    } else {
                        memberCount = 0;
                    }
                } catch (Exception ignored) {
                }
            }

            if (roomName.isEmpty()) roomName = code;
            out.add(new RoomInfo(code, role, roomName, memberCount));
        }
        return out;
    }

    public static void fetchMostRecentUserRoomCodeQueued(String uid, RoomCodeResult cb) {
        String u = String.valueOf(uid == null ? "" : uid).trim();
        if (u.isEmpty()) {
            if (cb != null) cb.onResult("");
            return;
        }
        try {
            db().child("userRooms").child(u).get().addOnSuccessListener(snapshot -> {
                String code = "";
                long bestCreated = -1;
                try {
                    if (snapshot != null && snapshot.exists()) {
                        for (DataSnapshot child : snapshot.getChildren()) {
                            if (child == null || child.getKey() == null) continue;
                            String k = child.getKey().trim();
                            if (k.isEmpty()) continue;
                            Object ca = child.child("createdAt").getValue();
                            long t = (ca instanceof Number) ? ((Number) ca).longValue() : 0L;
                            if (code.isEmpty() || t >= bestCreated) {
                                code = k;
                                bestCreated = t;
                            }
                        }
                    }
                } catch (Exception ignored) {
                }
                if (cb != null) cb.onResult(code);
            }).addOnFailureListener(e -> {
                if (cb != null) cb.onResult("");
            });
        } catch (Exception e) {
            if (cb != null) cb.onResult("");
        }
    }

    public interface BooleanCallback {
        void onResult(boolean result);
    }

    /** Sahkan sama ada pengguna benar-benar berdaftar dalam bilik ini di userRooms/{uid}/{roomCode}. */
    public static void verifyUserInRoom(String uid, String roomCode, BooleanCallback cb) {
        String u = String.valueOf(uid == null ? "" : uid).trim();
        String code = normalizeCode(roomCode);
        if (u.isEmpty() || code.length() < 4) {
            if (cb != null) cb.onResult(false);
            return;
        }
        try {
            db().child("userRooms").child(u).child(code).get().addOnSuccessListener(snap -> {
                boolean exists = snap != null && snap.exists();
                if (cb != null) cb.onResult(exists);
            }).addOnFailureListener(e -> {
                if (cb != null) cb.onResult(false);
            });
        } catch (Exception e) {
            if (cb != null) cb.onResult(false);
        }
    }

    /** Simpan atau hantar data RoomNameAsCreator. */
    public static void updateRoomNameAsCreator(String uid, String roomCode, String name) throws Exception {
        String u = String.valueOf(uid == null ? "" : uid).trim();
        String code = normalizeCode(roomCode);
        String n = String.valueOf(name == null ? "" : name).trim();
        if (u.isEmpty()) throw new RuntimeException("invalid_uid");
        if (code.length() < 4) throw new RuntimeException("invalid_room_code");
        if (n.isEmpty()) throw new RuntimeException("invalid_name");

        DataSnapshot roleSnap = await(db().child("userRooms").child(u).child(code).child("role").get());
        String role = roleSnap == null ? "" : String.valueOf(roleSnap.getValue());
        if (!"creator".equalsIgnoreCase(String.valueOf(role == null ? "" : role).trim())) {
            throw new RuntimeException("not_creator");
        }

        DataSnapshot membersSnap = await(db().child("rooms").child(code).child("members").get());
        java.util.ArrayList<String> memberUids = new java.util.ArrayList<>();
        if (membersSnap != null && membersSnap.exists()) {
            for (DataSnapshot child : membersSnap.getChildren()) {
                String mid = child.getKey() == null ? "" : child.getKey().trim();
                if (!mid.isEmpty()) memberUids.add(mid);
            }
        }
        if (!memberUids.contains(u)) memberUids.add(u);

        Map<String, Object> updates = new HashMap<>();
        updates.put("rooms/" + code + "/name", n);
        updates.put("rooms/" + code + "/updatedAt", ServerValue.TIMESTAMP);
        for (String mid : memberUids) {
            String m = String.valueOf(mid == null ? "" : mid).trim();
            if (m.isEmpty()) continue;
            updates.put("userRooms/" + m + "/" + code + "/label", n);
            updates.put("userRooms/" + m + "/" + code + "/name", n);
        }
        await(db().updateChildren(updates));
    }

    /** Muat turun tetapan kebenaran bilik (RoomPermissions). */
    public static void fetchRoomPermissions(String roomCode, RoomPermissionsCallback cb) {
        try {
            String code = normalizeCode(roomCode);
            if (code.length() < 4) {
                if (cb != null) cb.onLoaded(new RoomPermissions());
                return;
            }
            db().child("rooms").child(code).child("settings").get()
                    .addOnSuccessListener(snap -> {
                        RoomPermissions p = RoomPermissions.fromSnapshot(snap);
                        if (cb != null) cb.onLoaded(p);
                    })
                    .addOnFailureListener(e -> {
                        if (cb != null) cb.onLoaded(new RoomPermissions());
                    });
        } catch (Exception e) {
            if (cb != null) cb.onLoaded(new RoomPermissions());
        }
    }

    /** Dengar perubahan tetapan kebenaran bilik secara masa nyata. */
    public static ValueEventListener listenRoomPermissions(String roomCode, RoomPermissionsCallback cb) {
        try {
            String code = normalizeCode(roomCode);
            if (code.length() < 4) return null;
            ValueEventListener listener = new ValueEventListener() {
                @Override
                public void onDataChange(DataSnapshot snapshot) {
                    if (cb != null) cb.onLoaded(RoomPermissions.fromSnapshot(snapshot));
                }

                @Override
                public void onCancelled(DatabaseError error) {}
            };
            db().child("rooms").child(code).child("settings").addValueEventListener(listener);
            return listener;
        } catch (Exception ignored) {
            return null;
        }
    }

    /** Berhenti mendengar perubahan tetapan bilik. */
    public static void removeRoomPermissionsListener(String roomCode, ValueEventListener listener) {
        try {
            if (listener == null) return;
            String code = normalizeCode(roomCode);
            if (code.length() < 4) return;
            db().child("rooms").child(code).child("settings").removeEventListener(listener);
        } catch (Exception ignored) {}
    }

    /** Simpan atau kemas kini nama bilik dan kebenaran ahli sebagai creator. */
    public static void updateRoomSettingsAndName(String uid, String roomCode, String name, RoomPermissions permissions) throws Exception {
        String u = String.valueOf(uid == null ? "" : uid).trim();
        String code = normalizeCode(roomCode);
        String n = String.valueOf(name == null ? "" : name).trim();
        if (u.isEmpty()) throw new RuntimeException("invalid_uid");
        if (code.length() < 4) throw new RuntimeException("invalid_room_code");
        if (n.isEmpty()) throw new RuntimeException("invalid_name");

        DataSnapshot roleSnap = await(db().child("userRooms").child(u).child(code).child("role").get());
        String role = roleSnap == null ? "" : String.valueOf(roleSnap.getValue());
        if (!"creator".equalsIgnoreCase(String.valueOf(role == null ? "" : role).trim())) {
            throw new RuntimeException("not_creator");
        }

        DataSnapshot membersSnap = await(db().child("rooms").child(code).child("members").get());
        java.util.ArrayList<String> memberUids = new java.util.ArrayList<>();
        if (membersSnap != null && membersSnap.exists()) {
            for (DataSnapshot child : membersSnap.getChildren()) {
                String mid = child.getKey() == null ? "" : child.getKey().trim();
                if (!mid.isEmpty()) memberUids.add(mid);
            }
        }
        if (!memberUids.contains(u)) memberUids.add(u);

        Map<String, Object> updates = new HashMap<>();
        updates.put("rooms/" + code + "/name", n);
        updates.put("rooms/" + code + "/updatedAt", ServerValue.TIMESTAMP);
        if (permissions != null) {
            updates.put("rooms/" + code + "/settings", permissions.toMap());
        }
        for (String mid : memberUids) {
            String m = String.valueOf(mid == null ? "" : mid).trim();
            if (m.isEmpty()) continue;
            updates.put("userRooms/" + m + "/" + code + "/label", n);
            updates.put("userRooms/" + m + "/" + code + "/name", n);
        }
        await(db().updateChildren(updates));
    }

    /** Padam atau bersihkan UserRoom. */
    public static void deleteUserRoom(String uid, String roomCode) throws Exception {
        String u = String.valueOf(uid == null ? "" : uid).trim();
        String code = normalizeCode(roomCode);
        if (u.isEmpty()) throw new RuntimeException("invalid_uid");
        if (code.length() < 4) throw new RuntimeException("invalid_room_code");

        Map<String, Object> updates = new HashMap<>();

        updates.put("userNotifications/" + u + "/room_left_" + System.currentTimeMillis(), new HashMap<String, Object>() {{
            put("id", "room_left_" + System.currentTimeMillis());
            put("title", "Left Room");
            put("message", "You have left room " + code + ".");
            put("createdAt", ServerValue.TIMESTAMP);
        }});
        updates.put("userRooms/" + u + "/" + code, null);

        updates.put("rooms/" + code + "/members/" + u, null);
        await(db().updateChildren(updates));
    }

    public static void kickMemberAsCreatorQueued(String creatorUid, String roomCode, String targetUid) {
        try {
            String code = normalizeCode(roomCode);
            String by = String.valueOf(creatorUid == null ? "" : creatorUid).trim();
            String t = String.valueOf(targetUid == null ? "" : targetUid).trim();
            if (code.length() < 4) return;
            if (by.isEmpty() || t.isEmpty()) return;
            if (by.equals(t)) return;

            Map<String, Object> updates = new HashMap<>();

            Map<String, Object> meta = new HashMap<>();
            meta.put("at", ServerValue.TIMESTAMP);
            meta.put("by", by);
            updates.put("rooms/" + code + "/forceLeave/" + t, meta);

            updates.put("rooms/" + code + "/members/" + t, null);
            updates.put("rooms/" + code + "/bells/" + t, null);
            updates.put("userRooms/" + t + "/" + code, null);
            db().updateChildren(updates);
        } catch (Exception ignored) {
        }
    }

    public static void requestForceLeaveAsCreatorQueued(String uid, String roomCode, String targetUid) {
        String u = String.valueOf(uid == null ? "" : uid).trim();
        String code = normalizeCode(roomCode);
        String t = String.valueOf(targetUid == null ? "" : targetUid).trim();
        if (u.isEmpty()) return;
        if (code.length() < 4) return;
        if (t.isEmpty()) return;
        if (u.equals(t)) return;
        try {
            Map<String, Object> meta = new HashMap<>();
            meta.put("at", ServerValue.TIMESTAMP);
            meta.put("by", u);
            db().child("rooms").child(code).child("forceLeave").child(t).setValue(meta);
        } catch (Exception ignored) {
        }
    }

    public interface ForceLeaveHandler {
        void onForceLeave(String byUid, long atMillis);
    }

    public interface RoomDeletedHandler {
        void onDeleted();
    }

    public static ValueEventListener listenForceLeave(String roomCode, String uid, ForceLeaveHandler handler) {
        try {
            String code = normalizeCode(roomCode);
            String u = String.valueOf(uid == null ? "" : uid).trim();
            if (code.length() < 4) return null;
            if (u.isEmpty()) return null;
            DatabaseReference ref = db().child("rooms").child(code).child("forceLeave").child(u);
            ValueEventListener l = new ValueEventListener() {
                /** Callback apabila data Firebase Realtime Database berubah. */
    @Override
                public void onDataChange(DataSnapshot snapshot) {
                    try {
                        if (snapshot == null || !snapshot.exists()) return;
                        String by = String.valueOf(snapshot.child("by").getValue() == null ? "" : snapshot.child("by").getValue()).trim();
                        Long at = snapshot.child("at").getValue(Long.class);
                        long atMs = at == null ? 0L : at;
                        if (handler != null) handler.onForceLeave(by, atMs);
                    } catch (Exception ignored) {
                    }
                }

                /** Callback sekiranya operasi Firebase dibatalkan atau gagal. */
    @Override
                public void onCancelled(DatabaseError error) {
                }
            };
            ref.addValueEventListener(l);
            return l;
        } catch (Exception ignored) {
            return null;
        }
    }

    /** Padam atau bersihkan ForceLeaveListener. */
    public static void removeForceLeaveListener(String roomCode, String uid, ValueEventListener listener) {
        if (listener == null) return;
        try {
            String code = normalizeCode(roomCode);
            String u = String.valueOf(uid == null ? "" : uid).trim();
            if (code.length() < 4) return;
            if (u.isEmpty()) return;
            db().child("rooms").child(code).child("forceLeave").child(u).removeEventListener(listener);
        } catch (Exception ignored) {
        }
    }

    /** Padam atau bersihkan ForceLeaveRequestQueued. */
    public static void clearForceLeaveRequestQueued(String roomCode, String uid) {
        try {
            String code = normalizeCode(roomCode);
            String u = String.valueOf(uid == null ? "" : uid).trim();
            if (code.length() < 4) return;
            if (u.isEmpty()) return;
            db().child("rooms").child(code).child("forceLeave").child(u).removeValue();
        } catch (Exception ignored) {
        }
    }

    public static ValueEventListener listenRoomDeleted(String roomCode, RoomDeletedHandler handler) {
        try {
            String code = normalizeCode(roomCode);
            if (code.length() < 4) return null;
            DatabaseReference ref = db().child("rooms").child(code).child("creatorUid");
            ValueEventListener l = new ValueEventListener() {
                /** Callback apabila data Firebase Realtime Database berubah. */
    @Override
                public void onDataChange(DataSnapshot snapshot) {
                    try {
                        if (snapshot != null && snapshot.exists() && snapshot.getValue() != null) return;
                        if (handler != null) handler.onDeleted();
                    } catch (Exception ignored) {
                    }
                }

                /** Callback sekiranya operasi Firebase dibatalkan atau gagal. */
    @Override
                public void onCancelled(DatabaseError error) {
                }
            };
            ref.addValueEventListener(l);
            return l;
        } catch (Exception ignored) {
            return null;
        }
    }

    /** Padam atau bersihkan RoomDeletedListener. */
    public static void removeRoomDeletedListener(String roomCode, ValueEventListener listener) {
        if (listener == null) return;
        try {
            String code = normalizeCode(roomCode);
            if (code.length() < 4) return;
            db().child("rooms").child(code).child("creatorUid").removeEventListener(listener);
        } catch (Exception ignored) {
        }
    }

    /** Padam atau bersihkan RoomAsCreator. */
    public static void deleteRoomAsCreator(String uid, String roomCode) throws Exception {
        String u = String.valueOf(uid == null ? "" : uid).trim();
        String code = normalizeCode(roomCode);
        if (u.isEmpty()) throw new RuntimeException("invalid_uid");
        if (code.length() < 4) throw new RuntimeException("invalid_room_code");

        DataSnapshot roleSnap = await(db().child("userRooms").child(u).child(code).child("role").get());
        String role = roleSnap == null ? "" : String.valueOf(roleSnap.getValue());
        if (!"creator".equalsIgnoreCase(String.valueOf(role == null ? "" : role).trim())) {
            throw new RuntimeException("not_creator");
        }

        Map<String, Object> updates = new HashMap<>();
        Map<String, Object> tomb = new HashMap<>();
        tomb.put("deletedAt", ServerValue.TIMESTAMP);
        tomb.put("by", u);
        updates.put("roomTombstones/" + code, tomb);
        updates.put("rooms/" + code, null);

        updates.put("userNotifications/" + u + "/room_deleted_" + System.currentTimeMillis(), new HashMap<String, Object>() {{
            put("id", "room_deleted_" + System.currentTimeMillis());
            put("title", "Room Deleted");
            put("message", "You have deleted room " + code + ".");
            put("createdAt", ServerValue.TIMESTAMP);
        }});
        updates.put("userRooms/" + u + "/" + code, null);

        try {
            DataSnapshot membersSnap = await(db().child("rooms").child(code).child("members").get());
            if (membersSnap != null && membersSnap.exists()) {
                for (DataSnapshot child : membersSnap.getChildren()) {
                    String mid = child.getKey() == null ? "" : child.getKey().trim();
                    if (mid.isEmpty()) continue;
                    updates.put("userRooms/" + mid + "/" + code, null);
                }
            }
        } catch (Exception ignored) {

        }

        await(db().updateChildren(updates));
    }

    /** Padam atau bersihkan semua data akaun pengguna daripada Database Realtime secara menyeluruh. */
    public static void deleteAccountData(String uid) throws Exception {
        String u = String.valueOf(uid == null ? "" : uid).trim();
        if (u.isEmpty()) throw new RuntimeException("invalid_uid");

        // Kumpul email & publicId SEBELUM padam data users (perlukan baca dulu)
        String sanitizedEmail = null;
        String publicIdKey = null;

        try {
            DataSnapshot emailSnap = await(db().child("users").child(u).child("email").get());
            if (emailSnap != null && emailSnap.exists()) {
                String em = String.valueOf(emailSnap.getValue());
                if (!em.trim().isEmpty()) {
                    sanitizedEmail = em.trim().toLowerCase(java.util.Locale.ROOT)
                            .replace(".", "_")
                            .replace("@", "_at_");
                }
            }
        } catch (Exception ignored) {}

        try {
            com.google.firebase.auth.FirebaseUser currentAuth = com.google.firebase.auth.FirebaseAuth.getInstance().getCurrentUser();
            if (sanitizedEmail == null && currentAuth != null && currentAuth.getEmail() != null && !currentAuth.getEmail().trim().isEmpty()) {
                sanitizedEmail = currentAuth.getEmail().trim().toLowerCase(java.util.Locale.ROOT)
                        .replace(".", "_")
                        .replace("@", "_at_");
            }
        } catch (Exception ignored) {}

        try {
            DataSnapshot publicIdSnap = await(db().child("users").child(u).child("publicId").get());
            if (publicIdSnap != null && publicIdSnap.exists()) {
                String pid = String.valueOf(publicIdSnap.getValue());
                if (!pid.trim().isEmpty()) {
                    publicIdKey = pid.trim().toUpperCase(java.util.Locale.ROOT);
                }
            }
        } catch (Exception ignored) {}

        // Kumpul maklumat bilik & kawan SEBELUM padam
        Map<String, String> roomRoles = new HashMap<>();
        List<String> friendUids = new ArrayList<>();

        try {
            DataSnapshot roomsSnap = await(db().child("userRooms").child(u).get());
            if (roomsSnap != null && roomsSnap.exists()) {
                for (DataSnapshot child : roomsSnap.getChildren()) {
                    String code = child.getKey() == null ? "" : child.getKey().trim();
                    if (code.isEmpty()) continue;
                    String role = String.valueOf(child.child("role").getValue() == null ? "" : child.child("role").getValue());
                    roomRoles.put(code, role);
                }
            }
        } catch (Exception ignored) {}

        try {
            DataSnapshot friendsSnap = await(db().child("userFriends").child(u).get());
            if (friendsSnap != null && friendsSnap.exists()) {
                for (DataSnapshot friendChild : friendsSnap.getChildren()) {
                    String fUid = friendChild.getKey() == null ? "" : friendChild.getKey().trim();
                    if (!fUid.isEmpty()) friendUids.add(fUid);
                }
            }
        } catch (Exception ignored) {}

        try {
            android.content.Context ctx = com.example.resqtap.app.ResQTapApp.getInstance();
            if (ctx != null) {
                String activeCode = String.valueOf(com.example.resqtap.utils.UserPrefs.getActiveRoomCode(ctx) == null ? "" : com.example.resqtap.utils.UserPrefs.getActiveRoomCode(ctx)).trim().toUpperCase(java.util.Locale.ROOT);
                if (!activeCode.isEmpty() && !roomRoles.containsKey(activeCode)) {
                    roomRoles.put(activeCode, "joiner");
                }
            }
        } catch (Exception ignored) {}

        // 1. Bilik-bilik: creator padam room, member buang diri SEBELUM userRooms dipadam
        for (Map.Entry<String, String> entry : roomRoles.entrySet()) {
            String code = entry.getKey();
            String role = entry.getValue();
            try {
                if ("creator".equalsIgnoreCase(role)) {
                    // Buang userRooms ahli lain dulu
                    try {
                        DataSnapshot membersSnap = await(db().child("rooms").child(code).child("members").get());
                        if (membersSnap != null && membersSnap.exists()) {
                            for (DataSnapshot mem : membersSnap.getChildren()) {
                                String mid = mem.getKey() == null ? "" : mem.getKey().trim();
                                if (!mid.isEmpty() && !mid.equals(u)) {
                                    try { await(db().child("userRooms").child(mid).child(code).removeValue()); } catch (Exception ignored) {}
                                }
                            }
                        }
                    } catch (Exception ignored) {}
                    // Padam room & set tombstone
                    try { await(db().child("rooms").child(code).removeValue()); } catch (Exception ignored) {}
                    try { await(db().child("roomTombstones").child(code).setValue(true)); } catch (Exception ignored) {}
                } else {
                    // Buang diri dari room
                    try { await(db().child("rooms").child(code).child("members").child(u).removeValue()); } catch (Exception ignored) {}
                    try { await(db().child("rooms").child(code).child("bells").child(u).removeValue()); } catch (Exception ignored) {}
                    try { await(db().child("rooms").child(code).child("forceLeave").child(u).removeValue()); } catch (Exception ignored) {}
                }
            } catch (Exception ignored) {}
        }

        // 2. Buang diri dari senarai kawan orang lain
        for (String fUid : friendUids) {
            try { await(db().child("userFriends").child(fUid).child(u).removeValue()); } catch (Exception ignored) {}
            try { await(db().child("friendRequests").child(fUid).child(u).removeValue()); } catch (Exception ignored) {}
            try { await(db().child("sentRequests").child(fUid).child(u).removeValue()); } catch (Exception ignored) {}
        }

        // 3. Padam registeredEmails & publicIds (auth != null write rules)
        if (sanitizedEmail != null) {
            try { await(db().child("registeredEmails").child(sanitizedEmail).removeValue()); } catch (Exception ignored) {}
        }
        if (publicIdKey != null) {
            try { await(db().child("publicIds").child(publicIdKey).removeValue()); } catch (Exception ignored) {}
        }

        // 4. Padam data milik sendiri (user-level write rules: auth.uid === $uid)
        String[] ownedPaths = {
            "users/" + u,
            "admins/" + u,
            "supportChats/" + u,
            "aiChats/" + u,
            "userNotifications/" + u,
            "notifications/" + u,
            "sos_history/" + u,
            "sos_alerts/" + u,
            "sos_status/" + u,
            "live_locations/" + u,
            "userFriends/" + u,
            "friendRequests/" + u,
            "sentRequests/" + u,
            "userRooms/" + u
        };
        for (String path : ownedPaths) {
            try { await(db().child(path).removeValue()); } catch (Exception ignored) {}
        }
    }

    /**
     * Bersihkan semua akaun pengguna dalam Realtime Database kecuali akaun @resqtap.
     */
    public static void cleanNonResQTapUsersFromRTDB() {
        // Disabled: one-time administrative cleanup has completed.
    }

    public interface SendMessageCallback {
        void onResult(boolean ok, String error);
    }

    /** Hantar mesej dalam perbualan bilik (Room Chat) */
    public static void sendRoomMessage(String roomCode, String senderUid, String senderName, String senderPhotoUrl, String text, SendMessageCallback cb) {
        String code = normalizeCode(roomCode);
        String uid = String.valueOf(senderUid == null ? "" : senderUid).trim();
        String msg = String.valueOf(text == null ? "" : text).trim();
        if (code.length() < 4 || uid.isEmpty() || msg.isEmpty()) {
            if (cb != null) cb.onResult(false, "invalid_params");
            return;
        }

        try {
            DatabaseReference chatRef = db().child("rooms").child(code).child("messages").push();
            Map<String, Object> payload = new HashMap<>();
            payload.put("id", chatRef.getKey());
            payload.put("senderUid", uid);
            payload.put("senderName", senderName == null || senderName.trim().isEmpty() ? "User" : senderName.trim());
            payload.put("senderPhotoUrl", senderPhotoUrl == null ? "" : senderPhotoUrl.trim());
            payload.put("text", msg);
            payload.put("timestamp", ServerValue.TIMESTAMP);

            chatRef.setValue(payload).addOnCompleteListener(task -> {
                if (cb != null) {
                    cb.onResult(task.isSuccessful(), task.isSuccessful() ? "" : (task.getException() != null ? task.getException().getMessage() : "failed"));
                }
            });
        } catch (Exception e) {
            if (cb != null) cb.onResult(false, e.getMessage());
        }
    }

    /** Dengar senarai mesej dalam Room Chat secara realtime */
    public static ValueEventListener listenRoomMessages(String roomCode, RoomMessagesCallback callback) {
        String code = normalizeCode(roomCode);
        if (code.length() < 4 || callback == null) return null;

        Query query = db().child("rooms").child(code).child("messages").orderByChild("timestamp").limitToLast(100);
        ValueEventListener listener = new ValueEventListener() {
            @Override
            public void onDataChange(DataSnapshot snapshot) {
                ArrayList<RoomMessage> list = new ArrayList<>();
                if (snapshot.exists()) {
                    for (DataSnapshot child : snapshot.getChildren()) {
                        try {
                            String id = child.getKey();
                            String senderUid = child.child("senderUid").getValue(String.class);
                            String senderName = child.child("senderName").getValue(String.class);
                            String senderPhoto = child.child("senderPhotoUrl").getValue(String.class);
                            String text = child.child("text").getValue(String.class);
                            Long ts = child.child("timestamp").getValue(Long.class);
                            long timestamp = ts == null ? 0L : ts;
                            if (text != null && !text.isEmpty()) {
                                list.add(new RoomMessage(id, senderUid, senderName, senderPhoto, text, timestamp));
                            }
                        } catch (Exception ignored) {
                        }
                    }
                }
                callback.onMessagesUpdated(list);
            }

            @Override
            public void onCancelled(DatabaseError error) {
            }
        };

        query.addValueEventListener(listener);
        return listener;
    }

    /** Berhenti dengar mesej bilik */
    public static void stopListeningRoomMessages(String roomCode, ValueEventListener listener) {
        if (listener == null) return;
        String code = normalizeCode(roomCode);
        if (code.length() < 4) return;
        try {
            db().child("rooms").child(code).child("messages").removeEventListener(listener);
        } catch (Exception ignored) {
        }
    }
}