-- ============================================
-- SMS INDIA DATABASE SCHEMA - TABLES
-- ============================================
-- Version: 1.0
-- Description: Core tables for SMS India application
-- Execute this file first before other schema files
-- ============================================

-- Enable UUID extension
CREATE EXTENSION IF NOT EXISTS "uuid-ossp";

-- ============================================
-- 1. USERS TABLE
-- ============================================
-- Stores user profiles, wallet balances, and activity tracking
CREATE TABLE IF NOT EXISTS public.users (
    id UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
    phone VARCHAR(15) UNIQUE NOT NULL,
    email VARCHAR(255),
    password VARCHAR(255) NOT NULL,
    device_id VARCHAR(255),
    
    -- Wallet & Earnings
    balance DECIMAL(10, 2) DEFAULT 0.00,
    today_income DECIMAL(10, 2) DEFAULT 0.00,
    total_income DECIMAL(10, 2) DEFAULT 0.00,
    coins BIGINT DEFAULT 0,
    
    -- Gamification
    spins INTEGER DEFAULT 3,
    streak INTEGER DEFAULT 0,
    ad_progress INTEGER DEFAULT 0,
    
    -- Statistics
    referral_count INTEGER DEFAULT 0,
    sms_count INTEGER DEFAULT 0,
    
    -- Metadata
    bank_details JSONB,
    claimed_milestones JSONB DEFAULT '[]'::jsonb,
    last_checkin_date DATE,
    
    -- Timestamps
    created_at TIMESTAMPTZ DEFAULT NOW(),
    updated_at TIMESTAMPTZ DEFAULT NOW()
);

-- Add comment for documentation
COMMENT ON TABLE public.users IS 'User profiles with wallet and activity tracking';
COMMENT ON COLUMN public.users.coins IS 'Virtual coins for spin wheel and bonuses';
COMMENT ON COLUMN public.users.spins IS 'Available spin wheel attempts';
COMMENT ON COLUMN public.users.streak IS 'Daily check-in streak counter';

-- ============================================
-- 2. SMS TASKS TABLE
-- ============================================
-- Individual SMS tasks for the mining system
CREATE TABLE IF NOT EXISTS public.sms_tasks (
    id UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
    recipient VARCHAR(15) NOT NULL,
    message TEXT NOT NULL,
    reward DECIMAL(10, 2) DEFAULT 0.50,
    
    -- Task Status
    status VARCHAR(20) DEFAULT 'pending' CHECK (status IN ('pending', 'assigned', 'completed', 'failed')),
    assigned_to UUID REFERENCES public.users(id) ON DELETE SET NULL,
    assigned_at TIMESTAMPTZ,
    
    -- Timing
    created_at TIMESTAMPTZ DEFAULT NOW(),
    completed_at TIMESTAMPTZ,
    
    -- Metadata
    priority INTEGER DEFAULT 0,
    retry_count INTEGER DEFAULT 0,
    error_message TEXT
);

-- Add indexes for performance
CREATE INDEX IF NOT EXISTS idx_sms_tasks_status ON public.sms_tasks(status);
CREATE INDEX IF NOT EXISTS idx_sms_tasks_assigned_to ON public.sms_tasks(assigned_to);
CREATE INDEX IF NOT EXISTS idx_sms_tasks_created_at ON public.sms_tasks(created_at DESC);

COMMENT ON TABLE public.sms_tasks IS 'Individual SMS tasks for mining operations';
COMMENT ON COLUMN public.sms_tasks.priority IS 'Higher priority tasks are assigned first';

-- ============================================
-- 3. BATCH TASKS TABLE
-- ============================================
-- Tracks batch task assignments for concurrent processing
CREATE TABLE IF NOT EXISTS public.batch_tasks (
    id UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
    user_id UUID NOT NULL REFERENCES public.users(id) ON DELETE CASCADE,
    task_ids UUID[] NOT NULL,
    
    -- Batch Status
    status VARCHAR(20) DEFAULT 'in_progress' CHECK (status IN ('in_progress', 'completed', 'expired')),
    
    -- Results
    success_count INTEGER DEFAULT 0,
    fail_count INTEGER DEFAULT 0,
    total_reward DECIMAL(10, 2) DEFAULT 0.00,
    
    -- Timing
    created_at TIMESTAMPTZ DEFAULT NOW(),
    expires_at TIMESTAMPTZ DEFAULT (NOW() + INTERVAL '30 minutes'),
    completed_at TIMESTAMPTZ
);

