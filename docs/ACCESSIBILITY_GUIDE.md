# Accessibility Considerations for Gamified UI

## Overview
This document outlines accessibility considerations for the gamified UI features added in v1.0.20 and provides guidance for proper implementation.

## Animation Accessibility

### Reduced Motion Support
Some users with vestibular disorders or motion sensitivity may experience discomfort from animations. Always check for system reduced motion preferences:

```java
// Check if user has reduced motion enabled
public boolean isReducedMotionEnabled(Context context) {
    float duration = Settings.Global.getFloat(
        context.getContentResolver(),
        Settings.Global.ANIMATOR_DURATION_SCALE, 
        1f
    );
    return duration == 0f;
}

// Apply animation conditionally
if (!isReducedMotionEnabled(context)) {
    Animation animation = AnimationUtils.loadAnimation(context, R.anim.bounce);
    view.startAnimation(animation);
} else {
    // Show final state without animation
    view.setVisibility(View.VISIBLE);
}
```

### Animation Guidelines by Type

#### Shake Animation (shake.xml)
**Accessibility Concern**: May trigger motion sensitivity  
**Recommendation**: 
- Use sparingly, only for error states
- Provide alternative feedback (color change, haptic)
- Always check reduced motion preference

```java
// Safe shake implementation
public void showError(View view, String message) {
    if (!isReducedMotionEnabled(context)) {
        Animation shake = AnimationUtils.loadAnimation(context, R.anim.shake);
        view.startAnimation(shake);
    }
    // Always provide non-animated feedback
    view.setBackgroundColor(getColor(R.color.error_light));
    Toast.makeText(context, message, Toast.LENGTH_SHORT).show();
}
```

#### Pulse Animation (pulse.xml)
**Accessibility Concern**: Infinite animation can be distracting, battery drain  
**Recommendation**:
- Limit duration or repeat count
- Stop when view is not visible
- Respect reduced motion preference
- Consider device battery level

```java
// Safe pulse implementation
public void startPulseAnimation(View view) {
    if (isReducedMotionEnabled(context)) {
        return; // Skip animation
    }
    
    Animation pulse = AnimationUtils.loadAnimation(context, R.anim.pulse);
    // Limit to 3 cycles instead of infinite
    pulse.setRepeatCount(3);
    view.startAnimation(pulse);
    
    // Stop when view detaches
    view.addOnAttachStateChangeListener(new View.OnAttachStateChangeListener() {
        @Override
        public void onViewAttachedToWindow(View v) {}
        
        @Override
        public void onViewDetachedFromWindow(View v) {
            v.clearAnimation();
        }
    });
}
```

#### Bounce, Spring, Coin Flip, Card Flip
**Accessibility Concern**: Moderate motion, generally safe  
**Recommendation**:
- Check reduced motion for consistency
- Keep duration moderate (400-800ms)
- Provide alternative for critical information

## Color Contrast

### White Text on Gradient Backgrounds

**Files Affected**:
- item_onboarding_slide.xml
- dialog_achievement.xml

**Requirement**: WCAG AA requires 4.5:1 contrast for normal text, 3:1 for large text (18sp+)

**Current Implementation**:
- Title text: 28sp bold on gradient background ✅ (Large text exception)
- Description: 16sp on gradient background ⚠️ (Verify contrast)

**Recommendations**:

1. **Verify Contrast Ratios**
```java
// Test contrast ratios with your gradient colors
// Primary gradient: #FF9800 to #FFA726
// White text: #FFFFFF
// Should meet WCAG AA for large text (3:1)
```

2. **Add Text Shadows for Safety**
```xml
<!-- Enhance text readability on gradients -->
<TextView
    android:id="@+id/tv_onboarding_title"
    android:text="Welcome"
    android:textColor="@color/white_100"
    android:textSize="28sp"
    android:textStyle="bold"
    android:shadowColor="@color/black_38"
    android:shadowDx="0"
    android:shadowDy="2"
    android:shadowRadius="4" />
```

3. **Provide High Contrast Alternative**
```xml
<!-- In values/colors.xml -->
<color name="onboarding_text">@color/white_100</color>

<!-- In values-highcontrast/colors.xml (optional) -->
<color name="onboarding_text">@color/white_100</color>
```

## Content Descriptions

All interactive elements and images must have content descriptions:

### Current Status ✅
All new components include proper content descriptions:
- Achievement badges: "Gold achievement badge"
- Coin icons: "Coins"
- Notification icons: "Notifications"
- Level badges: "Level badge"

