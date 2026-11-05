package de.beispiel.antragsbearbeitung.service;

public enum AdressErgebnis {
    /** Der Dienst hat die Adresse bestätigt. */
    GUELTIG,
    /** Der Dienst war nicht erreichbar. Die Prüfung muss später nachgeholt werden. */
    OFFEN
}
