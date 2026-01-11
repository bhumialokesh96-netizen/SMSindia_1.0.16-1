-- ============================================
-- SMS INDIA DATABASE SCHEMA - ROW LEVEL SECURITY
-- ============================================
-- Version: 1.0
-- Description: Security policies for data access control
-- Execute this file after 04_indexes.sql
-- ============================================

-- ============================================
-- ENABLE RLS ON ALL TABLES
-- ============================================

ALTER TABLE public.users ENABLE ROW LEVEL SECURITY;
ALTER TABLE public.sms_tasks ENABLE ROW LEVEL SECURITY;
ALTER TABLE public.batch_tasks ENABLE ROW LEVEL SECURITY;
ALTER TABLE public.sms_logs ENABLE ROW LEVEL SECURITY;
ALTER TABLE public.transactions ENABLE ROW LEVEL SECURITY;
ALTER TABLE public.withdrawals ENABLE ROW LEVEL SECURITY;
ALTER TABLE public.app_config ENABLE ROW LEVEL SECURITY;
ALTER TABLE public.otp_verifications ENABLE ROW LEVEL SECURITY;
ALTER TABLE public.audit_logs ENABLE ROW LEVEL SECURITY;

-- ============================================
-- USERS TABLE POLICIES
-- ============================================

-- Allow users to read their own data
CREATE POLICY "Users can view own profile"
ON public.users
FOR SELECT
TO authenticated
USING (auth.uid() = id);

-- Allow anon users to create accounts (signup)
CREATE POLICY "Anyone can create user"
ON public.users
FOR INSERT
TO anon, authenticated
WITH CHECK (true);

-- Allow users to update their own profile
CREATE POLICY "Users can update own profile"
ON public.users
FOR UPDATE
TO authenticated
USING (auth.uid() = id)
WITH CHECK (auth.uid() = id);

-- Service role can do everything (for admin operations)
CREATE POLICY "Service role full access users"
ON public.users
FOR ALL
TO service_role
USING (true)
WITH CHECK (true);

-- ============================================
-- SMS TASKS TABLE POLICIES
-- ============================================

-- Users can view tasks assigned to them
CREATE POLICY "Users can view assigned tasks"
ON public.sms_tasks
FOR SELECT
TO authenticated
USING (
    assigned_to = auth.uid() 
    OR status = 'pending' -- Can see pending tasks
);

-- Service role can manage all tasks
CREATE POLICY "Service role full access tasks"
ON public.sms_tasks
FOR ALL
TO service_role
USING (true)
WITH CHECK (true);

-- Allow RPC functions to update tasks (via service role)
CREATE POLICY "Allow task updates via RPC"
ON public.sms_tasks
FOR UPDATE
TO authenticated
USING (assigned_to = auth.uid())
WITH CHECK (assigned_to = auth.uid());

-- ============================================
-- BATCH TASKS TABLE POLICIES
-- ============================================

-- Users can view their own batch tasks
CREATE POLICY "Users can view own batches"
ON public.batch_tasks
FOR SELECT
TO authenticated
USING (user_id = auth.uid());

-- Service role can manage all batches
CREATE POLICY "Service role full access batches"
ON public.batch_tasks
FOR ALL
TO service_role
USING (true)
WITH CHECK (true);

-- ============================================
-- SMS LOGS TABLE POLICIES
-- ============================================

-- Users can view their own SMS logs
CREATE POLICY "Users can view own logs"
ON public.sms_logs
FOR SELECT
TO authenticated
USING (user_id = auth.uid());

-- Service role can manage all logs
CREATE POLICY "Service role full access logs"
ON public.sms_logs
FOR ALL
TO service_role
USING (true)
WITH CHECK (true);

-- ============================================
-- TRANSACTIONS TABLE POLICIES
-- ============================================

-- Users can view their own transactions
CREATE POLICY "Users can view own transactions"
ON public.transactions
FOR SELECT
TO authenticated
USING (user_id = auth.uid());

-- Service role can manage all transactions
CREATE POLICY "Service role full access transactions"
ON public.transactions
FOR ALL
TO service_role
USING (true)
WITH CHECK (true);

-- ============================================
-- WITHDRAWALS TABLE POLICIES
-- ============================================

-- Users can view their own withdrawals
CREATE POLICY "Users can view own withdrawals"
ON public.withdrawals
FOR SELECT
TO authenticated
USING (user_id = auth.uid());

-- Users can create withdrawal requests
CREATE POLICY "Users can create withdrawals"
ON public.withdrawals
FOR INSERT
TO authenticated
WITH CHECK (user_id = auth.uid());

-- Users can cancel their pending withdrawals
CREATE POLICY "Users can cancel pending withdrawals"
ON public.withdrawals
FOR UPDATE
TO authenticated
USING (
    user_id = auth.uid() 
    AND status = 'pending'
)
WITH CHECK (
    user_id = auth.uid() 
    AND status = 'cancelled'
);

-- Service role can manage all withdrawals (for admin processing)
CREATE POLICY "Service role full access withdrawals"
ON public.withdrawals
FOR ALL
TO service_role
USING (true)
WITH CHECK (true);

-- ============================================
-- APP CONFIG TABLE POLICIES
-- ============================================

-- Everyone can read app configuration
CREATE POLICY "Anyone can read config"
ON public.app_config
FOR SELECT
TO anon, authenticated
USING (true);

