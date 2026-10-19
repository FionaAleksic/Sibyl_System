# Webdesign-Grundsätze — allgemeine Gestaltungspräferenz

**Gilt als Standard bei allen neuen Webseitenentwürfen und Weboberflächen, nicht nur bei Sibyl System.** Bei ausdrücklich abweichenden Projektanforderungen können Ausnahmen vereinbart werden.

## Stil

**Minimalistisch · kantig · funktional · menschlich, nicht hochglanzpoliert.**

- Keine abgerundeten Ecken: `border-radius: 0` für Buttons, Felder, Navigationspunkte, Panels, Dialoge und Statusanzeigen.
- Keine dekorativen Farbverläufe, Glas-Effekte oder großen Schlagschatten.
- Sichtbare, feine Trennlinien und erkennbare Seitenbereiche statt dekorativer Kartenberge.
- Typografie schlicht und gut lesbar. Wenige Schriftgrößen, eindeutige Hierarchie.
- Farben ruhig und sachlich; Akzentfarben nur dort, wo sie tatsächlich Orientierung geben.
- Gelegentlich kleine Abweichungen von der strengen Symmetrie (verschobener Akzentstrich, ungleiche Spaltenbreite, knappe Randnotiz) — **nie** zulasten von Orientierung, Lesbarkeit oder Zugänglichkeit.
- Keine absichtlich kaputten Abstände, fehlerhaften Inhalte oder inkonsistenten Bedienelemente. „Menschlich“ meint gestalterische Zurückhaltung, nicht mangelnde Qualität.
- Nutzeroberfläche für Desktop und Mobilgeräte funktional halten. Tastaturbedienung, Fokuszustände, Kontraste und Formularlabels sind Pflicht.
- Styles in gemeinsame Tokens bündeln, damit Core und Addons denselben visuellen Rahmen verwenden können. Module dürfen individuell gestaltet sein, respektieren aber die kantige Grundsprache.

## Design-Tokens (Ausgangswerte)

| Bedeutung | Hell | Dunkel |
| --- | --- | --- |
| Hintergrund | `#f4f5f6` | `#111416` |
| Oberfläche | `#ffffff` | `#181c20` |
| Hintergrund sekundär | `#eceff1` | `#20262b` |
| Text | `#15191d` | `#edf1f4` |
| Sekundärtext | `#5d6872` | `#a9b2ba` |
| Rahmen | `#b8c0c7` | `#3d464e` |
| Akzent | `#1c5d8f` | `#4d91c8` |

CSS-Baseline: `*,*::before,*::after {box-sizing:border-box;border-radius:0}`.

## Prüffragen vor Freigabe

1. Sehen Login, Startseite, Administration und Addons wie eine zusammengehörige Anwendung aus?
2. Sind die Ecken tatsächlich überall rechteckig?
3. Gibt es überflüssige Schatten, Farbverläufe, übertriebene Animationen oder Werbetexte?
4. Wirkt die Seite wie ein benutzbares Arbeitswerkzeug — auch mit wenig Inhalt?
5. Bleiben Funktion, Tastaturnavigation, Kontrast und responsive Nutzung vollständig erhalten?

Stand: 2026-10-13. Benutzerdefinierte Designpräferenz, die bei späteren Webseitenarbeiten erneut berücksichtigt werden soll.