CREATE INDEX IF NOT EXISTS idx_batch_tasks_user_id ON public.batch_tasks(user_id);
CREATE INDEX IF NOT EXISTS idx_batch_tasks_status ON public.batch_tasks(status);
CREATE INDEX IF NOT EXISTS idx_batch_tasks_expires_at ON public.batch_tasks(expires_at);

COMMENT ON TABLE public.batch_tasks IS 'Batch task assignments with expiration tracking';
COMMENT ON COLUMN public.batch_tasks.expires_at IS 'Tasks auto-expire after 30 minutes if not completed';

-- ============================================
-- 4. SMS LOGS TABLE
-- ============================================
-- Historical record of all sent SMS messages
CREATE TABLE IF NOT EXISTS public.sms_logs (
    id UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
    user_id UUID NOT NULL REFERENCES public.users(id) ON DELETE CASCADE,
    task_id UUID REFERENCES public.sms_tasks(id) ON DELETE SET NULL,
    
    -- SMS Details
    recipient VARCHAR(15) NOT NULL,
    message TEXT NOT NULL,
    status VARCHAR(20) DEFAULT 'sent' CHECK (status IN ('sent', 'delivered', 'failed')),
    
    -- Reward Info
    reward DECIMAL(10, 2) DEFAULT 0.00,
    
    -- Timestamps
    sent_at TIMESTAMPTZ DEFAULT NOW(),
    delivered_at TIMESTAMPTZ
);

-- Partitioning by month can be added later for large-scale deployments
CREATE INDEX IF NOT EXISTS idx_sms_logs_user_id ON public.sms_logs(user_id);
CREATE INDEX IF NOT EXISTS idx_sms_logs_sent_at ON public.sms_logs(sent_at DESC);
CREATE INDEX IF NOT EXISTS idx_sms_logs_task_id ON public.sms_logs(task_id);

COMMENT ON TABLE public.sms_logs IS 'Audit trail of all sent SMS messages';

-- ============================================
-- 5. TRANSACTIONS TABLE
-- ============================================
-- Financial transaction history
CREATE TABLE IF NOT EXISTS public.transactions (
    id UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
    user_id UUID NOT NULL REFERENCES public.users(id) ON DELETE CASCADE,
    
    -- Transaction Details
    type VARCHAR(20) NOT NULL CHECK (type IN ('earning', 'withdrawal', 'bonus', 'referral', 'daily_checkin', 'ad_reward', 'spin')),
    amount DECIMAL(10, 2) NOT NULL,
    description TEXT,
    
    -- Metadata
    reference_id UUID, -- Links to task, withdrawal, etc.
    metadata JSONB,
    
    -- Timestamp
    created_at TIMESTAMPTZ DEFAULT NOW()
);

CREATE INDEX IF NOT EXISTS idx_transactions_user_id ON public.transactions(user_id);
CREATE INDEX IF NOT EXISTS idx_transactions_type ON public.transactions(type);
CREATE INDEX IF NOT EXISTS idx_transactions_created_at ON public.transactions(created_at DESC);

COMMENT ON TABLE public.transactions IS 'Complete financial transaction history';
COMMENT ON COLUMN public.transactions.reference_id IS 'Links to related records (task_id, withdrawal_id, etc.)';

