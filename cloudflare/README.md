# Gratis KOVA-dispatcher

Status: lokalt testet, ikke koblet til produksjon. Cloudflare Workers Free og Firebase Spark beholdes. Ingen fakturering eller IAM-endringer kreves av koden.

## Installer i dashboard

1. Opprett Worker `kova-dispatch` med Hello World-malen på Workers Free.
2. Velg Edit code. Erstatt hele worker.js med innholdet i worker.mjs og velg Deploy.
3. Opprett et GitHub fine-grained personal access token med tilgang KUN til `Border55-repo/KOVA-Companion-Android`, repository permission Actions: Read and write. Velg en utløpsdato og planlegg fornyelse. Ikke del tokenet i chat eller kildekode.
4. Lagre tokenet som Cloudflare Worker Secret `GITHUB_TOKEN` under Settings / Variables and Secrets. Dette gir Workeren rett til å starte Actions-jobber i det ene repoet. Ingen Firebase-servicekontonøkkel skal legges inn.
5. Legg til Cron Trigger `*/5 * * * *` under Worker Settings / Trigger Events. Den starter Bridge når ingen jobb allerede kjører og siste jobb er eldre enn fire minutter. Behold GitHub cron som reserve.
6. Adminpanelet må integreres med POST /dispatch etter at forespørselen er lagret i Firestore. Send Firebase ID-token i Authorization: Bearer og JSON {command,requestId}. command er announcement eller bridgeSync. Endpoint for denne installasjonen er https://kova-dispatch.juliannordli.workers.dev/dispatch. Denne frontendintegrasjonen gjenstår.
7. Bekreft at /health viser configured:true. Publiser deretter én avtalt test fra admin, kontroller at en workflow_dispatch-jobb starter, og bekreft Firestore-status og fysisk mottak på iPhone/Android.

## Sikkerhet og begrensninger

Worker bruker innsenderens Firebase ID-token til en Firestore REST-lesing av det aktuelle adminCommands-dokumentet. De eksisterende adminReady-reglene verifiserer admintilgang; Worker har ingen servernøkkel som omgår disse. Origin-sjekk er bare et tillegg, ikke autentisering. Dokumentet må være requested og ha samme requestId. Mottakerfiltrering og pushnøkler beholdes i eksisterende Bridge.

GitHub-nøkkelen kan starte Actions-jobber i repoet og må beskyttes og fornyes før utløp. Ikke logg request headers eller tokenverdier. /health viser kun om nøkkelen finnes, ikke om den er gyldig.

HTTP 202 betyr at GitHub har akseptert jobben, ikke at telefoner har mottatt varsel. Jobbkø og transport kan fortsatt forsinke levering. Gjentatte autoriserte kall kan køe flere jobber; Bridge har versjonsbasert jobbkrav for annonser. Gratisgrenser hos Cloudflare, Firestore og GitHub gjelder fortsatt. Ingen automatisk oppgradering til betalte planer er konfigurert.

## Tester

`node --test cloudflare/worker.test.mjs`

Ni lokale tester dekker avvisning av feil origin/manglende innlogging, Firebase-regler, gamle kommandoer, fast repo/ref, skjuling av hemmeligheter, feilhåndtering og cron. Produksjonsforbindelsen er ennå ikke verifisert.
