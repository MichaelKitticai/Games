# Game Psychology Research Notes

Goal: a simple, addictive Candy Crush style game. Learn the user behavior first, build later.

## Target player

- Plays in 1 minute or less (meetings, bus stops, elevator)
- No rules to learn, no setup, no commitment
- Offline, one hand
- Easy enough for a 55-year-old and a 12-year-old
- Matching colors, puzzle
- Every hit gives feedback: vibration first, then sound and visuals. Bigger wins, bigger feedback.

## Books

**Habit and hooks**
1. Hooked - Nir Eyal (start here)
2. Addiction by Design - Natasha Dow Schüll (gambling, near misses, the "machine zone")
3. Thinking, Fast and Slow - Daniel Kahneman (loss aversion, sunk cost)
4. The Power of Habit - Charles Duhigg

**Game design**
5. Getting Gamers - Jamie Madigan
6. The Art of Game Design: A Book of Lenses - Jesse Schell
7. A Theory of Fun for Game Design - Raph Koster
8. Game Feel - Steve Swink
9. Flow - Mihaly Csikszentmihalyi

**Mobile / free-to-play**
10. Free-to-Play - Will Luton
11. Evil by Design - Chris Nodder
12. Actionable Gamification - Yu-kai Chou

Suggested order: Hooked, Sapolsky video, Addiction by Design, King GDC talk, Juice It or Lose It.

## Videos

**Conditioning and dopamine**
- Sapolsky, Dopamine Jackpot: https://www.youtube.com/watch?v=axrywDP9Ii0
- Schedules of reinforcement: https://www.youtube.com/watch?v=HOB-jaDk8M8
- Extra Credits, The Skinner Box: https://www.youtube.com/watch?v=tWtvrPTbQ_c
- Extra Credits, Avoiding Skinner Boxes: https://www.youtube.com/watch?v=S5camMoNw-o
- RagnarRox, Designed to Addict: https://www.youtube.com/watch?v=G05_icgcrOs

**Gambling**
- Schüll, Slot Machines: Addiction by Design: https://www.youtube.com/watch?v=ETB0x2UU6JE
- Schüll, 2023 talk: https://www.youtube.com/watch?v=GUeTnQeIFCE
- How Casinos Are Designed to Addict You: https://www.youtube.com/watch?v=gVbs3WERchk

**Hook model**
- Nir Eyal, CXL Live: https://www.youtube.com/watch?v=Rif0KTzO6iQ
- Hooked in 6 minutes: https://www.youtube.com/watch?v=SdnWbPD0-CM

**Candy Crush**
- King GDC, Level Design Saga: https://www.youtube.com/watch?v=LuNH9Rz2e2k
- Why Candy Crush Is So Addictive: https://www.youtube.com/watch?v=kORDVsYYJpg
- 3 Business Reasons: https://www.youtube.com/watch?v=u6p9P-4AolA
- Why is Candy Crush so addicting?: https://www.youtube.com/watch?v=zsMrn037RwQ

**Game feel / juice**
- Juice It or Lose It: https://www.youtube.com/watch?v=Fy0aCDmgnxg
- GMTK, Secrets of Game Feel and Juice: https://www.youtube.com/watch?v=216_5nu4aVQ

**Dark patterns**
- Are Your Games Playing You?: https://www.youtube.com/watch?v=OCkO8mNK3Gg
- Dark Patterns in Gaming: https://www.youtube.com/watch?v=jB2z9ceEEtw

## How Candy Crush uses psychology

| Concept | In Candy Crush |
|---|---|
| Variable ratio reward | Random cascades and combos |
| Near miss | Losing with 1 or 2 jellies left |
| Loss aversion | "Buy 5 moves or lose your progress" |
| Flow / difficulty curve | Easy, hard wall, easy again |
| Appointment mechanics | Lives refill over time |
| Juice | Sounds, "Sweet!", screen effects |
| Visible progress | The map path |
| Low friction | One finger, 2 to 3 minute sessions |

## Games to study (fit for 50+ and kids)

| Game | The one rule |
|---|---|
| Block Blast! | Fit blocks, fill a line, it clears |
| Toon Blast / Toy Blast | Tap 2+ same-color cubes |
| Two Dots | Connect same-color dots |
| Ball Sort / Water Sort | One color per tube |
| Suika Game | Same fruits merge |
| Woodoku | Block Blast on a wooden grid, calmer |
| Bubble Shooter | Aim, pop 3+ same color |
| Bejeweled Blitz | Swap gems, 1-minute rounds (online only) |
| Solitaire / Mahjong | Top games for 50+, calm appeal |

**Download links (official apps, use these for vibration)**
- Toon Blast: [iOS](https://apps.apple.com/us/app/toon-blast/id1176027022) / [Android](https://play.google.com/store/apps/details?id=net.peakgames.toonblast)
- Block Blast!: [iOS](https://apps.apple.com/us/app/block-blast/id1617391485) / [Android](https://play.google.com/store/apps/details?id=com.block.juggle)
- Two Dots: [iOS](https://apps.apple.com/us/app/two-dots-connect-the-colors/id880178264) / [Android](https://play.google.com/store/apps/details?id=com.weplaydots.twodotsandroid)

**Testing tips:** turn on vibration and sound, don't explain anything, note the first tap, first smile, any frustration, and whether they say "one more" unprompted.

## Designing for both ages

| 55-year-old needs | 12-year-old needs | Design answer |
|---|---|---|
| Big, clear pieces | Fast action | Large tiles, animations under 0.3s |
| No time stress | Excitement | Relaxed mode and rush mode |
| Readable colors | Bright colors | Strong colors plus shapes (colorblind safe) |
| No tutorial | No patience | First move teaches the rule |
| Not childish | Not boring | Clean, cozy look: fruit, gems, candy |
| Calm | Explosive | Feedback grows with skill |

## Feedback ladder

| Event | Vibration | Sound | Visual |
|---|---|---|---|
| Tap a piece | Tiny tick (10ms) | Soft click | Piece squishes |
| Small match | Light tap | Pop | Particle burst |
| Bigger match / combo | Medium double tap | Higher pop | Bigger burst, small shake |
| Chain reaction | Rising buzz | Notes rising up the scale | "Nice!", "Great!" |
| Target hit | Strong pulse | Chime | Confetti |
| Round won | Long celebration pattern | Fanfare | Full-screen celebration |
| Lost | None | Gentle "aww" | "So close!" with how close |

## Current direction

Tap-to-pop (like Toon Blast / Collapse):
- One rule: tap a group of same colors
- One finger, every tap gives a pop and a buzz
- Bigger groups, bigger explosions
- 60-second rounds or short goals, endless score chasing

Next step: write the feature list and map each feature to these principles.
