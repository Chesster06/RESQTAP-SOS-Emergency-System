const { cleanupScriptPath } = require('../services/rtdbWatcherService');

const ALLOWED_ORIGINS = new Set([
  'http://localhost:5000',
  'http://127.0.0.1:5000',
  'https://your-project.web.app',
  'https://your-project.firebaseapp.com'
]);

function getCorsOrigin(req) {
  const origin = req.headers['origin'] || '';
  if (!origin) return 'http://localhost:5000';
  if (ALLOWED_ORIGINS.has(origin)) {
    return origin;
  }
  return null;
}

function setCorsHeaders(res, origin) {
  if (origin) {
    res.setHeader('Access-Control-Allow-Origin', origin);
    res.setHeader('Access-Control-Allow-Methods', 'GET, POST, OPTIONS');
    res.setHeader('Access-Control-Allow-Headers', 'Content-Type, Authorization');
    res.setHeader('Access-Control-Allow-Credentials', 'true');
  }
}

/**
 * Verify Firebase Admin ID Token
 */
async function verifyAdminToken(req) {
  const authHeader = req.headers['authorization'] || '';
  if (!authHeader.startsWith('Bearer ')) {
    return null;
  }
  const idToken = authHeader.substring(7);
  try {
    const { getAdmin } = require(cleanupScriptPath);
    const admin = await getAdmin();
    const decodedToken = await admin.auth().verifyIdToken(idToken);
    
    // Verify user role in RTDB or check domain (must have verified email!)
    const db = admin.database();
    const adminSnap = await db.ref(`admins/${decodedToken.uid}`).once('value');
    const adminVal = adminSnap.val();
    const isDomainAdmin = decodedToken.email_verified === true && 
                          decodedToken.email && 
                          decodedToken.email.toLowerCase().endsWith('@resqtap.com');
    const isAdmin = adminVal === true || 
                    (adminVal && adminVal.active === true) || 
                    isDomainAdmin;

    if (!isAdmin) {
      console.warn(`[SECURITY] User ${decodedToken.uid} (${decodedToken.email}, verified: ${decodedToken.email_verified}) attempted admin action without admin role.`);
      return null;
    }
    return decodedToken;
  } catch (err) {
    console.error('[SECURITY] Bearer token verification failed:', err.message);
    return null;
  }
}

/**
 * Handle GET /api/admin/users
 * Returns list of Firebase Authentication users securely
 */
async function handleListUsers(req, res) {
  const origin = getCorsOrigin(req);
  if (req.headers['origin'] && !origin) {
    res.writeHead(403, { 'Content-Type': 'application/json' });
    res.end(JSON.stringify({ success: false, error: 'Forbidden: Untrusted Origin' }));
    return;
  }
  setCorsHeaders(res, origin);

  const adminUser = await verifyAdminToken(req);
  if (!adminUser) {
    res.writeHead(401, { 'Content-Type': 'application/json' });
    res.end(JSON.stringify({ success: false, error: 'Unauthorized: Valid Admin Token Required' }));
    return;
  }

  try {
    const { getAdmin } = require(cleanupScriptPath);
    const admin = await getAdmin();
    const userRecords = await admin.auth().listUsers(1000);
    const cleanUsers = userRecords.users.map(u => ({
      uid: u.uid,
      localId: u.uid,
      email: u.email,
      displayName: u.displayName,
      photoURL: u.photoURL,
      photoUrl: u.photoURL,
      createdAt: u.metadata.creationTime,
      lastSignInTime: u.metadata.lastSignInTime,
      disabled: u.disabled
    }));

    res.writeHead(200, { 'Content-Type': 'application/json' });
    res.end(JSON.stringify({ success: true, users: cleanUsers }));
  } catch (err) {
    console.error('[SERVER API] listUsers error:', err);
    res.writeHead(500, { 'Content-Type': 'application/json' });
    res.end(JSON.stringify({ success: false, error: err.message || 'Internal server error' }));
  }
}

/**
 * Handle Admin Clear/Reset Database API
 */