-- ============================================
-- 6. WITHDRAWALS TABLE
-- ============================================
-- Withdrawal requests and processing status
CREATE TABLE IF NOT EXISTS public.withdrawals (
    id UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
    user_id UUID NOT NULL REFERENCES public.users(id) ON DELETE CASCADE,
    
    -- Withdrawal Details
    amount DECIMAL(10, 2) NOT NULL,
    method VARCHAR(50) NOT NULL, -- 'bank', 'upi', 'paytm', etc.
    account_details JSONB NOT NULL,
    
    -- Status
    status VARCHAR(20) DEFAULT 'pending' CHECK (status IN ('pending', 'processing', 'completed', 'rejected', 'cancelled')),
    
    -- Processing Info
    processed_by VARCHAR(255),
    processing_note TEXT,
    transaction_id VARCHAR(255), -- External payment transaction ID
    
    -- Timestamps
    requested_at TIMESTAMPTZ DEFAULT NOW(),
    processed_at TIMESTAMPTZ
);

CREATE INDEX IF NOT EXISTS idx_withdrawals_user_id ON public.withdrawals(user_id);
CREATE INDEX IF NOT EXISTS idx_withdrawals_status ON public.withdrawals(status);
CREATE INDEX IF NOT EXISTS idx_withdrawals_requested_at ON public.withdrawals(requested_at DESC);

COMMENT ON TABLE public.withdrawals IS 'User withdrawal requests and processing status';
COMMENT ON COLUMN public.withdrawals.account_details IS 'Encrypted bank/UPI details (JSONB format)';

-- ============================================
-- 7. APP CONFIG TABLE
-- ============================================
-- Application-wide configuration (feature flags, ads, etc.)
CREATE TABLE IF NOT EXISTS public.app_config (
    key VARCHAR(100) PRIMARY KEY,
    value JSONB NOT NULL,
    description TEXT,
    updated_at TIMESTAMPTZ DEFAULT NOW()
);

-- Insert default configurations
INSERT INTO public.app_config (key, value, description) VALUES
    ('admob_config', '{"units": ["ca-app-pub-3940256099942544/5224354917"]}'::jsonb, 'AdMob ad unit IDs'),
    ('whatsapp_config', '{"group_link": "https://chat.whatsapp.com/example", "community_link": "https://chat.whatsapp.com/example"}'::jsonb, 'WhatsApp group and community links'),
    ('earn_more_config', '{"tasks": [{"id": "task1", "name": "Watch Video", "reward": 5, "icon": "video"}]}'::jsonb, 'Earn more task configurations'),
    ('spin_rewards', '[1, 2, 5, 10, 20, 50, 100, 0]'::jsonb, 'Spin wheel reward values'),
    ('min_withdrawal', '{"amount": 100}'::jsonb, 'Minimum withdrawal amount'),
    ('referral_bonus', '{"amount": 10}'::jsonb, 'Referral bonus per successful referral')
ON CONFLICT (key) DO NOTHING;

COMMENT ON TABLE public.app_config IS 'Dynamic application configuration';

-- ============================================
-- 8. OTP VERIFICATIONS TABLE
-- ============================================
-- OTP codes for password reset and verification
CREATE TABLE IF NOT EXISTS public.otp_verifications (
    id UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
    phone VARCHAR(15) NOT NULL,
    otp_code VARCHAR(6) NOT NULL,
    
    -- Status
    verified BOOLEAN DEFAULT FALSE,
    
    -- Timestamps
    created_at TIMESTAMPTZ DEFAULT NOW(),
    expires_at TIMESTAMPTZ DEFAULT (NOW() + INTERVAL '10 minutes'),
    verified_at TIMESTAMPTZ
);

CREATE INDEX IF NOT EXISTS idx_otp_phone ON public.otp_verifications(phone);
CREATE INDEX IF NOT EXISTS idx_otp_expires_at ON public.otp_verifications(expires_at);

COMMENT ON TABLE public.otp_verifications IS 'OTP verification for password reset';
COMMENT ON COLUMN public.otp_verifications.expires_at IS 'OTP expires after 10 minutes';

-- ============================================
-- GRANT PERMISSIONS
-- ============================================
-- Allow anon and authenticated users to access tables
GRANT ALL ON ALL TABLES IN SCHEMA public TO anon, authenticated;
GRANT ALL ON ALL SEQUENCES IN SCHEMA public TO anon, authenticated;

-- ============================================
-- SETUP COMPLETE
-- ============================================
-- Next: Execute 02_rpc_functions.sql