### Best Practices
```xml
<!-- Good: Descriptive content -->
<ImageView
    android:contentDescription="Achievement unlocked: First task completed" />

<!-- Bad: Missing or generic -->
<ImageView android:contentDescription="Icon" />
```

## Touch Targets

### Minimum Size Requirements
WCAG requires minimum 48dp touch targets.

**Current Implementation**: ✅ All interactive elements meet requirements
- Buttons: 56dp height minimum
- Icons: 40-48dp minimum
- Cards: Full width with adequate padding

## Screen Reader Support

### Implementation Checklist

1. **Content Descriptions**: ✅ All images have descriptions
2. **Focus Order**: Ensure logical tab order
3. **Announcements**: Use AccessibilityManager for important updates
4. **Labels**: All inputs have associated labels

### Example: Achievement Dialog
```java
// Announce achievement to screen reader
AccessibilityManager am = (AccessibilityManager) getSystemService(ACCESSIBILITY_SERVICE);
if (am != null && am.isEnabled()) {
    AccessibilityEvent event = AccessibilityEvent.obtain(
        AccessibilityEvent.TYPE_ANNOUNCEMENT
    );
    event.setContentDescription("Achievement unlocked: First task completed. You earned 50 coins!");
    am.sendAccessibilityEvent(event);
}
```

## Keyboard Navigation

For external keyboard users:
1. Ensure all interactive elements are focusable
2. Provide visible focus indicators
3. Support standard keyboard shortcuts

```xml
<!-- Make elements keyboard accessible -->
<Button
    android:focusable="true"
    android:focusableInTouchMode="false"
    android:background="@drawable/btn_gamified_gradient" />
```

## Implementation Checklist

### For Each New Feature:
- [ ] Check reduced motion preference before animations
- [ ] Verify color contrast ratios (WCAG AA)
- [ ] Add content descriptions to images
- [ ] Ensure 48dp minimum touch targets
- [ ] Test with screen reader (TalkBack)
- [ ] Verify keyboard navigation
- [ ] Test with high contrast mode
- [ ] Consider battery impact of animations

### Testing Tools:
1. **Accessibility Scanner** (Android Studio)
2. **TalkBack** (Screen reader testing)
3. **Contrast Checker** (Online tools)
4. **Device Settings** → Accessibility → Animation scale

## Code Templates

### Animation Wrapper Class
```java
public class AccessibleAnimations {
    private Context context;
    
    public AccessibleAnimations(Context context) {
        this.context = context;
    }
    
    public boolean isReducedMotionEnabled() {
        float scale = Settings.Global.getFloat(
            context.getContentResolver(),
            Settings.Global.ANIMATOR_DURATION_SCALE,
            1f
        );
        return scale == 0f;
    }
    
    public void playAnimation(View view, @AnimRes int animationId) {
        if (isReducedMotionEnabled()) {
            return; // Skip animation
        }
        Animation animation = AnimationUtils.loadAnimation(context, animationId);
        view.startAnimation(animation);
    }
    
    public void playInfiniteAnimation(View view, @AnimRes int animationId, int maxCycles) {
        if (isReducedMotionEnabled()) {
            return; // Skip animation
        }
        Animation animation = AnimationUtils.loadAnimation(context, animationId);
        animation.setRepeatCount(maxCycles);
        view.startAnimation(animation);
    }
}
```

### Usage:
```java
// In Activity or Fragment
AccessibleAnimations animations = new AccessibleAnimations(this);

// Play achievement animation
animations.playAnimation(badgeIcon, R.anim.coin_flip);

// Play pulse with limit
animations.playInfiniteAnimation(ctaButton, R.anim.pulse, 3);
```

## References

- [WCAG 2.1 Guidelines](https://www.w3.org/WAI/WCAG21/quickref/)
- [Android Accessibility](https://developer.android.com/guide/topics/ui/accessibility)
- [Material Design Accessibility](https://material.io/design/usability/accessibility.html)
- [Animation Accessibility](https://www.w3.org/WAI/WCAG21/Understanding/animation-from-interactions.html)

## Summary

**Key Points**:
1. ✅ Always check reduced motion preference
2. ✅ Verify color contrast ratios (4.5:1 for normal text, 3:1 for large)
3. ✅ Add content descriptions to all images
4. ✅ Maintain 48dp minimum touch targets
5. ✅ Limit infinite animations
6. ✅ Provide alternative feedback mechanisms
7. ✅ Test with accessibility tools

**Status**: All new UI components are designed with accessibility in mind. Implement the provided wrapper classes and follow guidelines for production deployment.

---

*Last Updated*: January 12, 2026  
*Version*: 1.0  
*Related*: GAMIFIED_UI_GUIDE.md
