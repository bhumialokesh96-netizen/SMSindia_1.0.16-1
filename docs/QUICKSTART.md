# SMS India - Quick Start Guide

## Prerequisites
- Android Studio Arctic Fox or newer
- JDK 17
- Android SDK with Build Tools 35
- A Supabase account (https://supabase.com)
- A Firebase account (https://firebase.google.com) for FCM and Auth

## Initial Setup

### 1. Clone the Repository
```bash
git clone https://github.com/bhumialokesh96-netizen/SMSindia_1.0.16-1.git
cd SMSindia_1.0.16-1
```

### 2. Set Up Supabase Database

1. Create a new Supabase project at https://supabase.com
2. Go to SQL Editor in Supabase dashboard
3. Execute the database schema files in order:
   ```
   docs/database/01_tables.sql
   docs/database/02_rpc_functions.sql
   docs/database/03_triggers.sql
   docs/database/04_indexes.sql
   docs/database/05_rls_policies.sql
   ```

4. Get your Supabase credentials:
   - Go to Project Settings > API
   - Copy the Project URL
   - Copy the `anon` public key

### 3. Configure the App

#### Option A: Using local.properties (Recommended for development)
1. Copy the template:
   ```bash
   cp local.properties.template local.properties
   ```

2. Edit `local.properties` and add your Supabase credentials:
   ```properties
   supabase.url=https://your-project-id.supabase.co
   supabase.anon.key=your-supabase-anon-key-here
   ```

#### Option B: Using Environment Variables (Recommended for CI/CD)
Set these environment variables before building:
```bash
export SUPABASE_URL=https://your-project-id.supabase.co
export SUPABASE_ANON_KEY=your-supabase-anon-key-here
```

### 4. Set Up Firebase

1. Go to Firebase Console (https://console.firebase.google.com)
2. Create a new project or use existing one
3. Add an Android app:
   - Package name: `com.smsindia.app`
   - Download `google-services.json`
   - Place it in `app/` directory

4. Enable Authentication:
   - Go to Authentication > Sign-in method
   - Enable Email/Password authentication

5. Enable Firestore (optional, if needed)
6. Enable Cloud Messaging (FCM) for push notifications

### 5. Build the App

#### Using Android Studio:
1. Open the project in Android Studio
2. Wait for Gradle sync to complete
3. Click Build > Build Bundle(s) / APK(s) > Build APK(s)

#### Using Command Line:
```bash
# Debug build
./gradlew assembleDebug

# Release build
./gradlew assembleRelease
```

The APK will be generated in:
- Debug: `app/build/outputs/apk/debug/app-debug.apk`
- Release: `app/build/outputs/apk/release/app-release-unsigned.apk`

## Project Structure

```
app/src/main/java/com/smsindia/app/
├── config/              # Configuration constants
│   └── Constants.java   # Centralized config (uses BuildConfig)
├── data/                # Data layer
│   ├── api/            # Retrofit API interfaces
│   ├── model/          # Data models (DTOs)
│   └── repository/     # Repository pattern (future)
├── service/             # Services
│   ├── MyFirebaseMessagingService.java
│   └── TokenManager.java
├── ui/                  # UI layer (Activities & Fragments)
├── workers/             # Background workers
│   └── SmsMiningService.java
└── MainActivity.java    # Main entry point
```

## Key Features

### Configuration Management
- API keys stored securely in BuildConfig
- Loads from environment variables or local.properties
- Fallback to default values for development

### Data Layer Architecture
- Models separated into `data/model/` package
- API interfaces in `data/api/` package
- Ready for Repository pattern implementation

### Security
- No hardcoded API keys in source code
- Supabase Row Level Security (RLS) enabled
- Secure token management via TokenManager

### Database Features
- Batch task processing with RPC functions
- Automated triggers for balance updates
- Performance-optimized indexes
- Comprehensive audit logging

## Common Issues & Solutions

### Build Errors

**Issue**: `Cannot find symbol: variable BuildConfig`
**Solution**: 
1. Check that `buildConfig true` is in build.gradle
2. Clean and rebuild: `./gradlew clean assembleDebug`
3. Sync Gradle files in Android Studio

**Issue**: `google-services.json not found`
**Solution**: 
1. Download from Firebase Console
2. Place in `app/` directory (not `app/src/`)

### Runtime Errors

**Issue**: "Network error" when logging in
**Solution**:
1. Verify Supabase URL and key in local.properties
2. Check internet connection
3. Verify Supabase project is active

**Issue**: Tasks not loading
**Solution**:
1. Check if database RPC functions are created
2. Verify user has proper authentication token
3. Check Supabase logs for errors

### Permission Issues

**Issue**: SMS permission denied
**Solution**:
1. App requests permissions at runtime
2. User must grant SMS and Phone State permissions
3. Check Settings > Apps > SMS India > Permissions

## Testing

### Test User Creation
```sql
-- In Supabase SQL Editor
INSERT INTO users (phone, email, password, device_id, coins, spins) 
VALUES ('9876543210', '9876543210@smsapp.com', 'test123', 'test-device', 100, 5);
```

### Test Task Creation
```sql
INSERT INTO sms_tasks (recipient, message, reward, status) 
VALUES ('1234567890', 'Test message', 0.50, 'pending');
```

### Test Batch Fetch
```sql
SELECT * FROM fetch_batch_tasks('<user-uuid>', 10);
```

## Development Workflow

1. **Make Changes**: Edit code in Android Studio
2. **Test Locally**: Run on emulator or physical device
3. **Commit**: Commit changes to Git
4. **Push**: Push to GitHub
5. **CI/CD**: GitHub Actions automatically builds and deploys

## CI/CD Pipeline

The project uses GitHub Actions for automated builds:
- Builds on every push to main/master
- Auto-increments version based on run number
- Signs APK with keystore (stored in secrets)
- Sends to Telegram channel

### Required GitHub Secrets:
- `SIGNING_KEY_STORE_BASE64`: Base64-encoded keystore
- `SIGNING_KEY_ALIAS`: Keystore alias
- `SIGNING_STORE_PASSWORD`: Keystore password
- `SIGNING_KEY_PASSWORD`: Key password
- `TELEGRAM_TOKEN`: Telegram bot token
- `TELEGRAM_TO`: Telegram chat ID

## Documentation

- **Database Schema**: `docs/database/README.md`
- **API Reference**: Check Supabase API documentation
- **Code Comments**: Inline documentation in source files

## Support

For issues or questions:
1. Check this guide first
2. Review database setup in `docs/database/`
3. Check GitHub Issues
4. Contact maintainers

## License

[Add your license information here]

## Contributors

[Add contributors here]
