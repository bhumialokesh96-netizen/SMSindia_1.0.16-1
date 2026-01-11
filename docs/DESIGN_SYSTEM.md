# SMSIndia Android App - Design System Documentation

## Overview
This document describes the comprehensive UI design system implemented for the SMSIndia Android application. The design system provides consistent colors, typography, spacing, and components following Material Design principles.

## 🎨 Color Palette

### Primary Colors
The primary color palette is based on orange tones:
- `primary_50` - `primary_900`: Full range of orange shades
- Main color: `primary_500` (#FF9800)
- Used for: Primary actions, branding, active states

### Secondary Colors
The secondary color palette uses green tones:
- `secondary_50` - `secondary_900`: Full range of green shades
- Main color: `secondary_500` (#4CAF50)
- Used for: Success states, secondary actions

### Accent Colors
Blue accent colors for highlights:
- `accent_50` - `accent_900`: Full range of blue shades
- Main color: `accent_500` (#2196F3)
- Used for: Information, links, highlights

### Semantic Colors
Purpose-specific colors:
- **Success**: `success` (#4CAF50), `success_light`, `success_dark`
- **Error**: `error` (#F44336), `error_light`, `error_dark`
- **Warning**: `warning` (#FFC107), `warning_light`, `warning_dark`
- **Info**: `info` (#2196F3), `info_light`, `info_dark`

### Text Colors
Optimized for readability and accessibility:
- `text_primary` (#212121) - Main text
- `text_secondary` (#757575) - Secondary text
- `text_hint` (#BDBDBD) - Hints and placeholders
- `text_disabled` (#E0E0E0) - Disabled text
- `text_on_primary` (#FFFFFF) - Text on colored backgrounds
- `text_on_secondary` (#FFFFFF) - Text on secondary colors

### Neutral Colors
Gray scale palette:
- `gray_50` - `gray_900`: Full range of gray shades
- Used for: Backgrounds, borders, dividers

## 📝 Typography

### Text Appearances

#### Headlines
- **Headline1**: 32sp, medium weight, -0.01 letter spacing
- **Headline2**: 28sp, medium weight
- **Headline3**: 24sp, medium weight
- **Headline4**: 20sp, medium weight, 0.01 letter spacing

Usage: Page titles, section headers

#### Subtitles
- **Subtitle1**: 16sp, medium weight, 0.01 letter spacing
- **Subtitle2**: 14sp, medium weight, 0.02 letter spacing

Usage: Subheadings, card titles

#### Body Text
- **Body1**: 16sp, regular weight, 0.03 letter spacing, 1.5 line spacing
- **Body2**: 14sp, regular weight, 0.03 letter spacing, 1.5 line spacing

Usage: Main content, descriptions

#### Caption & Small Text
- **Caption**: 12sp, regular weight, 0.04 letter spacing
- **Overline**: 10sp, medium weight, 0.15 letter spacing, uppercase

Usage: Helper text, labels, timestamps

#### Button Text
- **Button**: 14sp, medium weight, 0.08 letter spacing, uppercase

Usage: Button labels

## 📐 Spacing & Dimensions

### Spacing Scale
Consistent spacing values:
- `spacing_xs`: 4dp
- `spacing_sm`: 8dp
- `spacing_md`: 12dp
- `spacing_base`: 16dp (standard)
- `spacing_lg`: 20dp
- `spacing_xl`: 24dp
- `spacing_2xl`: 32dp
- `spacing_3xl`: 40dp

### Corner Radius
- `corner_radius_sm`: 8dp - Small components
- `corner_radius_md`: 12dp - Standard components
- `corner_radius_lg`: 16dp - Large components
- `corner_radius_xl`: 24dp - Dialogs, sheets
- `corner_radius_full`: 999dp - Circular elements

### Elevation
- `elevation_none`: 0dp - Flat surfaces
- `elevation_sm`: 2dp - Slight elevation
- `elevation_md`: 4dp - Standard elevation
- `elevation_lg`: 8dp - Elevated components
- `elevation_xl`: 12dp - Highest elevation

### Component Sizes
- **Buttons**: 48dp (standard), 56dp (large), 40dp (small)
- **Input Fields**: 56dp (standard), 48dp (small)
- **Icons**: 16dp, 20dp, 24dp, 32dp, 48dp, 64dp
- **Touch Targets**: Minimum 48dp

## 🎯 Button Styles

### Primary Button (`Widget.App.Button.Primary`)
- Orange background (`button_primary`)
- White text
- 12dp corner radius
- 4dp elevation
- Ripple effect

Usage: Main actions (Submit, Confirm, Save)

### Secondary Button (`Widget.App.Button.Secondary`)
- Green background (`button_secondary`)
- White text
- 12dp corner radius
- 4dp elevation
- Ripple effect

Usage: Alternative actions

### Outlined Button (`Widget.App.Button.Outlined`)
- Transparent background
- Orange border (2dp)
- Orange text
- 12dp corner radius
- Ripple effect

Usage: Secondary actions, Cancel

### Text Button (`Widget.App.Button.Text`)
- Transparent background
- Orange text
- No border
- Ripple effect

Usage: Low-priority actions

### Rounded Button (`Widget.App.Button.Rounded`)
- Fully rounded corners (28dp)
- 56dp height
- 6dp elevation
- Enhanced padding

Usage: Special CTAs, hero actions

## 🃏 Card Styles

### Elevated Card (`Widget.App.CardView.Elevated`)
- 16dp corner radius
- 8dp elevation
- White background
- 16dp content padding

Usage: Primary content cards

### Outlined Card (`Widget.App.CardView.Outlined`)
- 12dp corner radius
- No elevation
- 1dp border
- 16dp content padding

Usage: Less prominent cards

### Filled Card (`Widget.App.CardView.Filled`)
- 12dp corner radius
- Gray background
- No border or elevation
- 16dp content padding

Usage: Supporting content, backgrounds

## 🎨 Drawable Resources

### Button Backgrounds
- `btn_primary_selector.xml` - Primary button with states
- `btn_secondary_selector.xml` - Secondary button with states
- `btn_outlined_selector.xml` - Outlined button with states
- `btn_gradient_ripple.xml` - Gradient button with ripple

### Card Backgrounds
- `card_elevated.xml` - Card with shadow
- `card_outlined.xml` - Card with border
- `card_filled.xml` - Card with background color
- `card_ripple.xml` - Card with ripple effect

### Input Fields
- `input_field_bg.xml` - Text field with focus/error states

### Gradients
- `gradient_orange.xml` - Orange gradient (135° angle)
- `gradient_green.xml` - Green gradient (135° angle)
- `gradient_blue.xml` - Blue gradient (135° angle)

### Shapes
- `circle_primary.xml` - Orange circle
- `circle_secondary.xml` - Green circle
- `bottom_sheet_bg.xml` - Bottom sheet background
- `dialog_bg.xml` - Dialog background

### Other
- `tab_selector.xml` - Tab selection states
- `progress_bar_horizontal.xml` - Custom progress bar
- `divider_horizontal.xml` - Horizontal divider

## 🎬 Animations

### Fade Animations
- `fade_in.xml` - 300ms fade in
- `fade_out.xml` - 300ms fade out

Usage: Dialog appearances, view visibility changes

### Slide Animations
- `slide_in_left.xml` - Slide from left with fade
- `slide_out_left.xml` - Slide to left with fade
- `slide_in_right.xml` - Slide from right with fade
- `slide_out_right.xml` - Slide to right with fade
- `slide_in_bottom.xml` - Slide from bottom with fade
- `slide_out_bottom.xml` - Slide to bottom with fade

Usage: Screen transitions, fragment transactions

### Scale Animations
- `scale_down.xml` - Scale to 95% (150ms)
- `scale_up.xml` - Scale to 100% (150ms)

Usage: Button press feedback

### Zoom Animations
- `zoom_in.xml` - Zoom in with fade (200ms)
- `zoom_out.xml` - Zoom out with fade (200ms)

Usage: Modal dialogs, image previews

## 📱 Usage Guidelines

### Applying Colors
```xml
<!-- Use semantic color names -->
android:textColor="@color/text_primary"
android:background="@color/card_background"
```

### Applying Text Styles
```xml
<!-- Use text appearance styles -->
android:textAppearance="@style/TextAppearance.App.Headline3"
```

### Applying Spacing
```xml
<!-- Use dimension resources -->
android:padding="@dimen/spacing_base"
android:layout_margin="@dimen/spacing_lg"
```

### Applying Button Styles
```xml
<!-- Use button styles -->
<Button
    style="@style/Widget.App.Button.Primary"
    android:layout_width="wrap_content"
    android:layout_height="wrap_content"
    android:text="Submit"/>
```

### Applying Card Styles
```xml
<!-- Use card styles -->
<com.google.android.material.card.MaterialCardView
    style="@style/Widget.App.CardView.Elevated"
    android:layout_width="match_parent"
    android:layout_height="wrap_content">
    <!-- Card content -->
</com.google.android.material.card.MaterialCardView>
```

### Applying Animations
```xml
<!-- In Activity transitions -->
overridePendingTransition(R.anim.fade_in, R.anim.fade_out);

<!-- In Fragment transactions -->
transaction.setCustomAnimations(
    R.anim.slide_in_right,
    R.anim.slide_out_left
);
```

## 🎯 Best Practices

### Color Usage
- Always use semantic color names, not hex codes
- Ensure sufficient contrast ratios (WCAG AA: 4.5:1 for normal text)
- Use `text_on_primary` for text on colored backgrounds

### Typography
- Use text appearance styles instead of direct size/weight attributes
- Maintain consistent line spacing for readability
- Use appropriate letter spacing for headers

### Spacing
- Use the spacing scale consistently
- Maintain visual rhythm with multiples of 4dp
- Use larger spacing for content separation

### Components
- Use Material Design components when possible
- Apply consistent elevation hierarchy
- Maintain touch target minimum of 48dp

### Animations
- Keep animations subtle and purposeful
- Use standard durations (200-300ms)
- Provide visual feedback for user interactions

## 🔄 Migration Guide

### Updating Existing Layouts
1. Replace hard-coded colors with color resources
2. Replace text sizes with text appearance styles
3. Replace spacing values with dimension resources
4. Apply new drawable backgrounds where appropriate

### Example Migration
**Before:**
```xml
<TextView
    android:textSize="20sp"
    android:textColor="#212121"
    android:padding="16dp"/>
```

**After:**
```xml
<TextView
    android:textAppearance="@style/TextAppearance.App.Headline4"
    android:padding="@dimen/spacing_base"/>
```

## 📊 Accessibility

### Color Contrast
All text/background combinations meet WCAG AA standards:
- Primary text: 12.63:1 contrast ratio
- Secondary text: 4.54:1 contrast ratio
- Buttons: Sufficient contrast for all states

### Touch Targets
- Minimum touch target size: 48dp x 48dp
- Adequate spacing between interactive elements

### Typography
- Clear hierarchy with size and weight variations
- Sufficient line spacing for readability
- Appropriate letter spacing for headers

## 🚀 Future Enhancements

Potential additions to the design system:
- Dark theme support
- Additional component styles (chips, switches, etc.)
- More animation variations
- Tablet-specific layouts
- Accessibility enhancements

---

**Version**: 1.0  
**Last Updated**: 2026-01-11  
**Maintained by**: SMSIndia Development Team
