#!/usr/bin/env python3
"""Configure the iOS project for your Apple developer account and server.

Bundle identifiers must be unique per Apple account, so pick your own
(e.g. com.yourname.studyspot). This updates every place that depends on it —
both targets, the App Group in both entitlements files, and the Swift
constant — then regenerates StudySpot.xcodeproj.

Usage (from the ios/ folder):
  python3 scripts/configure.py --bundle-id com.yourname.studyspot \
      --server http://192.168.1.20:8787 --web http://192.168.1.20:5173

Every option is optional; omitted ones keep their current value.
"""
import argparse
import os
import re
import subprocess
import sys

HERE = os.path.dirname(os.path.abspath(__file__))
IOS = os.path.dirname(HERE)
GEN = os.path.join(HERE, "gen_project.py")
ENTITLEMENTS = [
    os.path.join(IOS, "StudySpot", "StudySpot.entitlements"),
    os.path.join(IOS, "StudySpotWidgets", "StudySpotWidgets.entitlements"),
]
SHARED = os.path.join(IOS, "Shared", "SharedModels.swift")


def read(p):
    with open(p) as f:
        return f.read()


def write(p, s):
    with open(p, "w") as f:
        f.write(s)


def main():
    ap = argparse.ArgumentParser(description=__doc__, formatter_class=argparse.RawDescriptionHelpFormatter)
    ap.add_argument("--bundle-id", help="App bundle ID, e.g. com.yourname.studyspot")
    ap.add_argument("--team", help="Apple Development Team ID (10 characters), optional")
    ap.add_argument("--server", help="Default server address, e.g. https://studyspot.example.com")
    ap.add_argument("--web", help="Website address where users log in. Defaults to the server "
                                  "address (right when one address serves both, as with Docker). Only needed "
                                  "for local development, e.g. http://<mac-ip>:5173")
    ap.add_argument("--push", action=argparse.BooleanOptionalAction, default=None,
                    help="Enable (--push) or disable (--no-push) the Push Notifications capability, used by the "
                         "server to start class Live Activities when the app is closed. Needs a paid Apple "
                         "Developer account and the APNs key set on the server.")
    args = ap.parse_args()

    gen = read(GEN)
    current = re.search(r"PRODUCT_BUNDLE_IDENTIFIER = ([\w.\-]+);\n\t\t\t\tPRODUCT_NAME = \"\$\(TARGET_NAME\)\";\n\t\t\t\tSTUDYSPOT", gen).group(1)

    if args.bundle_id:
        new = args.bundle_id.strip()
        if not re.fullmatch(r"[A-Za-z0-9\-]+(\.[A-Za-z0-9\-]+)+", new):
            sys.exit(f"Invalid bundle ID: {new!r} (use reverse-DNS like com.yourname.studyspot)")
        gen = gen.replace(f"PRODUCT_BUNDLE_IDENTIFIER = {current}.widgets;", f"PRODUCT_BUNDLE_IDENTIFIER = {new}.widgets;")
        gen = gen.replace(f"PRODUCT_BUNDLE_IDENTIFIER = {current};", f"PRODUCT_BUNDLE_IDENTIFIER = {new};")
        old_group, new_group = f"group.{current}", f"group.{new}"
        for p in ENTITLEMENTS:
            write(p, read(p).replace(old_group, new_group))
        shared = read(SHARED)
        shared = re.sub(r'static let appGroup = "[^"]+"', f'static let appGroup = "{new_group}"', shared)
        write(SHARED, shared)
        print(f"Bundle ID: {current} -> {new}  (widgets: {new}.widgets, App Group: {new_group})")

    if args.team is not None:
        team = args.team.strip()
        gen = re.sub(r'DEVELOPMENT_TEAM = "?[A-Z0-9]*"?;', f'DEVELOPMENT_TEAM = "{team}";', gen)
        print(f"Team: {team or '(none)'}")

    if args.server:
        gen = re.sub(r'STUDYSPOT_SERVER_URL = "[^"]*";', f'STUDYSPOT_SERVER_URL = "{args.server.rstrip("/")}";', gen)
        print(f"Default server: {args.server}")

    if args.web:
        gen = re.sub(r'STUDYSPOT_WEB_URL = "[^"]*";', f'STUDYSPOT_WEB_URL = "{args.web.rstrip("/")}";', gen)
        print(f"Default website: {args.web}")
    elif args.server:
        # One address serves the API and the website (e.g. Docker).
        gen = re.sub(r'STUDYSPOT_WEB_URL = "[^"]*";', 'STUDYSPOT_WEB_URL = "";', gen)
        print(f"Default website: same as server ({args.server})")

    if args.push is not None:
        app_ent = ENTITLEMENTS[0]
        s = read(app_ent)
        block = "\t<key>aps-environment</key>\n\t<string>development</string>\n"
        s = s.replace(block, "")
        if args.push:
            s = s.replace("</dict>\n</plist>", block + "</dict>\n</plist>")
        write(app_ent, s)
        print("Push Notifications:", "on (aps-environment)" if args.push else "off")

    write(GEN, gen)
    subprocess.run([sys.executable, GEN], cwd=IOS, check=True)
    print("Done. Open StudySpot.xcodeproj in Xcode.")


if __name__ == "__main__":
    main()
