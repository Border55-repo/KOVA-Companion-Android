# KOVA Companion – tilgjengelighetsgjennomgang 1.x

## Implementert

### Android
- Material 3-komponenter med standard minimum touch target
- dynamisk system dark mode
- tekstbaserte knapper i hovedflytene; funksjoner er ikke avhengige av ikon alene
- støtte for systemets tekstskalering gjennom Compose typography
- scrollbare innstillings- og aktivitetsflater
- korpssøk i stedet for kun lang rullegardin
- detaljer og handlinger er tilgjengelige uten gesture-only kontroll
- Android Lint kjøres i RC-gaten

### PWA
- skip-link til hovedinnhold
- hurtignavigasjon til Oversikt, Mine vakter, Varsler og Korps
- tydelig `:focus-visible`
- minimum 44 px høyde på interaktive kontroller
- `prefers-reduced-motion`
- responsive én-/to-kolonne-layouts
- dark mode gjennom `prefers-color-scheme`
- statusområder bruker `aria-live`
- tekstlabel på sentrale knapper og felter

## Kvalitetssikring før produksjon

Fysisk gjennomgang bør fortsatt gjøres med:
- Android stor skrift / font scaling
- TalkBack
- iPhone VoiceOver
- light/dark mode
- liten telefon
- nettbrett/stor skjerm

Automatiske tester og lint kan avdekke regressjoner, men erstatter ikke fysisk skjermleser- og stor-tekst-test.
