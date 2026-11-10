#!/usr/bin/env python3
"""Generate the checked-in Xcode project without third-party build tooling."""
from hashlib import sha1
import json
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]


def identifier(name):
    return sha1(name.encode()).hexdigest()[:24].upper()


def generate():
    objects = []
    def obj(key, body):
        objects.append(f'\t\t{identifier(key)} = {{ {body} }};')
        return identifier(key)
    def ids(keys):
        return '(' + ', '.join(identifier(key) for key in keys) + ',)'
    def quote(value):
        return json.dumps(value, ensure_ascii=False)
    def settings(values):
        return '{ ' + ' '.join(f'{key} = {quote(value)};' for key, value in values.items()) + ' }'

    sources = sorted(p.relative_to(ROOT).as_posix() for p in (ROOT / 'ChanGuan').rglob('*.swift'))
    tests = sorted(p.relative_to(ROOT).as_posix() for p in (ROOT / 'ChanGuanTests').rglob('*.swift'))
    resources = ['ChanGuan/Assets.xcassets', 'ChanGuan/PrivacyInfo.xcprivacy']
    test_resources = ['../tests/fixtures/client_contract.json']
    configs = ['Config/Debug.xcconfig', 'Config/Release.xcconfig']
    files = sources + tests + resources + test_resources + configs + ['ChanGuan/Info.plist', 'ChanGuan/ChanGuan.entitlements']
    for path in files:
        kind = 'sourcecode.swift' if path.endswith('.swift') else 'folder.assetcatalog' if path.endswith('.xcassets') else 'text.xcconfig' if path.endswith('.xcconfig') else 'text.json' if path.endswith('.json') else 'text.plist.xml'
        obj('ref:' + path, f'isa = PBXFileReference; lastKnownFileType = {kind}; path = {quote(path)}; sourceTree = "<group>";')
    for path in sources + tests + resources + test_resources:
        obj('build:' + path, f'isa = PBXBuildFile; fileRef = {identifier("ref:" + path)};')
    for key, name, kind in [('app-product', 'ChanGuan.app', 'wrapper.application'), ('test-product', 'ChanGuanTests.xctest', 'wrapper.cfbundle')]:
        obj(key, f'isa = PBXFileReference; explicitFileType = {kind}; path = {name}; sourceTree = BUILT_PRODUCTS_DIR;')
    obj('products', f'isa = PBXGroup; children = {ids(["app-product","test-product"])}; name = Products; sourceTree = "<group>";')
    obj('main-group', f'isa = PBXGroup; children = {ids(["ref:"+p for p in files]+["products"])}; sourceTree = "<group>";')
    for key, kind, paths in [('app-sources','PBXSourcesBuildPhase',sources), ('test-sources','PBXSourcesBuildPhase',tests), ('app-resources','PBXResourcesBuildPhase',resources), ('app-frameworks','PBXFrameworksBuildPhase',[]), ('test-frameworks','PBXFrameworksBuildPhase',[]), ('test-resources','PBXResourcesBuildPhase',test_resources)]:
        files_value = ids(['build:'+p for p in paths]) if paths else '()'
        obj(key, f'isa = {kind}; buildActionMask = 2147483647; files = {files_value}; runOnlyForDeploymentPostprocessing = 0;')
    for scope in ['project', 'app', 'test']:
        for mode in ['Debug', 'Release']:
            values = {'SDKROOT':'iphoneos', 'CLANG_ENABLE_MODULES':'YES', 'IPHONEOS_DEPLOYMENT_TARGET':'17.0', 'SWIFT_VERSION':'5.0'} if scope == 'project' else {
                'PRODUCT_NAME':'$(TARGET_NAME)', 'PRODUCT_BUNDLE_IDENTIFIER':'$(CHANLUN_BUNDLE_ID)' + ('.tests' if scope == 'test' else ''),
                'TARGETED_DEVICE_FAMILY':'1,2', 'CODE_SIGN_STYLE':'Automatic', 'MARKETING_VERSION':'0.1.0', 'CURRENT_PROJECT_VERSION':'1',
                'SWIFT_EMIT_LOC_STRINGS':'YES', 'SUPPORTED_PLATFORMS':'iphoneos iphonesimulator', 'SUPPORTS_MACCATALYST':'NO'}
            if scope == 'app':
                values.update(INFOPLIST_FILE='ChanGuan/Info.plist', CODE_SIGN_ENTITLEMENTS='ChanGuan/ChanGuan.entitlements', ASSETCATALOG_COMPILER_APPICON_NAME='AppIcon', GENERATE_INFOPLIST_FILE='NO', ENABLE_PREVIEWS='YES')
            elif scope == 'test':
                values.update(GENERATE_INFOPLIST_FILE='YES', TEST_HOST='$(BUILT_PRODUCTS_DIR)/ChanGuan.app/ChanGuan', BUNDLE_LOADER='$(TEST_HOST)')
            base = f'baseConfigurationReference = {identifier("ref:Config/"+mode+".xcconfig")};' if scope != 'project' else ''
            obj(scope+'-'+mode, f'isa = XCBuildConfiguration; {base} buildSettings = {settings(values)}; name = {mode};')
        obj(scope+'-configs', f'isa = XCConfigurationList; buildConfigurations = {ids([scope+"-Debug",scope+"-Release"])}; defaultConfigurationIsVisible = 0; defaultConfigurationName = Release;')
    obj('test-proxy', f'isa = PBXContainerItemProxy; containerPortal = {identifier("project")}; proxyType = 1; remoteGlobalIDString = {identifier("app-target")}; remoteInfo = ChanGuan;')
    obj('test-dependency', f'isa = PBXTargetDependency; target = {identifier("app-target")}; targetProxy = {identifier("test-proxy")};')
    for scope, name, phases, product in [('app','ChanGuan',['app-sources','app-frameworks','app-resources'],'app-product'), ('test','ChanGuanTests',['test-sources','test-frameworks','test-resources'],'test-product')]:
        dependencies = ids(['test-dependency']) if scope == 'test' else '()'
        product_type = 'com.apple.product-type.application' if scope == 'app' else 'com.apple.product-type.bundle.unit-test'
        obj(scope+'-target', f'isa = PBXNativeTarget; buildConfigurationList = {identifier(scope+"-configs")}; buildPhases = {ids(phases)}; buildRules = (); dependencies = {dependencies}; name = {name}; productName = {name}; productReference = {identifier(product)}; productType = "{product_type}";')
    obj('project', f'isa = PBXProject; attributes = {{ BuildIndependentTargetsInParallel = YES; LastUpgradeCheck = 1600; }}; buildConfigurationList = {identifier("project-configs")}; compatibilityVersion = "Xcode 14.0"; developmentRegion = zh_CN; knownRegions = (zh_CN, en, Base); mainGroup = {identifier("main-group")}; productRefGroup = {identifier("products")}; projectDirPath = ""; projectRoot = ""; targets = {ids(["app-target","test-target"])};')
    directory = ROOT / 'ChanGuan.xcodeproj'
    directory.mkdir(exist_ok=True)
    directory.joinpath('project.pbxproj').write_text('// !$*UTF8*$!\n{\n\tarchiveVersion = 1;\n\tclasses = {};\n\tobjectVersion = 56;\n\tobjects = {\n' + '\n'.join(objects) + f'\n\t}};\n\trootObject = {identifier("project")};\n}}\n')
    scheme = f'''<?xml version="1.0" encoding="UTF-8"?>
<Scheme LastUpgradeVersion="1600" version="1.3">
  <BuildAction parallelizeBuildables="YES" buildImplicitDependencies="YES"><BuildActionEntries><BuildActionEntry buildForTesting="YES" buildForRunning="YES" buildForProfiling="YES" buildForArchiving="YES" buildForAnalyzing="YES"><BuildableReference BuildableIdentifier="primary" BlueprintIdentifier="{identifier('app-target')}" BuildableName="ChanGuan.app" BlueprintName="ChanGuan" ReferencedContainer="container:ChanGuan.xcodeproj"/></BuildActionEntry></BuildActionEntries></BuildAction>
  <TestAction buildConfiguration="Debug" selectedDebuggerIdentifier="Xcode.DebuggerFoundation.Debugger.LLDB" selectedLauncherIdentifier="Xcode.IDEFoundation.Launcher.LLDB" shouldUseLaunchSchemeArgsEnv="YES"><Testables><TestableReference skipped="NO"><BuildableReference BuildableIdentifier="primary" BlueprintIdentifier="{identifier('test-target')}" BuildableName="ChanGuanTests.xctest" BlueprintName="ChanGuanTests" ReferencedContainer="container:ChanGuan.xcodeproj"/></TestableReference></Testables></TestAction>
  <LaunchAction buildConfiguration="Debug" selectedDebuggerIdentifier="Xcode.DebuggerFoundation.Debugger.LLDB" selectedLauncherIdentifier="Xcode.IDEFoundation.Launcher.LLDB" launchStyle="0" useCustomWorkingDirectory="NO" ignoresPersistentStateOnLaunch="NO" debugServiceExtension="internal" allowLocationSimulation="YES"><BuildableProductRunnable runnableDebuggingMode="0"><BuildableReference BuildableIdentifier="primary" BlueprintIdentifier="{identifier('app-target')}" BuildableName="ChanGuan.app" BlueprintName="ChanGuan" ReferencedContainer="container:ChanGuan.xcodeproj"/></BuildableProductRunnable></LaunchAction>
  <ProfileAction buildConfiguration="Release" shouldUseLaunchSchemeArgsEnv="YES" savedToolIdentifier="" useCustomWorkingDirectory="NO" debugServiceExtension="internal"><BuildableProductRunnable runnableDebuggingMode="0"><BuildableReference BuildableIdentifier="primary" BlueprintIdentifier="{identifier('app-target')}" BuildableName="ChanGuan.app" BlueprintName="ChanGuan" ReferencedContainer="container:ChanGuan.xcodeproj"/></BuildableProductRunnable></ProfileAction>
  <AnalyzeAction buildConfiguration="Debug"/>
  <ArchiveAction buildConfiguration="Release" revealArchiveInOrganizer="YES"/>
</Scheme>
'''
    destination = directory / 'xcshareddata/xcschemes'
    destination.mkdir(parents=True, exist_ok=True)
    destination.joinpath('ChanGuan.xcscheme').write_text(scheme)
    print(f'Generated project: {len(sources)} source files, {len(tests)} test files')


if __name__ == '__main__':
    generate()
