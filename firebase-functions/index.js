/**
 * ResQTap Backend Cloud Functions (asia-southeast1)
 * Mengendalikan proksi AI Chat, pemicu notifikasi tolak FCM (SOS / Bell), dan pembersihan data tamat tempoh.
 */
const functions = require("firebase-functions");
const admin = require("firebase-admin");

admin.initializeApp();

const rtdb = functions.region("asia-southeast1").database;
const callable = functions.region("asia-southeast1").https;
const AI_CHAT_EXPIRY_BUFFER_MS = 0;

function isAdminValue(value) {
  return value === true || (value && typeof value === "object" && value.active === true);
}

async function assertActiveAdmin(context) {
  const uid = context.auth && context.auth.uid;
  if (!uid) {
    throw new functions.https.HttpsError("unauthenticated", "Sign in to send notifications.");
  }

  await assertActiveAdminUid(uid);
  return uid;
}

async function assertActiveAdminUid(uid) {
  if (!uid) {
    throw new functions.https.HttpsError("unauthenticated", "Sign in to send notifications.");
  }

  const snap = await admin.database().ref(`/admins/${uid}`).get();
  if (!isAdminValue(snap.val())) {
    throw new functions.https.HttpsError("permission-denied", "Admin access is required.");
  }
  return uid;
}

function normalizeText(value, maxLength) {
  return String(value || "").trim().slice(0, maxLength);
}

async function cleanupExpiredAiChatsJob() {
  const now = Date.now();
  const updates = {};
  const aiSnap = await admin.database().ref("/aiChats").get();
  if (aiSnap.exists()) {
    aiSnap.forEach((child) => {
      const expiresAt = Number(child.child("meta/expiresAt").val() || 0);
      if (expiresAt > 0 && expiresAt + AI_CHAT_EXPIRY_BUFFER_MS <= now) {
        updates[`/aiChats/${child.key}`] = null;
      }
    });
  }
  const supportSnap = await admin.database().ref("/supportChats").get();
  if (supportSnap.exists()) {
    supportSnap.forEach((child) => {
      const expiresAt = Number(child.child("aiAssistant/meta/expiresAt").val() || 0);
      if (expiresAt > 0 && expiresAt + AI_CHAT_EXPIRY_BUFFER_MS <= now) {
        updates[`/supportChats/${child.key}/aiAssistant`] = null;
      }
    });
  }

  const count = Object.keys(updates).length;
  if (count > 0) {
    await admin.database().ref().update(updates);
  }
  return count;
}

exports.cleanupExpiredAiChats = functions
  .region("asia-southeast1")
  .pubsub
  .schedule("every 1 minutes")
  .onRun(async () => {
    const count = await cleanupExpiredAiChatsJob();
    console.log(`Cleaned ${count} expired AI chat thread(s).`);
    return null;
  });

async function getAudienceUids(audience) {
  const usersSnap = await admin.database().ref("/users").get();
  const users = usersSnap.exists() ? usersSnap.val() || {} : {};
  const allUids = Object.keys(users).filter((uid) => String(uid || "").trim());

  if (audience === "admins") {
    const adminsSnap = await admin.database().ref("/admins").get();
    const admins = adminsSnap.exists() ? adminsSnap.val() || {} : {};
    return allUids.filter((uid) => isAdminValue(admins[uid]));
  }

  if (audience === "live") {
    const cutoff = Date.now() - 2 * 60 * 1000;
    const liveUids = new Set();
    const roomsSnap = await admin.database().ref("/rooms").get();
    const rooms = roomsSnap.exists() ? roomsSnap.val() || {} : {};
    Object.keys(rooms).forEach((roomId) => {
      const members = (rooms[roomId] && rooms[roomId].members) || {};
      Object.keys(members).forEach((uid) => {
        const updatedAt = Number((members[uid] && members[uid].updatedAt) || 0);
        if (updatedAt >= cutoff) liveUids.add(uid);
      });
    });
    return allUids.filter((uid) => liveUids.has(uid));
  }

  return allUids;
}

