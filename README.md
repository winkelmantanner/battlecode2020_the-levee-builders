I spent a month of my life coding this bot that plays the Battlecode 2020 game.  Battlecode is an AI programming competition put on by MIT.  I think Missouri S&T's MegaMiner AI programming competition was inspired by Battlecode.

This bot was a finalist.  That means it did well enough in the Battlecode tournaments to be in the top 16 bots in the world.  This bot was known as the-levee-builders.

Battlecode was fun.  Its interesting how fast code came out of my fingertips during Battlecode.  When I try to get a technology to work, it takes a long time before I can get any working code at all.  But in Battlecode, I just wrote code and the code usually ended up working (after some debugging).  This is probably because Battlecode's coding interface was very user-friendly.  Battlecode's code is [very well documented](https://2020.battlecode.org/javadoc/index.html).  It would be nice if all technologies were like this.

Here are some things about this bot:

- The landscapers run around the HQ to get to the lowest map location adjacent to the HQ.  The landscapers keep the elevation changes of the 8 spaces around the HQ pathable, so they can move around the HQ.  Most other bots didn't seem to keep the elevation changes pathable.

- The delivery drones drop enemy units in pits.  The variable `pit_max_elevation` is the maximum elevation that is considered a pit.  It is computed as `(elevation of HQ) - 10`.

- On turn 1300, the miners become more likely to build fulfillment centers and the fulfillment centers become more likely to build drones.

- The biggest improvement I made during the entire coding of this bot was upping the number of miners to build from 3 to 7.  With only 3 miners, the bot only acheived exponential vaporator growth on about 10% of maps.  With 7 miners, the bot acheives exponential vaporator growth on about 80% of maps.


