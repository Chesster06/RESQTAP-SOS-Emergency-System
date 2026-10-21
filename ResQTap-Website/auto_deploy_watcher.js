/**
 * ResQTap Auto-Deploy Watcher
 * Memantau sebarang perubahan fail dalam folder 'public' (HTML, JS, CSS, dll.)
 * dan melancarkan 'firebase deploy --only hosting' secara automatik.
 */
const fs = require("fs");
const path = require("path");
const { exec } = require("child_process");

const WATCH_DIR = path.join(__dirname, "public");
const ROOT_DIR = path.resolve(__dirname, "..");
let debounceTimer = null;
let isDeploying = false;
let pendingDeploy = false;

console.log("ResQTap Auto-Deploy Watcher Aktif!");
console.log(`Memantau folder: ${WATCH_DIR}`);
console.log("Setiap perubahan fail akan auto-deploy ke Firebase Hosting.");

function triggerDeploy() {
  if (isDeploying) {
    pendingDeploy = true;
    return;
  }

  isDeploying = true;
  pendingDeploy = false;
  const time = new Date().toLocaleTimeString();
  console.log(`\n[${time}] Mengesan perubahan fail! Memulakan deploy ke Firebase Hosting...`);

  const cmd = "firebase deploy --only hosting --non-interactive";
  exec(cmd, { cwd: ROOT_DIR }, (error, stdout, stderr) => {
    isDeploying = false;
    const finishTime = new Date().toLocaleTimeString();

    if (error) {
      console.error(`[${finishTime}]  Ralat auto-deploy:`, error.message);
      if (stderr) console.error(stderr);
    } else {
      console.log(`[${finishTime}]  Berjaya deploy ke Firebase Hosting!`);
      console.log(" URL Rasmi: https://resqtap-b9ff5.web.app");
    }

    if (pendingDeploy) {
      console.log("[AUTO-DEPLOY] Ada perubahan baru semasa proses tadi, mendeploy semula...");
      triggerDeploy();
    }
  });
}

// Pantau perubahan fail secara rekursif
fs.watch(WATCH_DIR, { recursive: true }, (eventType, filename) => {
  if (!filename) return;
  // Abaikan fail sementara atau tersembunyi
  if (filename.startsWith(".") || filename.endsWith("~") || filename.includes(".tmp")) return;

  console.log(`[PERUBAHAN DIKESAN] ${filename} (${eventType})`);

  // Debounce 2.5 saat untuk tunggu semua fail siap disimpan
  if (debounceTimer) clearTimeout(debounceTimer);
  debounceTimer = setTimeout(() => {
    triggerDeploy();
  }, 2500);
});
