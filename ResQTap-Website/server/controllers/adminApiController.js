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
