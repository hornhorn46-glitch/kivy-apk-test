# External Holdout 100 Evaluation

This report is a test-only external holdout. Cases are not used for training.

- Total cases: 100
- Exact: 81
- Partial/useful: 18
- Miss: 1
- Insufficient: 0
- Useful rate: 99.0%

| Case | Source | Oracle | Expected | App top | Outcome | Recommendation |
|---|---|---|---|---|---|---|
| EXT-TRANS-SLIP-01 | [skanyx-live-data](https://skanyx.com/blog/live-data-stream-analysis-guide) | RPM rises under throttle without speed gain; expected downstream drivetrain/transmission issue. | transmission_slip | exhaust_restriction (52%/31%) | miss | Проверить противодавление выпуска |
| EXT-RICH-01 | [motor-fuel-trim](https://www.motor.com/magazine-summary/fuel-trim-data-powerful-diagnostic-tool/) | Negative trims and rich DTC indicate excess fuel or low measured air. | injector_or_fuel_regulator_rich | rich_condition (88%/78%) | partial | Проверить давление и удержание давления топлива |
| EXT-RICH-02 | [obdcodes-cavalier-rich](https://www.obd-codes.com/forums/viewtopic.php?t=5662) | Negative trims and rich DTC indicate excess fuel or low measured air. | injector_or_fuel_regulator_rich | rich_condition (88%/78%) | partial | Проверить давление и удержание давления топлива |
| EXT-RICH-03 | [motor-fuel-trim](https://www.motor.com/magazine-summary/fuel-trim-data-powerful-diagnostic-tool/) | Negative trims and rich DTC indicate excess fuel or low measured air. | injector_or_fuel_regulator_rich | rich_condition (88%/78%) | partial | Проверить давление и удержание давления топлива |
| EXT-RICH-04 | [obdcodes-cavalier-rich](https://www.obd-codes.com/forums/viewtopic.php?t=5662) | Negative trims and rich DTC indicate excess fuel or low measured air. | injector_or_fuel_regulator_rich | rich_condition (88%/78%) | partial | Проверить давление и удержание давления топлива |
| EXT-RICH-05 | [motor-fuel-trim](https://www.motor.com/magazine-summary/fuel-trim-data-powerful-diagnostic-tool/) | Negative trims and rich DTC indicate excess fuel or low measured air. | injector_or_fuel_regulator_rich | rich_condition (88%/78%) | partial | Проверить давление и удержание давления топлива |
| EXT-RICH-06 | [obdcodes-cavalier-rich](https://www.obd-codes.com/forums/viewtopic.php?t=5662) | Negative trims and rich DTC indicate excess fuel or low measured air. | injector_or_fuel_regulator_rich | rich_condition (88%/78%) | partial | Проверить давление и удержание давления топлива |
| EXT-RICH-07 | [motor-fuel-trim](https://www.motor.com/magazine-summary/fuel-trim-data-powerful-diagnostic-tool/) | Negative trims and rich DTC indicate excess fuel or low measured air. | injector_or_fuel_regulator_rich | rich_condition (88%/78%) | partial | Проверить давление и удержание давления топлива |
| EXT-RICH-08 | [obdcodes-cavalier-rich](https://www.obd-codes.com/forums/viewtopic.php?t=5662) | Negative trims and rich DTC indicate excess fuel or low measured air. | injector_or_fuel_regulator_rich | rich_condition (88%/78%) | partial | Проверить давление и удержание давления топлива |
| EXT-MISFIRE-01 | [obdcodes-avalanche-misfire](https://www.obd-codes.com/forums/viewtopic.php?t=11464) | Forum repair was ignition wire/secondary ignition; app should keep this in misfire checks. | ignition_secondary_repair | misfire (69%/58%) | partial | Определить цилиндр по DTC |
| EXT-MISFIRE-02 | [obdcodes-avalanche-misfire](https://www.obd-codes.com/forums/viewtopic.php?t=11464) | Forum repair was ignition wire/secondary ignition; app should keep this in misfire checks. | ignition_secondary_repair | misfire (50%/36%) | partial | Определить цилиндр по DTC |
| EXT-MISFIRE-03 | [obdcodes-avalanche-misfire](https://www.obd-codes.com/forums/viewtopic.php?t=11464) | Forum repair was ignition wire/secondary ignition; app should keep this in misfire checks. | ignition_secondary_repair | misfire (50%/36%) | partial | Определить цилиндр по DTC |
| EXT-MISFIRE-04 | [obdcodes-avalanche-misfire](https://www.obd-codes.com/forums/viewtopic.php?t=11464) | Forum repair was ignition wire/secondary ignition; app should keep this in misfire checks. | ignition_secondary_repair | misfire (67%/54%) | partial | Определить цилиндр по DTC |
| EXT-MISFIRE-05 | [obdcodes-avalanche-misfire](https://www.obd-codes.com/forums/viewtopic.php?t=11464) | Forum repair was ignition wire/secondary ignition; app should keep this in misfire checks. | ignition_secondary_repair | misfire (86%/76%) | partial | Определить цилиндр по DTC |
| EXT-MISFIRE-06 | [obdcodes-avalanche-misfire](https://www.obd-codes.com/forums/viewtopic.php?t=11464) | Forum repair was ignition wire/secondary ignition; app should keep this in misfire checks. | ignition_secondary_repair | misfire (67%/54%) | partial | Определить цилиндр по DTC |
| EXT-MISFIRE-07 | [obdcodes-avalanche-misfire](https://www.obd-codes.com/forums/viewtopic.php?t=11464) | Forum repair was ignition wire/secondary ignition; app should keep this in misfire checks. | ignition_secondary_repair | misfire (67%/54%) | partial | Определить цилиндр по DTC |
| EXT-MISFIRE-08 | [obdcodes-avalanche-misfire](https://www.obd-codes.com/forums/viewtopic.php?t=11464) | Forum repair was ignition wire/secondary ignition; app should keep this in misfire checks. | ignition_secondary_repair | misfire (50%/36%) | partial | Определить цилиндр по DTC |
| EXT-MISFIRE-09 | [obdcodes-avalanche-misfire](https://www.obd-codes.com/forums/viewtopic.php?t=11464) | Forum repair was ignition wire/secondary ignition; app should keep this in misfire checks. | ignition_secondary_repair | misfire (69%/58%) | partial | Определить цилиндр по DTC |
| EXT-MISFIRE-10 | [obdcodes-avalanche-misfire](https://www.obd-codes.com/forums/viewtopic.php?t=11464) | Forum repair was ignition wire/secondary ignition; app should keep this in misfire checks. | ignition_secondary_repair | misfire (50%/36%) | partial | Определить цилиндр по DTC |
| EXT-LEAN-VAC-01 | [obdcodes-f150-p0171](https://www.obd-codes.com/forums/viewtopic.php?t=11458) | Lean idle/load trims and forum repair pattern point to intake/vacuum leak. | unmetered_air | unmetered_air (95%/82%) | exact | Сравнить STFT/LTFT на холостом ходу и при 2500 RPM |
| EXT-LEAN-VAC-02 | [obdcodes-silverado-lean](https://www.obd-codes.com/forums/viewtopic.php?t=5661) | Lean idle/load trims and forum repair pattern point to intake/vacuum leak. | unmetered_air | unmetered_air (95%/82%) | exact | Сравнить STFT/LTFT на холостом ходу и при 2500 RPM |
| EXT-LEAN-VAC-03 | [obdcodes-f150-p0171](https://www.obd-codes.com/forums/viewtopic.php?t=11458) | Lean idle/load trims and forum repair pattern point to intake/vacuum leak. | unmetered_air | unmetered_air (95%/82%) | exact | Сравнить STFT/LTFT на холостом ходу и при 2500 RPM |

## Sources

- [underhood-fuel-delivery](https://www.underhoodservice.com/diagnostic-solutions-testing-fuel-delivery-systems-checking-the-basics-first-can-facilitate-diagnostic-strategy/) - Underhood Service fuel-delivery diagnostic examples.
- [underhood-maf](https://www.underhoodservice.com/troubleshooting-mass-air-flow-maf-sensors/) - Underhood Service MAF / VE diagnostic practice.
- [motor-fuel-trim](https://www.motor.com/magazine-summary/fuel-trim-data-powerful-diagnostic-tool/) - MOTOR fuel-trim diagnosis article.
- [skanyx-live-data](https://skanyx.com/blog/live-data-stream-analysis-guide) - Skanyx live data stream analysis guide.
- [obdcodes-f150-p0171](https://www.obd-codes.com/forums/viewtopic.php?t=11458) - OBD-Codes forum: Ford F-150 lean codes with vacuum leak discussion.
- [obdcodes-silverado-lean](https://www.obd-codes.com/forums/viewtopic.php?t=5661) - OBD-Codes forum: Silverado lean condition solved with intake gaskets.
- [obdcodes-avalanche-misfire](https://www.obd-codes.com/forums/viewtopic.php?t=11464) - OBD-Codes forum: Avalanche P0300/P0171 fixed by loose plug wire.
- [obdcodes-cavalier-rich](https://www.obd-codes.com/forums/viewtopic.php?t=5662) - OBD-Codes forum: Cavalier P0172 rich / restricted injectors discussion.
- [obdcodes-p0420-o2](https://www.obd-codes.com/forums/viewtopic.php?t=1960) - OBD-Codes forum: P0420 after replacing O2 sensors.
- [repairpal-p0128](https://repairpal.com/obd-ii-code-p0128) - RepairPal P0128 thermostat/coolant diagnosis.
- [obdii-p0562](https://www.obd-codes.com/p0562) - OBD-II P0562 system voltage reference.
- [obdii-egr](https://www.obd-codes.com/p0401) - OBD-II EGR flow DTC references.
- [obdii-vvt](https://www.obd-codes.com/p0016) - OBD-II cam/crank and VVT DTC references.
