#!/usr/bin/env python3
"""
Script to automatically fix Explicit API mode violations in Kotlin files.

This script:
1. Collects compilation errors
2. Groups them by file
3. Automatically fixes violations by adding 'public' modifiers and return types
"""

import os
import re
import subprocess
import sys
from collections import defaultdict
from pathlib import Path

PROJECT_ROOT = Path(__file__).parent.parent
ANDROID_DIR = PROJECT_ROOT / "android"
SRC_DIR = ANDROID_DIR / "app" / "src" / "main" / "kotlin"


def collect_violations():
    """Collect all Explicit API violations from compilation."""
    print("🔍 Collecting Explicit API violations...")
    
    os.chdir(ANDROID_DIR)
    
    try:
        result = subprocess.run(
            ["./gradlew", ":app:compileBetaDebugKotlin", "--no-daemon"],
            capture_output=True,
            text=True,
            timeout=600  # Increased timeout to 10 minutes
        )
    except subprocess.TimeoutExpired:
        print("⚠️  Compilation timed out (10 minutes)")
        return {}
    
    violations = defaultdict(list)
    
    for line in result.stderr.split('\n'):
        if "Visibility must be specified" in line or "Return type must be specified" in line:
            # Parse: e: file:///full/path/to/file.kt:123:45 Error message
            # Extract relative path from src/main/kotlin
            match = re.search(r'file://.*?/src/main/kotlin/([^:]+):(\d+):', line)
            if match:
                file_path = match.group(1)
                line_num = int(match.group(2))
                violations[file_path].append(line_num)
    
    print(f"📝 Found violations in {len(violations)} files")
    return violations


def fix_file(file_path: Path):
    """Fix Explicit API violations in a single file."""
    if not file_path.exists():
        print(f"⚠️  File not found: {file_path}")
        return False
    
    # Create backup
    backup_path = file_path.with_suffix(file_path.suffix + '.bak')
    file_path.rename(backup_path)
    
    try:
        with open(backup_path, 'r', encoding='utf-8') as f:
            lines = f.readlines()
        
        fixed = False
        new_lines = []
        
        for i, line in enumerate(lines):
            original = line
            new_line = line
            
            # Fix 1: Add public to top-level functions
            if re.match(r'^\s*fun\s+\w+', line):
                if not re.search(r'\b(public|private|internal|protected)\s+fun', line):
                    new_line = re.sub(r'^(\s*)fun', r'\1public fun', new_line)
                    fixed = True
            
            # Fix 2: Add public to top-level classes/objects/interfaces
            if re.match(r'^\s*(class|object|interface|enum class|data class|sealed class|sealed interface)\s+\w+', line):
                if not re.search(r'\b(public|private|internal|protected)\s+(class|object|interface|enum class|data class|sealed class|sealed interface)', line):
                    new_line = re.sub(
                        r'^(\s*)(class|object|interface|enum class|data class|sealed class|sealed interface)',
                        r'\1public \2',
                        new_line
                    )
                    fixed = True
            
            # Fix 3: Add public to companion objects
            if re.match(r'^\s*companion\s+object', line):
                if not re.search(r'\b(public|private|internal|protected)\s+companion', line):
                    new_line = re.sub(r'^(\s*)companion', r'\1public companion', new_line)
                    fixed = True
            
            # Fix 4: Add public to const val and return type
            if re.match(r'^\s*const\s+val', line):
                if not re.search(r'\b(public|private|internal|protected)\s+const', line):
                    new_line = re.sub(r'^(\s*)const', r'\1public const', new_line)
                    fixed = True
                # Add return type if missing (e.g., const val NAME = "value" -> const val NAME: String = "value")
                if ': ' not in line and '=' in line:
                    # Extract name and value
                    match = re.match(r'^(\s*public\s+const\s+val\s+)(\w+)(\s*=\s*)(.+)$', new_line)
                    if match:
                        indent, name, equals, value = match.groups()
                        # Try to infer type from value
                        value_stripped = value.strip().rstrip('\n')
                        if value_stripped.startswith('"') or value_stripped.startswith("'"):
                            type_name = "String"
                        elif value_stripped.replace('.', '').replace('-', '').isdigit():
                            if 'L' in value_stripped.upper():
                                type_name = "Long"
                            elif '.' in value_stripped:
                                type_name = "Float" if 'f' in value_stripped.lower() else "Double"
                            else:
                                type_name = "Int"
                        elif value_stripped.lower() in ('true', 'false'):
                            type_name = "Boolean"
                        else:
                            type_name = None
                        
                        if type_name:
                            new_line = f"{indent}{name}: {type_name}{equals}{value}"
                            fixed = True
            
            # Fix 5: Add public to var/val properties inside classes (indented, but not private/internal)
            if re.match(r'^\s+(var|val)\s+\w+', line):
                if not re.search(r'\b(public|private|internal|protected)\s+(var|val)', line):
                    # Only add public if it's not already private/internal
                    if not re.search(r'\b(private|internal)\s+(var|val)', line):
                        # Check if it's a class member (indented) or top-level
                        indent_match = re.match(r'^(\s+)(var|val)', line)
                        if indent_match:
                            indent = indent_match.group(1)
                            new_line = re.sub(r'^(\s+)(var|val)', r'\1public \2', new_line)
                            fixed = True
            
            # Fix 6: Add public to functions inside classes (indented, but not private/internal)
            if re.match(r'^\s+fun\s+\w+', line):
                if not re.search(r'\b(public|private|internal|protected)\s+fun', line):
                    # Only add public if it's not already private/internal
                    if not re.search(r'\b(private|internal)\s+fun', line):
                        new_line = re.sub(r'^(\s+)fun', r'\1public fun', new_line)
                        fixed = True
            
            # Fix 7: Add return type : Unit to functions ending with {
            if re.match(r'^(\s*(public\s+)?fun\s+\w+.*\))\s*{\s*$', line.rstrip()):
                if ': Unit' not in line and ': ' not in line:
                    new_line = re.sub(r'(\s*)\{\s*$', r'\1: Unit {', line.rstrip()) + '\n'
                    fixed = True
            
            # Fix 8: Add return type to expression body functions (simple cases)
            if re.match(r'^(\s*(public\s+)?fun\s+\w+.*\))\s*=\s*run\s*{', line):
                if ': Unit' not in line and ': ' not in line:
                    new_line = re.sub(r'\)\s*=\s*run\s*{', r') : Unit = run {', new_line)
                    fixed = True
            
            # Fix 9: Add return type to simple expression functions
            # Pattern: fun name(...) = expression (where expression is simple)
            if re.match(r'^(\s*(public\s+)?fun\s+\w+.*\))\s*=\s*[^=]+$', line.rstrip()):
                if ': ' not in line and not re.search(r':\s*(Boolean|Int|Long|String|Float|Double|Unit|List|Map|Set)', line):
                    # Try to infer type from expression
                    expr = line.split('=')[1].strip() if '=' in line else ''
                    if expr.startswith('run') or expr.startswith('if') or expr.startswith('when'):
                        new_line = re.sub(r'\)\s*=\s*', r') : Unit = ', new_line)
                        fixed = True
            
            # Fix 10: Add return type to property getters
            # Pattern: val/var name: Type get() = ...
            if re.match(r'^\s+(val|var)\s+\w+.*get\(\)\s*=', line):
                if ': ' not in line.split('get()')[0]:
                    # Try to infer from context or add Unit
                    new_line = re.sub(r'(get\(\)\s*=\s*)', r'get(): Unit = ', new_line)
                    fixed = True
            
            # Fix 11: Add return type to extension functions
            # Pattern: fun Type.name(...) = ...
            if re.match(r'^\s*(public\s+)?fun\s+\w+\.\w+', line):
                if not re.search(r'\b(public|private|internal|protected)\s+fun', line):
                    new_line = re.sub(r'^(\s*)fun', r'\1public fun', new_line)
                    fixed = True
                # Add return type if missing
                if ': ' not in line and '=' in line:
                    expr = line.split('=')[1].strip() if '=' in line else ''
                    if expr.startswith('run') or expr.startswith('if') or expr.startswith('when') or expr.startswith('{'):
                        new_line = re.sub(r'\)\s*=\s*', r') : Unit = ', new_line)
                        fixed = True
            
            new_lines.append(new_line)
        
        if fixed:
            with open(file_path, 'w', encoding='utf-8') as f:
                f.writelines(new_lines)
            backup_path.unlink()  # Remove backup if successful
            return True
        else:
            # Restore backup if no changes
            backup_path.rename(file_path)
            return False
    
    except Exception as e:
        print(f"❌ Error fixing {file_path}: {e}")
        # Restore backup on error
        if backup_path.exists():
            backup_path.rename(file_path)
        return False


