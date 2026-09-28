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
  { id: "reviewing", label: "Under Review", tone: "warn" },
  { id: "in_progress", label: "In Progress", tone: "warn" },
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
  soslivechatDeleteBtn: document.getElementById("soslivechatDeleteBtn"),
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
  ticketlivechatCount: document.getElementById("ticketlivechatCount"),
  ticketlivechatThreadList: document.getElementById("ticketlivechatThreadList"),
  ticketlivechatSelectedTitle: document.getElementById("ticketlivechatSelectedTitle"),
  ticketlivechatSelectedSubtitle: document.getElementById("ticketlivechatSelectedSubtitle"),
  ticketlivechatMessages: document.getElementById("ticketlivechatMessages"),
  ticketlivechatEmptyState: document.getElementById("ticketlivechatEmptyState"),
  ticketlivechatReplyForm: document.getElementById("ticketlivechatReplyForm"),
  ticketlivechatReplyInput: document.getElementById("ticketlivechatReplyInput"),
  ticketlivechatAttachButton: document.getElementById("ticketlivechatAttachButton"),
  ticketlivechatAttachMenu: document.getElementById("ticketlivechatAttachMenu"),
  ticketlivechatAttachmentInput: document.getElementById("ticketlivechatAttachmentInput"),
  ticketlivechatAttachmentLabel: document.getElementById("ticketlivechatAttachmentLabel"),
  ticketlivechatReplyButton: document.getElementById("ticketlivechatReplyButton"),
  ticketlivechatClaimBtn: document.getElementById("ticketlivechatClaimBtn"),
  ticketlivechatResolveBtn: document.getElementById("ticketlivechatResolveBtn"),
  ticketlivechatDeleteBtn: document.getElementById("ticketlivechatDeleteBtn"),
  ticketlivechatCallVideoBtn: document.getElementById("ticketlivechatCallVideoBtn"),
  ticketlivechatCallVoiceBtn: document.getElementById("ticketlivechatCallVoiceBtn"),
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
  sidebarSignOutBtn: document.getElementById("sidebarSignOutBtn"),
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
        return "**ResQTap SOS feature** lets users broadcast instant emergency alerts with a single tap.\n\nIt activates an emergency alarm, broadcasts real-time GPS coordinates directly to your **Channel members**, and immediately notifies administrators.";
      }
      if (q.includes("location") || q.includes("gps") || q.includes("tracking") || q.includes("live") || q.includes("map")) {
        return "**Live Location Tracking** in ResQTap is powered by Firebase Realtime Database. When an emergency is active, your trusted Channel members and responders can track your position continuously on the map.";
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
        return "Hello, I am **Katup**, your assistant for ResQTap. How can I assist you with SOS alerts, live tracking, or emergency channels today?";
      }
      if (q.includes("thank") || q.includes("thanks")) {
        return "You are very welcome. Stay safe, and let me know if you need anything else.";
      }

      return "Thank you for asking. I am **Katup**, your ResQTap assistant. You can ask me about **SOS alerts**, **Live Location sharing**, **Emergency Channels**, or **how to download the APK**.";
    }

    if (q.includes("sos") || q.includes("kecemasan") || q.includes("alert") || q.includes("bantuan") || q.includes("panik")) {
      return "Fungsi **SOS ResQTap** membolehkan anda menghantar amaran kecemasan pantas dalam 1 sentuhan.\n\nIa akan membunyikan amaran kecemasan, menghantar koordinat GPS secara langsung ke semua ahli **Channel** anda, dan memberi amaran kepada admin dengan serta-merta.";
    }
    if (q.includes("location") || q.includes("lokasi") || q.includes("gps") || q.includes("live tracking") || q.includes("jejak") || q.includes("peta")) {
      return "**Live Location Tracking** ResQTap menggunakan Firebase Realtime Database untuk menyegerakkan lokasi GPS anda secara langsung. Ahli Channel dan responder boleh menjejaki pergerakan anda pada peta secara tepat semasa insiden berlaku.";
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
  ticketChats: {},
  sosChats: {},
  aiChats: {},
  highlights: {},
  medicalCards: {},
  servedCases: new Set(),
  selectedSosSessionId: "",
  soslivechatSending: false,
  selectedChatUid: "",
  selectedTicketUid: "",
  selectedAiChatUid: "",
  livechatAttachment: null,
  livechatAttachmentKind: "",
  livechatSending: false,
  ticketlivechatAttachment: null,
  ticketlivechatAttachmentKind: "",
  ticketlivechatSending: false,
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
  aiChatCleanupRunning: false,
  deletedReportIds: null
};

