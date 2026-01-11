# SMS India - Earn by SMS Mining 📱💰

A modern Android application that allows users to earn money by sending SMS messages through a secure and automated system.

## 🌟 Features

- **SMS Mining**: Earn money by sending SMS through automated tasks
- **Batch Processing**: Handle multiple SMS tasks efficiently
- **Wallet System**: Track earnings and manage withdrawals
- **Daily Bonuses**: Check-in daily for streak rewards
- **Spin Wheel**: Gamified earning experience
- **Ad Rewards**: Watch ads to earn additional income
- **Referral System**: Earn by inviting friends
- **Secure Authentication**: Phone-based login with OTP
- **Real-time Updates**: Push notifications via FCM

## 🏗️ Architecture

### Clean Architecture with Data Layer Separation

```
app/src/main/java/com/smsindia/app/
├── config/              # Configuration & Constants
│   └── Constants.java   # Centralized config (BuildConfig)
├── data/                # Data Layer
│   ├── api/            # API Interfaces (Retrofit)
│   ├── model/          # Data Models & DTOs
│   └── repository/     # Repository Pattern (Future)
├── service/             # Background Services
├── ui/                  # Presentation Layer (Activities & Fragments)
├── workers/             # Background Workers
└── MainActivity.java    # Entry Point
```

### Key Technologies

- **Language**: Java 17
- **Build Tool**: Gradle
- **Backend**: Supabase (PostgreSQL + REST API)
- **Authentication**: Firebase Auth
- **Push Notifications**: Firebase Cloud Messaging
- **Network**: Retrofit 2 + Gson
- **UI**: Material Design Components
- **Background Tasks**: WorkManager
- **Ads**: Google AdMob

## 🚀 Quick Start

### Prerequisites

- Android Studio Arctic Fox or newer
- JDK 17
- Android SDK (API 24-35)
- Supabase account
- Firebase account

### Setup Instructions

1. **Clone the repository**
   ```bash
   git clone https://github.com/bhumialokesh96-netizen/SMSindia_1.0.16-1.git
   cd SMSindia_1.0.16-1
   ```

2. **Set up Supabase Database**
   - Create a Supabase project
   - Execute SQL files in order from `docs/database/`
   - See [Database Setup Guide](docs/database/README.md)

3. **Configure the app**
   ```bash
   cp local.properties.template local.properties
   # Edit local.properties with your credentials
   ```

4. **Add Firebase**
   - Download `google-services.json` from Firebase Console
   - Place in `app/` directory

5. **Build and Run**
   ```bash
   ./gradlew assembleDebug
   ```

For detailed setup instructions, see [Quick Start Guide](docs/QUICKSTART.md).

## 📦 Database Schema

### Core Tables
- **users** - User profiles and wallet information
- **sms_tasks** - Individual SMS mining tasks
- **batch_tasks** - Batch task assignments
- **sms_logs** - SMS history and audit trail
- **transactions** - Financial transaction history
- **withdrawals** - Withdrawal requests
- **app_config** - Dynamic app configuration
- **otp_verifications** - OTP for password reset

### Features
- ✅ Automated batch processing with RPC functions
- ✅ Row Level Security (RLS) for data protection
- ✅ Automated triggers for consistency
- ✅ Performance-optimized indexes
- ✅ Comprehensive audit logging

See [Database Documentation](docs/database/README.md) for details.

## 🔐 Security

### Configuration Management
- ✅ No hardcoded API keys in source code
- ✅ Credentials loaded from BuildConfig
- ✅ Environment variable support for CI/CD
- ✅ Template file for local development

### Database Security
- ✅ Row Level Security (RLS) enabled on all tables
- ✅ User-scoped data access policies
- ✅ Encrypted sensitive data (JSONB)
- ✅ Audit trail for all transactions

### Best Practices
- Secure token management via TokenManager
- Phone-based authentication with OTP
- Input validation in triggers
- Regular security updates

## 📱 App Structure

### Main Components

**Activities**
- `LoginActivity` - User authentication
- `MainActivity` - Main container with bottom navigation
- `HistoryActivity` - Transaction history
- `WithdrawalHistoryActivity` - Withdrawal tracking
- `DeliveryLogActivity` - SMS delivery logs
- `WebTaskActivity` - Web-based tasks

**Fragments**
- `HomeFragment` - Dashboard with earnings overview
- `TaskFragment` - SMS mining interface
- `SpinFragment` - Lucky spin wheel
- `ShareFragment` - Referral system
- `ProfileFragment` - User profile and settings

**Services**
- `SmsMiningService` - Background SMS processing
- `MyFirebaseMessagingService` - Push notifications

## 🔄 CI/CD Pipeline

The project uses GitHub Actions for automated builds:

- ✅ Builds on every push to main/master
- ✅ Auto-increments version number
- ✅ Signs APK with release keystore
- ✅ Deploys to Telegram channel

### Required GitHub Secrets
```
SIGNING_KEY_STORE_BASE64  # Base64-encoded keystore
SIGNING_KEY_ALIAS         # Keystore alias
SIGNING_STORE_PASSWORD    # Keystore password
SIGNING_KEY_PASSWORD      # Key password
TELEGRAM_TOKEN            # Telegram bot token
TELEGRAM_TO               # Telegram chat ID
```

## 🛠️ Development

### Building

```bash
# Debug build
./gradlew assembleDebug

# Release build
./gradlew assembleRelease

# Run tests
./gradlew test
```

### Project Configuration

**app/build.gradle**
- BuildConfig fields for API keys
- Loads from environment or local.properties
- Automatic version incrementing

**Constants.java**
- Centralized configuration
- Uses BuildConfig values
- Fallback defaults for development

## 📊 Version History

- **v1.0.16** - Major restructuring and database setup
  - Security: Removed all hardcoded API keys
  - Architecture: Clean data layer separation
  - Database: Comprehensive Supabase schema
  - Documentation: Complete setup guides

- **v1.0.15** - Initial release
  - Basic SMS mining functionality
  - User authentication
  - Wallet system

## 🤝 Contributing

Contributions are welcome! Please follow these guidelines:

1. Fork the repository
2. Create a feature branch
3. Make your changes
4. Test thoroughly
5. Submit a pull request

## 📄 License

[Add your license here]

## 👥 Team

- **Developer**: Bhumi Alokesh
- **GitHub**: [@bhumialokesh96-netizen](https://github.com/bhumialokesh96-netizen)

## 📞 Support

For issues or questions:
- Check [Quick Start Guide](docs/QUICKSTART.md)
- Review [Database Docs](docs/database/README.md)
- Open a GitHub Issue
- Contact the maintainers

## 🙏 Acknowledgments

- Supabase for backend infrastructure
- Firebase for authentication and messaging
- Material Design for UI components
- All contributors and testers

---

**Built with ❤️ for Android**