exports.onBellCreated = rtdb
  .ref("/rooms/{code}/bells/{toUid}/{bellId}")
  .onCreate(async (snap, context) => {
    const { code, toUid } = context.params;
    const payload = snap.val() || {};
    const fromUid = String(payload.fromUid || "");

    const tokens = [];
    const tokensSnap = await admin.database().ref(`/users/${toUid}/fcmTokens`).get();
    if (tokensSnap.exists()) {
      const v = tokensSnap.val() || {};
      Object.keys(v).forEach((k) => {
        const t = String(v[k] || "").trim();
        if (t) tokens.push(t);
      });
    }
    if (tokens.length === 0) {
      const tokenSnap = await admin.database().ref(`/users/${toUid}/fcmToken`).get();
      const token = tokenSnap.exists() ? String(tokenSnap.val() || "").trim() : "";
      if (token) tokens.push(token);
    }
    const uniqueTokens = Array.from(new Set(tokens));
    if (uniqueTokens.length === 0) return null;

    let fromName = "";
    if (fromUid) {
      const nameSnap = await admin.database().ref(`/users/${fromUid}/name`).get();
      fromName = nameSnap.exists() ? String(nameSnap.val() || "") : "";
    }

    const data = {
      type: "bell",
      room: String(code || ""),
      fromUid,
      fromName,

      sentAt: String(Date.now())
    };

    await Promise.allSettled(
      uniqueTokens.map((token) =>
        admin.messaging().send({
          token,
          data,

          notification: {
            title: "Bell",
            body: fromName ? `${fromName} pinged you.` : "Someone pinged you."
          },
          android: {
            priority: "high",
            ttl: 60 * 1000,
            notification: {
              channelId: "bell_alerts_v2",
              sound: "default"
            }
          }
        })
      )
    );
    return null;
  });

exports.onSosAlertCreated = rtdb
  .ref("/sos_alerts/{alertId}")
  .onCreate(async (snap, context) => {
    const payload = snap.val() || {};
    const alertId = String((context.params || {}).alertId || "");
    const toUid = String(payload.toUid || "");
    const fromUid = String(payload.fromUid || "");
    const roomCode = String(payload.roomCode || "");
    const type = String(payload.type || "sos");

    if (!toUid) return null;

    const tokens = [];
    const tokensSnap = await admin.database().ref(`/users/${toUid}/fcmTokens`).get();
    if (tokensSnap.exists()) {
      const v = tokensSnap.val() || {};
      Object.keys(v).forEach((k) => {
        const t = String(v[k] || "").trim();
        if (t) tokens.push(t);
      });
    }
    if (tokens.length === 0) {
      const tokenSnap = await admin.database().ref(`/users/${toUid}/fcmToken`).get();
      const token = tokenSnap.exists() ? String(tokenSnap.val() || "").trim() : "";
      if (token) tokens.push(token);
    }
    const uniqueTokens = Array.from(new Set(tokens));
    if (uniqueTokens.length === 0) return null;

    let fromName = "";
    if (fromUid) {
      const nameSnap = await admin.database().ref(`/users/${fromUid}/name`).get();
      fromName = nameSnap.exists() ? String(nameSnap.val() || "") : "";
    }

    const title = type.toLowerCase() === "bell" ? "ROOM" : "SOS";
    const body = fromName ? `${fromName} pinged you.` : "Emergency alert.";

    const data = {
      type,
      alertId,
      room: roomCode,
      fromUid,
      fromName,
      sentAt: String(Date.now())
    };

    await Promise.allSettled(
      uniqueTokens.map((token) =>
        admin.messaging().send({
          token,
          data,
          notification: { title, body },
          android: {
            priority: "high",
            ttl: 60 * 1000,
            notification: {
              channelId: "bell_alerts_v2",
              sound: "default"
            }
          }
        })
      )
    );
    return null;
  });

async function getUserTokens(uid) {
  const tokens = [];
  if (!uid) return tokens;

  const tokensSnap = await admin.database().ref(`/users/${uid}/fcmTokens`).get();
  if (tokensSnap.exists()) {
    const v = tokensSnap.val() || {};
    Object.keys(v).forEach((k) => {
      const t = String(v[k] || "").trim();
      if (t) tokens.push(t);
    });
  }
  if (tokens.length === 0) {
    const tokenSnap = await admin.database().ref(`/users/${uid}/fcmToken`).get();
    const token = tokenSnap.exists() ? String(tokenSnap.val() || "").trim() : "";
    if (token) tokens.push(token);
  }
  return Array.from(new Set(tokens));
}