async function handleClearDatabase(req, res) {
  const origin = getCorsOrigin(req);
  if (req.headers['origin'] && !origin) {
    res.writeHead(403, { 'Content-Type': 'application/json' });
    res.end(JSON.stringify({ success: false, error: 'Forbidden: Untrusted Origin' }));
    return;
  }
  setCorsHeaders(res, origin);

  if (req.method !== 'POST') {
    res.writeHead(405, { 'Content-Type': 'application/json' });
    res.end(JSON.stringify({ error: 'Method Not Allowed' }));
    return;
  }

  // Enforce Admin Authentication
  const adminUser = await verifyAdminToken(req);
  if (!adminUser) {
    res.writeHead(401, { 'Content-Type': 'application/json' });
    res.end(JSON.stringify({ success: false, error: 'Unauthorized: Admin authentication required to clear database' }));
    return;
  }

  let body = '';
  const MAX_PAYLOAD = 50 * 1024;
  req.on('data', chunk => {
    body += chunk;
    if (body.length > MAX_PAYLOAD) {
      res.writeHead(413, { 'Content-Type': 'application/json' });
      res.end(JSON.stringify({ success: false, error: 'Payload Too Large' }));
      req.destroy();
    }
  });

  req.on('end', async () => {
    if (body.length > MAX_PAYLOAD) return;
    try {
      console.log(`[SERVER API] Verified admin ${adminUser.email} requested database reset.`);
      delete require.cache[require.resolve(cleanupScriptPath)];
      const { resetDatabaseAccounts: runReset } = require(cleanupScriptPath);
      const result = await runReset();
      res.writeHead(200, { 'Content-Type': 'application/json' });
      res.end(JSON.stringify(result));
    } catch (err) {
      console.error('[SERVER API] Clear database error:', err);
      res.writeHead(500, { 'Content-Type': 'application/json' });
      res.end(JSON.stringify({ success: false, error: err.message || 'Internal server error' }));
    }
  });
}

/**
 * Handle Admin Delete Single User API
 */
async function handleDeleteUser(req, res) {
  const origin = getCorsOrigin(req);
  if (req.headers['origin'] && !origin) {
    res.writeHead(403, { 'Content-Type': 'application/json' });
    res.end(JSON.stringify({ success: false, error: 'Forbidden: Untrusted Origin' }));
    return;
  }
  setCorsHeaders(res, origin);

  if (req.method !== 'POST') {
    res.writeHead(405, { 'Content-Type': 'application/json' });
    res.end(JSON.stringify({ error: 'Method Not Allowed' }));
    return;
  }

  // Enforce Admin Authentication
  const adminUser = await verifyAdminToken(req);
  if (!adminUser) {
    res.writeHead(401, { 'Content-Type': 'application/json' });
    res.end(JSON.stringify({ success: false, error: 'Unauthorized: Admin authentication required to delete user' }));
    return;
  }

  let body = '';
  const MAX_PAYLOAD = 100 * 1024; // 100 KB limit
  req.on('data', chunk => {
    body += chunk;
    if (body.length > MAX_PAYLOAD) {
      res.writeHead(413, { 'Content-Type': 'application/json' });
      res.end(JSON.stringify({ success: false, error: 'Payload Too Large' }));
      req.destroy();
    }
  });
  req.on('end', async () => {
    if (body.length > MAX_PAYLOAD) return;
    try {
      const payload = JSON.parse(body || '{}');
      const uid = payload.uid;
      const email = payload.email || '';
      if (!uid) {
        res.writeHead(400, { 'Content-Type': 'application/json' });
        res.end(JSON.stringify({ success: false, error: 'UID is required' }));
        return;
      }

      console.log(`[SERVER API] Verified admin ${adminUser.email} deleting user ${uid}...`);
      delete require.cache[require.resolve(cleanupScriptPath)];
      const { deleteSingleUser: runDelete } = require(cleanupScriptPath);
      const result = await runDelete(uid, email);
      res.writeHead(200, { 'Content-Type': 'application/json' });
      res.end(JSON.stringify(result));
    } catch (err) {
      console.error('[SERVER API] Delete user error:', err);
      res.writeHead(500, { 'Content-Type': 'application/json' });
      res.end(JSON.stringify({ success: false, error: err.message || 'Internal server error' }));
    }
  });
}

module.exports = {
  handleListUsers,
  handleClearDatabase,
  handleDeleteUser
};
