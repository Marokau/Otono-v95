# Included Kinoko launcher binaries

Unmodified official release assets from iw2d/kinoko_client, version **v95.1.7**:

https://github.com/iw2d/kinoko_client/releases/tag/v95.1.7

- Kinoko.exe: the custom launcher.
- Kinoko.dll: the matching client modification.

These are included so normal server setup does not require a C++ compiler or a second repository download. Initial-Server-Setup.cmd copies them beside the detected MapleStory.exe, in game/ or game/MapleStory/. They do not include MapleStory.exe, supporting game files, WZ assets, or a saved database.

SHA256SUMS.txt records the downloaded files' SHA-256 checksums. Each was checked against the release asset digest supplied by the GitHub API when imported.

The client source baseline under client/ is separately recorded in docs/BASELINE.md and is newer than this release. Rebuilding that source may produce a different client version. Client source builds are optional developer work; see the root README. No client build script or C++ tools are needed for normal setup.



