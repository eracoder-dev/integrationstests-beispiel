package de.beispiel.antragsbearbeitung.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.Version;
import java.math.BigDecimal;
import java.time.LocalDateTime;

@Entity
@Table(name = "antrag")
public class Antrag {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(unique = true)
    private String antragsnummer;

    @Column(nullable = false)
    private String name;

    @Column(nullable = false, length = 5)
    private String plz;

    @Column(nullable = false)
    private String ort;

    @Column(name = "antragssumme", nullable = false)
    private BigDecimal antragssumme;

    @Column(nullable = false)
    private String status;

    @Column(name = "prozess_instanz_id")
    private String prozessInstanzId;

    @Column(name = "erfasst_am", insertable = false, updatable = false)
    private LocalDateTime erfasstAm;

    @Version
    private long version;

    protected Antrag() {
    }

    public Antrag(String name, String plz, String ort, BigDecimal antragssumme) {
        this.name = name;
        this.plz = plz;
        this.ort = ort;
        this.antragssumme = antragssumme;
        this.status = "ERFASST";
    }

    public Long getId() { return id; }
    public String getAntragsnummer() { return antragsnummer; }
    public void setAntragsnummer(String antragsnummer) { this.antragsnummer = antragsnummer; }
    public String getName() { return name; }
    public String getPlz() { return plz; }
    public String getOrt() { return ort; }
    public BigDecimal getAntragssumme() { return antragssumme; }
    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }
    public String getProzessInstanzId() { return prozessInstanzId; }
    public void setProzessInstanzId(String prozessInstanzId) { this.prozessInstanzId = prozessInstanzId; }
    public LocalDateTime getErfasstAm() { return erfasstAm; }
}
