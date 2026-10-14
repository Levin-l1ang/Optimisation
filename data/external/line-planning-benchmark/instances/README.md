# Instances

This folder contains one subfolder per network. Each subfolder has the same structure, described below.

## Network subfolders

| Subfolder | Network |
|---|---|
| `Randstad-IC` | Netherlands — Randstad region, Intercity only |
| `Randstad-IC-SPR` | Netherlands — Randstad region, Intercity + Sprinter |
| `Netherlands-IC` | Netherlands — full network, Intercity only |
| `Netherlands-IC-SPR` | Netherlands — full network, Intercity + Sprinter |
| `Switzerland-Small` | Switzerland — partial network |
| `Switzerland-Large` | Switzerland — full network |

## File descriptions

### `stations.txt`

One station per line:

```
id shortName fullName isIC isSPR numPlatforms isTerminal lon lat
```

| Column | Type | Description |
|---|---|---|
| `id` | int | Integer station index (used in `# LinePlan` section of solution files) |
| `shortName` | string | Abbreviated name (used as key in all other files) |
| `fullName` | string | Full station name |
| `isIC` | bool | Whether InterCity trains may stop here |
| `isSPR` | bool | Whether Sprinter trains may stop here |
| `numPlatforms` | int | Number of platforms |
| `isTerminal` | bool | Whether the station is a terminal (line endpoint) |
| `lon` | double | Longitude |
| `lat` | double | Latitude |

### `connections.txt`

A *connection* is a directed edge between two adjacent stations in the public transport network. Each connection has a corresponding reverse connection. One connection per line:

```
id fromShort toShort length travelTime maxFrequency type reverseId [trackId ...]
```

| Column | Type | Description |
|---|---|---|
| `id` | int | Connection index |
| `fromShort` | string | Departure station short name |
| `toShort` | string | Arrival station short name |
| `length` | double | Route distance in km |
| `travelTime` | double | Travel time in minutes |
| `maxFrequency` | int | Maximum number of trains per hour |
| `type` | int | Train type: 0 = SPR only, 1 = IC only, 2 = IC and SPR |
| `reverseId` | int | ID of the reverse connection (same pair, opposite direction) |
| `trackId ...` | int* | Ordered list of track segment IDs forming this connection |

### `tracks.txt`

A *track* is a directed physical rail segment between two stations. One track per line:

```
id fromShort toShort length speedLimit travelTime numTracks
```

| Column | Type | Description |
|---|---|---|
| `id` | int | Track segment index |
| `fromShort` | string | From-station short name |
| `toShort` | string | To-station short name |
| `length` | double | Length in km |
| `speedLimit` | int | Maximum speed in km/h |
| `travelTime` | double | Travel time in minutes |
| `numTracks` | int | Number of parallel tracks (capacity) |

### `forbiddenTurns.txt`

Each line lists two track IDs that cannot be traversed consecutively (physically impossible turn):

```
trackId1 trackId2
```

### `allowedTypeChanges.txt`

*(IC+SPR networks only)* Each line describes a permitted IC↔Sprinter type change at a junction, listing the three stations involved (short names). Empty in IC-only networks.

### `hasICs.txt` / `hasSPRs.txt`

Single line: `true` or `false`. Indicates whether Intercity / Sprinter lines are used in this network.

### `ODMatrix/dense.txt` and `ODMatrix/sparse.txt`

Demand matrix. One OD pair per line:

```
originShort destinationShort volume
```

`dense` is derived from a raw gravity model. `sparse` is a sparsified version obtained via OD splitting and through-station aggregation, which reduces the number of OD pairs while preserving total demand.

### `transferPenalty/small.txt`, `medium.txt`, `large.txt`

Single integer: the transfer penalty in minutes (5, 15, or 25).

### `budget/small.txt`, `medium.txt`, `large.txt`

Single integer: the line operation budget for the *medium* fixed cost level.

### `budget-fixedCost-low/` and `budget-fixedCost-high/`

Same structure as `budget/`. The budget values are re-calibrated for the low and high fixed cost levels so that the effective constraint tightness is comparable across fixed cost settings.

### `fixedCost/low.txt`, `medium.txt`, `high.txt`

Single integer: the fixed cost charged per activated line (0, medium value, or high value).

### `LineGeneration/small.txt`, `medium.txt`, `large.txt`

Two lines:
```
detourRatio
maxLineDurationMinutes
```

`detourRatio` controls the maximum allowed route detour (1.1 / 1.3 / 1.5); larger values yield larger line pools. `maxLineDurationMinutes` caps the end-to-end travel time of any generated line (always 60 minutes in this benchmark).

### `RouteGeneration/small.txt`, `medium.txt`, `large.txt`

Single integer: the maximum passenger route detour in minutes (0, 15, or 30). Used by route-based solvers (PTN, PTN-DC, DPTN-MIP) to enumerate feasible passenger routes.
