#!/usr/bin/env python3
"""
Analyze Android logcat startup_profile.log and format it into a readable report.

Usage:
    python scripts/analyze_startup_log.py startup_profile.log
    python scripts/analyze_startup_log.py startup_profile.log --output report.md
"""

import re
import sys
from collections import defaultdict
from dataclasses import dataclass
from typing import List, Dict
from pathlib import Path


@dataclass
class LogEntry:
    timestamp: str
    pid: str
    tid: str
    level: str
    tag: str
    message: str
    full_line: str


@dataclass
class CrashReport:
    timestamp: str
    pid: str
    exception_type: str
    message: str
    stack_trace: List[str]


def parse_logcat_line(line: str) -> LogEntry | None:
    """Parse a single logcat line."""
    # Format: MM-DD HH:MM:SS.mmm  PID  TID LEVEL TAG: Message
    pattern = r'^(\d{2}-\d{2}\s+\d{2}:\d{2}:\d{2}\.\d{3})\s+(\d+)\s+(\d+)\s+([A-Z])\s+([^:]+):\s+(.*)$'
    match = re.match(pattern, line)
    
    if match:
        return LogEntry(
            timestamp=match.group(1),
            pid=match.group(2),
            tid=match.group(3),
            level=match.group(4),
            tag=match.group(5).strip(),
            message=match.group(6),
            full_line=line
        )
    return None


def extract_crashes(lines: List[str]) -> List[CrashReport]:
    """Extract all FATAL EXCEPTION crashes from log."""
    crashes = []
    i = 0
    
    while i < len(lines):
        if 'FATAL EXCEPTION' in lines[i]:
            entry = parse_logcat_line(lines[i])
            if not entry:
                i += 1
                continue
                
            # Start collecting stack trace
            stack_trace = []
            exception_type = ""
            message = ""
            
            # Next line should be the exception type and message
            i += 1
            if i < len(lines):
                next_entry = parse_logcat_line(lines[i])
                if next_entry and next_entry.level == 'E':
                    # Extract exception type and message
                    exc_match = re.match(r'([^:]+):\s+(.+)', next_entry.message)
                    if exc_match:
                        exception_type = exc_match.group(1)
                        message = exc_match.group(2)
                    else:
                        exception_type = "Unknown"
                        message = next_entry.message
            
            # Collect stack trace lines
            i += 1
            while i < len(lines):
                line_entry = parse_logcat_line(lines[i])
                if not line_entry or line_entry.level != 'E' or line_entry.tag != 'AndroidRuntime':
                    break
                stack_trace.append(line_entry.message)
                i += 1
            
            crashes.append(CrashReport(
                timestamp=entry.timestamp,
                pid=entry.pid,
                exception_type=exception_type,
                message=message,
                stack_trace=stack_trace
            ))
        else:
            i += 1
    
    return crashes


def categorize_errors(lines: List[str]) -> Dict[str, List[LogEntry]]:
    """Categorize errors and warnings by type."""
    categories = defaultdict(list)
    
    for line in lines:
        entry = parse_logcat_line(line)
        if not entry:
            continue
        
        # Skip system noise
        if entry.tag in ['SurfaceComposerClient', 'SurfaceFlinger', 'AppSearchAppsUtil', 
                         'HeatmapThread', 'Watchdog', 'io_stats']:
            continue
        
        if entry.level in ['E', 'W']:
            # Categorize by tag
            categories[entry.tag].append(entry)
    
    return categories


