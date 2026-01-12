# Supabase Database Schema for SMS India App

## Overview
This directory contains the complete database schema for the SMS India application. The database uses Supabase (PostgreSQL) with Row Level Security (RLS) policies.

## Setup Instructions

1. **Create a new Supabase project** at https://supabase.com
2. **Execute the SQL files in order:**
   - `01_tables.sql` - Creates all database tables
   - `02_rpc_functions.sql` - Creates RPC functions for batch processing
   - `03_triggers.sql` - Creates triggers for automated actions
   - `04_indexes.sql` - Creates indexes for performance optimization
   - `05_rls_policies.sql` - Sets up Row Level Security policies
   - `06_referral_system_enhancement.sql` - Adds referral code system and SMS metrics
   - `07_referral_leaderboard_tiers.sql` - Adds referral leaderboard and tier system
   - `08_database_fixes_and_optimizations.sql` - **IMPORTANT**: Applies critical fixes and optimizations
   - `09_database_tests.sql` - **(Optional)** Runs comprehensive validation tests

3. **Update your app configuration:**
   - Copy `local.properties.template` to `local.properties`
   - Add your Supabase project URL and anon key
   - Or set environment variables `SUPABASE_URL` and `SUPABASE_ANON_KEY`

4. **Set up scheduled jobs (Recommended):**
   - See `DATABASE_DEBUG_REPORT.md` for pg_cron setup instructions
   - Schedule batch cleanup, daily income reset, and leaderboard refresh

## Recent Updates

### v1.0.17 - Database Debug and Optimization (Latest)
The `08_database_fixes_and_optimizations.sql` migration includes:
- **Critical Bug Fixes**: Fixed race conditions in triggers and RPC functions
- **Concurrency Improvements**: Added advisory locks to prevent deadlocks
- **RLS Policy Updates**: Enhanced security policies for better access control
- **Index Optimization**: Removed redundant indexes, added missing composite indexes
- **Referral Leaderboard Fixes**: Fixed tier update logic and analytics calculations
- **Performance Enhancements**: 3x faster queries with covering indexes
- **Scheduled Cleanup**: New function for automated batch expiration cleanup
- **Comprehensive Tests**: Added 10 test categories in `09_database_tests.sql`

**Important**: Execute `08_database_fixes_and_optimizations.sql` for all production databases.
**Documentation**: See `DATABASE_DEBUG_REPORT.md` for detailed information.

### v1.0.16-1 - Referral System Enhancement
The `06_referral_system_enhancement.sql` and `07_referral_leaderboard_tiers.sql` migrations add:
- **Referral Code Fields**: Each user gets a unique referral code (their phone number)
- **Company Referral Code**: Default code "666666" for users without referrals
- **Referral Rewards**: 
  - Referrer gets ₹10 per successful referral
  - Referee gets ₹5 + 50 coins when using a friend's code
  - Company referral users get 25 bonus coins
- **Referral Transactions Table**: Tracks all referral relationships and rewards
- **SMS Metrics Table**: Tracks daily SMS delivery statistics for admin dashboard
- **Referral Leaderboard**: Global rankings with top referrers
- **Tiered Rewards**: Bronze (1-10), Silver (11-50), Gold (51+) tiers
- **Analytics Dashboard**: Track referrals, conversion rates, and earnings
- **Automated Triggers**: Auto-reward users on referral signups and tier upgrades

## Database Architecture

### Core Tables
- **users** - User profiles and wallet information
- **sms_tasks** - Individual SMS tasks for mining
- **batch_tasks** - Batch task tracking for concurrent processing
- **sms_logs** - History of sent SMS messages
- **transactions** - Financial transaction history
- **withdrawals** - Withdrawal requests and status
- **app_config** - Application configuration (ads, features, etc.)
- **otp_verifications** - OTP verification for password reset

### Key Features
- ✅ Row Level Security (RLS) for data protection
- ✅ Batch task processing with RPC functions and concurrency control
- ✅ Automated triggers for balance updates with race condition prevention
- ✅ Optimized indexes for fast queries (3x performance improvement)
- ✅ Transaction tracking and comprehensive audit trail
- ✅ Referral leaderboard with tiered rewards system
- ✅ Advisory locks for high-concurrency scenarios
- ✅ Scheduled cleanup functions for maintenance
- ✅ Performance monitoring views

## Testing

After setup, run comprehensive tests:
```sql
-- Run all automated tests (recommended)
\i 09_database_tests.sql

-- Or test individual components:

-- Test user creation
INSERT INTO users (phone, email, password, device_id) 
VALUES ('1234567890', '1234567890@smsapp.com', 'test123', 'device-test-123');

-- Test batch task fetch
SELECT * FROM fetch_batch_tasks('user-uuid-here', 10);

-- Test batch submission
SELECT * FROM submit_batch_results(
    'user-uuid-here',
    ARRAY['task-uuid-1', 'task-uuid-2']::uuid[],
    ARRAY[]::uuid[]
);

-- Test leaderboard
SELECT * FROM get_top_referrers(10);

-- Check performance
SELECT * FROM performance_monitor;
```

## Maintenance

- **Backups**: Enable Supabase automatic backups in project settings
- **Monitoring**: Use Supabase dashboard and `performance_monitor` view for query performance
- **Scheduled Jobs**: Set up pg_cron for automated cleanup (see `DATABASE_DEBUG_REPORT.md`)
- **Index Monitoring**: Check unused indexes monthly and remove if not needed
- **Query Optimization**: Monitor slow queries (>100ms) and optimize as needed
- **Scaling**: Consider partitioning large tables (sms_logs, transactions) when reaching 1M+ rows

## Performance Optimization

The database has been optimized for production workloads:
- **Concurrency**: Advisory locks prevent race conditions and deadlocks
- **Indexes**: Covering indexes provide 3x faster query performance
- **Materialized Views**: Pre-computed leaderboard for instant results
- **Row Locking**: Prevents lost updates on concurrent operations
- **Cleanup Jobs**: Automated maintenance via scheduled functions

See `DATABASE_DEBUG_REPORT.md` for detailed performance metrics and improvements.

## Troubleshooting

### Common Issues

1. **Deadlocks on batch operations**
   - Fixed in v1.0.17 with advisory locks
   - Update to latest schema version

2. **Slow leaderboard queries**
   - Refresh materialized view: `REFRESH MATERIALIZED VIEW CONCURRENTLY referral_leaderboard`
   - Set up hourly refresh with pg_cron

3. **Expired batches not cleaning up**
   - Run manual cleanup: `SELECT cleanup_all_expired_batches()`
   - Set up scheduled job (every 5 minutes recommended)

4. **Balance inconsistencies**
   - Fixed in v1.0.17 with FOR UPDATE locks
   - Update to latest schema version

## Support

For issues or questions:
- Check `DATABASE_DEBUG_REPORT.md` for comprehensive documentation
- Review SQL file comments for detailed explanations
- Run `09_database_tests.sql` to validate your setup
- Check Supabase documentation: https://supabase.com/docs
- Test queries in Supabase SQL Editor before production use
- Open GitHub issue for persistent problems
