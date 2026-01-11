# Referral System Enhancement - Implementation Guide

## Overview
This document describes the enhanced referral system implementation with leaderboard, tiered rewards, and analytics dashboard features for SMS India app.

## Features Implemented

### 1. Referral Leaderboard 🏆
A competitive leaderboard system that displays top referrers globally.

**Key Features:**
- Real-time ranking based on successful referrals
- Top 50 referrers displayed
- User's current position highlighted
- Tier badges for each user
- Phone number masking for privacy
- Medal indicators for top 3 positions (🥇🥈🥉)

**Database Components:**
- Materialized view: `referral_leaderboard`
- RPC function: `get_top_referrers(limit_count)`
- RPC function: `get_user_leaderboard_position(user_phone)`

**UI Components:**
- `ReferralLeaderboardFragment.java`
- `fragment_referral_leaderboard.xml`
- `item_leaderboard.xml`

### 2. Tiered Referral Rewards 💎
A three-tier reward system that increases benefits as users refer more people.

**Tier Structure:**

| Tier   | Referrals | Reward Multiplier | Bonus Coins | Badge |
|--------|-----------|-------------------|-------------|-------|
| Bronze | 1-10      | 1.0x (₹10)       | 0           | 🏅    |
| Silver | 11-50     | 1.25x (₹12.50)   | 50          | ⭐    |
| Gold   | 51+       | 1.5x (₹15)       | 150         | 👑    |

**Database Components:**
- Table: `referral_tiers`
- User fields: `current_tier`, `tier_updated_at`
- Trigger: `update_user_tier()` - Auto-updates tier on referral count change
- Automatic tier upgrade bonus coins

**UI Components:**
- Tier display in ShareFragment
- Tier progress indicator
- "X more referrals to next tier" message

### 3. Referral Analytics Dashboard 📊
Comprehensive analytics to track referral performance.

**Metrics Tracked:**
- **Referral Statistics:**
  - Total referrals
  - Successful referrals
  - Pending referrals
  - Failed referrals

- **Earnings Metrics:**
  - Total rewards earned
  - Conversion rate (%)
  - Average value per referral
  - Tier bonus earnings

- **Tier Progress:**
  - Current tier display
  - Benefits description
  - Progress to next tier
  - Visual progress bar

**Database Components:**
- Table: `referral_analytics`
- Trigger: `update_referral_analytics()` - Updates metrics on new referral
- Fields for conversion rate, average value, best performing day

**UI Components:**
- `ReferralAnalyticsFragment.java`
- `fragment_referral_analytics.xml`
- Real-time data syncing

## Database Schema

### New Tables

#### 1. `referral_tiers`
```sql
CREATE TABLE referral_tiers (
    id UUID PRIMARY KEY,
    tier_name VARCHAR(50) UNIQUE,
    tier_level INTEGER UNIQUE,
    min_referrals INTEGER,
    max_referrals INTEGER,
    reward_multiplier DECIMAL(3,2),
    bonus_coins INTEGER,
    badge_color VARCHAR(20),
    badge_icon VARCHAR(50),
    benefits TEXT
);
```

#### 2. `referral_analytics`
```sql
CREATE TABLE referral_analytics (
    id UUID PRIMARY KEY,
    user_id UUID REFERENCES users(id),
    total_referrals INTEGER,
    successful_referrals INTEGER,
    pending_referrals INTEGER,
    failed_referrals INTEGER,
    total_rewards_earned DECIMAL(10,2),
    tier_bonus_earned DECIMAL(10,2),
    conversion_rate DECIMAL(5,2),
    avg_referral_value DECIMAL(10,2),
    last_referral_date TIMESTAMPTZ,
    best_performing_day VARCHAR(20)
);
```

