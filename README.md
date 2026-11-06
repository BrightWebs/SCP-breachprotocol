# Breach Protocol: an addon for SCP Unleashed

A NeoForge **1.21.1** addon for [SCP Unleashed](https://www.curseforge.com/minecraft/mc-mods/scp-unleashed) by Raikin.
**SCP Unleashed must be installed.** Breach Protocol doesn't add SCPs. It adds a Foundation career layer on top of
Raikin's anomalies, items and systems.

Raikin has said addons are welcome. This addon contains none of SCP Unleashed's code or assets. It refers to their
content by registry ID and calls their public classes at runtime. SCP concepts come from the
[SCP Foundation wiki](https://scp-wiki.wikidot.com) (CC BY-SA 3.0).

## Features

Checked against SCP Unleashed **0.0.5.2**.

- **Career ladder:** Class-D through Level 1-5, then O5-13 "The Tiebreaker", then The Administrator.
  - The O5 Council messages and orders you until you take the top seat. After that they call you for decisions.
  - Assignments use SCP Unleashed's anomalies: recover SCP-173, SCP-096, SCP-106 and others, keep SCP-999 company, terminate SCP-049-2.
- **Containment** uses SCP Unleashed's own building blocks. An anomaly counts as contained when either:
  - a working Forcefield is next to it, or
  - SCP Unleashed containment blocks (reinforced containment wall, HCZ/LCZ walls, containment doors, reinforced glass) box it in on four sides and above.
  - SCP-096 with the Containment Hood on counts too.
  - Anything in Unleashed's `stasis_immune` tag can't be held by Lockdown.
- **Clearance** follows SCP Unleashed's keycard rules: Level 1-5, Omni = 6. It's the highest of your carried keycard, your fused card, and your rank (O5 and above count as Omni).
- **Foundation Phone:** a smartphone with Back, Home and Recents buttons. You only see the apps your clearance allows.
  - *Settings:* fuse your best keycard into the phone. Hold the phone on an SCP Unleashed **Keycard Scanner** to swipe that card.
  - *Archive:* SCP Unleashed's own anomaly dossiers.
    - SCP Unleashed's clearance rule gates each file (Safe/Euclid need Level 2, Keter 3, Thaumiel 4, the rest 5).
    - Redacted passages stay black unless you hold Omni-level clearance.
  - *Camera and Gallery:* catching SCP-096's face counts as seeing it, using SCP Unleashed's own sighting check.
    - Opening that photo, or printing it as SCP Unleashed's Face-Down Photograph, goes through SCP Unleashed's own photo handling.
  - *Containment:* nearby anomalies, and whether each one is in a cell.
  - *Command (Level 4+):* call SCP Unleashed MTF squads.
    - It works like the MTF Signal Radio: a squad costs an Unstable Fragment of the matching rarity and shares the same cooldown.
    - Also request squad reports and extraction, and give Follow, Hold or Release orders to nearby D-Class.
  - *Network (Level 5+):* SCP Unleashed's orbital array (status, plus strikes at your crosshair) and the network hub count.
- **Council directives:** O5 can deploy the Nu-7 Strike Team immediately. The Administrator can deploy the Alpha-1 Detachment.
- **Site-01 and Ascension:** the Administrator's sanctum, built with SCP Unleashed cell walls and stocked with their gear.
  - Offer SCP-500 at the Ascension Altar to become SCP-Ω.
  - After that, anomalies won't target or hurt you, and SCP-001-1, the Gate Guardian, holds its blades for you.

## Building

```
./gradlew build     # build/libs/breachprotocol-<version>.jar
```
Requires JDK 21. CI builds every push and publishes the jar as the `latest` release.