async function sendAdminNotificationJob(data, adminUid, historyId) {
  const audience = ["all", "live", "admins"].includes(String(data && data.audience || "all"))
    ? String(data && data.audience || "all")
    : "all";
  const title = normalizeText(data && data.title, 60) || "ResQTap";
  const message = normalizeText(data && data.message, 180);

  if (!message) {
    throw new functions.https.HttpsError("invalid-argument", "Notification message is required.");
  }

  const targetUids = await getAudienceUids(audience);
  const tokenOwnerPairs = [];
  for (const uid of targetUids) {
    const tokens = await getUserTokens(uid);
    tokens.forEach((token) => tokenOwnerPairs.push({ uid, token }));
  }

  const uniqueByToken = new Map();
  tokenOwnerPairs.forEach((item) => {
    if (!uniqueByToken.has(item.token)) uniqueByToken.set(item.token, item.uid);
  });
  const tokens = Array.from(uniqueByToken.keys());
  const sentAt = Date.now();
  const historyRef = historyId
    ? admin.database().ref(`/admin_notifications/${historyId}`)
    : admin.database().ref("/admin_notifications").push();
  const notificationId = historyRef.key;

  if (!tokens.length) {
    await historyRef.set({
      audience,
      title,
      message,
      targetUserCount: targetUids.length,
      tokenCount: 0,
      successCount: 0,
      failureCount: 0,
      sentAt,
      sentBy: adminUid,
      status: "no_tokens"
    });
    return {
      id: notificationId,
      targetUserCount: targetUids.length,
      tokenCount: 0,
      successCount: 0,
      failureCount: 0
    };
  }

  const payload = {
    data: {
      type: "ADMIN_NOTIFICATION",
      notificationId: String(notificationId || ""),
      audience,
      title,
      message,
      sentAt: String(sentAt)
    },
    notification: {
      title,
      body: message
    },
    android: {
      priority: "high",
      ttl: 60 * 60 * 1000,
      notification: {
        sound: "default"
      }
    }
  };

  const results = await Promise.allSettled(
    tokens.map((token) => admin.messaging().send({ token, ...payload }))
  );
  const successCount = results.filter((result) => result.status === "fulfilled").length;
  const failureCount = results.length - successCount;
  const failureReasons = results
    .filter((result) => result.status === "rejected")
    .slice(0, 5)
    .map((result) => {
      const reason = result.reason || {};
      return String(reason.code || reason.message || reason);
    });

  await historyRef.set({
    audience,
    title,
    message,
    targetUserCount: targetUids.length,
    tokenCount: tokens.length,
    successCount,
    failureCount,
    failureReasons,
    sentAt,
    sentBy: adminUid,
    status: failureCount ? "partial" : "sent"
  });

  return {
    id: notificationId,
    targetUserCount: targetUids.length,
    tokenCount: tokens.length,
    successCount,
    failureCount,
    failureReasons
  };
}

exports.sendAdminNotification = callable.onCall(async (data, context) => {
  const adminUid = await assertActiveAdmin(context);
  return sendAdminNotificationJob(data, adminUid);
});

exports.onAdminNotificationRequestCreated = rtdb
  .ref("/admin_notification_requests/{requestId}")
  .onCreate(async (snap, context) => {
    const requestId = String((context.params || {}).requestId || "");
    const data = snap.val() || {};
    const adminUid = normalizeText(data.sentBy, 160);

    try {
      await assertActiveAdminUid(adminUid);
      const result = await sendAdminNotificationJob(data, adminUid, requestId);
      await snap.ref.update({
        status: "sent",
        processedAt: admin.database.ServerValue.TIMESTAMP,
        result
      });
      return null;
    } catch (error) {
      await snap.ref.update({
        status: "failed",
        processedAt: admin.database.ServerValue.TIMESTAMP,
        error: error && error.message ? String(error.message) : "Notification request failed."
      });
      throw error;
    }
});

async function getRoomMemberUids(roomId) {
  if (!roomId) return [];
  const snap = await admin.database().ref(`/rooms/${roomId}/members`).get();
  if (!snap.exists()) return [];
  const v = snap.val() || {};
  return Object.keys(v).filter((uid) => String(uid || "").trim());
}

async function sendSosToRoomMembers({
  roomId,
  senderUid,
  senderName,
  alertId,
  createdAt,
  type
}) {
  const memberUids = await getRoomMemberUids(roomId);
  const targets = memberUids.filter((u) => u && u !== senderUid);
  if (targets.length === 0) return null;

  const allTokens = [];
  for (const uid of targets) {
    const toks = await getUserTokens(uid);
    toks.forEach((t) => allTokens.push(t));
  }
  const uniqueTokens = Array.from(new Set(allTokens));
  if (uniqueTokens.length === 0) return null;

  const data = {
    type: String(type || "SOS_ALERT"),
    roomId: String(roomId || ""),
    alertId: String(alertId || ""),
    senderUid: String(senderUid || ""),
    senderName: String(senderName || ""),
    createdAt: String(createdAt || 0),
    sentAt: String(Date.now())
  };

  console.log("SOS_DEBUG Sending alert to roomId:", roomId, "type:", type, "tokens:", uniqueTokens.length);

  await Promise.allSettled(
    uniqueTokens.map((token) =>
      admin.messaging().send({
        token,
        data,
        android: {
          priority: "high",
          ttl: 60 * 1000
        }
      })
    )
  );
  return null;
}

