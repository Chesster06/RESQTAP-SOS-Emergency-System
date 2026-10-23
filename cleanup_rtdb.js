const https = require("https");
const fs = require("fs");
const path = require("path");

let admin;
try {
  admin = require("firebase-admin");
} catch (e) {
  admin = require("./firebase-functions/node_modules/firebase-admin");
}

// Supabase REST Configuration for full database resets
const SUPABASE_URL = "https://umcxapvojtoxqlpnrwdw.supabase.co";
const SUPABASE_ANON_KEY = "eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9.eyJpc3MiOiJzdXBhYmFzZSIsInJlZiI6InVtY3hhcHZvanRveHFscG5yd2R3Iiwicm9sZSI6ImFub24iLCJpYXQiOjE3OTEzMTg2OTksImV4cCI6MjEwNjg5NDY5OX0.8qDJYad_rTwlzAqmF0Z1OiRgQ9ocxaluN7MIjz0mL0M";

async function cleanupSupabaseUsers(uidsToDelete) {
  try {
    const headers = {
      apikey: SUPABASE_ANON_KEY,
      Authorization: `Bearer ${SUPABASE_ANON_KEY}`,
      "Content-Type": "application/json"
    };

    if (uidsToDelete && uidsToDelete.size > 0) {
      console.log(`[SUPABASE] Memadam ${uidsToDelete.size} rekod pengguna daripada Supabase...`);
      for (const uid of uidsToDelete) {
        await Promise.allSettled([
          fetch(`${SUPABASE_URL}/rest/v1/profiles?id=eq.${encodeURIComponent(uid)}`, { method: "DELETE", headers }),
          fetch(`${SUPABASE_URL}/rest/v1/medical_cards?user_id=eq.${encodeURIComponent(uid)}`, { method: "DELETE", headers }),
          fetch(`${SUPABASE_URL}/rest/v1/user_notifications?user_uid=eq.${encodeURIComponent(uid)}`, { method: "DELETE", headers }),
          fetch(`${SUPABASE_URL}/rest/v1/reports?user_id=eq.${encodeURIComponent(uid)}`, { method: "DELETE", headers })
        ]);
      }
    } else {
      console.log("[SUPABASE] Membersihkan semua data pengguna bukan induk dalam Supabase...");
      await Promise.allSettled([
        fetch(`${SUPABASE_URL}/rest/v1/profiles?id=neq.dummy_never_match`, { method: "DELETE", headers }),
        fetch(`${SUPABASE_URL}/rest/v1/medical_cards?user_id=neq.dummy_never_match`, { method: "DELETE", headers }),
        fetch(`${SUPABASE_URL}/rest/v1/user_notifications?id=neq.dummy_never_match`, { method: "DELETE", headers }),
        fetch(`${SUPABASE_URL}/rest/v1/room_tombstones?room_code=neq.dummy_never_match`, { method: "DELETE", headers }),
        fetch(`${SUPABASE_URL}/rest/v1/system_calls?id=neq.dummy_never_match`, { method: "DELETE", headers }),
        fetch(`${SUPABASE_URL}/rest/v1/reports?id=neq.00000000-0000-0000-0000-000000000000`, { method: "DELETE", headers }),
        fetch(`${SUPABASE_URL}/rest/v1/system_metadata?key=eq.counters`, {
          method: "PATCH",
          headers,
          body: JSON.stringify({ value: { user_public_id: 0 } })
        })
      ]);
    }
    console.log("[SUPABASE] Pembersihan rekod Supabase selesai dengan jaya.");
  } catch (err) {
    console.warn("[SUPABASE] Ralat membersihkan data Supabase:", err.message);
  }
}

