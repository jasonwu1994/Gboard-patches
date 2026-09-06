<h1 align="center">Gboard Patches</h1>

<p align="center">
  Morphe patches for Gboard with a mix of global improvements and Taiwan-focused enhancements.
</p>

<p align="center">
  <a href="https://github.com/jasonwu1994/Gboard-patches/releases/latest"><img alt="Latest release" src="https://img.shields.io/github/v/release/jasonwu1994/Gboard-patches?display_name=tag&label=Release&style=for-the-badge"></a>
  <a href="https://github.com/jasonwu1994/Gboard-patches"><img alt="Total downloads" src="https://img.shields.io/github/downloads/jasonwu1994/Gboard-patches/total?label=Downloads&style=for-the-badge"></a>
  <a href="https://morphe.software/add-source?github=jasonwu1994/Gboard-patches"><img alt="Add to Morphe" src="https://img.shields.io/badge/Morphe-Add%20Source-00A8FF?style=for-the-badge"></a>
  <a href="https://github.com/jasonwu1994/Gboard-patches"><img alt="GitHub stars" src="https://img.shields.io/github/stars/jasonwu1994/Gboard-patches?style=social"></a>
</p>

<p align="center">
  <a href="https://ko-fi.com/jasonwu1994"><img height="40" alt="Buy me a coffee on Ko-fi" src="https://storage.ko-fi.com/cdn/kofi5.png?v=3"></a>
</p>

## Overview

Gboard Patches is a public Morphe source for a curated set of Gboard enhancements, including both global usability improvements and Taiwan-focused features for local input habits and day-to-day use.

## Included Patches

### Project-Built Features

Features designed and built by this project rather than simply unlocking an existing Gboard flag.

<details>
  <summary><code>Clipboard Enhancements</code></summary>

  Lets you enhance clipboard retention time, item count limits, preview lines, countdown and creation-time labels, order index, grid columns, and optionally render only the first 1,000 characters on each clipboard card.
</details>

<details>
  <summary><code>Web Clipboard</code></summary>

  Hosts a phone-powered Web Clipboard portal that lets desktop browsers sync with Gboard over the same LAN, with a pairing code gate and an optional Quick Settings Tile.

  Preview:

  <img alt="Web Clipboard pairing gate" src="docs/assets/features/web-clipboard/01-pairing-gate.png" width="720">

  <img alt="Web Clipboard conversation view" src="docs/assets/features/web-clipboard/02-conversation-view.png" width="720">

</details>

<details>
  <summary><code>Floating Web Search</code></summary>

  Open a floating web page directly from Gboard to quickly search for the information you need.
</details>

<details>
  <summary><code>FTP Server</code></summary>

  Hosts an FTP server on your phone so desktop FTP clients can browse, upload, download, and resume file transfers over the same LAN. It supports anonymous or password-protected access, a configurable control and passive port range, read-only mode, <code>/sdcard</code> or a user-selected folder as the root, live transfer progress, retained partial uploads, and an optional Quick Settings Tile.
</details>

<details>
  <summary><code>Long-Press Editing Shortcuts</code></summary>

  Add Select all, Undo, Copy, Cut, Paste, and Redo long-press shortcuts to English QWERTY and Zhuyin, with an optional globe-key drag gesture that follows the same physical key positions across supported alphabet layouts.
</details>

<details>
  <summary><code>Swipeable Custom Top Row</code></summary>

  Lets you swipe the keyboard top row horizontally to open customizable text and JavaScript slots.
</details>

<details>
  <summary><code>Incognito Mode Toggle</code></summary>

  Add an Incognito toggle to the Access Point toolbar and configure clipboard and voice typing availability while Incognito mode is active.
</details>

<details>
  <summary><code>Custom Symbols</code></summary>

  Adds a dedicated symbols tab and a quick access entry from the comma long-press popup.
</details>

<details>
  <summary><code>Simple Calculator</code></summary>

  Adds an optional inline calculator for arithmetic expressions typed in any text field. The result appears in Gboard's suggestion row; tap it to replace the expression, or long-press it to copy the result.
</details>

<details>
  <summary><code>G Logo on Spacebar</code></summary>

  Show the G Logo on the spacebar and hide the language label.
</details>

<details>
  <summary><code>Rounded Keyboard Panel</code></summary>

  Customize which corners of the keyboard panel are rounded, and set the top and bottom radii separately.
</details>

<details>
  <summary><code>Latin Globe Key Ignore Interval</code></summary>

  Add an independent English globe key ignore interval override for post-typing language-switch delay.
</details>

<details>
  <summary><code>Emojis, stickers & GIFs Tab Order</code></summary>

Customize the bottom tab order in Gboard's Emojis, stickers & GIFs panel with drag-and-drop reordering.
</details>

<details>
  <summary><code>Backup &amp; Restore</code></summary>

  Exports all Gboard Patches settings to a portable JSON backup and restores only the modules you select, with per-module and per-key results. It also exports, compares, and restores Gboard's raw PB/XML flag-store files.
</details>


### Gboard Feature Unlocks

Features already present in Gboard that are exposed by enabling hidden settings, rollout gates, or built-in behavior.

