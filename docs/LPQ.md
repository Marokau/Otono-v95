# Ludibrium Party Quest development notes

## Entry and progression

The Red Sign at Eos Tower 101st Floor registers a party of 3–6 players, levels 30–200. A solo player can assemble a Fighter, an Ice/Lightning Wizard, and a Hunter through the sign's BotPQ menu. The player leads the stage checks. The crew collects the ten shared box passes, splits the five dark rooms, and occupies the equation platforms. The fifteen-row maze follows the map's portal destinations. Its chat hint uses the painted box labels, read from the bottom up.

## Boss and rewards

Alishar is present when the party enters the boss room. Defeating him unlocks the reward boxes. His magic attack, Seal, Darkness and three summon levels come from the installed v95 data. The skill levels share a summon ID but have separate HP requirements. Regression tests check the eligibility boundary for each level, its resulting monsters, and the attack delivered to a crew member while a human observes.

Each player can claim one box. The 7,000 completion EXP is awarded once on exit through the Violet Balloon, separately from Alishar's combat EXP. July 2011 GMS footage shows that award and a Sniper Potion reward. Other prize entries, their weights and potential level scaling remain provisional. February and July GMS footage informed the stage dialogue, with period regional recordings used for gaps. The boss banner uses Otono wording to describe the observed clear condition.

The in-game audit confirmed a full clear, incoming damage and Alishar's summons.
