---
title: Ranged Weapons — decisions log
type: index
layer: store
tags: [index]
---

# Ranged Weapons — decisions log

**Append-only.** Never edit an entry's rationale; supersede via a new entry with
`supersedes: D-NNNN`. Every entry has a status: `Active` / `Superseded` /
`Rejected`.

A decision note is warranted when the call took more than five minutes of
thought and someone later would want to know *why*.

| Id | Topic |
|----|-------|
| D-0001 | Capability beats profile; `resolve` is one function, not a provider chain |
| D-0002 | The fallback tier lives inside the protocol mod |
| D-0003 | AGPL-3.0-or-later on the protocol itself, not LGPL |
| D-0004 | Profiles reference items and sounds by id; the loud failure is restored at reload |
| D-0005 | Bullets break blocks by hardness, players by default, mobs only if the server says |
| D-0006 | Guns opt out of Hold My Items by writing its config, until it takes a tag |