function getDeletedReportIds() {
  if (!state.deletedReportIds) {
    try {
      const raw = window.localStorage.getItem("resqtap_deleted_reports");
      state.deletedReportIds = new Set(raw ? JSON.parse(raw) : []);
    } catch (_) {
      state.deletedReportIds = new Set();
    }
  }
  return state.deletedReportIds;
}

function markReportAsDeleted(id) {
  if (!id) return;
  const set = getDeletedReportIds();
  set.add(id);
  try {
    window.localStorage.setItem("resqtap_deleted_reports", JSON.stringify([...set]));
  } catch (_) {}
}

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

    // 3. Highlights (Supabase ONLY)
    const { data: hl } = await supabase.from("highlights").select("*").order("display_order", { ascending: true });
    if (Array.isArray(hl)) {
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
      state.highlights = hlMap;
      renderHighlights();
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

    // 7. Incident Reports - Disimpan dan dimuatkan dari Supabase sepenuhnya
    const { data: rep } = await supabase.from("reports").select("*").order("created_at", { ascending: false });
    if (Array.isArray(rep)) {
      const delSet = getDeletedReportIds();
      const repMap = {};
      rep.forEach((r) => {
        if (!delSet.has(r.id)) {
          repMap[r.id] = {
            id: r.id,
            supabaseId: r.id,
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
        }
      });
      state.incidentReports = repMap;
    }

    // 8. System Calls / Call Logs - Disimpan di Supabase sepenuhnya
    const { data: callsData } = await supabase.from("system_calls").select("*").order("created_at", { ascending: false });
    if (Array.isArray(callsData)) {
      const callsMap = {};
      callsData.forEach((c) => {
        const raw = c.raw_data || {};
        callsMap[c.id] = {
          id: c.id,
          callType: c.call_type || raw.callType || "video",
          callerUid: c.caller_uid || raw.callerUid || "",
          callerName: c.caller_name || raw.callerName || "",
          calleeUid: raw.calleeUid || "",
          calleeName: raw.calleeName || "",
          status: c.status || raw.status || "ended",
          duration: raw.duration || 0,
          roomId: c.room_id || raw.roomId || "-",
          alertId: raw.alertId || "-",
          source: raw.source || "system_calls",
          timestamp: raw.timestamp || (c.created_at ? new Date(c.created_at).getTime() : Date.now()),
          endedAt: raw.endedAt || Date.now()
        };
      });
      state.callLogs = callsMap;
    }

    state.lastSyncAt = Date.now();
    setSyncStatus(`Live Sync: ${new Date(state.lastSyncAt).toLocaleTimeString()}`);
    render();
  } catch (err) {
    console.warn("[SUPABASE_SYNC_ERROR]", err);
  }
}

