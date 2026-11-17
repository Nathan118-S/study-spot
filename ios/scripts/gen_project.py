#!/usr/bin/env python3
"""Regenerates StudySpot.xcodeproj (Xcode 16 synchronized folders).

Targets: StudySpot (app) and StudySpotWidgets (WidgetKit extension: widgets,
Lock Screen widgets, Live Activity). Shared/ is compiled into both.
Run from ios/:  python3 scripts/gen_project.py
"""
import os

names = ["app_product", "wid_product", "app_group", "wid_group", "shared_group",
         "app_exc", "wid_exc", "app_target", "wid_target",
         "app_src", "app_fw", "app_res", "app_embed", "wid_src", "wid_fw", "wid_res",
         "embed_bf", "proxy", "dep", "main", "products", "project",
         "proj_cfgs", "app_cfgs", "wid_cfgs", "proj_dbg", "proj_rel", "app_dbg", "app_rel", "wid_dbg", "wid_rel"]
I = {k: "5D1A2B3C4D5E6F70%08X" % i for i, k in enumerate(names, start=1)}

common = """				ALWAYS_SEARCH_USER_PATHS = NO;
				ASSETCATALOG_COMPILER_GENERATE_SWIFT_ASSET_SYMBOL_EXTENSIONS = YES;
				CLANG_ANALYZER_NONNULL = YES;
				CLANG_CXX_LANGUAGE_STANDARD = "gnu++20";
				CLANG_ENABLE_MODULES = YES;
				CLANG_ENABLE_OBJC_ARC = YES;
				CLANG_ENABLE_OBJC_WEAK = YES;
				CLANG_WARN_BLOCK_CAPTURE_AUTORELEASING = YES;
				CLANG_WARN_BOOL_CONVERSION = YES;
				CLANG_WARN_COMMA = YES;
				CLANG_WARN_CONSTANT_CONVERSION = YES;
				CLANG_WARN_DEPRECATED_OBJC_IMPLEMENTATIONS = YES;
				CLANG_WARN_DIRECT_OBJC_ISA_USAGE = YES_ERROR;
				CLANG_WARN_DOCUMENTATION_COMMENTS = YES;
				CLANG_WARN_EMPTY_BODY = YES;
				CLANG_WARN_ENUM_CONVERSION = YES;
				CLANG_WARN_INFINITE_RECURSION = YES;
				CLANG_WARN_INT_CONVERSION = YES;
				CLANG_WARN_NON_LITERAL_NULL_CONVERSION = YES;
				CLANG_WARN_OBJC_IMPLICIT_RETAIN_SELF = YES;
				CLANG_WARN_OBJC_LITERAL_CONVERSION = YES;
				CLANG_WARN_OBJC_ROOT_CLASS = YES_ERROR;
				CLANG_WARN_QUOTED_INCLUDE_IN_FRAMEWORK_HEADER = YES;
				CLANG_WARN_RANGE_LOOP_ANALYSIS = YES;
				CLANG_WARN_STRICT_PROTOTYPES = YES;
				CLANG_WARN_SUSPICIOUS_MOVE = YES;
				CLANG_WARN_UNGUARDED_AVAILABILITY = YES_AGGRESSIVE;
				CLANG_WARN_UNREACHABLE_CODE = YES;
				CLANG_WARN__DUPLICATE_METHOD_MATCH = YES;
				COPY_PHASE_STRIP = NO;
				ENABLE_STRICT_OBJC_MSGSEND = YES;
				ENABLE_USER_SCRIPT_SANDBOXING = YES;
				GCC_C_LANGUAGE_STANDARD = gnu17;
				GCC_NO_COMMON_BLOCKS = YES;
				GCC_WARN_64_TO_32_BIT_CONVERSION = YES;
				GCC_WARN_ABOUT_RETURN_TYPE = YES_ERROR;
				GCC_WARN_UNDECLARED_SELECTOR = YES;
				GCC_WARN_UNINITIALIZED_AUTOS = YES_AGGRESSIVE;
				GCC_WARN_UNUSED_FUNCTION = YES;
				GCC_WARN_UNUSED_VARIABLE = YES;
				IPHONEOS_DEPLOYMENT_TARGET = 17.0;
				LOCALIZATION_PREFERS_STRING_CATALOGS = YES;
				MTL_FAST_MATH = YES;
				SDKROOT = iphoneos;
				SWIFT_VERSION = 5.0;
"""
proj_dbg = common + """				DEBUG_INFORMATION_FORMAT = dwarf;
				ENABLE_TESTABILITY = YES;
				GCC_DYNAMIC_NO_PIC = NO;
				GCC_OPTIMIZATION_LEVEL = 0;
				GCC_PREPROCESSOR_DEFINITIONS = (
					"DEBUG=1",
					"$(inherited)",
				);
				MTL_ENABLE_DEBUG_INFO = INCLUDE_SOURCE;
				ONLY_ACTIVE_ARCH = YES;
				SWIFT_ACTIVE_COMPILATION_CONDITIONS = "DEBUG $(inherited)";
				SWIFT_OPTIMIZATION_LEVEL = "-Onone";
"""
proj_rel = common + """				DEBUG_INFORMATION_FORMAT = "dwarf-with-dsym";
				ENABLE_NS_ASSERTIONS = NO;
				MTL_ENABLE_DEBUG_INFO = NO;
				SWIFT_COMPILATION_MODE = wholemodule;
				VALIDATE_PRODUCT = YES;
"""
app = """				ASSETCATALOG_COMPILER_APPICON_NAME = AppIcon;
				ASSETCATALOG_COMPILER_GLOBAL_ACCENT_COLOR_NAME = AccentColor;
				CODE_SIGN_ENTITLEMENTS = StudySpot/StudySpot.entitlements;
				CODE_SIGN_STYLE = Automatic;
				CURRENT_PROJECT_VERSION = 1;
				DEVELOPMENT_TEAM = "";
				ENABLE_PREVIEWS = YES;
				GENERATE_INFOPLIST_FILE = YES;
				INFOPLIST_FILE = StudySpot/Info.plist;
				INFOPLIST_KEY_CFBundleDisplayName = "Study Spot";
				INFOPLIST_KEY_LSApplicationCategoryType = "public.app-category.education";
				INFOPLIST_KEY_NSSupportsLiveActivities = YES;
				INFOPLIST_KEY_UIApplicationSceneManifest_Generation = YES;
				INFOPLIST_KEY_UIApplicationSupportsIndirectInputEvents = YES;
				INFOPLIST_KEY_UISupportedInterfaceOrientations_iPad = "UIInterfaceOrientationPortrait UIInterfaceOrientationPortraitUpsideDown UIInterfaceOrientationLandscapeLeft UIInterfaceOrientationLandscapeRight";
				INFOPLIST_KEY_UISupportedInterfaceOrientations_iPhone = "UIInterfaceOrientationPortrait UIInterfaceOrientationLandscapeLeft UIInterfaceOrientationLandscapeRight";
				LD_RUNPATH_SEARCH_PATHS = (
					"$(inherited)",
					"@executable_path/Frameworks",
				);
				MARKETING_VERSION = 1.0;
				PRODUCT_BUNDLE_IDENTIFIER = com.studyspot.app;
				PRODUCT_NAME = "$(TARGET_NAME)";
				STUDYSPOT_SERVER_URL = "http://localhost:8787";
				STUDYSPOT_WEB_URL = "";
				SWIFT_EMIT_LOC_STRINGS = YES;
				TARGETED_DEVICE_FAMILY = "1,2";
"""
wid = """				ASSETCATALOG_COMPILER_GLOBAL_ACCENT_COLOR_NAME = AccentColor;
				CODE_SIGN_ENTITLEMENTS = StudySpotWidgets/StudySpotWidgets.entitlements;
				CODE_SIGN_STYLE = Automatic;
				CURRENT_PROJECT_VERSION = 1;
				DEVELOPMENT_TEAM = "";
				ENABLE_PREVIEWS = YES;
				GENERATE_INFOPLIST_FILE = YES;
				INFOPLIST_FILE = StudySpotWidgets/Info.plist;
				INFOPLIST_KEY_CFBundleDisplayName = "Study Spot";
				LD_RUNPATH_SEARCH_PATHS = (
					"$(inherited)",
					"@executable_path/Frameworks",
					"@executable_path/../../Frameworks",
				);
				MARKETING_VERSION = 1.0;
				PRODUCT_BUNDLE_IDENTIFIER = com.studyspot.app.widgets;
				PRODUCT_NAME = "$(TARGET_NAME)";
				SKIP_INSTALL = YES;
				SWIFT_EMIT_LOC_STRINGS = YES;
				TARGETED_DEVICE_FAMILY = "1,2";
"""

