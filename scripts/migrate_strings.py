#!/usr/bin/env python3
"""
Automated String Resource Migration Script
Converts hardcoded strings in Kotlin Compose files to use stringResource()
"""

import re
import os
import sys
from pathlib import Path
from typing import Dict, List, Tuple, Set
import xml.etree.ElementTree as ET

class StringResourceMigrator:
    def __init__(self, project_root: str):
        self.project_root = Path(project_root)
        self.strings_xml_en = self.project_root / "android/app/src/main/res/values/strings.xml"
        self.strings_xml_ru = self.project_root / "android/app/src/main/res/values-ru/strings.xml"
        self.existing_keys: Set[str] = set()
        self.new_strings: Dict[str, str] = {}
        
    def load_existing_strings(self):
        """Load existing string keys from strings.xml"""
        if self.strings_xml_en.exists():
            tree = ET.parse(self.strings_xml_en)
            root = tree.getroot()
            for string_elem in root.findall('string'):
                key = string_elem.get('name')
                if key:
                    self.existing_keys.add(key)
        print(f"📋 Loaded {len(self.existing_keys)} existing string keys")
    
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
        """Find all hardcoded strings in a Kotlin file"""
        strings = []
        with open(kotlin_file, 'r', encoding='utf-8') as f:
            for line_num, line in enumerate(f, 1):
                # Skip comments
                if line.strip().startswith('//'):
                    continue
                
                # Find strings in Text() calls, labels, contentDescription, etc.
                # Pattern: Text("string") or label = { Text("string") }
                matches = re.findall(r'Text\s*\(\s*"([^"]+)"\s*\)', line)
                for match in matches:
                    if match and not match.startswith('$'):  # Skip template strings
                        strings.append((match, line_num))
                
                # Find contentDescription strings
                matches = re.findall(r'contentDescription\s*=\s*"([^"]+)"', line)
                for match in matches:
                    if match:
                        strings.append((match, line_num))
                
                # Find label strings
                matches = re.findall(r'label\s*=\s*\{\s*Text\s*\(\s*"([^"]+)"\s*\)', line)
                for match in matches:
                    if match:
                        strings.append((match, line_num))
        
        return strings
    
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
            # Generate key
            key = self.string_to_key(text)
            base_key = key
            counter = 1
            
            # Ensure unique key
            while key in self.existing_keys or key in self.new_strings:
                key = f"{base_key}{counter}"
                counter += 1
            
            # Add to new strings
            if key not in self.existing_keys:
                self.new_strings[key] = text
                self.existing_keys.add(key)
                strings_added += 1
                print(f"   + New string: '{key}' = '{text[:50]}...'")
            
            # Store replacement
            replacements[text] = key
        
        if dry_run:
            return len(hardcoded), strings_added
        
        # Apply replacements
        for text, key in replacements.items():
            # Replace Text("string") with Text(stringResource(R.string.key))
            pattern = rf'Text\s*\(\s*"{re.escape(text)}"\s*\)'
            replacement = f'Text(stringResource(R.string.{key}))'
            content = re.sub(pattern, replacement, content)
            
            # Replace contentDescription = "string"
            pattern = rf'contentDescription\s*=\s*"{re.escape(text)}"'
            replacement = f'contentDescription = stringResource(R.string.{key})'
            content = re.sub(pattern, replacement, content)
            
            # Replace label = { Text("string") }
            pattern = rf'label\s*=\s*\{{\s*Text\s*\(\s*"{re.escape(text)}"\s*\)\s*\}}'
            replacement = f'label = {{ Text(stringResource(R.string.{key})) }}'
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
        
        # Generate new string entries
        new_entries = []
        for key, value in sorted(strings_dict.items()):
            # For Russian, mark as needing translation
            text_value = value if not is_russian else f"[RU] {value}"
            # Escape XML special characters
            text_value = text_value.replace('&', '&amp;').replace('<', '&lt;').replace('>', '&gt;').replace('"', '&quot;').replace("'", '&apos;')
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
            
            print(f"   ✅ Added {len(strings_dict)} string(s) while preserving comments")
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
        print("   1. Translate new Russian strings (marked with [RU])")
        print("   2. Run 'make clean-l10n' to remove any duplicates")
        print("   3. Test the app to verify string replacements")

if __name__ == '__main__':
    main()
