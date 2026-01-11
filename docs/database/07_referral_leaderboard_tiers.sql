-- ============================================
-- REFERRAL LEADERBOARD & TIERED REWARDS SYSTEM
-- ============================================
-- Version: 1.2
-- Description: Enhanced referral system with leaderboard and tiered rewards
-- Execute after 06_referral_system_enhancement.sql
-- ============================================

-- ============================================
-- 1. REFERRAL TIERS TABLE
-- ============================================
-- Define tier levels and their benefits
CREATE TABLE IF NOT EXISTS public.referral_tiers (
    id UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
    tier_name VARCHAR(50) NOT NULL UNIQUE,
    tier_level INTEGER NOT NULL UNIQUE,
    min_referrals INTEGER NOT NULL,
    max_referrals INTEGER,
    
    -- Rewards per tier
    reward_multiplier DECIMAL(3, 2) DEFAULT 1.00,
    bonus_coins INTEGER DEFAULT 0,
    badge_color VARCHAR(20),
    badge_icon VARCHAR(50),
    
    -- Tier benefits description
    benefits TEXT,
    
    -- Timestamps
    created_at TIMESTAMPTZ DEFAULT NOW(),
    updated_at TIMESTAMPTZ DEFAULT NOW()
);

-- Insert default tier configurations
INSERT INTO public.referral_tiers (tier_name, tier_level, min_referrals, max_referrals, reward_multiplier, bonus_coins, badge_color, badge_icon, benefits) VALUES
    ('Bronze', 1, 1, 10, 1.00, 0, '#CD7F32', 'bronze_badge', 'Basic referral rewards: ₹10 per referral'),
    ('Silver', 2, 11, 50, 1.25, 50, '#C0C0C0', 'silver_badge', 'Enhanced rewards: ₹12.50 per referral + 50 bonus coins'),
    ('Gold', 3, 51, 99999, 1.50, 150, '#FFD700', 'gold_badge', 'Premium rewards: ₹15 per referral + 150 bonus coins + exclusive perks')
ON CONFLICT (tier_name) DO UPDATE SET
    tier_level = EXCLUDED.tier_level,
    min_referrals = EXCLUDED.min_referrals,
    max_referrals = EXCLUDED.max_referrals,
    reward_multiplier = EXCLUDED.reward_multiplier,
    bonus_coins = EXCLUDED.bonus_coins,
    badge_color = EXCLUDED.badge_color,
    badge_icon = EXCLUDED.badge_icon,
    benefits = EXCLUDED.benefits,
    updated_at = NOW();

CREATE INDEX IF NOT EXISTS idx_referral_tiers_level ON public.referral_tiers(tier_level);

COMMENT ON TABLE public.referral_tiers IS 'Referral tier definitions and reward structures';

-- ============================================
-- 2. ADD TIER TRACKING TO USERS TABLE
-- ============================================
-- Add current tier information to users
ALTER TABLE public.users 
ADD COLUMN IF NOT EXISTS current_tier INTEGER DEFAULT 1,
ADD COLUMN IF NOT EXISTS tier_updated_at TIMESTAMPTZ DEFAULT NOW();

CREATE INDEX IF NOT EXISTS idx_users_current_tier ON public.users(current_tier);

COMMENT ON COLUMN public.users.current_tier IS 'Current referral tier level (1=Bronze, 2=Silver, 3=Gold)';
COMMENT ON COLUMN public.users.tier_updated_at IS 'When the user last changed tiers';

-- ============================================
-- 3. REFERRAL LEADERBOARD VIEW
-- ============================================
-- Materialized view for efficient leaderboard queries
CREATE MATERIALIZED VIEW IF NOT EXISTS public.referral_leaderboard AS
SELECT 
    u.id,
    u.phone,
    u.referral_code,
    u.referral_count,
    u.referral_reward_earned,
    u.current_tier,
    t.tier_name,
    t.badge_color,
    t.badge_icon,
    ROW_NUMBER() OVER (ORDER BY u.referral_count DESC, u.referral_reward_earned DESC) as rank
FROM public.users u
LEFT JOIN public.referral_tiers t ON u.current_tier = t.tier_level
WHERE u.referral_count > 0
ORDER BY u.referral_count DESC, u.referral_reward_earned DESC;

-- Create unique index for concurrent refresh
CREATE UNIQUE INDEX IF NOT EXISTS idx_leaderboard_user_id ON public.referral_leaderboard(id);

-- Index for rank lookups
CREATE INDEX IF NOT EXISTS idx_leaderboard_rank ON public.referral_leaderboard(rank);
CREATE INDEX IF NOT EXISTS idx_leaderboard_referral_code ON public.referral_leaderboard(referral_code);

COMMENT ON MATERIALIZED VIEW public.referral_leaderboard IS 'Real-time referral leaderboard rankings';

