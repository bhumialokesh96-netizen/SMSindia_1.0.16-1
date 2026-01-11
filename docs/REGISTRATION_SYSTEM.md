# Registration System Overhaul - Implementation Guide

## Overview
This document describes the enhanced registration system implemented in SMS India v1.0.16-1. The new system provides a modern, user-friendly registration experience with an integrated referral system.

## Key Features

### 1. Streamlined User Registration
- **Minimal Input**: Users only need to provide:
  - Email address (validated format)
  - Phone number (10 digits)
  - Password (minimum 6 characters)
- **No OTP Verification**: Simplified process for immediate access
- **Separate Registration Screen**: Dedicated RegisterActivity with modern UI

### 2. Referral Code System
- **Optional Referral Field**: Users can enter a friend's referral code
- **Auto-Fill Company Code**: If left empty, automatically fills with "666666"
- **Attractive Rewards**:
  - **Using Friend's Code**: ₹5 + 50 coins for new user, ₹10 for referrer
  - **Company Code**: 25 bonus coins for new user
  - **Base Signup Bonus**: 100 coins for all new users
- **Visual Benefits Display**: Clear explanation of rewards in registration screen

### 3. Database Enhancements
- **New User Fields**:
  - `referral_code`: User's unique code (phone number)
  - `referred_by`: Code of user who referred them
  - `referral_reward_earned`: Total earnings from referrals
- **Referral Transactions Table**: Complete audit trail of referral relationships
- **SMS Metrics Table**: Daily tracking of SMS delivery statistics
- **Automated Triggers**: Auto-reward system for referrals

### 4. UI/UX Improvements
- **Modern Material Design**: Consistent with app design system
- **Color-Coded Benefits**: Green cards show referral benefits
- **Clear Visual Hierarchy**: Important fields highlighted
- **Responsive Validation**: Real-time input validation
- **Attractive Icons**: Enhanced visual appeal with appropriate icons

## Implementation Details

### Files Added

#### Backend - Database
- `docs/database/06_referral_system_enhancement.sql`
  - Adds referral code fields to users table
  - Creates referral_transactions table
  - Creates sms_metrics table
  - Implements automated triggers for referral rewards
  - Adds app_config entries for referral settings

#### Frontend - Activities
- `app/src/main/java/com/smsindia/app/RegisterActivity.java`
  - New dedicated registration activity
  - Email, phone, password inputs
  - Referral code with auto-fill logic
  - Validation and error handling
  - Integration with Supabase Auth

#### Frontend - Layouts
- `app/src/main/res/layout/activity_register.xml`
  - Modern Material Design layout
  - Outlined text input fields
  - Referral benefits card
  - Attractive color scheme
  - Clear call-to-action buttons

#### Data Models
- `app/src/main/java/com/smsindia/app/data/model/RegisterRequest.java`
  - Request model for registration API
  - Supports email, password, phone, and referral code

### Files Modified

#### Activity Updates
- `app/src/main/java/com/smsindia/app/LoginActivity.java`
  - Signup button now redirects to RegisterActivity
  - Maintains backward compatibility with existing login

#### Configuration
- `app/src/main/AndroidManifest.xml`
  - Registered RegisterActivity
  - Configured with NoActionBar theme

#### Documentation
- `docs/database/README.md`
  - Updated with referral system information
  - Added migration instructions

#### UI Enhancements
- `app/src/main/res/layout/fragment_share.xml`
  - Added "How Referral Works" section
  - Enhanced visual presentation of benefits

## User Flow

### Registration Process
1. User opens app → LoginActivity
2. Clicks "CREATE NEW ACCOUNT" → RegisterActivity
3. Enters email, phone, password
4. Optionally enters referral code OR leaves empty
5. System auto-fills "666666" if empty
6. Clicks "CREATE ACCOUNT"
7. System validates inputs
8. Creates Supabase Auth account
9. Creates user profile with referral data
10. Awards appropriate bonuses
11. Saves user info to SharedPreferences
12. Navigates to MainActivity

### Referral Reward Logic
```
IF referral_code is empty:
    referral_code = "666666" (company code)
    bonus = 25 coins
ELSE IF referral_code is valid user code:
    referee gets: ₹5 + 50 coins
    referrer gets: ₹10 + referral_count++
ELSE:
    show error "Invalid referral code"

Base bonus for all users: 100 coins + 3 spins
```

## Database Schema Changes

### users Table Updates
```sql
-- New columns
referral_code VARCHAR(20) UNIQUE  -- User's unique referral code
referred_by VARCHAR(20)            -- Code of referrer
referral_reward_earned DECIMAL(10,2) -- Total referral earnings

-- Indexes
CREATE INDEX idx_users_referral_code ON users(referral_code);
CREATE INDEX idx_users_referred_by ON users(referred_by);
```

### New Tables

#### referral_transactions
```sql
CREATE TABLE referral_transactions (
    id UUID PRIMARY KEY,
    referrer_id UUID,           -- User who referred
    referee_id UUID,            -- User who was referred
    referral_code VARCHAR(20),  -- Code used
    referrer_reward DECIMAL(10,2) DEFAULT 10.00,
    referee_reward DECIMAL(10,2) DEFAULT 5.00,
    referee_coins INTEGER DEFAULT 50,
    status VARCHAR(20),
    created_at TIMESTAMPTZ
);
```

