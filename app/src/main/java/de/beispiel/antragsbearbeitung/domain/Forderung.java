package de.beispiel.antragsbearbeitung.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.math.BigDecimal;
import java.time.LocalDateTime;

/** Wird nur gelesen. Geschrieben wird sie vom Batchlauf per SQL, siehe ForderungWriter. */
@Entity
@Table(name = "forderung")
public class Forderung {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "vertrag_nummer", nullable = false, updatable = false)
    private String vertragNummer;

    @Column(nullable = false, updatable = false, length = 7)
    private String monat;

    @Column(nullable = false, updatable = false)
    private BigDecimal betrag;

    @Column(name = "erzeugt_am", insertable = false, updatable = false)
    private LocalDateTime erzeugtAm;

    public Long getId() { return id; }
    public String getVertragNummer() { return vertragNummer; }
    public String getMonat() { return monat; }
    public BigDecimal getBetrag() { return betrag; }
    public LocalDateTime getErzeugtAm() { return erzeugtAm; }
}
