# Kova Companion: demonstrasjon og fremtidig API

Kova Companion er et uavhengig prosjekt, ikke en offisiell Røde Kors-app.
Ingen offisiell API-tilgang er etablert eller forutsatt av denne utgaven.

## Demonstrasjon

Nettappen har «Prøv demo». Android har «Demo». Eksempelaktiviteter, favoritter
og eksempelvarsler kan vises uten utsending. Demovalg lagres bare i minnet.
En favoritt er en personlig huskeliste, ikke påmelding til en vakt.

## Data vi vil be om

Lesetilgang til korps-ID/navn, stabil aktivitets-ID, start/slutt med tidssone,
aktivitetstype, offentlig beskrivelse/sted, endringstid og avlysningsstatus.
Medlemslister, helseopplysninger og deltakerdetaljer inngår ikke i behovet.
Avklar eksplisitt hvilke felt som kan vises og mellomlagres før integrasjon.

Ønskede avklaringer med Røde Kors: testmiljø, autentisering, tillatte brukere,
rategrenser, paginering, delta/endringsstrøm, stabile ID-er, tidssoner,
lagringsperiode, rettigheter og kontaktpunkt ved driftsfeil.

## Utskiftbar datakilde

Bridge `ScheduleSource` i `bridge/sources.py` er inngangen til normaliserte data.
`sync.main(source=...)` lar en ny adapter bruke eksisterende endringskontroll,
snapshots og varselkø. Standardadapteren bruker fortsatt offentlige kalendere.
PWA `data-source.js` og Android `ScheduleSource` avgrenser klientenes datatilgang.
API-hemmeligheter skal aldri bygges inn i PWA, APK eller offentlige snapshots.
Ved senere innføring må tilgangskontroll og eventuell privat datalagring designes
etter API-avtalen; dagens offentlige GitHub-snapshots er kun for offentlige data.

## Godkjenning før bytte

Test stabile ID-er, avlysninger, tidssoner, tomme/mangelfulle svar, ratebegrensning,
utløpt tilgang og nettverksfeil. Kjør først i testmiljø uten push. Første datasett
er referansegrunnlag og skal ikke sende gamle aktiviteter som nye varsler.
Behold mulighet til å rulle tilbake uten dupliserte varsler.

## Drift og varsler

«Sist kontrollert» er tidspunktet kilden sist ble kontrollert med hell.
«Data endret» kan være eldre selv om kontrollen er fersk. Over 35 minutter
markeres som mulig foreldet; lagrede data beholdes ved feil.
Admin viser siste fullstendig vellykkede synk og feil som trenger oppfølging.
Aksept hos push-tjenesten bekrefter ikke at telefonen viste varselet.

Testmeldinger krever en bestemt PWA-enhetskode og sendes aldri til Android-topics.
Enhetskoden finner eieren under Varsler i sin egen PWA. Testmeldinger publiserer
ingen offentlig endringslogg. Ordinær endringslogg er offentlig, selv når push
avgrenses til ett korps. Forhåndsvisningen opplyser om dette før publisering.
Google Play er på vent; APK-utgivelse er fortsatt aktiv.
