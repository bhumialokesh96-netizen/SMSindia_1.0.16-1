# SMS India - Architecture Documentation

## Overview

This document describes the architectural decisions, patterns, and structure of the SMS India application.

## Architecture Principles

### 1. Clean Architecture
The application follows clean architecture principles with clear separation of concerns:

- **Data Layer**: Models, API interfaces, repositories
- **Domain Layer**: Business logic and use cases (planned)
- **Presentation Layer**: UI components (Activities, Fragments)
- **Configuration Layer**: Centralized constants and settings

### 2. Single Responsibility
Each class and module has a single, well-defined purpose:

- Models handle data representation
- API interfaces define network contracts
- Activities/Fragments handle UI and user interaction
- Services manage background operations
- Workers handle long-running tasks

### 3. Dependency Inversion
High-level modules don't depend on low-level modules. Both depend on abstractions:

- API interfaces (not implementations)
- Repository interfaces (planned)
- ViewModel contracts (planned)

## Package Structure

```
com.smsindia.app/
├── config/                     # Configuration Layer
│   └── Constants.java         # Centralized constants using BuildConfig
│
├── data/                       # Data Layer
│   ├── api/                   # Network API Interfaces
│   │   ├── AuthApi.java      # Authentication endpoints
│   │   ├── SupabaseApi.java  # Main API endpoints
│   │   └── WhatsAppApi.java  # WhatsApp integration
│   │
│   ├── model/                 # Data Transfer Objects (DTOs)
│   │   ├── UserModel.java
│   │   ├── TaskModel.java
│   │   ├── TransactionModel.java
│   │   ├── WithdrawModel.java
│   │   ├── SmsLogModel.java
│   │   ├── AppConfigModel.java
│   │   ├── AuthResponse.java
│   │   ├── AdRewardResponse.java
│   │   ├── LoginRequest.java
│   │   └── BatchResultRequest.java
│   │
│   └── repository/            # Repository Pattern (Future)
│       ├── UserRepository
│       ├── TaskRepository
│       └── TransactionRepository
│
├── service/                    # Background Services
│   ├── MyFirebaseMessagingService.java  # FCM push notifications
│   └── TokenManager.java                # Auth token management
│
├── ui/                         # Presentation Layer
│   ├── HomeFragment.java      # Dashboard
│   ├── TaskFragment.java      # SMS mining interface
│   ├── SpinFragment.java      # Spin wheel
│   ├── ShareFragment.java     # Referrals
│   ├── ProfileFragment.java   # User profile
│   ├── HistoryActivity.java   # Transaction history
│   ├── WithdrawalHistoryActivity.java
│   ├── DeliveryLogActivity.java
│   ├── WebTaskActivity.java
│   ├── BannerAdapter.java
│   └── LuckyWheelView.java
│
├── workers/                    # Background Processing
│   └── SmsMiningService.java  # SMS batch processing service
│
├── LoginActivity.java          # Authentication entry point
├── MainActivity.java           # Main app container
├── SplashActivity.java         # App initialization
└── MyApp.java                  # Application class
```

## Design Patterns

### 1. Repository Pattern (Planned)

**Purpose**: Abstract data sources and provide clean API for data access

**Implementation**:
```java
interface UserRepository {
    User getUser(String userId);
    void updateUser(User user);
    List<Transaction> getTransactions(String userId);
}

class UserRepositoryImpl implements UserRepository {
    private RemoteDataSource remoteDataSource;
    private LocalDataSource localDataSource;
    
    // Implementation with caching strategy
}
```

### 2. ViewModel Pattern (Planned)

**Purpose**: Manage UI-related data in lifecycle-conscious way

**Benefits**:
- Survives configuration changes
- Separates UI logic from business logic
- Testable without Android dependencies

### 3. Singleton Pattern

**Usage**: 
- `TokenManager` - Single instance for token management
- API clients - Retrofit instances

### 4. Observer Pattern

**Usage**:
- BroadcastReceiver for service updates
- LiveData for UI updates (planned)
- FCM for push notifications