def main():
    """Main function."""
    violations = collect_violations()
    
    if not violations:
        print("✨ No violations found!")
        return
    
    print(f"\n🔧 Processing {len(violations)} files...\n")
    
    total_fixed = 0
    skipped = 0
    for file_path_str, line_nums in violations.items():
        file_path = SRC_DIR / file_path_str
        try:
            if fix_file(file_path):
                print(f"  ✅ Fixed: {file_path_str} ({len(line_nums)} violations)")
                total_fixed += 1
            else:
                print(f"  ⚠️  Skipped (may need manual fix): {file_path_str} ({len(line_nums)} violations)")
                skipped += 1
        except Exception as e:
            print(f"  ❌ Error processing {file_path_str}: {e}")
            skipped += 1
    
    print(f"\n✨ Fixed {total_fixed} files")
    if skipped > 0:
        print(f"⚠️  Skipped {skipped} files (may need manual fixes)")
    
    # Check remaining errors
    print("\n🧪 Checking remaining violations...")
    remaining = collect_violations()
    remaining_count = sum(len(lines) for lines in remaining.values())
    
    print(f"📊 Remaining violations: {remaining_count} in {len(remaining)} files")
    
    if remaining_count == 0:
        print("🎉 All Explicit API violations fixed!")
    else:
        print(f"⚠️  {remaining_count} violations remain. Run the script again or fix manually.")
        if remaining_count < 100:
            print("\n📋 Files with remaining violations:")
            for file_path_str, line_nums in sorted(remaining.items())[:20]:
                print(f"  - {file_path_str}: {len(line_nums)} violations")


if __name__ == "__main__":
    main()