#### sms_metrics
```sql
CREATE TABLE sms_metrics (
    id UUID PRIMARY KEY,
    total_sent INTEGER,
    total_delivered INTEGER,
    total_failed INTEGER,
    total_pending INTEGER,
    date DATE,
    created_at TIMESTAMPTZ
);
```

## Testing Checklist

### Registration Flow
- [ ] User can register with email, phone, and password
- [ ] Email validation works correctly
- [ ] Phone validation accepts 10-digit numbers
- [ ] Password validation enforces 6-character minimum
- [ ] Empty referral code auto-fills to "666666"
- [ ] Valid referral code applies correct rewards
- [ ] Registration creates Supabase Auth account
- [ ] User profile created in database
- [ ] SharedPreferences saved correctly
- [ ] Navigates to MainActivity on success

### Referral System
- [ ] New user with company code gets 125 coins (100+25)
- [ ] New user with friend code gets 150 coins (100+50) + ₹5
- [ ] Referrer gets ₹10 when friend signs up
- [ ] Referrer's referral_count increments
- [ ] Referral transaction recorded in database
- [ ] ShareFragment displays correct referral stats

### UI/UX
- [ ] Registration screen displays correctly
- [ ] All input fields are accessible
- [ ] Validation errors show inline
- [ ] Benefits card displays referral rewards
- [ ] Success toast shows appropriate message
- [ ] Back button returns to login
- [ ] Login link navigates to LoginActivity

### Edge Cases
- [ ] Duplicate email shows error
- [ ] Duplicate phone shows error
- [ ] Invalid referral code shows error
- [ ] Network errors handled gracefully
- [ ] Database errors handled gracefully

## Deployment Steps

### 1. Database Migration
```bash
# Execute in Supabase SQL Editor
psql -U postgres -d your_database -f docs/database/06_referral_system_enhancement.sql
```

### 2. App Build
```bash
# Build debug APK
./gradlew assembleDebug

# Build release APK
./gradlew assembleRelease
```

### 3. Testing
- Install APK on test device
- Test complete registration flow
- Verify referral rewards
- Check database entries

### 4. Production Release
- Update version number in build.gradle
- Build signed release APK
- Deploy to Play Store or distribution channel

## Configuration

### Company Referral Code
The default company referral code is "666666" and is defined in:
- `RegisterActivity.COMPANY_REFERRAL_CODE`
- Database: `app_config.company_referral_code`

To change the company code:
1. Update `RegisterActivity.java`:
   ```java
   private static final String COMPANY_REFERRAL_CODE = "YOUR_CODE";
   ```
2. Update database:
   ```sql
   UPDATE app_config 
   SET value = '"YOUR_CODE"'::jsonb 
   WHERE key = 'company_referral_code';
   ```

### Referral Rewards
Reward amounts are configurable in the database:
```sql
UPDATE app_config SET value = '{"amount": 15, "coins": 0}'::jsonb 
WHERE key = 'referral_reward_referrer';

UPDATE app_config SET value = '{"amount": 10, "coins": 75}'::jsonb 
WHERE key = 'referral_reward_referee';
```

## API Integration

### Registration Endpoint
```http
POST /auth/v1/signup
Headers:
  apikey: YOUR_SUPABASE_ANON_KEY
  Content-Type: application/json
Body:
{
  "email": "user@example.com",
  "password": "securepassword"
}
```

### Create User Profile
```http
POST /rest/v1/users
Headers:
  apikey: YOUR_SUPABASE_ANON_KEY
  Authorization: Bearer USER_JWT_TOKEN
  Prefer: return=representation
Body:
{
  "email": "user@example.com",
  "phone": "1234567890",
  "password": "securepassword",
  "referral_code": "1234567890",
  "referred_by": "666666",
  "coins": 125,
  "balance": 0.0,
  ...
}
```

## Support & Troubleshooting

### Common Issues

**Issue**: Registration fails with "already registered"
**Solution**: User with this email/phone already exists. Use login instead.

**Issue**: Referral code not working
**Solution**: Ensure the referral code matches an existing user's phone number.

**Issue**: Rewards not applied
**Solution**: Check database triggers are enabled. Verify `set_referral_code()` function exists.

**Issue**: Auto-fill not working
**Solution**: Check `COMPANY_REFERRAL_CODE` constant in RegisterActivity.java.

### Support Contacts
- GitHub Issues: https://github.com/bhumialokesh96-netizen/SMSindia_1.0.16-1/issues
- Developer: Bhumi Alokesh

## Future Enhancements

### Planned Features
- [ ] Social media integration for easy sharing
- [ ] Referral leaderboard
- [ ] Tiered referral rewards (more referrals = higher rewards)
- [ ] Referral analytics dashboard
- [ ] Email verification (optional)
- [ ] SMS OTP verification (optional)
- [ ] Multi-level referral system (MLM)

### Potential Improvements
- Add referral campaign tracking
- Implement referral expiry dates
- Create referral bonus events
- Add gamification elements
- Develop admin panel for referral management

## Version History

### v1.0.16-1 (Current)
- ✅ Modern registration screen with email support
- ✅ Referral code system with auto-fill
- ✅ Company referral code (666666)
- ✅ Automated reward distribution
- ✅ SMS metrics tracking
- ✅ Enhanced ShareFragment UI
- ✅ Complete database migration

### Next Release (Planned)
- Social sharing improvements
- Referral analytics
- Email verification option

---

**Last Updated**: January 11, 2026
**Author**: SMS India Development Team
**Version**: 1.0.16-1