-- Only service role can modify config
CREATE POLICY "Only service role can modify config"
ON public.app_config
FOR ALL
TO service_role
USING (true)
WITH CHECK (true);

-- ============================================
-- OTP VERIFICATIONS TABLE POLICIES
-- ============================================

-- Allow anyone to create OTP (for password reset)
CREATE POLICY "Anyone can create OTP"
ON public.otp_verifications
FOR INSERT
TO anon, authenticated
WITH CHECK (true);

-- Users can verify their own OTP
CREATE POLICY "Users can verify OTP"
ON public.otp_verifications
FOR SELECT
TO anon, authenticated
USING (
    phone IN (
        SELECT phone FROM public.users 
        WHERE id = auth.uid()
    )
    OR auth.uid() IS NULL -- Allow anon for password reset
);

-- Allow updates for OTP verification
CREATE POLICY "Allow OTP verification updates"
ON public.otp_verifications
FOR UPDATE
TO anon, authenticated
USING (
    phone IN (
        SELECT phone FROM public.users 
        WHERE id = auth.uid()
    )
    OR auth.uid() IS NULL
)
WITH CHECK (verified = true);

-- Service role full access
CREATE POLICY "Service role full access OTP"
ON public.otp_verifications
FOR ALL
TO service_role
USING (true)
WITH CHECK (true);

-- ============================================
-- AUDIT LOGS TABLE POLICIES
-- ============================================

-- Only service role can view audit logs (admin only)
CREATE POLICY "Only service role can access audit logs"
ON public.audit_logs
FOR SELECT
TO service_role
USING (true);

-- Service role can insert audit logs
CREATE POLICY "Service role can insert audit logs"
ON public.audit_logs
FOR INSERT
TO service_role
WITH CHECK (true);

-- ============================================
-- ADDITIONAL SECURITY CONFIGURATIONS
-- ============================================

-- Create a custom role for the app (optional)
-- This is useful if you want to separate app permissions from service role
-- DO $$ 
-- BEGIN
--     IF NOT EXISTS (SELECT FROM pg_roles WHERE rolname = 'sms_app_role') THEN
--         CREATE ROLE sms_app_role;
--     END IF;
-- END $$;

-- Grant basic permissions to app role
-- GRANT USAGE ON SCHEMA public TO sms_app_role;
-- GRANT SELECT, INSERT, UPDATE ON public.users TO sms_app_role;
-- GRANT SELECT ON public.app_config TO sms_app_role;
-- etc...

-- ============================================
-- SECURITY HELPER FUNCTIONS
-- ============================================

-- Function to check if user is admin (for future use)
CREATE OR REPLACE FUNCTION public.is_admin(user_id UUID)
RETURNS BOOLEAN
LANGUAGE plpgsql
SECURITY DEFINER
AS $$
BEGIN
    -- Example: Check if user has admin flag
    -- RETURN EXISTS (
    --     SELECT 1 FROM public.users 
    --     WHERE id = user_id AND is_admin = true
    -- );
    RETURN FALSE; -- Placeholder
END;
$$;

COMMENT ON FUNCTION public.is_admin IS 'Checks if user has admin privileges';

-- Function to verify user owns resource
CREATE OR REPLACE FUNCTION public.user_owns_resource(
    resource_user_id UUID,
    claiming_user_id UUID
)
RETURNS BOOLEAN
LANGUAGE plpgsql
SECURITY DEFINER
AS $$
BEGIN
    RETURN resource_user_id = claiming_user_id;
END;
$$;

COMMENT ON FUNCTION public.user_owns_resource IS 'Validates resource ownership';

-- ============================================
-- RLS POLICY TESTING
-- ============================================

/*
TEST RLS POLICIES:

1. Test as authenticated user:
   SET ROLE authenticated;
   SET request.jwt.claim.sub = '<user-uuid>';
   SELECT * FROM users; -- Should only see own data

2. Test as anon user:
   SET ROLE anon;
   SELECT * FROM app_config; -- Should see config
   SELECT * FROM users; -- Should see nothing

3. Test as service role:
   SET ROLE service_role;
   SELECT * FROM users; -- Should see all data

4. Reset:
   RESET ROLE;
*/

-- ============================================
-- SECURITY BEST PRACTICES
-- ============================================

/*
SECURITY RECOMMENDATIONS:

1. API KEYS:
   - Never expose service_role key in client apps
   - Use anon key for client-side operations
   - Rotate keys periodically

2. RLS POLICIES:
   - Always test policies with different roles
   - Use SECURITY DEFINER carefully in functions
   - Audit policy changes regularly

3. AUTHENTICATION:
   - Use Supabase Auth for user management
   - Implement MFA for sensitive operations
   - Set session timeouts appropriately

4. DATA VALIDATION:
   - Validate inputs in triggers
   - Use CHECK constraints on tables
   - Sanitize user inputs in app layer

5. MONITORING:
   - Enable Supabase audit logs
   - Monitor failed authentication attempts
   - Alert on suspicious patterns

6. BACKUP & RECOVERY:
   - Enable point-in-time recovery
   - Test restore procedures regularly
   - Keep backups encrypted and off-site
*/

-- ============================================
-- SETUP COMPLETE
-- ============================================
-- Database schema setup is now complete!
-- Test all policies and functions before deploying to production.
