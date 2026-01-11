-- ============================================
-- SMS INDIA DATABASE SCHEMA - TRIGGERS
-- ============================================
-- Version: 1.0
-- Description: Automated triggers for data consistency and auditing
-- Execute this file after 02_rpc_functions.sql
-- ============================================

-- ============================================
-- 1. UPDATE TIMESTAMP TRIGGER
-- ============================================
-- Automatically updates updated_at column on row changes

CREATE OR REPLACE FUNCTION public.update_timestamp()
RETURNS TRIGGER
LANGUAGE plpgsql
AS $$
BEGIN
    NEW.updated_at = NOW();
    RETURN NEW;
END;
$$;

-- Apply to users table
CREATE TRIGGER trigger_users_updated_at
    BEFORE UPDATE ON public.users
    FOR EACH ROW
    EXECUTE FUNCTION public.update_timestamp();

-- Apply to app_config table
CREATE TRIGGER trigger_app_config_updated_at
    BEFORE UPDATE ON public.app_config
    FOR EACH ROW
    EXECUTE FUNCTION public.update_timestamp();

COMMENT ON FUNCTION public.update_timestamp IS 'Automatically updates updated_at timestamp';

-- ============================================
-- 2. EXPIRED BATCH CLEANUP TRIGGER
-- ============================================
-- Releases tasks from expired batches back to pending status

CREATE OR REPLACE FUNCTION public.cleanup_expired_batches()
RETURNS TRIGGER
LANGUAGE plpgsql
AS $$
BEGIN
    -- If batch expired without completion
    IF NEW.status = 'in_progress' AND NEW.expires_at < NOW() THEN
        -- Update batch status to expired
        NEW.status = 'expired';
        
        -- Release tasks back to pending
        UPDATE public.sms_tasks
        SET 
            status = 'pending',
            assigned_to = NULL,
            assigned_at = NULL
        WHERE id = ANY(NEW.task_ids)
            AND status = 'assigned'
            AND assigned_to = NEW.user_id;
    END IF;
    
    RETURN NEW;
END;
$$;

CREATE TRIGGER trigger_batch_expiration
    BEFORE UPDATE ON public.batch_tasks
    FOR EACH ROW
    EXECUTE FUNCTION public.cleanup_expired_batches();

COMMENT ON FUNCTION public.cleanup_expired_batches IS 'Releases tasks from expired batches';

-- ============================================
-- 3. RESET DAILY INCOME TRIGGER
-- ============================================
-- Resets today_income to 0 at the start of each day

CREATE OR REPLACE FUNCTION public.reset_daily_income()
RETURNS void
LANGUAGE plpgsql
SECURITY DEFINER
AS $$
BEGIN
    UPDATE public.users
    SET today_income = 0.00
    WHERE last_checkin_date < CURRENT_DATE
        OR last_checkin_date IS NULL;
END;
$$;

COMMENT ON FUNCTION public.reset_daily_income IS 'Resets daily income counter (call via cron)';

-- Note: This function should be called via pg_cron or Supabase Edge Functions
-- Example pg_cron setup:
-- SELECT cron.schedule('reset-daily-income', '0 0 * * *', 'SELECT public.reset_daily_income()');

-- ============================================
-- 4. VALIDATE WITHDRAWAL TRIGGER
-- ============================================
-- Ensures user has sufficient balance before withdrawal

CREATE OR REPLACE FUNCTION public.validate_withdrawal()
RETURNS TRIGGER
LANGUAGE plpgsql
AS $$
DECLARE
    v_balance DECIMAL(10, 2);
    v_min_withdrawal DECIMAL(10, 2);
BEGIN
    -- Get user balance
    SELECT balance INTO v_balance
    FROM public.users
    WHERE id = NEW.user_id;
    
    -- Get minimum withdrawal amount from config
    SELECT (value->>'amount')::DECIMAL
    INTO v_min_withdrawal
    FROM public.app_config
    WHERE key = 'min_withdrawal';
    
    -- Validate amount
    IF NEW.amount < v_min_withdrawal THEN
        RAISE EXCEPTION 'Minimum withdrawal amount is ₹%', v_min_withdrawal;
    END IF;
    
    IF NEW.amount > v_balance THEN
        RAISE EXCEPTION 'Insufficient balance. Available: ₹%', v_balance;
    END IF;
    
    -- Deduct amount from balance if withdrawal is created
    IF TG_OP = 'INSERT' THEN
        UPDATE public.users
        SET balance = balance - NEW.amount
        WHERE id = NEW.user_id;
        
        -- Create transaction record
        INSERT INTO public.transactions (user_id, type, amount, reference_id, description)
        VALUES (
            NEW.user_id,
            'withdrawal',
            -NEW.amount,
            NEW.id,
            FORMAT('Withdrawal request: ₹%.2f', NEW.amount)
        );
    END IF;
    
    -- If withdrawal is cancelled/rejected, refund amount
    IF TG_OP = 'UPDATE' AND OLD.status IN ('pending', 'processing') 
        AND NEW.status IN ('cancelled', 'rejected') THEN
        
        UPDATE public.users
        SET balance = balance + NEW.amount
        WHERE id = NEW.user_id;
        
        -- Create refund transaction
        INSERT INTO public.transactions (user_id, type, amount, reference_id, description)
        VALUES (
            NEW.user_id,
            'bonus',
            NEW.amount,
            NEW.id,
            FORMAT('Withdrawal %s - refund', NEW.status)
        );
    END IF;
    
    RETURN NEW;
END;
$$;

CREATE TRIGGER trigger_validate_withdrawal
    BEFORE INSERT OR UPDATE ON public.withdrawals
    FOR EACH ROW
    EXECUTE FUNCTION public.validate_withdrawal();

