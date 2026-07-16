# Diagnostic Model

The engine combines:

- expert rules
- physical calculations
- probability scoring
- correlation checks
- missing-data analysis

## Core Derived Signals

- Fuel trim state:
  - combined trim = STFT + LTFT
  - high positive trims indicate ECU adding fuel.
  - high negative trims indicate ECU removing fuel.

- Air plausibility:
  - MAF expected from displacement, RPM, volumetric efficiency.
  - MAP plausibility from load/throttle/RPM.

- Power estimation:
  - air mass method
  - acceleration method
  - GPS/speed method
  - mass/aero model
  - calculated load/torque method

- Misfire plausibility:
  - DTCs P0300-P030x
  - rough RPM variation
  - fuel trims
  - O2/lambda fluctuation
  - ignition timing/knock behavior

- Boost plausibility:
  - requested vs measured boost when available
  - MAP under WOT
  - MAF/load mismatch
  - throttle closure
  - knock/timing retard

## Probability

Each hypothesis starts with a base rule probability. Evidence modifies it:

- strong matching evidence increases confidence.
- contradictory evidence lowers probability.
- missing critical data reduces confidence but should not fabricate a conclusion.

## Required Output

Each diagnosis result must explain:

- probability
- severity
- evidence
- contradictory evidence
- missing data
- physical explanation
- recommended next checks
- likely repairs
- sources

If there is not enough evidence:

> Недостаточно данных для достоверного диагноза.
