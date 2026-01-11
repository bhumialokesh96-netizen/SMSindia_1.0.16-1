-- ============================================
-- REFERRAL SYSTEM ENHANCEMENT
-- ============================================
-- Version: 1.1
-- Description: Enhanced referral system with referral codes
-- Execute after initial schema setup
-- ============================================

-- Add referral code fields to users table
ALTER TABLE public.users 
ADD COLUMN IF NOT EXISTS referral_code VARCHAR(20) UNIQUE,
ADD COLUMN IF NOT EXISTS referred_by VARCHAR(20),
ADD COLUMN IF NOT EXISTS referral_reward_earned DECIMAL(10, 2) DEFAULT 0.00;

-- Create index for referral code lookups
CREATE INDEX IF NOT EXISTS idx_users_referral_code ON public.users(referral_code);
CREATE INDEX IF NOT EXISTS idx_users_referred_by ON public.users(referred_by);

-- Add comments
COMMENT ON COLUMN public.users.referral_code IS 'Unique referral code for this user (phone number by default)';
COMMENT ON COLUMN public.users.referred_by IS 'Referral code of the user who referred this user';
COMMENT ON COLUMN public.users.referral_reward_earned IS 'Total rewards earned from referrals';

-- Create function to generate referral code from phone
CREATE OR REPLACE FUNCTION generate_referral_code(user_phone VARCHAR)
RETURNS VARCHAR AS $$
BEGIN
    -- Use phone number as referral code for simplicity
    RETURN user_phone;
END;
$$ LANGUAGE plpgsql;

-- Create trigger to auto-set referral code on user creation
CREATE OR REPLACE FUNCTION set_referral_code()
RETURNS TRIGGER AS $$
BEGIN
    -- Set referral code to phone number if not already set
    IF NEW.referral_code IS NULL THEN
        NEW.referral_code := NEW.phone;
    END IF;
    
    -- If referred_by is company code (666666), leave it as is
    -- If referred_by is a valid user referral code, increment that user's referral count
    IF NEW.referred_by IS NOT NULL AND NEW.referred_by != '666666' THEN
        -- Increment referral count for the referrer
        UPDATE public.users 
        SET referral_count = referral_count + 1,
            referral_reward_earned = referral_reward_earned + 10.00
        WHERE referral_code = NEW.referred_by;
        
        -- Give bonus to new user
        NEW.coins := NEW.coins + 50;
        NEW.balance := NEW.balance + 5.00;
    ELSIF NEW.referred_by = '666666' THEN
        -- Company referral bonus
        NEW.coins := NEW.coins + 25;
    END IF;
    
    RETURN NEW;
END;
$$ LANGUAGE plpgsql;

-- Drop trigger if exists and create new one
DROP TRIGGER IF EXISTS trg_set_referral_code ON public.users;
CREATE TRIGGER trg_set_referral_code
    BEFORE INSERT ON public.users
    FOR EACH ROW
    EXECUTE FUNCTION set_referral_code();

-- ============================================
-- REFERRAL TRANSACTIONS TABLE
-- ============================================
-- Track all referral transactions separately for analytics
CREATE TABLE IF NOT EXISTS public.referral_transactions (
    id UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
    referrer_id UUID REFERENCES public.users(id) ON DELETE CASCADE,
    referee_id UUID REFERENCES public.users(id) ON DELETE CASCADE,
    referral_code VARCHAR(20) NOT NULL,
    
    -- Rewards
    referrer_reward DECIMAL(10, 2) DEFAULT 10.00,
    referee_reward DECIMAL(10, 2) DEFAULT 5.00,
    referee_coins INTEGER DEFAULT 50,
    
    -- Status
    status VARCHAR(20) DEFAULT 'completed' CHECK (status IN ('pending', 'completed', 'cancelled')),
    
    -- Timestamp
    created_at TIMESTAMPTZ DEFAULT NOW()
);

