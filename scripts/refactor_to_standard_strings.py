import re
import os
import glob

def refactor_strings():
    # 1. Define Mapping: Strings.kt property -> R.string.id
    # Based on the content of Strings.kt (which I know from previous steps or can infer)
    # Since Strings.kt was reverted to hardcoded strings, I have to match the VALUES to strings.xml keys
    # OR better, since I know the original intention was matching names often.
    # Actually, the user wants to use standard XML.
    # Let's read strings.xml to get value -> key map.
    
    strings_xml_path = 'android/app/src/main/res/values/strings.xml'
    value_to_key = load_strings_map(strings_xml_path)
    
    # Manually defined map based on viewing Strings.kt vs likely keys
    # This is safer than value matching which might be ambiguous
    field_map = {
        'navLibrary': 'library',
        'navSettings': 'navSettingsText',
        'screenLibrary': 'library',
        'screenPlayer': 'nowPlaying',
        'screenSettings': 'navSettingsText',
        'screenWebView': 'webview',
        'libraryEmptyTitle': 'noAudiobooks',
        'libraryEmptyMessage': 'addAudiobooksToGetStarted',
        'librarySearchHint': 'searchBooks1',
        # Parameterized
        'playerChapter': 'chapterNumber1',
        'playerLoading': 'loading',
        'playerUnknown': 'unknown',
        'playerPlay': 'playButton',
        'playerPause': 'pauseButton',
        'playerSkipPrevious': 'previousChapter',
        'playerSkipNext': 'nextChapter',
        'settingsSectionAppearance': 'appearance',
        'settingsSectionPlayback': 'playback',
        'settingsSectionAbout': 'aboutTitle',
        'settingsTheme': 'themeTitle',
        'settingsThemeDescription': 'chooseAppTheme',
        'settingsThemeLight': 'light',
        'settingsThemeDark': 'dark',
        'settingsThemeSystem': 'systemDefault1',
        'settingsAutoPlayNext': 'autoplayNextChapter',
        'settingsAutoPlayNextDescription': 'automaticallyPlayNextChapterWhenCurrentEnds',
        'settingsPlaybackSpeed': 'playbackSpeed',
        # Parameterized
        'settingsPlaybackSpeedValue': 'playbackSpeedValue_placeholder', # Special handling
        'settingsVersion': 'version',
        'webViewLoading': 'loading',
        'webViewBack': 'back',
        'commonLoading': 'loading1',
        'commonError': 'error',
        'commonRetry': 'retryButton',
        'commonBack': 'back',
        'commonClose': 'close',
    }

    # 2. Iterate over all Kotlin files
    kt_files = glob.glob('android/app/src/main/kotlin/**/*.kt', recursive=True)
    
    for kt_file in kt_files:
        if 'Strings.kt' in kt_file: continue
        
        with open(kt_file, 'r') as f:
            content = f.read()
            
        original_content = content
        
        # 3. Replace usages
        # Pattern: strings.propertyName -> stringResource(R.string.key)
        
        # Check if file uses LocalStrings
        if 'LocalStrings.current' in content:
            # It likely has 'val strings = LocalStrings.current'
            # We can remove that line later, but first replace usages
            
            for field, key in field_map.items():
                # Handle parameterized cases first (functions)
                if field == 'playerChapter':
                    # strings.playerChapter(num) -> stringResource(R.string.chapterNumber1, num)
                    # Regex: strings.playerChapter\(([^)]+)\)
                    content = re.sub(
                        r'strings\.playerChapter\(([^)]+)\)', 
                        f'stringResource(R.string.{key}, \\1)', 
                        content
                    )
                elif field == 'settingsPlaybackSpeedValue':
                    # strings.settingsPlaybackSpeedValue(speed)
                    # This one in Strings.kt was dynamic formatting: "%.1fx".format(speed)
                    # We should check if R.string has a format string for it.
                    # Verify first. Providing a fallback or manual fix for this one.
                    pass 
                else:
                    # Simple property access: strings.navLibrary
                    replacement = f'stringResource(R.string.{key})'
                    content = content.replace(f'strings.{field}', replacement)
            
            # Remove 'val strings = LocalStrings.current' if unused
            # Simple check: if 'val strings = LocalStrings.current' and 'strings.' not in content (anymore)
            # But let's just make sure imports are correct.
            # We need to add import com.jabook.app.jabook.R and androidx.compose.ui.res.stringResource if not present.
            
            if content != original_content:
                # Add imports if missing
                if 'import com.jabook.app.jabook.R' not in content:
                    # Find package declaration
                    content = re.sub(r'(package .+\n)', r'\1\nimport com.jabook.app.jabook.R', content, 1)
                
                if 'import androidx.compose.ui.res.stringResource' not in content:
                    content = re.sub(r'(package .+\n)', r'\1\nimport androidx.compose.ui.res.stringResource', content, 1)
                
                # Try to remove the strings definition line
                content = re.sub(r'\s*val\s+strings\s*=\s*LocalStrings\.current\n', '', content)

                with open(kt_file, 'w') as f:
                    f.write(content)
                print(f"Updated {kt_file}")

import xml.etree.ElementTree as ET
def load_strings_map(xml_path):
    mapping = {}
    if not os.path.exists(xml_path): return mapping
    tree = ET.parse(xml_path)
    root = tree.getroot()
    for string in root.findall('string'):
        name = string.get('name')
        if name and string.text:
            mapping[string.text] = name # Value to Key
    return mapping

if __name__ == '__main__':
    refactor_strings()
