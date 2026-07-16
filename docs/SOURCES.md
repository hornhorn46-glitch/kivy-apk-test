# Engineering Sources

This project uses open, non-proprietary references and project-authored engineering models.

## Standards And Protocols

- SAE J1979 / ISO 15031 summaries for OBD diagnostic services and standard PIDs.
- ISO 15765-4 / ISO-TP summaries for CAN OBD request/response behavior.
- ELM327 public AT command documentation and public command summaries.

## Open Reference Links

- OBD overview and SAE/ISO standard summary: https://en.wikipedia.org/wiki/On-board_diagnostics
- OBD-II PID/service overview and standard PID formulas: https://en.wikipedia.org/wiki/OBD-II_PIDs
- ELM327 protocol/command overview: https://en.wikipedia.org/wiki/ELM327
- US EPA basic OBD information is referenced by public OBD summaries.

## Licensing Notes

- Do not copy proprietary SAE/ISO text into the repository.
- Do not copy paid OEM PID databases.
- Use only open summaries and independently authored models/rules.
- Rules must cite source identifiers from `knowledge/sources.json`.

## Limits

Generic OBD-II is emissions-focused. Manufacturer-specific modules, bidirectional controls, coding, and many transmission/body systems require proprietary data or licensed access. The architecture supports these later, but the repository must not pretend to contain unavailable OEM data.
