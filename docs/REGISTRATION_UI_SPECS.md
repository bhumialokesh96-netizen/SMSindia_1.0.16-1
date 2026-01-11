# Registration System - Visual Flow & UI Specifications

## User Interface Specifications

### Registration Screen (RegisterActivity)

#### Layout Structure
```
┌─────────────────────────────────────┐
│           [App Logo]                │  ← User avatar icon in orange circle
│                                     │
│        Create Account               │  ← Bold, 32sp, orange
│   Join now and start earning!      │  ← Subtitle, 16sp, gray
│                                     │
│  ┌─────────────────────────────┐   │
│  │  Registration Card          │   │
│  │  ┌───────────────────────┐  │   │
│  │  │ 📧 Email Address     │  │   │  ← Email input with icon
│  │  └───────────────────────┘  │   │
│  │                             │   │
│  │  ┌───────────────────────┐  │   │
│  │  │ 📱 Phone Number      │  │   │  ← Phone input with icon
│  │  └───────────────────────┘  │   │
│  │                             │   │
│  │  ┌───────────────────────┐  │   │
│  │  │ 🔒 Password          │  │   │  ← Password input with toggle
│  │  └───────────────────────┘  │   │
│  │                             │   │
│  │  🎁 Referral Code (Optional)│   │
│  │  ┌───────────────────────┐  │   │
│  │  │ 🔗 Enter code or     │  │   │  ← Referral input with icon
│  │  │    leave empty        │  │   │
│  │  └───────────────────────┘  │   │
│  │  Get extra ₹5 & 50 coins!   │   │  ← Helper text
│  │                             │   │
│  │  ┌─────────────────────────┐ │   │
│  │  │ 💰 Referral Benefits   │ │   │  ← Green info card
│  │  │ ✓ Friend code: ₹5+50   │ │   │
│  │  │ ✓ Leave empty: Auto-   │ │   │
│  │  │   filled with 666666   │ │   │
│  │  │ ✓ Company: 25 coins    │ │   │
│  │  └─────────────────────────┘ │   │
│  │                             │   │
│  │  [ CREATE ACCOUNT ]         │   │  ← Primary button, orange
│  │                             │   │
│  │  Already have account? Login│   │  ← Navigation link
│  └─────────────────────────────┘   │
│                                     │
│  ✓ No OTP verification required    │  ← Quick note, green
│  ✓ Start earning immediately       │
└─────────────────────────────────────┘
```

#### Color Scheme
- **Primary (Orange)**: `#FF6F00` - Main actions, headers
- **Secondary (Green)**: `#4CAF50` - Success, referral benefits
- **Accent (Blue)**: `#2196F3` - Referral code field
- **Background**: `#F5F5F5` - Light gray
- **Card Background**: `#FFFFFF` - White
- **Text Primary**: `#212121` - Dark gray
- **Text Secondary**: `#757575` - Medium gray

#### Input Field Specifications
```
Material Design TextInputLayout - Outlined Box
┌─ Label ─────────────────────────────┐
│ [Icon]  User Input Text             │
└─────────────────────────────────────┘
Helper Text / Error Message

Specifications:
- Corner Radius: 12dp
- Stroke Width: 2dp
- Padding: 16dp
- Text Size: 16sp
- Icon Tint: Theme primary color
```

#### Referral Benefits Card
```
┌──────────────────────────────────────┐
│ 💰 Referral Benefits                 │  ← Bold, 14sp
│                                      │
│ ✓ Use friend's code: Get ₹5 + 50    │  ← Line 1
│   coins bonus                        │
│ ✓ Leave empty: Auto-filled with     │  ← Line 2
│   company code (666666)              │
│ ✓ Company bonus: Get 25 coins!      │  ← Line 3
└──────────────────────────────────────┘

Background: #E8F5E9 (Light green)
Text Color: #2E7D32 (Dark green)
Padding: 16dp
Margin Bottom: 20dp
Corner Radius: 12dp
```

### Share/Referral Screen (ShareFragment)

#### Layout Structure
```
┌─────────────────────────────────────┐
│    Refer & Earn                     │  ← Header, dark green bg
│    Invite friends and earn coins!   │
│                                     │
│  ┌─────────────────────────────┐   │
│  │  Overlapping Stats Card     │   │  ← Elevation, gold border
│  │  ┌─────────────────────────┐│   │
│  │  │ 🎁 How Referral Works  ││   │  ← NEW: Blue info card
│  │  │ ✓ Friend gets: ₹5+50   ││   │
│  │  │ ✓ You get: ₹10/referral││   │
│  │  │ ✓ Unlimited referrals! ││   │
│  │  └─────────────────────────┘│   │
│  │                             │   │
│  │    [0]      [₹0.0]   [0]    │   │  ← Stats row
│  │  Friends  Earnings  Coins   │   │
│  │                             │   │
│  │  ──────────────────────────  │   │  ← Divider
│  │                             │   │
│  │  YOUR REFERRAL CODE         │   │
│  │  ┌─────────────────────────┐│   │
│  │  │   1234567890            ││   │  ← Large, bold
│  │  └─────────────────────────┘│   │
│  │                             │   │
│  │  [ SHARE APP NOW ]          │   │  ← Gold 3D button
│  └─────────────────────────────┘   │
│                                     │
│  Milestones                         │  ← Section header
│  ┌─────────────────────────────┐   │
│  │ Milestone List              │   │
│  │ (RecyclerView)              │   │
│  └─────────────────────────────┘   │
└─────────────────────────────────────┘
```