def phase(isa, key, name, extra=""):
    return f"""		{I[key]} /* {name} */ = {{
			isa = {isa};
			buildActionMask = 2147483647;
{extra}			files = (
			);
			runOnlyForDeploymentPostprocessing = 0;
		}};
"""

def cfg(key, name, body):
    return f"""		{I[key]} /* {name} */ = {{
			isa = XCBuildConfiguration;
			buildSettings = {{
{body}			}};
			name = {name};
		}};
"""

def cfglist(key, label, dbg, rel):
    return f"""		{I[key]} /* Build configuration list for {label} */ = {{
			isa = XCConfigurationList;
			buildConfigurations = (
				{I[dbg]} /* Debug */,
				{I[rel]} /* Release */,
			);
			defaultConfigurationIsVisible = 0;
			defaultConfigurationName = Release;
		}};
"""

def target(key, name, cfgs, phases, groups, product, ptype, deps=""):
    ph = "".join(f"				{I[p]},\n" for p in phases)
    gr = "".join(f"				{I[g]},\n" for g in groups)
    return f"""		{I[key]} /* {name} */ = {{
			isa = PBXNativeTarget;
			buildConfigurationList = {I[cfgs]};
			buildPhases = (
{ph}			);
			buildRules = (
			);
			dependencies = (
{deps}			);
			fileSystemSynchronizedGroups = (
{gr}			);
			name = {name};
			packageProductDependencies = (
			);
			productName = {name};
			productReference = {I[product]};
			productType = "{ptype}";
		}};
"""