async function saveCallLogToSupabase(logData) {
  if (!logData || !logData.id) return;
  try {
    const payload = {
      id: logData.id,
      call_type: logData.callType || "video",
      caller_uid: logData.callerUid || "",
      caller_name: logData.callerName || "",
      room_id: logData.roomId || "-",
      status: logData.status || "ended",
      raw_data: {
        calleeUid: logData.calleeUid || "",
        calleeName: logData.calleeName || "",
        duration: logData.duration || 0,
        endedAt: logData.endedAt || Date.now(),
        source: logData.source || "system_calls",
        alertId: logData.alertId || "-",
        timestamp: logData.timestamp || Date.now()
      },
      created_at: new Date(logData.timestamp || Date.now()).toISOString()
    };
    if (state.callLogs) {
      state.callLogs[logData.id] = {
        ...logData,
        duration: payload.raw_data.duration,
        endedAt: payload.raw_data.endedAt
      };
      render();
    }
    await supabase.from("system_calls").upsert(payload);
  } catch (err) {
    console.warn("[SUPABASE_SAVE_CALL_LOG_ERROR]", err);
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
    const rawRooms = asRecord(value);
    if (rawRooms["REPORT"]) {
      delete rawRooms["REPORT"];
      remove(ref(db, "rooms/REPORT")).catch(() => {});
    }
    state.rooms = rawRooms;
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
  // Laporan kini disimpan dan diuruskan sepenuhnya dalam Supabase sahaja
  subscribe("admin_audit_logs", (value) => {
    state.adminAuditLogs = asRecord(value);
  });
  // Rekod panggilan kini disimpan dan disegerakkan daripada Supabase system_calls sahaja
  subscribe("admin_notifications", (value) => {
    state.adminNotifications = asRecord(value);
  });
  subscribe("supportChats", (value) => {
    state.supportChats = asRecord(value);
  });
  subscribe("ticketChats", (value) => {
    state.ticketChats = asRecord(value);
  });
  subscribe("sosChats", (value) => {
    state.sosChats = asRecord(value);
  });
  subscribe("aiChats", (value) => {
    state.aiChats = asRecord(value);
    cleanupExpiredAiChats().catch((error) => console.warn(error));
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
    if (!roomId || roomId === "REPORT" || roomId.startsWith("REPORT")) return;
    entries(asRecord(room).sosAlerts).forEach(([alertId, alertValue]) => {
      const alert = asRecord(alertValue);
      if (!alert.senderUid && !alert.fromUid && !alert.lat && !alert.type && !alert.status) return;
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
  const rawList = entries(state.incidentReports).map(([id, value]) => {
    const report = asRecord(value);
    const reportId = text(report.reportId).trim() || id;
    const senderUid = text(report.senderUid || report.userId || report.uid).trim();
    const user = asRecord(state.users?.[senderUid]);
    const isRtdb = id.startsWith("-") || !id.includes("-");
    return {
      id: reportId,
      key: id,
      supabaseId: report.supabaseId || (id.length === 36 && id.includes("-") ? id : null),
      isRtdb,
      senderUid,
      senderName: text(report.senderName || user.name || user.email || senderUid).trim(),
      senderEmail: text(report.senderEmail || user.email).trim(),
      publicId: text(report.publicId || user.publicId).trim(),
      category: text(report.category).trim(),
      categoryLabel: text(report.categoryLabel || report.title || report.category).trim() || "Report",
      details: text(report.details || report.description).trim(),
      address: text(report.address).trim(),
      latitude: Number(report.latitude),
      longitude: Number(report.longitude),
      status: text(report.status || "new").trim(),
      createdAt: millis(report.createdAt),
      updatedAt: millis(report.updatedAt),
      attachments: reportAttachments(report),
      data: report
    };
  });

  // Sort so RTDB entries come first
  rawList.sort((a, b) => {
    if (a.isRtdb !== b.isRtdb) return a.isRtdb ? -1 : 1;
    return b.createdAt - a.createdAt || b.updatedAt - a.updatedAt;
  });

  const deduped = [];
  for (const item of rawList) {
    const existing = deduped.find((d) => {
      const timeDiff = Math.abs(d.createdAt - item.createdAt);
      const isUidMatch = d.senderUid && item.senderUid && d.senderUid === item.senderUid;
      const isCatMatch = d.category.toLowerCase() === item.category.toLowerCase();
      const isCoordMatch = Math.abs(d.latitude - item.latitude) < 0.005 && Math.abs(d.longitude - item.longitude) < 0.005;
      return isUidMatch && timeDiff < 300000 && (isCoordMatch || isCatMatch);
    });

    if (existing) {
      if (item.supabaseId && !existing.supabaseId) {
        existing.supabaseId = item.supabaseId;
      }
      continue;
    }
    deduped.push(item);
  }

  return deduped.sort((a, b) => b.createdAt - a.createdAt || b.updatedAt - a.updatedAt);
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
    recipient: alert.source === "legacy" && alert.data.toUid ? userName(alert.data.toUid) : "Channel members",
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
    if (roomsDeleted > 0) subStats += `, ${roomsDeleted} saluran`;
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
    const meta = asRecord(thread.meta);
    const chatType = text(meta.chatType).trim().toLowerCase();
    const source = text(meta.source).trim().toLowerCase();
    // Exclude AI Assistant ticket escalations from Report Livechat
    if (chatType === "ticket" || source === "ai_assistant") {
      return false;
    }
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
    const reportId = text(meta.reportId).trim();
    const isReportChat = Boolean(meta.isReportChat || reportId);
    const lastMessage = text(meta.lastMessage || last.text).trim()
      || (lastAttachment.downloadUrl ? `Attachment: ${text(lastAttachment.name || "file")}` : "");

    return {
      uid,
      meta,
      messages,
      name,
      email,
      topic,
      reportId,
      isReportChat,
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

function getTicketThreads() {
  const records = new Map();

  // 1. Threads under ticketChats
  entries(state.ticketChats).forEach(([uid, value]) => {
    const thread = asRecord(value);
    const messages = getSupportChatMessages(thread);
    if (messages.length > 0 || Object.keys(asRecord(thread.meta)).length > 0) {
      records.set(uid, { uid, thread, store: "ticketChats" });
    }
  });

  // 2. Legacy / fallback threads under supportChats flagged as ticket or from ai_assistant
  entries(state.supportChats).forEach(([uid, value]) => {
    const thread = asRecord(value);
    const meta = asRecord(thread.meta);
    const chatType = text(meta.chatType).trim().toLowerCase();
    const source = text(meta.source).trim().toLowerCase();
    if (chatType === "ticket" || source === "ai_assistant" || (!meta.isReportChat && !meta.reportId && !records.has(uid))) {
      const messages = getSupportChatMessages(thread);
      if (messages.length > 0 && !records.has(uid)) {
        records.set(uid, { uid, thread, store: "supportChats" });
      }
    }
  });

  return Array.from(records.values()).map(({ uid, thread, store }) => {
    const meta = asRecord(thread.meta);
    const user = asRecord(state.users?.[uid]);
    const messages = getSupportChatMessages(thread);
    const last = messages[messages.length - 1] || {};
    const name = text(meta.userName || user.name || user.email || meta.userEmail || uid).trim() || uid;
    const email = text(meta.userEmail || user.email).trim();
    const updatedAt = Math.max(millis(meta.updatedAt), millis(last.createdAt));
    const lastSender = text(meta.lastSender || last.sender).trim();
    const lastAttachment = asRecord(last.attachment);
    const topic = text(meta.topic || "AI Assistant Ticket").trim();
    const ticketId = text(meta.ticketId || meta.sessionId || ("TICK-" + uid.substring(0, 6).toUpperCase())).trim();
    const lastMessage = text(meta.lastMessage || last.text).trim()
      || (lastAttachment.downloadUrl ? `Attachment: ${text(lastAttachment.name || "file")}` : "");

    return {
      uid,
      store,
      meta,
      messages,
      name,
      email,
      topic,
      ticketId,
      publicId: text(meta.publicId || user.publicId).trim(),
      photoUrl: text(user.photoUrl || meta.photoUrl).trim(),
      photoB64: text(user.photoB64 || meta.photoB64).trim(),
      status: text(meta.status).trim() || (messages.length > 0 ? "open" : "resolved"),
      claimedBy: text(meta.claimedBy).trim(),
      claimedByName: text(meta.claimedByName).trim(),
      claimedAt: millis(meta.claimedAt),
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
