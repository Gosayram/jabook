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
            
            # Pre-fix: Remove public from local variables inside function bodies
            # Check if we're inside a function body by looking for function declaration above
            is_inside_function_body = False
            brace_count = 0
            for j in range(max(0, i - 20), i):
                prev_line = lines[j]
                # Count braces to track nesting
                brace_count += prev_line.count('{') - prev_line.count('}')
                # Check if we found a function declaration
                if re.match(r'^\s*(public\s+)?(suspend\s+)?fun\s+\w+', prev_line.strip()):
                    # Found function, check if we're inside its body
                    if '{' in prev_line or any('{' in lines[k] for k in range(j + 1, min(len(lines), j + 5))):
                        is_inside_function_body = True
                        break
            
            # Remove public from local variables inside functions
            if is_inside_function_body and re.match(r'^\s+public\s+(var|val)\s+\w+', line):
                new_line = re.sub(r'^(\s+)public\s+(var|val)', r'\1\2', line)
                fixed = True
            
            # Also remove stray "public" at the end of lines (fix for broken formatting)
            if re.search(r'\bpublic\s*$', new_line.rstrip()):
                new_line = re.sub(r'\s+public\s*$', '', new_line.rstrip()) + '\n'
                fixed = True
            
            # Fix: Split multiple statements on the same line
            # Pattern: val x = y        return z
            if re.search(r'[=:]\s+[^=:]*\s{4,}[a-zA-Z]', new_line):
                # Split by 4+ spaces followed by a word (likely a new statement)
                parts = re.split(r'(\s{4,})(?=[a-zA-Z])', new_line)
                if len(parts) > 1:
                    indent = re.match(r'^(\s*)', new_line).group(1) if re.match(r'^\s*', new_line) else ''
                    new_statements = []
                    current_statement = parts[0]
                    for i in range(1, len(parts), 2):
                        if i + 1 < len(parts):
                            next_statement = parts[i + 1].strip()
                            if next_statement and not next_statement.startswith('//'):
                                new_statements.append(f"{indent}{current_statement.rstrip()}")
                                current_statement = next_statement
                            else:
                                current_statement += parts[i] + parts[i + 1]
                    if current_statement:
                        new_statements.append(f"{indent}{current_statement.rstrip()}")
                    if len(new_statements) > 1:
                        new_line = '\n'.join(new_statements) + '\n'
                        fixed = True
            
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
            
            # Fix 4: Split multiple const val declarations on the same line
            # Pattern: public const val NAME1 = "value1"        public const val NAME2 = "value2"
            if re.search(r'public\s+const\s+val.*public\s+const\s+val', line):
                # Split by multiple spaces (4+) followed by "public const val"
                indent = re.match(r'^(\s*)', line).group(1) if re.match(r'^\s*', line) else ''
                # Split by 4+ spaces before "public const val"
                parts = re.split(r'\s{4,}(?=public\s+const\s+val)', line)
                
                if len(parts) > 1:
                    new_const_lines = []
                    for part in parts:
                        part = part.strip()
                        if part.startswith('public const val'):
                            new_const_lines.append(f"{indent}{part}")
                        elif part and not part.startswith('public'):
                            # This might be closing brace or other content
                            if new_const_lines:
                                # Append to last line if it's just closing brace
                                if part.strip() == '}':
                                    new_const_lines[-1] += f" {part}"
                                else:
                                    new_const_lines.append(f"{indent}{part}")
                            else:
                                new_const_lines.append(f"{indent}{part}")
                    
                    if len(new_const_lines) > 1:
                        new_line = '\n'.join(new_const_lines)
                        if not new_line.endswith('\n'):
                            new_line += '\n'
                        fixed = True
            
            # Fix 4a: Add public to const val and return type (only if not already split)
            if re.match(r'^\s*(public\s+)?const\s+val', new_line) and 'public const val' not in new_line.split('\n')[0]:
                # Add public if missing
                if not re.search(r'\b(public|private|internal|protected)\s+const', new_line):
                    new_line = re.sub(r'^(\s*)const', r'\1public const', new_line)
                    fixed = True
                
                # Add return type if missing (e.g., const val NAME = "value" -> const val NAME: String = "value")
                # Handle both "const val" and "public const val"
                if ': ' not in new_line and '=' in new_line:
                    # Extract name and value - handle both with and without public
                    match = re.match(r'^(\s*(?:public\s+)?const\s+val\s+)(\w+)(\s*=\s*)(.+)$', new_line)
                    if match:
                        prefix, name, equals, value = match.groups()
                        # Try to infer type from value
                        value_stripped = value.strip().rstrip('\n').rstrip(')').rstrip('(')
                        
                        # Check for Long (ends with L)
                        if value_stripped.upper().endswith('L') or value_stripped.upper().endswith('L)'):
                            type_name = "Long"
                        # Check for String
                        elif value_stripped.startswith('"') or value_stripped.startswith("'"):
                            type_name = "String"
                        # Check for Boolean
                        elif value_stripped.lower() in ('true', 'false'):
                            type_name = "Boolean"
                        # Check for numeric types
                        elif re.match(r'^-?\d+\.?\d*', value_stripped):
                            if '.' in value_stripped:
                                type_name = "Float" if 'f' in value_stripped.lower() else "Double"
                            else:
                                type_name = "Int"
                        else:
                            type_name = None
                        
                        if type_name:
                            new_line = f"{prefix}{name}: {type_name}{equals}{value}"
                            fixed = True
            
            # Fix 5: Add public to var/val properties inside classes (indented, but not private/internal)
            # Also handle properties that already have public but need return type
            # BUT: Skip if it's inside a function body (check context)
            is_inside_function = False
            if i > 0:
                # Check previous lines to see if we're inside a function
                for j in range(max(0, i - 5), i):
                    prev_line = lines[j].strip()
                    if re.match(r'^(public\s+)?(suspend\s+)?fun\s+\w+', prev_line) and '{' in prev_line:
                        is_inside_function = True
                        break
                    if re.match(r'^(public\s+)?(suspend\s+)?fun\s+\w+', prev_line):
                        # Function declaration, check if next lines have {
                        for k in range(j + 1, min(len(lines), j + 3)):
                            if '{' in lines[k]:
                                is_inside_function = True
                                break
                        if is_inside_function:
                            break
            
            if re.match(r'^\s+(public\s+)?(var|val)\s+\w+', line) and not is_inside_function:
                # Remove public from local variables inside functions
                if is_inside_function and re.search(r'\bpublic\s+(var|val)', line):
                    new_line = re.sub(r'\bpublic\s+(var|val)', r'\1', new_line)
                    fixed = True
                # Add public if missing (only for class members, not local variables)
                elif not re.search(r'\b(public|private|internal|protected)\s+(var|val)', line):
                    # Only add public if it's not already private/internal
                    if not re.search(r'\b(private|internal)\s+(var|val)', line):
                        # Check if it's a class member (indented) or top-level
                        indent_match = re.match(r'^(\s+)(var|val)', line)
                        if indent_match:
                            indent = indent_match.group(1)
                            new_line = re.sub(r'^(\s+)(var|val)', r'\1public \2', new_line)
                            fixed = True
                
                # Add return type if missing for public properties
                if re.search(r'\bpublic\s+(var|val)', new_line) and ': ' not in new_line and '=' in new_line:
                    # Pattern: public var/val name = value
                    match = re.match(r'^(\s+public\s+(?:lateinit\s+)?(?:@\w+\s+)?(?:@\w+\([^)]+\)\s+)?)(var|val)\s+(\w+)(\s*=\s*)(.+)$', new_line)
                    if match:
                        prefix, var_or_val, name, equals, value = match.groups()
                        value_stripped = value.strip().rstrip('\n')
                        
                        # Try to infer type
                        if value_stripped.upper().endswith('L'):
                            type_name = "Long"
                        elif value_stripped.startswith('"') or value_stripped.startswith("'"):
                            type_name = "String"
                        elif value_stripped.lower() in ('true', 'false'):
                            type_name = "Boolean"
                        elif re.match(r'^-?\d+\.?\d*', value_stripped):
                            if '.' in value_stripped:
                                type_name = "Float" if 'f' in value_stripped.lower() else "Double"
                            else:
                                type_name = "Int"
                        else:
                            type_name = None
                        
                        if type_name:
                            new_line = f"{prefix}{var_or_val} {name}: {type_name}{equals}{value}"
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