embed_phase = f"""		{I['app_embed']} /* Embed Foundation Extensions */ = {{
			isa = PBXCopyFilesBuildPhase;
			buildActionMask = 2147483647;
			dstPath = "";
			dstSubfolderSpec = 13;
			files = (
				{I['embed_bf']} /* StudySpotWidgets.appex in Embed Foundation Extensions */,
			);
			name = "Embed Foundation Extensions";
			runOnlyForDeploymentPostprocessing = 0;
		}};
"""

pbx = f"""// !$*UTF8*$!
{{
	archiveVersion = 1;
	classes = {{
	}};
	objectVersion = 77;
	objects = {{

/* Begin PBXBuildFile section */
		{I['embed_bf']} /* StudySpotWidgets.appex in Embed Foundation Extensions */ = {{isa = PBXBuildFile; fileRef = {I['wid_product']}; settings = {{ATTRIBUTES = (RemoveHeadersOnCopy, ); }}; }};
/* End PBXBuildFile section */

/* Begin PBXContainerItemProxy section */
		{I['proxy']} /* PBXContainerItemProxy */ = {{
			isa = PBXContainerItemProxy;
			containerPortal = {I['project']};
			proxyType = 1;
			remoteGlobalIDString = {I['wid_target']};
			remoteInfo = StudySpotWidgets;
		}};
/* End PBXContainerItemProxy section */

/* Begin PBXCopyFilesBuildPhase section */
{embed_phase}/* End PBXCopyFilesBuildPhase section */

/* Begin PBXFileReference section */
		{I['app_product']} /* StudySpot.app */ = {{isa = PBXFileReference; explicitFileType = wrapper.application; includeInIndex = 0; path = StudySpot.app; sourceTree = BUILT_PRODUCTS_DIR; }};
		{I['wid_product']} /* StudySpotWidgets.appex */ = {{isa = PBXFileReference; explicitFileType = "wrapper.app-extension"; includeInIndex = 0; path = StudySpotWidgets.appex; sourceTree = BUILT_PRODUCTS_DIR; }};
/* End PBXFileReference section */

/* Begin PBXFileSystemSynchronizedBuildFileExceptionSet section */
		{I['app_exc']} /* Exceptions for "StudySpot" folder in "StudySpot" target */ = {{
			isa = PBXFileSystemSynchronizedBuildFileExceptionSet;
			membershipExceptions = (
				Info.plist,
				StudySpot.entitlements,
			);
			target = {I['app_target']};
		}};
		{I['wid_exc']} /* Exceptions for "StudySpotWidgets" folder in "StudySpotWidgets" target */ = {{
			isa = PBXFileSystemSynchronizedBuildFileExceptionSet;
			membershipExceptions = (
				Info.plist,
				StudySpotWidgets.entitlements,
			);
			target = {I['wid_target']};
		}};
/* End PBXFileSystemSynchronizedBuildFileExceptionSet section */

/* Begin PBXFileSystemSynchronizedRootGroup section */
		{I['app_group']} /* StudySpot */ = {{
			isa = PBXFileSystemSynchronizedRootGroup;
			exceptions = (
				{I['app_exc']},
			);
			path = StudySpot;
			sourceTree = "<group>";
		}};
		{I['wid_group']} /* StudySpotWidgets */ = {{
			isa = PBXFileSystemSynchronizedRootGroup;
			exceptions = (
				{I['wid_exc']},
			);
			path = StudySpotWidgets;
			sourceTree = "<group>";
		}};
		{I['shared_group']} /* Shared */ = {{
			isa = PBXFileSystemSynchronizedRootGroup;
			path = Shared;
			sourceTree = "<group>";
		}};
/* End PBXFileSystemSynchronizedRootGroup section */

/* Begin PBXFrameworksBuildPhase section */
{phase('PBXFrameworksBuildPhase','app_fw','Frameworks')}{phase('PBXFrameworksBuildPhase','wid_fw','Frameworks')}/* End PBXFrameworksBuildPhase section */

/* Begin PBXGroup section */
		{I['main']} = {{
			isa = PBXGroup;
			children = (
				{I['app_group']} /* StudySpot */,
				{I['wid_group']} /* StudySpotWidgets */,
				{I['shared_group']} /* Shared */,
				{I['products']} /* Products */,
			);
			sourceTree = "<group>";
		}};
		{I['products']} /* Products */ = {{
			isa = PBXGroup;
			children = (
				{I['app_product']},
				{I['wid_product']},
			);
			name = Products;
			sourceTree = "<group>";
		}};
/* End PBXGroup section */

/* Begin PBXNativeTarget section */
{target('app_target','StudySpot','app_cfgs',['app_src','app_fw','app_res','app_embed'],['app_group','shared_group'],'app_product','com.apple.product-type.application', f"				{I['dep']},\n")}{target('wid_target','StudySpotWidgets','wid_cfgs',['wid_src','wid_fw','wid_res'],['wid_group','shared_group'],'wid_product','com.apple.product-type.app-extension')}/* End PBXNativeTarget section */

/* Begin PBXProject section */
		{I['project']} /* Project object */ = {{
			isa = PBXProject;
			attributes = {{
				BuildIndependentTargetsInParallel = 1;
				LastSwiftUpdateCheck = 1600;
				LastUpgradeCheck = 1600;
				TargetAttributes = {{
					{I['app_target']} = {{
						CreatedOnToolsVersion = 16.0;
					}};
					{I['wid_target']} = {{
						CreatedOnToolsVersion = 16.0;
					}};
				}};
			}};
			buildConfigurationList = {I['proj_cfgs']};
			developmentRegion = en;
			hasScannedForEncodings = 0;
			knownRegions = (
				en,
				Base,
			);
			mainGroup = {I['main']};
			minimizedProjectReferenceProxies = 1;
			preferredProjectObjectVersion = 77;
			productRefGroup = {I['products']};
			projectDirPath = "";
			projectRoot = "";
			targets = (
				{I['app_target']},
				{I['wid_target']},
			);
		}};
/* End PBXProject section */

/* Begin PBXResourcesBuildPhase section */
{phase('PBXResourcesBuildPhase','app_res','Resources')}{phase('PBXResourcesBuildPhase','wid_res','Resources')}/* End PBXResourcesBuildPhase section */

/* Begin PBXSourcesBuildPhase section */
{phase('PBXSourcesBuildPhase','app_src','Sources')}{phase('PBXSourcesBuildPhase','wid_src','Sources')}/* End PBXSourcesBuildPhase section */

/* Begin PBXTargetDependency section */
		{I['dep']} /* PBXTargetDependency */ = {{
			isa = PBXTargetDependency;
			target = {I['wid_target']};
			targetProxy = {I['proxy']};
		}};
/* End PBXTargetDependency section */

/* Begin XCBuildConfiguration section */
{cfg('proj_dbg','Debug',proj_dbg)}{cfg('proj_rel','Release',proj_rel)}{cfg('app_dbg','Debug',app)}{cfg('app_rel','Release',app)}{cfg('wid_dbg','Debug',wid)}{cfg('wid_rel','Release',wid)}/* End XCBuildConfiguration section */

/* Begin XCConfigurationList section */
{cfglist('proj_cfgs','PBXProject "StudySpot"','proj_dbg','proj_rel')}{cfglist('app_cfgs','PBXNativeTarget "StudySpot"','app_dbg','app_rel')}{cfglist('wid_cfgs','PBXNativeTarget "StudySpotWidgets"','wid_dbg','wid_rel')}/* End XCConfigurationList section */
	}};
	rootObject = {I['project']};
}}
"""
open("StudySpot.xcodeproj/project.pbxproj", "w").write(pbx)