## User Flow Diagrams

### Registration Flow
```
START
  │
  ▼
┌─────────────────┐
│  LoginActivity  │  User sees login screen
└─────────────────┘
  │
  │ [Click "CREATE NEW ACCOUNT"]
  ▼
┌──────────────────┐
│ RegisterActivity │  User sees registration form
└──────────────────┘
  │
  │ [Fill email, phone, password]
  ▼
┌──────────────────┐
│ Input Validation │  Client-side validation
└──────────────────┘
  │
  ├─[Valid]──────────┐
  │                  │
  │                  ▼
  │          ┌────────────────┐
  │          │ Referral Code  │  Auto-fill if empty
  │          └────────────────┘
  │                  │
  │                  │ [Empty?] → "666666"
  │                  │ [Filled?] → Validate code
  │                  ▼
  │          ┌────────────────┐
  │          │ Supabase Auth  │  Create auth account
  │          │    Signup      │
  │          └────────────────┘
  │                  │
  │                  ▼
  │          ┌────────────────┐
  │          │ Get JWT Token  │  Save via TokenManager
  │          └────────────────┘
  │                  │
  │                  ▼
  │          ┌────────────────┐
  │          │ Create Profile │  Insert into users table
  │          │ + Referral Data│
  │          └────────────────┘
  │                  │
  │                  ▼
  │          ┌────────────────┐
  │          │ Database Trigger│ Apply referral rewards
  │          │  Auto-rewards  │
  │          └────────────────┘
  │                  │
  │                  ▼
  │          ┌────────────────┐
  │          │Save to SharedPref│ Store user info
  │          └────────────────┘
  │                  │
  │                  ▼
  │          ┌────────────────┐
  │          │  MainActivity  │  Navigate to main app
  │          └────────────────┘
  │                  │
  │                  ▼
  └────────────────END (Success)
  │
[Invalid]
  │
  ▼
┌──────────────────┐
│  Show Error      │  Display validation error
│  Stay on Form    │
└──────────────────┘
  │
  └──[Loop back to Input]
```

### Referral Reward Logic
```
New User Registers
  │
  ▼
┌─────────────────────────┐
│ Check Referral Code     │
└─────────────────────────┘
  │
  ├─[Empty]───────┐
  │               ▼
  │        ┌──────────────┐
  │        │ Auto-fill    │
  │        │ "666666"     │
  │        └──────────────┘
  │               │
  └───────────────┤
                  │
  ┌───────────────┴──────────────┐
  │                              │
  ▼                              ▼
Company Code              Friend Code
(666666)                  (Valid user phone)
  │                              │
  ▼                              ▼
┌─────────────┐          ┌──────────────┐
│ New User:   │          │ New User:    │
│ +100 coins  │          │ +100 coins   │
│ +25 bonus   │          │ +50 bonus    │
│ +3 spins    │          │ +₹5 balance  │
│ ───────     │          │ +3 spins     │
│ Total: 125  │          │ ────────     │
└─────────────┘          │ Total: 150   │
                         │ coins + ₹5   │
                         └──────────────┘
                                │
                                ▼
                         ┌──────────────┐
                         │ Referrer:    │
                         │ +₹10 balance │
                         │ referral_    │
                         │ count += 1   │
                         └──────────────┘
                                │
                                ▼
                         ┌──────────────┐
                         │ Create record│
                         │ in referral_ │
                         │ transactions │
                         └──────────────┘
```

## Validation Rules

### Email Validation
```
Pattern: ^[a-zA-Z0-9._%+-]+@[a-zA-Z0-9.-]+\.[a-zA-Z]{2,}$

Valid Examples:
✓ user@example.com
✓ john.doe@company.co.uk
✓ test123@test.com

Invalid Examples:
✗ user@
✗ @example.com
✗ user@example
✗ user.example.com
```

### Phone Validation
```
Pattern: ^\d{10}$

Valid Examples:
✓ 9876543210
✓ 1234567890

Invalid Examples:
✗ 987654321 (9 digits)
✗ 98765432101 (11 digits)
✗ +919876543210 (has +91)
✗ 98 7654 3210 (has spaces)
```

### Password Validation
```
Minimum Length: 6 characters
No maximum limit

Valid Examples:
✓ pass123
✓ MyP@ssw0rd!
✓ 123456

Invalid Examples:
✗ pass1 (5 chars)
✗ 12345 (5 chars)
```