exports.onRoomSosCreated = rtdb
  .ref("/rooms/{roomId}/sosAlerts/{alertId}")
  .onCreate(async (snap, context) => {
    const roomId = String((context.params || {}).roomId || "");
    const payload = snap.val() || {};

    const status = String(payload.status || "active");
    if (status.toLowerCase() !== "active") return null;

    const senderUid = String(payload.senderUid || payload.fromUid || "");
    const senderName = String(payload.senderName || payload.fromName || "");
    const createdAt = Number(payload.createdAt || payload.at || Date.now());
    const alertId = String(payload.alertId || (context.params || {}).alertId || "");

    return sendSosToRoomMembers({
      roomId,
      senderUid,
      senderName,
      alertId,
      createdAt,
      type: "SOS_ALERT"
    });
  });

exports.onRoomSosUpdated = rtdb
  .ref("/rooms/{roomId}/sosAlerts/{alertId}")
  .onUpdate(async (change, context) => {
    const roomId = String((context.params || {}).roomId || "");
    const before = change.before.val() || {};
    const after = change.after.val() || {};

    const beforeStatus = String(before.status || "active").toLowerCase();
    const afterStatus = String(after.status || "active").toLowerCase();

    if (beforeStatus === afterStatus) return null;
    if (afterStatus !== "cancelled") return null;

    const senderUid = String(after.senderUid || after.fromUid || "");
    const senderName = String(after.senderName || after.fromName || "");
    const cancelledAt = Number(after.cancelledAt || Date.now());
    const alertId = String(after.alertId || (context.params || {}).alertId || "");

    return sendSosToRoomMembers({
      roomId,
      senderUid,
      senderName,
      alertId,
      createdAt: cancelledAt,
      type: "SOS_CANCELLED"
    });
  });

/**
 * Auto-assign Admin Role for @resqtap.com accounts (Verified Email Only)
 * Memastikan peranan admin HANYA diberikan sekiranya e-mel telah disahkan secara sah.
 */
exports.onUserCreated = functions.region("asia-southeast1").auth.user().onCreate(async (user) => {
  try {
    const email = (user.email || "").trim().toLowerCase();
    const isVerified = user.emailVerified === true;

    if (email.endsWith("@resqtap.com")) {
      const uid = user.uid;
      const adminRef = admin.database().ref(`/admins/${uid}`);

      if (isVerified) {
        await adminRef.set({
          active: true,
          email: email,
          name: user.displayName || "",
          role: "admin",
          assignedAt: admin.database.ServerValue.TIMESTAMP,
          assignedBy: "system_verified_resqtap_domain"
        });
        console.log(`[AUTO-ADMIN] Assigned verified admin privileges to ${email} (${uid})`);
      } else {
        console.warn(`[SECURITY] Account ${email} (${uid}) registered with @resqtap.com domain but email is unverified. Admin privileges withheld.`);
      }
    }
  } catch (err) {
    console.error("[AUTO-ADMIN] Error handling admin role assignment:", err);
  }
});


/**
 * Handle Admin User Deletion Request
 * Memadam akaun pengguna sepenuhnya daripada Firebase Authentication dan RTDB.
 */
exports.onAdminUserDeletionRequest = rtdb
  .ref("/admin_user_deletions/{uid}")
  .onCreate(async (snap, context) => {
    const uid = String((context.params || {}).uid || "").trim();
    if (!uid) return null;

    const payload = snap.val() || {};
    const adminUid = String(payload.deletedBy || "").trim();

    try {
      if (!adminUid) {
        console.warn(`[ADMIN-DELETE] Rejected user deletion request for ${uid}: missing deletedBy admin UID.`);
        await snap.ref.remove();
        return null;
      }
      await assertActiveAdminUid(adminUid);

      // Padam akaun daripada Firebase Authentication
      try {
        await admin.auth().deleteUser(uid);
        console.log(`[ADMIN-DELETE] Successfully deleted user ${uid} from Firebase Auth.`);
      } catch (authErr) {
        console.log(`[ADMIN-DELETE] Auth user ${uid} not found or already deleted:`, authErr && authErr.message);
      }

      // Padam rekod pangkalan data pengguna secara menyeluruh
      const updates = {};
      updates[`/users/${uid}`] = null;
      updates[`/userRooms/${uid}`] = null;
      updates[`/admins/${uid}`] = null;
      updates[`/supportChats/${uid}`] = null;
      updates[`/aiChats/${uid}`] = null;
      updates[`/admin_user_deletions/${uid}`] = null;

      await admin.database().ref().update(updates);
      console.log(`[ADMIN-DELETE] User ${uid} fully purged.`);
      return null;
    } catch (error) {
      console.error(`[ADMIN-DELETE] Failed to process user deletion for ${uid}:`, error);
      await snap.ref.update({
        status: "failed",
        error: String(error && error.message || error)
      });
      return null;
    }
  });

