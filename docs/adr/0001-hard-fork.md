# Hard fork of EssentialKeyTools

This project is a hard fork of KoukeNeko/EssentialKeyTools: it has its own `applicationId` and
Kotlin package, and it does not track upstream. It merges features from
THE-TECH-CHANGER/NothingButtonRemapper and drops others (per-app Profiles replace a flat gesture
map, and NothingButtonRemapper's keyboard-based key mapper is left out). These changes go deep
enough that regularly merging upstream would cost more than it gives back. Both upstreams are MIT
licensed, and their copyright notices are kept.

## Consequences

- Upstream fixes are cherry-picked by hand, if at all.
- The update checker, issue links, privacy link and repository links point at this fork, not upstream.
- Distribution is GitHub releases only, mainly for the author's own use. So the name may include
  "Nothing", and Play's accessibility review does not apply.
