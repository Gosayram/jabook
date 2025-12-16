#!/usr/bin/env python3
"""
Automated String Resource Migration Script
Converts hardcoded strings in Kotlin Compose files to use stringResource()
"""

import re
import os
import sys
import json
from pathlib import Path
from typing import Dict, List, Tuple, Set, Optional
import xml.etree.ElementTree as ET

try:
    import requests
    HAS_REQUESTS = True
except ImportError:
    HAS_REQUESTS = False
    print("⚠️  Warning: 'requests' module not found. Translation will be skipped.")
    print("   Install with: pip3 install requests")

class StringResourceMigrator:
    def __init__(self, project_root: str, enable_translation: bool = True):
        self.project_root = Path(project_root)
        self.strings_xml_en = self.project_root / "android/app/src/main/res/values/strings.xml"
        self.strings_xml_ru = self.project_root / "android/app/src/main/res/values-ru/strings.xml"
        self.existing_keys: Set[str] = set()
        self.existing_values: Dict[str, str] = {}  # value -> key mapping
        self.new_strings: Dict[str, str] = {}
        self.enable_translation = enable_translation and HAS_REQUESTS
        
    def load_existing_strings(self):
        """Load existing string keys and values from strings.xml"""
        if self.strings_xml_en.exists():
            tree = ET.parse(self.strings_xml_en)
            root = tree.getroot()
            for string_elem in root.findall('string'):
                key = string_elem.get('name')
                value = string_elem.text
                if key:
                    self.existing_keys.add(key)
                    if value:
                        # Map value to key for lookup
                        self.existing_values[value] = key
        print(f"📋 Loaded {len(self.existing_keys)} existing string keys")
    
    def translate_to_russian(self, text: str, max_retries: int = 3) -> Optional[str]:
        """Translate English text to Russian using Google Translate API with retry"""
        if not self.enable_translation:
            return None
        
        for attempt in range(max_retries):
            try:
                # Use Google Translate API (unofficial endpoint, free)
                url = "https://translate.googleapis.com/translate_a/single"
                params = {
                    'client': 'gtx',
                    'sl': 'en',  # source language
                    'tl': 'ru',  # target language  
                    'dt': 't',   # return translation
                    'q': text
                }
                
                # Increase timeout for better reliability
                timeout = 10 + (attempt * 5)  # 10s, 15s, 20s
                response = requests.get(url, params=params, timeout=timeout)
                if response.status_code == 200:
                    result = response.json()
                    # Extract translated text from response
                    if result and len(result) > 0 and len(result[0]) > 0:
                        translated = ''.join([item[0] for item in result[0] if item[0]])
                        if translated and translated.strip():
                            return translated
            except Exception as e:
                if attempt < max_retries - 1:
                    print(f"   ⚠️  Translation attempt {attempt + 1} failed, retrying...")
                else:
                    print(f"   ⚠️  Translation failed after {max_retries} attempts: {e}")
        
        return None
    
    def string_to_key(self, text: str) -> str:
        """Convert a string to a camelCase resource key"""
        # Remove special characters, keep alphanumeric and spaces
        text = re.sub(r'[^\w\s]', '', text)
        # Split into words
        words = text.split()
        if not words:
            return "unnamed"
        # First word lowercase, rest capitalized
        key = words[0].lower() + ''.join(w.capitalize() for w in words[1:])
        # Truncate if too long
        if len(key) > 50:
            key = key[:50]
        return key
    
    def find_hardcoded_strings(self, kotlin_file: Path) -> List[Tuple[str, int]]:
        """Find all hardcoded strings in a Kotlin file - comprehensive UI string detection"""
        strings = []
        
        # Skip non-UI files - be very selective about what we process
        file_str = str(kotlin_file)
        skip_patterns = [
            '/data/', '/domain/', '/di/', '/util/', 
            'ViewModel.kt', 'Repository.kt', 'Dao.kt', 'Entity.kt', 'Module.kt',
            '/audio/', '/download/', '/infrastructure/', '/torrent/', '/migration/',
            '/sync/', '/worker/', '/service/', '/receiver/', '/broadcast/',
            'Manager.kt', 'Helper.kt', 'Provider.kt', 'Factory.kt',
            'Mapper.kt', 'UseCase.kt', 'Processor.kt', 'Handler.kt',
            'Listener.kt', 'Observer.kt', 'Callback.kt'
        ]
        
        if any(skip in file_str for skip in skip_patterns):
            return strings
            
        with open(kotlin_file, 'r', encoding='utf-8') as f:
            for line_num, line in enumerate(f, 1):
                # Skip comments
                if line.strip().startswith('//'):
                    continue
                
                # Skip imports, package declarations
                if line.strip().startswith(('import ', 'package ')):
                    continue
                
                # Skip logging statements - Log.d, Log.i, Log.e, println, logger, etc.
                if re.search(r'(Log\.[diewtv]|println|logger\.|Timber\.)', line):
                    continue
                
                # Find ALL string literals that look like UI text (not technical strings)
                # Match: "any text" but exclude technical patterns
                all_strings = re.findall(r'"([^"]+)"', line)
                
                for match in all_strings:
                    # Skip if it's a technical string (not UI text)
                    if self._is_technical_string(match):
                        continue
                    
                    # Skip template strings
                    if match.startswith('$'):
                        continue
                        
                    # This looks like UI text
                    strings.append((match, line_num))
        
        return strings
    
    def _is_technical_string(self, text: str) -> bool:
        """Determine if a string is technical (not UI text)"""
        # File paths
        if '/' in text or '\\\\' in text:
            return True
        
        # URLs and domains
        if text.startswith(('http://', 'https://', 'www.')) or '.com' in text or '.net' in text or '.org' in text:
            return True
            
        # MIME types
        if text.startswith('application/') or text.startswith('text/') or text.startswith('image/'):
            return True
        
        # Intent actions and categories
        if text.startswith('android.') or text.startswith('com.') or text.startswith('java.'):
            return True
            
        # Single letters or very short technical strings
        if len(text) <= 1:
            return True
            
        # Regex patterns
        if text.count('[') > 0 and text.count(']') > 0 and text.count('(') > 0:
            return True
        
        # Debug/Log messages - typically start with verbs in present continuous or technical verbs
        debug_verbs = [
            'Using', 'Loading', 'Saving', 'Validating', 'Waiting', 'Checking', 
            'Creating', 'Destroying', 'Initializing', 'Skipping', 'Found',
            'Processing', 'Applying', 'Clearing', 'Releasing', 'Binding',
            'Attempting', 'Pausing', 'Resuming', 'Stopping', 'Starting',
            'Seeking', 'Buffering', 'Preparing', 'Restoring', 'Requesting',
            'Received', 'Sending', 'Publishing', 'Updating track', 'Navigating to',
            'Setting', 'Getting', 'Fetching', 'Syncing', 'Registering',
            'Unregistering', 'Broadcasting', 'Observing', 'Cancelling'
        ]
        
        for verb in debug_verbs:
            if text.startswith(verb + ' '):
                return True
        
        # Strings with many variables (likely debug logs)
        if text.count('$') >= 2:  # More than 2 variables
            return True
        
        # Technical patterns in text
        technical_patterns = [
            'MediaSession', 'PendingIntent', 'Notification', 'ViewModel',
            'Repository', 'Dao', 'Entity', 'Processor', 'Manager',
            'Handler', 'Callback', 'Listener', 'Observer', 'Worker',
            'mediaItem', 'currentIndex', 'trackIndex', 'positionMs',
            'attempt=', 'state=', 'count=', 'index=', 'position=',
            '${', 'total=', 'expected=', 'current='
        ]
        
        for pattern in technical_patterns:
            if pattern in text:
                return True
        
        # All lowercase technical identifiers (method names, variables)
        if text.islower() and len(text) > 3 and ' ' not in text:
            return True
        
        # Looks good - likely UI text
        return False
    
    def migrate_file(self, kotlin_file: Path, dry_run: bool = False) -> Tuple[int, int]:
        """
        Migrate a single Kotlin file
        Returns: (strings_replaced, strings_added)
        """
        # Handle path display
        try:
            display_path = kotlin_file.relative_to(self.project_root)
        except ValueError:
            display_path = kotlin_file
        
        print(f"\n📝 Processing: {display_path}")
        
        # Find hardcoded strings
        hardcoded = self.find_hardcoded_strings(kotlin_file)
        if not hardcoded:
            print(f"   ✅ No hardcoded strings found")
            return 0, 0
        
        print(f"   Found {len(hardcoded)} hardcoded string(s)")
        
        # Read file content
        with open(kotlin_file, 'r', encoding='utf-8') as f:
            content = f.read()
            lines = content.split('\n')
        
        strings_replaced = 0
        strings_added = 0
        
        # Check if already has imports
        has_stringresource_import = 'import androidx.compose.ui.res.stringResource' in content
        has_r_import = 'import com.jabook.app.jabook.R' in content
        
        # Process each string
        replacements = {}
        for text, line_num in hardcoded:
            # First check if this exact string value already exists
            if text in self.existing_values:
                key = self.existing_values[text]
                print(f"   ♻️  Reusing existing: '{key}' for '{text[:50]}...'")
                replacements[text] = key
                continue
            
            # Check if already processed in this run
            if text in replacements:
                continue
            
            # Generate new key
            key = self.string_to_key(text)
            base_key = key
            counter = 1
            
            # Ensure unique key
            while key in self.existing_keys or key in self.new_strings:
                key = f"{base_key}{counter}"
                counter += 1
            
            # Add to new strings and mappings
            self.new_strings[key] = text
            self.existing_keys.add(key)
            self.existing_values[text] = key
            strings_added += 1
            print(f"   + New string: '{key}' = '{text[:50]}...'")
            
            # Store replacement
            replacements[text] = key
        
        if dry_run:
            return len(hardcoded), strings_added
        
        # Apply replacements
        for text, key in replacements.items():
            escaped_text = re.escape(text)
            
            # Universal replacement: replace "string" with stringResource(R.string.key)
            # This handles all contexts: Text("..."), title = "...", subtitle = "...", etc.
            pattern = rf'"{escaped_text}"'
            replacement = f'stringResource(R.string.{key})'
            content = re.sub(pattern, replacement, content)
            
            strings_replaced += 1
        
        # Add imports if needed
        if strings_replaced > 0:
            lines = content.split('\n')
            import_index = -1
            
            # Find last import statement
            for i, line in enumerate(lines):
                if line.startswith('import '):
                    import_index = i
            
            if import_index >= 0:
                imports_to_add = []
                if not has_stringresource_import:
                    imports_to_add.append('import androidx.compose.ui.res.stringResource')
                if not has_r_import:
                    imports_to_add.append('import com.jabook.app.jabook.R')
                
                # Insert imports after last import
                for imp in reversed(imports_to_add):
                    lines.insert(import_index + 1, imp)
                
                content = '\n'.join(lines)
        
        # Write back
        with open(kotlin_file, 'w', encoding='utf-8') as f:
            f.write(content)
        
        print(f"   ✅ Replaced {strings_replaced} string(s)")
        return strings_replaced, strings_added
    
    def add_strings_to_xml(self, strings_dict: Dict[str, str], xml_file: Path, is_russian: bool = False):
        """Add new strings to strings.xml while preserving comments"""
        if not strings_dict:
            return
        
        print(f"\n📦 Adding {len(strings_dict)} new strings to {xml_file.name}")
        
        # Read the file as text to preserve comments
        with open(xml_file, 'r', encoding='utf-8') as f:
            content = f.read()
        
        # Generate new string entries with comments
        new_entries = []
        for key, english_value in sorted(strings_dict.items()):
            # Escape XML special characters
            def escape_xml(text):
                return (text
                    .replace('&', '&amp;')
                    .replace('<', '&lt;')
                    .replace('>', '&gt;')
                    .replace('"', '&quot;')
                    .replace("'", '&apos;'))
            
            if is_russian:
                # Translate to Russian
                russian_value = self.translate_to_russian(english_value)
                if russian_value:
                    text_value = escape_xml(russian_value)
                    # For Russian comment, use the translated value
                    comment = f"    <!-- {russian_value} -->"
                    print(f"   ✅ Translated '{key}': '{english_value}' → '{russian_value}'")
                else:
                    # Fallback: use English text  with [NEEDS TRANSLATION] comment
                    text_value = escape_xml(english_value)
                    comment = f"    <!-- [NEEDS TRANSLATION] {english_value} -->"
                    print(f"   ⚠️  Could not translate '{key}', using English text")
            else:
                # English version - use the English value as comment
                text_value = escape_xml(english_value)
                comment = f"    <!-- {english_value} -->"
            
            # Add comment and string entry
            new_entries.append(comment)
            new_entries.append(f'    <string name="{key}">{text_value}</string>')
        
        # Find the closing </resources> tag and insert before it
        closing_tag = '</resources>'
        if closing_tag in content:
            # Insert new entries before closing tag
            insertion_point = content.rfind(closing_tag)
            new_content = (
                content[:insertion_point] +
                '\n'.join(new_entries) + '\n' +
                content[insertion_point:]
            )
            
            # Write back
            with open(xml_file, 'w', encoding='utf-8') as f:
                f.write(new_content)
            
            print(f"   ✅ Added {len(strings_dict)} string(s) with comments")
        else:
            print(f"   ⚠️  Warning: Could not find closing </resources> tag in {xml_file.name}")




    
    def migrate_directory(self, directory: Path, dry_run: bool = False):
        """Migrate all Kotlin files in a directory"""
        kotlin_files = list(directory.rglob('*.kt'))
        
        # Handle path display
        try:
            display_path = directory.relative_to(self.project_root)
        except ValueError:
            display_path = directory
        
        print(f"\n🔍 Found {len(kotlin_files)} Kotlin files in {display_path}")
        
        total_replaced = 0
        total_added = 0
        files_modified = 0
        
        for kt_file in kotlin_files:
            replaced, added = self.migrate_file(kt_file, dry_run)
            if replaced > 0:
                files_modified += 1
                total_replaced += replaced
                total_added += added
        
        print(f"\n📊 Summary:")
        print(f"   Files modified: {files_modified}/{len(kotlin_files)}")
        print(f"   Strings replaced: {total_replaced}")
        print(f"   New strings added: {total_added}")
        
        if not dry_run and self.new_strings:
            self.add_strings_to_xml(self.new_strings, self.strings_xml_en)
            self.add_strings_to_xml(self.new_strings, self.strings_xml_ru, is_russian=True)