def generate_markdown_report(log_path: Path) -> str:
    """Generate a markdown report from log file."""
    with open(log_path, 'r', encoding='utf-8', errors='ignore') as f:
        lines = f.readlines()
    
    # Extract relevant lines (errors/warnings/crashes)
    relevant_lines = [l for l in lines if any(x in l for x in ['error', 'Error', 'ERROR', 
                                                                'warn', 'Warn', 'WARN',
                                                                'fatal', 'Fatal', 'FATAL',
                                                                'exception', 'Exception',
                                                                'crash', 'Crash'])]
    
    crashes = extract_crashes(lines)
    errors_by_category = categorize_errors(relevant_lines)
    
    # Generate report
    report = []
    report.append(f"# Startup Log Analysis Report\n")
    report.append(f"**Log file:** `{log_path.name}`\n")
    report.append(f"**Total lines:** {len(lines):,}\n")
    report.append(f"**Relevant lines (errors/warnings):** {len(relevant_lines):,}\n")
    report.append("\n---\n")
    
    # Crashes section
    report.append(f"\n## 🔴 FATAL CRASHES ({len(crashes)})\n")
    
    if crashes:
        for idx, crash in enumerate(crashes, 1):
            report.append(f"\n### Crash #{idx}\n")
            report.append(f"**Time:** {crash.timestamp}\n")
            report.append(f"**PID:** {crash.pid}\n")
            report.append(f"**Exception:** `{crash.exception_type}`\n")
            report.append(f"**Message:**\n```\n{crash.message}\n```\n")
            
            report.append("\n**Stack Trace:**\n")
            report.append("```\n")
            for line in crash.stack_trace[:20]:  # Limit to first 20 lines
                report.append(f"{line}\n")
            if len(crash.stack_trace) > 20:
                report.append(f"... ({len(crash.stack_trace) - 20} more lines)\n")
            report.append("```\n")
    else:
        report.append("✅ No fatal crashes detected!\n")
    
    # Errors by category
    report.append(f"\n---\n\n## ⚠️ ERRORS AND WARNINGS BY CATEGORY\n")
    
    # Filter JaBook-specific errors
    jabook_categories = {k: v for k, v in errors_by_category.items() 
                         if 'jabook' in k.lower() or any('jabook' in e.message.lower() for e in v[:5])}
    
    report.append(f"\n### JaBook-Specific Issues ({sum(len(v) for v in jabook_categories.values())} entries)\n")
    
    for tag in sorted(jabook_categories.keys()):
        entries = jabook_categories[tag]
        report.append(f"\n#### {tag} ({len(entries)} occurrences)\n")
        
        # Show first 3 unique messages
        seen_messages = set()
        shown = 0
        for entry in entries:
            if entry.message not in seen_messages and shown < 3:
                report.append(f"- `[{entry.level}]` {entry.message[:200]}\n")
                seen_messages.add(entry.message)
                shown += 1
        
        if len(entries) > 3:
            report.append(f"  *(+{len(entries) - shown} more)*\n")
    
    # Other categories (top 10 by count)
    other_categories = {k: v for k, v in errors_by_category.items() if k not in jabook_categories}
    top_other = sorted(other_categories.items(), key=lambda x: len(x[1]), reverse=True)[:10]
    
    if top_other:
        report.append(f"\n### System Issues (Top 10 by frequency)\n")
        for tag, entries in top_other:
            report.append(f"- **{tag}**: {len(entries)} occurrences\n")
    
    # Statistics
    report.append(f"\n---\n\n## 📊 STATISTICS\n")
    
    error_count = sum(1 for l in relevant_lines if parse_logcat_line(l) and parse_logcat_line(l).level == 'E')
    warning_count = sum(1 for l in relevant_lines if parse_logcat_line(l) and parse_logcat_line(l).level == 'W')
    
    report.append(f"- **Total Errors (E):** {error_count}\n")
    report.append(f"- **Total Warnings (W):** {warning_count}\n")
    report.append(f"- **Fatal Crashes:** {len(crashes)}\n")
    report.append(f"- **Unique Error Categories:** {len(errors_by_category)}\n")
    report.append(f"- **JaBook-Specific Categories:** {len(jabook_categories)}\n")
    
    return ''.join(report)


def main():
    if len(sys.argv) < 2:
        print("Usage: python analyze_startup_log.py <log_file> [--output <output_file>]")
        sys.exit(1)
    
    log_path = Path(sys.argv[1])
    if not log_path.exists():
        print(f"Error: Log file '{log_path}' not found")
        sys.exit(1)
    
    output_path = None
    if '--output' in sys.argv:
        output_idx = sys.argv.index('--output')
        if output_idx + 1 < len(sys.argv):
            output_path = Path(sys.argv[output_idx + 1])
    
    print(f"Analyzing {log_path}...")
    report = generate_markdown_report(log_path)
    
    if output_path:
        with open(output_path, 'w', encoding='utf-8') as f:
            f.write(report)
        print(f"✅ Report saved to: {output_path}")
    else:
        print(report)


if __name__ == '__main__':
    main()
