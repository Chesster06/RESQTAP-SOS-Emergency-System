-- ==============================================================================
-- ResQTap: Jadual Tambahan untuk Menyimpan Baki Data daripada RTDB ke Supabase
-- Salin dan tampal skrip ini ke dalam Supabase -> SQL Editor -> Klik "Run"
-- ==============================================================================

-- 1. Sorotan Berita / Tip Kecemasan (Highlights)
CREATE TABLE IF NOT EXISTS public.highlights (
    id TEXT PRIMARY KEY,
    title TEXT NOT NULL,
    image_url TEXT,
    action_url TEXT,
    display_order INT DEFAULT 0,
    active BOOLEAN DEFAULT TRUE,
    created_at TIMESTAMPTZ DEFAULT NOW()
);

-- 2. Arkib Notifikasi Pengguna (User Notifications)
CREATE TABLE IF NOT EXISTS public.user_notifications (
    id TEXT PRIMARY KEY,
    user_uid TEXT NOT NULL,
    title TEXT NOT NULL,
    message TEXT NOT NULL,
    created_at_ms BIGINT,
    created_at TIMESTAMPTZ DEFAULT NOW()
);

-- 3. Sejarah Bilik Dipadam (Room Tombstones)
CREATE TABLE IF NOT EXISTS public.room_tombstones (
    room_code TEXT PRIMARY KEY,
    deleted_by TEXT,
    source TEXT,
    deleted_at_ms BIGINT,
    created_at TIMESTAMPTZ DEFAULT NOW()
);

-- 4. Rekod Panggilan & Isyarat Suara (Calls)
CREATE TABLE IF NOT EXISTS public.system_calls (
    id TEXT PRIMARY KEY,
    call_type TEXT,
    caller_uid TEXT,
    caller_name TEXT,
    room_id TEXT,
    status TEXT,
    raw_data JSONB,
    created_at TIMESTAMPTZ DEFAULT NOW()
);

-- 5. Status Sistem & Tugasan Pelayan (System Status & Admin Tasks)
CREATE TABLE IF NOT EXISTS public.system_metadata (
    key TEXT PRIMARY KEY,
    value JSONB NOT NULL,
    updated_at TIMESTAMPTZ DEFAULT NOW()
);

-- 6. Senarai Emel Unik Berdaftar (Registered Emails)
CREATE TABLE IF NOT EXISTS public.registered_emails (
    email_key TEXT PRIMARY KEY,
    is_active BOOLEAN DEFAULT TRUE,
    created_at TIMESTAMPTZ DEFAULT NOW()
);

-- Enable RLS & Hardened Access Policies
ALTER TABLE public.highlights ENABLE ROW LEVEL SECURITY;
ALTER TABLE public.user_notifications ENABLE ROW LEVEL SECURITY;
ALTER TABLE public.room_tombstones ENABLE ROW LEVEL SECURITY;
ALTER TABLE public.system_calls ENABLE ROW LEVEL SECURITY;
ALTER TABLE public.system_metadata ENABLE ROW LEVEL SECURITY;
ALTER TABLE public.registered_emails ENABLE ROW LEVEL SECURITY;

-- Highlights: Awam boleh melihat, hanya admin boleh mengurus
CREATE POLICY "Public Read Highlights" ON public.highlights FOR SELECT USING (true);
CREATE POLICY "Admin Manage Highlights" ON public.highlights FOR ALL 
    USING (EXISTS (SELECT 1 FROM public.profiles WHERE id = auth.uid() AND role = 'admin'));

-- User Notifications: Pengguna hanya membaca notifikasi miliknya sendiri
CREATE POLICY "Read Own Notifications" ON public.user_notifications FOR SELECT 
    USING (auth.uid() = user_uid);
CREATE POLICY "Insert User Notifications" ON public.user_notifications FOR INSERT 
    WITH CHECK (auth.uid() = user_uid OR EXISTS (SELECT 1 FROM public.profiles WHERE id = auth.uid() AND role = 'admin'));

-- Room Tombstones: Hanya ahli atau admin boleh semak bilik yang dipadam
CREATE POLICY "Read Room Tombstones" ON public.room_tombstones FOR SELECT 
    USING (auth.uid() IS NOT NULL);
CREATE POLICY "Insert Room Tombstones" ON public.room_tombstones FOR INSERT 
    WITH CHECK (auth.uid() = deleted_by OR EXISTS (SELECT 1 FROM public.profiles WHERE id = auth.uid() AND role = 'admin'));

-- System Calls: Hanya pemanggil atau admin boleh membaca rekod panggilan
CREATE POLICY "Read User Calls" ON public.system_calls FOR SELECT 
    USING (auth.uid() = caller_uid OR EXISTS (SELECT 1 FROM public.profiles WHERE id = auth.uid() AND role = 'admin'));
CREATE POLICY "Insert User Calls" ON public.system_calls FOR INSERT 
    WITH CHECK (auth.uid() = caller_uid);

-- System Metadata: Akses terhad kepada peranan admin sahaja
CREATE POLICY "Admin Manage System Metadata" ON public.system_metadata FOR ALL 
    USING (EXISTS (SELECT 1 FROM public.profiles WHERE id = auth.uid() AND role = 'admin'));

-- Registered Emails: Hanya boleh dibaca oleh pengguna log masuk untuk semakan pendaftaran
CREATE POLICY "Authenticated Read Emails" ON public.registered_emails FOR SELECT 
    USING (auth.uid() IS NOT NULL);
CREATE POLICY "Insert Own Registered Email" ON public.registered_emails FOR INSERT 
    WITH CHECK (auth.uid() IS NOT NULL);
