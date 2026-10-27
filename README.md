# Cripzy's Hugo Preise (Fabric, Minecraft 26.2)

Zeigt im Tooltip eines Items den **günstigsten AH-Preis** und den **höchsten Order-Preis**.

## So funktioniert es
Beim Hovern über ein **verzaubertes** Item holt die Mod im Hintergrund die aktuellen Marktdaten von
hugosmp-market.net und zeigt unter den Verzauberungen:

    AH: $47.000 (vor 3 Min)       <- niedrigster Stückpreis aller Auktionen der letzten 60 Min
    Order: $100.000 (vor 5 Std)   <- höchster Stückpreis aller Orders (bis 7 Tage alt)

- Abrufe laufen im Hintergrund, einer nach dem anderen, und werden 60 Sekunden zwischengespeichert.
- Klappt der Abruf nicht (Seite nicht erreichbar, geblockt), nutzt die Mod ersatzweise Preise, die du im
  Auktionshaus-/Order-Menü im Spiel gesehen hast.
- Die Zeit in Klammern sagt, wann der Preis auf dem Markt zuletzt gesehen wurde.

## Bauen (empfohlen: GitHub)
1. Neues GitHub-Repository anlegen und den Inhalt dieses Ordners hochladen.
2. Reiter **Actions** öffnen, Workflow **build** läuft automatisch.
3. Unter dem fertigen Lauf bei **Artifacts** die `cripzys-hugo-preise` herunterladen. Die `.jar` ohne `-sources` kommt in `.minecraft/mods`.

Voraussetzungen im Spiel: Fabric Loader 0.19.3+, Fabric API für 26.2, Java 25.

## Einstellungen
`.minecraft/config/hugoprices.properties` wird beim ersten Start angelegt.
Wenn der Server das Menü anders nennt oder die Preiszeile anders schreibt, dort die Wörter anpassen.
Wichtig: `ahPriceIsTotal` / `orderPriceIsTotal` legen fest, ob der angezeigte Preis für den ganzen Stapel gilt.
