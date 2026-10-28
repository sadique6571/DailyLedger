#!/usr/bin/env python3
"""Expose connected Android test failures in Actions annotations and the job summary."""
import os
import sys
from pathlib import Path
import xml.etree.ElementTree as ET


def command_text(value):
    return str(value).replace('%', '%25').replace('\r', '%0D').replace('\n', '%0A')


def report(root):
    failures = []
    for path in sorted(root.rglob('TEST-*.xml')):
        try:
            suite = ET.parse(path).getroot()
        except (ET.ParseError, OSError) as error:
            print(f'::warning::{command_text(f"Cannot read Android test report {path}: {error}")}')
            continue
        for case in suite.iter('testcase'):
            for failure in list(case.findall('failure')) + list(case.findall('error')):
                name = f'{case.get("classname", suite.get("name", "Android"))}#{case.get("name", "unknown")}'
                detail = failure.get('message') or (failure.text or '').strip() or 'No failure message recorded.'
                failures.append((name, detail, (failure.text or '').strip()))
    for name, detail, _ in failures:
        print(f'::error title=Android test failed::{command_text(name + ": " + detail[:4000])}')
    if not failures:
        print('::notice::No failed Android test assertion was written to XML. Check emulator startup and the release startup smoke test in the failed step.')
    summary = os.environ.get('GITHUB_STEP_SUMMARY')
    if summary:
        with open(summary, 'a', encoding='utf-8') as output:
            output.write('## Android test failures\n\n')
            for name, detail, trace in failures:
                output.write(f'### {name}\n\n```text\n{(trace or detail)[:12000].replace("```", "~~~")}\n```\n\n')
            if not failures:
                output.write('No failed test assertion found in XML; inspect emulator/startup logs.\n')
    return failures


if __name__ == '__main__':
    report(Path(sys.argv[1]) if len(sys.argv) > 1 else Path('app/build/outputs/androidTest-results/connected'))