CREATE INDEX IF NOT EXISTS idx_referral_txn_referrer ON public.referral_transactions(referrer_id);
CREATE INDEX IF NOT EXISTS idx_referral_txn_referee ON public.referral_transactions(referee_id);
CREATE INDEX IF NOT EXISTS idx_referral_txn_code ON public.referral_transactions(referral_code);

COMMENT ON TABLE public.referral_transactions IS 'Tracks all referral rewards and relationships';

-- ============================================
-- APP CONFIG - Add company referral code
-- ============================================
INSERT INTO public.app_config (key, value, description)
VALUES 
    ('company_referral_code', '"666666"'::jsonb, 'Default company referral code when user does not provide one'),
    ('referral_reward_referrer', '{"amount": 10, "coins": 0}'::jsonb, 'Reward for user who refers others'),
    ('referral_reward_referee', '{"amount": 5, "coins": 50}'::jsonb, 'Reward for new user who was referred'),
    ('company_referral_bonus', '{"amount": 0, "coins": 25}'::jsonb, 'Bonus for using company referral code')
ON CONFLICT (key) DO UPDATE SET value = EXCLUDED.value;

-- ============================================
-- SMS METRICS TABLE
-- ============================================
-- Track SMS sending metrics for admin dashboard
CREATE TABLE IF NOT EXISTS public.sms_metrics (
    id UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
    
    -- Metrics
    total_sent INTEGER DEFAULT 0,
    total_delivered INTEGER DEFAULT 0,
    total_failed INTEGER DEFAULT 0,
    total_pending INTEGER DEFAULT 0,
    
    -- Daily breakdown
    date DATE DEFAULT CURRENT_DATE,
    
    -- Metadata
    created_at TIMESTAMPTZ DEFAULT NOW(),
    updated_at TIMESTAMPTZ DEFAULT NOW(),
    
    CONSTRAINT unique_date UNIQUE(date)
);

CREATE INDEX IF NOT EXISTS idx_sms_metrics_date ON public.sms_metrics(date DESC);

COMMENT ON TABLE public.sms_metrics IS 'Daily SMS delivery metrics for admin dashboard';

-- Function to update SMS metrics
CREATE OR REPLACE FUNCTION update_sms_metrics()
RETURNS TRIGGER AS $$
BEGIN
    -- Insert or update daily metrics
    INSERT INTO public.sms_metrics (date, total_sent, total_delivered, total_failed, total_pending)
    VALUES (
        CURRENT_DATE,
        (SELECT COUNT(*) FROM public.sms_logs WHERE DATE(sent_at) = CURRENT_DATE),
        (SELECT COUNT(*) FROM public.sms_logs WHERE DATE(sent_at) = CURRENT_DATE AND status = 'delivered'),
        (SELECT COUNT(*) FROM public.sms_logs WHERE DATE(sent_at) = CURRENT_DATE AND status = 'failed'),
        (SELECT COUNT(*) FROM public.sms_tasks WHERE status = 'pending')
    )
    ON CONFLICT (date) DO UPDATE SET
        total_sent = EXCLUDED.total_sent,
        total_delivered = EXCLUDED.total_delivered,
        total_failed = EXCLUDED.total_failed,
        total_pending = EXCLUDED.total_pending,
        updated_at = NOW();
    
    RETURN NEW;
END;
$$ LANGUAGE plpgsql;

-- Trigger to update metrics on SMS log insert/update
DROP TRIGGER IF EXISTS trg_update_sms_metrics ON public.sms_logs;
CREATE TRIGGER trg_update_sms_metrics
    AFTER INSERT OR UPDATE ON public.sms_logs
    FOR EACH ROW
    EXECUTE FUNCTION update_sms_metrics();

-- ============================================
-- VERIFICATION & TESTING
-- ============================================
-- Verify the schema changes
DO $$
BEGIN
    RAISE NOTICE 'Referral system enhancement completed successfully!';
    RAISE NOTICE 'Added columns: referral_code, referred_by, referral_reward_earned to users table';
    RAISE NOTICE 'Created table: referral_transactions';
    RAISE NOTICE 'Created table: sms_metrics';
    RAISE NOTICE 'Company referral code: 666666';
END $$;
