# Breach Protocol: an addon for SCP Unleashed

A NeoForge **1.21.1** addon for [SCP Unleashed](https://www.curseforge.com/minecraft/mc-mods/scp-unleashed) by Raikin.
**SCP Unleashed must be installed.** Breach Protocol doesn't add SCPs. It adds a Foundation career layer on top of
Raikin's anomalies, items and systems.

Raikin has said addons are welcome. This addon contains none of SCP Unleashed's code or assets. It refers to their
content by registry ID and calls their public classes at runtime. SCP concepts come from the
[SCP Foundation wiki](https://scp-wiki.wikidot.com) (CC BY-SA 3.0).

## Features

- **Career ladder:** Class-D through Level 1-5, then O5-13 "The Tiebreaker", then The Administrator.
  - You earn XP by recontaining or terminating SCP Unleashed anomalies, and by completing O5 assignments.
  - The O5 Council messages and orders you until you take the top seat. After that they call you and ask what to do.
- **Foundation Phone:** a smartphone with Back, Home and Recents buttons. You only see the apps your clearance allows.
  - *Messages, Missions, Phone:* Council orders, assignments, and calls that ask you to decide.
  - *Camera and Gallery:* photograph anomalies.
    - A photo of SCP-096's face is a cognitohazard. Whoever opens it, prints it, or looks at it framed triggers SCP Unleashed's 096 against themselves.
    - From the Gallery you can delete, print, or send photos to a nearby player.
  - *Archive:* redacted files on Unleashed's SCPs. Sections above your clearance stay blacked out.
  - *Containment:* live status of anomalies within 64 m.
  - *Settings:* **fuse a keycard into your phone.** It consumes an SCP Unleashed keycard from your inventory, and the phone keeps that level permanently.
  - *Command (Level 4+):*
    - call SCP Unleashed MTF squads by threat tier
    - request squad reports and extraction
    - give Follow, Hold or Release orders to nearby D-Class
  - *Network (Level 5+):* SCP Unleashed's orbital array status, with strikes at your crosshair, plus the facility network hub count.
  - *Site-01 (Administrator):* travel to your secret site.
- **Clearance:** the highest of your carried SCP Unleashed keycard, your fused card, and your rank. O5 and above count as Level 6.
- **Containment Field Generator:** freezes Unleashed anomalies in place.
- **Site-01 and Ascension:** the Administrator's generated sanctum.
  - Offer SCP Unleashed's SCP-500 at the Ascension Altar to become SCP-Ω.
  - After Ascension, anomalies stop targeting you and the Gate Guardian kneels.

## Building

```
./gradlew build     # build/libs/breachprotocol-<version>.jar
```
Requires JDK 21. CI builds every push and publishes the jar as the `latest` release.
