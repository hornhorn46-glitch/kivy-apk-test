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
- MIT OCW 2.61 Internal Combustion Engines: https://ocw.mit.edu/courses/2-61-internal-combustion-engines-spring-2017/resources/lecture-notes/
- MIT OCW 8.21 Internal Combustion Engines lecture: https://ocw.mit.edu/courses/8-21-the-physics-of-energy-fall-2009/resources/mit8_21s09_lec11/
- NPTEL IC Engines and Gas Turbines: https://nptel.ac.in/courses/112103262
- Underhood Service fuel trim and MAF/VE diagnostic articles.
- MOTOR and DENSO public technical articles for repair-confirmed diagnostic patterns.
- dtcdb MIT-licensed generic OBD-II DTC seed database: https://github.com/todrobbins/dtcdb
- US EPA basic OBD information is referenced by public OBD summaries.

## Licensing Notes

- Do not copy proprietary SAE/ISO text into the repository.
- Do not copy paid OEM PID databases.
- Use only open summaries and independently authored models/rules.
- Rules must cite source identifiers from `knowledge/sources.json`.

## Limits

Generic OBD-II is emissions-focused. Manufacturer-specific modules, bidirectional controls, coding, and many transmission/body systems require proprietary data or licensed access. The architecture supports these later, but the repository must not pretend to contain unavailable OEM data.
