#!/usr/bin/env python3
"""
Convert Flutter ARB (Application Resource Bundle) format to Android XML strings.
This script converts localization files from Flutter format to Android format.
"""

import json
import sys
import re
from pathlib import Path
from xml.sax.saxutils import escape
from typing import Dict, Any


def convert_placeholder_format(text: str) -> tuple[str, bool]:
    """
    Convert Flutter placeholder format to Android format.
    Flutter: "Hello $name" or "Count: $count items"
    Android: "Hello %1$s" or "Count: %1$s items"
    
    Returns tuple of (converted_text, has_placeholders)
    """
    has_placeholders = False
    
    # Find all Flutter-style placeholders like $name, $count, $error
    placeholders = re.findall(r'\$(\w+)', text)
    
    if not placeholders:
        return text, has_placeholders
    
    has_placeholders = True
    result = text
    
    # Replace each unique placeholder with numbered Android format
    # We'll use %s for strings by default
    for idx, placeholder in enumerate(set(placeholders), start=1):
        # Replace $placeholder with %1$s, %2$s, etc.
        result = result.replace(f'${placeholder}', f'%{idx}$s')
    
    return result, has_placeholders


def escape_xml_value(value: str) -> str:
    """
    Escape special characters for Android XML string resources.
    """
    # First escape basic XML characters
    escaped = escape(value)
    
    # Android-specific escapes
    # Quote characters need to be escaped
    escaped = escaped.replace("'", "\\'")
    escaped = escaped.replace('"', '\\"')
    
    # Newlines should be \\n
    escaped = escaped.replace('\n', '\\n')
    
    return escaped


def arb_to_xml(arb_file: Path, output_file: Path) -> None:
    """
    Convert ARB file to Android strings.xml format.
    """
    try:
        # Read ARB file
        with open(arb_file, 'r', encoding='utf-8') as f:
            arb_data = json.load(f)
        
        # Start building XML
        xml_lines = ['<?xml version="1.0" encoding="utf-8"?>']
        xml_lines.append('<resources>')
        
        # Track keys to avoid duplicates
        processed_keys = set()
        string_count = 0
        
        # Iterate through ARB entries
        for key, value in arb_data.items():
            # Skip metadata entries (start with @) and locale
            if key.startswith('@') or key == '@@locale':
                continue
            
            # Skip if not a string value
            if not isinstance(value, str):
                continue
            
            # Skip duplicates
            if key in processed_keys:
                continue
            
            processed_keys.add(key)
            string_count += 1
            
            # Convert placeholders and escape XML
            converted_value, has_placeholders = convert_placeholder_format(value)
            escaped_value = escape_xml_value(converted_value)
            
            # Add comment if there's metadata for this key
            metadata_key = f'@{key}'
            if metadata_key in arb_data and isinstance(arb_data[metadata_key], dict):
                description = arb_data[metadata_key].get('description', '')
                if description:
                    xml_lines.append(f'    <!-- {escape(description)} -->')
            
            # Add the string resource
            xml_lines.append(f'    <string name="{key}">{escaped_value}</string>')
        
        xml_lines.append('</resources>')
        xml_lines.append('')  # Empty line at end
        
        # Write XML file
        output_file.parent.mkdir(parents=True, exist_ok=True)
        with open(output_file, 'w', encoding='utf-8') as f:
            f.write('\n'.join(xml_lines))
        
        print(f'✓ Converted {string_count} strings from {arb_file.name} to {output_file}')
        
    except Exception as e:
        print(f'✗ Error converting {arb_file}: {e}', file=sys.stderr)
        sys.exit(1)


def main():
    """Main entry point."""
    if len(sys.argv) != 3:
        print('Usage: arb_to_xml.py <input.arb> <output.xml>')
        print('Example: arb_to_xml.py app_en.arb values/strings.xml')
        sys.exit(1)
    
    input_file = Path(sys.argv[1])
    output_file = Path(sys.argv[2])
    
    if not input_file.exists():
        print(f'✗ Input file not found: {input_file}', file=sys.stderr)
        sys.exit(1)
    
    arb_to_xml(input_file, output_file)
    print('✓ Conversion complete!')


if __name__ == '__main__':
    main()
