/**
 * ResQTap Web Server Entry Point
 * Architecture: MVC (Model-View-Controller)
 * Modularized server logic located in ./server/
 */
const { server, startServer } = require('./server/server');

// Start server if executed directly
if (require.main === module) {
  startServer();
}

module.exports = { server, startServer };