### 5. Strategy Pattern

**Usage**:
- Different payment methods in withdrawals
- Various earning strategies (SMS, Ads, Spin)

## Configuration Management

### BuildConfig Integration

```java
// app/build.gradle
android {
    defaultConfig {
        buildConfigField "String", "SUPABASE_URL", "\"${supabaseUrl}\""
        buildConfigField "String", "SUPABASE_ANON_KEY", "\"${supabaseKey}\""
    }
}

// Constants.java
public class Constants {
    public static final String SUPABASE_URL = BuildConfig.SUPABASE_URL;
    public static final String SUPABASE_ANON_KEY = BuildConfig.SUPABASE_ANON_KEY;
}
```

### Configuration Priority

1. **Environment Variables** (highest priority) - for CI/CD
2. **local.properties** - for local development
3. **Default Fallbacks** - for testing

## Data Flow

### SMS Mining Flow

```
User clicks "Start Mining"
    ↓
TaskFragment initiates
    ↓
SmsMiningService.fetchBatchTasks()
    ↓
Supabase RPC: fetch_batch_tasks()
    ↓
Tasks locked and assigned to user
    ↓
Service sends SMS via SmsManager
    ↓
Service collects results
    ↓
SmsMiningService.submitBatchResults()
    ↓
Supabase RPC: submit_batch_results()
    ↓
Balance updated, transaction recorded
    ↓
UI updated via BroadcastReceiver
```

### Authentication Flow

```
User enters phone & password
    ↓
LoginActivity validates input
    ↓
AuthApi.login() or signup()
    ↓
Supabase Auth validates
    ↓
Returns JWT token + user data
    ↓
TokenManager saves token
    ↓
User data saved to SharedPreferences
    ↓
Navigate to MainActivity
    ↓
Load user profile from Supabase
```

## Network Layer

### Retrofit Configuration

```java
Retrofit retrofit = new Retrofit.Builder()
    .baseUrl(Constants.SUPABASE_URL)
    .addConverterFactory(GsonConverterFactory.create())
    .build();

SupabaseApi api = retrofit.create(SupabaseApi.class);
```

### API Headers

All Supabase requests include:
- `apikey` - Anon key for authentication
- `Authorization` - Bearer token with user JWT
- `Prefer` - Return preferences (e.g., return=representation)

### Error Handling

```java
call.enqueue(new Callback<ResponseType>() {
    @Override
    public void onResponse(Call<ResponseType> call, Response<ResponseType> response) {
        if (response.isSuccessful()) {
            // Handle success
        } else {
            // Handle HTTP errors
            handleHttpError(response.code());
        }
    }
    
    @Override
    public void onFailure(Call<ResponseType> call, Throwable t) {
        // Handle network errors
        Log.e(TAG, "Network error: " + t.getMessage());
    }
});
```

## Database Architecture

### Supabase Schema

**Tables**:
1. `users` - User profiles and wallet
2. `sms_tasks` - Task queue
3. `batch_tasks` - Batch tracking
4. `sms_logs` - Audit trail
5. `transactions` - Financial history
6. `withdrawals` - Payout requests
7. `app_config` - Dynamic config
8. `otp_verifications` - Password reset

**RPC Functions**:
1. `fetch_batch_tasks` - Atomic task assignment
2. `submit_batch_results` - Result processing with rewards
3. `claim_daily_checkin` - Daily bonus with streak
4. `watch_ad_reward` - Ad viewing rewards

**Security**:
- Row Level Security (RLS) on all tables
- User-scoped policies
- Admin operations via service_role
- Audit logging for sensitive operations

## Security Architecture

### 1. Configuration Security

**Problem**: Hardcoded API keys in source code
**Solution**: BuildConfig with environment variables

```java
// ❌ Before (INSECURE)
private static final String API_KEY = "eyJhbGci...";

// ✅ After (SECURE)
public static final String API_KEY = BuildConfig.SUPABASE_ANON_KEY;
```

