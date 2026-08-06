-- ==============================================================================
-- ResQTap Supabase Database Schema Migration
-- Project Ref: your-supabase-project-ref
-- Salin dan tampal skrip ini ke dalam Supabase Dashboard -> SQL Editor -> Klik "Run"
-- ==============================================================================

-- 1. Aktifkan sambungan PostGIS untuk carian geografi & koordinat GPS
CREATE EXTENSION IF NOT EXISTS postgis;

-- ==============================================================================
-- 2. JADUAL: profiles (Profil Pengguna & Tetapan)
-- ==============================================================================
CREATE TABLE IF NOT EXISTS public.profiles (
    id TEXT PRIMARY KEY, -- UID daripada Firebase Auth atau Supabase Auth
    email TEXT,
    full_name TEXT,
    phone TEXT,
    photo_url TEXT,
    role TEXT DEFAULT 'user',
    latitude DOUBLE PRECISION,
    longitude DOUBLE PRECISION,
    is_online BOOLEAN DEFAULT false,
    battery_pct INTEGER,
    last_seen TIMESTAMPTZ DEFAULT NOW(),
    created_at TIMESTAMPTZ DEFAULT NOW(),
    updated_at TIMESTAMPTZ DEFAULT NOW()
);

-- Migrasi sekiranya jadual profiles sudah wujud:
ALTER TABLE public.profiles ADD COLUMN IF NOT EXISTS latitude DOUBLE PRECISION;
ALTER TABLE public.profiles ADD COLUMN IF NOT EXISTS longitude DOUBLE PRECISION;
ALTER TABLE public.profiles ADD COLUMN IF NOT EXISTS is_online BOOLEAN DEFAULT false;
ALTER TABLE public.profiles ADD COLUMN IF NOT EXISTS battery_pct INTEGER;
ALTER TABLE public.profiles ADD COLUMN IF NOT EXISTS last_seen TIMESTAMPTZ DEFAULT NOW();

-- ==============================================================================
-- 3. JADUAL: medical_cards (Kad Perubatan Pengguna)
-- ==============================================================================
CREATE TABLE IF NOT EXISTS public.medical_cards (
    user_id TEXT PRIMARY KEY REFERENCES public.profiles(id) ON DELETE CASCADE,
    blood_type TEXT,
    allergies TEXT,
    medical_conditions TEXT,
    medications TEXT,
    emergency_contacts JSONB DEFAULT '[]'::jsonb,
    notes TEXT,
    updated_at TIMESTAMPTZ DEFAULT NOW()
);

-- 4. [DIKELUARKAN: hospitals]
DROP TABLE IF EXISTS public.hospitals CASCADE;

-- ==============================================================================
-- 5. JADUAL: reports (Laporan Insiden & Kecemasan)
-- ==============================================================================
CREATE TABLE IF NOT EXISTS public.reports (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    user_id TEXT,
    user_name TEXT,
    title TEXT NOT NULL,
    description TEXT,
    category TEXT, -- 'accident', 'medical', 'fire', 'crime'
    latitude DOUBLE PRECISION,
    longitude DOUBLE PRECISION,
    address TEXT,
    image_url TEXT,
    status TEXT DEFAULT 'pending', -- 'pending', 'in_progress', 'resolved'
    created_at TIMESTAMPTZ DEFAULT NOW(),
    updated_at TIMESTAMPTZ DEFAULT NOW()
);

-- ==============================================================================
-- 6. JADUAL: rooms & room_members (Bilik Kecemasan)
-- ==============================================================================
CREATE TABLE IF NOT EXISTS public.rooms (
    id TEXT PRIMARY KEY, -- Kod bilik cth: 'ABC123'
    name TEXT NOT NULL,
    creator_uid TEXT NOT NULL,
    created_at TIMESTAMPTZ DEFAULT NOW(),
    updated_at TIMESTAMPTZ DEFAULT NOW()
);

