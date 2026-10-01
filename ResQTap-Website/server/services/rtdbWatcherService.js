const path = require('path');

const cleanupScriptPath = path.resolve(__dirname, '..', '..', '..', 'cleanup_rtdb.js');

/**
 * Start RTDB Cloud Task Watcher
 * Listens for administrative tasks queued in Realtime Database:
 * 1. Database full reset for non-@resqtap accounts
 * 2. Individual user deletion from Firebase Auth & RTDB
 */
async function startRtdbTaskWatcher() {
  try {
    const { resetDatabaseAccounts, deleteSingleUser, getAdmin } = require(cleanupScriptPath);
    const adminInstance = await getAdmin();
    const db = adminInstance.database();
    console.log('[WATCHER] RTDB Task Watcher active: listening for admin tasks & user deletions...');

    // 1. Listen for Clear Database requests
    db.ref('admin_tasks/clear_database').on('value', async (snapshot) => {
      const task = snapshot.val();
      if (task && task.status === 'pending') {
        console.log('[WATCHER] Detected pending clear_database task. Executing full reset...');
        try {
          await db.ref('admin_tasks/clear_database/status').set('processing');
          const result = await resetDatabaseAccounts();
          await db.ref('admin_tasks/clear_database').update({
            status: 'completed',
            completedAt: Date.now(),
            summary: result.message
          });
          console.log('[WATCHER] Clear database task completed successfully.');
        } catch (err) {
          console.error('[WATCHER] Failed clear_database task:', err);
          await db.ref('admin_tasks/clear_database').update({
            status: 'failed',
            error: err.message || String(err)
          });
        }
      }
    });

    // 2. Handler for individual user deletion requests
    const handleUserDeletion = async (snapshot) => {
      const uid = snapshot.key;
      const data = snapshot.val();
      if (data && data.status === 'pending') {
        console.log(`[WATCHER] Detected pending user deletion for ${uid}...`);
        try {
          await snapshot.ref.update({ status: 'processing' });
          await deleteSingleUser(uid, data.email || '');
          await snapshot.ref.update({
            status: 'completed',
            completedAt: Date.now()
          });
          console.log(`[WATCHER] Successfully deleted user ${uid} from Auth & RTDB.`);
        } catch (err) {
          console.error(`[WATCHER] Failed user deletion for ${uid}:`, err);
          await snapshot.ref.update({
            status: 'failed',
            error: err.message || String(err)
          });
        }
      }
    };

    db.ref('admin_user_deletions').on('child_added', handleUserDeletion);
    db.ref('admin_user_deletions').on('child_changed', handleUserDeletion);
  } catch (err) {
    console.error('[WATCHER] Could not start RTDB task watcher:', err.message);
  }
}

module.exports = { startRtdbTaskWatcher, cleanupScriptPath };
