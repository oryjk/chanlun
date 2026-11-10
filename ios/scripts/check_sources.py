#!/usr/bin/env python3
"""Portable architecture, resources and optional Swift grammar checks (not an Xcode build)."""
import argparse
import json
from pathlib import Path
import plistlib
import re
import sys
import xml.etree.ElementTree as ET

ROOT = Path(__file__).resolve().parents[1]


def check(syntax=False):
    errors = []
    sources = sorted((ROOT / 'ChanGuan').rglob('*.swift'))
    tests = sorted((ROOT / 'ChanGuanTests').glob('*.swift'))
    project = (ROOT / 'ChanGuan.xcodeproj/project.pbxproj').read_text()
    for source in sources:
        relative = source.relative_to(ROOT).as_posix()
        content = source.read_text()
        layer = source.relative_to(ROOT / 'ChanGuan').parts[0]
        imports = re.findall(r'^import\s+(\w+)', content, re.M)
        if layer in ('Domain', 'Application') and any(value != 'Foundation' for value in imports):
            errors.append(f'{relative}: core imports a UI/SDK framework')
        if layer == 'Presentation' and re.search(r'\b(?:URLSession|APIClient|MarketGateway|AuthGateway|KeychainSessionStore|UserDefaults)\b', content):
            errors.append(f'{relative}: presentation reaches into infrastructure')
        if relative not in project:
            errors.append(f'{relative}: absent from Xcode project')
    for source in tests:
        if source.relative_to(ROOT).as_posix() not in project:
            errors.append(f'{source}: test absent from project')
    for source in list((ROOT / 'ChanGuan').rglob('*.plist')) + list((ROOT / 'ChanGuan').rglob('*.entitlements')) + list((ROOT / 'ChanGuan').rglob('*.xcprivacy')):
        with source.open('rb') as stream:
            plistlib.load(stream)
    for source in (ROOT / 'ChanGuan/Assets.xcassets').rglob('Contents.json'):
        data = json.loads(source.read_text())
        for image in data.get('images', []):
            if 'filename' in image and not source.with_name(image['filename']).is_file():
                errors.append(f'{source}: missing image {image["filename"]}')
    scheme = ET.parse(ROOT / 'ChanGuan.xcodeproj/xcshareddata/xcschemes/ChanGuan.xcscheme')
    for node in scheme.findall('.//BuildableReference'):
        if node.attrib['BlueprintIdentifier'] not in project:
            errors.append('Scheme references missing target')
    if syntax:
        from tree_sitter import Language, Parser
        import tree_sitter_swift
        parser = Parser(Language(tree_sitter_swift.language()))
        for source in sources + tests:
            tree = parser.parse(source.read_bytes())
            if tree.root_node.has_error:
                stack = [tree.root_node]
                while stack:
                    node = stack.pop()
                    if node.is_error or node.is_missing:
                        errors.append(f'{source.relative_to(ROOT)}:{node.start_point.row+1}: Swift grammar error near {node.text[:100]!r}')
                    stack.extend(reversed(node.children))
    if errors:
        print('\n'.join(errors), file=sys.stderr)
        return 1
    print(f'PASS: {len(sources)} Swift sources, {len(tests)} test files, resources, scheme and architecture' + ('; Swift grammar parsed' if syntax else ''))
    print('Not verified: Swift type checking, Apple SDK linking, Xcode build, XCTest execution, simulator or device behavior.')
    return 0


if __name__ == '__main__':
    parser = argparse.ArgumentParser()
    parser.add_argument('--syntax', action='store_true')
    sys.exit(check(parser.parse_args().syntax))