// Load optional local environment variables from .env if present
const envPath = path.join(__dirname, ".env");
if (fs.existsSync(envPath)) {
  try {
    const envContent = fs.readFileSync(envPath, "utf8");
    envContent.split(/\r?\n/).forEach(line => {
      const trimmed = line.trim();
      if (!trimmed || trimmed.startsWith("#")) return;
      const idx = trimmed.indexOf("=");
      if (idx > 0) {
        const key = trimmed.substring(0, idx).trim();
        const val = trimmed.substring(idx + 1).trim().replace(/^['"]|['"]$/g, "");
        if (!process.env[key]) process.env[key] = val;
      }
    });
  } catch (ignored) {}
}

const FIREBASE_TOOLS_CONFIG = process.env.FIREBASE_TOOLS_CONFIG || (
  process.env.USERPROFILE 
    ? path.join(process.env.USERPROFILE, ".config", "configstore", "firebase-tools.json")
    : "C:\\Users\\Administrator\\.config\\configstore\\firebase-tools.json"
);
const OAUTH_CLIENT_ID = process.env.FIREBASE_OAUTH_CLIENT_ID || "563584335869-fgrhgmd47bqnekij5i8b5pr03ho849e6.apps.googleusercontent.com";
const OAUTH_CLIENT_SECRET = process.env.FIREBASE_OAUTH_CLIENT_SECRET || "";


async function getValidAccessToken() {
  if (!fs.existsSync(FIREBASE_TOOLS_CONFIG)) {
    throw new Error(`Firebase tools config not found at ${FIREBASE_TOOLS_CONFIG}`);
  }

  const raw = fs.readFileSync(FIREBASE_TOOLS_CONFIG, "utf8");
  const config = JSON.parse(raw);
  const tokens = config.tokens || {};

  const now = Date.now();
  const expiresAt = Number(tokens.expires_at || 0);

  // If token has at least 3 minutes left, use it directly
  if (tokens.access_token && expiresAt > now + 3 * 60 * 1000) {
    return tokens.access_token;
  }

  if (!tokens.refresh_token) {
    throw new Error("No refresh_token found in firebase-tools config.");
  }

  // Refresh token via Google OAuth endpoint
  const postData = new URLSearchParams({
    client_id: OAUTH_CLIENT_ID,
    client_secret: OAUTH_CLIENT_SECRET,
    grant_type: "refresh_token",
    refresh_token: tokens.refresh_token
  }).toString();

  const refreshed = await new Promise((resolve, reject) => {
    const req = https.request({
      hostname: "oauth2.googleapis.com",
      path: "/token",
      method: "POST",
      headers: {
        "Content-Type": "application/x-www-form-urlencoded",
        "Content-Length": Buffer.byteLength(postData)
      }
    }, (res) => {
      let b = "";
      res.on("data", c => b += c);
      res.on("end", () => {
        try {
          const json = JSON.parse(b);
          if (res.statusCode >= 200 && res.statusCode < 300 && json.access_token) {
            resolve(json);
          } else {
            reject(new Error(`Failed to refresh token (${res.statusCode}): ${b}`));
          }
        } catch (e) {
          reject(e);
        }
      });
    });
    req.on("error", reject);
    req.write(postData);
    req.end();
  });

  // Update in-memory & file cache
  tokens.access_token = refreshed.access_token;
  tokens.expires_at = now + (Number(refreshed.expires_in || 3600) * 1000);
  config.tokens = tokens;
  try {
    fs.writeFileSync(FIREBASE_TOOLS_CONFIG, JSON.stringify(config, null, 2), "utf8");
  } catch (err) {
    console.warn("Could not save refreshed token back to firebase-tools.json:", err.message);
  }

  return refreshed.access_token;
}

let appInitialized = false;
async function getAdmin() {
  if (!appInitialized) {
    const saPath = process.env.FIREBASE_SERVICE_ACCOUNT_PATH || process.env.GOOGLE_APPLICATION_CREDENTIALS;
    if (!admin.apps || admin.apps.length === 0) {
      if (saPath && fs.existsSync(saPath)) {
        console.log(`[ADMIN-INIT] Using service account JSON from environment: ${saPath}`);
        admin.initializeApp({
          credential: admin.credential.cert(require(path.resolve(saPath))),
          databaseURL: "https://resqtap-b9ff5-default-rtdb.firebaseio.com"
        });
      } else {
        const token = await getValidAccessToken();
        admin.initializeApp({
          credential: {
            getAccessToken: async () => ({
              access_token: await getValidAccessToken(),
              expires_in: 3600
            })
          },
          projectId: "resqtap-b9ff5",
          databaseURL: "https://resqtap-b9ff5-default-rtdb.firebaseio.com"
        });
      }
    }
    appInitialized = true;
  }
  return admin;
}

async function resetDatabaseAccounts() {
  const startTime = Date.now();
  console.log("==================================================");
  console.log("   RESET DATABASE & AUTH ACCOUNT SELAIN @RESQTAP   ");
  console.log("==================================================");

  const fbAdmin = await getAdmin();
  const db = fbAdmin.database();

  const stats = {
    authDeleted: 0,
    rtdbUsersDeleted: 0,
    roomsDeleted: 0,
    membersRemoved: 0,
    messagesRemoved: 0,
    sosAlertsRemoved: 0,
    callsRemoved: 0,
    adminCallsRemoved: 0,
    incidentReportsRemoved: 0,
    totalRtdbUpdates: 0
  };

  // 1. Dapatkan semua pengguna dari Firebase Auth dan RTDB
  const listResult = await fbAdmin.auth().listUsers(1000);
  const usersSnap = await db.ref("users").once("value");
  const rtdbUsers = usersSnap.val() || {};

  const resqtapUids = new Set();
  const deleteUids = new Set();
  const deleteEmails = new Set();
  const deletePublicIds = new Set();

  // Scan Auth users
  for (const user of listResult.users) {
    const email = (user.email || "").trim().toLowerCase();
    if (email.includes("@resqtap")) {
      resqtapUids.add(user.uid);
      console.log(`[AUTH] KEEP: ${user.uid} (${email})`);
    } else {
      deleteUids.add(user.uid);
      if (email) deleteEmails.add(email);
      console.log(`[AUTH] TO DELETE: ${user.uid} (${email})`);
    }
  }

  // Scan RTDB users
  for (const [uid, data] of Object.entries(rtdbUsers)) {
    const email = (data && data.email || "").trim().toLowerCase();
    if (email.includes("@resqtap")) {
      resqtapUids.add(uid);
      console.log(`[RTDB] KEEP: ${uid} (${email})`);
    } else {
      deleteUids.add(uid);
      if (email) deleteEmails.add(email);
      if (data && data.publicId) deletePublicIds.add(String(data.publicId).trim().toUpperCase());
      console.log(`[RTDB] TO DELETE: ${uid} (${email})`);
    }
  }

  console.log(`\nSummary: Keep ${resqtapUids.size} @resqtap account(s), Delete ${deleteUids.size} account(s)\n`);

  const updates = {};

  // 2. Padam registeredEmails (Bersihkan semua emel bukan @resqtap)
  const emailsSnap = await db.ref("registeredEmails").once("value");
  if (emailsSnap.exists()) {
    for (const [key] of Object.entries(emailsSnap.val() || {})) {
      const decoded = key.replace(/_at_/g, "@").replace(/_/g, ".");
      if (!decoded.toLowerCase().includes("@resqtap")) {
        console.log(`- Remove registeredEmail: ${key} (${decoded})`);
        updates[`registeredEmails/${key}`] = null;
      }
    }
  }

  // 3. Padam publicIds
  const publicIdsSnap = await db.ref("publicIds").once("value");
  if (publicIdsSnap.exists()) {
    for (const [pid, val] of Object.entries(publicIdsSnap.val() || {})) {
      const targetUid = typeof val === "string" ? val : (val && val.uid ? val.uid : "");
      if (deleteUids.has(targetUid) || deletePublicIds.has(pid.toUpperCase()) || (!resqtapUids.has(targetUid) && targetUid)) {
        console.log(`- Remove publicId: ${pid}`);
        updates[`publicIds/${pid}`] = null;
      }
    }
  }

  // 4. Padam nod top-level RTDB pengguna yang dipadam
  const userNodes = [
    "users", "admins", "supportChats", "aiChats",
    "userNotifications", "notifications",
    "sos_history", "sos_alerts", "sos_status",
    "live_locations", "userFriends", "friendRequests",
    "sentRequests", "userRooms", "userCalls"
  ];

  for (const node of userNodes) {
    for (const uid of deleteUids) {
      updates[`${node}/${uid}`] = null;
    }
  }

  // 5. Bersihkan rooms
  const roomsSnap = await db.ref("rooms").once("value");
  if (roomsSnap.exists()) {
    for (const [roomId, room] of Object.entries(roomsSnap.val() || {})) {
      if (!room) continue;
      const creatorUid = room.creatorUid;
      if (deleteUids.has(creatorUid)) {
        console.log(`- Delete room ${roomId} created by deleted user ${creatorUid}`);
        updates[`rooms/${roomId}`] = null;
        for (const uid of resqtapUids) {
          updates[`userRooms/${uid}/${roomId}`] = null;
        }
        stats.roomsDeleted++;
      } else {
        if (room.members && typeof room.members === "object") {
          for (const memberUid of Object.keys(room.members)) {
            if (deleteUids.has(memberUid)) {
              console.log(`- Remove member ${memberUid} from room ${roomId}`);
              updates[`rooms/${roomId}/members/${memberUid}`] = null;
              if (room.bells && room.bells[memberUid]) {
                updates[`rooms/${roomId}/bells/${memberUid}`] = null;
              }
              stats.membersRemoved++;
            }
          }
        }
        if (room.messages && typeof room.messages === "object") {
          for (const [msgId, msg] of Object.entries(room.messages)) {
            if (msg && deleteUids.has(msg.senderUid)) {
              updates[`rooms/${roomId}/messages/${msgId}`] = null;
              stats.messagesRemoved++;
            }
          }
        }
        if (room.sosAlerts && typeof room.sosAlerts === "object") {
          for (const [alertId, alert] of Object.entries(room.sosAlerts)) {
            if (alert && (deleteUids.has(alert.fromUid) || deleteUids.has(alert.senderUid))) {
              updates[`rooms/${roomId}/sosAlerts/${alertId}`] = null;
              stats.sosAlertsRemoved++;
            }
          }
        }
      }
    }
  }

  // 6. Bersihkan calls & incidentReports
  const callsSnap = await db.ref("calls").once("value");
  if (callsSnap.exists()) {
    for (const [callId, callData] of Object.entries(callsSnap.val() || {})) {
      if (callData && (deleteUids.has(callData.callerUid) || deleteUids.has(callData.calleeUid))) {
        updates[`calls/${callId}`] = null;
        stats.callsRemoved++;
      }
    }
  }

  const incSnap = await db.ref("incidentReports").once("value");
  if (incSnap.exists()) {
    for (const [reportId, report] of Object.entries(incSnap.val() || {})) {
      if (report && deleteUids.has(report.senderUid)) {
        updates[`incidentReports/${reportId}`] = null;
        stats.incidentReportsRemoved++;
      }
    }
  }

  // 7. Bersihkan adminCalls/incoming jika ada
  const adminCallsSnap = await db.ref("adminCalls/incoming").once("value");
  if (adminCallsSnap.exists()) {
    const call = adminCallsSnap.val();
    if (call && (deleteUids.has(call.callerUid) || !call.status || call.status === "cancelled" || call.status === "declined" || call.status === "ended")) {
      updates["adminCalls/incoming"] = null;
      stats.adminCallsRemoved++;
    }
  }

  // 8. Log Audit Rekod
  const auditLogId = `audit_${Date.now()}`;
  const auditTimestamp = Date.now();
  updates[`admin_audit_logs/${auditLogId}`] = {
    id: auditLogId,
    type: "Database Reset",
    action: "clear_database",
    triggeredBy: "Admin Session",
    accountsDeleted: deleteUids.size,
    accountsKept: resqtapUids.size,
    roomsDeleted: stats.roomsDeleted,
    messagesRemoved: stats.messagesRemoved,
    sosAlertsRemoved: stats.sosAlertsRemoved,
    callsRemoved: stats.callsRemoved,
    incidentReportsRemoved: stats.incidentReportsRemoved,
    details: `Sesi pembersihan database & auth selesai: ${deleteUids.size} akaun selain @resqtap dipadam sepenuhnya.`,
    timestamp: auditTimestamp,
    createdAt: auditTimestamp
  };

  // Bersihkan request task status jika ada
  updates["admin_tasks/clear_database/status"] = "completed";
  updates["admin_tasks/clear_database/completedAt"] = auditTimestamp;

  // Laksanakan kemaskini RTDB
  stats.totalRtdbUpdates = Object.keys(updates).length;
  console.log(`\nExecuting ${stats.totalRtdbUpdates} RTDB updates...`);
  if (stats.totalRtdbUpdates > 0) {
    await db.ref().update(updates);
    console.log("RTDB updates applied successfully.");
  }

  // 9. PADAM PENGGUNA DARI FIREBASE AUTH SEPENUHNYA
  console.log(`\nDeleting ${deleteUids.size} users from Firebase Authentication...`);
  for (const uid of deleteUids) {
    try {
      await fbAdmin.auth().deleteUser(uid);
      console.log(`[AUTH] Deleted user: ${uid}`);
      stats.authDeleted++;
    } catch (e) {
      if (e.code === "auth/user-not-found") {
        console.log(`[AUTH] User ${uid} already not in Auth.`);
      } else {
        console.warn(`[AUTH] Warning deleting ${uid}:`, e.message);
      }
    }
  }

  // 10. PADAM PENGGUNA & DATA DARI SUPABASE
  console.log(`\nMembersihkan rekod pengguna daripada Supabase...`);
  await cleanupSupabaseUsers(deleteUids);

  const durationSec = ((Date.now() - startTime) / 1000).toFixed(2);
  console.log(`\nReset Database & Auth selesai dalam ${durationSec}s.`);

  return {
    success: true,
    keepCount: resqtapUids.size,
    deleteCount: deleteUids.size,
    deletedUids: Array.from(deleteUids),
    deletedEmails: Array.from(deleteEmails),
    stats,
    durationSec,
    message: `Reset selesai! ${deleteUids.size} akaun selain @resqtap telah dipadam sepenuhnya dari Database & Firebase Auth (${durationSec}s).`
  };
}

async function deleteSingleUser(uid, emailHint = "") {
  if (!uid) throw new Error("UID diperlukan untuk padam pengguna.");

  const fbAdmin = await getAdmin();
  const db = fbAdmin.database();

  console.log(`[DELETE-USER] Memadam pengguna: ${uid}`);

  // 1. Padam daripada Firebase Auth
  let authDeleted = false;
  try {
    await fbAdmin.auth().deleteUser(uid);
    authDeleted = true;
    console.log(`[DELETE-USER] Berjaya padam dari Auth: ${uid}`);
  } catch (e) {
    if (e.code === "auth/user-not-found") {
      console.log(`[DELETE-USER] Pengguna ${uid} tiada dalam Auth.`);
    } else {
      console.warn(`[DELETE-USER] Ralat padam Auth:`, e.message);
    }
  }

  // 2. Dapatkan emel jika ada untuk bersihkan registeredEmails
  let email = emailHint;
  if (!email) {
    const userSnap = await db.ref(`users/${uid}/email`).once("value");
    if (userSnap.exists()) email = String(userSnap.val() || "").trim();
  }

  const updates = {};
  const userNodes = [
    "users", "admins", "supportChats", "aiChats",
    "userNotifications", "notifications",
    "sos_history", "sos_alerts", "sos_status",
    "live_locations", "userFriends", "friendRequests",
    "sentRequests", "userRooms", "userCalls"
  ];
  for (const node of userNodes) {
    updates[`${node}/${uid}`] = null;
  }

  if (email) {
    const sanitized = email.toLowerCase().replace(/\./g, "_").replace(/@/g, "_at_");
    updates[`registeredEmails/${sanitized}`] = null;
  }

  // Bersihkan sebarang request deletion
  updates[`admin_user_deletions/${uid}`] = null;

  // 3. Padam daripada Supabase
  try {
    const headers = {
      apikey: SUPABASE_ANON_KEY,
      Authorization: `Bearer ${SUPABASE_ANON_KEY}`
    };
    await Promise.allSettled([
      fetch(`${SUPABASE_URL}/rest/v1/profiles?id=eq.${encodeURIComponent(uid)}`, { method: "DELETE", headers }),
      fetch(`${SUPABASE_URL}/rest/v1/medical_cards?user_id=eq.${encodeURIComponent(uid)}`, { method: "DELETE", headers }),
      fetch(`${SUPABASE_URL}/rest/v1/user_notifications?user_uid=eq.${encodeURIComponent(uid)}`, { method: "DELETE", headers })
    ]);
    console.log(`[DELETE-USER] Berjaya padam dari Supabase: ${uid}`);
  } catch (err) {
    console.warn(`[DELETE-USER] Ralat padam Supabase:`, err.message);
  }

  await db.ref().update(updates);
  console.log(`[DELETE-USER] Selesai membersihkan RTDB untuk ${uid}`);

  return {
    success: true,
    authDeleted,
    uid,
    message: `Pengguna ${uid} berjaya dipadam dari Database dan Auth.`
  };
}

if (require.main === module) {
  resetDatabaseAccounts()
    .then((res) => {
      console.log(res.message);
      process.exit(0);
    })
    .catch((err) => {
      console.error("FATAL ERROR:", err);
      process.exit(1);
    });
}

module.exports = { resetDatabaseAccounts, deleteSingleUser, getAdmin };