def main():
    if len(sys.argv) < 2:
        print("Usage: python migrate_strings.py <path_to_kotlin_files> [--dry-run]")
        print("Example: python migrate_strings.py android/app/src/main/kotlin/com/jabook/app/jabook/compose/feature/settings")
        sys.exit(1)
    
    project_root = Path.cwd()
    target_path = Path(sys.argv[1])
    
    # Convert to absolute path if relative
    if not target_path.is_absolute():
        target_path = project_root / target_path
    
    dry_run = '--dry-run' in sys.argv
    
    if dry_run:
        print("🔍 DRY RUN MODE - No files will be modified\n")
    
    migrator = StringResourceMigrator(str(project_root))
    migrator.load_existing_strings()
    
    if target_path.is_file():
        replaced, added = migrator.migrate_file(target_path, dry_run)
        if not dry_run and migrator.new_strings:
            migrator.add_strings_to_xml(migrator.new_strings, migrator.strings_xml_en)
            migrator.add_strings_to_xml(migrator.new_strings, migrator.strings_xml_ru, is_russian=True)
    elif target_path.is_dir():
        migrator.migrate_directory(target_path, dry_run)
    else:
        print(f"❌ Path not found: {target_path}")
        sys.exit(1)
    
    print("\n✅ Migration complete!")
    if not dry_run:
        print("⚠️  Don't forget to:")
        print("   1. Checking translation quality in strings.xml (locale: values-ru)")
        print("   2. Run 'make clean-l10n' to remove any duplicates")
        print("   3. Test the app to verify string replacements")

if __name__ == '__main__':
    main()
