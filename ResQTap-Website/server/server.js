const http = require('http');
const fs = require('fs');
const path = require('path');
const { MIME_TYPES } = require('./utils/mimeTypes');
const { startRtdbTaskWatcher } = require('./services/rtdbWatcherService');
const { handleClearDatabase, handleDeleteUser } = require('./controllers/adminApiController');

const PORT = 5000;
const PUBLIC_DIR = path.resolve(__dirname, '..', 'public');

const server = http.createServer((req, res) => {
  const urlObj = new URL(req.url, `http://${req.headers.host || 'localhost'}`);
  let pathname = decodeURIComponent(urlObj.pathname);

  // Handle CORS Preflight
  if (req.method === 'OPTIONS') {
    const origin = req.headers['origin'] || '';
    const allowedOrigins = [
      'http://localhost:5000',
      'http://127.0.0.1:5000',
      'https://your-project.web.app',
      'https://your-project.firebaseapp.com'
    ];
    const allowed = allowedOrigins.includes(origin) ? origin : null;

    if (!allowed && origin) {
      res.writeHead(403, { 'Content-Type': 'text/plain' });
      res.end('403 Forbidden: CORS Origin Not Allowed');
      return;
    }

    res.writeHead(204, {
      'Access-Control-Allow-Origin': allowed || 'http://localhost:5000',
      'Access-Control-Allow-Methods': 'GET, POST, OPTIONS',
      'Access-Control-Allow-Headers': 'Content-Type, Authorization',
      'Access-Control-Allow-Credentials': 'true'
    });
    res.end();
    return;
  }

  const { handleClearDatabase, handleDeleteUser, handleListUsers } = require('./controllers/adminApiController');

  if (pathname === '/api/admin/users') {
    return handleListUsers(req, res);
  }

  if (pathname === '/api/admin/clear-database' || pathname === '/api/admin/reset-database') {
    return handleClearDatabase(req, res);
  }

  if (pathname === '/api/admin/delete-user') {
    return handleDeleteUser(req, res);
  }

  // URL Rewrites matching serve.json & firebase.json
  if (pathname === '/admin' || pathname.startsWith('/admin/')) {
    pathname = '/admin.html';
  } else if (pathname === '/download') {
    pathname = '/index.html';
  }

  // Normalize safe path and prevent directory traversal / arbitrary file reads
  const safePath = path.resolve(PUBLIC_DIR, '.' + pathname);
  if (!safePath.startsWith(PUBLIC_DIR)) {
    res.writeHead(403, { 'Content-Type': 'text/plain' });
    res.end('403 Forbidden: Access Denied');
    return;
  }

  let filePath = safePath;

  // Check if directory -> index.html
  if (fs.existsSync(filePath) && fs.statSync(filePath).isDirectory()) {
    filePath = path.join(filePath, 'index.html');
  }

  // Clean URLs: /flow -> /flow.html or /features -> /features.html
  if (!fs.existsSync(filePath) && fs.existsSync(filePath + '.html')) {
    filePath = filePath + '.html';
  }

  // SPA fallback
  if (!fs.existsSync(filePath)) {
    filePath = path.join(PUBLIC_DIR, 'index.html');
  }

  // Final check to guarantee filePath does not leave PUBLIC_DIR
  if (!path.resolve(filePath).startsWith(PUBLIC_DIR)) {
    res.writeHead(403, { 'Content-Type': 'text/plain' });
    res.end('403 Forbidden: Access Denied');
    return;
  }

  fs.readFile(filePath, (err, data) => {
    if (err) {
      res.writeHead(500, { 'Content-Type': 'text/plain' });
      res.end('500 Internal Server Error');
      return;
    }

    const ext = path.extname(filePath).toLowerCase();
    const contentType = MIME_TYPES[ext] || 'application/octet-stream';

    res.writeHead(200, {
      'Content-Type': contentType,
      'Access-Control-Allow-Origin': '*',
      'Cache-Control': 'no-cache, no-store, must-revalidate'
    });
    res.end(data);
  });
});

function startServer(port = PORT) {
  server.listen(port, '0.0.0.0', () => {
    console.log(`ResQTap Website running at http://localhost:${port}`);
    console.log(`Admin Dashboard: http://localhost:${port}/admin`);
    startRtdbTaskWatcher();
  });
  return server;
}

if (require.main === module) {
  startServer();
}

module.exports = { server, startServer };
