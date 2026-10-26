# Cripzy's Hugo Preise (Fabric, Minecraft 26.2)

Zeigt im Tooltip eines Items den **günstigsten AH-Preis** und den **höchsten Order-Preis**.

## So funktioniert es
Die Mod liest die Preise mit, während du das Auktionshaus (`/ah`) oder das Order-Menü offen hast.
Pro Item (inkl. Verzauberungen) merkt sie sich im AH den niedrigsten und bei Order den höchsten Preis, den sie gesehen hat.
Beim Hovern über ein Item steht das unter den Verzauberungen:

    AH: $1.500.000 (vor 3 Min)
    Order: $2.000.000 (vor 3 Min)

Tipp: Sortiere im AH nach Preis aufsteigend und in Order nach Preis absteigend, dann liegt der gesuchte Preis auf der ersten Seite.
Die Werte gelten nur für das, was du seit dem Start gesehen hast und werden nach 15 Minuten ausgeblendet (einstellbar).

## Bauen (empfohlen: GitHub)
1. Neues GitHub-Repository anlegen und den Inhalt dieses Ordners hochladen.
2. Reiter **Actions** öffnen, Workflow **build** läuft automatisch.
3. Unter dem fertigen Lauf bei **Artifacts** die `cripzys-hugo-preise` herunterladen. Die `.jar` ohne `-sources` kommt in `.minecraft/mods`.

Voraussetzungen im Spiel: Fabric Loader 0.19.3+, Fabric API für 26.2, Java 25.

## Einstellungen
`.minecraft/config/hugoprices.properties` wird beim ersten Start angelegt.
Wenn der Server das Menü anders nennt oder die Preiszeile anders schreibt, dort die Wörter anpassen.
Wichtig: `ahPriceIsTotal` / `orderPriceIsTotal` legen fest, ob der angezeigte Preis für den ganzen Stapel gilt.
