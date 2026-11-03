---
name: current-month
description: Returns the current month name in English
allowed-tools: Read, Bash
---

Execute `scripts/current-month.sh` using its advertised script tool, with no
arguments, to get the current month name in English. Do not generate substitute
shell commands or guess the month. Report a script failure rather than inventing
a result.

## Example

```
$ scripts/current-month.sh
May
```

Return the output as-is — a single capitalized English month name.