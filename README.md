<div align="center">

# ResQTap

[![platform](https://img.shields.io/badge/platform-Android%207.0%2B%20(API%2024%2B)-3DDC84?logo=android&logoColor=white)](https://developer.android.com/)
[![language](https://img.shields.io/badge/language-Java%20%7C%20Kotlin-ED8B00?logo=openjdk&logoColor=white)](https://www.java.com/)
[![backend](https://img.shields.io/badge/backend-Firebase%20%7C%20Supabase-FFCA28?logo=firebase&logoColor=black)](https://firebase.google.com/)
[![build](https://img.shields.io/badge/build-Gradle%20AGP%209.x-02303A?logo=gradle&logoColor=white)](https://gradle.org/)
[![license](https://img.shields.io/badge/license-MIT-blue)](LICENSE)
[![status](https://img.shields.io/badge/status-active-success)](https://github.com/Chesster06/RESQTAP-SOS-Emergency-System)
[![release](https://img.shields.io/badge/release-v1.0-blue.svg)](https://github.com/Chesster06/RESQTAP-SOS-Emergency-System)

A mission-critical personal emergency SOS and rapid-response companion for Android: instant one-tap distress signaling, live GPS tracking, automated emergency contact dispatch, offline-accessible medical ID, WebRTC live voice and video calling, AI safety assistant, emergency coordination rooms, and real-time incident management.

End-to-end cloud synchronization, zero third-party telemetry, built for instant crisis reaction.

</div>

---

## Features

### Emergency SOS & Rapid Dispatch
- Instant One-Tap Alarm: High-priority trigger activates emergency sirens, hardware vibration patterns, and immediate cloud broadcast.
- Automated Contact Dispatch: Instantly dispatches distress SMS and push notifications containing live coordinates to designated guardians.
- Interactive Countdown & Cancel Window: Configurable grace period to avert accidental alerts before full escalation.
- Continuous Background Beaconing: Transmits periodic location updates even when screen is locked or app is minimized.

### Real-Time Geolocation & Navigation
- Precision GPS Tracking: Powered by Google Play Services Fused Location Provider for accurate indoor and outdoor positioning.
- Interactive Incident Maps: Real-time vector map rendering with custom dark and light tactical map styles.
- Proximity Hospital Discovery: Automatic scanning and distance calculation to nearest medical facilities and emergency departments.
- Turn-by-Turn Navigation Routing: Deep-links directly to Google Maps for rapid navigation to victims or distress points.

### Medical Card & First Responder Profile
- Quick-Access Medical Profile: Stores essential emergency medical data including blood group, chronic conditions, verified allergies, and active medications.
- First Responder Offline Mode: Critical medical parameters accessible without active internet connection during rescue situations.
- Dynamic Card Sync: Synchronizes medical cards across Supabase PostgreSQL backend with full Row Level Security (RLS) enforcement.

### Emergency Rooms & Live Tracking Hub
- Dynamic Group Incident Rooms: Create private, temporary emergency response rooms via 6-character room codes.
- Multi-User Tactical Map: Live visualization of all room members, responders, and victims on a unified tactical coordinate overlay.
- Real-Time Room Chat: Low-latency messaging, status broadcast, and emergency coordination within active incident hubs.
- Rapid Onboarding via QR Code: Instant connection and room joining through camera scanning or gallery QR image import.

### Live WebRTC Calls & Responder Communication
- P2P Audio & Video Calling: Low-latency WebRTC media stream channels for direct emergency calls between users and central responders.
- Real-Time Signaling via RTDB: Firebase Realtime Database manages ICE candidates, session descriptions (SDP), and call lifecycle state.
- Floating Incoming Alert: System-wide heads-up call notification with quick accept and decline actions.

### AI Emergency Assistant
- Intelligent Safety Consultation: Integrated Gemini AI conversational engine tailored for emergency triage, first aid instructions, and safety guidance.
- Instant Ticket Dispatch: One-click handoff from help documentation and support tickets directly into the AI consultation channel.
- Context-Aware Responses: Pre-configured prompt templates for burns, fractures, CPR guidance, domestic safety, and disaster readiness.

### Incident Reporting & Verified News
- Community Incident Reporting: Submit geotagged incident reports with multimedia evidence, category classification, and detailed narratives.
- Administrative Triage Workflow: Web admin dashboard verifies, claims, resolves, or updates active public safety tickets.
- Real-Time Safety & Medical Bulletins: Live RSS and database feed delivering verified safety warnings, weather hazards, and health advisories.

### Security, Biometrics & Duress Protocol
- Native Biometric Authentication: Fingerprint and facial unlock with hardware keystore backed validation.
- Dual-Layer Security PIN: Independent 4-digit security PIN fallback for biometric failures.
- Covert Duress Safeguard: Dedicated duress PIN that silently transmits an emergency distress signal while simulating normal app unlock during forced duress.
- Strict App Lock Lifecycle: Auto-locks sensitive screens and chat records upon app minimization or inactivity timeout.

---

## Technology Stack

| Component | Technology | Responsibility |
|---|---|---|
| Mobile Client | Android SDK (Java 17 / Kotlin) | Native Android mobile application (Min SDK 24, Target SDK 36) |
| Cloud Database | Firebase Realtime Database | Low-latency state synchronization for SOS alerts, signaling, and presence |
| Relational Backend | Supabase (PostgreSQL 15) | Relational storage for profiles, medical cards, reports, and audit logs |
| Push Notifications | Firebase Cloud Messaging (FCM) | High-priority background alerts, room invites, and call wakeups |
| Storage Engine | Firebase Storage | Encrypted storage for incident media, chat attachments, and profile photos |
| Cloud Compute | Firebase Cloud Functions (Node.js) | Serverless automation, AI proxying, message routing, and scheduled cleanups |
| Web Portal & Admin | Vanilla JS / Node.js HTTP Server | Real-time monitoring dashboard, APK download portal, and dispatch panel |
| Real-Time Calls | Google WebRTC / RTDB Signaling | Peer-to-peer encrypted voice and video streaming |
| Mapping Engine | Google Maps SDK / Fused Location | Precision spatial indexing, vector rendering, and geo-tracking |

---

## Quick Start

### 1. Prerequisites
- Android Studio Ladybug (2024.2+) or newer
- Java Development Kit (JDK) 17
- Android SDK Platform 36 with Build-Tools 36.0.0
- Physical Android device or Android Virtual Device (AVD) running Android 7.0 (API 24) or higher
- Node.js 18+ (for web admin portal and Firebase Cloud Functions)

### 2. Configuration & Setup

#### Clone Repository
```bash
git clone https://github.com/Chesster06/resqtap-sos-emergency-systems.git
cd resqtap-sos-emergency-systems
```

#### Android Firebase Configuration
Place your verified `google-services.json` inside the `app/` directory:
```text
app/google-services.json
```

#### API Keys Configuration
Create or update `local.properties` in project root:
```properties
sdk.dir=C:\\Users\\YOUR_USER\\AppData\\Local\\Android\\Sdk
MAPS_API_KEY=YOUR_GOOGLE_MAPS_API_KEY
GEMINI_API_KEY=YOUR_GEMINI_API_KEY
```

#### Supabase Database Migration
Execute SQL migration scripts in your Supabase SQL Editor:
```bash
# Core schema: profiles, medical_cards, reports, rooms
database/migrations/supabase_schema.sql

# Auxiliary tables: highlights, notifications, system_metadata
database/migrations/supabase_additional_tables.sql
```

### 3. Build & Run Android Application

#### Command Line Build (Debug APK)
```bash
# Windows
.\gradlew.bat assembleDebug

# Linux / macOS
./gradlew assembleDebug
```
Output artifact generated at:
```text
app/build/outputs/apk/debug/RESQTAP.apk
```

#### Install on Connected Device / Emulator
```bash
.\gradlew.bat installDebug
```

### 4. Web Portal & Admin Dashboard

The web portal includes an administrative operations console and direct APK distribution host.

#### Run Local Development Server
```bash
cd ResQTap-Website
npm start
```
- Public Portal & Download: `http://localhost:5000`
- Admin Operations Dashboard: `http://localhost:5000/admin`

#### Deploy to Firebase Hosting
```bash
firebase deploy --only hosting
```

---

## Repository Structure

```text
├── app/                                # Android native application module
│   ├── src/main/java/com/example/resqtap/
│   │   ├── app/                        # Application entry, base activity, lifecycle
│   │   ├── auth/                       # Authentication, registration, password recovery
│   │   ├── call/                       # WebRTC calling engine & signaling clients
│   │   ├── chat/                       # Live messaging, AI assistant, ticket support
│   │   ├── contacts/                   # Emergency contacts management & adapters
│   │   ├── friend/                     # QR code pairing, friends directory
│   │   ├── home/                       # Main dashboard, highlights, dynamic feed
│   │   ├── map/                        # Google Maps integration, hospital locators
│   │   ├── news/                       # Verified safety & medical bulletins
│   │   ├── profile/                    # User settings, medical card management
│   │   ├── report/                     # Incident report creation & tracking
│   │   ├── room/                       # Emergency rooms hub & tactical coordinate map
│   │   ├── security/                   # App lock, biometric auth, duress PIN engine
│   │   ├── sos/                        # Emergency state machine, siren, snooze, dispatch
│   │   └── utils/                      # Formatting, battery optimizations, helpers
│   ├── src/main/res/                   # Layouts, vector drawables, localization strings
│   ├── google-services.json            # Firebase client credentials
│   └── build.gradle.kts                # Application build configurations
├── ResQTap-Website/                    # Public portal & administrative operations dashboard
│   ├── public/                         # Static assets, SPA client, styles, APK binaries
│   ├── server/                         # MVC backend server (admin API & static routing)
│   └── package.json                    # Node dependencies & npm commands
├── firebase-functions/                 # Serverless Cloud Functions (Node.js 18)
│   ├── index.js                        # FCM trigger routing, AI proxy, automated cleanup
│   └── package.json                    # Cloud Functions dependencies
├── database/                           # Relational database schemas & SQL migrations
│   └── migrations/                     # Supabase PostgreSQL relational schemas
│       ├── supabase_schema.sql         # Primary PostgreSQL database migration script
│       └── supabase_additional_tables.sql # Auxiliary relational tables migration script
├── database.rules.json                 # Firebase Realtime Database security rules
├── storage.rules                       # Firebase Cloud Storage security rules
├── firebase.json                       # Firebase project configuration & routing rules
├── cleanup_rtdb.js                     # Administrative database cleanup maintenance script
└── README.md                           # Technical documentation
```

---

## Security & Privacy Architecture

- Granular Realtime Rules: Access to `/sos_alerts`, `/active_sos`, and `/rooms` strictly restricted to authorized incident participants and verified administrative roles via `database.rules.json`.
- Row Level Security (RLS): All Supabase PostgreSQL tables enforce hardened RLS policies ensuring users can only read and mutate their own medical records, notifications, and incident drafts.
- Secure Credentials Isolation: Private keys, Firebase Admin SDK credentials (`*.json`), keystore files (`*.jks`), and Google Maps API keys are isolated from public repository tracking via `.gitignore`.
- Duress Emergency Protocol: In coercive scenarios, entering the designated duress PIN grants visible access to a decoy interface while silently beaconing background distress coordinates to authorities.

---

## Disclaimer

This software was engineered as an academic Final Year Project (FYP). Distributed for educational, developmental, and research evaluation purposes.
