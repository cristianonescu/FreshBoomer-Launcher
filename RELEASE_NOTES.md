# v1.2.1 Release Notes

## Phone Screen Compatibility
- Added responsive font scaling for phone screens (< 600dp width). Text automatically scales down proportionally on smaller devices while keeping the original large sizes on tablets.
- Expanded the app typography system from 4 styles to a full set of 15 Material3 typography levels, replacing ~80 hardcoded font sizes across 11 files with theme references for consistent, centralized control.
- Fixed confirm call dialog buttons overflowing on small screens: reduced button height, icon size, spacing, and contact photo size.

## Privacy Policy
- Added a privacy policy page at `app/src/main/assets/privacy-policy.html` covering permissions, local data storage, and network usage. Displayed in-app via WebView (works offline).

## Config Editor Hints
- Added an informational hint card in both the Settings screen and the JSON Config Editor, pointing users to the online config editor and explaining that the config JSON must be self-hosted for remote import.
- New string resources added in Romanian and English.

## App Icon
- Replaced the app launcher icon with a new design (adaptive icon with PNG assets).

## Version
- Bumped versionCode to 4, versionName to 1.2.1.