-- ============================================
-- 4. REFERRAL ANALYTICS TABLE
-- ============================================
-- Store detailed analytics per user
CREATE TABLE IF NOT EXISTS public.referral_analytics (
    id UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
    user_id UUID NOT NULL REFERENCES public.users(id) ON DELETE CASCADE,
    
    -- Success metrics
    total_referrals INTEGER DEFAULT 0,
    successful_referrals INTEGER DEFAULT 0,
    pending_referrals INTEGER DEFAULT 0,
    failed_referrals INTEGER DEFAULT 0,
    
    -- Revenue metrics
    total_rewards_earned DECIMAL(10, 2) DEFAULT 0.00,
    tier_bonus_earned DECIMAL(10, 2) DEFAULT 0.00,
    
    -- Engagement metrics
    conversion_rate DECIMAL(5, 2) DEFAULT 0.00, -- Percentage
    avg_referral_value DECIMAL(10, 2) DEFAULT 0.00,
    
    -- Time-based metrics
    last_referral_date TIMESTAMPTZ,
    best_performing_day VARCHAR(20), -- Monday, Tuesday, etc.
    
    -- Timestamps
    created_at TIMESTAMPTZ DEFAULT NOW(),
    updated_at TIMESTAMPTZ DEFAULT NOW(),
    
    CONSTRAINT unique_user_analytics UNIQUE(user_id)
);

CREATE INDEX IF NOT EXISTS idx_referral_analytics_user ON public.referral_analytics(user_id);

COMMENT ON TABLE public.referral_analytics IS 'Detailed referral performance analytics per user';

-- ============================================
-- 5. FUNCTION: UPDATE USER TIER
-- ============================================
-- Automatically update user tier based on referral count
CREATE OR REPLACE FUNCTION update_user_tier()
RETURNS TRIGGER AS $$
DECLARE
    new_tier INTEGER;
BEGIN
    -- Determine new tier based on referral count
    SELECT tier_level INTO new_tier
    FROM public.referral_tiers
    WHERE NEW.referral_count >= min_referrals 
      AND (max_referrals IS NULL OR NEW.referral_count <= max_referrals)
    ORDER BY tier_level DESC
    LIMIT 1;
    
    -- Update tier if changed
    IF new_tier IS NOT NULL AND new_tier != NEW.current_tier THEN
        NEW.current_tier := new_tier;
        NEW.tier_updated_at := NOW();
        
        -- Award tier upgrade bonus
        UPDATE public.users 
        SET coins = coins + (SELECT bonus_coins FROM public.referral_tiers WHERE tier_level = new_tier)
        WHERE id = NEW.id;
    END IF;
    
    RETURN NEW;
END;
$$ LANGUAGE plpgsql;

-- Drop and recreate trigger
DROP TRIGGER IF EXISTS trg_update_user_tier ON public.users;
CREATE TRIGGER trg_update_user_tier
    BEFORE UPDATE OF referral_count ON public.users
    FOR EACH ROW
    EXECUTE FUNCTION update_user_tier();

-- ============================================
-- 6. FUNCTION: UPDATE REFERRAL ANALYTICS
-- ============================================
-- Update analytics when referral transactions occur
CREATE OR REPLACE FUNCTION update_referral_analytics()
RETURNS TRIGGER AS $$
BEGIN
    -- Insert or update analytics record
    INSERT INTO public.referral_analytics (
        user_id,
        total_referrals,
        successful_referrals,
        total_rewards_earned,
        last_referral_date,
        conversion_rate,
        avg_referral_value,
        updated_at
    )
    SELECT 
        NEW.referrer_id,
        COUNT(*) as total_referrals,
        COUNT(CASE WHEN status = 'completed' THEN 1 END) as successful_referrals,
        SUM(CASE WHEN status = 'completed' THEN referrer_reward ELSE 0 END) as total_rewards,
        MAX(created_at) as last_referral,
        (COUNT(CASE WHEN status = 'completed' THEN 1 END)::DECIMAL / COUNT(*)::DECIMAL * 100) as conv_rate,
        AVG(CASE WHEN status = 'completed' THEN referrer_reward ELSE 0 END) as avg_value,
        NOW()
    FROM public.referral_transactions
    WHERE referrer_id = NEW.referrer_id
    GROUP BY referrer_id
    ON CONFLICT (user_id) DO UPDATE SET
        total_referrals = EXCLUDED.total_referrals,
        successful_referrals = EXCLUDED.successful_referrals,
        total_rewards_earned = EXCLUDED.total_rewards_earned,
        last_referral_date = EXCLUDED.last_referral_date,
        conversion_rate = EXCLUDED.conversion_rate,
        avg_referral_value = EXCLUDED.avg_referral_value,
        updated_at = NOW();
    
    RETURN NEW;