CREATE TABLE IF NOT EXISTS public.room_members (
    room_id TEXT REFERENCES public.rooms(id) ON DELETE CASCADE,
    user_uid TEXT NOT NULL,
    user_name TEXT,
    role TEXT DEFAULT 'member', -- 'creator', 'member', 'admin'
    joined_at TIMESTAMPTZ DEFAULT NOW(),
    PRIMARY KEY (room_id, user_uid)
);

-- ==============================================================================
-- 7. ROW LEVEL SECURITY (RLS) POLICIES (HARDENED)
-- ==============================================================================
ALTER TABLE public.profiles ENABLE ROW LEVEL SECURITY;
ALTER TABLE public.medical_cards ENABLE ROW LEVEL SECURITY;
ALTER TABLE public.reports ENABLE ROW LEVEL SECURITY;
ALTER TABLE public.rooms ENABLE ROW LEVEL SECURITY;
ALTER TABLE public.room_members ENABLE ROW LEVEL SECURITY;

-- 7.0 Fungsi Pembantu Admin Selamat (Mengelakkan Recursive Policy / Error 42P17)
CREATE OR REPLACE FUNCTION public.is_app_admin()
RETURNS boolean
LANGUAGE sql
STABLE
SECURITY DEFINER
SET search_path = public
AS $$
    SELECT EXISTS (
        SELECT 1 FROM public.profiles 
        WHERE id = auth.uid() AND (role = 'admin' OR is_admin = true)
    );
$$;



-- 7.2 Profiles: Pengguna hanya boleh melihat dan menyunting profil sendiri (atau dibaca oleh admin)
CREATE POLICY "Read Profiles" ON public.profiles FOR SELECT 
    USING (auth.uid() = id OR public.is_app_admin());
CREATE POLICY "Update Own Profile" ON public.profiles FOR UPDATE 
    USING (auth.uid() = id);
CREATE POLICY "Insert Own Profile" ON public.profiles FOR INSERT 
    WITH CHECK (auth.uid() = id);

-- 7.3 Medical Cards: Data perubatan peribadi HANYA boleh diakses oleh pemilik atau responden/admin kecemasan
CREATE POLICY "Read Medical Cards" ON public.medical_cards FOR SELECT 
    USING (auth.uid() = user_id OR public.is_app_admin());
CREATE POLICY "Modify Own Medical Card" ON public.medical_cards FOR ALL 
    USING (auth.uid() = user_id);

-- 7.4 Reports: Akses penuh untuk pembacaan, penghantaran, pengemaskinian status dan pemadaman
DROP POLICY IF EXISTS "Read Reports" ON public.reports;
DROP POLICY IF EXISTS "Insert Reports" ON public.reports;
DROP POLICY IF EXISTS "Admin Update Reports" ON public.reports;
DROP POLICY IF EXISTS "Allow All Reports" ON public.reports;
CREATE POLICY "Allow All Reports" ON public.reports FOR ALL 
    USING (true) 
    WITH CHECK (true);

-- 7.5 Rooms & Members: Hanya ahli atau pencipta bilik boleh membaca dan mengurus
CREATE POLICY "Read Rooms" ON public.rooms FOR SELECT 
    USING (auth.uid() = creator_uid OR EXISTS (SELECT 1 FROM public.room_members WHERE room_id = public.rooms.id AND user_uid = auth.uid()));
CREATE POLICY "Manage Own Rooms" ON public.rooms FOR ALL 
    USING (auth.uid() = creator_uid);
CREATE POLICY "Read Room Members" ON public.room_members FOR SELECT 
    USING (EXISTS (SELECT 1 FROM public.room_members rm WHERE rm.room_id = public.room_members.room_id AND rm.user_uid = auth.uid()));
CREATE POLICY "Join Or Leave Room" ON public.room_members FOR ALL 
    USING (auth.uid() = user_uid OR EXISTS (SELECT 1 FROM public.rooms r WHERE r.id = public.room_members.room_id AND r.creator_uid = auth.uid()));


