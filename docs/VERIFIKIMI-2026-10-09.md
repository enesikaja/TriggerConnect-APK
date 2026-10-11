# Kontrolli i aplikacionit — 9 tetor 2026

> Ky ishte kontrolli fillestar. Problemet 1–6 më poshtë janë korrigjuar në source pas kontrollit; shih `PROMPT-COMPLIANCE.md` dhe rezultatet më të fundit të build/test.

Push notifications nuk mund të konsiderohen plotësisht funksionale në versionin aktual. Ky kontroll nuk ndryshoi kodin dhe nuk dërgoi njoftime te përdoruesit.

## Çfarë u verifikua

- Ndërtimi i APK-së debug përfundon me sukses.
- Të 18 testet JVM kaluan, pa gabime. Ato përdorin edhe zëvendësues të serverit dhe nuk provojnë funksionimin e plotë online.
- Supabase `/auth/v1/health` përgjigjet HTTP 200.
- Nëntë funksione të mbrojtura, përfshirë `register-device`, `trigger-dispatch`, lidhjet, grupet dhe konfirmimin e marrjes, kthyen HTTP 401 pa sesion. Kjo provon arritshmërinë dhe refuzimin e kërkesave pa hyrje, jo suksesin e operacioneve me llogari.
- Konfigurimi Android i Firebase përputhet me paketën `com.triggerconnect.app`; service account lokal i përket të njëjtit projekt. Nuk u verifikua që sekreti është vendosur saktë në serverin e publikuar.
- Ka emulator të lidhur, por asnjë Samsung fizik. Testet instrumentale u bllokuan në `mergeDebugAndroidTestJavaResource`, para ekzekutimit: gjashtë varësi sjellin të njëjtin `META-INF/LICENSE.md`. Kontrolli i përbashkët përfundoi me gabim; nuk u prodhua raport përfundimtar lint.

## Probleme të konfirmuara nga kodi

1. **Leja e njoftimeve nuk kërkohet.** `POST_NOTIFICATIONS` deklarohet në manifest dhe kontrollohet te `NotificationHelper`, por aplikacioni nuk ka kërkesë runtime për këtë leje. Në instalime të reja Android 13+, njoftimet mbeten të bllokuara derisa leja të jepet. Burim: https://developer.android.com/develop/ui/compose/notifications/notification-permission
2. **Regjistrimi i pajisjes mund të raportohet gabimisht si sukses.** `FcmTokenRegistrar.kt:22` kontrollon vetëm përjashtimet, ndërsa repository kthen `AppResult.Failure`. `MainViewModel.kt:33` e shënon regjistrimin si të kryer përpara rezultatit dhe nuk e riprovon në atë sesion të ViewModel.
3. **Serveri nuk kontrollon gabimet e ruajtjes së token-it.** `supabase/functions/register-device/index.ts:19–27` fshin dhe shton token-in pa kontrolluar gabimet e bazës dhe kthen `ok: true` edhe nëse ruajtja dështon.
4. **Cilësimet Sound, Vibration dhe Confirm urgent triggers nuk zbatohen.** Preferencat ruhen, por `NotificationHelper`/`NotificationChannels` nuk lexojnë tingullin ose vibrimin; `HomeScreen.kt:149` kërkon konfirmim për çdo trigger urgjent pavarësisht preferencës.
5. **Dalja nga llogaria nuk pastron pajisjen dhe të dhënat lokale.** `AuthRepositoryImpl.kt:110–113` pastron vetëm sesionin/ruajtjen e sigurt. Token-i mbetet në server dhe tabelat Room nuk pastrohen ose ndahen sipas pronarit. Kjo mund të lërë njoftime për llogarinë e vjetër dhe të dhëna të vjetra të dukshme pas ndërrimit të llogarisë, sidomos kur rifreskimi dështon.
6. **Gabimi i autorizimit FCM mund të lërë dërgimin të papërfunduar.** `sendPush` merr token-in OAuth përpara trajtimit të gabimeve për çdo pajisje. Nëse ky hap dështon, `trigger-dispatch` del para krijimit të marrësve dhe statusit përfundimtar; ngjarja mund të mbetet `DISPATCHING` dhe riprovimi kthen ngjarjen ekzistuese pa ridërgim.

## Çfarë mbetet për provë reale

Pas korrigjimeve, duhet provuar regjistrimi/hyrja me dy llogari testimi, lidhja me QR, grupet, dërgimi personal dhe në grup, marrja me aplikacionin hapur dhe në sfond, hapja nga njoftimi, konfirmimi i marrjes dhe dalja/ndërrimi i llogarisë. Arritja e një push-i real në Samsung nuk u konfirmua nga ky kontroll.