END;
$$ LANGUAGE plpgsql;

-- Trigger on referral transactions
DROP TRIGGER IF EXISTS trg_update_referral_analytics ON public.referral_transactions;
CREATE TRIGGER trg_update_referral_analytics
    AFTER INSERT OR UPDATE ON public.referral_transactions
    FOR EACH ROW
    EXECUTE FUNCTION update_referral_analytics();

-- ============================================
-- 7. RPC FUNCTION: GET USER LEADERBOARD POSITION
-- ============================================
-- Get a specific user's rank and surrounding entries
CREATE OR REPLACE FUNCTION get_user_leaderboard_position(user_phone VARCHAR)
RETURNS TABLE (
    user_rank BIGINT,
    user_referrals INTEGER,
    user_tier_name VARCHAR,
    above_user JSONB,
    below_user JSONB
) AS $$
DECLARE
    user_position BIGINT;
BEGIN
    -- Refresh leaderboard materialized view
    REFRESH MATERIALIZED VIEW CONCURRENTLY public.referral_leaderboard;
    
    -- Get user's rank
    SELECT rank INTO user_position
    FROM public.referral_leaderboard
    WHERE referral_code = user_phone;
    
    -- Return user position with context
    RETURN QUERY
    SELECT 
        l.rank as user_rank,
        l.referral_count as user_referrals,
        l.tier_name as user_tier_name,
        (SELECT json_build_object(
            'rank', rank,
            'referrals', referral_count,
            'tier', tier_name
        ) FROM public.referral_leaderboard WHERE rank = user_position - 1) as above_user,
        (SELECT json_build_object(
            'rank', rank,
            'referrals', referral_count,
            'tier', tier_name
        ) FROM public.referral_leaderboard WHERE rank = user_position + 1) as below_user
    FROM public.referral_leaderboard l
    WHERE l.referral_code = user_phone;
END;
$$ LANGUAGE plpgsql;

-- ============================================
-- 8. RPC FUNCTION: GET TOP REFERRERS
-- ============================================
-- Get top N referrers for leaderboard display
CREATE OR REPLACE FUNCTION get_top_referrers(limit_count INTEGER DEFAULT 50)
RETURNS TABLE (
    rank BIGINT,
    phone VARCHAR,
    referral_code VARCHAR,
    referral_count INTEGER,
    rewards_earned DECIMAL,
    tier_name VARCHAR,
    badge_color VARCHAR,
    badge_icon VARCHAR
) AS $$
BEGIN
    -- Refresh leaderboard
    REFRESH MATERIALIZED VIEW CONCURRENTLY public.referral_leaderboard;
    
    -- Return top entries
    RETURN QUERY
    SELECT 
        l.rank,
        l.phone,
        l.referral_code,
        l.referral_count,
        l.referral_reward_earned,
        l.tier_name,
        l.badge_color,
        l.badge_icon
    FROM public.referral_leaderboard l
    ORDER BY l.rank
    LIMIT limit_count;
END;
$$ LANGUAGE plpgsql;

-- ============================================
-- 9. SCHEDULED LEADERBOARD REFRESH
-- ============================================
-- Note: This requires pg_cron extension for production
-- For now, leaderboard is refreshed on-demand in RPC functions
COMMENT ON MATERIALIZED VIEW public.referral_leaderboard IS 
    'Leaderboard is refreshed on-demand during queries. 
     For production, consider using pg_cron for hourly refresh:
     SELECT cron.schedule(''refresh-leaderboard'', ''0 * * * *'', 
         ''REFRESH MATERIALIZED VIEW CONCURRENTLY public.referral_leaderboard'')';

-- ============================================
-- 10. GRANT PERMISSIONS
-- ============================================
GRANT SELECT ON public.referral_tiers TO anon, authenticated;
GRANT SELECT ON public.referral_leaderboard TO anon, authenticated;
GRANT SELECT ON public.referral_analytics TO anon, authenticated;
GRANT EXECUTE ON FUNCTION get_user_leaderboard_position(VARCHAR) TO anon, authenticated;
GRANT EXECUTE ON FUNCTION get_top_referrers(INTEGER) TO anon, authenticated;

-- ============================================
-- VERIFICATION & TESTING
-- ============================================
DO $$
BEGIN
    RAISE NOTICE 'Referral leaderboard & tiers system setup completed!';
    RAISE NOTICE 'Created tables: referral_tiers, referral_analytics';
    RAISE NOTICE 'Created materialized view: referral_leaderboard';
    RAISE NOTICE 'Created RPC functions: get_user_leaderboard_position, get_top_referrers';
    RAISE NOTICE 'Tier system: Bronze (1-10), Silver (11-50), Gold (51+)';
END $$;
