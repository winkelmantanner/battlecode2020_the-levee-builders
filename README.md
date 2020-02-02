I spent a month coding this bot that plays the Battlecode 2020 game.  Battlecode is an AI programming competition put on by MIT.  I think Missouri S&T's MegaMiner AI programming competition was inspired by Battlecode.

This bot was a finalist (in the top 16) and it came out in the 5th-6th level in the final tournament.  Those are global ranks, and it means this bot is one of the top bots in the world.  This bot was known as the-levee-builders in the tournaments.  You can watch the tournaments on [Battlecode's twitch](https://www.twitch.tv/mitbattlecode).

Here are some things about this bot:

- The landscapers run around the HQ to get to the lowest map location adjacent to the HQ.  The landscapers keep the elevation changes of the 8 spaces around the HQ pathable, so they can move around the HQ.  Most other bots didn't seem to keep these elevation changes pathable.

- The delivery drones drop enemy units in pits.  The variable `pit_max_elevation` is the maximum elevation that is considered a pit.  It is computed as `(elevation of HQ) - 10`.

- On turn 1300, the miners become more likely to build fulfillment centers and the fulfillment centers become more likely to build drones.

- The miners like to stand on top of soup while they mine it.  This makes it easier for other miners to see that they can path to where they can reach the soup.

- The biggest improvement I made during the entire coding of this bot was [upping the number of miners to build from 3 to 7](https://github.com/winkelmantanner/battlecode2020_the-levee-builders/commit/121e1f3b5a7b334dda776576f4783eace99d7123).  With only 3 miners, the bot only acheived exponential vaporator growth on about 10% of maps.  With 7 miners, the bot acheives exponential vaporator growth on about 80% of maps (unless it is interfered with by the opponent bot).