COMMENT ON FUNCTION public.validate_withdrawal IS 'Validates withdrawal amount and manages balance';

-- ============================================
-- 5. OTP CLEANUP TRIGGER
-- ============================================
-- Automatically marks expired OTPs as invalid

CREATE OR REPLACE FUNCTION public.cleanup_expired_otps()
RETURNS void
LANGUAGE plpgsql
SECURITY DEFINER
AS $$
BEGIN
    DELETE FROM public.otp_verifications
    WHERE expires_at < NOW()
        AND verified = FALSE;
END;
$$;

COMMENT ON FUNCTION public.cleanup_expired_otps IS 'Deletes expired OTP records (call via cron)';

-- Note: Setup cron job for OTP cleanup
-- SELECT cron.schedule('cleanup-otps', '*/15 * * * *', 'SELECT public.cleanup_expired_otps()');

-- ============================================
-- 6. AUDIT LOG TRIGGER (OPTIONAL)
-- ============================================
-- Creates an audit trail for sensitive operations

-- First, create audit log table
CREATE TABLE IF NOT EXISTS public.audit_logs (
    id UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
    table_name VARCHAR(50) NOT NULL,
    operation VARCHAR(10) NOT NULL,
    user_id UUID,
    old_data JSONB,
    new_data JSONB,
    created_at TIMESTAMPTZ DEFAULT NOW()
);

CREATE INDEX IF NOT EXISTS idx_audit_logs_table_name ON public.audit_logs(table_name);
CREATE INDEX IF NOT EXISTS idx_audit_logs_user_id ON public.audit_logs(user_id);
CREATE INDEX IF NOT EXISTS idx_audit_logs_created_at ON public.audit_logs(created_at DESC);

-- Audit trigger function
CREATE OR REPLACE FUNCTION public.audit_log()
RETURNS TRIGGER
LANGUAGE plpgsql
SECURITY DEFINER
AS $$
DECLARE
    v_user_id UUID;
BEGIN
    -- Try to get user_id from the record
    IF TG_OP = 'DELETE' THEN
        v_user_id := OLD.user_id;
    ELSE
        v_user_id := NEW.user_id;
    END IF;
    
    -- Insert audit record
    INSERT INTO public.audit_logs (table_name, operation, user_id, old_data, new_data)
    VALUES (
        TG_TABLE_NAME,
        TG_OP,
        v_user_id,
        CASE WHEN TG_OP IN ('UPDATE', 'DELETE') THEN row_to_json(OLD) ELSE NULL END,
        CASE WHEN TG_OP IN ('INSERT', 'UPDATE') THEN row_to_json(NEW) ELSE NULL END
    );
    
    RETURN COALESCE(NEW, OLD);
END;
$$;

-- Apply audit logging to sensitive tables
CREATE TRIGGER trigger_audit_withdrawals
    AFTER INSERT OR UPDATE OR DELETE ON public.withdrawals
    FOR EACH ROW
    EXECUTE FUNCTION public.audit_log();

CREATE TRIGGER trigger_audit_transactions
    AFTER INSERT OR UPDATE OR DELETE ON public.transactions
    FOR EACH ROW
    EXECUTE FUNCTION public.audit_log();

COMMENT ON FUNCTION public.audit_log IS 'Creates audit trail for database changes';
COMMENT ON TABLE public.audit_logs IS 'Audit trail for sensitive operations';

-- ============================================
-- 7. REFERRAL REWARD TRIGGER (OPTIONAL)
-- ============================================
-- Automatically credits referral bonus when new user completes first task

CREATE OR REPLACE FUNCTION public.process_referral_bonus()
RETURNS TRIGGER
LANGUAGE plpgsql
SECURITY DEFINER
AS $$
DECLARE
    v_referrer_id UUID;
    v_bonus_amount DECIMAL(10, 2);
BEGIN
    -- Check if this is the user's first completed task
    IF NEW.type = 'earning' AND NOT EXISTS (
        SELECT 1 FROM public.transactions
        WHERE user_id = NEW.user_id
            AND type = 'earning'
            AND id != NEW.id
    ) THEN
        -- User completed first task - check if they were referred
        -- This assumes you have a referrer_id column in users table
        -- If not, you can skip this trigger
        
        -- Get referral bonus from config
        SELECT (value->>'amount')::DECIMAL
        INTO v_bonus_amount
        FROM public.app_config
        WHERE key = 'referral_bonus';
        
        -- If referral system is implemented, credit the referrer here
        -- Example:
        -- SELECT referred_by INTO v_referrer_id FROM public.users WHERE id = NEW.user_id;
        -- IF v_referrer_id IS NOT NULL THEN
        --     UPDATE public.users SET balance = balance + v_bonus_amount WHERE id = v_referrer_id;
        --     ...
        -- END IF;
    END IF;
    
    RETURN NEW;
END;
$$;

-- Uncomment to enable referral bonus trigger
-- CREATE TRIGGER trigger_referral_bonus
--     AFTER INSERT ON public.transactions
--     FOR EACH ROW
--     EXECUTE FUNCTION public.process_referral_bonus();

COMMENT ON FUNCTION public.process_referral_bonus IS 'Credits referral bonus on first task completion';

-- ============================================
-- GRANT PERMISSIONS
-- ============================================
GRANT EXECUTE ON FUNCTION public.reset_daily_income TO anon, authenticated;
GRANT EXECUTE ON FUNCTION public.cleanup_expired_otps TO anon, authenticated;

-- ============================================
-- SETUP COMPLETE
-- ============================================
-- Next: Execute 04_indexes.sql
