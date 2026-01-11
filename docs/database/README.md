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
   - `06_referral_system_enhancement.sql` - **NEW**: Adds referral code system and SMS metrics

3. **Update your app configuration:**
   - Copy `local.properties.template` to `local.properties`
   - Add your Supabase project URL and anon key
   - Or set environment variables `SUPABASE_URL` and `SUPABASE_ANON_KEY`

## Recent Updates (v1.0.16-1)

### Referral System Enhancement
The `06_referral_system_enhancement.sql` migration adds:
- **Referral Code Fields**: Each user gets a unique referral code (their phone number)
- **Company Referral Code**: Default code "666666" for users without referrals
- **Referral Rewards**: 
  - Referrer gets ₹10 per successful referral
  - Referee gets ₹5 + 50 coins when using a friend's code
  - Company referral users get 25 bonus coins
- **Referral Transactions Table**: Tracks all referral relationships and rewards
- **SMS Metrics Table**: Tracks daily SMS delivery statistics for admin dashboard
- **Automated Triggers**: Auto-reward users on referral signups

**To apply this update**: Execute `06_referral_system_enhancement.sql` in your Supabase SQL editor.

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
- Row Level Security (RLS) for data protection
- Batch task processing with RPC functions
- Automated triggers for balance updates
- Optimized indexes for fast queries
- Transaction tracking and audit trail

## Testing

After setup, test the database with:
```sql
-- Test user creation
INSERT INTO users (phone, email, password, device_id) 
VALUES ('1234567890', '1234567890@smsapp.com', 'test123', 'device-test-123');

-- Test configuration
INSERT INTO app_config (key, value) 
VALUES ('admob_config', '{"units": ["ca-app-pub-123"]}');

-- Test batch task fetch
SELECT * FROM fetch_batch_tasks('user-uuid-here', 10);
```

## Maintenance

- **Backups**: Enable Supabase automatic backups in project settings
- **Monitoring**: Use Supabase dashboard for query performance monitoring
- **Scaling**: Consider partitioning large tables (sms_logs, transactions) if needed

## Support

For issues or questions:
- Check Supabase documentation: https://supabase.com/docs
- Review SQL file comments for detailed explanations
- Test queries in Supabase SQL Editor before production use
