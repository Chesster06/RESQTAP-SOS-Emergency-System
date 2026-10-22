/**
 * ResQTap Admin Web Portal SPA Logic
 * Pengurusan papan pemuka masa nyata: pantau SOS, semak laporan insiden, livechat sokongan, dan siaran notifikasi.
 */
import { initializeApp } from "https://www.gstatic.com/firebasejs/10.12.5/firebase-app.js";
import {
  getAuth,
  onAuthStateChanged,
  signInWithEmailAndPassword,
  signOut,
  updateProfile,
  updatePassword
} from "https://www.gstatic.com/firebasejs/10.12.5/firebase-auth.js";
import {
  getDatabase,
  ref,
  get,
  onValue,
  push,
  set,
  update,
  remove,
  serverTimestamp,
  child
} from "https://www.gstatic.com/firebasejs/10.12.5/firebase-database.js";
import {
  getStorage,
  ref as storageRef,
  uploadBytes,
  getDownloadURL
} from "https://www.gstatic.com/firebasejs/10.12.5/firebase-storage.js";
import {
  purgeNonResqtapAuthAccounts,
  deleteSingleAuthAccount,
  listAllAuthUsers
} from "./admin_auth_bridge.js";
import { createClient } from "https://cdn.jsdelivr.net/npm/@supabase/supabase-js@2/+esm";
import { SUPABASE_URL, SUPABASE_ANON_KEY, firebaseConfig } from "./config.js";

export { SUPABASE_URL, SUPABASE_ANON_KEY };
export const supabase = createClient(SUPABASE_URL, SUPABASE_ANON_KEY);

const app = initializeApp(firebaseConfig);
const auth = getAuth(app);
const db = getDatabase(app);
const storage = getStorage(app);

const ONLINE_MS = 2 * 60 * 1000;
const RECENT_MS = 15 * 60 * 1000;
const NAV_DOT_RECENT_MS = 24 * 60 * 60 * 1000;
const NAV_SEEN_STORAGE_KEY = "ResQTap_admin_nav_seen_v1";
const SOS_STALE_MS = 10 * 60 * 1000;
const LOW_BATTERY = 20;
const INVALID_KEY = /[.#$\/\[\]]/;
const NOTICE_METHOD = "app_notification_bar";
const NOTICE_SOURCE = "admin-panel";
const LIVECHAT_ADMIN_LABEL = "ResQTap Support";
const LIVECHAT_CLAIM_NOTICE = "Agent has been claimed your ticket";
const LIVECHAT_TYPING_IDLE_MS = 3000;
const LIVECHAT_TYPING_REFRESH_MS = 1500;
const AI_CHAT_EXPIRY_MS = 5 * 60 * 1000;
const NOTIFICATION_PAGE_SIZES = [10, 20, 30];
const NOTIFICATION_CATEGORIES = [
  { id: "all", label: "All", icon: "layers-3" },
  { id: "calls", label: "Calls", icon: "phone-call", types: ["Video Call", "Voice Call", "Incoming Call", "Answered Call", "Missed Call", "Declined Call", "Call Ended", "SOS Call Request"] },
  { id: "system", label: "System & Cleanup", icon: "database", types: ["Database Reset", "System Cleanup"] },
  { id: "notice", label: "Notifications", icon: "bell-ring", types: ["Notifications"] },
  { id: "sos", label: "SOS", icon: "siren", types: ["SOS Alert", "SOS Cancelled", "SOS Resolved"] },
  { id: "bell", label: "Bell", icon: "bell", types: ["Bell"] },
  { id: "legacy", label: "Legacy", icon: "archive", types: ["Legacy Delivery"] }
];
const REPORT_STATUSES = [
  { id: "new", label: "New", tone: "alert" },
  { id: "reviewing", label: "Reviewing", tone: "warn" },
  { id: "resolved", label: "Resolved", tone: "good" },
  { id: "rejected", label: "Rejected", tone: "" }
];
const LIVECHAT_ATTACHMENT_OPTIONS = {
  document: {
    accept: ".pdf,.txt,.doc,.docx,.xls,.xlsx,.ppt,.pptx,application/pdf,text/plain,application/msword,application/vnd.openxmlformats-officedocument.wordprocessingml.document,application/vnd.ms-excel,application/vnd.openxmlformats-officedocument.spreadsheetml.sheet,application/vnd.ms-powerpoint,application/vnd.openxmlformats-officedocument.presentationml.presentation"
  },
  photo: {
    accept: "image/*,video/*"
  },
  camera: {
    accept: "image/*",
    capture: "environment"
  },
  audio: {
    accept: "audio/*"
  },
  calendar: {
    accept: ".ics,text/calendar,application/ics"
  }
};

const els = {
  publicSite: document.getElementById("publicSite"),
  authScreen: document.getElementById("authScreen"),
  deniedScreen: document.getElementById("deniedScreen"),
  appShell: document.getElementById("appShell"),
  loginForm: document.getElementById("loginForm"),
  loginButton: document.getElementById("loginButton"),
  emailInput: document.getElementById("emailInput"),
  passwordInput: document.getElementById("passwordInput"),
  authMessage: document.getElementById("authMessage"),
  deniedUid: document.getElementById("deniedUid"),
  adminPathCode: document.getElementById("adminPathCode"),
  deniedSignOut: document.getElementById("deniedSignOut"),
  sidebarSignOutBtn: document.getElementById("sidebarSignOutBtn"),
  signOutButton: document.getElementById("sidebarSignOutBtn") || document.getElementById("signOutButton"),
  syncStatus: document.getElementById("syncStatus"),
  viewTitle: document.getElementById("viewTitle"),
  sidebarToggleBtn: document.getElementById("sidebarToggleBtn"),
  sidebarCloseBtn: document.getElementById("sidebarCloseBtn"),
  sidebarBackdrop: document.getElementById("sidebarBackdrop"),
  globalSearch: document.getElementById("globalSearch"),
  dashboardRangeSelect: document.getElementById("dashboardRangeSelect"),
  exportButton: document.getElementById("exportButton"),
  clearHistoryButton: document.getElementById("clearHistoryButton"),
  clearDatabaseButton: document.getElementById("clearDatabaseButton"),
  metricGrid: document.getElementById("metricGrid"),
  usageChart: document.getElementById("usageChart"),
  featureChart: document.getElementById("featureChart"),
  featureLegend: document.getElementById("featureLegend"),
  usersCount: document.getElementById("usersCount"),
  usersTableBody: document.getElementById("usersTableBody"),
  userFilterPills: document.getElementById("userFilterPills"),
  userCountAll: document.getElementById("userCountAll"),
  userCountOnline: document.getElementById("userCountOnline"),
  userCountOffline: document.getElementById("userCountOffline"),
  roomsCount: document.getElementById("roomsCount"),
  roomsTableBody: document.getElementById("roomsTableBody"),
  sosCount: document.getElementById("sosCount"),
  sosTableBody: document.getElementById("sosTableBody"),
  reportsCount: document.getElementById("reportsCount"),
  reportsTableBody: document.getElementById("reportsTableBody"),
  noticeForm: document.getElementById("noticeForm"),
  notificationAudienceInput: document.getElementById("notificationAudienceInput"),
  notificationTitleInput: document.getElementById("notificationTitleInput"),
  notificationMessageInput: document.getElementById("notificationMessageInput"),
  notificationCharCount: document.getElementById("notificationCharCount"),
  clearNotificationButton: document.getElementById("clearNotificationButton"),
  sendNotificationButton: document.getElementById("sendNotificationButton"),
  notificationPageSizeSelect: document.getElementById("notificationPageSizeSelect"),
  notificationCategoryTabs: document.getElementById("notificationCategoryTabs"),
  notificationPagination: document.getElementById("notificationPagination"),
  notificationPageSummary: document.getElementById("notificationPageSummary"),
  notificationPrevPageButton: document.getElementById("notificationPrevPageButton"),
  notificationNextPageButton: document.getElementById("notificationNextPageButton"),
  notificationsCount: document.getElementById("notificationsCount"),
  notificationsTableBody: document.getElementById("notificationsTableBody"),
  soslivechatCount: document.getElementById("soslivechatCount"),
  soslivechatSearchInput: document.getElementById("soslivechatSearchInput"),
  soslivechatThreadList: document.getElementById("soslivechatThreadList"),
  soslivechatSelectedTitle: document.getElementById("soslivechatSelectedTitle"),
  soslivechatSelectedSubtitle: document.getElementById("soslivechatSelectedSubtitle"),
  soslivechatMessages: document.getElementById("soslivechatMessages"),
  soslivechatEmptyState: document.getElementById("soslivechatEmptyState"),
  soslivechatReplyForm: document.getElementById("soslivechatReplyForm"),
  soslivechatReplyInput: document.getElementById("soslivechatReplyInput"),
  soslivechatReplyButton: document.getElementById("soslivechatReplyButton"),
  soslivechatCallVideoBtn: document.getElementById("soslivechatCallVideoBtn"),
  soslivechatCallVoiceBtn: document.getElementById("soslivechatCallVoiceBtn"),
  soslivechatViewAlertBtn: document.getElementById("soslivechatViewAlertBtn"),
  soslivechatClearBtn: document.getElementById("soslivechatClearBtn"),
  livechatCount: document.getElementById("livechatCount"),
  livechatThreadList: document.getElementById("livechatThreadList"),
  livechatSelectedTitle: document.getElementById("livechatSelectedTitle"),
  livechatSelectedSubtitle: document.getElementById("livechatSelectedSubtitle"),
  livechatMessages: document.getElementById("livechatMessages"),
  livechatEmptyState: document.getElementById("livechatEmptyState"),
  livechatReplyForm: document.getElementById("livechatReplyForm"),
  livechatReplyInput: document.getElementById("livechatReplyInput"),
  livechatAttachButton: document.getElementById("livechatAttachButton"),
  livechatAttachMenu: document.getElementById("livechatAttachMenu"),
  livechatAttachmentInput: document.getElementById("livechatAttachmentInput"),
  livechatAttachmentLabel: document.getElementById("livechatAttachmentLabel"),
  livechatReplyButton: document.getElementById("livechatReplyButton"),
  livechatCallVideoBtn: document.getElementById("livechatCallVideoBtn"),
  livechatCallVoiceBtn: document.getElementById("livechatCallVoiceBtn"),
  resolveChatButton: document.getElementById("resolveChatButton"),
  deleteChatButton: document.getElementById("deleteChatButton"),
  aiChatCount: document.getElementById("aiChatCount"),
  aiChatThreadList: document.getElementById("aiChatThreadList"),
  aiChatSelectedTitle: document.getElementById("aiChatSelectedTitle"),
  aiChatSelectedSubtitle: document.getElementById("aiChatSelectedSubtitle"),
  aiChatMessages: document.getElementById("aiChatMessages"),
  deleteAiChatButton: document.getElementById("deleteAiChatButton"),
  adminsCount: document.getElementById("adminsCount"),
  adminsList: document.getElementById("adminsList"),
  grantAdminForm: document.getElementById("grantAdminForm"),
  adminUidInput: document.getElementById("adminUidInput"),
  detailPanel: document.getElementById("detailPanel"),
  toast: document.getElementById("toast"),
  adminProfileForm: document.getElementById("adminProfileForm"),
  adminProfileName: document.getElementById("adminProfileName"),
  adminProfileEmail: document.getElementById("adminProfileEmail"),
  adminProfilePhone: document.getElementById("adminProfilePhone"),
  adminProfileRole: document.getElementById("adminProfileRole"),
  adminProfileUid: document.getElementById("adminProfileUid"),
  copyAdminUidBtn: document.getElementById("copyAdminUidBtn"),
  saveAdminProfileBtn: document.getElementById("saveAdminProfileBtn"),
  profileAvatarDisplay: document.getElementById("profileAvatarDisplay"),
  profileAvatarInitials: document.getElementById("profileAvatarInitials"),
  profileDisplayHeaderName: document.getElementById("profileDisplayHeaderName"),
  profileDisplayHeaderEmail: document.getElementById("profileDisplayHeaderEmail"),
  profileRoleBadge: document.getElementById("profileRoleBadge"),
  profileUidShort: document.getElementById("profileUidShort"),
  adminPasswordForm: document.getElementById("adminPasswordForm"),
  adminNewPassword: document.getElementById("adminNewPassword"),
  adminConfirmPassword: document.getElementById("adminConfirmPassword"),
  toggleNewPasswordBtn: document.getElementById("toggleNewPasswordBtn"),
  toggleConfirmPasswordBtn: document.getElementById("toggleConfirmPasswordBtn"),
  saveAdminPasswordBtn: document.getElementById("saveAdminPasswordBtn"),
  adminPhotoFileInput: document.getElementById("adminPhotoFileInput"),
  adminRemovePhotoBtn: document.getElementById("adminRemovePhotoBtn"),
  adminUploadBtnLabel: document.getElementById("adminUploadBtnLabel"),
  profileAvatarImg: document.getElementById("profileAvatarImg"),
  adminProfileSignOutBtn: document.getElementById("adminProfileSignOutBtn"),
  adminPageSignOutBtn: document.getElementById("adminPageSignOutBtn"),
  sidebarProfileNavBtn: document.getElementById("sidebarProfileNavBtn"),
  sidebarProfileName: document.getElementById("sidebarProfileName"),
  sidebarProfileRole: document.getElementById("sidebarProfileRole"),
  sidebarAvatarMini: document.getElementById("sidebarAvatarMini"),
  sidebarAvatarMiniInitials: document.getElementById("sidebarAvatarMiniInitials"),
  sidebarAvatarMiniImg: document.getElementById("sidebarAvatarMiniImg"),
  profileSessionEmailText: document.getElementById("profileSessionEmailText"),
  profileHeroAlertsCount: document.getElementById("profileHeroAlertsCount"),
  profileHeroRespondersCount: document.getElementById("profileHeroRespondersCount"),
  profileHeroUsersCount: document.getElementById("profileHeroUsersCount")
};

const PUBLIC_PAGES = new Set(["home", "features", "flow", "safety"]);
const PUBLIC_PAGE_PATHS = {
  home: "/",
  features: "/features",
  flow: "/flow",
  safety: "/safety"
};

function isAdminRoute() {
  const p = window.location.pathname.replace(/\/+$/, "");
  const search = window.location.search;
  const hash = window.location.hash;
  return p === "/admin" || p === "/admin.html" || p.startsWith("/admin/") || search.includes("admin=true") || hash === "#admin";
}

function showPublicSite() {
  if (window.location.pathname.replace(/\/+$/, "") === "/download") {
    window.location.replace("/");
    return;
  }

  cleanupDataListeners();
  document.documentElement.classList.remove("admin-route-loading");
  document.documentElement.classList.remove("has-admin-session");
  const loader = document.getElementById("adminRouteLoader");
  if (loader) loader.classList.add("hidden");
  document.body.classList.add("public-site-active");
  document.body.classList.remove("admin-site-active");
  if (els.publicSite) els.publicSite.classList.remove("hidden");
  els.authScreen.classList.add("hidden");
  els.deniedScreen.classList.add("hidden");
  els.appShell.classList.add("hidden");
  document.title = "ResQTap";
  setPublicPage(readPublicPageFromUrl(), false);
  initPublicAnimations();
}

function enterAdminRoute() {
  document.body.classList.remove("public-site-active");
  document.body.classList.add("admin-site-active");
  if (els.publicSite) els.publicSite.classList.add("hidden");
  document.title = "ResQTap Admin";
}

let publicRevealObserver = null;
let publicRouterBound = false;

function readPublicPageFromUrl() {
  const path = window.location.pathname.replace(/\/+$/, "") || "/";
  const page = Object.entries(PUBLIC_PAGE_PATHS).find(([, pagePath]) => pagePath === path);
  return page ? page[0] : "home";
}

function setPublicPage(page, push = true) {
  const nextPage = PUBLIC_PAGES.has(page) ? page : "home";
  document.querySelectorAll("[data-public-panel]").forEach((panel) => {
    panel.classList.toggle("is-active", panel.dataset.publicPanel === nextPage);
  });
  document.querySelectorAll("[data-public-page]").forEach((link) => {
    link.classList.toggle("is-active", link.dataset.publicPage === nextPage);
  });

  if (push) {
    const url = PUBLIC_PAGE_PATHS[nextPage] || "/";
    window.history.pushState({ publicPage: nextPage }, "", url);
  }

  window.scrollTo({ top: 0, behavior: "smooth" });
  initPublicAnimations();
}

let lenisInstance = null;

function initSmoothMomentumScroll() {
  if (isAdminRoute() || window.innerWidth <= 860 || window.matchMedia("(pointer: coarse)").matches) {
    if (lenisInstance) {
      try { lenisInstance.destroy(); } catch (e) {}
      lenisInstance = null;
    }
    return;
  }

  if (typeof window.Lenis !== "undefined") {
    try {
      if (!lenisInstance) {
        lenisInstance = new window.Lenis({
          duration: 1.1,
          easing: (t) => Math.min(1, 1.001 - Math.pow(2, -10 * t)),
          orientation: "vertical",
          gestureOrientation: "vertical",
          smoothWheel: true,
          wheelMultiplier: 1.0,
          touchMultiplier: 0,
          infinite: false,
        });

        function raf(time) {
          if (lenisInstance) {
            lenisInstance.raf(time);
            requestAnimationFrame(raf);
          }
        }
        requestAnimationFrame(raf);
      }
      return;
    } catch (err) {
      console.warn("Lenis init failed, using built-in smooth scroller", err);
    }
  }

  if (window.__momentumScrollBound) return;
  window.__momentumScrollBound = true;

  let targetY = window.scrollY;
  let currentY = window.scrollY;
  let isMoving = false;

  function updateMomentum() {
    if (!isMoving) return;
    const diff = targetY - currentY;
    if (Math.abs(diff) < 0.5) {
      currentY = targetY;
      window.scrollTo(0, currentY);
      isMoving = false;
      return;
    }
    currentY += diff * 0.14;
    window.scrollTo(0, currentY);
    requestAnimationFrame(updateMomentum);
  }

  window.addEventListener("wheel", (e) => {
    if (isAdminRoute() || e.ctrlKey || e.altKey) return;
    const scrollableParent = e.target.closest(".workspace, .livechat-thread-list, .livechat-messages, .table-wrap, .detail-panel");
    if (scrollableParent) return;

    e.preventDefault();
    const delta = e.deltaY;
    const maxScroll = Math.max(0, document.documentElement.scrollHeight - window.innerHeight);
    targetY = Math.max(0, Math.min(maxScroll, targetY + delta * 1.05));

    if (!isMoving) {
      currentY = window.scrollY;
      isMoving = true;
      requestAnimationFrame(updateMomentum);
    }
  }, { passive: false });

  window.addEventListener("scroll", () => {
    if (!isMoving) {
      targetY = window.scrollY;
      currentY = window.scrollY;
    }
  }, { passive: true });
}

function bindPublicRouter() {
  if (publicRouterBound) return;
  publicRouterBound = true;

  initSmoothMomentumScroll();

  const backToTopBtn = document.getElementById("backToTopBtn");
  if (backToTopBtn) {
    backToTopBtn.addEventListener("click", () => {
      if (lenisInstance) {
        lenisInstance.scrollTo(0, { duration: 1.2 });
      } else {
        window.scrollTo({ top: 0, behavior: "smooth" });
      }
    });
  }

  const sections = ["home", "features", "flow", "safety"];

  function setActiveNavSection(sectionId) {
    document.querySelectorAll(".pill-nav-dock .pill-nav-item, .public-nav-links a").forEach((navLink) => {
      const href = navLink.getAttribute("href") || "";
      const isCurrent = href === `#${sectionId}` || href === `/${sectionId === "home" ? "" : sectionId}`;
      navLink.classList.toggle("is-active", isCurrent);
    });
  }

  document.addEventListener("click", (event) => {
    const anchor = event.target.closest("a[href^='#']");
    if (anchor && anchor.getAttribute("href").length > 1) {
      const targetId = anchor.getAttribute("href").slice(1);
      const targetEl = document.getElementById(targetId);
      if (targetEl) {
        event.preventDefault();

        setActiveNavSection(targetId);

        if (lenisInstance) {
          lenisInstance.scrollTo(targetEl, { offset: -70, duration: 1.1 });
        } else {
          targetEl.scrollIntoView({ behavior: "smooth", block: "start" });
        }
        return;
      }
    }

    const link = event.target.closest("[data-public-page]");
    if (!link || isAdminRoute()) return;

    event.preventDefault();
    setPublicPage(link.dataset.publicPage || "home");
  });

  function updateScrollspy() {
    if (isAdminRoute()) return;

    if (backToTopBtn) {
      if (window.scrollY > 280) {
        backToTopBtn.classList.add("is-visible");
      } else {
        backToTopBtn.classList.remove("is-visible");
      }
    }

    if (window.scrollY < 100) {
      setActiveNavSection("home");
      return;
    }

    if (window.innerHeight + window.scrollY >= document.documentElement.scrollHeight - 60) {
      setActiveNavSection("safety");
      return;
    }

    const triggerY = window.innerHeight * 0.36;
    let currentSection = "home";

    for (let i = 0; i < sections.length; i++) {
      const id = sections[i];
      const el = document.getElementById(id);
      if (el) {
        const rect = el.getBoundingClientRect();
        if (rect.top <= triggerY && rect.bottom > triggerY) {
          currentSection = id;
          break;
        }
      }
    }

    setActiveNavSection(currentSection);
  }

  window.addEventListener("scroll", updateScrollspy, { passive: true });
  window.addEventListener("resize", updateScrollspy, { passive: true });
  updateScrollspy();

  window.addEventListener("popstate", () => {
    if (!isAdminRoute()) setPublicPage(readPublicPageFromUrl(), false);
  });
}

let katupChatbotBound = false;

function initPublicAnimations() {
  refreshIcons();
  bindPublicRouter();
  initKatupChatbot();

  const revealItems = Array.from(document.querySelectorAll(".public-site .reveal"));
  if (!revealItems.length) return;

  if (!("IntersectionObserver" in window)) {
    revealItems.forEach((item) => item.classList.add("is-visible"));
    return;
  }

  if (!publicRevealObserver) {
    publicRevealObserver = new IntersectionObserver((entries) => {
      entries.forEach((entry) => {
        if (!entry.isIntersecting) return;
        entry.target.classList.add("is-visible");
      });
    }, {
      rootMargin: "0px 0px -8% 0px",
      threshold: 0.12
    });
  }

  revealItems.forEach((item) => {
    publicRevealObserver.observe(item);
  });
}

function initKatupChatbot() {
  if (katupChatbotBound) return;

  const launcherBtn = document.getElementById("katupLauncherBtn");
  const chatBox = document.getElementById("katupChatBox");
  const closeBtn = document.getElementById("katupCloseBtn");
  const chatForm = document.getElementById("katupChatForm");
  const chatInput = document.getElementById("katupChatInput");
  const chatMessages = document.getElementById("katupChatMessages");
  const suggestions = document.getElementById("katupSuggestions");
  const langBmBtn = document.getElementById("katupLangBm");
  const langEnBtn = document.getElementById("katupLangEn");
  const welcomeMsg = document.getElementById("katupWelcomeMsg");
  const helperLabel = document.getElementById("katupHelperLabel");

  if (!launcherBtn || !chatBox) return;
  katupChatbotBound = true;

  let currentLang = "bm";

  const suggestionPresets = {
    bm: [
      { label: "Cara guna SOS?", prompt: "Bagaimana cara fungsi SOS ResQTap berfungsi?" },
      { label: "Jejak Live Location?", prompt: "Bagaimanakah Live Location berfungsi dalam ResQTap?" },
      { label: "Apa itu Rooms?", prompt: "Ceritakan tentang fungsi Rooms dan koordinasi kecemasan." },
      { label: "Muat turun APK", prompt: "Bagaimana cara muat turun APK ResQTap?" },
      { label: "Siapa cipta ResQTap?", prompt: "Siapakah pembangun projek ResQTap ini?" },
      { label: "Hospital berdekatan", prompt: "Bagaimana cari hospital atau klinik berdekatan?" }
    ],
    en: [
      { label: "How SOS works?", prompt: "How does ResQTap SOS alert work?" },
      { label: "Live GPS tracking?", prompt: "How does Live Location tracking work in ResQTap?" },
      { label: "What are Channels?", prompt: "Tell me about Channels and emergency coordination." },
      { label: "Download APK", prompt: "How do I download the ResQTap APK?" },
      { label: "Who built ResQTap?", prompt: "Who is the creator and developer of ResQTap?" }
    ]
  };

  function updateLanguage(lang, announce = true) {
    currentLang = lang;
    if (langBmBtn) langBmBtn.classList.toggle("is-active", lang === "bm");
    if (langEnBtn) langEnBtn.classList.toggle("is-active", lang === "en");

    if (chatInput) {
      chatInput.placeholder = lang === "bm"
        ? "Tanya apa-apa tentang ResQTap..."
        : "Ask anything about ResQTap...";
    }
    if (helperLabel) {
      helperLabel.textContent = lang === "bm"
        ? "Cadangan Soalan Pintar"
        : "Quick Suggestions";
    }
    if (welcomeMsg) {
      welcomeMsg.innerHTML = lang === "bm"
        ? 'Hai, saya <strong>Katup</strong>, pembantu pintar ResQTap. Tanya saya apa-apa tentang fungsi SOS, perkongsian lokasi, atau bilik kecemasan.'
        : 'Hello, I am <strong>Katup</strong>, ResQTap AI assistant. Ask me anything about SOS features, live location sharing, or emergency rooms.';
    }

    if (suggestions) {
      const items = suggestionPresets[lang] || suggestionPresets.bm;
      suggestions.innerHTML = items
        .map((item) => `<button class="katup-pill-btn" type="button" data-prompt="${escapeHtml(item.prompt)}">${escapeHtml(item.label)}</button>`)
        .join("");
    }

    if (announce) {
      if (lang === "bm") {
        appendMessage("**Bahasa Melayu dipilih.** Saya sedia menjawab sebarang soalan mengenai ResQTap.", "bot");
      } else {
        appendMessage("**English selected.** I am ready to answer any questions about ResQTap.", "bot");
      }
    }
  }

  if (langBmBtn) {
    langBmBtn.addEventListener("click", (e) => {
      e.preventDefault();
      updateLanguage("bm");
    });
  }

  if (langEnBtn) {
    langEnBtn.addEventListener("click", (e) => {
      e.preventDefault();
      updateLanguage("en");
    });
  }

  function toggleChat(forceOpen) {
    const shouldOpen = typeof forceOpen === "boolean" ? forceOpen : !chatBox.classList.contains("is-open");
    if (shouldOpen) {
      chatBox.classList.add("is-open");
      chatBox.setAttribute("aria-hidden", "false");
      setTimeout(() => {
        if (chatInput) chatInput.focus();
        scrollChatToBottom();
      }, 200);
    } else {
      chatBox.classList.remove("is-open");
      chatBox.setAttribute("aria-hidden", "true");
    }
  }

  launcherBtn.addEventListener("click", (e) => {
    e.preventDefault();
    e.stopPropagation();
    toggleChat();
  });

  if (closeBtn) {
    closeBtn.addEventListener("click", (e) => {
      e.preventDefault();
      e.stopPropagation();
      toggleChat(false);
    });
  }

  document.addEventListener("click", (e) => {
    if (chatBox.classList.contains("is-open")) {
      if (!chatBox.contains(e.target) && !launcherBtn.contains(e.target)) {
        toggleChat(false);
      }
    }
  });

  function handleInnerWheel(e) {
    if (!chatMessages) return;
    e.stopPropagation();
    chatMessages.scrollTop += e.deltaY;
    e.preventDefault();
  }

  chatMessages.addEventListener("wheel", handleInnerWheel, { passive: false });
  chatBox.addEventListener("wheel", (e) => {
    if (chatMessages && e.target !== chatMessages && !chatMessages.contains(e.target)) {
      handleInnerWheel(e);
    }
  }, { passive: false });

  function scrollChatToBottom() {
    if (chatMessages) {
      chatMessages.scrollTop = chatMessages.scrollHeight;
    }
  }

  function appendMessage(text, sender = "bot") {
    if (!chatMessages) return;
    const msgDiv = document.createElement("div");
    msgDiv.className = `katup-msg katup-msg-${sender}`;

    const bubble = document.createElement("div");
    bubble.className = "katup-msg-bubble";

    const safeText = String(text || "")
      .replace(/&/g, "&amp;")
      .replace(/</g, "&lt;")
      .replace(/>/g, "&gt;")
      .replace(/"/g, "&quot;")
      .replace(/'/g, "&#39;");

    const formatted = safeText
      .replace(/\*\*(.*?)\*\*/g, "<strong>$1</strong>")
      .replace(/\n/g, "<br>");
    bubble.innerHTML = `<p>${formatted}</p>`;

    msgDiv.appendChild(bubble);
    chatMessages.appendChild(msgDiv);
    scrollChatToBottom();
  }

  function showTypingIndicator() {
    if (!chatMessages) return;
    const typing = document.createElement("div");
    typing.id = "katupTypingIndicator";
    typing.className = "katup-typing-indicator";
    typing.innerHTML = "<span></span><span></span><span></span>";
    chatMessages.appendChild(typing);
    scrollChatToBottom();
  }

  function removeTypingIndicator() {
    const typing = document.getElementById("katupTypingIndicator");
    if (typing) typing.remove();
  }

  function detectQueryLanguage(text) {
    const t = text.toLowerCase();
    const enTokens = ["how", "what", "where", "who", "when", "why", "can", "is", "are", "tell", "download", "apk", "help", "thanks", "thank you", "location", "hospital", "creator", "developer", "emergency"];
    const bmTokens = ["apa", "bagaimana", "macam", "cara", "siapa", "mana", "kenapa", "bila", "boleh", "tolong", "muat turun", "cipta", "bilik", "lokasi", "terima kasih", "salam", "khabar"];

    let enScore = 0;
    let bmScore = 0;

    enTokens.forEach((k) => { if (t.includes(k)) enScore++; });
    bmTokens.forEach((k) => { if (t.includes(k)) bmScore++; });

    if (enScore > bmScore) return "en";
    if (bmScore > enScore) return "bm";
    return currentLang;
  }

  function getKatupResponse(query) {
    const q = query.toLowerCase().trim();
    const detectedLang = detectQueryLanguage(query);

    if (q.includes("speak english") || q.includes("in english") || q.includes("use english")) {
      updateLanguage("en", false);
      return "Sure! I have switched to **English**. Feel free to ask me anything about ResQTap.";
    }
    if (q.includes("cakap melayu") || q.includes("dalam melayu") || q.includes("bahasa melayu")) {
      updateLanguage("bm", false);
      return "Baiklah, saya telah bertukar kepada **Bahasa Melayu**. Sila tanya apa sahaja tentang ResQTap.";
    }

    if (detectedLang === "en") {

      if (q.includes("sos") || q.includes("alert") || q.includes("emergency") || q.includes("help") || q.includes("panic")) {
        return "**ResQTap SOS feature** lets users broadcast instant emergency alerts with a single tap.\n\nIt activates an emergency alarm, broadcasts real-time GPS coordinates directly to your **Room members**, and immediately notifies administrators.";
      }
      if (q.includes("location") || q.includes("gps") || q.includes("tracking") || q.includes("live") || q.includes("map")) {
        return "**Live Location Tracking** in ResQTap is powered by Firebase Realtime Database. When an emergency is active, your trusted Room members and responders can track your position continuously on the map.";
      }
      if (q.includes("channel") || q.includes("room") || q.includes("group") || q.includes("member") || q.includes("contact")) {
        return "**Channels** connect you with your trusted circle and emergency contacts.\n\nEach Channel displays live safety states, battery percentage, and latest GPS coordinates so family and friends can stay aware.";
      }
      if (q.includes("download") || q.includes("apk") || q.includes("install") || q.includes("app")) {
        return "You can download the **ResQTap APK** directly for free. Click the **Download APK** button on the home section, or download the APK file from this website.";
      }
      if (q.includes("who built") || q.includes("creator") || q.includes("developer") || q.includes("chesster") || q.includes("author") || q.includes("project")) {
        return "**ResQTap** was created and engineered by **Chesster** as a Final Year Project (OneTapSOS). Built with Android (Java) and Firebase Realtime Backend to safeguard lives during critical moments.";
      }
      if (q.includes("hospital") || q.includes("clinic") || q.includes("medical") || q.includes("doctor")) {
        return "ResQTap includes a built-in **Nearby Hospital** feature that automatically discovers nearby healthcare centers and emergency clinics with quick navigation.";
      }
      if (q.includes("hi") || q.includes("hello") || q.includes("hey") || q.includes("how are you")) {
        return "Hello, I am **Katup**, your assistant for ResQTap. How can I assist you with SOS alerts, live tracking, or emergency rooms today?";
      }
      if (q.includes("thank") || q.includes("thanks")) {
        return "You are very welcome. Stay safe, and let me know if you need anything else.";
      }

      return "Thank you for asking. I am **Katup**, your ResQTap assistant. You can ask me about **SOS alerts**, **Live Location sharing**, **Emergency Channels**, or **how to download the APK**.";
    }

    if (q.includes("sos") || q.includes("kecemasan") || q.includes("alert") || q.includes("bantuan") || q.includes("panik")) {
      return "Fungsi **SOS ResQTap** membolehkan anda menghantar amaran kecemasan pantas dalam 1 sentuhan.\n\nIa akan membunyikan amaran kecemasan, menghantar koordinat GPS secara langsung ke semua ahli **Room** anda, dan memberi amaran kepada admin dengan serta-merta.";
    }
    if (q.includes("location") || q.includes("lokasi") || q.includes("gps") || q.includes("live tracking") || q.includes("jejak") || q.includes("peta")) {
      return "**Live Location Tracking** ResQTap menggunakan Firebase Realtime Database untuk menyegerakkan lokasi GPS anda secara langsung. Ahli Room dan responder boleh menjejaki pergerakan anda pada peta secara tepat semasa insiden berlaku.";
    }
    if (q.includes("channel") || q.includes("room") || q.includes("saluran") || q.includes("bilik") || q.includes("group") || q.includes("ahli") || q.includes("contact") || q.includes("kenalan")) {
      return "**Fungsi Channels** menghubungkan anda dengan kenalan kecemasan dipercayai (trusted contacts).\n\nSetiap Channel memaparkan status keselamatan ahli, tahap bateri telefon, dan lokasi terkini bagi memastikan semua orang sentiasa selamat.";
    }
    if (q.includes("download") || q.includes("muat turun") || q.includes("apk") || q.includes("install") || q.includes("pasang")) {
      return "Anda boleh muat turun **ResQTap APK** secara percuma. Sila klik butang **Download APK** di halaman utama, atau muat turun fail APK terus dari laman ini.";
    }
    if (q.includes("chesster") || q.includes("who built") || q.includes("siapa cipta") || q.includes("pembangun") || q.includes("developer") || q.includes("story") || q.includes("pasukan") || q.includes("projek")) {
      return "**ResQTap** dibangunkan oleh **Chesster** sebagai projek tahun akhir (Final Year Project - OneTapSOS). Dibina dengan Android (Java) dan Firebase Realtime Backend untuk menyelamatkan nyawa semasa waktu kecemasan.";
    }
    if (q.includes("hospital") || q.includes("klinik") || q.includes("medical") || q.includes("rawatan") || q.includes("doktor")) {
      return "ResQTap dilengkapi fungsi **Nearby Hospital** untuk mencari pusat rawatan dan hospital berdekatan secara automatik berserta navigasi terus dari aplikasi.";
    }
    if (q.includes("hai") || q.includes("hello") || q.includes("apa khabar") || q.includes("hi") || q.includes("salam") || q.includes("hey")) {
      return "Hai, saya **Katup**, pembantu pintar anda untuk ResQTap. Ada apa-apa soalan tentang fungsi SOS, perkongsian lokasi, atau bilik kecemasan yang boleh saya bantu?";
    }
    if (q.includes("terima kasih") || q.includes("tq") || q.includes("thanks")) {
      return "Sama-sama. Kekal selamat dan beritahu saya jika ada apa-apa soalan lain.";
    }

    return "Terima kasih kerana bertanya. Saya **Katup**, pembantu pintar ResQTap. Anda boleh bertanya saya tentang **fungsi SOS**, **perkongsian Live Location**, **Channels**, atau cara **muat turun APK** ResQTap.";
  }

  function handleUserSend(text) {
    if (!text || !text.trim()) return;
    const cleanText = text.trim();

    appendMessage(cleanText, "user");
    if (chatInput) chatInput.value = "";

    showTypingIndicator();

    setTimeout(() => {
      removeTypingIndicator();
      const reply = getKatupResponse(cleanText);
      appendMessage(reply, "bot");
    }, 550);
  }

  if (chatForm) {
    chatForm.addEventListener("submit", (e) => {
      e.preventDefault();
      if (chatInput) handleUserSend(chatInput.value);
    });
  }

  if (suggestions) {
    suggestions.addEventListener("click", (e) => {
      const btn = e.target.closest(".katup-pill-btn");
      if (!btn) return;
      const prompt = btn.getAttribute("data-prompt") || btn.innerText;
      handleUserSend(prompt);
    });
  }

  updateLanguage("bm", false);
}

let uiAudioCtx = null;
function playUiClickSound() {
  try {
    if (!uiAudioCtx) {
      const AudioCtx = window.AudioContext || window.webkitAudioContext;
      if (AudioCtx) uiAudioCtx = new AudioCtx();
    }
    if (!uiAudioCtx) return;
    if (uiAudioCtx.state === "suspended") {
      uiAudioCtx.resume();
    }

    const now = uiAudioCtx.currentTime;
    const osc = uiAudioCtx.createOscillator();
    const gain = uiAudioCtx.createGain();

    osc.type = "sine";
    osc.frequency.setValueAtTime(800, now);
    osc.frequency.exponentialRampToValueAtTime(320, now + 0.035);

    gain.gain.setValueAtTime(0.09, now);
    gain.gain.exponentialRampToValueAtTime(0.0001, now + 0.035);

    osc.connect(gain);
    gain.connect(uiAudioCtx.destination);

    osc.start(now);
    osc.stop(now + 0.04);
  } catch (err) {

  }
}

document.addEventListener("click", (e) => {
  const clickable = e.target.closest("button, a, .katup-pill-btn, .katup-lang-btn, .public-nav-link, .hero-btn, input[type='submit'], [role='button']");
  if (clickable) {
    playUiClickSound();
  }
}, true);

if (document.readyState === "loading") {
  document.addEventListener("DOMContentLoaded", initKatupChatbot);
} else {
  initKatupChatbot();
}

const state = {
  currentUser: null,
  activeView: "dashboard",
  search: "",
  dashboardRange: "today",
  userFilter: "all",
  authUsers: [],
  authUsersPollTimer: null,
  users: {},
  rooms: {},
  roomTombstones: {},
  userRooms: {},
  admins: {},
  responders: {},
  legacyAlerts: {},
  incidentReports: {},
  adminAuditLogs: {},
  callLogs: {},
  adminNotifications: {},
  supportChats: {},
  aiChats: {},
  highlights: {},
  medicalCards: {},
  servedCases: new Set(),
  selectedSosSessionId: "",
  soslivechatSending: false,
  selectedChatUid: "",
  selectedAiChatUid: "",
  livechatAttachment: null,
  livechatAttachmentKind: "",
  livechatSending: false,
  livechatTypingActive: false,
  livechatTypingUid: "",
  livechatTypingLastSentAt: 0,
  livechatTypingTimer: null,
  notificationCategory: "all",
  notificationPage: 1,
  notificationPageSize: 10,
  dashboardSignature: "",
  dashboardMetricValues: {},
  unsubscribers: [],
  selected: null,
  detailWidth: 318,
  lastSyncAt: 0,
  navSeenSignatures: readNavSeenSignatures(),
  aiChatCleanupTimer: null,
  aiChatCleanupRunning: false
};

const DETAIL_PANEL_MIN_WIDTH = 318;
const DETAIL_PANEL_MAX_WIDTH = 760;
const DETAIL_MAIN_MIN_WIDTH = 520;

function isRecord(value) {
  return value !== null && typeof value === "object" && !Array.isArray(value);
}

function asRecord(value) {
  return isRecord(value) ? value : {};
}

function entries(value) {
  return Object.entries(asRecord(value));
}

function readNavSeenSignatures() {
  try {
    const raw = window.localStorage.getItem(NAV_SEEN_STORAGE_KEY);
    const parsed = raw ? JSON.parse(raw) : {};
    return isRecord(parsed) ? parsed : {};
  } catch (error) {
    return {};
  }
}

function saveNavSeenSignatures() {
  try {
    window.localStorage.setItem(NAV_SEEN_STORAGE_KEY, JSON.stringify(state.navSeenSignatures));
  } catch (error) {
    console.warn(error);
  }
}

function signatureFromItems(items) {
  return items
    .map((item) => text(item).trim())
    .filter(Boolean)
    .sort()
    .join("|");
}

function text(value) {
  return String(value === null || value === undefined ? "" : value);
}

function escapeHtml(value) {
  return text(value)
    .replace(/&/g, "&amp;")
    .replace(/</g, "&lt;")
    .replace(/>/g, "&gt;")
    .replace(/"/g, "&quot;")
    .replace(/'/g, "&#039;");
}

function icon(name) {
  return `<i data-lucide="${escapeHtml(name)}" aria-hidden="true"></i>`;
}

function refreshIcons() {
  if (window.lucide && typeof window.lucide.createIcons === "function") {
    window.lucide.createIcons({
      attrs: {
        "stroke-width": 2.4,
        "aria-hidden": "true"
      }
    });
  }
}

function millis(value) {
  if (typeof value === "number" && Number.isFinite(value)) return value;
  if (typeof value === "string" && value.trim() !== "") {
    const parsed = Number(value);
    if (Number.isFinite(parsed)) return parsed;
  }
  return 0;
}

function formatDate(value) {
  const ms = millis(value);
  if (!ms) return "-";
  return new Intl.DateTimeFormat(undefined, {
    year: "numeric",
    month: "short",
    day: "2-digit",
    hour: "2-digit",
    minute: "2-digit"
  }).format(new Date(ms));
}

function formatTimeOnly(value) {
  const ms = millis(value);
  if (!ms) return "-";
  return new Intl.DateTimeFormat(undefined, {
    hour: "numeric",
    minute: "2-digit"
  }).format(new Date(ms));
}

function ageLabel(value) {
  const ms = millis(value);
  if (!ms) return "-";
  const diff = Math.max(0, Date.now() - ms);
  const min = Math.floor(diff / 60000);
  if (min < 1) return "just now";
  if (min < 60) return `${min}m ago`;
  const hours = Math.floor(min / 60);
  if (hours < 24) return `${hours}h ago`;
  const days = Math.floor(hours / 24);
  return `${days}d ago`;
}

function futureAgeLabel(value) {
  const ms = millis(value);
  if (!ms) return "-";
  const diff = ms - Date.now();
  if (diff <= 0) return "expired";
  const sec = Math.ceil(diff / 1000);
  if (sec < 60) return `${sec}s`;
  const min = Math.ceil(sec / 60);
  if (min < 60) return `${min}m`;
  const hours = Math.ceil(min / 60);
  if (hours < 24) return `${hours}h`;
  const days = Math.ceil(hours / 24);
  return `${days}d`;
}

function formatNumber(value) {
  return new Intl.NumberFormat(undefined).format(Number(value) || 0);
}

function initials(name, email, uid) {
  const source = text(name).trim() || text(email).trim() || text(uid).trim();
  const parts = source.split(/\s+/).filter(Boolean);
  if (parts.length >= 2) return `${parts[0][0]}${parts[1][0]}`.toUpperCase();
  return source.slice(0, 2).toUpperCase() || "OT";
}

function avatarHtml(item, uid) {
  const name = text(item.name || item.senderName || item.fromName);
  const email = text(item.email);
  let photoUrl = text(item.photoUrl || item.photoUri || item.photoURL || item.photo).trim();
  const b64 = text(item.photoB64).trim();
  const label = escapeHtml(initials(name, email, uid));

  // Jika photoUrl adalah raw base64 string (seperti JPEG / PNG dari kamera Android)
  if (photoUrl && !photoUrl.startsWith("http://") && !photoUrl.startsWith("https://") && !photoUrl.startsWith("data:")) {
    if (photoUrl.startsWith("/9j/") || photoUrl.startsWith("iVBORw0KGgo") || photoUrl.length > 100) {
      photoUrl = `data:image/jpeg;base64,${photoUrl}`;
    }
  }

  let src = photoUrl;
  if (!src && b64) {
    src = b64.startsWith("data:") ? b64 : `data:image/jpeg;base64,${b64}`;
  }

  if (src) {
    return `<span class="avatar"><img src="${escapeHtml(src)}" alt="" referrerpolicy="no-referrer" onerror="this.onerror=null;this.parentElement.className='avatar avatar-default';this.parentElement.innerHTML='<span title=\\'${label}\\'>${label}</span>';"></span>`;
  }
  return `<span class="avatar avatar-default" title="${label}">${icon("user-round")}</span>`;
}

function userName(uid) {
  if (!uid) return "";
  const admin = asRecord(state.admins?.[uid]);
  const user = asRecord(state.users?.[uid]);
  const responder = asRecord(state.responders?.[uid]);
  return text(admin.name || user.name || responder.name || user.email || admin.email || uid).trim() || uid;
}

function validPathSegment(value) {
  const v = text(value).trim();
  return v !== "" && !INVALID_KEY.test(v);
}

function safeStorageName(value) {
  const clean = text(value)
    .trim()
    .replace(/[\\/:*?"<>|#%\[\]]+/g, "_")
    .replace(/\s+/g, "_")
    .slice(0, 90);
  return clean || "attachment";
}

function loadImageFromUrl(url) {
  return new Promise((resolve, reject) => {
    const image = new Image();
    image.onload = () => resolve(image);
    image.onerror = () => reject(new Error("Unable to load image."));
    image.src = url;
  });
}

async function imageFileToInlineDataUrl(file) {
  const objectUrl = URL.createObjectURL(file);
  try {
    const image = await loadImageFromUrl(objectUrl);
    const maxSide = 1280;
    const scale = Math.min(1, maxSide / Math.max(image.naturalWidth || 1, image.naturalHeight || 1));
    const width = Math.max(1, Math.round((image.naturalWidth || 1) * scale));
    const height = Math.max(1, Math.round((image.naturalHeight || 1) * scale));
    const canvas = document.createElement("canvas");
    canvas.width = width;
    canvas.height = height;
    const ctx = canvas.getContext("2d");
    ctx.fillStyle = "#ffffff";
    ctx.fillRect(0, 0, width, height);
    ctx.drawImage(image, 0, 0, width, height);
    return canvas.toDataURL("image/jpeg", 0.82);
  } finally {
    URL.revokeObjectURL(objectUrl);
  }
}

function canInlineImage(file) {
  const mime = text(file && file.type).toLowerCase();
  return mime.startsWith("image/")
    && mime !== "image/gif"
    && mime !== "image/svg+xml";
}

function isAdminValue(value) {
  return value === true
    || value === "true"
    || value === 1
    || (isRecord(value) && (value.active === true || value.active === "true" || value.active === 1));
}

function isAdminAccount(uid, userRecord = {}) {
  if (!uid) return false;
  if (state.admins && isAdminValue(state.admins[uid])) return true;
  const u = isRecord(userRecord) ? userRecord : asRecord(state.users && state.users[uid]);
  const role = text(u.role).toLowerCase();
  if (role === "admin" || role === "superadmin" || role === "super_admin") return true;
  const email = text(u.email || (state.currentUser && state.currentUser.uid === uid ? state.currentUser.email : "")).toLowerCase();
  if (email.endsWith("@resqtap.com") || email.endsWith("@resqtap.my") || email.endsWith("@resqtap.org")) return true;
  return false;
}

async function isCurrentUserAdmin(uid) {
  try {
    const { data: profile } = await supabase
      .from("profiles")
      .select("role")
      .eq("id", uid)
      .maybeSingle();
    if (profile && (profile.role === "admin" || profile.role === "ADMIN")) {
      return true;
    }
  } catch (e) {
    console.warn("[SUPABASE_ADMIN_CHECK_ERR]", e);
  }

  const adminSnapshot = await get(ref(db, `admins/${uid}`));
  if (isAdminValue(adminSnapshot.val())) return true;

  const activeSnapshot = await get(ref(db, `admins/${uid}/active`));
  return isAdminValue(activeSnapshot.val());
}

function audienceLabel(audience) {
  const labels = {
    all: "All app users",
    live: "Online users",
    admins: "Admins"
  };
  return labels[audience] || labels.all;
}

function setScreen(name) {
  enterAdminRoute();
  document.documentElement.classList.remove("admin-route-loading");
  const loader = document.getElementById("adminRouteLoader");
  if (loader) loader.classList.add("hidden");

  if (name === "app") {
    try {
      localStorage.setItem("resqtap_admin_session", "active");
      document.documentElement.classList.add("has-admin-session");
    } catch (e) {}
  } else {
    try {
      localStorage.removeItem("resqtap_admin_session");
      document.documentElement.classList.remove("has-admin-session");
    } catch (e) {}
  }

  if (els.authScreen) els.authScreen.classList.toggle("hidden", name !== "auth");
  if (els.deniedScreen) els.deniedScreen.classList.toggle("hidden", name !== "denied");
  if (els.appShell) els.appShell.classList.toggle("hidden", name !== "app");
}

function showToast(message) {
  els.toast.textContent = message;
  els.toast.classList.remove("hidden");
  window.clearTimeout(showToast.timer);
  showToast.timer = window.setTimeout(() => els.toast.classList.add("hidden"), 3200);
}

function setSyncStatus(message) {
  els.syncStatus.textContent = message;
}

function cleanupDataListeners() {
  state.unsubscribers.forEach((unsubscribe) => {
    try {
      unsubscribe();
    } catch (error) {
      console.warn(error);
    }
  });
  state.unsubscribers = [];
  if (state.aiChatCleanupTimer) {
    window.clearInterval(state.aiChatCleanupTimer);
    state.aiChatCleanupTimer = null;
  }
  if (state.authUsersPollTimer) {
    window.clearInterval(state.authUsersPollTimer);
    state.authUsersPollTimer = null;
  }
  if (state.supabasePollTimer) {
    window.clearInterval(state.supabasePollTimer);
    state.supabasePollTimer = null;
  }
}

async function syncFromSupabase() {
  try {
    // 1. Profiles & Admins
    const { data: profiles } = await supabase.from("profiles").select("*");
    if (Array.isArray(profiles) && profiles.length > 0) {
      const uMap = {};
      const aMap = {};
      profiles.forEach((p) => {
        let pUrl = (p.photo_url || "").trim();
        if (pUrl && !pUrl.startsWith("http://") && !pUrl.startsWith("https://") && !pUrl.startsWith("data:")) {
          pUrl = `data:image/jpeg;base64,${pUrl}`;
        }
        const lat = Number(p.latitude !== undefined && p.latitude !== null ? p.latitude : p.lat) || 0;
        const lng = Number(p.longitude !== undefined && p.longitude !== null ? p.longitude : p.lng) || 0;
        const uTime = p.last_seen ? new Date(p.last_seen).getTime() : (p.updated_at ? new Date(p.updated_at).getTime() : Date.now());
        const isAdmin = p.role === "admin" || p.role === "ADMIN" || (p.email && (p.email.endsWith("@resqtap.com") || p.email.endsWith("@resqtap.my") || p.email.endsWith("@resqtap.org")));
        if (isAdmin) {
          aMap[p.id] = {
            active: true,
            email: p.email || "",
            name: p.full_name || "Admin",
            role: "admin",
            updatedAt: p.updated_at ? new Date(p.updated_at).getTime() : Date.now()
          };
        } else {
          uMap[p.id] = {
            name: p.full_name || "User",
            email: p.email || "",
            phone: p.phone || "",
            photoUrl: pUrl,
            photo_url: pUrl,
            role: p.role || "user",
            lat,
            lng,
            latitude: lat,
            longitude: lng,
            isOnline: Boolean(p.is_online),
            batteryPct: Number.isFinite(Number(p.battery_pct)) ? Number(p.battery_pct) : null,
            source: "supabase",
            updatedAt: uTime,
            locationUpdatedAt: uTime,
            lastSeen: uTime
          };
        }
      });
      state.users = { ...uMap, ...state.users };
      state.admins = { ...aMap, ...state.admins };
      // Purge any admin accounts from state.users
      Object.keys(state.users).forEach((uId) => {
        if (isAdminAccount(uId, state.users[uId])) {
          delete state.users[uId];
        }
      });
    }

    // 2. Medical Cards
    const { data: medCards } = await supabase.from("medical_cards").select("*");
    if (Array.isArray(medCards)) {
      const mcMap = {};
      medCards.forEach((mc) => {
        mcMap[mc.user_id] = mc;
        if (state.users[mc.user_id]) {
          state.users[mc.user_id].bloodType = mc.blood_type || state.users[mc.user_id].bloodType;
          state.users[mc.user_id].allergies = mc.allergies || state.users[mc.user_id].allergies;
          state.users[mc.user_id].existingConditions = mc.medical_conditions || state.users[mc.user_id].existingConditions;
          state.users[mc.user_id].medications = mc.medications || state.users[mc.user_id].medications;
          state.users[mc.user_id].notes = mc.notes || state.users[mc.user_id].notes;
          if (Array.isArray(mc.emergency_contacts) && mc.emergency_contacts.length > 0) {
            state.users[mc.user_id].emergencyContacts = mc.emergency_contacts;
          }
        }
      });
      state.medicalCards = mcMap;
    }

    // 3. Highlights
    const { data: hl } = await supabase.from("highlights").select("*").order("display_order", { ascending: true });
    if (Array.isArray(hl) && hl.length > 0) {
      const hlMap = {};
      hl.forEach((h) => {
        hlMap[h.id] = {
          id: h.id,
          title: h.title,
          imageUrl: h.image_url,
          actionUrl: h.action_url,
          order: h.display_order,
          active: h.active !== false
        };
      });
      state.highlights = { ...hlMap, ...state.highlights };
    }

    // 5. Rooms
    const { data: rm } = await supabase.from("rooms").select("*");
    if (Array.isArray(rm) && rm.length > 0) {
      const rmMap = {};
      rm.forEach((r) => {
        rmMap[r.id] = {
          name: r.name || r.id,
          creatorUid: r.creator_uid
        };
      });
      state.rooms = { ...rmMap, ...state.rooms };
    }

    // 6. Room Tombstones
    const { data: rt } = await supabase.from("room_tombstones").select("*");
    if (Array.isArray(rt) && rt.length > 0) {
      const rtMap = {};
      rt.forEach((t) => {
        rtMap[t.room_code] = {
          by: t.deleted_by,
          source: t.source,
          deletedAt: Number(t.deleted_at_ms || Date.now())
        };
      });
      state.roomTombstones = { ...rtMap, ...state.roomTombstones };
    }

    // 7. Incident Reports
    const { data: rep } = await supabase.from("reports").select("*").order("created_at", { ascending: false });
    if (Array.isArray(rep) && rep.length > 0) {
      const repMap = {};
      rep.forEach((r) => {
        repMap[r.id] = {
          id: r.id,
          title: r.title,
          description: r.description,
          category: r.category,
          latitude: r.latitude,
          longitude: r.longitude,
          address: r.address,
          status: r.status,
          senderName: r.user_name || "User",
          senderUid: r.user_id,
          createdAt: r.created_at ? new Date(r.created_at).getTime() : Date.now()
        };
      });
      state.incidentReports = { ...repMap, ...state.incidentReports };
    }

    state.lastSyncAt = Date.now();
    setSyncStatus(`Live Sync: ${new Date(state.lastSyncAt).toLocaleTimeString()}`);
    render();
  } catch (err) {
    console.warn("[SUPABASE_SYNC_ERROR]", err);
  }
}

async function loadAuthUsers() {
  try {
    const list = await listAllAuthUsers();
    if (Array.isArray(list)) {
      state.authUsers = list;
      render();
    }
  } catch (err) {
    console.warn("[ADMIN] Could not fetch Auth users via bridge:", err.message);
  }
}

function subscribe(path, callback) {
  const unsubscribe = onValue(
    ref(db, path),
    (snapshot) => {
      callback(snapshot.val());
      state.lastSyncAt = Date.now();
      setSyncStatus(`Live Sync: ${new Date(state.lastSyncAt).toLocaleTimeString()}`);
      render();
    },
    (error) => {
      setSyncStatus("Sync blocked");
      showToast(error.message || "Unable to read database.");
    }
  );
  state.unsubscribers.push(unsubscribe);
}

function startDataListeners() {
  cleanupDataListeners();
  setSyncStatus("Syncing");
  loadAuthUsers();
  state.authUsersPollTimer = window.setInterval(loadAuthUsers, 30000);

  // Supabase Data Sync
  syncFromSupabase();
  state.supabasePollTimer = window.setInterval(syncFromSupabase, 8000);

  subscribe("users", (value) => {
    const rtdbUsers = asRecord(value);
    const merged = { ...state.users };
    Object.keys(rtdbUsers).forEach((uid) => {
      if (isAdminAccount(uid, rtdbUsers[uid])) {
        delete merged[uid];
        remove(ref(db, `users/${uid}`)).catch(() => {});
        return;
      }
      merged[uid] = { ...(merged[uid] || {}), ...rtdbUsers[uid] };
    });
    // Ensure all registered admins are purged from state.users
    if (state.admins) {
      Object.keys(state.admins).forEach((adminUid) => {
        if (isAdminValue(state.admins[adminUid]) && merged[adminUid]) {
          delete merged[adminUid];
        }
      });
    }
    state.users = merged;
  });
  subscribe("rooms", (value) => {
    state.rooms = asRecord(value);
  });
  subscribe("roomTombstones", (value) => {
    state.roomTombstones = asRecord(value);
  });
  subscribe("userRooms", (value) => {
    state.userRooms = asRecord(value);
  });
  subscribe("admins", (value) => {
    state.admins = asRecord(value);
  });
  subscribe("responders", (value) => {
    state.responders = asRecord(value);
  });
  subscribe("sos_alerts", (value) => {
    state.legacyAlerts = asRecord(value);
  });
  subscribe("incidentReports", (value) => {
    state.incidentReports = asRecord(value);
  });
  subscribe("admin_audit_logs", (value) => {
    state.adminAuditLogs = asRecord(value);
  });
  subscribe("call_logs", (value) => {
    state.callLogs = asRecord(value);
  });
  subscribe("admin_notifications", (value) => {
    state.adminNotifications = asRecord(value);
  });
  subscribe("supportChats", (value) => {
    state.supportChats = asRecord(value);
  });
  subscribe("aiChats", (value) => {
    state.aiChats = asRecord(value);
    cleanupExpiredAiChats().catch((error) => console.warn(error));
  });
  subscribe("highlights", (value) => {
    const rtdbHl = asRecord(value);
    state.highlights = { ...(state.highlights || {}), ...rtdbHl };
  });
  subscribe("adminCalls/incoming", (value) => {
    handleAdminIncomingCall(value);
  });
  state.aiChatCleanupTimer = window.setInterval(() => {
    cleanupExpiredAiChats().catch((error) => console.warn(error));
  }, 30000);
  state.sosCleanupTimer = window.setInterval(() => {
    cleanupExpiredSosAlerts().catch((error) => console.warn(error));
  }, 30000);
  cleanupExpiredSosAlerts().catch((error) => console.warn(error));
}

function userRoomsFor(uid) {
  const all = asRecord(state.userRooms?.[uid]);
  const valid = {};
  entries(all).forEach(([code, meta]) => {
    if (state.roomTombstones && state.roomTombstones[code]) return;
    const room = asRecord(state.rooms[code]);
    if (!room || !Object.keys(room).length) return;
    if (room.deleted || room.deletedAt || room.isDeleted) return;
    valid[code] = meta;
  });
  return valid;
}

function contactsFor(user) {
  return entries(user.emergencyContacts);
}

function getUserLastSeen(uid, user) {
  let last = Math.max(millis(user.updatedAt), millis(user.createdAt));
  const myRooms = userRoomsFor(uid);
  entries(state.rooms).forEach(([code, room]) => {
    if (!myRooms[code]) return;
    if (state.roomTombstones && state.roomTombstones[code]) return;
    const member = asRecord(asRecord(room.members)[uid]);
    last = Math.max(last, millis(member.updatedAt));
  });
  return last;
}

function getUsers() {
  const map = new Map();

  // 1. Masukkan semua akaun pengguna berdaftar daripada Firebase Authentication (kecuali admin)
  (state.authUsers || []).forEach((authUser) => {
    const uid = authUser.localId || authUser.uid;
    if (!uid) return;
    if (isAdminAccount(uid, authUser)) return;
    const email = authUser.email || "";
    const name = authUser.displayName || (email ? email.split("@")[0] : "User");
    const lastLogin = Number(authUser.lastLoginAt) || 0;
    const createdAt = Number(authUser.createdAt) || 0;
    const photoUrl = authUser.photoURL || authUser.photoUrl || "";
    map.set(uid, {
      uid,
      data: {
        name,
        email,
        photoUrl,
        createdAt,
        lastLoginAt: lastLogin
      },
      rooms: userRoomsFor(uid),
      contacts: [],
      lastSeen: lastLogin || createdAt || 0,
      isAuthOnly: true
    });
  });

  // 2. Gabungkan data profil lengkap daripada Realtime Database (RTDB) (kecuali admin)
  entries(state.users).forEach(([uid, userVal]) => {
    if (isAdminAccount(uid, userVal)) return;
    const rtdbData = asRecord(userVal);
    const existing = map.get(uid);
    const mergedData = existing ? { ...existing.data, ...rtdbData } : rtdbData;
    map.set(uid, {
      uid,
      data: mergedData,
      rooms: userRoomsFor(uid),
      contacts: contactsFor(mergedData),
      lastSeen: getUserLastSeen(uid, mergedData) || (existing ? existing.lastSeen : 0),
      isAuthOnly: false
    });
  });

  return Array.from(map.values());
}

function getRoomMembers(room) {
  const roomCode = room.code || room.id || "";
  return entries(room.members)
    .filter(([uid]) => {
      if (roomCode && state.userRooms && state.userRooms[uid] && !state.userRooms[uid][roomCode]) {
        return false;
      }
      return true;
    })
    .map(([uid, member]) => {
      const data = asRecord(member);
      return {
        uid: text(data.uid).trim() || uid,
        data,
        updatedAt: millis(data.updatedAt),
        batteryPct: Number.isFinite(Number(data.batteryPct)) ? Number(data.batteryPct) : null,
        lat: Number(data.lat),
        lng: Number(data.lng)
      };
    });
}

function getRooms() {
  return entries(state.rooms)
    .filter(([code, roomValue]) => {
      if (code === "DIRECT" || code === "GLOBAL") return false;
      if (state.roomTombstones && state.roomTombstones[code]) return false;
      const room = asRecord(roomValue);
      if (!room || !Object.keys(room).length) return false;
      if (room.deleted || room.deletedAt || room.isDeleted) return false;

      // Filter out orphaned rooms where creator has no userRooms entry and no registered users in userRooms
      const creatorUid = text(room.creatorUid).trim();
      const hasCreatorInUserRooms = creatorUid && state.userRooms[creatorUid] && state.userRooms[creatorUid][code];
      const hasAnyMemberInUserRooms = entries(state.userRooms).some(([, ur]) => asRecord(ur)[code]);
      if (creatorUid && !hasCreatorInUserRooms && !hasAnyMemberInUserRooms) return false;

      return true;
    })
    .map(([code, roomValue]) => {
      const room = asRecord(roomValue);
      const members = getRoomMembers({ ...room, code });
      const alerts = entries(room.sosAlerts).map(([alertId, alert]) => ({
        id: alertId,
        data: asRecord(alert)
      }));
      const lastMemberUpdate = members.reduce((max, member) => Math.max(max, member.updatedAt), 0);
      return {
        code,
        data: room,
        members,
        alerts,
        liveCount: members.filter((member) => Date.now() - member.updatedAt <= ONLINE_MS).length,
        lowBatteryCount: members.filter((member) => member.batteryPct !== null && member.batteryPct <= LOW_BATTERY).length,
        updatedAt: Math.max(millis(room.updatedAt), lastMemberUpdate, millis(room.createdAt))
      };
    });
}

function alertCreatedAt(alert) {
  return millis(alert.createdAt) || millis(alert.at) || millis(alert.clientAt);
}

function isCancelled(alert) {
  if (!alert) return true;
  const status = text(alert.status || "").toLowerCase();
  if (status === "cancelled" || status === "canceled" || status === "resolved") return true;
  if (alert.active === false) return true;
  if (alert.resolvedAt || (alert.progressStep && Number(alert.progressStep) >= 4)) return true;
  if (alert.cancelledAt || alert.cancelledClientAt) return true;
  return false;
}

function getSosAlerts() {
  const roomAlerts = [];
  entries(state.rooms).forEach(([roomId, room]) => {
    entries(asRecord(room).sosAlerts).forEach(([alertId, alertValue]) => {
      const alert = asRecord(alertValue);
      const createdAt = alertCreatedAt(alert);
      const senderUid = text(alert.senderUid || alert.fromUid).trim();
      const active = !isCancelled(alert);
      roomAlerts.push({
        id: text(alert.alertId).trim() || alertId,
        key: alertId,
        roomId,
        senderUid,
        senderName: text(alert.senderName || alert.fromName || userName(senderUid)).trim(),
        createdAt,
        cancelledAt: millis(alert.cancelledAt) || millis(alert.cancelledClientAt),
        active,
        stale: active && createdAt > 0 && Date.now() - createdAt > SOS_STALE_MS,
        source: "room",
        data: alert
      });
    });
  });

  entries(state.legacyAlerts).forEach(([alertId, alertValue]) => {
    const alert = asRecord(alertValue);
    if (alert.type === "bell") return;
    const createdAt = alertCreatedAt(alert);
    const senderUid = text(alert.fromUid || alert.senderUid).trim();
    const isOld = !createdAt || Date.now() - createdAt > SOS_STALE_MS;
    const active = !isOld && !isCancelled(alert);
    roomAlerts.push({
      id: alertId,
      key: alertId,
      roomId: text(alert.roomCode || alert.roomId).trim(),
      senderUid,
      senderName: text(alert.fromName || alert.senderName || userName(senderUid)).trim(),
      createdAt,
      cancelledAt: 0,
      active,
      stale: isOld,
      source: "legacy",
      data: alert
    });
  });

  return roomAlerts.sort((a, b) => b.createdAt - a.createdAt);
}

function reportStatusMeta(status) {
  const normalized = text(status || "new").toLowerCase();
  return REPORT_STATUSES.find((item) => item.id === normalized) || REPORT_STATUSES[0];
}

function reportAttachments(report) {
  const raw = report.attachments;
  if (Array.isArray(raw)) return raw.map(asRecord).filter((item) => Object.keys(item).length);
  return entries(raw).map(([, item]) => asRecord(item)).filter((item) => Object.keys(item).length);
}

function getIncidentReports() {
  return entries(state.incidentReports).map(([id, value]) => {
    const report = asRecord(value);
    const reportId = text(report.reportId).trim() || id;
    const senderUid = text(report.senderUid).trim();
    const user = asRecord(state.users?.[senderUid]);
    return {
      id: reportId,
      key: id,
      senderUid,
      senderName: text(report.senderName || user.name || user.email || senderUid).trim(),
      senderEmail: text(report.senderEmail || user.email).trim(),
      publicId: text(report.publicId || user.publicId).trim(),
      category: text(report.category).trim(),
      categoryLabel: text(report.categoryLabel || report.category).trim() || "Report",
      details: text(report.details).trim(),
      address: text(report.address).trim(),
      latitude: Number(report.latitude),
      longitude: Number(report.longitude),
      status: text(report.status || "new").trim(),
      createdAt: millis(report.createdAt),
      updatedAt: millis(report.updatedAt),
      attachments: reportAttachments(report),
      data: report
    };
  }).sort((a, b) => b.createdAt - a.createdAt || b.updatedAt - a.updatedAt);
}

function getStats() {
  const users = getUsers();
  const rooms = getRooms();
  const sosAlerts = getSosAlerts();
  const reports = getIncidentReports();
  const activeSos = sosAlerts.filter((alert) => alert.active && !alert.stale);
  const staleSos = sosAlerts.filter((alert) => alert.active && alert.stale);
  const liveUserIds = new Set();
  const lowBatteryIds = new Set();
  let contactCount = 0;

  users.forEach((user) => {
    contactCount += user.contacts.length;
  });

  rooms.forEach((room) => {
    room.members.forEach((member) => {
      if (Date.now() - member.updatedAt <= ONLINE_MS) liveUserIds.add(member.uid);
      if (member.batteryPct !== null && member.batteryPct <= LOW_BATTERY) lowBatteryIds.add(member.uid);
    });
  });

  return {
    users: users.length,
    rooms: rooms.length,
    liveUsers: liveUserIds.size,
    activeSos: activeSos.length,
    staleSos: staleSos.length,
    openReports: reports.filter((report) => report.status !== "resolved" && report.status !== "rejected").length,
    lowBattery: lowBatteryIds.size,
    contacts: contactCount
  };
}

function isOpenReport(status) {
  const normalized = text(status || "new").trim().toLowerCase();
  return normalized !== "resolved" && normalized !== "rejected";
}

function isRecentForNavDot(value) {
  const ms = millis(value);
  return !ms || Date.now() - ms <= NAV_DOT_RECENT_MS;
}

function getNavAttentionState() {
  const sosAlerts = getSosAlerts();
  const reports = getIncidentReports();
  const supportThreads = getSupportThreads();
  const aiThreads = getAiThreads();
  const notificationHistory = getNotificationHistory();
  const activeSos = sosAlerts.filter((alert) => alert.active);
  const openReports = reports.filter((report) => isOpenReport(report.status));
  const recentNotices = entries(state.adminNotifications)
    .map(([id, value]) => ({ id, value: asRecord(value) }))
    .filter((notice) => isRecentForNavDot(notice.value.sentAt));
  const recentHistory = notificationHistory.filter((notification) => isRecentForNavDot(notification.sentAt));
  const unreadSupport = supportThreads.filter((thread) => {
    const status = text(thread.status).trim().toLowerCase();
    return thread.unread && status !== "resolved";
  });
  const unreadAi = aiThreads.filter((thread) => {
    const status = text(thread.status).trim().toLowerCase();
    return thread.unread && status !== "expired" && status !== "answered";
  });

  return {
    sos: {
      count: activeSos.length,
      signature: signatureFromItems(activeSos.map((alert) => `${alert.source}:${alert.roomId}:${alert.key}:${alert.createdAt}:${alert.cancelledAt}`))
    },
    livemap: {
      count: activeSos.length,
      signature: signatureFromItems(activeSos.map((alert) => `${alert.source}:${alert.roomId}:${alert.key}:${alert.createdAt}:${alert.cancelledAt}`))
    },
    soslivechat: {
      count: activeSos.length,
      signature: signatureFromItems(activeSos.map((alert) => `${alert.source}:${alert.roomId}:${alert.key}:${alert.createdAt}:${alert.cancelledAt}`))
    },
    reports: {
      count: openReports.length,
      signature: signatureFromItems(openReports.map((report) => `${report.key}:${report.status}:${report.createdAt}:${report.updatedAt}`))
    },
    notices: {
      count: recentNotices.length,
      signature: signatureFromItems(recentNotices.map((notice) => `${notice.id}:${notice.value.status}:${notice.value.sentAt}`))
    },
    logs: {
      count: recentHistory.length,
      signature: signatureFromItems(recentHistory.map((notification) => `${notification.source}:${notification.id}:${notification.type}:${notification.sentAt}`))
    },
    livechat: {
      count: unreadSupport.length,
      signature: signatureFromItems(unreadSupport.map((thread) => `${thread.uid}:${thread.updatedAt}:${thread.messageCount}:${thread.lastSender}`))
    }
  };
}

function markNavViewSeen(view, attentionState) {
  const attention = asRecord(attentionState)[view];
  if (!attention || !attention.signature) return;
  if (state.navSeenSignatures[view] === attention.signature) return;
  state.navSeenSignatures[view] = attention.signature;
  saveNavSeenSignatures();
}

function localDayStart(offset = 0) {
  const date = new Date();
  date.setHours(0, 0, 0, 0);
  date.setDate(date.getDate() + offset);
  return date.getTime();
}

function localDateStart(date) {
  const copy = new Date(date);
  copy.setHours(0, 0, 0, 0);
  return copy;
}

function monthStart(date, offset = 0) {
  return new Date(date.getFullYear(), date.getMonth() + offset, 1).getTime();
}

function yearStart(date, offset = 0) {
  return new Date(date.getFullYear() + offset, 0, 1).getTime();
}

function weekStart(date, offset = 0) {
  const start = localDateStart(date);
  const day = start.getDay();
  const mondayOffset = day === 0 ? -6 : 1 - day;
  start.setDate(start.getDate() + mondayOffset + offset * 7);
  return start.getTime();
}

function addMs(ms, amount) {
  return ms + amount;
}

function addDays(ms, amount) {
  return addMs(ms, amount * 24 * 60 * 60 * 1000);
}

function rangeLabel(range) {
  const labels = {
    today: "Today",
    week: "This Week",
    month: "This Month",
    year: "This Year"
  };
  return labels[range] || labels.today;
}

function comparisonLabel(range) {
  const labels = {
    today: "from yesterday",
    week: "from last week",
    month: "from last month",
    year: "from last year"
  };
  return labels[range] || labels.today;
}

function dashboardRangeWindow(range, offset = 0) {
  const now = new Date();
  if (range === "week") {
    const start = weekStart(now, offset);
    return { start, end: addDays(start, 7) };
  }
  if (range === "month") {
    return { start: monthStart(now, offset), end: monthStart(now, offset + 1) };
  }
  if (range === "year") {
    return { start: yearStart(now, offset), end: yearStart(now, offset + 1) };
  }
  return { start: localDayStart(offset), end: localDayStart(offset + 1) };
}

function makeBucket(start, end, label) {
  return { start, end, label };
}

function dashboardBuckets(range, period) {
  if (range === "today") {
    const step = 4 * 60 * 60 * 1000;
    return Array.from({ length: 6 }, (_, index) => {
      const start = period.start + step * index;
      const hour = new Intl.DateTimeFormat("en-US", {
        hour: "2-digit",
        minute: "2-digit"
      }).format(new Date(start));
      return makeBucket(start, Math.min(start + step, period.end), hour);
    });
  }

  if (range === "week") {
    return Array.from({ length: 7 }, (_, index) => {
      const start = addDays(period.start, index);
      return makeBucket(start, addDays(start, 1), new Intl.DateTimeFormat("en-US", {
        weekday: "short"
      }).format(new Date(start)));
    });
  }

  if (range === "month") {
    const buckets = [];
    let start = period.start;
    while (start < period.end) {
      const end = Math.min(addDays(start, 7), period.end);
      buckets.push(makeBucket(start, end, new Intl.DateTimeFormat("en-US", {
        day: "numeric",
        month: "short"
      }).format(new Date(start))));
      start = end;
    }
    return buckets;
  }

  return Array.from({ length: 12 }, (_, index) => {
    const start = new Date(new Date(period.start).getFullYear(), index, 1).getTime();
    const end = new Date(new Date(period.start).getFullYear(), index + 1, 1).getTime();
    return makeBucket(start, end, new Intl.DateTimeFormat("en-US", {
      month: "short"
    }).format(new Date(start)));
  });
}

function countInRange(values, start, end) {
  return values.filter((value) => {
    const ms = millis(value);
    return ms >= start && ms < end;
  }).length;
}

function valuesByBuckets(values, buckets) {
  return buckets.map((bucket) => countInRange(values, bucket.start, bucket.end));
}

function trendMeta(current, previous, compareText) {
  const diff = current - previous;
  const pct = previous > 0 ? Math.round((Math.abs(diff) / previous) * 100) : (current > 0 ? 100 : 0);
  return {
    tone: diff < 0 ? "down" : "up",
    icon: diff < 0 ? "arrow-down" : "arrow-up",
    text: `${pct}% ${compareText}`
  };
}

function getBellEvents() {
  const events = [];
  entries(state.rooms).forEach(([roomId, roomValue]) => {
    const room = asRecord(roomValue);
    entries(room.bells).forEach(([toUid, bells]) => {
      entries(bells).forEach(([bellId, bellValue]) => {
        const bell = asRecord(bellValue);
        events.push({
          id: bellId,
          roomId,
          toUid,
          fromUid: text(bell.fromUid || bell.senderUid).trim(),
          fromName: text(bell.fromName || bell.senderName).trim(),
          at: millis(bell.at) || millis(bell.createdAt) || millis(bell.clientAt) || millis(room.updatedAt),
          data: bell
        });
      });
    });
  });
  return events;
}

function getNotificationHistory() {
  const bellNotifications = getBellEvents().map((bell) => ({
    id: bell.id,
    type: "Bell",
    recipient: userName(bell.toUid),
    recipientMeta: bell.toUid,
    sender: bell.fromName || userName(bell.fromUid),
    senderMeta: bell.fromUid,
    roomId: bell.roomId,
    sentAt: bell.at,
    source: "rooms/bells"
  }));

  const sosNotifications = getSosAlerts().map((alert) => ({
    id: alert.id,
    type: alert.active ? "SOS Alert" : (alert.data && (alert.data.resolvedAt || alert.data.status === "resolved" || Number(alert.data.progressStep) >= 4) ? "SOS Resolved" : "SOS Cancelled"),
    recipient: alert.source === "legacy" && alert.data.toUid ? userName(alert.data.toUid) : "Room members",
    recipientMeta: alert.source === "legacy" ? text(alert.data.toUid || "") : "excluding sender",
    sender: alert.senderName || userName(alert.senderUid),
    senderMeta: alert.senderUid,
    roomId: alert.roomId,
    sentAt: alert.cancelledAt || alert.createdAt,
    source: alert.source === "legacy" ? "sos_alerts" : "rooms/sosAlerts"
  }));

  const adminNotifications = entries(state.adminNotifications).map(([id, value]) => {
    const notification = asRecord(value);
    const successCount = Number(notification.successCount) || 0;
    const failureCount = Number(notification.failureCount) || 0;
    const status = text(notification.status || (failureCount ? "partial" : "sent"));
    const databaseNotice = text(notification.method) === NOTICE_METHOD;
    const failureReasons = Array.isArray(notification.failureReasons)
      ? notification.failureReasons.map((reason) => text(reason)).filter(Boolean)
      : [];
    const recipientMeta = databaseNotice
      ? `${status}: notifications`
      : `${status}: ${successCount} sent / ${failureCount} failed`;
    return {
      id,
      type: databaseNotice ? "Notifications" : "Legacy Delivery",
      canDelete: true,
      recipient: audienceLabel(text(notification.audience || "all")),
      recipientMeta,
      sender: userName(notification.sentBy),
      senderMeta: text(notification.sentBy),
      title: text(notification.title),
      message: failureReasons.length ? failureReasons.join(", ") : text(notification.message),
      roomId: "-",
      sentAt: millis(notification.sentAt),
      source: databaseNotice ? `admin_notifications:${NOTICE_METHOD}` : `admin_notifications:${status}`
    };
  });

  const auditNotifications = entries(state.adminAuditLogs).map(([id, value]) => {
    const audit = asRecord(value);
    const accountsDeleted = Number(audit.accountsDeleted) || 0;
    const accountsKept = Number(audit.accountsKept) || 0;
    const roomsDeleted = Number(audit.roomsDeleted) || 0;
    const messagesRemoved = Number(audit.messagesRemoved) || 0;
    const sosAlertsRemoved = Number(audit.sosAlertsRemoved) || 0;
    const callsRemoved = Number(audit.callsRemoved) || 0;
    const incidentReportsRemoved = Number(audit.incidentReportsRemoved) || 0;
    const triggeredBy = text(audit.triggeredBy || "Admin Session");
    const action = text(audit.action || "clear_database");
    const details = text(audit.details || `Database reset: ${accountsDeleted} accounts deleted`);

    let subStats = `${accountsDeleted} akaun`;
    if (roomsDeleted > 0) subStats += `, ${roomsDeleted} bilik`;
    if (messagesRemoved > 0) subStats += `, ${messagesRemoved} mesej`;
    if (callsRemoved > 0) subStats += `, ${callsRemoved} panggilan`;
    if (sosAlertsRemoved > 0) subStats += `, ${sosAlertsRemoved} SOS`;
    if (incidentReportsRemoved > 0) subStats += `, ${incidentReportsRemoved} laporan`;
    subStats += " dipadam";

    return {
      id,
      type: text(audit.type || "Database Reset"),
      canDelete: true,
      recipient: "System / All Users",
      recipientMeta: subStats,
      sender: triggeredBy,
      senderMeta: action,
      title: text(audit.type || "Database Reset"),
      message: details,
      roomId: "-",
      sentAt: millis(audit.timestamp || audit.createdAt),
      source: "admin_audit_logs"
    };
  });

  const callNotifications = entries(state.callLogs).map(([id, value]) => {
    const call = asRecord(value);
    const isVideo = text(call.callType) === "video";
    const status = text(call.status || "ended").toLowerCase();
    const callerName = text(call.callerName || userName(call.callerUid) || "Pengguna");
    const calleeName = text(call.calleeName || userName(call.calleeUid) || "Admin");
    const duration = Number(call.duration) || 0;
    const durationText = duration > 0 ? ` (${Math.floor(duration / 60)}m ${duration % 60}s)` : "";

    let callTypeLabel = isVideo ? "Video Call" : "Voice Call";
    let statusText = status;
    if (status === "answered") {
      callTypeLabel = "Answered Call";
      statusText = "Dijawab";
    } else if (status === "declined") {
      callTypeLabel = "Declined Call";
      statusText = "Ditolak";
    } else if (status === "missed") {
      callTypeLabel = "Missed Call";
      statusText = "Terlepas";
    } else if (status === "ended") {
      callTypeLabel = "Call Ended";
      statusText = `Selesai${durationText}`;
    } else if (status === "ringing" || status === "calling" || status === "outgoing") {
      callTypeLabel = "Incoming Call";
      statusText = "Panggilan Masuk / Keluar";
    }

    return {
      id,
      type: callTypeLabel,
      canDelete: true,
      recipient: calleeName,
      recipientMeta: `Status: ${statusText}`,
      sender: callerName,
      senderMeta: text(call.callerUid || "-"),
      title: `${isVideo ? "Panggilan Video" : "Panggilan Suara"} (${statusText})`,
      message: `${isVideo ? "Video Call" : "Voice Call"} antara ${callerName} & ${calleeName}. Tempoh: ${duration > 0 ? duration + "s" : "-"}`,
      roomId: text(call.roomId || "-"),
      sentAt: millis(call.timestamp || call.createdAt || call.answeredAt || call.endedAt),
      source: "call_logs"
    };
  });

  const sosCallRequests = [];
  getSosAlerts().forEach((alert) => {
    const data = asRecord(alert.data);
    const cr = asRecord(data.callRequest);
    if (cr && cr.status) {
      const crId = `sos_call_${alert.roomId}_${alert.id}`;
      if (!state.callLogs || !state.callLogs[crId]) {
        const isVideo = text(cr.callType) === "video";
        const crStatus = text(cr.status);
        let crType = "SOS Call Request";
        if (crStatus === "answered") crType = "Answered Call";
        else if (crStatus === "declined") crType = "Declined Call";

        sosCallRequests.push({
          id: crId,
          type: crType,
          canDelete: false,
          recipient: "ResQTap Support (Admin)",
          recipientMeta: `Status: ${crStatus}`,
          sender: alert.senderName || userName(alert.senderUid),
          senderMeta: text(alert.senderUid),
          title: `SOS ${isVideo ? "Video" : "Voice"} Call (${crStatus})`,
          message: `Permintaan panggilan kecemasan daripada mangsa SOS di bilik ${alert.roomId}. Status: ${crStatus}`,
          roomId: alert.roomId,
          sentAt: millis(cr.timestamp || cr.answeredAt || cr.declinedAt || alert.createdAt),
          source: "rooms/sosAlerts/callRequest"
        });
      }
    }
  });

  return bellNotifications
    .concat(adminNotifications)
    .concat(auditNotifications)
    .concat(callNotifications)
    .concat(sosCallRequests)
    .concat(sosNotifications)
    .sort((a, b) => b.sentAt - a.sentAt);
}

function supportMessageTime(message) {
  return millis(message.createdAt) || millis(message.sentAt) || millis(message.clientAt);
}

function getSupportChatMessages(thread) {
  return entries(asRecord(thread).messages)
    .map(([id, value]) => {
      const data = asRecord(value);
      const attachment = asRecord(data.attachment);
      return {
        id,
        data,
        text: text(data.text).trim(),
        topic: text(data.topic).trim(),
        sender: text(data.sender).trim(),
        senderUid: text(data.senderUid).trim(),
        senderName: text(data.senderName).trim(),
        source: text(data.source).trim(),
        createdAt: supportMessageTime(data),
        attachment: {
          name: text(attachment.name).trim(),
          mimeType: text(attachment.mimeType).trim(),
          downloadUrl: text(attachment.downloadUrl).trim(),
          size: Number(attachment.size) || 0
        }
      };
    })
    .filter((message) => message.text || message.attachment.downloadUrl)
    .sort((a, b) => a.createdAt - b.createdAt || a.id.localeCompare(b.id));
}

function chatStatusLabel(status) {
  const normalized = text(status).trim().toLowerCase();
  if (normalized === "resolved") return "Resolved";
  if (normalized === "answered") return "Answered";
  return "Open";
}

function chatStatusTone(status) {
  const normalized = text(status).trim().toLowerCase();
  if (normalized === "resolved") return "good";
  if (normalized === "answered") return "";
  return "warn";
}

function getSupportThreads() {
  return entries(state.supportChats).filter(([, value]) => {
    const thread = asRecord(value);
    const messages = getSupportChatMessages(thread);
    // Hanya senaraikan sesi livechat yang mempunyai sekurang-kurangnya 1 mesej.
    // Jika thread kosong atau hanya mempunyai rekod rating, jangan buka di admin dashboard!
    return messages.length > 0;
  }).map(([uid, value]) => {
    const thread = asRecord(value);
    const meta = asRecord(thread.meta);
    const user = asRecord(state.users?.[uid]);
    const messages = getSupportChatMessages(thread);
    const last = messages[messages.length - 1] || {};
    const name = text(meta.userName || user.name || user.email || meta.userEmail || uid).trim() || uid;
    const email = text(meta.userEmail || user.email).trim();
    const updatedAt = Math.max(millis(meta.updatedAt), millis(last.createdAt));
    const lastSender = text(meta.lastSender || last.sender).trim();
    const lastAttachment = asRecord(last.attachment);
    const topic = text(meta.topic || last.topic).trim();
    const lastMessage = text(meta.lastMessage || last.text).trim()
      || (lastAttachment.downloadUrl ? `Attachment: ${text(lastAttachment.name || "file")}` : "");

    return {
      uid,
      meta,
      messages,
      name,
      email,
      topic,
      publicId: text(meta.publicId || user.publicId).trim(),
      photoUrl: text(user.photoUrl || meta.photoUrl).trim(),
      photoB64: text(user.photoB64 || meta.photoB64).trim(),
      status: text(meta.status).trim() || (messages.length > 0 ? "open" : "resolved"),
      updatedAt,
      lastMessage,
      lastSender,
      unread: (lastSender === "user" || lastSender === "ai") && text(meta.status).trim() !== "resolved",
      messageCount: messages.length
    };
  }).sort((a, b) => b.updatedAt - a.updatedAt || a.name.localeCompare(b.name));
}

function aiMessageTime(message) {
  return millis(message.createdAt) || millis(message.sentAt) || millis(message.clientAt);
}

function getAiChatMessages(thread) {
  return entries(asRecord(thread).messages)
    .map(([id, value]) => {
      const data = asRecord(value);
      const role = text(data.role || data.sender).trim().toLowerCase();
      return {
        id,
        data,
        text: text(data.text || data.content).trim(),
        role: role === "ai" ? "assistant" : role,
        senderUid: text(data.senderUid).trim(),
        senderName: text(data.senderName).trim(),
        source: text(data.source).trim(),
        createdAt: aiMessageTime(data)
      };
    })
    .filter((message) => message.text && (message.role === "user" || message.role === "assistant"))
    .sort((a, b) => a.createdAt - b.createdAt || a.id.localeCompare(b.id));
}

function getAiThreads() {
  const records = new Map();
  entries(state.supportChats).forEach(([uid, value]) => {
    const aiAssistant = asRecord(asRecord(value).aiAssistant);
    if (Object.keys(aiAssistant).length > 0) {
      records.set(uid, { uid, value: aiAssistant, store: "supportChats" });
    }
  });
  entries(state.aiChats).forEach(([uid, value]) => {
    records.set(uid, { uid, value, store: "aiChats" });
  });

  return Array.from(records.values()).map((record) => {
    const { uid, value, store } = record;
    const thread = asRecord(value);
    const meta = asRecord(thread.meta);
    const user = asRecord(state.users?.[uid]);
    const messages = getAiChatMessages(thread);
    const last = messages[messages.length - 1] || {};
    const lastUser = [...messages].reverse().find((message) => message.role === "user") || {};
    const name = text(meta.userName || user.name || user.email || meta.userEmail || uid).trim() || uid;
    const email = text(meta.userEmail || user.email).trim();
    const lastUserAt = millis(meta.lastUserAt) || millis(meta.lastUserClientAt) || millis(lastUser.createdAt);
    const expiresAt = millis(meta.expiresAt) || (lastUserAt ? lastUserAt + AI_CHAT_EXPIRY_MS : 0);
    const updatedAt = Math.max(millis(meta.updatedAt), millis(last.createdAt), lastUserAt);
    const lastMessage = text(meta.lastMessage || last.text).trim();
    const lastSender = text(meta.lastSender || last.role).trim();
    const expired = expiresAt > 0 && expiresAt <= Date.now();

    return {
      uid,
      meta,
      messages,
      name,
      email,
      publicId: text(meta.publicId || user.publicId).trim(),
      photoUrl: text(user.photoUrl || meta.photoUrl).trim(),
      photoB64: text(user.photoB64 || meta.photoB64).trim(),
      status: expired ? "expired" : text(meta.status || "active").trim() || "active",
      store,
      updatedAt,
      expiresAt,
      lastMessage,
      lastSender,
      unread: lastSender === "user",
      messageCount: messages.length
    };
  }).sort((a, b) => b.updatedAt - a.updatedAt || a.name.localeCompare(b.name));
}

function getDashboardModel(range = state.dashboardRange) {
  const users = getUsers();
  const rooms = getRooms();
  const alerts = getSosAlerts();
  const reports = getIncidentReports();
  const bells = getBellEvents();
  const supportMessages = getSupportThreads().flatMap((thread) => thread.messages);
  const aiMessages = getAiThreads().flatMap((thread) => thread.messages);
  const aiAssistanceTimes = aiMessages
    .filter((message) => message.role === "assistant")
    .map((message) => message.createdAt)
    .filter(Boolean);
  const userTimes = users
    .map((user) => millis(user.data.createdAt) || millis(user.data.updatedAt) || user.lastSeen)
    .filter(Boolean);
  const communicationTimes = [];

  rooms.forEach((room) => {
    if (room.updatedAt) communicationTimes.push(room.updatedAt);
    room.members.forEach((member) => {
      if (member.updatedAt) communicationTimes.push(member.updatedAt);
    });
  });
  bells.forEach((bell) => {
    if (bell.at) communicationTimes.push(bell.at);
  });
  supportMessages.forEach((message) => {
    if (message.createdAt) communicationTimes.push(message.createdAt);
  });
  aiMessages.forEach((message) => {
    if (message.createdAt) communicationTimes.push(message.createdAt);
  });

  const bellTimes = bells.map((bell) => bell.at).filter(Boolean);
  const alertTimes = alerts.map((alert) => alert.createdAt).filter(Boolean);
  const reportTimes = reports.map((report) => report.createdAt).filter(Boolean);
  const emergencyTimes = alertTimes.concat(reportTimes);
  const current = dashboardRangeWindow(range, 0);
  const previous = dashboardRangeWindow(range, -1);
  const compareText = comparisonLabel(range);
  const buckets = dashboardBuckets(range, current);
  const currentUsers = countInRange(userTimes, current.start, current.end);
  const previousUsers = countInRange(userTimes, previous.start, previous.end);
  const currentCommunication = countInRange(communicationTimes, current.start, current.end);
  const previousCommunication = countInRange(communicationTimes, previous.start, previous.end);
  const currentEmergency = countInRange(emergencyTimes, current.start, current.end);
  const previousEmergency = countInRange(emergencyTimes, previous.start, previous.end);
  const currentSignals = countInRange(bellTimes, current.start, current.end);
  const currentAiAssistance = countInRange(aiAssistanceTimes, current.start, current.end);
  const featureGroups = [
    { label: "Chat & Calls", value: currentCommunication, color: "#3B82F6" },
    { label: "Active Users", value: currentUsers, color: "#10B981" },
    { label: "AI Assistance", value: currentAiAssistance, color: "#8B5CF6" },
    { label: "Signals & Bells", value: currentSignals, color: "#F59E0B" },
    { label: "SOS Alerts", value: countInRange(alertTimes, current.start, current.end), color: "#E60067" },
    { label: "Incident Reports", value: countInRange(reportTimes, current.start, current.end), color: "#EC4899" }
  ];

  return {
    rangeLabel: rangeLabel(range),
    totals: {
      users: users.length,
      communication: currentCommunication,
      emergency: currentEmergency
    },
    trends: {
      users: trendMeta(currentUsers, previousUsers, compareText),
      communication: trendMeta(currentCommunication, previousCommunication, compareText),
      emergency: trendMeta(currentEmergency, previousEmergency, compareText)
    },
    buckets,
    series: [
      { label: "Communication (Chat & Calls)", values: valuesByBuckets(communicationTimes, buckets), color: "#3B82F6" },
      { label: "Active Users", values: valuesByBuckets(userTimes, buckets), color: "#10B981" },
      { label: "Emergency & SOS", values: valuesByBuckets(alertTimes.concat(bellTimes), buckets), color: "#E60067" }
    ],
    featureGroups
  };
}

function matchesSearch(fields) {
  const query = state.search.trim().toLowerCase();
  if (!query) return true;
  return fields.some((field) => text(field).toLowerCase().includes(query));
}

function notificationCategory(categoryId) {
  return NOTIFICATION_CATEGORIES.find((category) => category.id === categoryId) || NOTIFICATION_CATEGORIES[0];
}

function matchesNotificationCategory(notification, categoryId) {
  const category = notificationCategory(categoryId);
  if (!category.types) return true;
  return category.types.includes(notification.type);
}

function renderNotificationCategoryTabs(rows, activeCategoryId) {
  els.notificationCategoryTabs.innerHTML = NOTIFICATION_CATEGORIES.map((category) => {
    const count = rows.filter((notification) => matchesNotificationCategory(notification, category.id)).length;
    const active = category.id === activeCategoryId;
    return `
      <button
        class="category-tab${active ? " is-active" : ""}"
        data-notification-category="${escapeHtml(category.id)}"
        type="button"
        role="tab"
        aria-selected="${active ? "true" : "false"}"
      >
        ${icon(category.icon)}
        <span>${escapeHtml(category.label)}</span>
        <span class="category-tab-count">${count}</span>
      </button>
    `;
  }).join("");
}

function emptyRow(colspan, message) {
  return `<tr><td colspan="${colspan}" class="muted">${escapeHtml(message)}</td></tr>`;
}

function pill(label, tone = "") {
  const cls = tone ? ` status-pill ${tone}` : "status-pill";
  return `<span class="${cls.trim()}">${escapeHtml(label)}</span>`;
}

function niceChartMax(value) {
  if (value <= 4) return 4;
  if (value <= 8) return 8;
  const magnitude = 10 ** Math.floor(Math.log10(value));
  return Math.ceil(value / magnitude) * magnitude;
}

function renderLineChart(days, series) {
  const width = 1000;
  const height = 360;
  const left = 44;
  const right = 28;
  const top = 34;
  const bottom = 54;
  const plotWidth = width - left - right;
  const plotHeight = height - top - bottom;
  const maxValue = niceChartMax(Math.max(1, ...series.flatMap((item) => item.values)));
  const ticks = [0, Math.round(maxValue * 0.25), Math.round(maxValue * 0.5), Math.round(maxValue * 0.75), maxValue];
  const uniqueTicks = [...new Set(ticks)].sort((a, b) => a - b);
  
  const x = (index) => left + (days.length <= 1 ? plotWidth / 2 : (plotWidth / (days.length - 1)) * index);
  const y = (value) => top + plotHeight - (Math.max(0, value) / maxValue) * plotHeight;

  const defs = `
    <defs>
      <linearGradient id="grad-blue" x1="0" y1="0" x2="0" y2="1">
        <stop offset="0%" stop-color="#3B82F6" stop-opacity="0.25"/>
        <stop offset="100%" stop-color="#3B82F6" stop-opacity="0.0"/>
      </linearGradient>
      <linearGradient id="grad-green" x1="0" y1="0" x2="0" y2="1">
        <stop offset="0%" stop-color="#10B981" stop-opacity="0.25"/>
        <stop offset="100%" stop-color="#10B981" stop-opacity="0.0"/>
      </linearGradient>
      <linearGradient id="grad-pink" x1="0" y1="0" x2="0" y2="1">
        <stop offset="0%" stop-color="#E60067" stop-opacity="0.25"/>
        <stop offset="100%" stop-color="#E60067" stop-opacity="0.0"/>
      </linearGradient>
    </defs>
  `;

  const gradMap = { "#3B82F6": "url(#grad-blue)", "#10B981": "url(#grad-green)", "#E60067": "url(#grad-pink)" };

  const grid = uniqueTicks.map((tick) => {
    const yPos = y(tick).toFixed(1);
    return `
      <g>
        <line x1="${left}" y1="${yPos}" x2="${width - right}" y2="${yPos}" class="chart-grid-line" stroke="#F1F5F9" stroke-width="1.2" stroke-dasharray="4 4"></line>
        <text x="${left - 10}" y="${Number(yPos) + 4}" class="chart-axis-text" text-anchor="end" fill="#94A3B8" font-size="12" font-weight="750">${escapeHtml(formatNumber(tick))}</text>
      </g>
    `;
  }).join("");

  const labels = days.map((day, index) => {
    const xPos = x(index).toFixed(1);
    return `
      <text x="${xPos}" y="${height - 14}" class="chart-axis-text chart-x-label" text-anchor="middle" fill="#64748B" font-size="12" font-weight="700">${escapeHtml(day.label)}</text>
    `;
  }).join("");

  const layers = series.map((item, seriesIndex) => {
    const pts = item.values.map((value, index) => ({ x: x(index).toFixed(1), y: y(value).toFixed(1), val: value }));
    const pointsStr = pts.map((p) => `${p.x},${p.y}`).join(" ");
    const areaD = `M ${pts[0].x} ${y(0).toFixed(1)} L ${pts.map(p => `${p.x} ${p.y}`).join(" L ")} L ${pts[pts.length - 1].x} ${y(0).toFixed(1)} Z`;
    const gradFill = gradMap[item.color] || "none";

    const circles = pts.map((p, index) => {
      const showBadge = p.val > 0 ? `
        <g class="chart-value-badge">
          <rect x="${Number(p.x) - 10}" y="${Number(p.y) - 22}" width="20" height="16" rx="4" fill="${item.color}"/>
          <text x="${p.x}" y="${Number(p.y) - 11}" fill="#FFFFFF" font-size="10" font-weight="800" text-anchor="middle">${p.val}</text>
        </g>
      ` : "";
      return `
        <circle class="chart-point" cx="${p.x}" cy="${p.y}" r="5.5" fill="${item.color}" stroke="#FFFFFF" stroke-width="2.5" style="--point-index:${index};"></circle>
        ${showBadge}
      `;
    }).join("");

    return `
      <path d="${areaD}" fill="${gradFill}" />
      <polyline class="chart-line" pathLength="100" points="${pointsStr}" fill="none" stroke="${item.color}" stroke-width="3" stroke-linecap="round" stroke-linejoin="round" style="--line-index:${seriesIndex};"></polyline>
      ${circles}
    `;
  }).join("");

  const legend = series.map((item) => `
    <span class="chart-legend-item">
      <span class="legend-dot" style="background:${item.color};"></span>
      <strong>${escapeHtml(item.label)}</strong>
    </span>
  `).join("");

  return `
    <div class="chart-legend">${legend}</div>
    <svg class="chart-svg" viewBox="0 0 ${width} ${height}" preserveAspectRatio="none" role="img" aria-label="Usage statistics chart">
      ${defs}
      ${grid}
      ${labels}
      ${layers}
    </svg>
  `;
}

function renderFeatureDonut(features) {
  const total = features.reduce((sum, item) => sum + item.value, 0);
  if (!total) {
    return `
      <div class="donut-chart is-empty">
        <svg class="donut-svg" viewBox="0 0 120 120" role="img" aria-hidden="true">
          <circle class="donut-track" cx="60" cy="60" r="44"></circle>
          <circle class="donut-center" cx="60" cy="60" r="28"></circle>
        </svg>
      </div>
    `;
  }

  let offset = 0;
  const segments = features
    .filter((item) => item.value > 0)
    .map((item, index) => {
      const dash = (item.value / total) * 100;
      const gap = Math.max(0, 100 - dash);
      const dashOffset = -offset;
      offset += dash;
      return `
        <circle
          class="donut-segment"
          cx="60"
          cy="60"
          r="44"
          pathLength="100"
          stroke="${item.color}"
          stroke-dasharray="${dash.toFixed(3)} ${gap.toFixed(3)}"
          style="--dash-offset:${dashOffset.toFixed(3)}; --segment-index:${index};"
        ></circle>
      `;
    }).join("");

  return `
    <div class="donut-chart">
      <svg class="donut-svg" viewBox="0 0 120 120" role="img" aria-hidden="true">
        <circle class="donut-track" cx="60" cy="60" r="44"></circle>
        ${segments}
        <circle class="donut-center" cx="60" cy="60" r="28"></circle>
      </svg>
    </div>
  `;
}

function renderFeatureLegend(features) {
  const total = features.reduce((sum, item) => sum + item.value, 0);
  return features.map((item) => {
    const pct = total > 0 ? Math.round((item.value / total) * 100) : 0;
    return `
      <div class="feature-legend-item">
        <span class="legend-dot" style="background:${item.color}"></span>
        <span>${escapeHtml(item.label)}</span>
        <strong>${pct}%</strong>
      </div>
    `;
  }).join("");
}

function dashboardRenderSignature(model, cards) {
  return JSON.stringify({
    range: state.dashboardRange,
    cards: cards.map((card) => ({
      label: card.label,
      value: card.value,
      trendTone: card.trend.tone,
      trendText: card.trend.text
    })),
    buckets: model.buckets.map((bucket) => bucket.label),
    series: model.series.map((item) => item.values),
    features: model.featureGroups.map((item) => ({
      label: item.label,
      value: item.value
    }))
  });
}

function easeOutCubic(value) {
  return 1 - ((1 - value) ** 3);
}

function animateNumber(el, from, to) {
  const start = performance.now();
  const duration = 620;
  const diff = to - from;

  function tick(now) {
    const progress = Math.min(1, (now - start) / duration);
    const current = Math.round(from + (diff * easeOutCubic(progress)));
    el.textContent = formatNumber(current);
    if (progress < 1) requestAnimationFrame(tick);
  }

  requestAnimationFrame(tick);
}

function animateDashboardMetrics(cards) {
  cards.forEach((card, index) => {
    const valueEl = els.metricGrid.querySelector(`[data-metric-index="${index}"]`);
    if (!valueEl) return;

    const previous = Number(state.dashboardMetricValues[card.label]);
    const next = Number(card.value) || 0;
    if (Number.isFinite(previous) && previous !== next) {
      animateNumber(valueEl, previous, next);
    } else {
      valueEl.textContent = formatNumber(next);
    }
    state.dashboardMetricValues[card.label] = next;
  });
}

/* ------------------------------------------------------------------ */
/* SOS LIVE ALARM SYSTEM (30-SECOND BLIP & WEB AUDIO SIREN)           */
/* ------------------------------------------------------------------ */
class SosAlarmSound {
  constructor() {
    this.ctx = null;
    this.osc = null;
    this.gain = null;
    this.timer = null;
    this.isPlaying = false;
    this.mutedAlerts = new Set();
  }

  ensureContext() {
    if (!this.ctx) {
      const AudioCtx = window.AudioContext || window.webkitAudioContext;
      if (AudioCtx) this.ctx = new AudioCtx();
    }
    if (this.ctx && this.ctx.state === "suspended") {
      this.ctx.resume().catch(() => {});
    }
  }

  play(alertId) {
    if (alertId && this.mutedAlerts.has(alertId)) return;
    this.ensureContext();
    if (!this.ctx) return;

    if (this.ctx.state === "suspended") {
      this.ctx.resume().then(() => {
        if (!this.isPlaying) this.startOscillator();
      }).catch(() => {});
    } else {
      if (!this.isPlaying) this.startOscillator();
    }
  }

  startOscillator() {
    if (this.isPlaying || !this.ctx) return;
    try {
      this.isPlaying = true;
      const now = this.ctx.currentTime;
      this.osc = this.ctx.createOscillator();
      this.gain = this.ctx.createGain();

      this.osc.type = "sawtooth";
      this.osc.frequency.setValueAtTime(720, now);
      this.gain.gain.setValueAtTime(0.2, now);

      let hi = false;
      this.timer = setInterval(() => {
        if (!this.ctx || !this.isPlaying || !this.osc) return;
        hi = !hi;
        const t = this.ctx.currentTime;
        this.osc.frequency.setTargetAtTime(hi ? 960 : 700, t, 0.08);
      }, 350);

      this.osc.connect(this.gain);
      this.gain.connect(this.ctx.destination);
      this.osc.start();
    } catch (e) {
      console.warn("Could not start SOS siren audio:", e);
    }
  }

  stop() {
    if (!this.isPlaying) return;
    this.isPlaying = false;
    if (this.timer) {
      clearInterval(this.timer);
      this.timer = null;
    }
    if (this.osc) {
      try {
        this.osc.stop();
        this.osc.disconnect();
      } catch (e) {}
      this.osc = null;
    }
    if (this.gain) {
      try {
        this.gain.disconnect();
      } catch (e) {}
      this.gain = null;
    }
  }

  mute(alertId) {
    if (alertId) this.mutedAlerts.add(alertId);
    this.stop();
  }
}

const sosAlarmSound = new SosAlarmSound();
["pointerdown", "click", "keydown", "touchstart"].forEach((evt) => {
  window.addEventListener(evt, () => sosAlarmSound.ensureContext(), { passive: true });
});

class CallRingtoneSound {
  constructor() {
    this.ctx = null;
    this.timer = null;
    this.isPlaying = false;
  }
  ensureContext() {
    if (!this.ctx) {
      const AudioCtx = window.AudioContext || window.webkitAudioContext;
      if (AudioCtx) this.ctx = new AudioCtx();
    }
    if (this.ctx && this.ctx.state === "suspended") {
      this.ctx.resume().catch(() => {});
    }
  }
  play() {
    this.ensureContext();
    if (!this.ctx || this.isPlaying) return;
    this.isPlaying = true;
    const playRingCycle = () => {
      if (!this.isPlaying || !this.ctx) return;
      try {
        const now = this.ctx.currentTime;
        const osc1 = this.ctx.createOscillator();
        const osc2 = this.ctx.createOscillator();
        const gain = this.ctx.createGain();

        osc1.frequency.setValueAtTime(440, now);
        osc2.frequency.setValueAtTime(480, now);

        gain.gain.setValueAtTime(0.12, now);
        gain.gain.exponentialRampToValueAtTime(0.001, now + 1.6);

        osc1.connect(gain);
        osc2.connect(gain);
        gain.connect(this.ctx.destination);

        osc1.start(now);
        osc2.start(now);
        osc1.stop(now + 1.6);
        osc2.stop(now + 1.6);
      } catch (e) {}
    };
    playRingCycle();
    this.timer = setInterval(playRingCycle, 3000);
  }
  stop() {
    this.isPlaying = false;
    if (this.timer) {
      clearInterval(this.timer);
      this.timer = null;
    }
  }
}

const callRingtoneSound = new CallRingtoneSound();
["pointerdown", "click", "keydown", "touchstart"].forEach((evt) => {
  window.addEventListener(evt, () => callRingtoneSound.ensureContext(), { passive: true });
});

function handleAdminIncomingCall(data) {
  const alertEl = document.getElementById("adminIncomingCallAlert");
  if (!alertEl) return;

  if (data && data.status === "ringing") {
    const now = Date.now();
    const callTime = Number(data.timestamp) || now;
    if (Math.abs(now - callTime) < 180000 || !data.timestamp) {
      const callerName = data.callerName || "Mangsa SOS";
      const isVoice = data.callType === "voice";
      const callTypeDesc = isVoice ? "Panggilan Suara" : "Panggilan Video";
      const roomText = data.roomId ? ` (Bilik: ${data.roomId})` : "";

      const nameEl = document.getElementById("adminIncomingCallerName");
      const descEl = document.getElementById("adminIncomingCallDesc");
      const acceptBtnText = document.getElementById("adminAcceptCallBtnText");

      if (nameEl) nameEl.textContent = callerName;
      if (descEl) descEl.textContent = `${callTypeDesc} masuk${roomText}`;
      if (acceptBtnText) acceptBtnText.textContent = `Jawab ${isVoice ? "Suara" : "Video"}`;

      alertEl.classList.remove("hidden");
      callRingtoneSound.play();
      refreshIcons();

      const acceptBtn = document.getElementById("adminAcceptCallBtn");
      const declineBtn = document.getElementById("adminDeclineCallBtn");

      if (acceptBtn) {
        acceptBtn.onclick = async () => {
          alertEl.classList.add("hidden");
          callRingtoneSound.stop();
          const logId = data.callLogId || `call_${data.timestamp || Date.now()}`;
          vcActiveCallLogId = logId;
          vcActiveCallStartTime = Date.now();
          try {
            await update(ref(db, "adminCalls/incoming"), {
              status: "answered",
              answeredAt: serverTimestamp()
            });
            if (data.roomId && data.alertId) {
              await update(ref(db, `rooms/${data.roomId}/sosAlerts/${data.alertId}/callRequest`), {
                status: "answered",
                answeredAt: serverTimestamp()
              });
            }
            await update(ref(db, `call_logs/${logId}`), {
              id: logId,
              callType: data.callType || "video",
              callerUid: data.callerUid || "",
              callerName: callerName,
              calleeUid: state.currentUser ? state.currentUser.uid : "admin",
              calleeName: "ResQTap Support (Admin)",
              status: "answered",
              roomId: data.roomId || "-",
              alertId: data.alertId || "-",
              timestamp: callTime,
              answeredAt: Date.now(),
              source: "adminCalls/incoming"
            });
          } catch (e) {}
          startAdminCall(data.callerUid, callerName, data.callType || "video", logId);
        };
      }

      if (declineBtn) {
        declineBtn.onclick = async () => {
          alertEl.classList.add("hidden");
          callRingtoneSound.stop();
          const logId = data.callLogId || `call_${data.timestamp || Date.now()}`;
          try {
            await update(ref(db, "adminCalls/incoming"), {
              status: "declined",
              declinedAt: serverTimestamp()
            });
            if (data.roomId && data.alertId) {
              await update(ref(db, `rooms/${data.roomId}/sosAlerts/${data.alertId}/callRequest`), {
                status: "declined",
                declinedAt: serverTimestamp()
              });
            }
            await update(ref(db, `call_logs/${logId}`), {
              id: logId,
              callType: data.callType || "video",
              callerUid: data.callerUid || "",
              callerName: callerName,
              calleeUid: state.currentUser ? state.currentUser.uid : "admin",
              calleeName: "ResQTap Support (Admin)",
              status: "declined",
              roomId: data.roomId || "-",
              alertId: data.alertId || "-",
              timestamp: callTime,
              declinedAt: Date.now(),
              duration: 0,
              source: "adminCalls/incoming"
            });
          } catch (e) {}
        };
      }
      return;
    }
  }

  alertEl.classList.add("hidden");
  callRingtoneSound.stop();
}

let sosAlarmTicker = null;

function getActiveSosForUser(uid) {
  if (!uid) return null;
  const found = getSosAlerts().find((a) => {
    if (a.senderUid !== uid) return false;
    if (!a.active) return false;
    if (isCancelled(a.data)) return false;
    return true;
  });
  if (found) return found;

  for (const room of getRooms()) {
    const alert = (room.alerts || []).find((a) => {
      const sUid = a.data.senderUid || a.data.fromUid;
      if (sUid !== uid) return false;
      if (isCancelled(a.data)) return false;
      return true;
    });
    if (alert) {
      return {
        roomId: room.code,
        key: alert.key,
        id: alert.data.alertId || alert.key,
        senderUid: uid
      };
    }
  }
  return null;
}

async function focusSosSenderOnMap(alert) {
  if (!alert) return;
  const alertId = alert.key || alert.id;
  const senderUid = alert.senderUid;

  // 1. Close modal if open so sidebar and map are completely visible
  closeCaseModal();
  closeCaseCallOptions();

  // 2. Set selected for detail sidebar before switching view
  state.selected = { type: "sos", roomId: alert.roomId, alertId: alertId };

  // 3. Navigate to Live Map view & open detail sidebar
  setActiveView("livemap");
  renderDetail();

  // 4. Force map init if not yet initialized
  if (!window.__resqLiveMap || !window.__resqLiveMap.leaflet || !window.__resqLiveMap.initialized) {
    if (typeof window.__resqInitLiveMap === "function") {
      window.__resqInitLiveMap();
    }
  }

  // 5. Resolve coordinates from alert payload, rooms, or user profile
  let lat = 0, lng = 0;
  if (alert.data) {
    const dLat = Number(alert.data.lat !== undefined ? alert.data.lat : alert.data.latitude);
    const dLng = Number(alert.data.lng !== undefined ? alert.data.lng : alert.data.longitude);
    if (Number.isFinite(dLat) && Number.isFinite(dLng) && !(dLat === 0 && dLng === 0)) {
      lat = dLat;
      lng = dLng;
    }
  }
  if (!lat && !lng && (alert.lat || alert.latitude)) {
    const aLat = Number(alert.lat || alert.latitude);
    const aLng = Number(alert.lng || alert.longitude);
    if (Number.isFinite(aLat) && Number.isFinite(aLng) && !(aLat === 0 && aLng === 0)) {
      lat = aLat;
      lng = aLng;
    }
  }
  if (!lat && !lng && senderUid && state.rooms) {
    for (const [, rVal] of Object.entries(state.rooms)) {
      const rm = asRecord(rVal);
      const members = asRecord(rm.members);
      if (members[senderUid]) {
        const m = asRecord(members[senderUid]);
        const mLat = Number(m.lat !== undefined ? m.lat : m.latitude);
        const mLng = Number(m.lng !== undefined ? m.lng : m.longitude);
        if (Number.isFinite(mLat) && Number.isFinite(mLng) && !(mLat === 0 && mLng === 0)) {
          lat = mLat;
          lng = mLng;
          break;
        }
      }
    }
  }
  if (!lat && !lng && senderUid && state.users && state.users[senderUid]) {
    const u = asRecord(state.users[senderUid]);
    const uLat = Number(u.lat !== undefined ? u.lat : u.latitude);
    const uLng = Number(u.lng !== undefined ? u.lng : u.longitude);
    if (Number.isFinite(uLat) && Number.isFinite(uLng) && !(uLat === 0 && uLng === 0)) {
      lat = uLat;
      lng = uLng;
    }
  }

  // Direct RTDB fetch fallback if still zero
  if (!lat && !lng && senderUid) {
    try {
      const snap = await get(ref(db, `users/${senderUid}`));
      if (snap.exists()) {
        const val = snap.val() || {};
        const uLat = Number(val.lat !== undefined ? val.lat : val.latitude);
        const uLng = Number(val.lng !== undefined ? val.lng : val.longitude);
        if (Number.isFinite(uLat) && Number.isFinite(uLng) && !(uLat === 0 && uLng === 0)) {
          lat = uLat;
          lng = uLng;
        }
      }
    } catch (e) {
      console.warn("RTDB direct loc fetch error:", e);
    }
  }

  // 6. Refresh markers so sender marker is placed with active SOS badge immediately
  if (typeof window.__resqRefreshMarkers === "function") {
    window.__resqRefreshMarkers();
  }

  // 7. Smooth flyTo zoom to user location on Leaflet map
  if (lat && lng) {
    const targetLatLng = [lat, lng];
    const doZoom = () => {
      if (window.__resqLiveMap && window.__resqLiveMap.leaflet) {
        try {
          const map = window.__resqLiveMap.leaflet;
          map.invalidateSize();
          map.flyTo(targetLatLng, 18, {
            animate: true,
            duration: 1.2,
            easeLinearity: 0.25
          });

          if (window.__resqLiveMap.markers) {
            const marker = window.__resqLiveMap.markers.get(`u:${senderUid}`)
              || window.__resqLiveMap.markers.get(`sos:${alert.key}`)
              || window.__resqLiveMap.markers.get(`sos:${alert.id}`);
            if (marker) {
              marker.openPopup();
            }
          }
        } catch (e) {
          console.warn("Leaflet zoom error:", e);
        }
      }
    };
    [60, 200, 450, 800, 1400].forEach((delay) => setTimeout(doZoom, delay));
  } else {
    showToast(`Waiting for GPS coordinates from ${alert.senderName || "user"}...`);
  }
}

function updateSosAlarmSystem() {
  const now = Date.now();
  // Filter active, non-cancelled, UN-SERVED SOS alerts created within the last 60 seconds (with clock-skew tolerance)
  const activeAlerts = getSosAlerts().filter((alert) => {
    if (!alert.active) return false;
    if (isCancelled(alert.data)) return false;
    if (alert.data && (alert.data.served || alert.data.servedBy || alert.data.resolvedAt || (alert.data.progressStep && Number(alert.data.progressStep) >= 1))) return false;
    const aId = alert.key || alert.id;
    if (state.servedCases && (state.servedCases.has(aId) || state.servedCases.has(alert.key) || state.servedCases.has(alert.id))) return false;

    // Use alert's createdAt or fallback to current time if missing/zero
    const createdAt = alert.createdAt && alert.createdAt > 0 ? alert.createdAt : now;
    const age = now - createdAt;
    // Allow age between -120s (Google server time ahead of local PC clock) and +60s (active alarm window)
    return age >= -120000 && age <= 60000;
  });

  let banner = document.getElementById("sosAlarmBanner");
  const SHIELD_SVG = `<svg width="15" height="15" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2.4" stroke-linecap="round" stroke-linejoin="round" style="flex-shrink:0;"><path d="M12 22s8-4 8-10V5l-8-3-8 3v7c0 6 8 10 8 10z"/></svg>`;
  const MAP_PIN_SVG = `<svg width="15" height="15" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2.4" stroke-linecap="round" stroke-linejoin="round" style="flex-shrink:0;"><path d="M20 10c0 6-8 12-8 12s-8-6-8-12a8 8 0 0 1 16 0Z"/><circle cx="12" cy="10" r="3"/></svg>`;

  if (!banner) {
    banner = document.createElement("div");
    banner.id = "sosAlarmBanner";
    banner.className = "sos-alarm-banner hidden";
    banner.innerHTML = `
      <div class="sos-alarm-icon">🚨</div>
      <div class="sos-alarm-text">
        <span class="sos-alarm-title">EMERGENCY SOS ACTIVE</span>
        <span class="sos-alarm-subtitle" id="sosAlarmSubtitle">-</span>
      </div>
      <span class="sos-alarm-timer" id="sosAlarmTimer">30s</span>
      <div class="sos-alarm-actions">
        <button class="sos-alarm-btn sos-alarm-btn-serve" id="sosAlarmBtnServe" type="button">
          ${SHIELD_SVG}<span>Serve Case</span>
        </button>
        <button class="sos-alarm-btn sos-alarm-btn-view" id="sosAlarmBtnView" type="button">
          ${MAP_PIN_SVG}<span>View on Map</span>
        </button>
      </div>
    `;
    document.body.appendChild(banner);
  } else {
    // If old banner exists in DOM, clean up any old mute button
    const oldMute = document.getElementById("sosAlarmBtnMute");
    if (oldMute) oldMute.remove();
  }

  if (!activeAlerts.length) {
    sosAlarmSound.stop();
    banner.classList.add("hidden");
    if (sosAlarmTicker) {
      clearInterval(sosAlarmTicker);
      sosAlarmTicker = null;
    }
    return;
  }

  const latestAlert = activeAlerts.sort((a, b) => b.createdAt - a.createdAt)[0];
  const alertId = latestAlert.key || latestAlert.id;
  const alertCreated = latestAlert.createdAt && latestAlert.createdAt > 0 ? latestAlert.createdAt : now;
  // If PC clock is behind Google NTP (now < alertCreated), treat elapsed as 0 so full 30s is shown
  const elapsedMs = Math.max(0, now - alertCreated);
  const remainingMs = Math.max(0, 30000 - elapsedMs);
  const remainingSec = Math.max(1, Math.ceil(remainingMs / 1000));

  // Play audio alarm (will skip if already muted/served)
  sosAlarmSound.play(alertId);

  const subtitleEl = document.getElementById("sosAlarmSubtitle");
  const timerEl = document.getElementById("sosAlarmTimer");
  const btnServe = document.getElementById("sosAlarmBtnServe");
  const btnView = document.getElementById("sosAlarmBtnView");

  if (subtitleEl) {
    const roomTxt = latestAlert.roomId === "DIRECT" ? "Direct SOS (No Room)" : `Room ${latestAlert.roomId || "-"}`;
    subtitleEl.textContent = `${latestAlert.senderName || "User"} • ${roomTxt}`;
  }
  if (timerEl) {
    timerEl.textContent = `${remainingSec}s`;
  }

  if (btnServe) {
    btnServe.innerHTML = `${SHIELD_SVG}<span>Serve Case</span>`;
    btnServe.classList.remove("is-served");
    btnServe.disabled = false;
    btnServe.onclick = async () => {
      state.servedCases = state.servedCases || new Set();
      state.servedCases.add(alertId);
      if (latestAlert.key) state.servedCases.add(latestAlert.key);
      if (latestAlert.id) state.servedCases.add(latestAlert.id);

      // AUTOMATICALLY MUTE & STOP SIREN IMMEDIATELY
      sosAlarmSound.mute(alertId);
      sosAlarmSound.stop();

      // HILANGKAN TERUS BANNER
      if (banner) {
        banner.classList.add("hidden");
      }

      showToast(`Case reserved for ${latestAlert.senderName || "user"}.`);

      // Reserve case in RTDB so Android user gets taken to SOS Progress page!
      await reserveSosCase(latestAlert.roomId, alertId, latestAlert.senderUid, latestAlert.senderName);

      // Immediately refresh SOS alarm system so ticker sees 0 active unserved alerts
      updateSosAlarmSystem();

      // Ensure local alert state reflects active served case so sidebar renders active immediately
      latestAlert.active = true;
      latestAlert.cancelledAt = 0;
      if (latestAlert.data) {
        latestAlert.data.active = true;
        latestAlert.data.status = "active";
        latestAlert.data.served = true;
        delete latestAlert.data.cancelledAt;
        delete latestAlert.data.cancelledClientAt;
      }

      // Zoom to user location on Live Map and open the detail sidebar!
      await focusSosSenderOnMap(latestAlert);
    };
  }

  if (btnView) {
    btnView.onclick = async () => {
      await focusSosSenderOnMap(latestAlert);
    };
  }

  banner.classList.remove("hidden");

  if (!sosAlarmTicker) {
    sosAlarmTicker = setInterval(() => {
      updateSosAlarmSystem();
      if (typeof window.__resqRefreshMarkers === "function") {
        window.__resqRefreshMarkers();
      }
    }, 1000);
  }
}

function render() {
  if (els.appShell.classList.contains("hidden")) return;
  updateSosAlarmSystem();
  renderNav();
  renderDashboard();
  renderUsers();
  renderRooms();
  renderSos();
  renderReports();
  renderNotices();
  renderSosLivechat();
  renderLivechat();
  if (els.aiChatCount) renderAiChat();
  renderAdmins();
  renderHighlights();
  renderAdminProfile();
  renderDetail();

  // If case update modal is open and the victim cancelled the alert, auto-close modal
  if (activeCaseModalData) {
    const modalAlert = getSosAlerts().find(
      (a) => a.roomId === activeCaseModalData.roomId && (a.key === activeCaseModalData.alertId || a.id === activeCaseModalData.alertId)
    );
    if (!modalAlert || isCancelled(modalAlert.data) || !modalAlert.active) {
      const sName = activeCaseModalData.senderName || "User";
      closeCaseModal();
      showToast(`Emergency SOS was cancelled by ${sName}.`);
    }
  }

  refreshIcons();
  if (typeof window.__resqRefreshMarkers === "function") {
    window.__resqRefreshMarkers();
  }
}

function renderNav() {
  if (state.activeView === "aichat") {
    state.activeView = "dashboard";
  }
  const titles = {
    dashboard: "Dashboard",
    users: "Users",
    rooms: "Channels",
    livemap: "SOS Alert",
    soslivechat: "SOS Livechat",
    reports: "Reports",
    notices: "Notifications",
    livechat: "Ticket",
    highlights: "Highlights (Mobile Carousel)",
    logs: "Logs",
    admins: "Admins",
    profile: "Admin Profile"
  };
  const attentionState = getNavAttentionState();
  markNavViewSeen(state.activeView, attentionState);
  els.viewTitle.textContent = titles[state.activeView] || "Dashboard";
  document.querySelectorAll(".nav-button").forEach((button) => {
    const view = button.dataset.view || "";
    const attention = attentionState[view] || { count: 0, signature: "" };
    const attentionCount = Number(attention.count) || 0;
    const attentionSignature = text(attention.signature);
    const hasUnseenAttention = attentionCount > 0 && attentionSignature && state.navSeenSignatures[view] !== attentionSignature;
    button.classList.toggle("is-active", view === state.activeView);
    button.classList.toggle("has-dot", hasUnseenAttention);
    if (hasUnseenAttention) {
      button.dataset.attentionCount = String(attentionCount);
      button.title = `${attentionCount} item${attentionCount === 1 ? "" : "s"} need attention`;
    } else {
      delete button.dataset.attentionCount;
      button.removeAttribute("title");
    }
  });
  document.querySelectorAll(".view-section").forEach((section) => section.classList.add("hidden"));
  const active = document.getElementById(`${state.activeView}View`);
  if (active) active.classList.remove("hidden");
}

function renderDashboard() {
  const model = getDashboardModel();
  els.dashboardRangeSelect.value = state.dashboardRange;
  const cards = [
    {
      label: "Total Users",
      value: model.totals.users,
      trend: model.trends.users,
      iconName: "user-round",
      tone: "purple"
    },
    {
      label: "Communication",
      value: model.totals.communication,
      trend: model.trends.communication,
      iconName: "message-square",
      tone: "green"
    },
    {
      label: "Emergency",
      value: model.totals.emergency,
      trend: model.trends.emergency,
      iconName: "siren",
      tone: "red"
    }
  ];
  const signature = dashboardRenderSignature(model, cards);
  if (state.dashboardSignature === signature) return;
  state.dashboardSignature = signature;

  els.metricGrid.innerHTML = cards.map((card, index) => `
    <article class="metric-card stat-card ${card.tone}">
      <div class="metric-copy">
        <span class="metric-label">${escapeHtml(card.label)}</span>
        <strong data-metric-index="${index}">${escapeHtml(formatNumber(state.dashboardMetricValues[card.label] ?? card.value))}</strong>
        <em class="metric-trend ${card.trend.tone}">
          ${icon(card.trend.icon)}
          <span>${escapeHtml(card.trend.text)}</span>
        </em>
      </div>
      <div class="stat-icon">${icon(card.iconName)}</div>
    </article>
  `).join("");

  els.usageChart.setAttribute("aria-label", `Usage statistics for ${model.rangeLabel}`);
  els.usageChart.innerHTML = renderLineChart(model.buckets, model.series);
  els.featureChart.innerHTML = renderFeatureDonut(model.featureGroups);
  els.featureLegend.innerHTML = renderFeatureLegend(model.featureGroups);
  animateDashboardMetrics(cards);
}

function renderUsers() {
  const allUsers = getUsers()
    .filter((user) => matchesSearch([
      user.uid,
      user.data.name,
      user.data.email,
      user.data.publicId,
      user.data.phoneNumber,
      user.data.bloodType
    ]));

  const onlineCount = allUsers.filter((u) => Date.now() - u.lastSeen <= ONLINE_MS).length;
  const offlineCount = allUsers.length - onlineCount;

  if (els.userCountAll) els.userCountAll.textContent = allUsers.length;
  if (els.userCountOnline) els.userCountOnline.textContent = onlineCount;
  if (els.userCountOffline) els.userCountOffline.textContent = offlineCount;

  const rows = allUsers
    .filter((user) => {
      const live = Date.now() - user.lastSeen <= ONLINE_MS;
      if (state.userFilter === "online") return live;
      if (state.userFilter === "offline") return !live;
      return true;
    })
    .sort((a, b) => b.lastSeen - a.lastSeen);

  els.usersCount.textContent = `${rows.length} shown (${allUsers.length} total)`;
  els.usersTableBody.innerHTML = rows.length ? rows.map((user) => {
    const roomCount = entries(user.rooms).length;
    const contactCount = user.contacts.length;
    const medical = [user.data.bloodType || "-", user.data.allergies ? "Allergies" : "", user.data.existingConditions ? "Conditions" : ""]
      .filter(Boolean)
      .join(" / ");
    const live = Date.now() - user.lastSeen <= ONLINE_MS;
    return `
      <tr>
        <td>
          <div class="user-cell">
            ${avatarHtml(user.data, user.uid)}
            <div class="cell-main">
              <strong>${escapeHtml(user.data.name || "Unnamed")}</strong>
              <span>${escapeHtml(user.data.email || "-")}</span>
              <span>${escapeHtml(user.data.publicId || user.uid)}</span>
            </div>
          </div>
        </td>
        <td>${escapeHtml(medical)}</td>
        <td>${contactCount}</td>
        <td>${roomCount}</td>
        <td>${pill(live ? "Online" : "Offline", live ? "good" : "neutral")}</td>
        <td>${user.lastSeen ? escapeHtml(ageLabel(user.lastSeen)) : '<span class="muted">Never</span>'}</td>
        <td>
          <div class="row-actions">
            <button class="small-button" data-action="view-user" data-uid="${escapeHtml(user.uid)}" data-force-profile="true" type="button">${icon("eye")}<span>View</span></button>
            <button class="small-button danger" data-action="delete-user" data-uid="${escapeHtml(user.uid)}" type="button">${icon("trash-2")}<span>Delete</span></button>
          </div>
        </td>
      </tr>
    `;
  }).join("") : emptyRow(7, "No users match the current search or filter.");
}

function renderRooms() {
  const rows = getRooms()
    .filter((room) => matchesSearch([
      room.code,
      room.data.name,
      room.data.creatorUid,
      userName(room.data.creatorUid)
    ]))
    .sort((a, b) => b.updatedAt - a.updatedAt);

  els.roomsCount.textContent = `${rows.length} shown`;
  els.roomsTableBody.innerHTML = rows.length ? rows.map((room) => {
    const activeAlerts = room.alerts.filter((alert) => !isCancelled(alert.data)).length;
    return `
      <tr>
        <td>
          <div class="cell-main">
            <strong>${escapeHtml(room.data.name || room.code)}</strong>
            <span>${escapeHtml(room.code)}</span>
          </div>
        </td>
        <td>${escapeHtml(userName(room.data.creatorUid))}</td>
        <td>${room.members.length}</td>
        <td>${pill(room.liveCount, room.liveCount ? "good" : "")}</td>
        <td>${pill(activeAlerts, activeAlerts ? "alert" : "")}</td>
        <td>${escapeHtml(ageLabel(room.updatedAt))}</td>
        <td>
          <div class="row-actions">
            <button class="small-button" data-action="view-room" data-room="${escapeHtml(room.code)}" type="button">${icon("eye")}<span>View</span></button>
            <button class="small-button danger" data-action="delete-room" data-room="${escapeHtml(room.code)}" type="button">${icon("trash-2")}<span>Delete</span></button>
          </div>
        </td>
      </tr>
    `;
  }).join("") : emptyRow(7, "No channels match the current search.");
}

function renderSos() {
  const rows = getSosAlerts()
    .filter((alert) => matchesSearch([
      alert.id,
      alert.roomId,
      alert.senderUid,
      alert.senderName,
      alert.source
    ]));

  els.sosCount.textContent = `${rows.length} shown`;
  els.sosTableBody.innerHTML = rows.length ? rows.map((alert) => {
    const isResolved = Boolean(alert.data && (alert.data.resolvedAt || alert.data.status === "resolved" || Number(alert.data.progressStep) >= 4));
    const label = alert.active ? (alert.stale ? "Stale" : "Active") : (isResolved ? "Resolved" : "Cancelled");
    const tone = alert.active ? (alert.stale ? "warn" : "alert") : (isResolved ? "good" : "neutral");
    const canCancel = alert.source === "room" && alert.active && !isResolved;
    const roomBadge = alert.roomId === "DIRECT"
      ? '<span class="pill-badge pill-neutral">Direct (No Channel)</span>'
      : escapeHtml(alert.roomId || "-");
    return `
      <tr>
        <td>${pill(label, tone)}</td>
        <td>${roomBadge}</td>
        <td>
          <div class="cell-main">
            <strong>${escapeHtml(alert.senderName || "Unknown")}</strong>
            <span>${escapeHtml(alert.senderUid || "-")}</span>
          </div>
        </td>
        <td>${escapeHtml(formatDate(alert.createdAt))}</td>
        <td>${escapeHtml(ageLabel(alert.createdAt))}</td>
        <td>
          <div class="row-actions">
            <button class="small-button" data-action="call-sos" data-uid="${escapeHtml(alert.senderUid)}" data-name="${escapeHtml(alert.senderName)}" type="button">${icon("phone")}<span>Call</span></button>
            <button class="small-button" data-action="view-sos" data-room="${escapeHtml(alert.roomId)}" data-alert="${escapeHtml(alert.key)}" type="button">${icon("eye")}<span>View</span></button>
            ${canCancel ? `<button class="small-button danger" data-action="cancel-sos" data-room="${escapeHtml(alert.roomId)}" data-alert="${escapeHtml(alert.key)}" type="button">${icon("circle-x")}<span>Cancel</span></button>` : ""}
          </div>
        </td>
      </tr>
    `;
  }).join("") : emptyRow(6, "No SOS alerts match the current search.");
}

function renderReports() {
  const rows = getIncidentReports()
    .filter((report) => matchesSearch([
      report.id,
      report.senderUid,
      report.senderName,
      report.senderEmail,
      report.publicId,
      report.category,
      report.categoryLabel,
      report.status,
      report.address,
      report.details
    ]));

  els.reportsCount.textContent = `${rows.length} shown`;
  els.reportsTableBody.innerHTML = rows.length ? rows.map((report) => {
    const status = reportStatusMeta(report.status);
    const hasMap = Number.isFinite(report.latitude) && Number.isFinite(report.longitude);
    return `
      <tr>
        <td>${pill(status.label, status.tone)}</td>
        <td>
          <div class="cell-main">
            <strong>${escapeHtml(report.categoryLabel)}</strong>
            <span>${escapeHtml(report.id)}</span>
          </div>
        </td>
        <td>
          <div class="cell-main">
            <strong>${escapeHtml(report.senderName || "Unknown")}</strong>
            <span>${escapeHtml(report.senderEmail || report.senderUid || "-")}</span>
          </div>
        </td>
        <td>
          <div class="cell-main">
            <strong>${escapeHtml(report.address || "-")}</strong>
            <span>${hasMap ? `${escapeHtml(report.latitude.toFixed(5))}, ${escapeHtml(report.longitude.toFixed(5))}` : "-"}</span>
          </div>
        </td>
        <td>${escapeHtml(formatDate(report.createdAt))}</td>
        <td>
          <div class="row-actions">
            <button class="small-button" data-action="view-report" data-report="${escapeHtml(report.key)}" type="button">${icon("eye")}<span>View</span></button>
            ${report.status !== "reviewing" ? `<button class="small-button" data-action="set-report-status" data-report="${escapeHtml(report.key)}" data-status="reviewing" type="button">${icon("clock")}<span>Review</span></button>` : ""}
            ${report.status !== "resolved" ? `<button class="small-button" data-action="set-report-status" data-report="${escapeHtml(report.key)}" data-status="resolved" type="button">${icon("check-circle-2")}<span>Resolve</span></button>` : ""}
            <button class="small-button danger" data-action="delete-report" data-report="${escapeHtml(report.key)}" type="button">${icon("trash-2")}<span>Delete</span></button>
          </div>
        </td>
      </tr>
    `;
  }).join("") : emptyRow(6, "No incident reports match the current search.");
}

function renderNotices() {
  const searchedRows = getNotificationHistory()
    .filter((notification) => matchesSearch([
      notification.id,
      notification.type,
      notification.recipient,
      notification.recipientMeta,
      notification.sender,
      notification.senderMeta,
      notification.title,
      notification.message,
      notification.roomId,
      notification.source
    ]));
  const activeCategory = notificationCategory(state.notificationCategory);
  const filteredRows = searchedRows.filter((notification) => (
    matchesNotificationCategory(notification, activeCategory.id)
  ));
  const pageSize = NOTIFICATION_PAGE_SIZES.includes(state.notificationPageSize)
    ? state.notificationPageSize
    : 10;
  const totalPages = Math.max(1, Math.ceil(filteredRows.length / pageSize));
  const page = Math.min(Math.max(Number(state.notificationPage) || 1, 1), totalPages);
  const start = (page - 1) * pageSize;
  const rows = filteredRows.slice(start, start + pageSize);

  state.notificationCategory = activeCategory.id;
  state.notificationPage = page;
  renderNotificationCategoryTabs(searchedRows, activeCategory.id);

  els.notificationPageSizeSelect.value = String(pageSize);
  els.notificationsCount.textContent = filteredRows.length
    ? `${start + 1}-${start + rows.length} of ${filteredRows.length}`
    : "0 shown";
  els.notificationPagination.classList.toggle("hidden", filteredRows.length === 0);
  els.notificationPageSummary.textContent = `Page ${page} of ${totalPages}`;
  els.notificationPrevPageButton.disabled = page <= 1;
  els.notificationNextPageButton.disabled = page >= totalPages;
  els.notificationsTableBody.innerHTML = rows.length ? rows.map((notification) => {
    let tone = "";
    if (notification.type.includes("SOS")) {
      tone = notification.type.includes("Cancelled") ? "good" : "alert";
    } else if (notification.type === "Database Reset" || notification.type === "System Cleanup") {
      tone = "warn";
    } else if (notification.type.includes("Answered")) {
      tone = "good";
    } else if (notification.type.includes("Missed") || notification.type.includes("Declined")) {
      tone = "alert";
    } else if (notification.type.includes("Call")) {
      tone = "warn";
    }
    return `
      <tr>
        <td>${pill(notification.type, tone)}</td>
        <td>
          <div class="cell-main">
            <strong>${escapeHtml(notification.recipient || "-")}</strong>
            <span>${escapeHtml(notification.recipientMeta || "-")}</span>
          </div>
        </td>
        <td>
          <div class="cell-main">
            <strong>${escapeHtml(notification.sender || "System")}</strong>
            <span>${escapeHtml(notification.message || notification.senderMeta || "-")}</span>
          </div>
        </td>
        <td>${escapeHtml(notification.roomId || "-")}</td>
        <td>${escapeHtml(notification.sentAt ? formatDate(notification.sentAt) : "-")}</td>
        <td>${escapeHtml(notification.source)}</td>
        <td>
          <div class="row-actions">
            ${notification.canDelete ? `<button class="small-button danger" data-action="delete-notification" data-notice="${escapeHtml(notification.id)}" type="button">${icon("trash-2")}<span>Delete</span></button>` : ""}
          </div>
        </td>
      </tr>
    `;
  }).join("") : emptyRow(7, `No ${activeCategory.label.toLowerCase()} history matches the current search.`);
}

function getSosLivechatSessions() {
  const alerts = getSosAlerts();
  const activeAlertsByUser = new Map();
  const resolvedAlerts = [];

  alerts.forEach((alert) => {
    const data = asRecord(alert.data);
    const chat = asRecord(data.chat);
    const meta = asRecord(chat.meta);
    const rawMessages = asRecord(chat.messages);
    const messages = entries(rawMessages).map(([id, m]) => {
      const val = asRecord(m);
      return {
        id,
        text: text(val.text || val.message),
        sender: text(val.sender || (val.senderType === "admin" ? "admin" : "user")),
        senderName: text(val.senderName || (val.sender === "admin" ? "Admin Responder" : alert.senderName)),
        senderUid: text(val.senderUid),
        createdAt: millis(val.createdAt || val.timestamp)
      };
    }).sort((a, b) => a.createdAt - b.createdAt);

    const isAlertActive = alert.active && !isCancelled(alert.data);
    const lastMsg = text(meta.lastMessage) || (messages.length ? messages[messages.length - 1].text : "");
    const lastTime = millis(meta.updatedAt) || (messages.length ? messages[messages.length - 1].createdAt : alert.createdAt);

    const activeAlertKey = alert.key || alert.id;
    const sessionObj = {
      sessionId: `${alert.roomId}__${activeAlertKey}`,
      roomId: alert.roomId,
      alertId: activeAlertKey,
      senderUid: alert.senderUid,
      senderName: alert.senderName,
      active: isAlertActive,
      status: isAlertActive ? (data.servedBy ? "served" : "active") : (data.status || "resolved"),
      lastMessage: lastMsg,
      updatedAt: lastTime,
      createdAt: alert.createdAt,
      messageCount: messages.length,
      messages,
      meta,
      data: alert
    };

    if (isAlertActive && alert.senderUid) {
      if (!activeAlertsByUser.has(alert.senderUid)) {
        activeAlertsByUser.set(alert.senderUid, []);
      }
      activeAlertsByUser.get(alert.senderUid).push(sessionObj);
    } else {
      // Only keep resolved cases that actually have chat messages
      if (messages.length > 0) {
        resolvedAlerts.push(sessionObj);
      }
    }
  });

  const finalSessions = [];

  // Merge multi-room active alerts for the same victim into one single session
  activeAlertsByUser.forEach((userSessions) => {
    if (userSessions.length === 1) {
      const s = userSessions[0];
      s.allRooms = [s.roomId];
      s.allAlerts = [{ roomId: s.roomId, alertId: s.alertId }];
      s.allSessionIds = [s.sessionId];
      s.roomDisplay = s.roomId === "DIRECT" ? "Direct SOS" : s.roomId;
      finalSessions.push(s);
      return;
    }

    // Sort to prioritize the room that already has messages, then latest update
    userSessions.sort((a, b) => {
      if (b.messageCount !== a.messageCount) {
        return b.messageCount - a.messageCount;
      }
      return b.updatedAt - a.updatedAt;
    });

    const primary = userSessions[0];
    const allRooms = [...new Set(userSessions.map((s) => s.roomId).filter(Boolean))];
    const allAlerts = userSessions.map((s) => ({ roomId: s.roomId, alertId: s.alertId }));
    const allSessionIds = userSessions.map((s) => s.sessionId);

    // Merge any messages from secondary rooms with robust deduplication
    const combinedMessages = [];
    const seenIds = new Set();
    userSessions.forEach((s) => {
      (s.messages || []).forEach((m) => {
        if (!m || !m.text) return;
        if (m.id && seenIds.has(m.id)) return;

        const isDuplicate = combinedMessages.some((existing) => {
          if (m.id && existing.id && m.id === existing.id) return true;
          const sameSender = existing.sender === m.sender;
          const sameText = String(existing.text).trim().toLowerCase() === String(m.text).trim().toLowerCase();
          const closeTime = Math.abs((existing.createdAt || 0) - (m.createdAt || 0)) < 15000;
          return sameSender && sameText && closeTime;
        });

        if (!isDuplicate) {
          if (m.id) seenIds.add(m.id);
          combinedMessages.push(m);
        }
      });
    });
    combinedMessages.sort((a, b) => a.createdAt - b.createdAt);

    primary.allRooms = allRooms;
    primary.roomDisplay = allRooms.length > 1 ? `${primary.roomId} (+${allRooms.length - 1} channels)` : primary.roomId;
    primary.allAlerts = allAlerts;
    primary.allSessionIds = allSessionIds;
    primary.messages = combinedMessages;
    primary.messageCount = combinedMessages.length;
    if (combinedMessages.length > 0) {
      primary.lastMessage = combinedMessages[combinedMessages.length - 1].text;
      primary.updatedAt = combinedMessages[combinedMessages.length - 1].createdAt || primary.updatedAt;
    }

    finalSessions.push(primary);
  });

  resolvedAlerts.forEach((r) => {
    r.allRooms = [r.roomId];
    r.allAlerts = [{ roomId: r.roomId, alertId: r.alertId }];
    r.allSessionIds = [r.sessionId];
    r.roomDisplay = r.roomId;
    finalSessions.push(r);
  });

  return finalSessions.sort((a, b) => b.updatedAt - a.updatedAt);
}

function renderSosLivechat() {
  const allSessions = getSosLivechatSessions();
  const searchVal = text(els.soslivechatSearchInput ? els.soslivechatSearchInput.value : "").trim().toLowerCase();
  const searchedSessions = allSessions.filter((s) => {
    if (!searchVal) return true;
    return s.roomId.toLowerCase().includes(searchVal)
      || (s.allRooms && s.allRooms.some((r) => r.toLowerCase().includes(searchVal)))
      || s.senderName.toLowerCase().includes(searchVal)
      || s.alertId.toLowerCase().includes(searchVal)
      || s.lastMessage.toLowerCase().includes(searchVal);
  });

  if (els.soslivechatCount) {
    els.soslivechatCount.textContent = `${searchedSessions.length} session${searchedSessions.length === 1 ? "" : "s"}`;
  }

  const selectedExists = state.selectedSosSessionId
    && allSessions.some((s) => s.sessionId === state.selectedSosSessionId || (s.allSessionIds && s.allSessionIds.includes(state.selectedSosSessionId)));

  if (!selectedExists) {
    const firstActive = searchedSessions.find((s) => s.active);
    if (firstActive && state.activeView === "soslivechat" && !state.selectedSosSessionId) {
      state.selectedSosSessionId = firstActive.sessionId;
    } else if (!firstActive && !allSessions.some((s) => s.sessionId === state.selectedSosSessionId || (s.allSessionIds && s.allSessionIds.includes(state.selectedSosSessionId)))) {
      state.selectedSosSessionId = "";
    }
  }

  if (els.soslivechatThreadList) {
    els.soslivechatThreadList.innerHTML = searchedSessions.length ? searchedSessions.map((session) => {
      const isSelected = session.sessionId === state.selectedSosSessionId
        || (session.allSessionIds && session.allSessionIds.includes(state.selectedSosSessionId));
      const userRec = asRecord(state.users?.[session.senderUid]);
      const avatar = avatarHtml({
        name: session.senderName,
        email: userRec.email,
        photoUrl: userRec.photoUrl,
        photoB64: userRec.photoB64
      }, session.senderUid);

      const statusTone = session.active ? (session.status === "served" ? "warning" : "danger") : "slate";
      const statusLabel = session.active ? (session.status === "served" ? "DISPATCHED" : "ACTIVE SOS") : "RESOLVED";

      return `
        <button class="livechat-thread${isSelected ? " is-active" : ""}" data-action="select-sos-chat" data-session-id="${escapeHtml(session.sessionId)}" type="button">
          ${avatar}
          <span class="livechat-thread-main">
            <span class="livechat-thread-head">
              <strong>${escapeHtml(session.senderName || "Unknown")}</strong>
              ${pill(statusLabel, statusTone)}
            </span>
            <span style="font-size:12.5px; opacity:0.9;">${escapeHtml(session.lastMessage || "No messages sent yet")}</span>
            <span class="cell-meta">${escapeHtml(ageLabel(session.updatedAt))} • ${session.messageCount} msg</span>
          </span>
          ${session.active ? `<span class="livechat-unread" style="background:#e11d48;" aria-label="Active SOS session"></span>` : ""}
        </button>
      `;
    }).join("") : `<div class="livechat-empty inline"><strong>No SOS Chat Sessions</strong><span>Active and resolved SOS cases will appear here.</span></div>`;
  }

  const selectedSession = allSessions.find((s) => s.sessionId === state.selectedSosSessionId || (s.allSessionIds && s.allSessionIds.includes(state.selectedSosSessionId)));

  if (!selectedSession) {
    if (els.soslivechatSelectedTitle) els.soslivechatSelectedTitle.textContent = "Select an SOS Incident";
    if (els.soslivechatSelectedSubtitle) els.soslivechatSelectedSubtitle.textContent = "Active emergency alerts and responder livechats appear here.";
    if (els.soslivechatMessages) {
      els.soslivechatMessages.innerHTML = `
        <div class="livechat-empty">
          <strong>No SOS incident selected</strong>
          <span>Select an SOS livechat session from the left to communicate with the victim.</span>
        </div>
      `;
    }
    if (els.soslivechatReplyInput) els.soslivechatReplyInput.disabled = true;
    if (els.soslivechatReplyButton) els.soslivechatReplyButton.disabled = true;
    if (els.soslivechatCallVideoBtn) els.soslivechatCallVideoBtn.disabled = true;
    if (els.soslivechatCallVoiceBtn) els.soslivechatCallVoiceBtn.disabled = true;
    if (els.soslivechatViewAlertBtn) els.soslivechatViewAlertBtn.disabled = true;
    if (els.soslivechatClearBtn) els.soslivechatClearBtn.disabled = true;
    return;
  }

  if (els.soslivechatSelectedTitle) {
    els.soslivechatSelectedTitle.textContent = `SOS: ${selectedSession.senderName || "Mangsa"}`;
  }
  if (els.soslivechatSelectedSubtitle) {
    const roomSub = (selectedSession.roomId === "DIRECT" || selectedSession.roomDisplay === "Direct SOS")
      ? "Direct Emergency (No Room)"
      : (selectedSession.roomDisplay ? `Room ${selectedSession.roomDisplay}` : "");
    els.soslivechatSelectedSubtitle.textContent = `${roomSub ? `${roomSub} • ` : ""}Created ${formatDate(selectedSession.createdAt)}`;
  }

  if (els.soslivechatCallVideoBtn) {
    els.soslivechatCallVideoBtn.disabled = false;
    els.soslivechatCallVideoBtn.onclick = () => {
      startAdminCall(selectedSession.senderUid, selectedSession.senderName, "video");
    };
  }
  if (els.soslivechatCallVoiceBtn) {
    els.soslivechatCallVoiceBtn.disabled = false;
    els.soslivechatCallVoiceBtn.onclick = () => {
      startAdminCall(selectedSession.senderUid, selectedSession.senderName, "voice");
    };
  }
  if (els.soslivechatViewAlertBtn) {
    els.soslivechatViewAlertBtn.disabled = false;
    els.soslivechatViewAlertBtn.onclick = () => {
      openCaseModal(selectedSession.roomId, selectedSession.alertId, selectedSession.senderUid, selectedSession.senderName);
    };
  }
  if (els.soslivechatClearBtn) {
    els.soslivechatClearBtn.disabled = !selectedSession.messages || selectedSession.messages.length === 0;
    els.soslivechatClearBtn.onclick = () => {
      clearSosLivechatMessages(selectedSession);
    };
  }

  if (els.soslivechatReplyInput) {
    els.soslivechatReplyInput.disabled = state.soslivechatSending;
    if (els.soslivechatReplyButton) {
      els.soslivechatReplyButton.disabled = !els.soslivechatReplyInput.value.trim() || state.soslivechatSending;
    }
  }

  // Render message stream
  if (els.soslivechatMessages) {
    if (!selectedSession.messages || selectedSession.messages.length === 0) {
      els.soslivechatMessages.innerHTML = `
        <div class="livechat-empty">
          <strong style="color:var(--danger, #e11d48);">Saluran SOS Langsung Dibuka</strong>
          <span>Mangsa belum menghantar mesej teks. Anda boleh hantar balasan kecemasan pertama sekarang di bawah.</span>
        </div>
      `;
    } else {
      els.soslivechatMessages.innerHTML = selectedSession.messages.map((m) => {
        const isAdmin = m.sender === "admin";
        const timeStr = m.createdAt ? formatTimeOnly(m.createdAt) : "";
        const senderName = m.senderName || (isAdmin ? "Admin Responder" : selectedSession.senderName);
        const msgClass = isAdmin ? "from-admin" : "from-user is-sos-victim";
        return `
          <div class="livechat-message ${msgClass}">
            <div class="livechat-bubble" style="${!isAdmin ? "background:#FFE4EE !important; color:#9F1239 !important; border:1px solid #FECDD3 !important;" : ""}">
              <span class="livechat-sender" style="font-weight:700; ${!isAdmin ? "color:#E11D48 !important;" : ""}">${escapeHtml(senderName)}</span>
              <p style="margin:4px 0 6px 0; font-size:14.5px; line-height:1.45; word-break:break-word;">${escapeHtml(m.text)}</p>
              <time style="font-size:11px; opacity:0.75; display:block; text-align:right;">${escapeHtml(timeStr)}</time>
            </div>
          </div>
        `;
      }).join("");
      els.soslivechatMessages.scrollTop = els.soslivechatMessages.scrollHeight;
    }
  }
}

async function clearSosLivechatMessages(sessionParam) {
  const allSessions = getSosLivechatSessions();
  const session = sessionParam || allSessions.find((s) => s.sessionId === state.selectedSosSessionId || (s.allSessionIds && s.allSessionIds.includes(state.selectedSosSessionId)));
  if (!session) {
    showToast("SOS Session not found.");
    return;
  }

  if (!window.confirm(`Adakah anda pasti ingin memadam semua mesej dalam sesi SOS Livechat (${session.senderName || "Mangsa"}) ini?`)) {
    return;
  }

  try {
    const alertsToClear = session.allAlerts && session.allAlerts.length
      ? session.allAlerts
      : [{ roomId: session.roomId, alertId: session.alertId }];

    const updates = {};
    alertsToClear.forEach((item) => {
      updates[`rooms/${item.roomId}/sosAlerts/${item.alertId}/chat/messages`] = null;
      updates[`rooms/${item.roomId}/sosAlerts/${item.alertId}/chat/meta/lastMessage`] = "";
      updates[`rooms/${item.roomId}/sosAlerts/${item.alertId}/chat/meta/updatedAt`] = serverTimestamp();
    });

    await update(ref(db), updates);
    showToast("Semua mesej SOS Livechat telah dipadam.");
  } catch (err) {
    console.error("Error clearing SOS chat messages:", err);
    showToast("Gagal memadam mesej: " + (err.message || err));
  }
}

async function handleSosLivechatSubmit(event) {
  if (event) event.preventDefault();
  if (!els.soslivechatReplyInput) return;
  const message = els.soslivechatReplyInput.value.trim();
  if (!message || !state.selectedSosSessionId) return;

  const allSessions = getSosLivechatSessions();
  const session = allSessions.find((s) => s.sessionId === state.selectedSosSessionId || (s.allSessionIds && s.allSessionIds.includes(state.selectedSosSessionId)));
  if (!session) {
    showToast("SOS Session not found.");
    return;
  }

  state.soslivechatSending = true;
  if (els.soslivechatReplyButton) els.soslivechatReplyButton.disabled = true;

  try {
    const adminName = state.currentUser ? (state.currentUser.displayName || state.currentUser.email || "Admin Responder") : "Admin Responder";
    const adminUid = state.currentUser ? state.currentUser.uid : "admin";

    const alertsToWrite = session.allAlerts && session.allAlerts.length
      ? session.allAlerts
      : [{ roomId: session.roomId, alertId: session.alertId }];

    // Use a single shared message ID across all alerts/rooms to avoid multi-room duplicates
    const sharedMsgId = push(ref(db, `rooms/${alertsToWrite[0].roomId}/sosAlerts/${alertsToWrite[0].alertId}/chat/messages`)).key;

    const updates = {};
    alertsToWrite.forEach((item) => {
      const chatMsg = {
        id: sharedMsgId,
        text: message,
        sender: "admin",
        senderType: "admin",
        senderUid: adminUid,
        senderName: adminName,
        createdAt: serverTimestamp()
      };

      updates[`rooms/${item.roomId}/sosAlerts/${item.alertId}/chat/messages/${sharedMsgId}`] = chatMsg;
      updates[`rooms/${item.roomId}/sosAlerts/${item.alertId}/chat/meta/lastMessage`] = message;
      updates[`rooms/${item.roomId}/sosAlerts/${item.alertId}/chat/meta/lastSender`] = "admin";
      updates[`rooms/${item.roomId}/sosAlerts/${item.alertId}/chat/meta/lastAdminUid`] = adminUid;
      updates[`rooms/${item.roomId}/sosAlerts/${item.alertId}/chat/meta/lastAdminName`] = adminName;
      updates[`rooms/${item.roomId}/sosAlerts/${item.alertId}/chat/meta/updatedAt`] = serverTimestamp();
    });

    await update(ref(db), updates);
    els.soslivechatReplyInput.value = "";
    showToast("Urgent message sent to victim.");
  } catch (err) {
    console.error("Error sending SOS livechat message:", err);
    showToast("Failed to send message: " + err.message);
  } finally {
    state.soslivechatSending = false;
    renderSosLivechat();
  }
}

function renderLivechat() {
  const searchedThreads = getSupportThreads().filter((thread) => matchesSearch([
    thread.uid,
    thread.name,
    thread.email,
    thread.publicId,
    thread.topic,
    thread.status,
    thread.lastMessage
  ]));
  const allThreads = getSupportThreads();
  const selectedExists = state.selectedChatUid
    && allThreads.some((thread) => thread.uid === state.selectedChatUid);

  if (!selectedExists) {
    state.selectedChatUid = "";
  }

  els.livechatCount.textContent = `${searchedThreads.length} shown`;
  els.livechatThreadList.innerHTML = searchedThreads.length ? searchedThreads.map((thread) => {
    const active = thread.uid === state.selectedChatUid;
    const avatar = avatarHtml({
      name: thread.name,
      email: thread.email,
      photoUrl: thread.photoUrl,
      photoB64: thread.photoB64
    }, thread.uid);
    return `
      <button class="livechat-thread${active ? " is-active" : ""}" data-action="select-chat" data-chat-uid="${escapeHtml(thread.uid)}" type="button">
        ${avatar}
        <span class="livechat-thread-main">
          <span class="livechat-thread-head">
            <strong>${escapeHtml(thread.name)}</strong>
            ${pill(chatStatusLabel(thread.status), chatStatusTone(thread.status))}
          </span>
          ${thread.topic ? `<span class="cell-meta">${escapeHtml(thread.topic)}</span>` : ""}
          <span>${escapeHtml(thread.lastMessage || "No messages yet")}</span>
          <span class="cell-meta">${escapeHtml(thread.email || thread.uid)} - ${escapeHtml(ageLabel(thread.updatedAt))}</span>
        </span>
        ${thread.unread ? `<span class="livechat-unread" aria-label="Unread user reply"></span>` : ""}
      </button>
    `;
  }).join("") : `<div class="livechat-empty inline"><strong>No livechat threads</strong><span>App users will appear after sending a message.</span></div>`;

  const selectedThread = allThreads.find((thread) => thread.uid === state.selectedChatUid);
  if (!selectedThread) {
    els.livechatSelectedTitle.textContent = "Select a conversation";
    els.livechatSelectedSubtitle.textContent = "App users will appear here after they send a message.";
    els.livechatMessages.innerHTML = `
      <div class="livechat-empty">
        <strong>No conversation selected</strong>
        <span>Choose a livechat thread to reply.</span>
      </div>
    `;
    if (state.livechatAttachment) clearLivechatAttachment();
    updateLivechatComposer();
    els.resolveChatButton.disabled = true;
    els.deleteChatButton.disabled = true;
    if (els.livechatCallVideoBtn) {
      els.livechatCallVideoBtn.disabled = true;
      els.livechatCallVideoBtn.onclick = null;
    }
    if (els.livechatCallVoiceBtn) {
      els.livechatCallVoiceBtn.disabled = true;
      els.livechatCallVoiceBtn.onclick = null;
    }
    return;
  }

  els.livechatSelectedTitle.textContent = selectedThread.name;
  els.livechatSelectedSubtitle.textContent = [
    selectedThread.topic ? `Topic: ${selectedThread.topic}` : "",
    selectedThread.email || selectedThread.uid,
    `${selectedThread.messageCount} messages`,
    chatStatusLabel(selectedThread.status)
  ].filter(Boolean).join(" - ");
  const isResolved = text(selectedThread.status).toLowerCase() === "resolved";
  els.resolveChatButton.disabled = isResolved;
  els.deleteChatButton.disabled = !isResolved;
  if (els.livechatCallVideoBtn) {
    els.livechatCallVideoBtn.disabled = false;
    els.livechatCallVideoBtn.onclick = () => {
      startAdminCall(selectedThread.uid, selectedThread.name, "video");
    };
  }
  if (els.livechatCallVoiceBtn) {
    els.livechatCallVoiceBtn.disabled = false;
    els.livechatCallVoiceBtn.onclick = () => {
      startAdminCall(selectedThread.uid, selectedThread.name, "voice");
    };
  }
  updateLivechatComposer();

  els.livechatMessages.innerHTML = selectedThread.messages.length ? selectedThread.messages.map((message) => {
    const fromAdmin = message.sender === "admin";
    const fromSystem = message.sender === "system";
    const fromAi = message.sender === "ai" || message.sender === "assistant";
    let sender = message.senderName || selectedThread.name;
    let badgeHtml = "";

    if (fromAdmin) {
      sender = (message.source === "admin-panel" || !message.senderName || message.senderName === "Admin") ? LIVECHAT_ADMIN_LABEL : message.senderName;
    } else if (fromSystem) {
      sender = "ResQTap System Notice";
      badgeHtml = ` <span class="badge-system-pill">System</span>`;
    } else if (fromAi) {
      sender = (message.senderName && message.senderName !== "ResQTap" && message.senderName !== "ResQTap Assistant" && message.senderName !== "ResQTap Support Chat Bot" && message.senderName !== "Admin") ? message.senderName : "ResQTap Support";
      badgeHtml = ` <span class="badge-ai-pill">AI</span>`;
    }

    const attachment = asRecord(message.attachment);
    const rawAttachUrl = text(attachment.downloadUrl).trim();
    const isSafeAttach = rawAttachUrl.startsWith("https://") || rawAttachUrl.startsWith("http://") || rawAttachUrl.startsWith("data:image/") || rawAttachUrl.startsWith("/");
    const safeAttachUrl = isSafeAttach ? rawAttachUrl : "#";
    const attachmentHtml = rawAttachUrl ? `
      ${rawAttachUrl.startsWith("data:image/")
        ? `<img class="livechat-image-preview" src="${escapeHtml(safeAttachUrl)}" alt="${escapeHtml(attachment.name || "Attachment")}">`
        : `<a class="livechat-attachment" href="${escapeHtml(safeAttachUrl)}" target="_blank" rel="noopener noreferrer">
            ${icon("paperclip")}
            <span>${escapeHtml(attachment.name || "Attachment")}</span>
          </a>`}
    ` : "";

    const msgClass = fromAdmin ? "from-admin" : (fromSystem ? "from-system" : (fromAi ? "from-ai" : "from-user"));

    return `
      <div class="livechat-message ${msgClass}">
        <div class="livechat-bubble">
          <span class="livechat-sender">${escapeHtml(sender)}${badgeHtml}</span>
          ${message.text ? `<p>${escapeHtml(message.text)}</p>` : ""}
          ${attachmentHtml}
          <time>${escapeHtml(message.createdAt ? formatDate(message.createdAt) : "-")}</time>
        </div>
      </div>
    `;
  }).join("") : `
    <div class="livechat-empty">
      <strong>No messages yet</strong>
      <span>This thread is open but still empty.</span>
    </div>
  `;
  els.livechatMessages.scrollTop = els.livechatMessages.scrollHeight;
}

function aiChatStatusLabel(status) {
  const normalized = text(status).trim().toLowerCase();
  if (normalized === "expired") return "Expired";
  if (normalized === "answered") return "Answered";
  return "Active";
}

function aiChatStatusTone(status) {
  const normalized = text(status).trim().toLowerCase();
  if (normalized === "expired") return "warn";
  if (normalized === "answered") return "good";
  return "";
}

function renderAiChat() {
  if (!els.aiChatCount || !els.aiChatThreadList) return;
  const allThreads = getAiThreads();
  const searchedThreads = allThreads.filter((thread) => matchesSearch([
    thread.uid,
    thread.name,
    thread.email,
    thread.publicId,
    thread.status,
    thread.lastMessage
  ]));
  const selectedExists = state.selectedAiChatUid
    && allThreads.some((thread) => thread.uid === state.selectedAiChatUid);

  if (!selectedExists) {
    state.selectedAiChatUid = "";
  }

  els.aiChatCount.textContent = `${searchedThreads.length} shown`;
  els.aiChatThreadList.innerHTML = searchedThreads.length ? searchedThreads.map((thread) => {
    const active = thread.uid === state.selectedAiChatUid;
    const avatar = avatarHtml({
      name: thread.name,
      email: thread.email,
      photoUrl: thread.photoUrl,
      photoB64: thread.photoB64
    }, thread.uid);
    const expiryText = thread.expiresAt ? `Expires in ${futureAgeLabel(thread.expiresAt)}` : "No expiry";
    return `
      <button class="livechat-thread${active ? " is-active" : ""}" data-action="select-ai-chat" data-ai-chat-uid="${escapeHtml(thread.uid)}" type="button">
        ${avatar}
        <span class="livechat-thread-main">
          <span class="livechat-thread-head">
            <strong>${escapeHtml(thread.name)}</strong>
            ${pill(aiChatStatusLabel(thread.status), aiChatStatusTone(thread.status))}
          </span>
          <span>${escapeHtml(thread.lastMessage || "No messages yet")}</span>
          <span class="cell-meta">${escapeHtml(thread.email || thread.uid)} - ${escapeHtml(expiryText)}</span>
        </span>
        ${thread.unread ? `<span class="livechat-unread" aria-label="Waiting for AI reply"></span>` : ""}
      </button>
    `;
  }).join("") : `<div class="livechat-empty inline"><strong>No AI chat threads</strong><span>App users will appear after asking the assistant.</span></div>`;

  const selectedThread = allThreads.find((thread) => thread.uid === state.selectedAiChatUid);
  if (!selectedThread) {
    els.aiChatSelectedTitle.textContent = "Select a conversation";
    els.aiChatSelectedSubtitle.textContent = "App users will appear here after they ask the assistant.";
    els.aiChatMessages.innerHTML = `
      <div class="livechat-empty">
        <strong>No conversation selected</strong>
        <span>Choose an AI chat thread to monitor.</span>
      </div>
    `;
    els.deleteAiChatButton.disabled = true;
    return;
  }

  els.aiChatSelectedTitle.textContent = selectedThread.name;
  els.aiChatSelectedSubtitle.textContent = [
    selectedThread.email || selectedThread.uid,
    `${selectedThread.messageCount} messages`,
    aiChatStatusLabel(selectedThread.status),
    selectedThread.expiresAt ? `Expires ${formatDate(selectedThread.expiresAt)}` : ""
  ].filter(Boolean).join(" - ");
  els.deleteAiChatButton.disabled = false;

  els.aiChatMessages.innerHTML = selectedThread.messages.length ? selectedThread.messages.map((message) => {
    const fromAssistant = message.role === "assistant";
    const sender = fromAssistant ? "ResQTap AI" : (message.senderName || selectedThread.name);
    return `
      <div class="livechat-message ${fromAssistant ? "from-admin" : "from-user"}">
        <div class="livechat-bubble">
          <span class="livechat-sender">${escapeHtml(sender)}</span>
          <p>${escapeHtml(message.text)}</p>
          <time>${escapeHtml(message.createdAt ? formatDate(message.createdAt) : "-")}${message.source ? ` - ${escapeHtml(message.source)}` : ""}</time>
        </div>
      </div>
    `;
  }).join("") : `
    <div class="livechat-empty">
      <strong>No messages yet</strong>
      <span>This AI chat is open but still empty.</span>
    </div>
  `;
  els.aiChatMessages.scrollTop = els.aiChatMessages.scrollHeight;
}

function renderAdmins() {
  const admins = entries(state.admins)
    .map(([uid, value]) => ({
      uid,
      value,
      name: isRecord(value) ? text(value.name).trim() : "",
      active: isAdminValue(value),
      grantedAt: isRecord(value) ? millis(value.grantedAt) : 0,
      grantedBy: isRecord(value) ? text(value.grantedBy) : ""
    }))
    .sort((a, b) => Number(b.active) - Number(a.active) || a.uid.localeCompare(b.uid));

  els.adminsCount.textContent = `${admins.length} records`;
  els.adminsList.innerHTML = admins.length ? admins.map((admin) => `
    <article class="list-item">
      <div>
        <strong>${escapeHtml(admin.name || userName(admin.uid))}</strong>
        <span>${escapeHtml(admin.uid)}</span>
        <span>${admin.grantedAt ? `Granted ${escapeHtml(formatDate(admin.grantedAt))}` : "Manual record"}</span>
      </div>
      <div class="row-actions">
        ${pill(admin.active ? "Active" : "Inactive", admin.active ? "good" : "warn")}
        <button class="small-button danger" data-action="revoke-admin" data-uid="${escapeHtml(admin.uid)}" type="button">${icon("shield-x")}<span>Revoke</span></button>
      </div>
    </article>
  `).join("") : `<div class="list-item"><span>No admin records loaded.</span></div>`;
}

function renderHighlights() {
  const hlGrid = document.getElementById("highlightsGrid");
  const hlCount = document.getElementById("highlightsCount");
  if (!hlGrid) return;

  const items = entries(state.highlights)
    .map(([id, value]) => ({
      id,
      ...asRecord(value)
    }))
    .sort((a, b) => (Number(a.order) || 99) - (Number(b.order) || 99));

  if (hlCount) hlCount.textContent = `${items.length} banner${items.length === 1 ? "" : "s"}`;

  if (!items.length) {
    hlGrid.innerHTML = `
      <div class="empty-highlights-state">
        <i data-lucide="sparkles" style="width: 36px; height: 36px; color: #E60067; margin-bottom: 10px;"></i>
        <strong style="display: block; font-size: 16px; font-weight: 800; color: #1C1E21; margin-bottom: 6px;">No Highlights Found</strong>
        <p class="muted" style="margin-bottom: 18px; font-size: 13px; max-width: 480px; margin-left: auto; margin-right: auto;">The carousel database is currently empty. You can create a new banner above or load the default official emergency banners.</p>
        <button class="primary-button icon-button" data-action="seed-default-highlights" type="button" style="margin: 0 auto;">
          <i data-lucide="upload-cloud" aria-hidden="true"></i>
          <span>Load 4 Official Banners to Firebase</span>
        </button>
      </div>
    `;
    if (window.lucide) window.lucide.createIcons();
    return;
  }

  hlGrid.innerHTML = items.map((item) => {
    const isActive = item.active !== false && item.active !== "false";
    const imgSrc = item.imageUrl || "assets/resqtap_launcher.png";
    return `
      <div class="highlight-item-card${isActive ? "" : " is-hidden"}">
        <div class="highlight-card-media">
          <div class="hl-media-backdrop" style="background-image: url('${escapeHtml(imgSrc)}');"></div>
          <img src="${escapeHtml(imgSrc)}" alt="${escapeHtml(item.title || "Banner")}">
          <div class="highlight-badges-row">
            <span class="hl-badge-order">#${escapeHtml(String(item.order || 1))}</span>
          </div>
        </div>
        <div class="highlight-card-body">
          <strong class="highlight-card-title">${escapeHtml(item.title || "Untitled Banner")}</strong>
          ${item.actionUrl ? `
            <a class="highlight-card-link" href="${escapeHtml(item.actionUrl)}" target="_blank" rel="noreferrer">
              <i data-lucide="external-link" style="width: 12px; height: 12px; flex-shrink: 0;"></i>
              <span>${escapeHtml(item.actionUrl)}</span>
            </a>
          ` : '<span class="highlight-card-nolink">No web link attached</span>'}

          <div class="highlight-card-actions">
            <button class="action-pill-btn info" data-action="edit-highlight" data-highlight-id="${escapeHtml(item.id)}" type="button">
              ${icon("edit-3")}
              <span>Edit</span>
            </button>
            <button class="action-pill-btn ${isActive ? "muted" : "success"}" data-action="toggle-highlight" data-highlight-id="${escapeHtml(item.id)}" data-active="${isActive ? 'true' : 'false'}" type="button">
              ${icon(isActive ? "eye-off" : "eye")}
              <span>${isActive ? "Hide" : "Show"}</span>
            </button>
            <button class="action-pill-btn danger" data-action="delete-highlight" data-highlight-id="${escapeHtml(item.id)}" type="button">
              ${icon("trash-2")}
              <span>Delete</span>
            </button>
          </div>
        </div>
      </div>
    `;
  }).join("");

  if (window.lucide) window.lucide.createIcons();
}

function renderAdminProfile() {
  if (!state.currentUser) return;
  const uid = state.currentUser.uid;
  const adminData = asRecord(state.admins?.[uid]);

  // Pastikan profil akaun admin ini dibersihkan daripada senarai pengguna biasa (users)
  if (state.users && state.users[uid]) {
    delete state.users[uid];
    remove(ref(db, `users/${uid}`)).catch(() => {});
  }

  const name = text(adminData.name || state.currentUser.displayName || "Administrator").trim();
  const email = text(adminData.email || state.currentUser.email || "").trim();
  const phone = text(adminData.phone || "").trim();
  const role = text(adminData.role || "Administrator").trim();
  const photoUrl = text(adminData.photoUrl || adminData.photo_url || state.currentUser.photoURL || "").trim();

  const defaultAvatar = "/assets/default_avatar.svg";
  const effectiveAvatar = photoUrl || defaultAvatar;

  if (els.profileDisplayHeaderName) {
    els.profileDisplayHeaderName.textContent = name;
  }
  if (els.profileDisplayHeaderEmail) {
    els.profileDisplayHeaderEmail.textContent = email || "No email";
  }

  if (els.profileAvatarImg) {
    els.profileAvatarImg.src = effectiveAvatar;
    els.profileAvatarImg.classList.remove("hidden");
  }
  if (els.profileAvatarInitials) {
    els.profileAvatarInitials.classList.add("hidden");
  }

  if (els.adminRemovePhotoBtn) {
    els.adminRemovePhotoBtn.classList.toggle("hidden", !photoUrl);
  }
  if (els.adminUploadBtnLabel) {
    els.adminUploadBtnLabel.textContent = photoUrl ? "Change Picture" : "Upload Picture";
  }

  if (els.adminProfileName && document.activeElement !== els.adminProfileName) {
    els.adminProfileName.value = name;
  }
  if (els.adminProfileEmail) {
    els.adminProfileEmail.value = email;
  }
  if (els.adminProfilePhone && document.activeElement !== els.adminProfilePhone) {
    els.adminProfilePhone.value = phone;
  }
  if (els.adminProfileRole) {
    els.adminProfileRole.value = role;
  }
  if (els.adminProfileUid) {
    els.adminProfileUid.value = uid;
  }

  // Update session email text in profile view
  if (els.profileSessionEmailText) {
    els.profileSessionEmailText.textContent = email || "admin@resqtap.com";
  }

  let displayRole = role || "Administrator";
  if (/^super/i.test(role)) displayRole = "Super Admin";
  else if (/^admin/i.test(role)) displayRole = "Administrator";

  if (els.profileRoleBadge) {
    els.profileRoleBadge.textContent = displayRole;
  }

  // Update sidebar profile card (Avatar & Username only)
  if (els.sidebarProfileName) {
    els.sidebarProfileName.textContent = name;
  }
  if (els.sidebarProfileRole) {
    els.sidebarProfileRole.textContent = "";
    els.sidebarProfileRole.style.display = "none";
  }
  if (els.sidebarProfileNavBtn) {
    els.sidebarProfileNavBtn.setAttribute("data-label", name);
  }
  if (els.sidebarAvatarMiniImg) {
    els.sidebarAvatarMiniImg.src = effectiveAvatar;
    els.sidebarAvatarMiniImg.classList.remove("hidden");
  }
  if (els.sidebarAvatarMiniInitials) {
    els.sidebarAvatarMiniInitials.classList.add("hidden");
  }

  // Update Hero Live Operational Stats
  if (els.profileHeroAlertsCount) {
    const alertCount = Object.keys(state.alerts || {}).length;
    els.profileHeroAlertsCount.textContent = alertCount.toLocaleString();
  }
  if (els.profileHeroRespondersCount) {
    const respCount = Object.keys(state.responders || {}).length;
    els.profileHeroRespondersCount.textContent = respCount.toLocaleString();
  }
  if (els.profileHeroUsersCount) {
    const userCount = Object.keys(state.users || {}).length;
    els.profileHeroUsersCount.textContent = userCount.toLocaleString();
  }

  refreshIcons();
}



function detailPanelMaxWidth() {
  const shellWidth = els.appShell.getBoundingClientRect().width || window.innerWidth;
  const sidebarWidth = window.matchMedia("(max-width: 1320px)").matches ? 208 : 216;
  const availableWidth = shellWidth - sidebarWidth - DETAIL_MAIN_MIN_WIDTH;
  return Math.max(DETAIL_PANEL_MIN_WIDTH, Math.min(DETAIL_PANEL_MAX_WIDTH, availableWidth));
}

function setDetailWidth(width) {
  const nextWidth = Math.round(Math.min(Math.max(width, DETAIL_PANEL_MIN_WIDTH), detailPanelMaxWidth()));
  state.detailWidth = nextWidth;
  els.appShell.style.setProperty("--detail-width", `${nextWidth}px`);
}

function detailResizeHandle() {
  return `
    <div class="detail-resize-handle" role="separator" aria-orientation="vertical" aria-label="Resize detail panel" tabindex="0"></div>
  `;
}

function detailShell(title, subtitle, body, avatar = "") {
  setDetailWidth(state.detailWidth);
  els.appShell.classList.add("detail-open");
  els.detailPanel.classList.add("has-content");
  els.detailPanel.classList.remove("hidden");
  els.detailPanel.innerHTML = `
    ${detailResizeHandle()}
    <div class="detail-content">
      <div class="detail-header">
        <div class="detail-title">
          ${avatar}
          <div>
            <h3>${escapeHtml(title)}</h3>
            <span class="muted">${escapeHtml(subtitle)}</span>
          </div>
        </div>
        <button class="secondary-button icon-button" data-action="clear-detail" type="button">${icon("x")}<span>Close</span></button>
      </div>
      ${body}
    </div>
  `;
  refreshIcons();
  setTimeout(refreshIcons, 20);
}

function kv(label, value) {
  return `<div class="kv"><span>${escapeHtml(label)}</span><span>${escapeHtml(value || "-")}</span></div>`;
}

function renderDetail() {
  if (!state.selected) {
    els.appShell.classList.remove("detail-open");
    els.detailPanel.classList.remove("has-content");
    els.detailPanel.classList.add("hidden");
    els.detailPanel.innerHTML = `
      <div class="detail-empty">
        <strong>No item selected</strong>
        <span>Select a row to inspect details.</span>
      </div>
    `;
    return;
  }

  if (state.selected.type === "user") {
    const activeSos = getActiveSosForUser(state.selected.id);
    if (activeSos && !state.selected.forceProfile) {
      renderSosDetail(activeSos.roomId, activeSos.key || activeSos.id);
      return;
    }
    renderUserDetail(state.selected.id);
  }
  if (state.selected.type === "room") renderRoomDetail(state.selected.id);
  if (state.selected.type === "sos") renderSosDetail(state.selected.roomId, state.selected.alertId);
  if (state.selected.type === "report") renderReportDetail(state.selected.id);
}

function renderUserDetail(uid) {
  const rawUser = asRecord(state.users?.[uid]);
  const user = Object.keys(rawUser).length ? rawUser : { uid, name: uid, email: "" };
  const myRooms = userRoomsFor(uid);
  const roomEntries = entries(myRooms);
  const contactEntries = contactsFor(user);
  const locations = getRooms()
    .filter((room) => myRooms[room.code])
    .map((room) => ({ room, member: room.members.find((item) => item.uid === uid) }))
    .filter((item) => item.member && Number.isFinite(item.member.lat) && Number.isFinite(item.member.lng) && item.member.lat !== 0 && item.member.lng !== 0);

  const contactHtml = contactEntries.length ? contactEntries.map(([, contact]) => {
    const c = asRecord(contact);
    return `
      <div class="contact-row">
        <strong>${escapeHtml(c.name || "-")}</strong>
        <span class="cell-meta">${escapeHtml(c.relationship || "-")} - ${escapeHtml(c.phone || "-")}</span>
      </div>
    `;
  }).join("") : `<span class="muted">No emergency contacts.</span>`;

  const roomsHtml = roomEntries.length ? roomEntries.map(([roomCode, meta]) => {
    const m = asRecord(meta);
    return `
      <div class="member-row">
        <div class="member-row-head">
          <strong>${escapeHtml(m.label || roomCode)}</strong>
          <button class="small-button" data-action="view-room" data-room="${escapeHtml(roomCode)}" type="button">${icon("external-link")}<span>Open</span></button>
        </div>
        <span class="cell-meta">${escapeHtml(roomCode)} - ${escapeHtml(m.role || "member")}</span>
      </div>
    `;
  }).join("") : `<span class="muted">No rooms.</span>`;

  const locationsHtml = locations.length ? locations.map(({ room, member }) => {
    const hasMap = Number.isFinite(member.lat) && Number.isFinite(member.lng) && member.lat !== 0 && member.lng !== 0;
    return `
      <div class="member-row">
        <div class="member-row-head">
          <strong>${escapeHtml(room.code)}</strong>
        ${hasMap ? `<a class="small-button" href="https://www.google.com/maps?q=${member.lat},${member.lng}" target="_blank" rel="noreferrer">${icon("map-pin")}<span>Map</span></a>` : ""}
        </div>
        <span class="cell-meta">${escapeHtml(ageLabel(member.updatedAt))} - Battery ${member.batteryPct === null ? "-" : `${member.batteryPct}%`}</span>
      </div>
    `;
  }).join("") : `<span class="muted">No online room location.</span>`;

  const activeSos = getActiveSosForUser(uid);
  const sosBanner = activeSos ? `
    <div style="background: rgba(239, 68, 68, 0.12); border: 1px solid rgba(239, 68, 68, 0.4); border-radius: 8px; padding: 10px 14px; margin-bottom: 14px; display: flex; align-items: center; justify-content: space-between; gap: 8px;">
      <div style="display: flex; align-items: center; gap: 8px;">
        ${icon("siren")}
        <div>
          <strong style="color: #ef4444; font-size: 13px; display: block;">Active SOS Triggered</strong>
          <span style="font-size: 12px; color: var(--text-dim, #888);">Room: ${escapeHtml(activeSos.roomId || "-")}</span>
        </div>
      </div>
      <button class="small-button danger" data-action="view-sos" data-room="${escapeHtml(activeSos.roomId)}" data-alert="${escapeHtml(activeSos.key || activeSos.id)}" type="button">
        ${icon("external-link")}<span>View Alert</span>
      </button>
    </div>
  ` : "";

  const body = `
    ${sosBanner}
    <section class="detail-section">
      ${kv("UID", uid)}
      ${kv("Public ID", user.publicId)}
      ${kv("Email", user.email)}
      ${kv("Phone", user.phoneNumber || user.phone)}
      ${kv("Data Source", user.source === "supabase" ? "Supabase Cloud" : "Firebase Realtime")}
      ${kv("Last seen", ageLabel(getUserLastSeen(uid, user)))}
    </section>
    <section class="detail-section">
      <div style="display: flex; align-items: center; justify-content: space-between; margin-bottom: 8px;">
        <h3 style="margin: 0; font-size: 0.95rem;">Medical Card</h3>
        ${state.medicalCards && state.medicalCards[uid] ? '<span class="status-pill active" style="font-size: 10px;">Supabase Verified</span>' : '<span class="status-pill muted" style="font-size: 10px;">Standard</span>'}
      </div>
      ${kv("Blood type", (state.medicalCards && state.medicalCards[uid] && state.medicalCards[uid].blood_type) || user.bloodType)}
      ${kv("Allergies", (state.medicalCards && state.medicalCards[uid] && state.medicalCards[uid].allergies) || user.allergies)}
      ${kv("Conditions", (state.medicalCards && state.medicalCards[uid] && state.medicalCards[uid].medical_conditions) || user.existingConditions)}
      ${kv("Medications", (state.medicalCards && state.medicalCards[uid] && state.medicalCards[uid].medications) || user.medications || "-")}
      ${kv("Medical Notes", (state.medicalCards && state.medicalCards[uid] && state.medicalCards[uid].notes) || "-")}
      ${kv("Date of birth", user.dateOfBirth || user.dob)}
      ${kv("Address", user.address)}
    </section>
    <section class="detail-section">
      <h3>Emergency Contacts</h3>
      ${contactHtml}
    </section>
    <section class="detail-section">
      <h3>Channels</h3>
      ${roomsHtml}
    </section>
    <section class="detail-section">
      <h3>Online Locations</h3>
      ${locationsHtml}
    </section>
  `;
  detailShell(user.name || user.email || "User", uid, body, avatarHtml(user, uid));
}

function renderRoomDetail(roomCode) {
  const room = asRecord(state.rooms?.[roomCode]);
  if (!Object.keys(room).length) {
    state.selected = null;
    renderDetail();
    return;
  }

  const roomModel = getRooms().find((item) => item.code === roomCode);
  const members = roomModel ? roomModel.members : [];
  const alerts = getSosAlerts().filter((alert) => alert.roomId === roomCode && alert.source === "room");
  const memberHtml = members.length ? members.map((member) => {
    const hasMap = Number.isFinite(member.lat) && Number.isFinite(member.lng) && member.lat !== 0 && member.lng !== 0;
    const battery = member.batteryPct === null ? "-" : `${member.batteryPct}%`;
    const batteryTone = member.batteryPct !== null && member.batteryPct <= LOW_BATTERY ? "warn" : "";
    return `
      <div class="member-row">
        <div class="member-row-head">
          <strong>${escapeHtml(member.data.name || userName(member.uid))}</strong>
          <div class="row-actions">
            ${pill(Date.now() - member.updatedAt <= ONLINE_MS ? "Online" : ageLabel(member.updatedAt), Date.now() - member.updatedAt <= ONLINE_MS ? "good" : "")}
            ${hasMap ? `<a class="small-button" href="https://www.google.com/maps?q=${member.lat},${member.lng}" target="_blank" rel="noreferrer">${icon("map-pin")}<span>Map</span></a>` : ""}
            <button class="small-button danger" data-action="remove-member" data-room="${escapeHtml(roomCode)}" data-uid="${escapeHtml(member.uid)}" type="button">${icon("user-minus")}<span>Remove</span></button>
          </div>
        </div>
        <span class="cell-meta">${escapeHtml(member.uid)} - Battery ${pill(battery, batteryTone)}</span>
      </div>
    `;
  }).join("") : `<span class="muted">No members in this channel.</span>`;

  const alertHtml = alerts.length ? alerts.slice(0, 8).map((alert) => {
    const isResolved = Boolean(alert.data && (alert.data.resolvedAt || alert.data.status === "resolved" || Number(alert.data.progressStep) >= 4));
    const label = alert.active ? (alert.stale ? "Stale" : "Active") : (isResolved ? "Resolved" : "Cancelled");
    const tone = alert.active ? (alert.stale ? "warn" : "alert") : (isResolved ? "good" : "neutral");
    return `
      <div class="member-row">
        <div class="member-row-head">
          <strong>${escapeHtml(alert.senderName || alert.senderUid || "Unknown")}</strong>
          <button class="small-button" data-action="view-sos" data-room="${escapeHtml(roomCode)}" data-alert="${escapeHtml(alert.key)}" type="button">${icon("external-link")}<span>Open</span></button>
        </div>
        <span class="cell-meta">${pill(label, tone)} ${escapeHtml(formatDate(alert.createdAt))}</span>
      </div>
    `;
  }).join("") : `<span class="muted">No SOS alerts in this channel.</span>`;

  const body = `
    <section class="detail-section">
      ${kv("Code", roomCode)}
      ${kv("Name", room.name || roomCode)}
      ${kv("Creator", `${userName(room.creatorUid)} (${room.creatorUid || "-"})`)}
      ${kv("Created", formatDate(room.createdAt))}
      ${kv("Updated", ageLabel(roomModel ? roomModel.updatedAt : room.updatedAt))}
      ${kv("Members", String(members.length))}
    </section>
    <section class="detail-section">
      <h3>Members</h3>
      ${memberHtml}
    </section>
    <section class="detail-section">
      <h3>SOS Alerts</h3>
      ${alertHtml}
    </section>
    <section class="detail-section">
      <button class="secondary-button danger icon-button" data-action="delete-room" data-room="${escapeHtml(roomCode)}" type="button">${icon("trash-2")}<span>Delete channel</span></button>
    </section>
  `;
  detailShell(room.name || roomCode, `Channel ${roomCode}`, body);
}

function renderSosDetail(roomId, alertId) {
  const alert = getSosAlerts().find((item) => item.roomId === roomId && (item.key === alertId || item.id === alertId))
    || getSosAlerts().find((item) => item.key === alertId || item.id === alertId);
  if (!alert) {
    state.selected = null;
    renderDetail();
    return;
  }

  const senderUser = asRecord(state.users?.[alert.senderUid]);
  const senderPhone = senderUser.phoneNumber || senderUser.phone || "";
  const isCancelledState = isCancelled(alert.data) || !alert.active;
  const isCurrentlyServed = !isCancelledState && Boolean(alert.data && (alert.data.served || alert.data.servedBy));
  const isResolved = Boolean(alert.data && (alert.data.resolvedAt || alert.data.status === "resolved" || Number(alert.data.progressStep) >= 4));
  const isCaseActive = alert.active && !isCancelledState && !isResolved;
  const label = isCaseActive ? (alert.stale ? "Stale active" : "Active") : (isResolved ? "Resolved" : "Cancelled");
  const canCancel = alert.source === "room" && isCaseActive;
  const body = `
    <section class="detail-section">
      ${kv("Alert ID", alert.id)}
      ${kv("Room", alert.roomId === "DIRECT" ? "Direct SOS (No Room)" : alert.roomId)}
      ${kv("Source", alert.source)}
      ${kv("Status", label)}
      ${kv("Created", formatDate(alert.createdAt))}
      ${kv("Cancelled", isCaseActive ? "-" : (alert.cancelledAt ? formatDate(alert.cancelledAt) : "-"))}
      ${kv("Sender UID", alert.senderUid)}
      ${kv("Sender name", alert.senderName)}
      ${senderPhone ? kv("Phone", senderPhone) : ""}
    </section>
    <section class="detail-section">
      ${isCaseActive ? `<button class="primary-button icon-button" data-action="manage-sos-case" data-room="${escapeHtml(alert.roomId)}" data-alert="${escapeHtml(alert.key)}" data-uid="${escapeHtml(alert.senderUid)}" data-name="${escapeHtml(alert.senderName || "Sender")}" type="button">${icon("shield-alert")}<span>${isCurrentlyServed ? "Update Case" : "Reserve Case"}</span></button>` : ""}
      ${alert.roomId !== "DIRECT" ? `<button class="secondary-button icon-button" data-action="view-room" data-room="${escapeHtml(alert.roomId)}" type="button">${icon("external-link")}<span>Open room</span></button>` : ""}
      ${alert.senderUid ? `<button class="secondary-button icon-button" data-action="view-user" data-uid="${escapeHtml(alert.senderUid)}" data-force-profile="true" type="button">${icon("user-round")}<span>Profile View</span></button>` : ""}
      ${canCancel ? `<button class="secondary-button danger icon-button" data-action="cancel-sos" data-room="${escapeHtml(alert.roomId)}" data-alert="${escapeHtml(alert.key)}" type="button">${icon("circle-x")}<span>Cancel SOS</span></button>` : ""}
    </section>
  `;
  const senderAvatar = alert.senderUid ? avatarHtml(senderUser, alert.senderUid) : "";
  const roomTitle = alert.roomId === "DIRECT" ? "Direct SOS (No Room)" : (alert.roomId || "-");
  detailShell(alert.senderName || alert.senderUid || "SOS Alert", `${roomTitle} - ${label}`, body, senderAvatar);
}

function renderReportDetail(reportKey) {
  const report = getIncidentReports().find((item) => item.key === reportKey);
  if (!report) {
    state.selected = null;
    renderDetail();
    return;
  }

  const status = reportStatusMeta(report.status);
  const hasMap = Number.isFinite(report.latitude) && Number.isFinite(report.longitude);
  const mapUrl = hasMap ? `https://www.google.com/maps?q=${report.latitude},${report.longitude}` : "";
  const attachmentsHtml = report.attachments.length ? report.attachments.map((attachment, index) => {
    const name = text(attachment.name || "Attachment");
    const mime = text(attachment.mimeType).toLowerCase();
    const url = text(attachment.downloadUrl);
    const isImage = mime.startsWith("image/") || url.startsWith("data:image/");
    if (!url) {
      return `
        <div class="report-attachment-card">
          ${icon("paperclip")}
          <span>${escapeHtml(name)}</span>
        </div>
      `;
    }
    return `
      <button class="report-attachment-card report-attachment-button" data-action="view-report-attachment" data-report="${escapeHtml(report.key)}" data-attachment-index="${index}" type="button">
        ${isImage ? `<img class="report-attachment-preview" src="${escapeHtml(url)}" alt="">` : icon("paperclip")}
        <span>${escapeHtml(name)}</span>
        <em>View</em>
      </button>
    `;
  }).join("") : `<span class="muted">No attachments.</span>`;

  const body = `
    <section class="detail-section">
      ${kv("Report ID", report.id)}
      ${kv("Status", status.label)}
      ${kv("Category", report.categoryLabel)}
      ${kv("Created", formatDate(report.createdAt))}
      ${kv("Updated", report.updatedAt ? formatDate(report.updatedAt) : "-")}
      ${kv("Source", report.data.source || "android_app")}
    </section>
    <section class="detail-section">
      <h3>Reporter</h3>
      ${kv("Name", report.senderName)}
      ${kv("Email", report.senderEmail)}
      ${kv("UID", report.senderUid)}
      ${kv("Public ID", report.publicId)}
      ${report.senderUid ? `<button class="secondary-button icon-button" data-action="view-user" data-uid="${escapeHtml(report.senderUid)}" data-force-profile="true" type="button">${icon("user-round")}<span>Open reporter</span></button>` : ""}
      ${report.senderUid ? `<button class="secondary-button icon-button" data-action="call-reporter" data-uid="${escapeHtml(report.senderUid)}" data-name="${escapeHtml(report.senderName || report.senderEmail || "Reporter")}" type="button">${icon("phone-call")}<span>Call Reporter</span></button>` : ""}
    </section>
    <section class="detail-section">
      <h3>Location</h3>
      ${kv("Address", report.address)}
      ${kv("Coordinates", hasMap ? `${report.latitude}, ${report.longitude}` : "-")}
      ${mapUrl ? `<a class="secondary-button icon-button" href="${escapeHtml(mapUrl)}" target="_blank" rel="noreferrer">${icon("map-pin")}<span>Open map</span></a>` : ""}
    </section>
    <section class="detail-section">
      <h3>Details</h3>
      <p class="detail-text">${escapeHtml(report.details || "No additional details.")}</p>
    </section>
    <section class="detail-section">
      <h3>Attachments</h3>
      <div class="report-attachment-grid">${attachmentsHtml}</div>
    </section>
    <section class="detail-section">
      <h3>Status Actions</h3>
      <div class="row-actions">
        ${REPORT_STATUSES.map((option) => `
          <button class="small-button${option.id === report.status ? " is-current" : ""}" data-action="set-report-status" data-report="${escapeHtml(report.key)}" data-status="${escapeHtml(option.id)}" type="button">
            ${icon(option.id === "resolved" ? "check-circle-2" : option.id === "rejected" ? "circle-x" : option.id === "reviewing" ? "clock" : "sparkles")}
            <span>${escapeHtml(option.label)}</span>
          </button>
        `).join("")}
        <button class="small-button danger" data-action="delete-report" data-report="${escapeHtml(report.key)}" type="button">${icon("trash-2")}<span>Delete</span></button>
      </div>
    </section>
  `;
  detailShell(report.categoryLabel || "Incident Report", `${status.label} - ${formatDate(report.createdAt)}`, body, avatarHtml(report.data, report.senderUid));
}

function attachmentViewerEls() {
  return {
    modal: document.getElementById("attachmentViewerModal"),
    title: document.getElementById("attachmentViewerTitle"),
    meta: document.getElementById("attachmentViewerMeta"),
    body: document.getElementById("attachmentViewerBody"),
    openLink: document.getElementById("attachmentViewerOpen")
  };
}

function closeAttachmentViewer() {
  const viewer = attachmentViewerEls();
  if (!viewer.modal || !viewer.body) return;
  viewer.modal.classList.add("hidden");
  viewer.body.innerHTML = "";
  if (viewer.openLink) viewer.openLink.removeAttribute("href");
}

function viewReportAttachment(reportKey, attachmentIndex) {
  const report = getIncidentReports().find((item) => item.key === reportKey);
  const attachment = report && report.attachments[Number(attachmentIndex)];
  const url = text(attachment && attachment.downloadUrl);
  if (!attachment || !url) {
    showToast("Attachment is not available.");
    return;
  }

  const name = text(attachment.name || "Attachment");
  const mime = text(attachment.mimeType).toLowerCase();
  const viewer = attachmentViewerEls();
  if (!viewer.modal || !viewer.body) {
    window.open(url, "_blank", "noopener,noreferrer");
    return;
  }

  viewer.title.textContent = name;
  viewer.meta.textContent = mime || "attachment";
  viewer.body.innerHTML = "";
  if (viewer.openLink) {
    viewer.openLink.href = url;
    viewer.openLink.download = name;
  }

  const isImage = mime.startsWith("image/") || url.startsWith("data:image/");
  const isVideo = mime.startsWith("video/");
  const isAudio = mime.startsWith("audio/");
  const isPdf = mime === "application/pdf" || name.toLowerCase().endsWith(".pdf");

  if (isImage) {
    const image = document.createElement("img");
    image.className = "attachment-viewer-image";
    image.alt = name;
    image.src = url;
    viewer.body.appendChild(image);
  } else if (isVideo) {
    const video = document.createElement("video");
    video.className = "attachment-viewer-media";
    video.controls = true;
    video.src = url;
    viewer.body.appendChild(video);
  } else if (isAudio) {
    const audio = document.createElement("audio");
    audio.className = "attachment-viewer-audio";
    audio.controls = true;
    audio.src = url;
    viewer.body.appendChild(audio);
  } else if (isPdf) {
    const frame = document.createElement("iframe");
    frame.className = "attachment-viewer-frame";
    frame.title = name;
    frame.src = url;
    viewer.body.appendChild(frame);
  } else {
    const empty = document.createElement("div");
    empty.className = "attachment-viewer-fallback";
    empty.innerHTML = `
      ${icon("paperclip")}
      <strong>${escapeHtml(name)}</strong>
      <span>Preview is not available for this file type. Open it in a new tab.</span>
    `;
    viewer.body.appendChild(empty);
    refreshIcons();
  }

  viewer.modal.classList.remove("hidden");
}

function setActiveView(view) {
  if (view !== "livechat") clearLivechatAdminTyping();

  const vcModal = document.getElementById("videoCallModal");
  if (vcModal && !vcModal.classList.contains("hidden") && !vcModal.classList.contains("floating")) {
    vcModal.classList.add("floating");
  }

  state.activeView = view;
  render();
}

function updateNotificationComposer() {
  const length = els.notificationMessageInput.value.length;
  els.notificationCharCount.textContent = `${length}/180`;
  els.sendNotificationButton.disabled = !els.notificationMessageInput.value.trim();
  els.clearNotificationButton.disabled = !els.notificationTitleInput.value.trim()
    && !els.notificationMessageInput.value.trim();
}

function clearNotificationComposer() {
  els.notificationAudienceInput.value = "all";
  els.notificationTitleInput.value = "";
  els.notificationMessageInput.value = "";
  updateNotificationComposer();
  els.notificationMessageInput.focus();
}

async function handleNoticeSubmit(event) {
  event.preventDefault();
  const message = els.notificationMessageInput.value.trim();
  const title = els.notificationTitleInput.value.trim();
  const audience = els.notificationAudienceInput.value || "all";
  if (!message) {
    showToast("Enter a notification message.");
    updateNotificationComposer();
    return;
  }

  els.sendNotificationButton.disabled = true;
  try {
    const noticeRef = push(ref(db, "admin_notifications"));
    const noticeId = noticeRef.key;
    if (!noticeId) throw new Error("Unable to create notice id.");

    const sentAt = serverTimestamp();
    const baseNotice = {
      audience,
      audienceLabel: audienceLabel(audience),
      title,
      message,
      sentBy: state.currentUser.uid,
      sentAt,
      status: "published",
      method: NOTICE_METHOD,
      source: NOTICE_SOURCE
    };
    const updates = {
      [`admin_notifications/${noticeId}`]: {
        ...baseNotice,
        successCount: 1,
        failureCount: 0
      },
      "broadcastNotifications/current": {
        ...baseNotice,
        id: noticeId,
        active: true
      },
      [`broadcastNotifications/history/${noticeId}`]: {
        ...baseNotice,
        id: noticeId,
        active: true
      }
    };

    await update(ref(db), updates);
    els.notificationTitleInput.value = "";
    els.notificationMessageInput.value = "";
    updateNotificationComposer();
    showToast("Notification published.");
  } catch (error) {
    console.error(error);
    showToast(error.message || "Unable to publish notice.");
  } finally {
    updateNotificationComposer();
  }
}

function currentAdminName() {
  return LIVECHAT_ADMIN_LABEL;
}

function setLivechatAttachMenu(open) {
  const shouldOpen = Boolean(open && state.selectedChatUid && !state.livechatSending);
  els.livechatAttachMenu.classList.toggle("hidden", !shouldOpen);
  els.livechatAttachButton.setAttribute("aria-expanded", shouldOpen ? "true" : "false");
}

function renderLivechatAttachment() {
  const attachment = state.livechatAttachment;
  els.livechatAttachmentLabel.classList.toggle("hidden", !attachment);
  if (!attachment) {
    els.livechatAttachmentLabel.innerHTML = "";
    return;
  }

  els.livechatAttachmentLabel.innerHTML = `
    <span>Attached: <strong>${escapeHtml(attachment.name)}</strong></span>
    <button data-livechat-clear-attachment type="button" aria-label="Remove attachment">&times;</button>
  `;
}

function clearLivechatAttachment() {
  state.livechatAttachment = null;
  state.livechatAttachmentKind = "";
  els.livechatAttachmentInput.value = "";
  renderLivechatAttachment();
  updateLivechatComposer();
}

function updateLivechatComposer() {
  const hasThread = Boolean(state.selectedChatUid);
  const hasText = Boolean(els.livechatReplyInput.value.trim());
  const hasAttachment = Boolean(state.livechatAttachment);
  els.livechatReplyInput.disabled = !hasThread || state.livechatSending;
  els.livechatAttachButton.disabled = !hasThread || state.livechatSending;
  els.livechatReplyButton.disabled = !hasThread || state.livechatSending || (!hasText && !hasAttachment);
  if (!hasThread || state.livechatSending) setLivechatAttachMenu(false);
  renderLivechatAttachment();
}

function clearLivechatTypingTimer() {
  if (state.livechatTypingTimer) {
    window.clearTimeout(state.livechatTypingTimer);
    state.livechatTypingTimer = null;
  }
}

async function writeLivechatTyping(active, uid = state.selectedChatUid) {
  if (!validPathSegment(uid)) return;
  const updates = {
    adminTyping: Boolean(active),
    adminTypingAt: serverTimestamp(),
    adminTypingBy: state.currentUser ? state.currentUser.uid : "",
    adminTypingName: LIVECHAT_ADMIN_LABEL
  };
  await update(ref(db, `supportChats/${uid}/meta`), updates);
}

function clearLivechatAdminTyping(uid = state.livechatTypingUid || state.selectedChatUid) {
  clearLivechatTypingTimer();
  if (!state.livechatTypingActive && !state.livechatTypingUid) return;
  const targetUid = uid;
  state.livechatTypingActive = false;
  state.livechatTypingUid = "";
  state.livechatTypingLastSentAt = 0;
  writeLivechatTyping(false, targetUid).catch((error) => console.warn(error));
}

function handleLivechatTypingInput() {
  updateLivechatComposer();
  const uid = state.selectedChatUid;
  const hasText = Boolean(els.livechatReplyInput.value);
  if (!validPathSegment(uid) || state.livechatSending || !hasText) {
    clearLivechatAdminTyping(uid);
    return;
  }

  const now = Date.now();
  state.livechatTypingUid = uid;
  if (!state.livechatTypingActive || now - state.livechatTypingLastSentAt >= LIVECHAT_TYPING_REFRESH_MS) {
    state.livechatTypingActive = true;
    state.livechatTypingLastSentAt = now;
    writeLivechatTyping(true, uid).catch((error) => console.warn(error));
  }

  clearLivechatTypingTimer();
  state.livechatTypingTimer = window.setTimeout(() => {
    clearLivechatAdminTyping(uid);
  }, LIVECHAT_TYPING_IDLE_MS);
}

function chooseLivechatAttachment(kind) {
  const option = LIVECHAT_ATTACHMENT_OPTIONS[kind];
  if (!option) return;

  state.livechatAttachmentKind = kind;
  els.livechatAttachmentInput.value = "";
  els.livechatAttachmentInput.accept = option.accept || "";
  if (option.capture) {
    els.livechatAttachmentInput.setAttribute("capture", option.capture);
  } else {
    els.livechatAttachmentInput.removeAttribute("capture");
  }
  els.livechatAttachmentInput.click();
}

function handleLivechatAttachmentSelected() {
  const file = els.livechatAttachmentInput.files && els.livechatAttachmentInput.files[0];
  if (!file) return;
  state.livechatAttachment = {
    file,
    kind: state.livechatAttachmentKind,
    name: file.name || "attachment",
    mimeType: file.type || "application/octet-stream",
    size: file.size || 0
  };
  setLivechatAttachMenu(false);
  updateLivechatComposer();
}

async function buildLivechatAttachmentPayload(uid, messageId) {
  const attachment = state.livechatAttachment;
  if (!attachment || !attachment.file) return null;

  if (canInlineImage(attachment.file)) {
    return {
      name: attachment.name,
      mimeType: "image/jpeg",
      size: attachment.size,
      downloadUrl: await imageFileToInlineDataUrl(attachment.file)
    };
  }

  const adminUid = state.currentUser && state.currentUser.uid;
  if (!validPathSegment(adminUid)) throw new Error("Admin account is not ready.");
  const safeName = safeStorageName(attachment.name);
  const target = storageRef(
    storage,
    `supportChatAdminUploads/${adminUid}/${uid}/${messageId}/${Date.now()}_${safeName}`
  );
  await uploadBytes(target, attachment.file, {
    contentType: attachment.mimeType || "application/octet-stream"
  });
  return {
    name: attachment.name,
    mimeType: attachment.mimeType || "application/octet-stream",
    size: attachment.size || 0,
    downloadUrl: await getDownloadURL(target)
  };
}

async function sendAdminLivechatReply(event) {
  event.preventDefault();
  const uid = state.selectedChatUid;
  const message = els.livechatReplyInput.value.trim();
  const attachment = state.livechatAttachment;
  if (!validPathSegment(uid)) {
    showToast("Select a livechat thread first.");
    return;
  }
  if (!message && !attachment) {
    showToast("Type a reply or attach a file first.");
    updateLivechatComposer();
    return;
  }

  state.livechatSending = true;
  clearLivechatAdminTyping(uid);
  updateLivechatComposer();
  try {
    const messageRef = push(ref(db, `supportChats/${uid}/messages`));
    const messageId = messageRef.key;
    if (!messageId) throw new Error("Unable to create message id.");

    const adminName = currentAdminName();
    const attachmentPayload = await buildLivechatAttachmentPayload(uid, messageId);
    const chatMessage = {
      text: message,
      sender: "admin",
      senderUid: state.currentUser.uid,
      senderName: adminName,
      createdAt: serverTimestamp(),
      source: "admin-panel"
    };
    if (attachmentPayload) chatMessage.attachment = attachmentPayload;

    const lastMessage = message || `Attachment: ${attachmentPayload.name || "file"}`;
    await update(ref(db), {
      [`supportChats/${uid}/messages/${messageId}`]: chatMessage,
      [`supportChats/${uid}/meta/status`]: "answered",
      [`supportChats/${uid}/meta/lastMessage`]: lastMessage,
      [`supportChats/${uid}/meta/lastSender`]: "admin",
      [`supportChats/${uid}/meta/lastAdminUid`]: state.currentUser.uid,
      [`supportChats/${uid}/meta/lastAdminName`]: adminName,
      [`supportChats/${uid}/meta/updatedAt`]: serverTimestamp()
    });
    els.livechatReplyInput.value = "";
    clearLivechatAttachment();
    updateLivechatComposer();
    showToast("Livechat reply sent.");
  } catch (error) {
    console.error(error);
    showToast(error.message || "Unable to send livechat reply.");
  } finally {
    state.livechatSending = false;
    updateLivechatComposer();
  }
}

async function resolveSelectedChat() {
  const uid = state.selectedChatUid;
  if (!validPathSegment(uid)) {
    showToast("Select a livechat thread first.");
    return;
  }

  await update(ref(db, `supportChats/${uid}/meta`), {
    status: "resolved",
    resolvedAt: serverTimestamp(),
    resolvedBy: state.currentUser.uid,
    updatedAt: serverTimestamp()
  });
  showToast("Livechat marked resolved.");
}

async function claimLivechatThread(uid) {
  if (!validPathSegment(uid)) return;
  const thread = asRecord(state.supportChats?.[uid]);
  const meta = asRecord(thread.meta);
  if (millis(meta.claimedAt)) return;

  await update(ref(db, `supportChats/${uid}/meta`), {
    claimedAt: serverTimestamp(),
    claimedBy: state.currentUser.uid,
    claimedByName: LIVECHAT_ADMIN_LABEL,
    claimNotice: LIVECHAT_CLAIM_NOTICE
  });
}

async function deleteSelectedResolvedChat() {
  const uid = state.selectedChatUid;
  if (!validPathSegment(uid)) {
    showToast("Select a livechat thread first.");
    return;
  }

  const thread = getSupportThreads().find((item) => item.uid === uid);
  if (!thread || text(thread.status).toLowerCase() !== "resolved") {
    showToast("Resolve the livechat before deleting it.");
    return;
  }

  if (!window.confirm(`Delete resolved livechat for ${thread.name}?`)) return;
  await remove(ref(db, `supportChats/${uid}`));
  state.selectedChatUid = "";
  showToast("Resolved livechat deleted.");
}

async function deleteSelectedAiChat() {
  const uid = state.selectedAiChatUid;
  if (!validPathSegment(uid)) {
    showToast("Select an AI chat thread first.");
    return;
  }

  const thread = getAiThreads().find((item) => item.uid === uid);
  if (!thread) {
    state.selectedAiChatUid = "";
    renderAiChat();
    return;
  }

  if (!window.confirm(`Reset AI chat for ${thread.name}?`)) return;
  await update(ref(db), {
    [`aiChats/${uid}`]: null,
    [`supportChats/${uid}/aiAssistant`]: null
  });
  state.selectedAiChatUid = "";
  showToast("AI chat reset.");
}

async function cleanupExpiredAiChats() {
  if (!state.currentUser || state.aiChatCleanupRunning) return;
  const expired = getAiThreads().filter((thread) => (
    thread.expiresAt > 0 && thread.expiresAt <= Date.now()
  ));
  if (!expired.length) return;

  state.aiChatCleanupRunning = true;
  try {
    const updates = {};
    expired.forEach((thread) => {
      if (!validPathSegment(thread.uid)) return;
      updates[`aiChats/${thread.uid}`] = null;
      updates[`supportChats/${thread.uid}/aiAssistant`] = null;
    });
    if (!Object.keys(updates).length) return;
    await update(ref(db), updates);
    if (expired.some((thread) => thread.uid === state.selectedAiChatUid)) {
      state.selectedAiChatUid = "";
    }
  } finally {
    state.aiChatCleanupRunning = false;
  }
}

async function cleanupExpiredSosAlerts() {
  if (!state.currentUser || state.sosCleanupRunning) return;
  state.sosCleanupRunning = true;
  try {
    const now = Date.now();
    const GRACE_PERIOD_MS = 20 * 1000;
    const updates = {};

    entries(state.rooms).forEach(([roomId, room]) => {
      if (!room || !room.sosAlerts) return;
      entries(room.sosAlerts).forEach(([alertId, alert]) => {
        if (!alert || typeof alert !== "object") return;
        const isResolved = alert.status === "resolved" || (alert.progressStep && Number(alert.progressStep) >= 4) || alert.resolvedAt;
        const isCancelled = alert.status === "cancelled" || alert.cancelledAt || (alert.active === false && !isResolved);

        if (isResolved) {
          const resolvedTime = millis(alert.resolvedAt || alert.updatedAt || alert.createdAt);
          if (resolvedTime > 0 && (now - resolvedTime) >= GRACE_PERIOD_MS) {
            updates[`rooms/${roomId}/sosAlerts/${alertId}`] = null;
          }
        } else if (isCancelled) {
          const cancelledTime = millis(alert.cancelledAt || alert.updatedAt || alert.createdAt);
          if (cancelledTime > 0 && (now - cancelledTime) >= GRACE_PERIOD_MS) {
            updates[`rooms/${roomId}/sosAlerts/${alertId}`] = null;
          }
        }
      });
    });

    if (Object.keys(updates).length > 0) {
      console.log(`[RTDB AUTO-CLEAR] Membersihkan ${Object.keys(updates).length} kes SOS selesai/batal dari RTDB...`);
      await update(ref(db), updates);
    }
  } catch (err) {
    console.warn("[RTDB AUTO-CLEAR] Ralat pembersihan SOS:", err);
  } finally {
    state.sosCleanupRunning = false;
  }
}

function getAdminDisplayName() {
  if (!state.currentUser) return "Admin Responder";
  const uid = state.currentUser.uid;
  const userRecord = state.users && state.users[uid] ? asRecord(state.users[uid]) : null;
  const nameFromDb = userRecord && userRecord.name ? text(userRecord.name).trim() : "";
  if (nameFromDb && !nameFromDb.includes("@")) return nameFromDb;

  const displayName = text(state.currentUser.displayName || "").trim();
  if (displayName && !displayName.includes("@")) return displayName;

  if (nameFromDb) {
    const part = nameFromDb.split("@")[0].replace(/[._-]/g, " ").trim();
    if (part) {
      return part.split(/\s+/).map((w) => w.charAt(0).toUpperCase() + w.slice(1)).join(" ");
    }
  }

  const email = text(state.currentUser.email || "").trim();
  if (email) {
    const part = email.split("@")[0].replace(/[._-]/g, " ").trim();
    if (part) {
      return part.split(/\s+/).map((w) => w.charAt(0).toUpperCase() + w.slice(1)).join(" ");
    }
  }

  return "Admin Responder";
}

async function reserveSosCase(roomId, alertId, senderUid, senderName) {
  if (!validPathSegment(roomId) || !validPathSegment(alertId)) return;

  state.servedCases = state.servedCases || new Set();
  state.servedCases.add(alertId);

  // AUTOMATICALLY MUTE & STOP SIREN IMMEDIATELY ON FIRST CLICK
  sosAlarmSound.mute(alertId);
  sosAlarmSound.stop();

  const banner = document.getElementById("sosAlarmBanner");
  if (banner) banner.classList.add("hidden");

  const adminName = getAdminDisplayName();
  const adminUid = state.currentUser ? state.currentUser.uid : "admin";
  const primaryUpdates = {
    status: "active",
    active: true,
    served: true,
    servedBy: adminUid,
    servedByName: "Administrator",
    servedAt: serverTimestamp(),
    progressStep: 1,
    progressStatus: "Admin Dispatched",
    progressNotes: "Emergency assistance is assigned and responders have been notified."
  };

  const crossRoomUpdates = {};
  if (senderUid) {
    getSosAlerts().forEach((a) => {
      const isCancelled = a.cancelledAt || (a.data && a.data.cancelledAt) || a.status === "cancelled" || (a.data && a.data.status === "cancelled");
      if (a.source === "room" && a.senderUid === senderUid && a.roomId !== roomId && a.key !== alertId && !isCancelled && validPathSegment(a.roomId) && validPathSegment(a.key)) {
        if (state.rooms && state.rooms[a.roomId] && state.rooms[a.roomId].sosAlerts && state.rooms[a.roomId].sosAlerts[a.key]) {
          crossRoomUpdates[`rooms/${a.roomId}/sosAlerts/${a.key}/status`] = "active";
          crossRoomUpdates[`rooms/${a.roomId}/sosAlerts/${a.key}/active`] = true;
          crossRoomUpdates[`rooms/${a.roomId}/sosAlerts/${a.key}/served`] = true;
          crossRoomUpdates[`rooms/${a.roomId}/sosAlerts/${a.key}/servedBy`] = adminUid;
          crossRoomUpdates[`rooms/${a.roomId}/sosAlerts/${a.key}/servedByName`] = "Administrator";
          crossRoomUpdates[`rooms/${a.roomId}/sosAlerts/${a.key}/servedAt`] = serverTimestamp();
          crossRoomUpdates[`rooms/${a.roomId}/sosAlerts/${a.key}/progressStep`] = 1;
          crossRoomUpdates[`rooms/${a.roomId}/sosAlerts/${a.key}/progressStatus`] = "Admin Dispatched";
          crossRoomUpdates[`rooms/${a.roomId}/sosAlerts/${a.key}/progressNotes`] = "Emergency assistance is assigned and responders have been notified.";
        }
      }
    });
  }

  try {
    await update(ref(db, `rooms/${roomId}/sosAlerts/${alertId}`), primaryUpdates);
    if (Object.keys(crossRoomUpdates).length > 0) {
      try {
        await update(ref(db), crossRoomUpdates);
      } catch (ignored) {}
    }
  } catch (err) {
    console.error("reserveSosCase error:", err);
  }
}

let activeCaseModalData = null;

function openCaseModal(roomId, alertId, senderUid, senderName) {
  const modal = document.getElementById("sosCaseUpdateModal");
  if (!modal) return;

  const matched = getSosAlerts().find(
    (a) => (a.roomId === roomId || a.roomId === "DIRECT") && (a.key === alertId || a.id === alertId)
  );
  const curStep = (matched && matched.data && matched.data.progressStep) || 1;
  const curStatus = (matched && matched.data && matched.data.progressStatus) || "Admin Dispatched";
  const curNotes = (matched && matched.data && (matched.data.progressNotes || matched.data.notes)) || "Emergency assistance is assigned and responders have been notified.";
  const resolvedName = senderName || (matched && (matched.senderName || (matched.data && (matched.data.senderName || matched.data.fromName)))) || "User";
  const resolvedUid = senderUid || (matched && (matched.senderUid || (matched.data && (matched.data.senderUid || matched.data.fromUid)))) || "";

  activeCaseModalData = {
    roomId,
    alertId,
    senderUid: resolvedUid,
    senderName: resolvedName,
    step: Number(curStep) || 1,
    status: curStatus,
    desc: curNotes
  };

  const title = document.getElementById("caseModalTitle");
  const sub = document.getElementById("caseModalSubtitle");
  const notesInput = document.getElementById("caseNotesInput");
  const callBtn = document.getElementById("caseCallBtn");
  const msgBtn = document.getElementById("caseMessageBtn");
  const roomBtn = document.getElementById("caseRoomBtn");

  if (title) title.textContent = `SOS: ${resolvedName}`;
  if (sub) sub.textContent = `${roomId === "DIRECT" ? "Direct SOS (No Room)" : `Room ${roomId || "-"}`} • Active Emergency Case`;
  if (notesInput) notesInput.value = curNotes;

  // Reset/Set active step
  document.querySelectorAll(".sos-case-step-btn").forEach((btn) => {
    btn.classList.toggle("is-active", String(btn.dataset.step) === String(curStep));
  });

  updateCaseAutoSaveStatus("All changes auto-saved", "saved");

  if (callBtn) {
    callBtn.onclick = () => {
      openCaseCallOptions(resolvedUid, resolvedName);
    };
  }
  if (msgBtn) {
    msgBtn.onclick = () => {
      closeCaseModal();
      state.selectedSosSessionId = `${roomId}__${alertId}`;
      setActiveView("soslivechat");
    };
  }
  if (roomBtn) {
    if (roomId === "DIRECT") {
      roomBtn.style.display = "none";
    } else {
      roomBtn.style.display = "";
      roomBtn.onclick = () => {
        state.selected = { type: "room", id: roomId };
        closeCaseModal();
        render();
      };
    }
  }

  modal.classList.remove("hidden");
  refreshIcons();
}

let activeCaseCallData = null;

function openCaseCallOptions(uid, name) {
  activeCaseCallData = { uid, name };
  const modal = document.getElementById("caseCallOptionsModal");
  if (!modal) {
    if (uid) startAdminCall(uid, name || "Sender", "video");
    return;
  }
  const title = document.getElementById("caseCallOptionsTitle");
  const sub = document.getElementById("caseCallOptionsSubtitle");
  if (title) title.textContent = `Panggilan Kecemasan: ${name || "Pengadu"}`;
  if (sub) sub.textContent = "Pilih mod panggilan untuk menghubungi peranti pengadu/mangsa:";

  const vidBtn = document.getElementById("adminStartVideoCallBtn");
  const voiceBtn = document.getElementById("adminStartVoiceCallBtn");
  const cancelBtn = document.getElementById("caseCallOptionsCancelBtn");
  const closeBtn = document.getElementById("caseCallOptionsCloseBtn");
  const backdrop = document.getElementById("caseCallOptionsBackdrop");

  const cleanup = () => closeCaseCallOptions();
  if (cancelBtn) cancelBtn.onclick = cleanup;
  if (closeBtn) closeBtn.onclick = cleanup;
  if (backdrop) backdrop.onclick = cleanup;

  if (vidBtn) {
    vidBtn.onclick = () => {
      cleanup();
      closeCaseModal();
      startAdminCall(uid, name || "Sender", "video");
    };
  }
  if (voiceBtn) {
    voiceBtn.onclick = () => {
      cleanup();
      closeCaseModal();
      startAdminCall(uid, name || "Sender", "voice");
    };
  }

  modal.classList.remove("hidden");
  refreshIcons();
}

function closeCaseCallOptions() {
  const modal = document.getElementById("caseCallOptionsModal");
  if (modal) modal.classList.add("hidden");
  activeCaseCallData = null;
}

function closeCaseModal() {
  const modal = document.getElementById("sosCaseUpdateModal");
  if (modal) modal.classList.add("hidden");
  activeCaseModalData = null;
}

function updateCaseAutoSaveStatus(msg, type = "saved") {
  const indicator = document.getElementById("caseAutoSaveStatus");
  if (!indicator) return;
  if (type === "saving") {
    indicator.className = "sos-case-autosave is-saving";
    indicator.innerHTML = `<i data-lucide="refresh-cw" class="spin" style="width:14px;height:14px;"></i> <span id="caseAutoSaveText">${msg || "Saving changes..."}</span>`;
  } else if (type === "error") {
    indicator.className = "sos-case-autosave is-error";
    indicator.innerHTML = `<i data-lucide="alert-circle" style="width:14px;height:14px;"></i> <span id="caseAutoSaveText">${msg || "Error saving"}</span>`;
  } else {
    indicator.className = "sos-case-autosave is-saved";
    indicator.innerHTML = `<i data-lucide="check" style="width:14px;height:14px;"></i> <span id="caseAutoSaveText">${msg || "All changes auto-saved"}</span>`;
  }
  refreshIcons();
}

let isSavingCaseProgress = false;
let pendingCaseSave = false;

async function saveCaseProgress(options = {}) {
  const shouldClose = options === true || (options && options.shouldClose);
  const silent = Boolean(options && options.silent);

  if (isSavingCaseProgress) {
    pendingCaseSave = true;
    return;
  }
  if (!activeCaseModalData) {
    return;
  }
  isSavingCaseProgress = true;
  updateCaseAutoSaveStatus("Saving changes...", "saving");

  const { roomId, alertId, senderUid } = activeCaseModalData;
  const targetSenderName = activeCaseModalData.senderName || "user";
  const activeStepBtn = document.querySelector(".sos-case-step-btn.is-active");
  const step = activeStepBtn ? (Number(activeStepBtn.dataset.step) || activeCaseModalData.step || 1) : (activeCaseModalData.step || 1);
  const status = activeStepBtn ? (activeStepBtn.dataset.status || activeCaseModalData.status || "In Progress") : (activeCaseModalData.status || "In Progress");

  const notesInput = document.getElementById("caseNotesInput");
  const notes = notesInput ? notesInput.value.trim() : "";
  const adminName = getAdminDisplayName();
  const adminUid = state.currentUser ? state.currentUser.uid : "admin";

  const primaryUpdates = {
    progressStep: Number(step) || 1,
    progressStatus: status || "In Progress",
    progressNotes: notes || "Responder updating status.",
    updatedAt: serverTimestamp(),
    updatedBy: adminUid,
    updatedByName: adminName
  };

  if (Number(step) === 4) {
    // Step 4 is Resolved
    primaryUpdates.status = "resolved";
    primaryUpdates.active = false;
    primaryUpdates.resolvedAt = serverTimestamp();
    primaryUpdates.resolvedBy = adminUid;
    primaryUpdates.progressStep = 4;
    primaryUpdates.progressStatus = "Case Resolved";
  }

  // Cross-room synchronization: sync to all rooms for this sender
  const crossRoomUpdates = {};
  if (senderUid) {
    getSosAlerts().forEach((a) => {
      if (a.source === "room" && a.senderUid === senderUid && a.key !== alertId && validPathSegment(a.roomId) && validPathSegment(a.key)) {
        if (state.rooms && state.rooms[a.roomId] && state.rooms[a.roomId].sosAlerts && state.rooms[a.roomId].sosAlerts[a.key]) {
          crossRoomUpdates[`rooms/${a.roomId}/sosAlerts/${a.key}/progressStep`] = Number(step) || 1;
          crossRoomUpdates[`rooms/${a.roomId}/sosAlerts/${a.key}/progressStatus`] = status || "In Progress";
          crossRoomUpdates[`rooms/${a.roomId}/sosAlerts/${a.key}/progressNotes`] = notes || "Responder updating status.";
          crossRoomUpdates[`rooms/${a.roomId}/sosAlerts/${a.key}/updatedAt`] = serverTimestamp();
          crossRoomUpdates[`rooms/${a.roomId}/sosAlerts/${a.key}/updatedBy`] = adminUid;
          crossRoomUpdates[`rooms/${a.roomId}/sosAlerts/${a.key}/updatedByName`] = adminName;
          if (Number(step) === 4) {
            crossRoomUpdates[`rooms/${a.roomId}/sosAlerts/${a.key}/status`] = "resolved";
            crossRoomUpdates[`rooms/${a.roomId}/sosAlerts/${a.key}/active`] = false;
            crossRoomUpdates[`rooms/${a.roomId}/sosAlerts/${a.key}/resolvedAt`] = serverTimestamp();
            crossRoomUpdates[`rooms/${a.roomId}/sosAlerts/${a.key}/resolvedBy`] = adminUid;
            crossRoomUpdates[`rooms/${a.roomId}/sosAlerts/${a.key}/progressStep`] = 4;
            crossRoomUpdates[`rooms/${a.roomId}/sosAlerts/${a.key}/progressStatus`] = "Case Resolved";
          }
        }
      }
    });
  }

  try {
    await update(ref(db, `rooms/${roomId}/sosAlerts/${alertId}`), primaryUpdates);
    if (Object.keys(crossRoomUpdates).length > 0) {
      try {
        await update(ref(db), crossRoomUpdates);
      } catch (crossErr) {
        console.warn("Non-blocking cross-room update notice:", crossErr);
      }
    }
    updateCaseAutoSaveStatus("All changes auto-saved", "saved");
    if (!silent) {
      showToast(`Status updated: ${status}`);
    }

    if (Number(step) === 4) {
      sosAlarmSound.mute(alertId);
      sosAlarmSound.stop();
      if (state.servedCases) {
        state.servedCases.delete(alertId);
        if (activeCaseModalData && activeCaseModalData.alertId) {
          state.servedCases.delete(activeCaseModalData.alertId);
        }
      }
      const banner = document.getElementById("sosAlarmBanner");
      if (banner) banner.classList.add("hidden");

      if (state.selected && state.selected.type === "sos") {
        state.selected = null;
      }
      if (typeof window.__resqRefreshMarkers === "function") {
        window.__resqRefreshMarkers();
      }
      closeCaseModal();

      // AUTO-CLEAR DARI RTDB:
      // Beri ruang masa 15 saat untuk peranti mangsa menerima status "Case Resolved",
      // kemudian padam nod alert daripada RTDB secara automatik
      const currentRoomId = roomId;
      const currentAlertId = alertId;
      const currentCrossUpdates = { ...crossRoomUpdates };
      setTimeout(async () => {
        try {
          const deleteUpdates = {};
          deleteUpdates[`rooms/${currentRoomId}/sosAlerts/${currentAlertId}`] = null;
          Object.keys(currentCrossUpdates).forEach((k) => {
            const parts = k.split("/");
            if (parts.length >= 4) {
              deleteUpdates[`${parts[0]}/${parts[1]}/${parts[2]}/${parts[3]}`] = null;
            }
          });
          await update(ref(db), deleteUpdates);
          console.log(`[RTDB AUTO-CLEAR] Alert ${currentAlertId} berjaya dipadam dari RTDB selepas kes selesai.`);
        } catch (e) {
          console.warn("[RTDB AUTO-CLEAR] Ralat memadam alert selesai:", e);
        }
      }, 15000);
    } else if (shouldClose) {
      closeCaseModal();
    }

    render();
  } catch (err) {
    console.error("Failed to save case progress:", err);
    updateCaseAutoSaveStatus("Error auto-saving", "error");
    if (!silent) {
      showToast("Error updating case: " + (err.message || err));
    }
  } finally {
    isSavingCaseProgress = false;
    if (pendingCaseSave) {
      pendingCaseSave = false;
      saveCaseProgress(options);
    }
  }
}

async function cancelSos(roomId, alertId) {
  if (!validPathSegment(roomId) || !validPathSegment(alertId)) {
    showToast("Invalid SOS path.");
    return;
  }
  if (!window.confirm(`Cancel SOS alert ${alertId}?`)) return;

  const alertObj = getSosAlerts().find((a) => a.roomId === roomId && a.key === alertId);
  const senderUid = alertObj ? alertObj.senderUid : "";

  const updates = {};
  updates[`rooms/${roomId}/sosAlerts/${alertId}/status`] = "cancelled";
  updates[`rooms/${roomId}/sosAlerts/${alertId}/cancelledAt`] = serverTimestamp();
  updates[`rooms/${roomId}/sosAlerts/${alertId}/cancelledByAdmin`] = state.currentUser.uid;
  updates[`rooms/${roomId}/sosAlerts/${alertId}/active`] = false;

  // Also cancel any other active alerts from the same sender in any room so stray alerts don't hijack coordinates
  if (senderUid) {
    getSosAlerts().forEach((a) => {
      if (a.senderUid === senderUid && a.active && a.key !== alertId) {
        if (validPathSegment(a.roomId) && validPathSegment(a.key)) {
          updates[`rooms/${a.roomId}/sosAlerts/${a.key}/status`] = "cancelled";
          updates[`rooms/${a.roomId}/sosAlerts/${a.key}/cancelledAt`] = serverTimestamp();
          updates[`rooms/${a.roomId}/sosAlerts/${a.key}/cancelledByAdmin`] = state.currentUser.uid;
          updates[`rooms/${a.roomId}/sosAlerts/${a.key}/active`] = false;
        }
      }
    });
  }

  await update(ref(db), updates);
  showToast("SOS alert cancelled.");
  if (typeof window.__resqRefreshMarkers === "function") {
    window.__resqRefreshMarkers();
  }
  render();
}

async function deleteNotification(noticeId) {
  if (!validPathSegment(noticeId)) {
    showToast("Invalid notification path.");
    return;
  }
  if (!window.confirm(`Delete notification ${noticeId}?`)) return;

  const currentSnapshot = await get(ref(db, "broadcastNotifications/current/id"));
  const updates = {
    [`admin_notifications/${noticeId}`]: null,
    [`broadcastNotifications/history/${noticeId}`]: null,
    [`admin_audit_logs/${noticeId}`]: null,
    [`call_logs/${noticeId}`]: null
  };
  if (text(currentSnapshot.val()).trim() === noticeId) {
    updates["broadcastNotifications/current"] = null;
  }

  await update(ref(db), updates);
  showToast("Notification deleted.");
}

async function removeMember(roomId, uid) {
  if (!validPathSegment(roomId) || !validPathSegment(uid)) {
    showToast("Invalid member path.");
    return;
  }
  if (!window.confirm(`Remove ${userName(uid)} from room ${roomId}?`)) return;
  const updates = {};
  updates[`rooms/${roomId}/forceLeave/${uid}`] = {
    at: serverTimestamp(),
    by: state.currentUser.uid,
    source: "admin-panel"
  };
  updates[`rooms/${roomId}/members/${uid}`] = null;
  updates[`rooms/${roomId}/bells/${uid}`] = null;
  updates[`userRooms/${uid}/${roomId}`] = null;
  await update(ref(db), updates);
  showToast("Member removed.");
}

async function deleteUser(uid) {
  if (!validPathSegment(uid)) {
    showToast("Invalid user UID.");
    return;
  }

  const name = userName(uid);
  const self = uid === state.currentUser.uid;
  const message = self
    ? "Delete your own user record? You may lose admin panel access after refresh."
    : `Delete user ${name}? This will remove their profile and unlink them from channels.`;
  if (!window.confirm(message)) return;

  const userObj = state.users && state.users[uid];
  const userEmail = (userObj && userObj.email) ? String(userObj.email).trim() : "";

  const updates = {};
  updates[`users/${uid}`] = null;
  updates[`userRooms/${uid}`] = null;
  updates[`admins/${uid}`] = null;
  updates[`admin_user_deletions/${uid}`] = {
    deletedBy: state.currentUser.uid,
    deletedAt: serverTimestamp(),
    email: userEmail,
    status: "pending"
  };

  if (userEmail) {
    const sanitizedEmail = userEmail.toLowerCase().replace(/\./g, "_").replace(/@/g, "_at_");
    updates[`registeredEmails/${sanitizedEmail}`] = null;
  }

  // Padam akaun daripada Firebase Authentication secara langsung via Auth Bridge
  // Selagi akaun Auth gagal dipadam, akaun tidak akan dibuang dari sistem
  try {
    await deleteSingleAuthAccount(uid);
  } catch (authErr) {
    console.error("[DELETE-USER] Gagal memadam akaun daripada Firebase Auth:", authErr);
    showToast("Gagal memadam akaun Authentication: " + (authErr.message || authErr));
    return;
  }

  // Panggil API backend jika tersedia untuk padam Auth serta-merta
  try {
    fetch("/api/admin/delete-user", {
      method: "POST",
      headers: { "Content-Type": "application/json" },
      body: JSON.stringify({ uid, email: userEmail })
    }).catch(() => {});
  } catch (ignored) {}

  entries(state.rooms).forEach(([roomId, room]) => {
    if (!validPathSegment(roomId)) return;

    const roomRecord = asRecord(room);
    if (asRecord(roomRecord.members)[uid]) {
      updates[`rooms/${roomId}/forceLeave/${uid}`] = {
        at: serverTimestamp(),
        by: state.currentUser.uid,
        source: "admin-panel"
      };
      updates[`rooms/${roomId}/members/${uid}`] = null;
    }

    if (asRecord(roomRecord.bells)[uid]) {
      updates[`rooms/${roomId}/bells/${uid}`] = null;
    }

    entries(roomRecord.sosAlerts).forEach(([alertId, alertValue]) => {
      const alert = asRecord(alertValue);
      const senderUid = text(alert.senderUid || alert.fromUid).trim();
      if (validPathSegment(alertId) && senderUid === uid) {
        updates[`rooms/${roomId}/sosAlerts/${alertId}`] = null;
      }
    });
  });

  entries(state.userRooms).forEach(([otherUid, rooms]) => {
    if (otherUid !== uid && validPathSegment(otherUid) && asRecord(rooms)[uid]) {
      updates[`userRooms/${otherUid}/${uid}`] = null;
    }
  });

  entries(state.legacyAlerts).forEach(([alertId, alertValue]) => {
    const alert = asRecord(alertValue);
    const senderUid = text(alert.senderUid || alert.fromUid).trim();
    if (validPathSegment(alertId) && senderUid === uid) {
      updates[`sos_alerts/${alertId}`] = null;
    }
  });

  await update(ref(db), updates);
  if (state.selected && state.selected.type === "user" && state.selected.id === uid) {
    state.selected = null;
  }
  await loadAuthUsers();
  showToast("User deleted.");
}

async function deleteRoom(roomId) {
  if (!validPathSegment(roomId)) {
    showToast("Invalid channel code.");
    return;
  }
  if (!window.confirm(`Delete channel ${roomId} and unlink it from all members?`)) return;
  const room = asRecord(state.rooms[roomId]);
  const updates = {};
  updates[`roomTombstones/${roomId}`] = {
    deletedAt: serverTimestamp(),
    by: state.currentUser.uid,
    source: "admin-panel"
  };
  updates[`rooms/${roomId}`] = null;
  getRoomMembers(room).forEach((member) => {
    updates[`userRooms/${member.uid}/${roomId}`] = null;
  });
  entries(state.userRooms).forEach(([uid, rooms]) => {
    if (asRecord(rooms)[roomId]) updates[`userRooms/${uid}/${roomId}`] = null;
  });
  await update(ref(db), updates);
  state.selected = null;
  showToast("Channel deleted.");
}

async function seedDefaultHighlights() {
  const updates = {
    "highlights/hl_people_first": {
      id: "hl_people_first",
      title: "People First Abilities Always",
      imageUrl: "assets/highlight_1.jpg",
      actionUrl: "https://www.jkm.gov.my",
      order: 1,
      active: true,
      createdAt: serverTimestamp()
    },
    "highlights/hl_ability_limits": {
      id: "hl_ability_limits",
      title: "Ability Has No Limits - Inclusion Is Everyone's Mission",
      imageUrl: "assets/highlight_2.jpg",
      actionUrl: "https://www.moh.gov.my",
      order: 2,
      active: true,
      createdAt: serverTimestamp()
    },
    "highlights/hl_different_abilities": {
      id: "hl_different_abilities",
      title: "Different Abilities One Community - Inclusion Today",
      imageUrl: "assets/highlight_3.jpg",
      actionUrl: "https://www.malaysia.gov.my",
      order: 3,
      active: true,
      createdAt: serverTimestamp()
    }
  };

  try {
    await update(ref(db), updates);
    // Persist to Supabase
    try {
      await supabase.from("highlights").upsert([
        { id: "hl_people_first", title: "People First Abilities Always", image_url: "assets/highlight_1.jpg", action_url: "https://www.jkm.gov.my", display_order: 1, active: true },
        { id: "hl_ability_limits", title: "Ability Has No Limits - Inclusion Is Everyone's Mission", image_url: "assets/highlight_2.jpg", action_url: "https://www.moh.gov.my", display_order: 2, active: true },
        { id: "hl_different_abilities", title: "Different Abilities One Community - Inclusion Today", image_url: "assets/highlight_3.jpg", action_url: "https://www.malaysia.gov.my", display_order: 3, active: true }
      ]);
      syncFromSupabase();
    } catch (sErr) {
      console.warn("[SUPABASE_HL_SEED_ERR]", sErr);
    }
    showToast("3 Banner Kempen Inklusi OKU rasmi berjaya dimuatkan ke Firebase & Supabase!");
  } catch (error) {
    console.error(error);
    showToast(error.message || "Gagal memuatkan banner default.");
  }
}

async function grantAdmin(uid) {
  if (!validPathSegment(uid)) {
    showToast("Enter a valid Firebase UID.");
    return;
  }
  await set(ref(db, `admins/${uid}`), {
    active: true,
    grantedAt: serverTimestamp(),
    grantedBy: state.currentUser.uid
  });
  // Dual-cloud sync: Update Supabase profile role
  try {
    await supabase.from("profiles").update({ role: "admin", updated_at: new Date().toISOString() }).eq("id", uid);
    if (state.users[uid]) state.users[uid].role = "admin";
  } catch (sErr) {
    console.warn("[SUPABASE_GRANT_ADMIN_ERR]", sErr);
  }
  els.adminUidInput.value = "";
  showToast("Admin access granted.");
}

async function revokeAdmin(uid) {
  if (!validPathSegment(uid)) {
    showToast("Invalid admin UID.");
    return;
  }
  const self = uid === state.currentUser.uid;
  const message = self
    ? "Revoke your own admin access? You will lose access after refresh."
    : `Revoke admin access for ${uid}?`;
  if (!window.confirm(message)) return;
  await remove(ref(db, `admins/${uid}`));
  // Dual-cloud sync: Update Supabase profile role
  try {
    await supabase.from("profiles").update({ role: "user", updated_at: new Date().toISOString() }).eq("id", uid);
    if (state.users[uid]) state.users[uid].role = "user";
  } catch (sErr) {
    console.warn("[SUPABASE_REVOKE_ADMIN_ERR]", sErr);
  }
  showToast("Admin access revoked.");
}

async function setReportStatus(reportId, status) {
  if (!validPathSegment(reportId)) {
    showToast("Invalid report ID.");
    return;
  }
  const meta = reportStatusMeta(status);
  const updates = {
    status: meta.id,
    updatedAt: serverTimestamp(),
    reviewedBy: state.currentUser.uid
  };
  if (meta.id === "reviewing") updates.reviewedAt = serverTimestamp();
  if (meta.id === "resolved") updates.resolvedAt = serverTimestamp();
  if (meta.id === "rejected") updates.rejectedAt = serverTimestamp();
  await update(ref(db, `incidentReports/${reportId}`), updates);
  // Dual-cloud sync: Update Supabase reports table
  try {
    await supabase.from("reports").update({ status: meta.id }).eq("id", reportId);
  } catch (sErr) {
    console.warn("[SUPABASE_REPORT_STATUS_ERR]", sErr);
  }
  showToast(`Report marked ${meta.label.toLowerCase()}.`);
}

async function deleteReport(reportId) {
  if (!validPathSegment(reportId)) {
    showToast("Invalid report ID.");
    return;
  }
  if (!window.confirm(`Delete report ${reportId}?`)) return;
  // Dual-cloud sync: Delete from Supabase reports table
  try {
    await supabase.from("reports").delete().eq("id", reportId);
  } catch (sErr) {
    console.warn("[SUPABASE_REPORT_DEL_ERR]", sErr);
  }
  let rep = state.incidentReports && state.incidentReports[reportId];
  if (!rep) {
    try {
      const snap = await get(ref(db, `incidentReports/${reportId}`));
      if (snap && snap.exists()) rep = snap.val();
    } catch (_) {}
  }
  const senderUid = rep ? (rep.senderUid || rep.userId || rep.uid) : null;
  await remove(ref(db, `incidentReports/${reportId}`));
  if (senderUid && validPathSegment(senderUid)) {
    try {
      await remove(ref(db, `users/${senderUid}/lastIncidentReportId`));
    } catch (e) {
      console.warn("Could not remove user lastIncidentReportId:", e);
    }
  }
  if (state.users) {
    for (const [uid, userVal] of Object.entries(state.users)) {
      if (userVal && userVal.lastIncidentReportId === reportId) {
        try {
          await remove(ref(db, `users/${uid}/lastIncidentReportId`));
        } catch (_) {}
      }
    }
  }
  if (state.selected && state.selected.type === "report" && state.selected.id === reportId) {
    state.selected = null;
  }
  showToast("Report deleted.");
}

function exportJson() {
  const payload = {
    exportedAt: new Date().toISOString(),
    users: state.users,
    rooms: state.rooms,
    userRooms: state.userRooms,
    incidentReports: state.incidentReports,
    admin_notifications: state.adminNotifications,
    supportChats: state.supportChats,
    aiChats: state.aiChats,
    sos_alerts: state.legacyAlerts
  };
  const blob = new Blob([JSON.stringify(payload, null, 2)], { type: "application/json" });
  const url = URL.createObjectURL(blob);
  const link = document.createElement("a");
  link.href = url;
  link.download = `ResQTap-admin-export-${Date.now()}.json`;
  document.body.appendChild(link);
  link.click();
  link.remove();
  URL.revokeObjectURL(url);
}

async function clearHistory() {
  const visibleHistoryCount = getNotificationHistory().length;
  if (!visibleHistoryCount) {
    showToast("No history logs to clear.");
    return;
  }

  if (!window.confirm(`Clear all ${visibleHistoryCount} history logs? This removes notification, bell, and SOS logs from Firebase.`)) return;

  const updates = {};
  entries(state.rooms).forEach(([roomId, room]) => {
    if (validPathSegment(roomId) && entries(asRecord(room).sosAlerts).length) {
      updates[`rooms/${roomId}/sosAlerts`] = null;
    }
    if (validPathSegment(roomId) && entries(asRecord(room).bells).length) {
      updates[`rooms/${roomId}/bells`] = null;
    }
  });
  if (entries(state.adminNotifications).length) {
    updates.admin_notifications = null;
    updates["broadcastNotifications/current"] = null;
  }
  updates["broadcastNotifications/history"] = null;
  if (entries(state.legacyAlerts).length) {
    updates.sos_alerts = null;
  }
  if (entries(state.adminAuditLogs).length) {
    updates.admin_audit_logs = null;
  }
  if (entries(state.callLogs).length) {
    updates.call_logs = null;
  }

  if (!Object.keys(updates).length) {
    showToast("No history logs to clear.");
    return;
  }

  await update(ref(db), updates);
  state.selected = null;
  state.notificationPage = 1;
  showToast("History cleared.");
}

function openClearDbModal() {
  if (!state.currentUser) {
    showToast("Log masuk sebagai Admin diperlukan.");
    return;
  }
  const modal = document.getElementById("clearDbModal");
  if (!modal) return;
  modal.classList.remove("hidden");
  if (window.lucide && window.lucide.createIcons) window.lucide.createIcons();
}

function closeClearDbModal() {
  const modal = document.getElementById("clearDbModal");
  if (!modal) return;
  modal.classList.add("hidden");
}

async function executeClearDatabase() {
  const confirmBtn = document.getElementById("clearDbConfirmBtn");
  const cancelBtn = document.getElementById("clearDbCancelBtn");
  const closeBtn = document.getElementById("clearDbCloseBtn");
  const originalConfirmHtml = confirmBtn ? confirmBtn.innerHTML : "";

  if (confirmBtn) {
    confirmBtn.disabled = true;
    confirmBtn.innerHTML = `<i data-lucide="loader-2" class="spin" aria-hidden="true"></i><span>Membersihkan DB & Auth...</span>`;
    if (window.lucide && window.lucide.createIcons) window.lucide.createIcons();
  }
  if (cancelBtn) cancelBtn.disabled = true;
  if (closeBtn) closeBtn.disabled = true;

  try {
    let authPurgedCount = 0;
    const authPurgedUids = new Set();

    // 1. Padam semua akaun bukan @resqtap daripada Firebase Authentication menggunakan Web Crypto Auth Bridge
    // SYARAT MUTLAK: Selagi authentication tak berjaya dipadam, akaun tidak akan dibuang
    const authResult = await purgeNonResqtapAuthAccounts();
    if (authResult && typeof authResult.deletedCount === "number") {
      authPurgedCount = authResult.deletedCount;
      if (Array.isArray(authResult.deletedUids)) {
        authResult.deletedUids.forEach((id) => authPurgedUids.add(id));
      }
    }

    // 2. Kumpul semua UID bukan @resqtap dari RTDB users, state.authUsers, & hasil Auth purge
    const users = state.users || {};
    const deleteUids = new Set(authPurgedUids);
    const resqtapUids = new Set();

    // Periksa dari state.authUsers (termasuk akaun Google yang tiada profil RTDB)
    (state.authUsers || []).forEach((u) => {
      const email = text(u && u.email).trim().toLowerCase();
      const uid = u.localId || u.uid;
      if (!uid) return;
      if (email.includes("@resqtap")) {
        resqtapUids.add(uid);
      } else {
        deleteUids.add(uid);
      }
    });

    // Periksa dari RTDB state.users
    entries(users).forEach(([uid, u]) => {
      const email = text(u && u.email).trim().toLowerCase();
      if (email.includes("@resqtap")) {
        resqtapUids.add(uid);
      } else {
        deleteUids.add(uid);
      }
    });

    // Ambil registeredEmails secara langsung dari database secara tepat
    let registeredEmailsMap = {};
    try {
      const emailsSnap = await get(ref(db, "registeredEmails"));
      if (emailsSnap.exists()) {
        registeredEmailsMap = emailsSnap.val() || {};
      }
    } catch (e) {
      console.warn("Could not fetch registeredEmails:", e);
    }

    let hasNonResqtapEmails = false;
    entries(registeredEmailsMap).forEach(([key]) => {
      const decoded = key.replace(/_at_/g, "@").replace(/_/g, ".");
      if (!decoded.toLowerCase().includes("@resqtap")) {
        hasNonResqtapEmails = true;
      }
    });

    if (deleteUids.size === 0 && !hasNonResqtapEmails && authPurgedCount === 0) {
      closeClearDbModal();
      showToast("Tiada akaun selain @resqtap untuk dipadam. Pangkalan data dan Authentication sudah bersih.");
      return;
    }

    const updates = {};
    const userNodes = [
      "users", "admins", "supportChats", "aiChats",
      "userNotifications", "notifications",
      "sos_history", "sos_alerts", "sos_status",
      "live_locations", "userFriends", "friendRequests",
      "sentRequests", "userRooms", "userCalls"
    ];

    deleteUids.forEach((uid) => {
      userNodes.forEach((node) => {
        updates[`${node}/${uid}`] = null;
      });
      updates[`admin_user_deletions/${uid}`] = {
        deletedBy: state.currentUser.uid,
        deletedAt: serverTimestamp(),
        status: "completed"
      };
    });

    // Padam registeredEmails bukan @resqtap
    entries(registeredEmailsMap).forEach(([key]) => {
      const decoded = key.replace(/_at_/g, "@").replace(/_/g, ".");
      if (!decoded.toLowerCase().includes("@resqtap")) {
        updates[`registeredEmails/${key}`] = null;
      }
    });

    // publicIds
    entries(state.publicIds || {}).forEach(([pid, val]) => {
      const targetUid = typeof val === "string" ? val : (val && val.uid ? val.uid : "");
      if (deleteUids.has(targetUid) || !resqtapUids.has(targetUid)) {
        updates[`publicIds/${pid}`] = null;
      }
    });

    // Rooms
    entries(state.rooms || {}).forEach(([roomId, room]) => {
      const r = asRecord(room);
      const creatorUid = text(r.creatorUid).trim();
      if (deleteUids.has(creatorUid)) {
        updates[`rooms/${roomId}`] = null;
      } else {
        deleteUids.forEach((uid) => {
          if (asRecord(r.members)[uid]) {
            updates[`rooms/${roomId}/members/${uid}`] = null;
          }
          if (asRecord(r.bells)[uid]) {
            updates[`rooms/${roomId}/bells/${uid}`] = null;
          }
        });
      }
    });

    // Calls
    entries(state.calls || {}).forEach(([callId, c]) => {
      if (c && (deleteUids.has(c.callerUid) || deleteUids.has(c.calleeUid))) {
        updates[`calls/${callId}`] = null;
      }
    });

    // Reports
    entries(state.reports || {}).forEach(([reportId, rep]) => {
      if (rep && deleteUids.has(rep.senderUid)) {
        updates[`incidentReports/${reportId}`] = null;
      }
    });

    // Tandakan admin_tasks/clear_database completed
    updates["admin_tasks/clear_database"] = {
      requestedBy: state.currentUser.uid,
      requestedAt: Date.now(),
      status: "completed",
      completedAt: serverTimestamp(),
      summary: `Pembersihan selesai: ${Math.max(deleteUids.size, authPurgedCount)} akaun dipadam.`
    };

    // Log client-side audit record
    const auditLogId = `audit_${Date.now()}`;
    const auditTimestamp = serverTimestamp();
    const finalCount = Math.max(deleteUids.size, authPurgedCount);
    updates[`admin_audit_logs/${auditLogId}`] = {
      id: auditLogId,
      type: "Database Reset",
      action: "clear_database",
      triggeredBy: (state.currentUser && (state.currentUser.email || state.currentUser.uid)) || "Admin Session",
      accountsDeleted: finalCount,
      roomsDeleted: 0,
      messagesRemoved: 0,
      sosAlertsRemoved: 0,
      callsRemoved: 0,
      details: `Sesi pembersihan database & auth: ${finalCount} akaun selain @resqtap telah dipadam sepenuhnya.`,
      timestamp: Date.now(),
      createdAt: auditTimestamp
    };

    await update(ref(db), updates);

    // Muat semula senarai Auth Users serta-merta agar UI dikemaskini
    await loadAuthUsers();

    closeClearDbModal();
    showToast(`Pangkalan Data & Firebase Auth dibersihkan! (${finalCount} akaun dipadam).`);
  } catch (err) {
    console.error("Gagal membersihkan database & auth:", err);
    showToast("Gagal membersihkan: " + (err.message || err));
  } finally {
    if (confirmBtn) {
      confirmBtn.disabled = false;
      confirmBtn.innerHTML = originalConfirmHtml;
      if (window.lucide && window.lucide.createIcons) window.lucide.createIcons();
    }
    if (cancelBtn) cancelBtn.disabled = false;
    if (closeBtn) closeBtn.disabled = false;
  }
}

function handleAction(button) {
  const action = button.dataset.action;
  const uid = button.dataset.uid || "";
  const chatUid = button.dataset.chatUid || "";
  const aiChatUid = button.dataset.aiChatUid || "";
  const room = button.dataset.room || "";
  const alert = button.dataset.alert || "";
  const notice = button.dataset.notice || "";
  const report = button.dataset.report || "";
  const status = button.dataset.status || "";
  const attachmentIndex = button.dataset.attachmentIndex || "";

  const run = async () => {
    if (action === "view-user") {
      const forceProfile = button.dataset.forceProfile === "true";
      const activeSos = !forceProfile && getActiveSosForUser(uid);
      if (activeSos) {
        state.selected = { type: "sos", roomId: activeSos.roomId, alertId: activeSos.key || activeSos.id };
      } else {
        state.selected = { type: "user", id: uid, forceProfile: true };
      }
    }
    if (action === "view-room") state.selected = { type: "room", id: room };
    if (action === "view-sos") state.selected = { type: "sos", roomId: room, alertId: alert };
    if (action === "view-report") state.selected = { type: "report", id: report };
    if (action === "view-report-attachment") {
      viewReportAttachment(report, attachmentIndex);
      return;
    }
    if (action === "close-attachment-viewer") {
      closeAttachmentViewer();
      return;
    }
    if (action === "call-sos") {
      startAdminCall(uid, button.dataset.name || "", "video");
      return;
    }
    if (action === "call-reporter") {
      startAdminCall(uid, button.dataset.name || "Reporter", "voice");
      return;
    }
    if (action === "select-chat") {
      if (state.selectedChatUid && state.selectedChatUid !== chatUid) clearLivechatAdminTyping(state.selectedChatUid);
      if (state.selectedChatUid !== chatUid) clearLivechatAttachment();
      state.selectedChatUid = chatUid;
      state.activeView = "livechat";
      try {
        await claimLivechatThread(chatUid);
      } catch (error) {
        console.error(error);
        showToast(error.message || "Unable to claim livechat.");
      }
    }
    if (action === "select-ai-chat") {
      state.selectedAiChatUid = aiChatUid;
      state.activeView = "aichat";
    }
    if (action === "select-sos-chat") {
      state.selectedSosSessionId = button.dataset.sessionId || "";
      state.activeView = "soslivechat";
      render();
      return;
    }
    if (action === "clear-detail") state.selected = null;
    if (action === "manage-sos-case") {
      sosAlarmSound.mute(alert);
      sosAlarmSound.stop();
      state.servedCases = state.servedCases || new Set();
      if (alert) state.servedCases.add(alert);
      const banner = document.getElementById("sosAlarmBanner");
      if (banner) banner.classList.add("hidden");
      await reserveSosCase(room, alert, uid, button.dataset.name || "Sender");
      updateSosAlarmSystem();

      const matchedAlert = getSosAlerts().find(
        (a) => (a.roomId === room || a.roomId === "DIRECT") && (a.key === alert || a.id === alert)
      ) || { key: alert, id: alert, roomId: room, senderUid: uid, senderName: button.dataset.name || "Sender", data: {} };

      await focusSosSenderOnMap(matchedAlert);
      openCaseModal(room, alert, uid, button.dataset.name || "Sender");
      return;
    }
    if (action === "close-case-modal") {
      closeCaseModal();
      return;
    }
    if (action === "save-case-progress") {
      await saveCaseProgress();
      return;
    }
    if (action === "cancel-sos") await cancelSos(room, alert);
    if (action === "set-report-status") await setReportStatus(report, status);
    if (action === "delete-report") await deleteReport(report);
    if (action === "delete-notification") await deleteNotification(notice);
    if (action === "remove-member") await removeMember(room, uid);
    if (action === "delete-user") await deleteUser(uid);
    if (action === "delete-room") await deleteRoom(room);
    if (action === "revoke-admin") await revokeAdmin(uid);
    if (action === "toggle-highlight") {
      const hlId = button.dataset.highlightId;
      const curActive = button.dataset.active === "true";
      if (hlId) {
        await update(ref(db, `highlights/${hlId}`), { active: !curActive });
        try {
          await supabase.from("highlights").update({ active: !curActive }).eq("id", hlId);
        } catch (sErr) {
          console.warn("[SUPABASE_HL_TOGGLE_ERR]", sErr);
        }
        showToast(curActive ? "Highlight hidden from mobile." : "Highlight active on mobile.");
      }
    }
    if (action === "edit-highlight") {
      const hlId = button.dataset.highlightId;
      const item = state.highlights[hlId];
      if (item) {
        const titleInput = document.getElementById("hlTitleInput");
        const actionUrlInput = document.getElementById("hlActionUrlInput");
        const orderInput = document.getElementById("hlOrderInput");
        const urlInput = document.getElementById("hlImageUrlInput");
        const fileInput = document.getElementById("hlImageFileInput");
        const previewImg = document.getElementById("hlImagePreview");
        const noPreview = document.getElementById("hlNoPreviewBox");
        const saveBtn = document.getElementById("saveHighlightBtn");

        if (titleInput) titleInput.value = item.title || "";
        if (actionUrlInput) actionUrlInput.value = item.actionUrl || "";
        if (orderInput) orderInput.value = item.order || 1;
        if (fileInput) fileInput.value = "";

        if (urlInput) {
          urlInput.value = (item.imageUrl && !item.imageUrl.startsWith("data:")) ? item.imageUrl : "";
        }

        if (previewImg && item.imageUrl) {
          previewImg.src = item.imageUrl;
          previewImg.style.display = "block";
          if (noPreview) noPreview.style.display = "none";
        }

        const form = document.getElementById("highlightCreatorForm");
        if (form) {
          form.dataset.editingId = hlId;
          form.dataset.currentImageUrl = item.imageUrl || "";
        }

        if (saveBtn) {
          saveBtn.innerHTML = `${icon("check")}<span>Update Highlight Banner</span>`;
        }

        const view = document.getElementById("highlightsView");
        if (view) {
          view.scrollIntoView({ behavior: "smooth", block: "start" });
        }
        showToast(`Editing banner: ${item.title || "Untitled"}`);
      }
    }
    if (action === "delete-highlight") {
      const hlId = button.dataset.highlightId;
      if (hlId && window.confirm("Delete this highlight banner permanently?")) {
        await remove(ref(db, `highlights/${hlId}`));
        try {
          await supabase.from("highlights").delete().eq("id", hlId);
        } catch (sErr) {
          console.warn("[SUPABASE_HL_DEL_ERR]", sErr);
        }
        delete state.highlights[hlId];
        showToast("Highlight banner deleted.");
      }
    }
    if (action === "seed-default-highlights") {
      await seedDefaultHighlights();
    }
    render();
  };

  run().catch((error) => {
    console.error(error);
    showToast(error.message || "Action failed.");
  });
}

function canResizeDetailPanel() {
  return window.matchMedia("(min-width: 1321px)").matches;
}

function detailWidthFromPointer(clientX) {
  const shellRect = els.appShell.getBoundingClientRect();
  return shellRect.right - clientX;
}

function handleDetailResizeStart(event) {
  const handle = event.target.closest(".detail-resize-handle");
  if (!handle || !canResizeDetailPanel()) return;

  event.preventDefault();
  document.body.classList.add("detail-resizing");
  handle.classList.add("is-dragging");
  setDetailWidth(detailWidthFromPointer(event.clientX));

  try {
    handle.setPointerCapture(event.pointerId);
  } catch (error) {
    console.warn(error);
  }

  const resize = (moveEvent) => {
    setDetailWidth(detailWidthFromPointer(moveEvent.clientX));
  };

  const stopResize = () => {
    document.body.classList.remove("detail-resizing");
    handle.classList.remove("is-dragging");
    window.removeEventListener("pointermove", resize);
    window.removeEventListener("pointerup", stopResize);
    window.removeEventListener("pointercancel", stopResize);
    try {
      handle.releasePointerCapture(event.pointerId);
    } catch (error) {
      console.warn(error);
    }
  };

  window.addEventListener("pointermove", resize);
  window.addEventListener("pointerup", stopResize, { once: true });
  window.addEventListener("pointercancel", stopResize, { once: true });
}

function handleDetailResizeKeydown(event) {
  const handle = event.target.closest(".detail-resize-handle");
  if (!handle || !canResizeDetailPanel()) return;

  const step = event.shiftKey ? 72 : 32;
  if (event.key === "ArrowLeft") {
    event.preventDefault();
    setDetailWidth(state.detailWidth + step);
  }
  if (event.key === "ArrowRight") {
    event.preventDefault();
    setDetailWidth(state.detailWidth - step);
  }
  if (event.key === "Home") {
    event.preventDefault();
    setDetailWidth(DETAIL_PANEL_MIN_WIDTH);
  }
  if (event.key === "End") {
    event.preventDefault();
    setDetailWidth(DETAIL_PANEL_MAX_WIDTH);
  }
}

function bindEvents() {
  els.loginForm.addEventListener("submit", async (event) => {
    event.preventDefault();
    let email = els.emailInput.value.trim();
    const password = els.passwordInput.value;
    els.authMessage.textContent = "";
    els.loginButton.disabled = true;
    const btnText = els.loginButton.querySelector(".btn-text");
    const originalText = btnText ? btnText.textContent : "Login";
    if (btnText) btnText.textContent = "Logging in...";

    if (email && !email.includes("@")) {
      email = email + "@resqtap.com";
    }

    try {
      document.documentElement.classList.remove("admin-route-loading");
      await signInWithEmailAndPassword(auth, email, password);
    } catch (error) {
      console.error("Admin login error:", error);
      let msg = error.message || "Unable to sign in. Please verify your credentials.";
      if (error.code === "auth/invalid-credential" || error.code === "auth/user-not-found" || error.code === "auth/wrong-password") {
        msg = "Email atau kata laluan tidak sah / Invalid email or password.";
      } else if (error.code === "auth/invalid-email") {
        msg = "Format email tidak sah / Invalid email format.";
      } else if (error.code === "auth/too-many-requests") {
        msg = "Terlalu banyak percubaan gagal. Sila cuba sebentar lagi / Too many failed attempts.";
      }
      els.authMessage.textContent = msg;
    } finally {
      els.loginButton.disabled = false;
      if (btnText) btnText.textContent = originalText;
    }
  });

  const togglePasswordBtn = document.getElementById("togglePasswordBtn");
  if (togglePasswordBtn) {
    togglePasswordBtn.addEventListener("click", (e) => {
      e.preventDefault();
      const pwdInput = els.passwordInput;
      if (!pwdInput) return;
      const isPassword = pwdInput.getAttribute("type") === "password";
      pwdInput.setAttribute("type", isPassword ? "text" : "password");
      const iconEl = togglePasswordBtn.querySelector("[data-lucide]");
      if (iconEl) {
        iconEl.setAttribute("data-lucide", isPassword ? "eye-off" : "eye");
        refreshIcons();
      }
    });
  }

  const handleSignOut = async () => {
    try {
      localStorage.removeItem("resqtap_admin_session");
      document.documentElement.classList.remove("has-admin-session");
    } catch (e) {}
    await signOut(auth);
  };

  if (els.signOutButton) {
    els.signOutButton.addEventListener("click", handleSignOut);
  }
  if (els.sidebarSignOutBtn && els.sidebarSignOutBtn !== els.signOutButton) {
    els.sidebarSignOutBtn.addEventListener("click", handleSignOut);
  }
  if (els.deniedSignOut) {
    els.deniedSignOut.addEventListener("click", handleSignOut);
  }
  if (els.adminProfileSignOutBtn) {
    els.adminProfileSignOutBtn.addEventListener("click", handleSignOut);
  }
  if (els.adminPageSignOutBtn) {
    els.adminPageSignOutBtn.addEventListener("click", handleSignOut);
  }

  // Dark Mode Toggle Logic
  function applyTheme(theme) {
    const isDark = theme === "dark";
    document.documentElement.classList.toggle("dark-mode", isDark);
    document.body.classList.toggle("dark-mode", isDark);
    try {
      localStorage.setItem("resqtap_theme", theme);
    } catch (e) {}

    const icon = document.getElementById("themeToggleIcon");
    const text = document.getElementById("themeToggleText");
    if (icon) {
      icon.setAttribute("data-lucide", isDark ? "sun" : "moon");
    }
    if (text) {
      text.textContent = isDark ? "Light Mode" : "Dark Mode";
    }
    refreshIcons();
  }

  const themeToggleBtn = document.getElementById("themeToggleBtn");
  if (themeToggleBtn) {
    themeToggleBtn.addEventListener("click", () => {
      const isDark = document.documentElement.classList.contains("dark-mode") || document.body.classList.contains("dark-mode");
      applyTheme(isDark ? "light" : "dark");
    });
  }

  // Initialize theme on start
  try {
    const currentSavedTheme = localStorage.getItem("resqtap_theme") || (document.documentElement.classList.contains("dark-mode") ? "dark" : "light");
    applyTheme(currentSavedTheme);
  } catch (e) {}

  els.globalSearch.addEventListener("input", () => {
    state.search = els.globalSearch.value;
    state.notificationPage = 1;
    render();
  });

  els.dashboardRangeSelect.addEventListener("change", () => {
    state.dashboardRange = els.dashboardRangeSelect.value || "today";
    render();
  });

  els.notificationMessageInput.addEventListener("input", updateNotificationComposer);
  els.notificationTitleInput.addEventListener("input", updateNotificationComposer);
  els.clearNotificationButton.addEventListener("click", clearNotificationComposer);
  els.noticeForm.addEventListener("submit", handleNoticeSubmit);
  els.notificationPageSizeSelect.addEventListener("change", () => {
    state.notificationPageSize = Number(els.notificationPageSizeSelect.value) || 10;
    state.notificationPage = 1;
    renderNotices();
    refreshIcons();
  });
  els.notificationCategoryTabs.addEventListener("click", (event) => {
    const tab = event.target.closest("[data-notification-category]");
    if (!tab) return;
    state.notificationCategory = tab.dataset.notificationCategory || "all";
    state.notificationPage = 1;
    renderNotices();
    refreshIcons();
  });
  els.notificationPrevPageButton.addEventListener("click", () => {
    state.notificationPage = Math.max(1, state.notificationPage - 1);
    renderNotices();
    refreshIcons();
  });
  els.notificationNextPageButton.addEventListener("click", () => {
    state.notificationPage += 1;
    renderNotices();
    refreshIcons();
  });

  if (els.userFilterPills) {
    els.userFilterPills.addEventListener("click", (event) => {
      const btn = event.target.closest("button[data-user-filter]");
      if (!btn) return;
      state.userFilter = btn.dataset.userFilter || "all";
      els.userFilterPills.querySelectorAll("button").forEach((b) => {
        b.classList.toggle("is-active", b === btn);
      });
      renderUsers();
      refreshIcons();
    });
  }

  els.livechatReplyInput.addEventListener("input", handleLivechatTypingInput);
  els.livechatReplyInput.addEventListener("blur", () => clearLivechatAdminTyping());
  els.livechatReplyForm.addEventListener("submit", sendAdminLivechatReply);
  els.livechatAttachButton.addEventListener("click", (event) => {
    event.stopPropagation();
    setLivechatAttachMenu(els.livechatAttachMenu.classList.contains("hidden"));
  });
  els.livechatAttachMenu.addEventListener("click", (event) => {
    event.stopPropagation();
    const option = event.target.closest("[data-livechat-attach]");
    if (!option) return;
    chooseLivechatAttachment(option.dataset.livechatAttach || "");
  });
  els.livechatAttachmentInput.addEventListener("change", handleLivechatAttachmentSelected);
  els.livechatAttachmentLabel.addEventListener("click", (event) => {
    const clear = event.target.closest("[data-livechat-clear-attachment]");
    if (clear) clearLivechatAttachment();
  });
  els.resolveChatButton.addEventListener("click", () => {
    resolveSelectedChat().catch((error) => {
      console.error(error);
      showToast(error.message || "Unable to resolve livechat.");
    });
  });
  els.deleteChatButton.addEventListener("click", () => {
    deleteSelectedResolvedChat().catch((error) => {
      console.error(error);
      showToast(error.message || "Unable to delete livechat.");
    });
  });
  if (els.deleteAiChatButton) {
    els.deleteAiChatButton.addEventListener("click", () => {
      deleteSelectedAiChat().catch((error) => {
        console.error(error);
        showToast(error.message || "Unable to reset AI chat.");
      });
    });
  }

  if (els.soslivechatReplyInput) {
    els.soslivechatReplyInput.addEventListener("input", () => {
      if (els.soslivechatReplyButton) {
        els.soslivechatReplyButton.disabled = !els.soslivechatReplyInput.value.trim() || state.soslivechatSending;
      }
    });
  }
  if (els.soslivechatSearchInput) {
    els.soslivechatSearchInput.addEventListener("input", renderSosLivechat);
  }
  if (els.soslivechatReplyForm) {
    els.soslivechatReplyForm.addEventListener("submit", handleSosLivechatSubmit);
  }

  if (els.exportButton) {
    els.exportButton.addEventListener("click", exportJson);
  }
  if (els.clearHistoryButton) {
    els.clearHistoryButton.addEventListener("click", () => {
      clearHistory().catch((error) => {
        console.error(error);
        showToast(error.message || "Unable to clear history logs.");
      });
    });
  }
  if (els.clearDatabaseButton) {
    els.clearDatabaseButton.addEventListener("click", openClearDbModal);
  }

  const clearDbBackdrop = document.getElementById("clearDbBackdrop");
  const clearDbCloseBtn = document.getElementById("clearDbCloseBtn");
  const clearDbCancelBtn = document.getElementById("clearDbCancelBtn");
  const clearDbConfirmBtn = document.getElementById("clearDbConfirmBtn");

  if (clearDbBackdrop) clearDbBackdrop.addEventListener("click", closeClearDbModal);
  if (clearDbCloseBtn) clearDbCloseBtn.addEventListener("click", closeClearDbModal);
  if (clearDbCancelBtn) clearDbCancelBtn.addEventListener("click", closeClearDbModal);
  if (clearDbConfirmBtn) {
    clearDbConfirmBtn.addEventListener("click", () => {
      executeClearDatabase().catch((error) => {
        console.error(error);
        showToast(error.message || "Gagal membersihkan database.");
      });
    });
  }

  window.addEventListener("keydown", (e) => {
    if (e.key === "Escape") {
      closeClearDbModal();
    }
  });

  if (els.grantAdminForm) {
    els.grantAdminForm.addEventListener("submit", (event) => {
      event.preventDefault();
      grantAdmin(els.adminUidInput.value.trim()).catch((error) => {
        console.error(error);
        showToast(error.message || "Unable to grant admin access.");
      });
    });
  }

  // Highlight Form Listeners
  const hlForm = document.getElementById("highlightForm");
  const hlFile = document.getElementById("hlImageFileInput");
  const hlUrl = document.getElementById("hlImageUrlInput");
  const hlPreview = document.getElementById("hlImagePreview");
  const hlNoPreview = document.getElementById("hlNoPreviewBox") || document.getElementById("hlNoPreviewText");

  if (hlFile) {
    hlFile.addEventListener("change", async () => {
      const file = hlFile.files && hlFile.files[0];
      if (file) {
        try {
          const dataUrl = await imageFileToInlineDataUrl(file);
          if (hlPreview) {
            hlPreview.src = dataUrl;
            hlPreview.style.display = "block";
            if (hlNoPreview) hlNoPreview.style.display = "none";
          }
        } catch (e) {
          console.warn(e);
        }
      }
    });
  }

  if (hlUrl) {
    hlUrl.addEventListener("input", () => {
      const val = hlUrl.value.trim();
      if (val && hlPreview) {
        hlPreview.src = val;
        hlPreview.style.display = "block";
        if (hlNoPreview) hlNoPreview.style.display = "none";
      }
    });
  }

  if (hlForm) {
    hlForm.addEventListener("submit", async (e) => {
      e.preventDefault();
      const title = document.getElementById("hlTitleInput").value.trim();
      const actionUrl = document.getElementById("hlActionUrlInput").value.trim();
      const order = Number(document.getElementById("hlOrderInput").value) || 1;
      const editingId = hlForm.dataset.editingId;
      let imageUrl = hlUrl ? hlUrl.value.trim() : "";

      if (hlFile && hlFile.files && hlFile.files[0]) {
        try {
          imageUrl = await imageFileToInlineDataUrl(hlFile.files[0]);
        } catch (err) {
          showToast("Error processing image file.");
          return;
        }
      } else if (!imageUrl && editingId && hlForm.dataset.currentImageUrl) {
        imageUrl = hlForm.dataset.currentImageUrl;
      }

      if (!imageUrl) {
        showToast("Please provide an image URL or upload an image file.");
        return;
      }

      try {
        const targetId = editingId || `hl_${Date.now()}`;
        if (editingId) {
          await update(ref(db, `highlights/${editingId}`), {
            title,
            imageUrl,
            actionUrl,
            order,
            updatedAt: serverTimestamp()
          });
          delete hlForm.dataset.editingId;
          delete hlForm.dataset.currentImageUrl;
          const saveBtn = document.getElementById("saveHighlightBtn");
          if (saveBtn) saveBtn.innerHTML = `${icon("plus")}<span>Add Highlight Banner</span>`;
          showToast("Highlight banner updated successfully!");
        } else {
          const newRef = push(ref(db, "highlights"));
          await set(newRef, {
            id: newRef.key || targetId,
            title,
            imageUrl,
            actionUrl,
            order,
            active: true,
            createdAt: serverTimestamp()
          });
          showToast("Highlight banner added successfully!");
        }

        // Persist to Supabase
        try {
          await supabase.from("highlights").upsert({
            id: targetId,
            title: title,
            image_url: imageUrl,
            action_url: actionUrl,
            display_order: order,
            active: true
          });
          syncFromSupabase();
        } catch (sErr) {
          console.warn("[SUPABASE_HL_SAVE_ERR]", sErr);
        }

        hlForm.reset();
        if (hlPreview) hlPreview.style.display = "none";
        if (hlNoPreview) hlNoPreview.style.display = "block";
      } catch (err) {
        console.error(err);
        showToast(err.message || "Failed to save highlight banner.");
      }
    });
  }

  els.detailPanel.addEventListener("pointerdown", handleDetailResizeStart);
  els.detailPanel.addEventListener("keydown", handleDetailResizeKeydown);
  window.addEventListener("resize", () => {
    if (state.selected) setDetailWidth(state.detailWidth);
    setLivechatAttachMenu(false);
  });
  window.addEventListener("keydown", (event) => {
    if (event.key === "Escape") {
      setLivechatAttachMenu(false);
      closeAttachmentViewer();
    }
  });

  try {
    localStorage.removeItem("resqtap_admin_sidebar_collapsed");
  } catch (e) {}
  if (els.appShell) {
    els.appShell.classList.remove("sidebar-collapsed");
  }

  function toggleMobileSidebar(force) {
    if (!els.appShell) return;
    const shouldOpen = typeof force === "boolean" ? force : !els.appShell.classList.contains("sidebar-open");
    els.appShell.classList.toggle("sidebar-open", shouldOpen);
    if (els.sidebarBackdrop) els.sidebarBackdrop.classList.toggle("is-open", shouldOpen);
  }

  function closeMobileSidebar() {
    toggleMobileSidebar(false);
  }

  if (els.sidebarToggleBtn) {
    els.sidebarToggleBtn.addEventListener("click", () => toggleMobileSidebar());
  }
  if (els.sidebarCloseBtn) {
    els.sidebarCloseBtn.addEventListener("click", () => closeMobileSidebar());
  }
  if (els.sidebarBackdrop) {
    els.sidebarBackdrop.addEventListener("click", () => closeMobileSidebar());
  }

  document.body.addEventListener("click", (event) => {
    if (!event.target.closest(".livechat-attach-wrap")) {
      setLivechatAttachMenu(false);
    }

    const nav = event.target.closest("[data-view]");
    if (nav) {
      setActiveView(nav.dataset.view);
      closeMobileSidebar();
      return;
    }

    const jump = event.target.closest("[data-view-jump]");
    if (jump) {
      setActiveView(jump.dataset.viewJump);
      closeMobileSidebar();
      return;
    }

    const action = event.target.closest("[data-action]");
    if (action) handleAction(action);
  });

  if (els.adminProfileForm) {
    els.adminProfileForm.addEventListener("submit", async (event) => {
      event.preventDefault();
      if (!state.currentUser) {
        showToast("Session expired. Please log in again.");
        return;
      }
      const uid = state.currentUser.uid;
      const newName = (els.adminProfileName ? els.adminProfileName.value : "").trim();
      const newPhone = (els.adminProfilePhone ? els.adminProfilePhone.value : "").trim();
      if (!newName) {
        showToast("Please enter a valid display name.");
        return;
      }

      const saveBtn = els.saveAdminProfileBtn;
      if (saveBtn) {
        saveBtn.disabled = true;
        saveBtn.innerHTML = `${icon("loader-2")}<span>Saving...</span>`;
      }

      try {
        if (auth.currentUser) {
          await updateProfile(auth.currentUser, { displayName: newName });
        }

        const adminUpdate = {
          name: newName,
          phone: newPhone,
          updatedAt: serverTimestamp()
        };
        await update(ref(db, `admins/${uid}`), adminUpdate);

        try {
          await remove(ref(db, `users/${uid}`));
          if (state.users) delete state.users[uid];
        } catch (_) {}

        try {
          await supabase.from("profiles").update({
            full_name: newName,
            phone: newPhone,
            updated_at: new Date().toISOString()
          }).eq("id", uid);
        } catch (_) {}

        if (typeof state.admins[uid] !== "object" || !state.admins[uid]) {
          state.admins[uid] = { active: true };
        }
        state.admins[uid].name = newName;
        state.admins[uid].phone = newPhone;
        if (state.users && state.users[uid]) {
          delete state.users[uid];
        }

        showToast("Profile details updated successfully!");
        render();
      } catch (err) {
        console.error("Failed to update profile:", err);
        showToast("Failed to save: " + (err.message || "Unknown error"));
      } finally {
        if (saveBtn) {
          saveBtn.disabled = false;
          saveBtn.innerHTML = `${icon("save")}<span>Save Changes</span>`;
          refreshIcons();
        }
      }
    });
  }

  if (els.copyAdminUidBtn) {
    els.copyAdminUidBtn.addEventListener("click", async () => {
      const uid = (els.adminProfileUid ? els.adminProfileUid.value : "") || (state.currentUser ? state.currentUser.uid : "");
      if (!uid) return;
      try {
        await navigator.clipboard.writeText(uid);
        showToast("UID copied to clipboard!");
      } catch (_) {
        showToast("UID: " + uid);
      }
    });
  }

  if (els.toggleNewPasswordBtn && els.adminNewPassword) {
    els.toggleNewPasswordBtn.addEventListener("click", () => {
      const isPwd = els.adminNewPassword.type === "password";
      els.adminNewPassword.type = isPwd ? "text" : "password";
      const iconEl = els.toggleNewPasswordBtn.querySelector("[data-lucide]");
      if (iconEl) {
        iconEl.setAttribute("data-lucide", isPwd ? "eye-off" : "eye");
        refreshIcons();
      }
    });
  }

  if (els.toggleConfirmPasswordBtn && els.adminConfirmPassword) {
    els.toggleConfirmPasswordBtn.addEventListener("click", () => {
      const isPwd = els.adminConfirmPassword.type === "password";
      els.adminConfirmPassword.type = isPwd ? "text" : "password";
      const iconEl = els.toggleConfirmPasswordBtn.querySelector("[data-lucide]");
      if (iconEl) {
        iconEl.setAttribute("data-lucide", isPwd ? "eye-off" : "eye");
        refreshIcons();
      }
    });
  }

  if (els.adminPasswordForm) {
    els.adminPasswordForm.addEventListener("submit", async (event) => {
      event.preventDefault();
      if (!auth.currentUser) {
        showToast("Session expired. Please log in again.");
        return;
      }
      const newPwd = els.adminNewPassword ? els.adminNewPassword.value : "";
      const confirmPwd = els.adminConfirmPassword ? els.adminConfirmPassword.value : "";
      if (newPwd.length < 6) {
        showToast("Password must be at least 6 characters.");
        return;
      }
      if (newPwd !== confirmPwd) {
        showToast("Passwords do not match. Please verify.");
        return;
      }

      const pwdBtn = els.saveAdminPasswordBtn;
      if (pwdBtn) {
        pwdBtn.disabled = true;
        pwdBtn.innerHTML = `${icon("loader-2")}<span>Updating...</span>`;
      }

      try {
        await updatePassword(auth.currentUser, newPwd);
        if (els.adminNewPassword) els.adminNewPassword.value = "";
        if (els.adminConfirmPassword) els.adminConfirmPassword.value = "";
        showToast("Password updated successfully!");
      } catch (err) {
        console.error("Password update error:", err);
        if (err.code === "auth/requires-recent-login") {
          showToast("This operation requires recent authentication. Please sign out and log back in to change password.");
        } else {
          showToast("Error updating password: " + (err.message || "Failed"));
        }
      } finally {
        if (pwdBtn) {
          pwdBtn.disabled = false;
          pwdBtn.innerHTML = `${icon("key-round")}<span>Update Password</span>`;
          refreshIcons();
        }
      }
    });
  }

  if (els.adminPhotoFileInput) {
    els.adminPhotoFileInput.addEventListener("change", async (event) => {
      const file = event.target.files && event.target.files[0];
      if (!file || !state.currentUser) return;

      if (!file.type.startsWith("image/")) {
        showToast("Please choose an image file (PNG, JPG, WebP).");
        return;
      }
      if (file.size > 5 * 1024 * 1024) {
        showToast("Photo must be smaller than 5MB.");
        return;
      }

      const uid = state.currentUser.uid;
      const uploadLabel = els.adminUploadBtnLabel;
      if (uploadLabel) uploadLabel.textContent = "Uploading...";

      try {
        const inlineUrl = await imageFileToInlineDataUrl(file);
        let finalPhotoUrl = inlineUrl;

        try {
          const sRef = storageRef(getStorage(app), `admin_avatars/${uid}_${Date.now()}.jpg`);
          const res = await fetch(inlineUrl);
          const blob = await res.blob();
          const snapshot = await uploadBytes(sRef, blob, { contentType: "image/jpeg" });
          finalPhotoUrl = await getDownloadURL(snapshot.ref);
        } catch (stgErr) {
          console.warn("[STORAGE_FALLBACK] Using inline photo:", stgErr);
        }

        if (auth.currentUser) {
          await updateProfile(auth.currentUser, { photoURL: finalPhotoUrl });
        }

        await update(ref(db, `admins/${uid}`), {
          photoUrl: finalPhotoUrl,
          updatedAt: serverTimestamp()
        });

        try {
          await remove(ref(db, `users/${uid}`));
          if (state.users) delete state.users[uid];
        } catch (_) {}

        try {
          await supabase.from("profiles").update({
            photo_url: finalPhotoUrl,
            updated_at: new Date().toISOString()
          }).eq("id", uid);
        } catch (_) {}

        if (state.admins[uid]) state.admins[uid].photoUrl = finalPhotoUrl;
        if (state.users && state.users[uid]) delete state.users[uid];

        showToast("Profile picture updated successfully!");
        render();
      } catch (err) {
        console.error("Photo upload error:", err);
        showToast("Failed to upload: " + (err.message || "Error"));
      } finally {
        if (uploadLabel) uploadLabel.textContent = "Change Picture";
        els.adminPhotoFileInput.value = "";
      }
    });
  }

  if (els.adminRemovePhotoBtn) {
    els.adminRemovePhotoBtn.addEventListener("click", async () => {
      if (!state.currentUser) return;
      const uid = state.currentUser.uid;
      try {
        if (auth.currentUser) {
          await updateProfile(auth.currentUser, { photoURL: "" });
        }
        await update(ref(db, `admins/${uid}`), {
          photoUrl: "",
          updatedAt: serverTimestamp()
        });
        try {
          await remove(ref(db, `users/${uid}`));
          if (state.users) delete state.users[uid];
        } catch (_) {}
        try {
          await supabase.from("profiles").update({
            photo_url: "",
            updated_at: new Date().toISOString()
          }).eq("id", uid);
        } catch (_) {}

        if (state.admins[uid]) state.admins[uid].photoUrl = "";
        if (state.users[uid]) state.users[uid].photoUrl = "";

        showToast("Profile picture removed.");
        render();
      } catch (err) {
        console.error("Photo remove error:", err);
        showToast("Failed to remove photo: " + (err.message || "Error"));
      }
    });
  }
}

window.addEventListener("load", refreshIcons);

onAuthStateChanged(auth, async (user) => {
  if (!isAdminRoute()) {
    showPublicSite();
    return;
  }

  enterAdminRoute();
  cleanupDataListeners();
  state.currentUser = user;
  state.selected = null;
  state.selectedChatUid = "";
  state.selectedAiChatUid = "";

  if (!user) {
    setScreen("auth");
    state.users = {};
    state.rooms = {};
    state.roomTombstones = {};
    state.userRooms = {};
    state.admins = {};
    state.legacyAlerts = {};
    state.incidentReports = {};
    state.adminNotifications = {};
    state.supportChats = {};
    state.aiChats = {};
    state.selectedChatUid = "";
    state.selectedAiChatUid = "";
    state.dashboardSignature = "";
    state.dashboardMetricValues = {};
    return;
  }

  try {
    if (!await isCurrentUserAdmin(user.uid)) {
      els.deniedUid.textContent = `UID: ${user.uid}`;
      els.adminPathCode.textContent = `admins/${user.uid}/active = true`;
      setScreen("denied");
      return;
    }

    setScreen("app");
    startDataListeners();
  } catch (error) {
    els.deniedUid.textContent = `UID: ${user.uid}`;
    els.adminPathCode.textContent = `admins/${user.uid}/active = true`;
    setScreen("denied");
    showToast(error.message || "Unable to verify admin access.");
  }
});

bindEvents();

let vcPeerConnection = null;
let vcLocalStream = null;
let vcRemoteStream = null;
let vcCurrentCallId = null;
let vcTargetUid = null;
let vcCurrentCallType = "video";
let vcUnsubscribers = [];
let vcActiveCallLogId = null;
let vcActiveCallStartTime = null;

const elsVc = {
  modal: document.getElementById("videoCallModal"),
  status: document.getElementById("videoCallStatus"),
  overlay: document.getElementById("videoCallOverlay"),
  localVideo: document.getElementById("localVideo"),
  remoteVideo: document.getElementById("remoteVideo"),
  endBtn: document.getElementById("vcEndBtn"),
  micBtn: document.getElementById("vcMicBtn"),
  camBtn: document.getElementById("vcCamBtn"),
  speakerBtn: document.getElementById("vcSpeakerBtn"),
  moreBtn: document.getElementById("vcMoreBtn"),
  bottomSheet: document.getElementById("vcBottomSheet"),
  shareScreenBtn: document.getElementById("vcShareScreenBtn"),
  sendMessageBtn: document.getElementById("vcSendMessageBtn"),
  localMuteIndicator: document.getElementById("localMuteIndicator"),
  remoteMuteIndicator: document.getElementById("remoteMuteIndicator"),
  localCamOffIndicator: document.getElementById("localCamOffIndicator"),
  remoteCamOffIndicator: document.getElementById("remoteCamOffIndicator"),
  minimizeBtn: document.getElementById("vcMinimizeBtn"),
  floatingAvatar: document.getElementById("vcFloatingAvatar"),
  floatingName: document.getElementById("vcFloatingName"),
  voicePanel: document.getElementById("voiceCallPanel"),
  voiceAvatar: document.getElementById("voiceCallAvatar"),
  voiceName: document.getElementById("voiceCallName"),
  voiceLabel: document.getElementById("voiceCallLabel")
};

let isVcMicEnabled = true;
let isVcCamEnabled = true;
async function startAdminCall(uid, name, callType = "video", existingCallLogId = null) {
  if (vcPeerConnection) endAdminCall();

  // Pastikan modal kemas kini kes SOS dan pilihan panggilan ditutup serta-merta
  closeCaseModal();
  closeCaseCallOptions();
  const caseModalEl = document.getElementById("sosCaseUpdateModal");
  if (caseModalEl) caseModalEl.classList.add("hidden");
  const caseOptionsEl = document.getElementById("caseCallOptionsModal");
  if (caseOptionsEl) caseOptionsEl.classList.add("hidden");
  const incomingAlertEl = document.getElementById("adminIncomingCallAlert");
  if (incomingAlertEl) incomingAlertEl.classList.add("hidden");

  const isVoiceCall = callType === "voice";
  const displayName = name || "User";
  vcTargetUid = uid;
  vcCurrentCallType = isVoiceCall ? "voice" : "video";
  elsVc.status.textContent = "Calling " + displayName.toUpperCase() + "...";
  elsVc.floatingName.textContent = displayName;
  if (elsVc.voiceName) elsVc.voiceName.textContent = displayName;
  if (elsVc.voiceLabel) elsVc.voiceLabel.textContent = isVoiceCall ? "Voice call" : "Video call";

  if (existingCallLogId) {
    vcActiveCallLogId = existingCallLogId;
    vcActiveCallStartTime = Date.now();
  } else {
    const logId = `call_${Date.now()}`;
    vcActiveCallLogId = logId;
    vcActiveCallStartTime = Date.now();
    update(ref(db, `call_logs/${logId}`), {
      id: logId,
      callType: vcCurrentCallType,
      callerUid: state.currentUser ? state.currentUser.uid : "admin",
      callerName: "ResQTap Support (Admin)",
      calleeUid: uid,
      calleeName: displayName,
      status: "outgoing",
      roomId: "-",
      timestamp: Date.now(),
      source: "admin_outgoing_call"
    }).catch(() => {});
  }

  const userPhoto = state.users?.[uid]?.photoUrl;
  if (userPhoto) {
    elsVc.floatingAvatar.style.backgroundImage = `url(${userPhoto})`;
    elsVc.floatingAvatar.textContent = "";
    if (elsVc.voiceAvatar) {
      elsVc.voiceAvatar.style.backgroundImage = `url(${userPhoto})`;
      elsVc.voiceAvatar.textContent = "";
    }
  } else {
    elsVc.floatingAvatar.style.backgroundImage = "none";
    elsVc.floatingAvatar.textContent = displayName.charAt(0).toUpperCase();
    if (elsVc.voiceAvatar) {
      elsVc.voiceAvatar.style.backgroundImage = "none";
      elsVc.voiceAvatar.textContent = displayName.charAt(0).toUpperCase();
    }
  }

  elsVc.remoteVideo.srcObject = null;
  elsVc.localVideo.srcObject = null;
  elsVc.overlay.classList.remove("hidden");
  elsVc.modal.classList.remove("hidden");
  elsVc.modal.classList.toggle("voice-mode", isVoiceCall);
  if (elsVc.voicePanel) elsVc.voicePanel.classList.toggle("hidden", !isVoiceCall);
  elsVc.bottomSheet.classList.remove("show");

  isVcMicEnabled = true;
  isVcCamEnabled = !isVoiceCall;

  const micOn = elsVc.micBtn.querySelector(".icon-on");
  const micOff = elsVc.micBtn.querySelector(".icon-off");
  if (micOn) micOn.classList.remove("hidden");
  if (micOff) micOff.classList.add("hidden");

  const camOn = elsVc.camBtn.querySelector(".icon-on");
  const camOff = elsVc.camBtn.querySelector(".icon-off");
  if (camOn) camOn.classList.toggle("hidden", isVoiceCall);
  if (camOff) camOff.classList.add("hidden");
  elsVc.localMuteIndicator.classList.add("hidden");
  if (elsVc.remoteMuteIndicator) elsVc.remoteMuteIndicator.classList.add("hidden");
  elsVc.localCamOffIndicator.classList.add("hidden");
  if (elsVc.remoteCamOffIndicator) elsVc.remoteCamOffIndicator.classList.add("hidden");

  try {
    let stream = null;
    try {
      stream = await navigator.mediaDevices.getUserMedia(
        isVoiceCall ? { audio: true, video: false } : { video: true, audio: true }
      );
    } catch (mediaErr) {
      console.warn("Could not get media stream:", mediaErr);
      if (!isVoiceCall) {
        try {
          stream = await navigator.mediaDevices.getUserMedia({ audio: true, video: false });
          isVoiceCall = true;
          vcCurrentCallType = "voice";
          showToast("Kamera tidak dapat diakses. Beralih ke panggilan suara.");
          if (elsVc.modal) elsVc.modal.classList.add("voice-mode");
          if (elsVc.voicePanel) elsVc.voicePanel.classList.remove("hidden");
        } catch (audioErr) {
          showToast("Sila benarkan akses mikrofon/kamera dalam pelayar anda.");
          endAdminCall();
          return;
        }
      } else {
        showToast("Sila benarkan akses mikrofon dalam pelayar anda: " + mediaErr.message);
        endAdminCall();
        return;
      }
    }

    vcLocalStream = stream;
    if (!isVoiceCall && elsVc.localVideo) {
      elsVc.localVideo.srcObject = vcLocalStream;
    }

    vcPeerConnection = new RTCPeerConnection({
      iceServers: [{ urls: "stun:stun.l.google.com:19302" }]
    });

    vcLocalStream.getTracks().forEach(track => {
      vcPeerConnection.addTrack(track, vcLocalStream);
    });

    vcPeerConnection.ontrack = (event) => {
      if (elsVc.overlay) elsVc.overlay.classList.add("hidden");
      if (!vcRemoteStream) {
        vcRemoteStream = new MediaStream();
        if (elsVc.remoteVideo) elsVc.remoteVideo.srcObject = vcRemoteStream;
      }
      vcRemoteStream.addTrack(event.track);
      if (elsVc.remoteVideo) {
        elsVc.remoteVideo.play().catch(() => {});
      }
    };

    const callRef = push(ref(db, "calls"));
    vcCurrentCallId = callRef.key;

    vcPeerConnection.onicecandidate = (event) => {
      if (event.candidate && vcCurrentCallId) {
        push(ref(db, "calls/" + vcCurrentCallId + "/callerCandidates"), {
          candidate: event.candidate.candidate,
          sdpMid: event.candidate.sdpMid,
          sdpMLineIndex: event.candidate.sdpMLineIndex
        }).catch(() => {});
      }
    };

    const offer = await vcPeerConnection.createOffer();
    await vcPeerConnection.setLocalDescription(offer);

    const callerUid = (state.currentUser && state.currentUser.uid) ? state.currentUser.uid : "admin";

    await update(ref(db, "calls/" + vcCurrentCallId), {
      offer: { type: offer.type, sdp: offer.sdp },
      callerUid: callerUid,
      callerName: "ResQTap",
      callerPhotoUrl: "",
      callType: vcCurrentCallType,
      status: "calling",
      timestamp: serverTimestamp()
    });

    await update(ref(db, "userCalls/" + vcTargetUid + "/currentCall"), {
      callId: vcCurrentCallId,
      status: "ringing",
      callerName: "ResQTap",
      callType: vcCurrentCallType,
      timestamp: serverTimestamp()
    });

    const answerUnsub = onValue(ref(db, "calls/" + vcCurrentCallId + "/answer"), (snapshot) => {
      const data = snapshot.val();
      if (data && data.sdp && vcPeerConnection && !vcPeerConnection.currentRemoteDescription) {
        const rtcDescription = new RTCSessionDescription(data);
        vcPeerConnection.setRemoteDescription(rtcDescription).catch(e => console.warn("Failed to set remote answer:", e));
      }
    });
    vcUnsubscribers.push(answerUnsub);

    const cameraStateRef = ref(db, `calls/${vcCurrentCallId}/cameraState`);
    set(child(cameraStateRef, 'caller'), !isVoiceCall).catch(() => {});
    if (!isVoiceCall) {
      const cameraUnsub = onValue(child(cameraStateRef, 'callee'), (snapshot) => {
        if (snapshot.exists()) {
          const isCamEnabled = snapshot.val();
          if (elsVc.remoteCamOffIndicator) {
            if (isCamEnabled) {
              elsVc.remoteCamOffIndicator.classList.add("hidden");
            } else {
              elsVc.remoteCamOffIndicator.classList.remove("hidden");
            }
          }
        }
      });
      vcUnsubscribers.push(cameraUnsub);
    }

    const micStateRef = ref(db, `calls/${vcCurrentCallId}/micState`);
    set(child(micStateRef, 'caller'), true).catch(() => {});

    const micUnsub = onValue(child(micStateRef, 'callee'), (snapshot) => {
      if (snapshot.exists()) {
        const isMicEnabled = snapshot.val();
        if (elsVc.remoteMuteIndicator) {
          if (isMicEnabled) {
            elsVc.remoteMuteIndicator.classList.add("hidden");
          } else {
            elsVc.remoteMuteIndicator.classList.remove("hidden");
          }
        }
      }
    });
    vcUnsubscribers.push(micUnsub);

    const processedCandidates = new Set();
    const iceUnsub = onValue(ref(db, "calls/" + vcCurrentCallId + "/calleeCandidates"), (snapshot) => {
      snapshot.forEach(childSnapshot => {
        const key = childSnapshot.key;
        if (processedCandidates.has(key)) return;
        processedCandidates.add(key);
        const data = childSnapshot.val();
        if (data && data.candidate && vcPeerConnection) {
          const candidate = new RTCIceCandidate(data);
          vcPeerConnection.addIceCandidate(candidate).catch(() => {});
        }
      });
    });
    vcUnsubscribers.push(iceUnsub);

    vcPeerConnection.onconnectionstatechange = () => {
      if (!vcPeerConnection) return;
      const connState = vcPeerConnection.connectionState;
      if (connState === "closed") {
        endAdminCall();
      } else if (connState === "disconnected" || connState === "failed") {
        setTimeout(() => {
          if (vcPeerConnection && (vcPeerConnection.connectionState === "disconnected" || vcPeerConnection.connectionState === "failed")) {
            showToast("Panggilan telah ditamatkan");
            endAdminCall();
          }
        }, 2000);
      }
    };

    vcPeerConnection.oniceconnectionstatechange = () => {
      if (!vcPeerConnection) return;
      const iceState = vcPeerConnection.iceConnectionState;
      if (iceState === "closed") {
        endAdminCall();
      } else if (iceState === "disconnected" || iceState === "failed") {
        setTimeout(() => {
          if (vcPeerConnection && (vcPeerConnection.iceConnectionState === "disconnected" || vcPeerConnection.iceConnectionState === "failed")) {
            showToast("Panggilan telah ditamatkan");
            endAdminCall();
          }
        }, 2000);
      }
    };

    const statusUnsub = onValue(ref(db, "userCalls/" + uid + "/currentCall/status"), (snapshot) => {
      const status = snapshot.val();
      if (!status) return;
      if (status === "chat_instead") {
        showToast("Pengguna memilih untuk bersembang (Chat instead)");
        const targetUid = uid;
        endAdminCall(true);
        switchToChatForUser(targetUid);
        return;
      }
      if (status === "rejected") {
        showToast("Panggilan telah ditolak oleh pengguna");
        endAdminCall();
      } else if (status === "ended") {
        showToast("Panggilan telah ditamatkan");
        endAdminCall();
      } else if (status === "accepted") {
        if (elsVc.status) elsVc.status.textContent = "Connecting...";
      }
    });
    vcUnsubscribers.push(statusUnsub);

    const callStatusUnsub = onValue(ref(db, "calls/" + vcCurrentCallId + "/status"), (snapshot) => {
      const status = snapshot.val();
      if (!status) return;
      if (status === "chat_instead") {
        showToast("Pengguna memilih untuk bersembang (Chat instead)");
        const targetUid = uid;
        endAdminCall(true);
        switchToChatForUser(targetUid);
        return;
      }
      if (status === "rejected" || status === "ended") {
        showToast("Panggilan telah ditamatkan");
        endAdminCall();
      }
    });
    vcUnsubscribers.push(callStatusUnsub);

  } catch (error) {
    console.error("Call error:", error);
    showToast("Error starting call: " + error.message);
    endAdminCall();
  }
}

function switchToChatForUser(uid) {
  if (!uid) return;

  // 1. Semak sesi SOS Livechat aktif untuk pengguna ini
  const sosSessions = getSosLivechatSessions();
  const activeSos = sosSessions.find((s) => s.senderUid === uid && s.active) || sosSessions.find((s) => s.senderUid === uid);
  if (activeSos) {
    state.selectedSosSessionId = activeSos.sessionId;
    setActiveView("soslivechat");
    return;
  }

  // 2. Semak jika ada alert SOS aktif secara langsung melalui getActiveSosForUser
  const directSos = getActiveSosForUser(uid);
  if (directSos) {
    const alertKey = directSos.key || directSos.id;
    state.selectedSosSessionId = `${directSos.roomId}__${alertKey}`;
    setActiveView("soslivechat");
    return;
  }

  // 3. Fallback ke Livechat sokongan biasa
  state.selectedChatUid = uid;
  setActiveView("livechat");
}

function endAdminCall(preserveStatus = false) {
  if (!vcPeerConnection && !vcCurrentCallId && !vcTargetUid && (!elsVc.modal || elsVc.modal.classList.contains("hidden"))) {
    return;
  }

  vcUnsubscribers.forEach(unsub => {
    try { unsub(); } catch (e) {}
  });
  vcUnsubscribers = [];

  if (vcActiveCallLogId) {
    const durationSec = vcActiveCallStartTime ? Math.max(0, Math.round((Date.now() - vcActiveCallStartTime) / 1000)) : 0;
    update(ref(db, `call_logs/${vcActiveCallLogId}`), {
      status: preserveStatus ? "chat_instead" : "ended",
      endedAt: Date.now(),
      duration: durationSec
    }).catch(() => {});
    vcActiveCallLogId = null;
    vcActiveCallStartTime = null;
  }

  if (vcTargetUid && vcCurrentCallId) {
    const targetUidToClean = vcTargetUid;
    const callIdToClean = vcCurrentCallId;
    if (!preserveStatus) {
      update(ref(db, "userCalls/" + targetUidToClean + "/currentCall"), { status: "ended" }).catch(() => {});
    } else {
      setTimeout(() => {
        update(ref(db, "userCalls/" + targetUidToClean + "/currentCall"), { status: "ended" }).catch(() => {});
      }, 2500);
    }
    remove(ref(db, "calls/" + callIdToClean)).catch(() => {});
  }

  if (vcPeerConnection) {
    try { vcPeerConnection.close(); } catch (e) {}
    vcPeerConnection = null;
  }
  if (vcLocalStream) {
    try { vcLocalStream.getTracks().forEach(track => track.stop()); } catch (e) {}
    vcLocalStream = null;
  }

  if (elsVc.localVideo) elsVc.localVideo.srcObject = null;
  if (elsVc.remoteVideo) elsVc.remoteVideo.srcObject = null;
  vcRemoteStream = null;
  vcCurrentCallId = null;
  vcTargetUid = null;
  vcCurrentCallType = "video";

  if (elsVc.modal) {
    elsVc.modal.classList.remove("floating");
    elsVc.modal.classList.remove("voice-mode");
    elsVc.modal.style.left = "";
    elsVc.modal.style.top = "";
    elsVc.modal.style.right = "";
    elsVc.modal.style.bottom = "";
    elsVc.modal.classList.add("hidden");
  }
  if (elsVc.voicePanel) elsVc.voicePanel.classList.add("hidden");
  if (elsVc.bottomSheet) elsVc.bottomSheet.classList.remove("show");
}

elsVc.minimizeBtn.addEventListener("click", (e) => {
  e.stopPropagation();
  elsVc.modal.classList.add("floating");
});

elsVc.endBtn.addEventListener("click", () => {
  endAdminCall();
});

elsVc.micBtn.addEventListener("click", () => {
  if (!vcLocalStream) return;
  const audioTrack = vcLocalStream.getAudioTracks()[0];
  if (!audioTrack) return;
  isVcMicEnabled = !isVcMicEnabled;
  audioTrack.enabled = isVcMicEnabled;

  const micOn = elsVc.micBtn.querySelector(".icon-on");
  const micOff = elsVc.micBtn.querySelector(".icon-off");
  if (isVcMicEnabled) {
    if (micOn) micOn.classList.remove("hidden");
    if (micOff) micOff.classList.add("hidden");
    elsVc.localMuteIndicator.classList.add("hidden");
    if (vcCurrentCallId) {
      set(ref(db, `calls/${vcCurrentCallId}/micState/caller`), true);
    }
  } else {
    if (micOn) micOn.classList.add("hidden");
    if (micOff) micOff.classList.remove("hidden");
    elsVc.localMuteIndicator.classList.remove("hidden");
    if (vcCurrentCallId) {
      set(ref(db, `calls/${vcCurrentCallId}/micState/caller`), false);
    }
  }
});

elsVc.camBtn.addEventListener("click", () => {
  if (!vcLocalStream || vcCurrentCallType === "voice") return;
  const videoTrack = vcLocalStream.getVideoTracks()[0];
  if (!videoTrack) return;
  isVcCamEnabled = !isVcCamEnabled;
  videoTrack.enabled = isVcCamEnabled;

  const camOn = elsVc.camBtn.querySelector(".icon-on");
  const camOff = elsVc.camBtn.querySelector(".icon-off");
  if (isVcCamEnabled) {
    if (camOn) camOn.classList.remove("hidden");
    if (camOff) camOff.classList.add("hidden");
    elsVc.localCamOffIndicator.classList.add("hidden");
    if (vcCurrentCallId) {
      set(ref(db, `calls/${vcCurrentCallId}/cameraState/caller`), true);
    }
  } else {
    if (camOn) camOn.classList.add("hidden");
    if (camOff) camOff.classList.remove("hidden");
    elsVc.localCamOffIndicator.classList.remove("hidden");
    if (vcCurrentCallId) {
      set(ref(db, `calls/${vcCurrentCallId}/cameraState/caller`), false);
    }
  }
});

elsVc.moreBtn.addEventListener("click", () => {
  elsVc.bottomSheet.classList.toggle("show");
});

let isDraggingVc = false;
let vcDragStartX = 0;
let vcDragStartY = 0;
let vcInitialLeft = 0;
let vcInitialTop = 0;
let vcHasDragged = false;

elsVc.modal.addEventListener("mousedown", (e) => {
  if (!elsVc.modal.classList.contains("floating")) return;
  if (e.target.closest("button") || e.target.closest(".vc-bottom-sheet")) return;

  isDraggingVc = true;
  vcHasDragged = false;
  vcDragStartX = e.clientX;
  vcDragStartY = e.clientY;
  const rect = elsVc.modal.getBoundingClientRect();
  vcInitialLeft = rect.left;
  vcInitialTop = rect.top;

  elsVc.modal.style.transition = "none";
  elsVc.modal.style.right = "auto";
  elsVc.modal.style.bottom = "auto";
  elsVc.modal.style.left = vcInitialLeft + "px";
  elsVc.modal.style.top = vcInitialTop + "px";
});

window.addEventListener("mousemove", (e) => {
  if (!isDraggingVc) return;
  const dx = e.clientX - vcDragStartX;
  const dy = e.clientY - vcDragStartY;

  if (Math.abs(dx) > 5 || Math.abs(dy) > 5) vcHasDragged = true;

  let newLeft = vcInitialLeft + dx;
  let newTop = vcInitialTop + dy;

  const w = elsVc.modal.offsetWidth;
  const h = elsVc.modal.offsetHeight;
  if (newLeft < 0) newLeft = 0;
  if (newTop < 0) newTop = 0;
  if (newLeft + w > window.innerWidth) newLeft = window.innerWidth - w;
  if (newTop + h > window.innerHeight) newTop = window.innerHeight - h;

  elsVc.modal.style.left = newLeft + "px";
  elsVc.modal.style.top = newTop + "px";
});

window.addEventListener("mouseup", (e) => {
  if (!isDraggingVc) return;
  isDraggingVc = false;

  elsVc.modal.style.transition = "all 0.3s cubic-bezier(0.4, 0, 0.2, 1)";

  const w = elsVc.modal.offsetWidth;
  const h = elsVc.modal.offsetHeight;
  const rect = elsVc.modal.getBoundingClientRect();
  const centerX = rect.left + w / 2;

  if (centerX < window.innerWidth / 2) {
    elsVc.modal.style.left = "24px";
  } else {
    elsVc.modal.style.left = (window.innerWidth - w - 24) + "px";
  }

  if (rect.top < 24) elsVc.modal.style.top = "24px";
  if (rect.top + h > window.innerHeight - 24) elsVc.modal.style.top = (window.innerHeight - h - 24) + "px";
});

elsVc.modal.addEventListener("click", (e) => {
  if (elsVc.modal.classList.contains("floating")) {
    if (vcHasDragged) {
      vcHasDragged = false;
      return;
    }
    elsVc.modal.classList.remove("floating");
    elsVc.modal.style.left = "";
    elsVc.modal.style.top = "";
    elsVc.modal.style.right = "";
    elsVc.modal.style.bottom = "";
    return;
  }
  if (!e.target.closest(".vc-bottom-sheet") && !e.target.closest("#vcMoreBtn") && !e.target.closest("#vcMinimizeBtn")) {
    elsVc.bottomSheet.classList.remove("show");
  }
});

elsVc.shareScreenBtn.addEventListener("click", async (e) => {
  e.stopPropagation();
  elsVc.bottomSheet.classList.remove("show");
  if (!vcPeerConnection || vcCurrentCallType === "voice") return;

  try {
    const displayStream = await navigator.mediaDevices.getDisplayMedia({ video: true });
    const screenTrack = displayStream.getVideoTracks()[0];

    const senders = vcPeerConnection.getSenders();
    const videoSender = senders.find(s => s.track && s.track.kind === 'video');
    if (videoSender) {
      videoSender.replaceTrack(screenTrack);
    }
    elsVc.localVideo.srcObject = displayStream;

    screenTrack.onended = () => {

      const cameraTrack = vcLocalStream ? vcLocalStream.getVideoTracks()[0] : null;
      if (cameraTrack && videoSender) {
        videoSender.replaceTrack(cameraTrack);
        elsVc.localVideo.srcObject = vcLocalStream;
      }
    };
  } catch (error) {
    console.error("Screen sharing failed", error);
  }
});

elsVc.sendMessageBtn.addEventListener("click", (e) => {
  e.stopPropagation();
  elsVc.bottomSheet.classList.remove("show");
  const uid = vcTargetUid;

  elsVc.modal.classList.add("floating");

  state.selectedChatUid = uid;
  setActiveView("livechat");
});

/* Live map module: Leaflet satellite map of all user locations,
   active SOS alerts, and incident reports in real time. */
(function initLiveMapModule() {
  // ── Inject CSS ──────────────────────────────────────────────────
  if (!document.getElementById("livemap-styles")) {
    const s = document.createElement("style");
    s.id = "livemap-styles";
    s.textContent = `.livemap-toolbar{display:flex;align-items:center;gap:12px;flex-wrap:wrap;padding:10px 16px;background:var(--surface,#fff);border-bottom:1px solid var(--border,#e2e8f0);position:relative;z-index:10}.livemap-filters{display:flex;gap:6px;flex-wrap:wrap}.livemap-filter-btn{display:inline-flex;align-items:center;gap:5px;padding:5px 12px;border-radius:20px;border:1.5px solid var(--border,#e2e8f0);background:transparent;font-size:.78rem;font-weight:500;cursor:pointer;color:var(--text-muted,#64748b);transition:all .18s}.livemap-filter-btn:hover{border-color:var(--primary,#6366f1);color:var(--primary,#6366f1)}.livemap-filter-btn.is-active{background:var(--primary,#6366f1);color:#fff;border-color:var(--primary,#6366f1);box-shadow:0 2px 8px rgba(99,102,241,.28)}.livemap-filter-btn i{width:13px;height:13px}.livemap-stats{display:flex;gap:14px;align-items:center;flex:1;flex-wrap:wrap}.livemap-stat{display:inline-flex;align-items:center;gap:5px;font-size:.78rem;color:var(--text-muted,#64748b)}.livemap-stat i{width:13px;height:13px}.livemap-stat-sos{color:#ef4444;font-weight:600}.livemap-stat-sos strong{color:#ef4444}.livemap-legend{display:flex;gap:16px;flex-wrap:wrap;align-items:center;padding:6px 16px;font-size:.72rem;color:var(--text-muted,#64748b);background:var(--surface,#fff);border-bottom:1px solid var(--border,#e2e8f0)}.livemap-legend-item{display:flex;align-items:center;gap:5px}.livemap-dot{width:10px;height:10px;border-radius:50%;display:inline-block;flex-shrink:0}.livemap-dot-online{background:#22c55e;box-shadow:0 0 0 2px #bbf7d0}.livemap-dot-recent{background:#f59e0b;box-shadow:0 0 0 2px #fde68a}.livemap-dot-offline{background:#94a3b8}.livemap-dot-sos{background:#ef4444;box-shadow:0 0 0 2px #fecaca;animation:mapSosPulse 1.2s infinite}.livemap-dot-report{background:#8b5cf6}@keyframes mapSosPulse{0%,100%{box-shadow:0 0 0 2px #fecaca}50%{box-shadow:0 0 0 8px rgba(239,68,68,.18)}}#livemapView{display:flex;flex-direction:column;height:100%;overflow:hidden}#livemapView.hidden{display:none!important}.livemap-container{flex:1;min-height:480px;position:relative;background-color:#aad3df!important}.livemap-container .leaflet-container{background-color:#aad3df!important;outline:0!important}.dark-mode .livemap-container,.dark-mode .livemap-container .leaflet-container{background-color:#1e293b!important}.livemap-sidepanel{position:absolute;top:0;right:0;width:300px;height:100%;background:var(--surface,#fff);border-left:1px solid var(--border,#e2e8f0);z-index:410;display:flex;flex-direction:column;box-shadow:-4px 0 16px rgba(0,0,0,.08)}.livemap-sidepanel.hidden{display:none}.livemap-sidepanel-header{display:flex;align-items:center;justify-content:space-between;padding:14px 16px;border-bottom:1px solid var(--border,#e2e8f0)}.livemap-sidepanel-header h3{margin:0;font-size:.92rem;font-weight:700}.livemap-close-btn{background:none;border:none;cursor:pointer;color:var(--text-muted,#64748b);padding:4px;border-radius:6px;display:flex;align-items:center}.livemap-close-btn:hover{background:var(--bg-hover,#f1f5f9)}.livemap-sidepanel-body{flex:1;overflow-y:auto;padding:14px 16px;font-size:.82rem}.livemap-detail-row{display:flex;flex-direction:column;gap:2px;margin-bottom:12px}.livemap-detail-row label{font-size:.7rem;font-weight:600;text-transform:uppercase;letter-spacing:.06em;color:var(--text-muted,#64748b)}.livemap-detail-row span{color:var(--text,#1e293b)}.livemap-detail-badge{display:inline-flex;align-items:center;gap:4px;padding:2px 9px;border-radius:10px;font-size:.72rem;font-weight:600}.livemap-detail-badge.online{background:#dcfce7;color:#15803d}.livemap-detail-badge.recent{background:#fef9c3;color:#92400e}.livemap-detail-badge.offline{background:#f1f5f9;color:#475569}.livemap-detail-badge.sos{background:#fee2e2;color:#b91c1c}.livemap-detail-badge.report{background:#ede9fe;color:#6d28d9}.lm-marker-wrap{position:relative;width:40px;height:40px;border-radius:50%;background:#fff;box-shadow:0 3px 10px rgba(0,0,0,.32);transition:transform .18s cubic-bezier(.34,1.56,.64,1);box-sizing:border-box;display:flex;align-items:center;justify-content:center;overflow:visible!important}.lm-marker-wrap:hover{transform:scale(1.18);z-index:1000!important}.lm-marker-online{border:3px solid #22c55e;box-shadow:0 0 0 2px rgba(34,197,94,.35),0 3px 10px rgba(0,0,0,.32)}.lm-marker-recent{border:3px solid #f59e0b;box-shadow:0 0 0 2px rgba(245,158,11,.35),0 3px 10px rgba(0,0,0,.32)}.lm-marker-offline{border:3px solid #94a3b8;box-shadow:0 3px 8px rgba(0,0,0,.25)}.lm-marker-sos{border:3.5px solid #ef4444;box-shadow:0 0 0 3px rgba(239,68,68,.45),0 4px 14px rgba(239,68,68,.6);animation:mapSosPulse 1.1s infinite}.lm-marker-report{border:3px solid #8b5cf6;box-shadow:0 0 0 2px rgba(139,92,246,.3),0 3px 10px rgba(0,0,0,.3)}.lm-avatar-frame{position:relative;z-index:1;width:100%;height:100%;border-radius:50%;overflow:hidden;background:#FFE4EE;display:flex;align-items:center;justify-content:center}.lm-avatar-frame img{width:100%;height:100%;object-fit:cover;display:block;border-radius:50%}.lm-avatar-frame svg{width:100%;height:100%;display:block}.lm-icon-report-inner{background:#8b5cf6;color:#fff;font-weight:800;font-size:16px;width:100%;height:100%;display:flex;align-items:center;justify-content:center;font-family:'Inter',sans-serif}.lm-marker-dot{position:absolute;bottom:-2px;right:-2px;width:11px;height:11px;border-radius:50%;border:2px solid #fff;box-shadow:0 1px 3px rgba(0,0,0,.35);z-index:10}.lm-dot-online{background:#22c55e}.lm-dot-recent{background:#f59e0b}.lm-dot-offline{background:#94a3b8}.lm-dot-report{background:#8b5cf6}.lm-sos-badge{position:absolute!important;bottom:-18px!important;left:50%!important;transform:translateX(-50%)!important;background:#ef4444!important;color:#fff!important;font-size:11px!important;font-weight:900!important;padding:2.5px 8px!important;border-radius:9999px!important;border:2px solid #fff!important;box-shadow:0 3px 8px rgba(0,0,0,.6)!important;letter-spacing:.8px!important;line-height:1!important;white-space:nowrap!important;z-index:99999!important;pointer-events:none!important;display:block!important}.leaflet-popup-content-wrapper{border-radius:10px!important;font-family:'Inter',sans-serif!important}.leaflet-popup-content{font-size:.8rem!important;line-height:1.5!important}.dark-mode .livemap-toolbar,.dark-mode .livemap-legend,.dark-mode .livemap-sidepanel{background:var(--surface-dark,#1e293b);border-color:var(--border-dark,#334155)}.dark-mode .livemap-sidepanel-header{border-color:var(--border-dark,#334155)}.dark-mode .livemap-detail-row span{color:#e2e8f0}@media(max-width:700px){.livemap-sidepanel{width:100%}.livemap-stats{display:none}}`;
    document.head.appendChild(s);
  }

  // ── Default Avatar SVG matching ResQTap Android App (ic_avatar.xml) ──
  const DEFAULT_AVATAR_SVG = `<svg viewBox="0 0 48 48" width="100%" height="100%" xmlns="http://www.w3.org/2000/svg" style="display:block;border-radius:50%;"><circle cx="24" cy="24" r="24" fill="#FFE4EE"/><circle cx="24" cy="24" r="23.4" fill="none" stroke="#FFD1E0" stroke-width="1.2"/><circle cx="24" cy="16.5" r="5.5" fill="#E60067"/><path d="M24,24.5c-4.6,0 -9.2,2.3 -12.2,6.2c-0.8,1 -1.1,2.2 -1.1,3.4v1.9c0,0.8 0.6,1.4 1.4,1.4h23.8c0.8,0 1.4,-0.6 1.4,-1.4v-1.9c0,-1.2 -0.3,-2.4 -1.1,-3.4c-3,-3.9 -7.6,-6.2 -12.2,-6.2z" fill="#E60067"/></svg>`;
  window.__resqDefaultAvatarSvg = DEFAULT_AVATAR_SVG;

  function getUserPhoto(uid, memData) {
    const u = asRecord(state.users?.[uid]);
    const m = asRecord(memData);
    const uPhoto = text(m.photoUrl || u.photoUrl || m.photoUri || u.photoUri).trim();
    if (uPhoto && (uPhoto.startsWith("http://") || uPhoto.startsWith("https://") || uPhoto.startsWith("data:image/"))) {
      return uPhoto;
    }
    const b64 = text(m.photoB64 || u.photoB64).trim();
    if (b64 && b64.length < 400000) {
      return `data:image/jpeg;base64,${b64}`;
    }
    return "";
  }

  function buildAvatarHtml(photoSrc) {
    if (photoSrc) {
      return `<img src="${escapeHtml(photoSrc)}" alt="" onerror="this.onerror=null;this.parentElement.innerHTML=window.__resqDefaultAvatarSvg;" style="width:100%;height:100%;object-fit:cover;display:block;border-radius:50%;">`;
    }
    return DEFAULT_AVATAR_SVG;
  }

  // ── Map state ───────────────────────────────────────────────────
  const ms = { leaflet: null, markers: new Map(), filter: "all", initialized: false };
  window.__resqLiveMap = ms;
  window.__resqInitLiveMap = initMap;
  window.__resqRefreshMarkers = refreshMarkers;

  function stCls(updatedAt) {
    const age = Date.now() - millis(updatedAt);
    if (age <= ONLINE_MS) return "online";
    if (age <= RECENT_MS) return "recent";
    return "offline";
  }

  function mkIcon(opts) {
    const o = typeof opts === "string" ? { type: opts } : (opts || {});
    const type = o.type || "offline";

    if (type === "report") {
      return window._LeafletMap.divIcon({
        className: "",
        html: `<div class="lm-marker-wrap lm-marker-report"><div class="lm-avatar-frame"><div class="lm-icon-report-inner">!</div></div><span class="lm-marker-dot lm-dot-report"></span></div>`,
        iconSize: [40, 40],
        iconAnchor: [20, 20],
        popupAnchor: [0, -22]
      });
    }

    const photoSrc = o.photo || "";
    const avatarContent = buildAvatarHtml(photoSrc);
    let badgeHtml = "";
    if (type === "sos") {
      badgeHtml = `<span class="lm-sos-badge" style="position:absolute!important;bottom:-18px!important;left:50%!important;transform:translateX(-50%)!important;z-index:99999!important;background:#ef4444!important;color:#ffffff!important;font-size:11px!important;font-weight:900!important;padding:2.5px 8px!important;border-radius:9999px!important;border:2px solid #ffffff!important;box-shadow:0 3px 8px rgba(0,0,0,0.6)!important;letter-spacing:0.8px!important;line-height:1!important;white-space:nowrap!important;pointer-events:none!important;display:block!important;">SOS</span>`;
    } else {
      badgeHtml = `<span class="lm-marker-dot lm-dot-${type}"></span>`;
    }

    const wrapCls = `lm-marker-wrap lm-marker-${type}`;
    return window._LeafletMap.divIcon({
      className: "",
      html: `<div class="${wrapCls}" style="overflow:visible!important;"><div class="lm-avatar-frame" style="position:relative!important;z-index:1!important;">${avatarContent}</div>${badgeHtml}</div>`,
      iconSize: [40, 40],
      iconAnchor: [20, 20],
      popupAnchor: [0, -22]
    });
  }

  function closeSide() { const p = document.getElementById("livemapSidepanel"); if (p) p.classList.add("hidden"); }

  function openSide(title, body) {
    const p = document.getElementById("livemapSidepanel");
    const t = document.getElementById("livemapSidepanelTitle");
    const b = document.getElementById("livemapSidepanelBody");
    if (!p || !t || !b) return;
    t.textContent = title; b.innerHTML = body;
    p.classList.remove("hidden");
    refreshIcons();
  }

  function dr(label, val) { return `<div class="livemap-detail-row"><label>${escapeHtml(label)}</label><span>${val}</span></div>`; }
  function badge(cls, lbl) { return `<span class="livemap-detail-badge ${escapeHtml(cls)}">${escapeHtml(lbl)}</span>`; }

  function fitAll() {
    if (!ms.leaflet) return;
    const pts = []; ms.markers.forEach((m) => { if (m._map) pts.push(m.getLatLng()); });
    if (!pts.length) {
      ms.leaflet.setView([4.0, 109.5], 6);
      return;
    }
    if (pts.length === 1) {
      ms.leaflet.setView(pts[0], 12);
      return;
    }
    try { ms.leaflet.fitBounds(window._LeafletMap.latLngBounds(pts), { padding:[50,50], maxZoom:14 }); } catch(e) { /**/ }
  }

  function prune(keep) {
    ms.markers.forEach((m, k) => { if (!keep.has(k)) { m.remove(); ms.markers.delete(k); } });
  }

  function upsert(key, lat, lng, iconOpts, popup, cb) {
    if (!ms.leaflet || !window._LeafletMap) return;
    if (!Number.isFinite(lat) || !Number.isFinite(lng) || (lat===0 && lng===0)) return;
    const icon = mkIcon(iconOpts);
    const ll   = window._LeafletMap.latLng(lat, lng);
    if (ms.markers.has(key)) {
      const m = ms.markers.get(key);
      m.setLatLng(ll).setIcon(icon).bindPopup(popup);
      if (cb) {
        m.off("click");
        m.on("click", cb);
      }
    } else {
      const m = window._LeafletMap.marker(ll, {icon}).addTo(ms.leaflet).bindPopup(popup);
      if (cb) m.on("click", cb);
      ms.markers.set(key, m);
    }
  }

  function refreshMarkers() {
    if (!ms.initialized || !window._LeafletMap) return;
    const f = ms.filter;
    const keep = new Set();

    if (f === "all" || f === "users") {
      const activeUserMap = new Map();
      getRooms().forEach((room) => {
        room.members.forEach((mem) => {
          const lat = Number(mem.data.lat !== undefined ? mem.data.lat : mem.data.latitude);
          const lng = Number(mem.data.lng !== undefined ? mem.data.lng : mem.data.longitude);
          if (!lat && !lng) return;
          if (!Number.isFinite(lat) || !Number.isFinite(lng)) return;
          if (lat === 0 && lng === 0) return;
          const uid = mem.uid;
          if (state.userRooms && state.userRooms[uid] && !state.userRooms[uid][room.code]) return;
          const existing = activeUserMap.get(uid);
          const roomHasSos = (room.alerts || []).some((a) => {
            if (a.data.senderUid !== uid) return false;
            if (isCancelled(a.data)) return false;
            return true;
          });
          const userHasActiveSos = roomHasSos || Boolean(getActiveSosForUser(uid));
          if (!existing) {
            activeUserMap.set(uid, {
              room,
              mem,
              lat,
              lng,
              hasSos: userHasActiveSos,
              updatedAt: mem.updatedAt
            });
          } else {
            // Keep the most recent live location
            if (mem.updatedAt > existing.updatedAt) {
              existing.room = room;
              existing.mem = mem;
              existing.lat = lat;
              existing.lng = lng;
              existing.updatedAt = mem.updatedAt;
            }
            // If any room has active SOS for this user, flag hasSos
            if (userHasActiveSos) {
              existing.hasSos = true;
            }
          }
        });
      });

      // Also map users from state.users with coordinates
      if (state.users) {
        entries(state.users).forEach(([uid, uVal]) => {
          if (isAdminAccount(uid, uVal)) return;
          const u = asRecord(uVal);
          const lat = Number(u.lat !== undefined ? u.lat : u.latitude);
          const lng = Number(u.lng !== undefined ? u.lng : u.longitude);
          if (!lat || !lng || !Number.isFinite(lat) || !Number.isFinite(lng) || (lat === 0 && lng === 0)) return;
          const userHasActiveSos = Boolean(getActiveSosForUser(uid));
          const existing = activeUserMap.get(uid);
          const uTime = millis(u.updatedAt || u.locationUpdatedAt);
          if (!existing) {
            activeUserMap.set(uid, {
              room: null,
              mem: { uid, data: u, updatedAt: uTime, batteryPct: Number.isFinite(Number(u.batteryPct)) ? Number(u.batteryPct) : null },
              lat,
              lng,
              hasSos: userHasActiveSos,
              updatedAt: uTime
            });
          } else {
            if (uTime > existing.updatedAt) {
              existing.lat = lat;
              existing.lng = lng;
              existing.updatedAt = uTime;
            }
            if (userHasActiveSos) {
              existing.hasSos = true;
            }
          }
        });
      }

      // Also ensure any sender with active SOS has their marker on the map!
      getSosAlerts().filter((a) => a.active && !isCancelled(a.data)).forEach((alert) => {
        const uid = alert.senderUid;
        if (!uid) return;
        let lat = Number(alert.data && (alert.data.lat !== undefined ? alert.data.lat : alert.data.latitude));
        let lng = Number(alert.data && (alert.data.lng !== undefined ? alert.data.lng : alert.data.longitude));
        const existing = activeUserMap.get(uid);
        if (!lat || !lng) {
          if (existing) {
            lat = existing.lat;
            lng = existing.lng;
          } else if (state.users && state.users[uid]) {
            const u = asRecord(state.users[uid]);
            lat = Number(u.lat !== undefined ? u.lat : u.latitude);
            lng = Number(u.lng !== undefined ? u.lng : u.longitude);
          }
        }
        if (Number.isFinite(lat) && Number.isFinite(lng) && !(lat === 0 && lng === 0)) {
          if (!existing) {
            const u = asRecord(state.users && state.users[uid]);
            activeUserMap.set(uid, {
              room: { code: alert.roomId },
              mem: { uid, data: u, updatedAt: alert.createdAt || Date.now(), batteryPct: null },
              lat,
              lng,
              hasSos: true,
              updatedAt: alert.createdAt || Date.now()
            });
          } else {
            existing.hasSos = true;
            existing.lat = lat;
            existing.lng = lng;
          }
        }
      });

      activeUserMap.forEach(({ room, mem, lat, lng, hasSos }, uid) => {
        const key = `u:${uid}`;
        keep.add(key);
        const st  = stCls(mem.updatedAt);
        const it  = hasSos ? "sos" : st;
        const nm  = escapeHtml(userName(uid));
        const bat = mem.batteryPct!==null ? `${mem.batteryPct}%` : "N/A";
        const ago = mem.updatedAt ? ageLabel(mem.updatedAt) : "N/A";
        const photo = getUserPhoto(uid, mem.data);
        const avatarSnippet = buildAvatarHtml(photo);
        const stBorder = it === 'sos' ? '#ef4444' : st === 'online' ? '#22c55e' : st === 'recent' ? '#f59e0b' : '#94a3b8';
        const popup = `<div style="display:flex;align-items:center;gap:10px;margin-bottom:8px;">
          <div style="width:38px;height:38px;border-radius:50%;overflow:hidden;border:2.5px solid ${stBorder};flex-shrink:0;background:#FFE4EE;box-shadow:0 2px 6px rgba(0,0,0,.15);">${avatarSnippet}</div>
          <div>
            <strong style="font-size:0.9rem;color:#0f172a;display:block;">${nm}</strong>
          </div>
        </div>
        <div style="font-size:0.77rem;color:#475569;line-height:1.5;border-top:1px solid #e2e8f0;padding-top:6px;">
          Status: <strong>${st}</strong><br>
          Battery: <strong>${escapeHtml(bat)}</strong><br>
          Last seen: ${escapeHtml(ago)}
          ${hasSos ? "<br><strong style='color:#ef4444;'>🚨 Active SOS</strong>" : ""}
        </div>`;
        upsert(key, lat, lng, { type: it, photo, uid, name: nm }, popup, () => {
          if (hasSos) {
            const activeSos = getActiveSosForUser(uid);
            if (activeSos) {
              const rId = activeSos.roomId || (room && room.code ? room.code : "DIRECT");
              const aId = activeSos.key || activeSos.id;
              state.selected = { type: "sos", roomId: rId, alertId: aId };
            } else {
              state.selected = { type: "user", id: uid };
            }
          } else {
            state.selected = { type: "user", id: uid };
          }
          renderDetail();
          [50, 150, 300].forEach((t) => setTimeout(() => { if (ms.leaflet) ms.leaflet.invalidateSize(); }, t));
        });
      });
    }

    if (f === "sos") {
      getSosAlerts().filter((a) => a.active && !isCancelled(a.data)).forEach((alert) => {
        let lat=0, lng=0;
        if (alert.data) {
          if (Number(alert.data.lat) && Number(alert.data.lng)) {
            lat = Number(alert.data.lat); lng = Number(alert.data.lng);
          } else if (Number(alert.data.latitude) && Number(alert.data.longitude)) {
            lat = Number(alert.data.latitude); lng = Number(alert.data.longitude);
          }
        }
        if (!lat && !lng) {
          getRooms().forEach((rm)=>{
            const m=rm.members.find((item) => item.uid === alert.senderUid);
            if(m && Number(m.lat)&&Number(m.lng)){lat=Number(m.lat);lng=Number(m.lng);}
          });
        }
        if (!lat && !lng && alert.senderUid && state.users && state.users[alert.senderUid]) {
          const u = asRecord(state.users[alert.senderUid]);
          const uLat = Number(u.lat !== undefined ? u.lat : u.latitude);
          const uLng = Number(u.lng !== undefined ? u.lng : u.longitude);
          if (Number.isFinite(uLat) && Number.isFinite(uLng) && !(uLat === 0 && uLng === 0)) {
            lat = uLat; lng = uLng;
          }
        }
        if(!lat&&!lng) return;
        const key=`sos:${alert.key}`; keep.add(key);
        const sn=escapeHtml(alert.senderName||userName(alert.senderUid));
        const senderUid = alert.senderUid;
        const photo = getUserPhoto(senderUid);
        const avatarSnippet = buildAvatarHtml(photo);
        const popup = `<div style="display:flex;align-items:center;gap:10px;margin-bottom:8px;">
          <div style="width:38px;height:38px;border-radius:50%;overflow:hidden;border:2.5px solid #ef4444;flex-shrink:0;background:#FFE4EE;box-shadow:0 2px 6px rgba(0,0,0,.15);">${avatarSnippet}</div>
          <div>
            <strong style="color:#ef4444;font-size:0.9rem;display:block;">🚨 ACTIVE SOS</strong>
            <strong style="font-size:0.85rem;color:#0f172a;">${sn}</strong>
          </div>
        </div>
        <div style="font-size:0.77rem;color:#475569;line-height:1.5;border-top:1px solid #e2e8f0;padding-top:6px;">
          Triggered: ${escapeHtml(formatDate(alert.createdAt))}
        </div>`;
        upsert(key, lat, lng, { type: "sos", photo, uid: senderUid, name: sn }, popup, () => {
          state.selected = { type: "sos", roomId: alert.roomId, alertId: alert.id };
          renderDetail();
          [50, 150, 300].forEach((t) => setTimeout(() => { if (ms.leaflet) ms.leaflet.invalidateSize(); }, t));
        });
      });
    }

    if (f === "all" || f === "reports") {
      getIncidentReports().forEach((rep) => {
        const lat=Number(rep.latitude), lng=Number(rep.longitude);
        if(!lat&&!lng) return;
        const key=`rpt:${rep.id}`; keep.add(key);
        const popup=`<strong>${escapeHtml(rep.categoryLabel||rep.category)}</strong><br>Reporter: ${escapeHtml(rep.senderName)}<br>Status: ${escapeHtml(rep.status)}<br>${rep.address?`Address: ${escapeHtml(rep.address)}<br>`:""}${escapeHtml(formatDate(rep.createdAt))}`;
        upsert(key,lat,lng,{ type: "report" },popup,()=>{
          state.selected = { type: "report", id: rep.id };
          renderDetail();
          [50, 150, 300].forEach((t) => setTimeout(() => { if (ms.leaflet) ms.leaflet.invalidateSize(); }, t));
        });
      });
    }
    prune(keep);
  }

  function updateStats() {
    const rooms=getRooms(); let oc=0; const seen=new Set();
    rooms.forEach((r)=>{ r.members.forEach((m)=>{ if(seen.has(m.uid)) return; seen.add(m.uid); if(Date.now()-millis(m.updatedAt)<=ONLINE_MS) oc++; }); });
    if (state.users) {
      entries(state.users).forEach(([uid, uVal]) => {
        if (isAdminAccount(uid, uVal)) return;
        if (seen.has(uid)) return;
        const u = asRecord(uVal);
        const uTime = millis(u.updatedAt || u.locationUpdatedAt);
        if (Date.now() - uTime <= ONLINE_MS) {
          seen.add(uid);
          oc++;
        }
      });
    }
    const ac=getSosAlerts().filter((a)=>a.active&&!a.stale).length;
    const rc=getIncidentReports().length;
    const uce=document.getElementById("livemapUserCount");
    const sce=document.getElementById("livemapSosCount");
    const rce=document.getElementById("livemapReportCount");
    if(uce) uce.textContent=oc;
    if(sce) sce.textContent=ac;
    if(rce) rce.textContent=rc;
  }

  function initMap() {
    console.log("[LiveMap] initMap called. window._LeafletMap:", !!window._LeafletMap, "initialized:", ms.initialized);
    if (ms.initialized) return;
    if (!window._LeafletMap) {
      console.error("[LiveMap] Leaflet not loaded! window._LeafletMap is undefined.");
      return;
    }
    const view = document.getElementById("livemapView");
    if (view && view.classList.contains("hidden") && state.activeView === "livemap") {
      view.classList.remove("hidden");
    }
    const container = document.getElementById("livemapContainer");
    if (!container) { console.error("[LiveMap] #livemapContainer not found!"); return; }
    console.log("[LiveMap] Container size:", container.offsetWidth, "x", container.offsetHeight);

    const worldBounds = window._LeafletMap.latLngBounds(
      window._LeafletMap.latLng(-85.05112878, -180),
      window._LeafletMap.latLng(85.05112878, 180)
    );

    ms.leaflet = window._LeafletMap.map(container, {
      center: [4.0, 109.5],
      zoom: 6,
      minZoom: 3,
      maxZoom: 19,
      maxBounds: worldBounds,
      maxBoundsViscosity: 1.0,
      worldCopyJump: false
    });

    window._LeafletMap.tileLayer("https://{s}.tile.openstreetmap.org/{z}/{x}/{y}.png", {
      minZoom: 3,
      maxZoom: 19,
      bounds: worldBounds,
      attribution: "&copy; <a href='https://www.openstreetmap.org/copyright'>OpenStreetMap</a> contributors"
    }).addTo(ms.leaflet);

    const syncMapLimits = () => {
      if (!ms.leaflet || !window._LeafletMap) return;
      try {
        const fitMin = ms.leaflet.getBoundsZoom(worldBounds, true);
        const safeMin = Math.max(3, Number.isFinite(fitMin) ? fitMin : 3);
        ms.leaflet.setMinZoom(safeMin);
        if (ms.leaflet.getZoom() < safeMin) {
          ms.leaflet.setZoom(safeMin);
        }
      } catch (e) {
        ms.leaflet.setMinZoom(3);
      }
    };

    syncMapLimits();
    window.addEventListener("resize", syncMapLimits);

    ms.initialized = true;
    window.__resqLiveMap = ms;
    window.__resqRefreshMarkers = refreshMarkers;
    // Force size recalculation at multiple intervals to handle any layout delay
    [50, 150, 400].forEach((t) => setTimeout(() => {
      if (ms.leaflet) {
        ms.leaflet.invalidateSize();
        syncMapLimits();
      }
    }, t));
    console.log("[LiveMap] Map initialized OK");

    // Auto resize map when detail panel opens or closes
    if (els.appShell) {
      const shellObs = new MutationObserver(() => {
        if (ms.leaflet) {
          requestAnimationFrame(() => ms.leaflet.invalidateSize());
          setTimeout(() => { if (ms.leaflet) ms.leaflet.invalidateSize(); }, 260);
        }
      });
      shellObs.observe(els.appShell, { attributes: true, attributeFilter: ["class"] });
    }


    // Filter buttons
    document.querySelectorAll(".livemap-filter-btn").forEach((btn) => {
      btn.addEventListener("click", () => {
        ms.filter = btn.dataset.mapFilter || "all";
        document.querySelectorAll(".livemap-filter-btn").forEach((b) => b.classList.toggle("is-active", b===btn));
        refreshMarkers(); refreshIcons();
      });
    });

    const cb = document.getElementById("livemapCenterBtn");
    if (cb) cb.addEventListener("click", fitAll);
    const cl = document.getElementById("livemapSidepanelClose");
    if (cl) cl.addEventListener("click", closeSide);
  }

  // ── MutationObserver: trigger map init the moment user navigates to Live Map ──
  // This is reliable regardless of how render() is scoped in the ES module.
  function watchLivemapView() {
    const view = document.getElementById("livemapView");
    if (!view) {
      // DOM not ready yet, retry once after a short delay
      setTimeout(watchLivemapView, 300);
      return;
    }

    const obs = new MutationObserver(() => {
      const hidden = view.classList.contains("hidden");
      document.body.classList.toggle("is-livemap-active", !hidden);
      if (!hidden) {
        // View just became visible
        if (!ms.initialized) {
          initMap();
        }
        // Always resize + refresh on show
        requestAnimationFrame(() => {
          if (ms.leaflet) ms.leaflet.invalidateSize();
          updateStats();
          refreshMarkers();
          fitAll();
        });
      }
    });
    obs.observe(view, { attributes: true, attributeFilter: ["class"] });

    // Also patch renderNav's titles map so "Live Map" shows in the topbar
    // We wait for the module-level renderNav to exist before patching
    const origRenderNav = renderNav;
    // Shadow renderNav in module scope: this works because it's in the same script
    window.__livemapPatchNav = function() {
      if (state.activeView === "livemap" && els.viewTitle) {
        els.viewTitle.textContent = "Live Map";
      }
    };
  }

  // Run after DOM is ready
  if (document.readyState === "loading") {
    document.addEventListener("DOMContentLoaded", watchLivemapView);
  } else {
    watchLivemapView();
  }
})();

// SOS Case Modal Listeners & Stepper Setup with Automatic Saving
(function initSosCaseModalListeners() {
  let notesDebounceTimer = null;

  // Delegated click handling on document for stages and quick tags
  document.addEventListener("click", async (e) => {
    // 1. Click stage step button (Dispatched, En Route, On Scene, Resolved)
    const stepBtn = e.target.closest(".sos-case-step-btn");
    if (stepBtn) {
      e.preventDefault();
      document.querySelectorAll(".sos-case-step-btn").forEach((b) => b.classList.remove("is-active"));
      stepBtn.classList.add("is-active");
      if (activeCaseModalData) {
        activeCaseModalData.step = Number(stepBtn.dataset.step) || 1;
        activeCaseModalData.status = stepBtn.dataset.status || "In Progress";
        activeCaseModalData.desc = stepBtn.dataset.desc || "";
      }
      const notesInput = document.getElementById("caseNotesInput");
      if (notesInput && stepBtn.dataset.desc) {
        notesInput.value = stepBtn.dataset.desc;
      }
      await saveCaseProgress();
      return;
    }

    // 2. Click quick note tag chip
    const tagChip = e.target.closest(".sos-tag-chip");
    if (tagChip) {
      e.preventDefault();
      const notesInput = document.getElementById("caseNotesInput");
      if (notesInput && tagChip.dataset.note) {
        notesInput.value = tagChip.dataset.note;
        await saveCaseProgress();
      }
      return;
    }
  });

  function setupNotesInput() {
    const notesInput = document.getElementById("caseNotesInput");
    if (!notesInput) return;

    // Auto-save while typing (debounced)
    notesInput.addEventListener("input", () => {
      updateCaseAutoSaveStatus("Saving notes...", "saving");
      clearTimeout(notesDebounceTimer);
      notesDebounceTimer = setTimeout(() => {
        saveCaseProgress({ silent: true });
      }, 500);
    });

    // Save immediately when clicking outside (blur) or on change
    notesInput.addEventListener("change", () => {
      clearTimeout(notesDebounceTimer);
      saveCaseProgress({ silent: true });
    });

    notesInput.addEventListener("blur", () => {
      clearTimeout(notesDebounceTimer);
      saveCaseProgress({ silent: true });
    });

    // Press Enter to commit note immediately and unfocus
    notesInput.addEventListener("keydown", (e) => {
      if (e.key === "Enter" && !e.shiftKey) {
        e.preventDefault();
        clearTimeout(notesDebounceTimer);
        notesInput.blur();
      }
    });
  }

  if (document.readyState === "loading") {
    document.addEventListener("DOMContentLoaded", setupNotesInput);
  } else {
    setupNotesInput();
  }
})();

