# SMS India - Flutter UI Module

This directory contains the Flutter-based user interface for the SMS India application.

## Architecture

This Flutter module provides the complete UI layer while native Android handles all SMS operations. Communication between Flutter and native code uses Platform Channels.

## Structure

```
flutter_ui/
├── lib/
│   ├── main.dart                   # App entry point
│   ├── constants/
│   │   └── app_colors.dart         # Design system colors
│   ├── screens/
│   │   ├── splash_screen.dart      # Animated splash
│   │   ├── login_screen.dart       # Login UI
│   │   ├── main_screen.dart        # Bottom navigation
│   │   ├── home_screen.dart        # Dashboard
│   │   ├── task_screen.dart        # SMS mining controls
│   │   ├── spin_screen.dart        # Lucky wheel (TODO)
│   │   ├── share_screen.dart       # Referral program (TODO)
│   │   └── profile_screen.dart     # User profile
│   ├── services/
│   │   ├── sms_service.dart        # Platform channel client for SMS
│   │   └── auth_service.dart       # Authentication service
│   └── widgets/
│       └── (reusable components - to be added)
└── pubspec.yaml                    # Dependencies
```

## Setup

### Prerequisites

- Flutter SDK 3.27.1 or higher
- Dart 3.0.0 or higher
- Android Studio / VS Code with Flutter extensions

### Installation

1. Install Flutter SDK:
   ```bash
   # Download and extract Flutter
   # Add to PATH
   export PATH="$PATH:/path/to/flutter/bin"
   ```

2. Get dependencies:
   ```bash
   cd flutter_ui
   flutter pub get
   ```

3. Verify setup:
   ```bash
   flutter doctor
   flutter analyze
   ```

## Features

### Implemented
- ✅ Splash screen with animations
- ✅ Login screen with form validation
- ✅ Bottom navigation with 5 tabs
- ✅ Home dashboard with stats
- ✅ Task screen with SMS mining controls
- ✅ Profile screen with logout
- ✅ Platform channel integration for SMS
- ✅ Event channel for real-time updates
- ✅ State management with Provider
- ✅ Material Design 3 theme
- ✅ Gradient backgrounds and animations

### To Be Implemented
- ⏳ Registration screen
- ⏳ Lucky spin wheel
- ⏳ Referral/Share screen
- ⏳ Transaction history
- ⏳ Withdrawal screens
- ⏳ Settings screen
- ⏳ Notification handling

## Platform Channel Communication

### SMS Operations

The Flutter UI communicates with native Android code via Platform Channels:

```dart
// Example: Start SMS Mining
final smsService = Provider.of<SmsService>(context);
final started = await smsService.startSmsMining(
  userId: 'user123',
  simSlot: 0,
);

// Listen to SMS events
smsService.addListener(() {
  print('Progress: ${smsService.progress}%');
  print('Status: ${smsService.statusMessage}');
});
```

### Available Methods

1. **checkSmsPermissions()** - Check if SMS permissions are granted
2. **requestSmsPermissions()** - Request SMS permissions
3. **sendSms(phoneNumber, message)** - Send single SMS
4. **startSmsMining(userId, simSlot)** - Start SMS mining service
5. **stopSmsMining()** - Stop SMS mining service
6. **getServiceStatus()** - Get current service status
7. **getAvailableTasksCount()** - Get available tasks count

### Event Stream

Listen to real-time updates:
- `progress` - Mining progress updates
- `batch_complete` - Batch completion notification
- `service_started` - Service started event
- `service_stopped` - Service stopped event

## Development

### Running the App

```bash
# Run in debug mode
flutter run

# Run in profile mode
flutter run --profile

# Run in release mode
flutter run --release
```

### Code Quality

```bash
# Analyze code
flutter analyze

# Format code
flutter format lib/

# Run tests (when available)
flutter test
```

### Adding New Screens

1. Create screen file in `lib/screens/`
2. Follow existing patterns (StatefulWidget/StatelessWidget)
3. Use Provider for state management
4. Access SMS operations via SmsService
5. Update navigation in `main_screen.dart`

Example:
```dart
import 'package:flutter/material.dart';
import 'package:provider/provider.dart';
import '../services/sms_service.dart';
import '../constants/app_colors.dart';

class NewScreen extends StatelessWidget {
  const NewScreen({super.key});

  @override
  Widget build(BuildContext context) {
    final smsService = Provider.of<SmsService>(context);
    
    return Scaffold(
      appBar: AppBar(title: const Text('New Screen')),
      body: Center(
        child: Text('New Screen Content'),
      ),
    );
  }
}
```

## Design System

### Colors

Defined in `constants/app_colors.dart`:
- Primary: Indigo (#6366F1)
- Secondary: Amber (#F59E0B)
- Accent: Emerald (#10B981)
- Status colors: Success, Error, Warning, Info
- Gamification: Gold, Silver, Bronze

### Typography

Using Google Fonts (Poppins family):
- Regular (400)
- Semi-Bold (600)
- Bold (700)

### Components

Standard Material Design 3 components:
- Elevated buttons with gradients
- Text fields with rounded borders
- Cards with shadows
- Bottom navigation bar
- App bar with transparency

## Performance

### Best Practices
- Use `const` constructors where possible
- Implement `Keys` for list items
- Use `ListView.builder` for long lists
- Lazy load images and assets
- Minimize widget rebuilds

### Optimization
- Code splitting for large screens
- Image optimization and caching
- Efficient state management
- Platform channel batching

## Testing

### Widget Tests (To Be Added)
```bash
flutter test test/widget_test.dart
```

### Integration Tests (To Be Added)
```bash
flutter test integration_test/
```

## Building

### Debug Build
```bash
flutter build apk --debug
```

### Release Build
```bash
flutter build apk --release
flutter build appbundle --release
```

## Troubleshooting

### Common Issues

1. **Dependencies not resolving**
   ```bash
   flutter clean
   flutter pub get
   ```

2. **Platform channel not working**
   - Verify channel names match in Dart and Java
   - Check native side is initialized
   - Enable debug logging

3. **Hot reload not working**
   - Restart app completely
   - Check for syntax errors

## Resources

- [Flutter Documentation](https://flutter.dev/docs)
- [Dart Language Tour](https://dart.dev/guides/language/language-tour)
- [Material Design](https://material.io/design)
- [Platform Channels](https://flutter.dev/docs/development/platform-integration/platform-channels)

## License

Same as parent project

---

**Version:** 1.0.20+20
**Flutter SDK:** >=3.0.0
**Dart SDK:** >=3.0.0
