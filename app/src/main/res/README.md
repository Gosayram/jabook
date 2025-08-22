# App Resources Structure

This directory contains all the visual resources for the JaBook application.

## Directory Structure

### Drawable Resources (`drawable/`)
Contains drawable resources used throughout the application:
- Icons
- Backgrounds
- UI elements
- Vector graphics
- Custom shapes

### Mipmap Resources (`mipmap-*/`)
Contains application launcher icons in different densities:

#### `mipmap-mdpi/`
- Medium density (~160dpi)
- Launcher icon: 48x48px
- Round launcher icon: 48x48px

#### `mipmap-hdpi/`
- High density (~240dpi)
- Launcher icon: 72x72px
- Round launcher icon: 72x72px

#### `mipmap-xhdpi/`
- Extra high density (~320dpi)
- Launcher icon: 96x96px
- Round launcher icon: 96x96px

#### `mipmap-xxhdpi/`
- Extra extra high density (~480dpi)
- Launcher icon: 144x144px
- Round launcher icon: 144x144px

#### `mipmap-xxxhdpi/`
- Extra extra extra high density (~640dpi)
- Launcher icon: 192x192px
- Round launcher icon: 192x192px

#### `mipmap-anydpi-v26/`
- Adaptive icons for Android 8.0+ (API 26+)
- Contains XML files that define adaptive icon layers

## Required Icon Files

For each mipmap density, you should create:

1. **ic_launcher.png** - Standard launcher icon
2. **ic_launcher_round.png** - Round launcher icon
3. **ic_launcher_foreground.xml** - Adaptive icon foreground (API 26+)
4. **ic_launcher_background.xml** - Adaptive icon background (API 26+)

## Logo Guidelines

### App Logo
The JaBook app logo should follow these guidelines:
- **Primary Colors**: Use the app's primary color scheme
- **Style**: Clean, modern, and recognizable at small sizes
- **Format**: PNG with transparency for launcher icons
- **Vector Format**: SVG for scalable assets (convert to XML vector drawables)

### Icon Sizes
Create icons in the following sizes:

| Density | Size (px) | Scale Factor |
|---------|-----------|--------------|
| mdpi    | 48x48     | 1.0x         |
| hdpi    | 72x72     | 1.5x         |
| xhdpi   | 96x96     | 2.0x         |
| xxhdpi  | 144x144   | 3.0x         |
| xxxhdpi | 192x192   | 4.0x         |

### Design Tips
1. **Keep it simple**: Complex details may not be visible at small sizes
2. **Use proper padding**: Leave some space around the icon
3. **Test on backgrounds**: Ensure the icon looks good on various backgrounds
4. **Maintain consistency**: Keep the same visual style across all densities

## File Naming Convention

### Launcher Icons
- `ic_launcher.png` - Standard launcher icon
- `ic_launcher_round.png` - Round launcher icon
- `ic_launcher_foreground.xml` - Adaptive icon foreground
- `ic_launcher_background.xml` - Adaptive icon background

### UI Icons
- `ic_[action]_[size].xml` - Vector drawable icons
  - Example: `ic_search_24.xml`, `ic_settings_24.xml`
- `bg_[element]_[style].xml` - Background resources
  - Example: `bg_button_rounded.xml`, `bg_card_shadow.xml`

### Illustrations
- `ill_[scene]_[style].xml` - Vector illustrations
  - Example: `ill_empty_library.xml`, `ill_no_connection.xml`

## Asset Generation Tools

### Recommended Tools
1. **Android Studio Asset Studio**
   - Built-in tool for generating launcher icons
   - Path: Right-click res folder → New → Image Asset

2. **Figma**
   - Design icons at xxxhdpi size (192x192px)
   - Export in multiple densities

3. **Sketch**
   - Use Sketch plugins for Android asset generation
   - Export in multiple formats

### Command Line Tools
```bash
# Using Android SDK's aapt to generate mipmap resources
aapt sng -i source_icon.png -o mipmap-xxxhdpi/ic_launcher.png
aapt sng -i source_icon.png -o mipmap-xxhdpi/ic_launcher.png
aapt sng -i source_icon.png -o mipmap-xhdpi/ic_launcher.png
aapt sng -i source_icon.png -o mipmap-hdpi/ic_launcher.png
aapt sng -i source_icon.png -o mipmap-mdpi/ic_launcher.png
```

## Adaptive Icons (Android 8.0+)

For Android 8.0 (API 26) and above, use adaptive icons:

### Structure
```xml
<!-- ic_launcher.xml (mipmap-anydpi-v26) -->
<?xml version="1.0" encoding="utf-8"?>
<adaptive-icon xmlns:android="http://schemas.android.com/apk/res/android">
    <background android:drawable="@drawable/ic_launcher_background"/>
    <foreground android:drawable="@drawable/ic_launcher_foreground"/>
</adaptive-icon>
```

### Guidelines
1. **Foreground**: Should be 108x108dp
2. **Background**: Should be 108x108dp
3. **Safe zone**: Keep important content within 72x72dp center area
4. **Masking**: The system will apply a circular mask

## Testing Icons

### Visual Testing
1. Test icons on different background colors
2. Verify visibility in different lighting conditions
3. Check appearance on various device sizes
4. Test with launcher icon packs

### Automated Testing
```kotlin
// Example icon visibility test
@Test
fun testLauncherIconVisibility() {
    val icon = ContextCompat.getDrawable(context, R.mipmap.ic_launcher)
    assertNotNull(icon)
    assertTrue(icon is BitmapDrawable)
    
    val bitmap = (icon as BitmapDrawable).bitmap
    assertFalse(bitmap.width == 0)
    assertFalse(bitmap.height == 0)
}
```

## Maintenance

### Version Control
- Add generated icons to version control
- Include source files (SVG, Sketch, Figma) in documentation
- Document any custom tools or scripts used

### Updates
- When updating icons, update all densities simultaneously
- Test the new icons across all target API levels
- Consider backward compatibility for older Android versions

## References

- [Android App Icons Documentation](https://developer.android.com/guide/practices/ui_guidelines/icon_design_app)
- [Adaptive Icons Documentation](https://developer.android.com/guide/practices/ui_guidelines/icon_design_adaptive)
- [Material Design Icons](https://material.io/resources/icons/)

---
*Last updated: 2025-08-22*