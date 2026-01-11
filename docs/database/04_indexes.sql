-- ============================================
-- SMS INDIA DATABASE SCHEMA - PERFORMANCE INDEXES
-- ============================================
-- Version: 1.0
-- Description: Additional indexes for query optimization
-- Execute this file after 03_triggers.sql
-- Note: Some basic indexes were already created in 01_tables.sql
-- ============================================

-- ============================================
-- COMPOSITE INDEXES FOR COMMON QUERIES
-- ============================================

-- 1. Users - Find by phone and status
CREATE INDEX IF NOT EXISTS idx_users_phone_active 
ON public.users(phone) 
WHERE created_at > NOW() - INTERVAL '1 year'; -- Partial index for active users

-- 2. SMS Tasks - Complex filtering
CREATE INDEX IF NOT EXISTS idx_sms_tasks_status_priority 
ON public.sms_tasks(status, priority DESC, created_at ASC)
WHERE status IN ('pending', 'assigned');

-- 3. SMS Tasks - Find assigned tasks by user
CREATE INDEX IF NOT EXISTS idx_sms_tasks_assigned_status 
ON public.sms_tasks(assigned_to, status)
WHERE assigned_to IS NOT NULL;

-- 4. Batch Tasks - Find active batches
CREATE INDEX IF NOT EXISTS idx_batch_tasks_user_status 
ON public.batch_tasks(user_id, status, created_at DESC)
WHERE status = 'in_progress';

-- 5. SMS Logs - User activity timeline
CREATE INDEX IF NOT EXISTS idx_sms_logs_user_time 
ON public.sms_logs(user_id, sent_at DESC);

-- 6. Transactions - User financial history
CREATE INDEX IF NOT EXISTS idx_transactions_user_type_time 
ON public.transactions(user_id, type, created_at DESC);

-- 7. Withdrawals - Admin review queue
CREATE INDEX IF NOT EXISTS idx_withdrawals_status_time 
ON public.withdrawals(status, requested_at ASC)
WHERE status IN ('pending', 'processing');

-- ============================================
-- JSONB INDEXES FOR CONFIG QUERIES
-- ============================================

-- 1. App Config - Fast key lookups (already has primary key)
-- Additional GIN index for JSONB value searches
CREATE INDEX IF NOT EXISTS idx_app_config_value 
ON public.app_config USING GIN (value);

-- 2. Users - Bank details search (if needed)
CREATE INDEX IF NOT EXISTS idx_users_bank_details 
ON public.users USING GIN (bank_details)
WHERE bank_details IS NOT NULL;

-- 3. Transactions - Metadata search
CREATE INDEX IF NOT EXISTS idx_transactions_metadata 
ON public.transactions USING GIN (metadata)
WHERE metadata IS NOT NULL;

-- ============================================
-- TEXT SEARCH INDEXES (OPTIONAL)
-- ============================================

-- 1. SMS Tasks - Full-text search on message content
CREATE INDEX IF NOT EXISTS idx_sms_tasks_message_search 
ON public.sms_tasks USING GIN (to_tsvector('english', message));

-- 2. Transactions - Description search
CREATE INDEX IF NOT EXISTS idx_transactions_description_search 
ON public.transactions USING GIN (to_tsvector('english', description))
WHERE description IS NOT NULL;

-- ============================================
-- COVERING INDEXES FOR PERFORMANCE
-- ============================================

-- 1. SMS Tasks - Include reward in status index (covering index)
CREATE INDEX IF NOT EXISTS idx_sms_tasks_status_with_reward 
ON public.sms_tasks(status) 
INCLUDE (reward, created_at);

-- 2. Users - Balance lookup with stats
CREATE INDEX IF NOT EXISTS idx_users_balance_stats 
ON public.users(id) 
INCLUDE (balance, today_income, total_income, sms_count);

-- ============================================
-- UNIQUE CONSTRAINTS (Beyond Primary Keys)
-- ============================================

-- 1. Ensure phone uniqueness (case-insensitive)
CREATE UNIQUE INDEX IF NOT EXISTS idx_users_phone_unique 
ON public.users(LOWER(phone));

-- 2. Prevent duplicate OTP for same phone within validity period
CREATE UNIQUE INDEX IF NOT EXISTS idx_otp_phone_active 
ON public.otp_verifications(phone) 
WHERE verified = FALSE AND expires_at > NOW();

-- ============================================
-- PARTIAL INDEXES FOR EFFICIENCY
-- ============================================

-- 1. Active users only (logged in within last 30 days)
CREATE INDEX IF NOT EXISTS idx_users_active 
ON public.users(updated_at DESC)
WHERE updated_at > NOW() - INTERVAL '30 days';

-- 2. Recent transactions (last 90 days)
CREATE INDEX IF NOT EXISTS idx_transactions_recent 
ON public.transactions(user_id, created_at DESC)
WHERE created_at > NOW() - INTERVAL '90 days';

-- 3. Pending tasks with high priority
CREATE INDEX IF NOT EXISTS idx_sms_tasks_high_priority 
ON public.sms_tasks(priority DESC, created_at ASC)
WHERE status = 'pending' AND priority > 5;

-- ============================================
-- STATISTICS AND MAINTENANCE
-- ============================================

-- Update table statistics for better query planning
ANALYZE public.users;
ANALYZE public.sms_tasks;
ANALYZE public.batch_tasks;
ANALYZE public.sms_logs;
ANALYZE public.transactions;
ANALYZE public.withdrawals;
ANALYZE public.app_config;
ANALYZE public.otp_verifications;

-- ============================================
-- MONITORING QUERIES
-- ============================================

-- View index usage statistics (helpful for optimization)
COMMENT ON SCHEMA public IS 'Run this query to monitor index usage:
SELECT 
    schemaname,
    tablename,
    indexname,
    idx_scan as index_scans,
    idx_tup_read as tuples_read,
    idx_tup_fetch as tuples_fetched
FROM pg_stat_user_indexes
WHERE schemaname = ''public''
ORDER BY idx_scan DESC;';

-- View table sizes
COMMENT ON DATABASE postgres IS 'Check table sizes with:
SELECT 
    schemaname,
    tablename,
    pg_size_pretty(pg_total_relation_size(schemaname||''.''||tablename)) AS size
FROM pg_tables
WHERE schemaname = ''public''
ORDER BY pg_total_relation_size(schemaname||''.''||tablename) DESC;';

-- ============================================
-- PERFORMANCE RECOMMENDATIONS
-- ============================================

/*
RECOMMENDATIONS FOR PRODUCTION:

1. PARTITIONING (for large tables):
   - Consider partitioning sms_logs by month (Range Partitioning)
   - Consider partitioning transactions by month
   - Helps with query performance and archival

2. VACUUMING:
   - Enable autovacuum (should be on by default)
   - Monitor bloat in high-traffic tables
   - Run VACUUM ANALYZE periodically

3. CONNECTION POOLING:
   - Use PgBouncer or Supabase's built-in pooling
   - Limit max connections appropriately

4. QUERY OPTIMIZATION:
   - Use EXPLAIN ANALYZE to profile slow queries
   - Add indexes based on actual query patterns
   - Consider materialized views for complex aggregations

5. MONITORING:
   - Set up Supabase monitoring dashboard
   - Watch for slow queries (> 100ms)
   - Monitor table sizes and growth rates

6. ARCHIVAL STRATEGY:
   - Archive old sms_logs (> 6 months) to separate table
   - Archive completed transactions (> 1 year)
   - Keep audit_logs for required retention period
*/

-- ============================================
-- SETUP COMPLETE
-- ============================================
-- Next: Execute 05_rls_policies.sql