scheme_ref = lambda: f"""<BuildableReference
               BuildableIdentifier = "primary"
               BlueprintIdentifier = "{I['app_target']}"
               BuildableName = "StudySpot.app"
               BlueprintName = "StudySpot"
               ReferencedContainer = "container:StudySpot.xcodeproj">
            </BuildableReference>"""
os.makedirs("StudySpot.xcodeproj/xcshareddata/xcschemes", exist_ok=True)
open("StudySpot.xcodeproj/xcshareddata/xcschemes/StudySpot.xcscheme", "w").write(f"""<?xml version="1.0" encoding="UTF-8"?>
<Scheme LastUpgradeVersion = "1600" version = "1.7">
   <BuildAction parallelizeBuildables = "YES" buildImplicitDependencies = "YES">
      <BuildActionEntries>
         <BuildActionEntry buildForTesting = "YES" buildForRunning = "YES" buildForProfiling = "YES" buildForArchiving = "YES" buildForAnalyzing = "YES">
            {scheme_ref()}
         </BuildActionEntry>
      </BuildActionEntries>
   </BuildAction>
   <TestAction buildConfiguration = "Debug" selectedDebuggerIdentifier = "Xcode.DebuggerFoundation.Debugger.LLDB" selectedLauncherIdentifier = "Xcode.DebuggerFoundation.Launcher.LLDB" shouldUseLaunchSchemeArgsEnv = "YES">
   </TestAction>
   <LaunchAction buildConfiguration = "Debug" selectedDebuggerIdentifier = "Xcode.DebuggerFoundation.Debugger.LLDB" selectedLauncherIdentifier = "Xcode.DebuggerFoundation.Launcher.LLDB" launchStyle = "0" useCustomWorkingDirectory = "NO" ignoresPersistentStateOnLaunch = "NO" debugDocumentVersioning = "YES" debugServiceExtension = "internal" allowLocationSimulation = "YES">
      <BuildableProductRunnable runnableDebuggingMode = "0">
         {scheme_ref()}
      </BuildableProductRunnable>
   </LaunchAction>
   <ProfileAction buildConfiguration = "Release" shouldUseLaunchSchemeArgsEnv = "YES" savedToolIdentifier = "" useCustomWorkingDirectory = "NO" debugDocumentVersioning = "YES">
      <BuildableProductRunnable runnableDebuggingMode = "0">
         {scheme_ref()}
      </BuildableProductRunnable>
   </ProfileAction>
   <AnalyzeAction buildConfiguration = "Debug">
   </AnalyzeAction>
   <ArchiveAction buildConfiguration = "Release" revealArchiveInOrganizer = "YES">
   </ArchiveAction>
</Scheme>
""")
print("wrote project with", len(I), "object ids")
