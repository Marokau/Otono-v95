# Kerning City Party Quest development notes

The crew uses Kinoko's character movement, combat, and party systems. It collects coupons and passes, takes positions for the puzzles, and fights the final boss while the human leads stage progression.

The extra Curse Eyes and duplicate King Slime came from resetting initially populated rooms during entry. Preserving the existing monster instances resolved both issues in the client audit. Reward selection now reads the box chosen by the client and preserves the first accepted choice.

For Magic Claw, the server already sent both hit values. The observing client skipped nearby hits in its remote magic playback path. The bundled DLL adjusts that check for Magic Claw, preserving the native rendering of the individual hits. The in-game audit confirmed close and ranged visuals. For similar issues, compare working and failing outgoing packets before changing damage calculations or client playback.