#### 3. `referral_leaderboard` (Materialized View)
```sql
CREATE MATERIALIZED VIEW referral_leaderboard AS
SELECT 
    u.id, u.phone, u.referral_code,
    u.referral_count, u.referral_reward_earned,
    u.current_tier, t.tier_name, t.badge_color, t.badge_icon,
    ROW_NUMBER() OVER (ORDER BY u.referral_count DESC) as rank
FROM users u
LEFT JOIN referral_tiers t ON u.current_tier = t.tier_level
WHERE u.referral_count > 0;
```

### Updated Tables

#### Users Table - New Fields
```sql
ALTER TABLE users 
ADD COLUMN current_tier INTEGER DEFAULT 1,
ADD COLUMN tier_updated_at TIMESTAMPTZ DEFAULT NOW();
```

## API Endpoints

### New Supabase API Methods

```java
// Get all tier definitions
@GET("/rest/v1/referral_tiers")
Call<List<ReferralTier>> getReferralTiers(
    @Header("apikey") String apiKey,
    @Header("Authorization") String auth,
    @Query("order") String order
);

// Get top referrers leaderboard
@POST("/rest/v1/rpc/get_top_referrers")
Call<List<LeaderboardEntry>> getTopReferrers(
    @Header("apikey") String apiKey,
    @Header("Authorization") String auth,
    @Body Map<String, Object> body
);

// Get user's leaderboard position
@POST("/rest/v1/rpc/get_user_leaderboard_position")
Call<List<Map<String, Object>>> getUserLeaderboardPosition(
    @Header("apikey") String apiKey,
    @Header("Authorization") String auth,
    @Body Map<String, Object> body
);

// Get referral analytics
@GET("/rest/v1/referral_analytics")
Call<List<ReferralAnalytics>> getReferralAnalytics(
    @Header("apikey") String apiKey,
    @Header("Authorization") String auth,
    @Query("user_id") String userIdQuery
);
```

## Data Models

### ReferralTier.java
```java
public class ReferralTier {
    public String tierName;
    public int tierLevel;
    public int minReferrals;
    public Integer maxReferrals;
    public double rewardMultiplier;
    public int bonusCoins;
    public String badgeColor;
    public String badgeIcon;
    public String benefits;
}
```

### LeaderboardEntry.java
```java
public class LeaderboardEntry {
    public long rank;
    public String phone;
    public String referralCode;
    public int referralCount;
    public double rewardsEarned;
    public String tierName;
    public String badgeColor;
    public String badgeIcon;
}
```

### ReferralAnalytics.java
```java
public class ReferralAnalytics {
    public int totalReferrals;
    public int successfulReferrals;
    public int pendingReferrals;
    public int failedReferrals;
    public double totalRewardsEarned;
    public double tierBonusEarned;
    public double conversionRate;
    public double avgReferralValue;
    public String lastReferralDate;
    public String bestPerformingDay;
}
```

## User Journey

### 1. Viewing Referral Dashboard (ShareFragment)
1. User opens the Share/Referral tab
2. Displays:
   - Total referrals count
   - Current tier badge and name
   - Progress to next tier
   - Milestones list
   - Action buttons: Share, Leaderboard, Analytics

### 2. Checking Leaderboard
1. User taps "🏆 Leaderboard" button
2. System loads top 50 referrers
3. User's position highlighted if in top 50
4. Shows rank, masked phone, referrals count, and tier badge
5. Top 3 positions have special medal indicators

### 3. Viewing Analytics
1. User taps "📊 Analytics" button
2. System displays:
   - Current tier with benefits description
   - Progress bar to next tier
   - Referral statistics (total, successful, pending, failed)
   - Earnings metrics (total rewards, conversion rate, average value)

### 4. Tier Progression
1. User refers more friends
2. System automatically checks referral count
3. When threshold reached:
   - Tier automatically upgraded via database trigger
   - Bonus coins awarded
   - Tier updated timestamp recorded
4. User sees updated tier in ShareFragment
5. Benefits apply immediately to new referrals

## Database Triggers & Automation

### 1. Automatic Tier Updates
```sql
CREATE TRIGGER trg_update_user_tier
    BEFORE UPDATE OF referral_count ON users
    FOR EACH ROW
    EXECUTE FUNCTION update_user_tier();
```
- Automatically updates user tier when referral count changes
- Awards tier upgrade bonus coins
- Updates tier timestamp