<details>
  <summary><code>AI Writing Tools</code></summary>

  Enables the <code>Text correction &gt; Writing tools</code> setting with support for all languages.
</details>

<details>
  <summary><code>Advanced Voice Typing</code></summary>

  Enable Advanced Voice Typing with automatic punctuation, and separately enable automatic punctuation for Traditional Chinese voice typing, which does not support Advanced Voice Typing.
</details>

<details>
  <summary><code>Enable OCR / Scan Text</code></summary>

  Enable the OCR / Scan Text feature with Latin, Chinese, Japanese, Korean, and Devanagari recognition backends.
</details>

<details>
  <summary><code>English QWERTY Up-Flick Uppercase</code></summary>

  Flick up on the English QWERTY keyboard to toggle uppercase and lowercase.
</details>

<details>
  <summary><code>Enable Inline Autofill Suggestions</code></summary>

  Enables inline autofill suggestions in supported contexts.
</details>

<details>
  <summary><code>Grammar Checker</code></summary>

  Enables the <code>Text correction &gt; Grammar check</code> setting and its related rollout gate.
</details>

<details>
  <summary><code>Inline Suggestions</code></summary>

  Enables the <code>Text correction &gt; Smart Compose</code> setting and its related rollout gate.
</details>

<details>
  <summary><code>Key Shape Selection</code></summary>

  Enables the <code>Key shape</code> option inside theme details without forcing rounded keys by default.
</details>

<details>
  <summary><code>Use Bluetooth Microphone</code></summary>

  Enables the <code>Voice typing &gt; Use Bluetooth microphone</code> setting and its related rollout gate.
</details>

<details>
  <summary><code>Change emoji size</code></summary>

  Enables Gboard's emoji size setting.
</details>

<details>
  <summary><code>Enable cursor trackpad mode</code></summary>

  Enables the long-press-spacebar trackpad, cursor lock mode, and the required scrub-move preference.
</details>

<details>
  <summary><code>Enable split keyboard</code></summary>

  Enables Gboard's split keyboard layout.
</details>

<details>
  <summary><code>Enable accessibility layout</code></summary>

  Enables accessibility layout.
</details>

<details>
  <summary><code>Quick Insert</code></summary>

Enables the Quick Insert panel and toolbar access point.
</details>

<details>
  <summary><code>Hyperspeed Typing Animation</code></summary>

Shows the animation during sustained fast typing with support for all keyboards.
</details>

<details>
  <summary><code>Close Proactive Suggestions</code></summary>

Shows a dismiss button in the proactive suggestions bar.
</details>

<details>
  <summary><code>Clipboard Custom Character Limit</code></summary>

  Lets you set the maximum number of characters stored for each text clipboard item, with Gboard's stock 20,000-character limit as the default.
</details>

<details>
  <summary><code>Access Points menu style</code></summary>

  Lets you switch between the new and legacy Access Points menu styles.
</details>

<details>
  <summary><code>Top Toolbar Item Count</code></summary>

  Lets you customize the top toolbar item count.
</details>

<details>
  <summary><code>Settings Homepage Override</code></summary>

  Lets you switch between the new and legacy Gboard settings homepage styles.
</details>

<details>
  <summary><code>Developer options</code></summary>

  Enable Developer options and the Flag Editor, allowing you to modify flag values.
</details>

<details>
  <summary><code>Package Rename</code></summary>

  Renames the patched package so it can be installed alongside the official Gboard app.
</details>

### Taiwan-focused Features

Features tailored to Traditional Chinese and Zhuyin input workflows.

<details>
  <summary><code>Zhuyin Slide Input</code></summary>

  On the Zhuyin keyboard, swipe up or down to enter English letters without switching to another keyboard layout.
</details>

<details>
  <summary><code>Zhuyin Quick Traditional/Simplified Toggle</code></summary>

  Swipe up on the Zhuyin <code>ㄥ</code> key to quickly toggle between Traditional and Simplified Chinese.
</details>

<details>
  <summary><code>Zhuyin Bottom Row Key Sizes</code></summary>

  Adjusts the seven bottom-row slot sizes on the Zhuyin keyboard, including <code>?123</code>, <code>，</code>, the globe key, space, <code>ㄦ</code>, backspace, and the IME action key.
</details>

## Install

Add this repository as a Morphe source:

- [Open in Morphe](https://morphe.software/add-source?github=jasonwu1994/Gboard-patches)
- Or manually add `https://github.com/jasonwu1994/Gboard-patches`

## Build

Before running Gradle locally, authenticate to Morphe's GitHub Packages registry with either:

- `gpr.user` and `gpr.key` in `~/.gradle/gradle.properties`
- `GITHUB_ACTOR` and `GITHUB_TOKEN` as environment variables

Build the Android patch bundle:

```powershell
.\gradlew.bat :patches:buildAndroid
```

Regenerate patch metadata:

```powershell
.\gradlew.bat generatePatchesList
```

Generated outputs:

- `patches/build/libs/*.mpp`
- `patches-list.json`
- `patches-bundle.json`

## License

Released under the [GNU General Public License v3.0](LICENSE).
