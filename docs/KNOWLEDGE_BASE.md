# Knowledge Base

Knowledge is stored outside code under `knowledge/`.

## Files

- `knowledge/schema/rule.schema.json`
- `knowledge/rules/*.json`
- `knowledge/encyclopedia/*.json`
- `knowledge/profiles/*.json`
- `knowledge/pids/*.json`

## Rule Fields

Every rule must contain:

- `id`
- `title`
- `category`
- `conditions`
- `probability`
- `physicalExplanation`
- `symptoms`
- `possibleCauses`
- `recommendedChecks`
- `repairRecommendations`
- `confidence`
- `severity`
- `sources`

## Rule Evaluation

Rules should not make conclusions from one parameter unless the condition is explicitly about a single confirmed DTC. Normal drivability diagnosis must combine multiple signals, for example:

- fuel trims
- MAF/MAP
- RPM/load
- throttle
- lambda/O2
- fuel pressure
- ignition timing
- knock
- temperature
- DTCs

## Source Policy

Each rule must list sources. Allowed sources:

- open standards summaries
- government/public documentation
- open technical articles with compatible terms
- manufacturer public datasheets/manuals
- project-authored engineering derivations

Paid/proprietary OEM data must not be copied into the repository.