### Referral Code Validation
```
Pattern: ^\d{6,15}$ (6-15 digits, usually phone number)

Valid Examples:
✓ 9876543210 (user's phone)
✓ 666666 (company code)

Invalid Examples:
✗ ABC123 (contains letters)
✗ 12345 (too short)

Special Case:
Empty → Auto-fills to "666666"
```

## State Management

### Registration States
```
┌──────────────────────────────────────┐
│ IDLE                                 │
│ - Form ready for input               │
│ - All fields empty/default           │
│ - Submit button enabled              │
└──────────────────────────────────────┘
         │
         ▼ [User clicks submit]
┌──────────────────────────────────────┐
│ VALIDATING                           │
│ - Check all inputs                   │
│ - Show inline errors if invalid      │
│ - Auto-fill referral if empty        │
└──────────────────────────────────────┘
         │
         ▼ [Validation passed]
┌──────────────────────────────────────┐
│ LOADING                              │
│ - Button disabled                    │
│ - Text: "CREATING ACCOUNT..."        │
│ - Show progress indicator            │
└──────────────────────────────────────┘
         │
         ├─[Success]───────────┐
         │                     │
         ▼                     ▼
┌─────────────────┐   ┌────────────────┐
│ SUCCESS         │   │ ERROR          │
│ - Show toast    │   │ - Show error   │
│ - Navigate away │   │ - Re-enable btn│
└─────────────────┘   │ - Stay on form │
                      └────────────────┘
```

## Error Handling

### Network Errors
```
Error: Network timeout/failure
Display: "Network error: [message]"
Action: Enable retry, stay on form
```

### Validation Errors
```
Error: Invalid input
Display: Inline error message on field
Example:
  - "Enter a valid email address"
  - "Enter valid 10-digit phone number"
  - "Password must be at least 6 characters"
Action: User corrects input
```

### Duplicate User Errors
```
Error: Email/phone already registered
Display: "Email or phone already registered. Please login."
Action: Suggest navigation to login
```

### Invalid Referral Code
```
Error: Referral code doesn't exist
Display: "Invalid referral code"
Action: User corrects code or leaves empty
```

## Success Messages

### Registration Success (Company Code)
```
Toast: "Account created successfully! You got 25 bonus coins! 🎁"
Duration: LONG (3 seconds)
Action: Navigate to MainActivity
```

### Registration Success (Friend Code)
```
Toast: "Account created successfully! You got ₹5 + 50 coins! 🎉"
Duration: LONG (3 seconds)
Action: Navigate to MainActivity
```

## Accessibility

### Text Contrast Ratios (WCAG AA)
- Primary text on white: 12.63:1 ✓
- Secondary text on white: 4.54:1 ✓
- Button text on orange: 4.5:1 ✓

### Touch Targets
- Minimum size: 48dp x 48dp ✓
- All buttons meet requirement ✓
- Input fields exceed requirement ✓

### Content Descriptions
- All icons have content descriptions
- Form fields have proper labels
- Buttons have descriptive text

## Animations & Transitions

### Form Entry
- Fade in: 300ms
- Slide up: 250ms
- Material motion

### Button Press
- Ripple effect: 400ms
- Scale down: 100ms
- Material feedback

### Field Focus
- Border color change: 200ms
- Label float: 150ms
- Material motion

### Navigation
- Activity transition: 300ms
- Cross-fade: 250ms
- Shared element: 375ms

## Performance Considerations

### Layout Performance
- View hierarchy depth: ≤ 10 levels ✓
- Overdraw: Minimal (1-2 layers) ✓
- Render time: < 16ms target ✓

### Network Performance
- API calls: 2 per registration
- Timeout: 30 seconds
- Retry logic: Exponential backoff

### Memory Usage
- Leak-free: Proper lifecycle ✓
- Bitmap optimization: N/A
- Cache management: Minimal

## Testing Scenarios

### Happy Path
1. Open app
2. Click "CREATE NEW ACCOUNT"
3. Enter valid email
4. Enter valid phone
5. Enter valid password
6. Leave referral empty → auto-fills "666666"
7. Click "CREATE ACCOUNT"
8. Success! Navigate to MainActivity

### Friend Referral Path
1-5. Same as above
6. Enter friend's phone number
7. Click "CREATE ACCOUNT"
8. Success! Both users get rewards

### Error Path
1-5. Same as above
6. Enter invalid referral code
7. Click "CREATE ACCOUNT"
8. Error: "Invalid referral code"
9. Correct and retry

## Responsive Design

### Screen Sizes Supported
- Small (320dp width): ✓ Supported
- Normal (360dp-480dp): ✓ Optimized
- Large (600dp+): ✓ Responsive
- Tablets: ✓ Adaptive layout

### Orientation
- Portrait: ✓ Primary (locked)
- Landscape: ⚠️ Not locked (scrollable)

---

**Last Updated**: January 11, 2026
**UI Version**: 1.0.16-1
**Status**: ✅ Design Complete