### 2. Token Management

**TokenManager Class**:
- Stores JWT tokens securely in EncryptedSharedPreferences
- Provides token with Bearer prefix
- Handles token expiration (planned)

### 3. Data Protection

**SharedPreferences**:
- User data in `SMSINDIA_USER` namespace
- Device ID for session tracking
- Login timestamp for session validation

**Database**:
- RLS policies prevent unauthorized access
- User can only see their own data
- Admin operations require service_role key

### 4. Input Validation

**Client Side**:
- Phone number format validation
- Password strength requirements
- Amount limits for withdrawals

**Server Side**:
- Trigger validation in database
- CHECK constraints on tables
- Type safety in RPC functions

## Background Processing

### SmsMiningService

**Purpose**: Handle SMS sending in background with batch processing

**Lifecycle**:
1. Started by TaskFragment
2. Acquires WakeLock to prevent sleep
3. Runs as Foreground Service with notification
4. Processes tasks in batches
5. Reports progress via BroadcastReceiver
6. Stops when batch complete or error

**Features**:
- Concurrent SIM card support
- Progress tracking
- Error handling and retry
- Auto-stop on completion

## UI Architecture

### Fragment-based Navigation

**MainActivity** hosts fragments in FrameLayout:
- `HomeFragment` - Tab 1
- `SpinFragment` - Tab 2
- `TaskFragment` - Tab 3
- `ShareFragment` - Tab 4
- `ProfileFragment` - Tab 5

**BottomNavigationView** handles tab switching

### ViewBinding

All layouts use ViewBinding for type-safe view access:

```java
private ActivityMainBinding binding;

@Override
protected void onCreate(Bundle savedInstanceState) {
    binding = ActivityMainBinding.inflate(getLayoutInflater());
    setContentView(binding.getRoot());
    
    binding.button.setOnClickListener(v -> {
        // Type-safe access
    });
}
```

## Future Improvements

### 1. Dependency Injection
- Implement Hilt/Dagger for DI
- Easier testing and maintenance
- Cleaner architecture

### 2. ViewModel Pattern
- Add Jetpack ViewModel
- LiveData for reactive UI
- Survive configuration changes

### 3. Repository Pattern
- Abstract data sources
- Implement caching strategy
- Offline support

### 4. Testing
- Unit tests for business logic
- Integration tests for API
- UI tests with Espresso

### 5. Kotlin Migration
- Modern language features
- Coroutines for async
- Flow for reactive streams

### 6. Modularization
- Feature modules
- Core module for shared code
- Faster build times

## Performance Considerations

### 1. Database Optimization
- Indexed queries for fast lookups
- Partial indexes for active data
- VACUUM for table maintenance

### 2. Network Optimization
- Batch API requests
- Gzip compression
- Response caching (planned)

### 3. UI Performance
- RecyclerView for lists
- View recycling
- Lazy loading (planned)

### 4. Memory Management
- Properly release resources
- Avoid memory leaks
- Use weak references where appropriate

## Monitoring & Logging

### Current Implementation
- Log.d/Log.e for debug/error logging
- Console output for development
- Supabase dashboard for backend monitoring

### Planned Improvements
- Crashlytics for crash reporting
- Analytics for user behavior
- Performance monitoring
- Custom error reporting

## Build & Deployment

### Build Types
- **Debug**: Development builds with logging
- **Release**: Production builds, minified, signed

### CI/CD Pipeline
- GitHub Actions for automated builds
- Version auto-increment
- APK signing
- Telegram distribution

### Version Management
```
versionCode = BUILD_NUMBER (from GitHub Actions)
versionName = "1.0.${BUILD_NUMBER}"
```

## Conclusion

This architecture provides:
- ✅ Clean separation of concerns
- ✅ Secure configuration management
- ✅ Scalable database design
- ✅ Maintainable code structure
- ✅ Ready for future enhancements

The foundation is solid for implementing advanced patterns like Repository and ViewModel as the application grows.