### 2. Analytics Updates
```sql
CREATE TRIGGER trg_update_referral_analytics
    AFTER INSERT OR UPDATE ON referral_transactions
    FOR EACH ROW
    EXECUTE FUNCTION update_referral_analytics();
```
- Automatically calculates and updates analytics
- Computes conversion rates
- Tracks average referral value

### 3. Leaderboard Refresh
- Materialized view refreshed on-demand during queries
- Uses CONCURRENT refresh to avoid locking
- For production: Consider pg_cron for scheduled hourly refresh

## Performance Optimizations

### Indexes
```sql
-- Leaderboard performance
CREATE INDEX idx_users_current_tier ON users(current_tier);
CREATE INDEX idx_leaderboard_rank ON referral_leaderboard(rank);

-- Analytics queries
CREATE INDEX idx_referral_analytics_user ON referral_analytics(user_id);
CREATE INDEX idx_referral_tiers_level ON referral_tiers(tier_level);
```

### Materialized View Benefits
- Pre-computed rankings for fast leaderboard queries
- Reduces join operations during peak traffic
- Unique index enables concurrent refresh

## Testing Checklist

### Unit Testing
- [ ] Tier calculation logic in UserModel
- [ ] Analytics metrics calculations
- [ ] Leaderboard ranking algorithm

### Integration Testing
- [ ] API calls for leaderboard data
- [ ] API calls for analytics data
- [ ] Tier progression flow
- [ ] Navigation between fragments

### UI Testing
- [ ] Leaderboard display and scrolling
- [ ] Analytics dashboard layout
- [ ] Tier badge display in ShareFragment
- [ ] Button click handlers

### Database Testing
- [ ] Tier upgrade trigger
- [ ] Analytics update trigger
- [ ] Leaderboard view refresh
- [ ] RPC function performance

## Security Considerations

1. **Phone Number Privacy:**
   - Phone numbers masked in leaderboard display
   - Only last 2 and first 2 digits shown

2. **Authorization:**
   - All API calls require JWT authentication
   - Row Level Security (RLS) policies enforce data access

3. **Data Validation:**
   - Referral count validated before tier upgrade
   - Analytics calculations performed server-side
   - No client-side manipulation of tier data

## Future Enhancements

1. **Time-based Leaderboards:**
   - Weekly/Monthly leaderboards
   - Seasonal competitions

2. **Advanced Analytics:**
   - Referral source tracking
   - Geographic distribution
   - Time-of-day patterns

3. **Social Features:**
   - Share leaderboard position
   - Challenge friends
   - Referral achievements

4. **Tier Benefits:**
   - Exclusive features per tier
   - Priority support for Gold tier
   - Special rewards and perks

## Migration Notes

### For Existing Users
- All existing users start at Bronze tier (tier_level = 1)
- Tier automatically calculated based on current referral_count
- No manual intervention required

### Database Migration Steps
1. Execute `07_referral_leaderboard_tiers.sql`
2. Verify tier table populated with 3 tiers
3. Check existing users assigned Bronze tier
4. Test trigger by updating a user's referral_count
5. Verify materialized view created successfully

## Troubleshooting

### Leaderboard Not Updating
- Check materialized view refresh status
- Verify referral_count being updated correctly
- Check database trigger logs

### Tier Not Upgrading
- Verify trigger is enabled
- Check referral_count threshold
- Review trigger execution logs
- Ensure referral_tiers table populated

### Analytics Not Showing
- Verify user_id matches in analytics table
- Check if referral_transactions exist
- Review trigger execution for analytics updates

## Support

For issues or questions regarding the referral system enhancement:
1. Check database logs for trigger execution
2. Verify API responses in app logs
3. Review Supabase dashboard for data consistency
4. Contact development team for assistance

---

**Version:** 1.2  
**Last Updated:** 2026-01-11  
**Author:** SMS India Development Team
