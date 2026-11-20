# Otono v95 development journal

This journal records fixes that changed gameplay or client behavior and how they were verified. Individual activity notes include implementation details.

## v0.2.0 · Kerning City Party Quest

### Magic Claw at point-blank range

**Bug.** The crew mage dealt damage at close range, but the observing client showed neither damage numbers nor the monster flinch. Ranged casts displayed correctly.

**Cause.** The attack packet already carried both hits. The client checked a point 50 units in front of the caster against the near edge of the monster. At close range the check skipped hit playback.

**Fix.** The Otono client DLL exempts Magic Claw from that near-edge check for valid target rectangles, so both hits follow the native playback path.

**Verified.** The in-game audit confirmed point-blank and ranged visuals. Initial Setup and Repair Setup both installed the audited DLL. A separate in-game test from the publication folder also confirmed point-blank Magic Claw displays correctly.

**Debugging lesson.** Compare the outgoing packets from working and failing casts first. If both carry the expected hits, follow the client playback path before changing server combat.

### Extra Curse Eyes and duplicate King Slime

**Bug.** Stage Four showed extra Curse Eyes, and the final stage showed a second King Slime that players could see but could not hit.

**Fix.** Preserve monsters already spawned when players enter a stage. Populate the stage only when needed.

**Verified.** The in-game retest showed no ghost Curse Eyes and one King Slime.

### Reward box selection

**Bug.** Clicking a different reward box still selected the bottom-left box.

**Fix.** Decode the chosen box from the client response and keep the first accepted choice for that player.

**Verified.** The final in-game audit confirmed reward box selection works.

### Crew movement, knockback, and coupon pickup

- **Stage One movement stall.** The bot and map lookup could disagree about which foothold owned a shared endpoint. Routing now starts from the bot's current foothold when valid.
- **Knockback through platforms.** Landing now checks for the first foothold crossed during descent. A client retest found knockback improved and bots stayed on the platform in the observed cases.
- **Coupon chase and pickup.** The crew cancels its route when the coupon disappears and avoids sending more than one bot after the same drop. The final in-game audit confirmed KPQ completion. Each movement case was not individually rechecked in that run.

See [KPQ implementation notes](KPQ.md) for activity-specific details.

## v0.2.1 · Launcher and interim progression

The launcher gained an inline guide, clearer status text, and a more compact layout. Its helper scripts moved under launcher while Otono.cmd remains the entry point.

The GM level command could leave EXP from the old level displayed on the bar. Changing the level now resets EXP and sends the new level and EXP together.

The interim advancement guide uses the native stat-grant routine for missing paths. Client checks covered first-job Thief and early Dual Blade, including 62 unspent SP at level 30 under current rules when leveled through the normal routine. Later advancement awards and quest-dependent skill unlocks still need auditing.
