/**
 * ResQTap Admin Authentication Bridge
 * Pengurusan operasi pentadbiran akaun pengguna secara selamat melalui Backend API & RTDB Task Queue.
 * Kunci sulit Service Account disimpan dengan selamat di pelayan (server-side).
 */

import { getAuth } from "https://www.gstatic.com/firebasejs/10.12.5/firebase-auth.js";
import { getDatabase, ref, set } from "https://www.gstatic.com/firebasejs/10.12.5/firebase-database.js";

async function getAdminToken() {
  try {
    const auth = getAuth();
    if (auth.currentUser) {
      return await auth.currentUser.getIdToken(false);
    }
  } catch (e) {
    console.warn("[AUTH-BRIDGE] Could not get currentUser ID token:", e.message);
  }
  return null;
}

/**
 * Muat turun senarai pengguna dari Firebase Authentication secara selamat melalui Backend API
 */
export async function listAllAuthUsers() {
  try {
    const token = await getAdminToken();
    const headers = { "Content-Type": "application/json" };
    if (token) {
      headers["Authorization"] = `Bearer ${token}`;
    }

    const res = await fetch("/api/admin/users", {
      method: "GET",
      headers
    });

    if (res.ok) {
      const data = await res.json();
      if (data.success && Array.isArray(data.users)) {
        return data.users;
      }
    }
  } catch (err) {
    console.warn("[AUTH-BRIDGE] Backend /api/admin/users not reachable:", err.message);
  }

  return [];
}

/**
 * Padam akaun secara kelompok melalui Backend API atau antrian tugas RTDB
 */
export async function batchDeleteAuthUsers(uids) {
  if (!uids || uids.length === 0) return { deletedCount: 0 };
  let deletedCount = 0;

  for (const uid of uids) {
    const ok = await deleteSingleAuthAccount(uid);
    if (ok) deletedCount++;
  }

  return { deletedCount };
}

/**
 * Padam semua akaun bukan @resqtap daripada Firebase Authentication
 */
export async function purgeNonResqtapAuthAccounts() {
  try {
    const token = await getAdminToken();
    const headers = { "Content-Type": "application/json" };
    if (token) {
      headers["Authorization"] = `Bearer ${token}`;
    }

    // 1. Cuba melalui Backend API terlebih dahulu
    try {
      const res = await fetch("/api/admin/clear-database", {
        method: "POST",
        headers
      });

      if (res.ok) {
        const json = await res.json();
        console.log("[AUTH-BRIDGE] Database reset berjaya melalui Backend API:", json);
        return {
          success: true,
          deletedCount: json.authDeleted || 0,
          deletedUids: json.deletedUids || []
        };
      }
    } catch (apiErr) {
      console.warn("[AUTH-BRIDGE] Backend API offline, queueing to RTDB:", apiErr.message);
    }

    // 2. Sandaran (Fallback): Hantar tugasan ke RTDB Cloud Task Queue
    const db = getDatabase();
    await set(ref(db, "admin_tasks/clear_database"), {
      status: "pending",
      requestedAt: Date.now()
    });

    console.log("[AUTH-BRIDGE] Tugasan clear_database dihantar ke RTDB queue.");
    return {
      success: true,
      deletedCount: 0,
      deletedUids: []
    };
  } catch (err) {
    console.error("[AUTH-BRIDGE] Ralat semasa purgeNonResqtapAuthAccounts:", err);
    throw err;
  }
}

/**
 * Padam akaun individu terus daripada Firebase Authentication secara selamat
 */
export async function deleteSingleAuthAccount(uid, email = "") {
  if (!uid) return false;
  try {
    const token = await getAdminToken();
    const headers = { "Content-Type": "application/json" };
    if (token) {
      headers["Authorization"] = `Bearer ${token}`;
    }

    // 1. Cuba melalui Backend API
    try {
      const res = await fetch("/api/admin/delete-user", {
        method: "POST",
        headers,
        body: JSON.stringify({ uid, email })
      });

      if (res.ok) {
        console.log(`[AUTH-BRIDGE] Berjaya memadam akaun Auth: ${uid} melalui Backend API.`);
        return true;
      }
    } catch (apiErr) {
      console.warn(`[AUTH-BRIDGE] Backend API offline untuk user ${uid}, queueing to RTDB.`);
    }

    // 2. Sandaran (Fallback): Hantar tugasan ke RTDB admin_user_deletions
    const db = getDatabase();
    await set(ref(db, `admin_user_deletions/${uid}`), {
      email,
      status: "pending",
      requestedAt: Date.now()
    });

    console.log(`[AUTH-BRIDGE] Tugasan delete-user untuk ${uid} dihantar ke RTDB queue.`);
    return true;
  } catch (err) {
    console.warn(`[AUTH-BRIDGE] Amaran memadam akaun Auth ${uid}:`, err.message);
    return false;
  }
}
