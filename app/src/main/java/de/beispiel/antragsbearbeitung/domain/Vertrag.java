package de.beispiel.antragsbearbeitung.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.Version;
import java.math.BigDecimal;

@Entity
@Table(name = "vertrag")
public class Vertrag {

    @Id
    private String nummer;

    @Column(name = "vertragspartner", nullable = false)
    private String vertragspartner;

    @Column(name = "monatsbeitrag", nullable = false)
    private BigDecimal monatsbeitrag;

    @Column(nullable = false)
    private String status;

    @Version
    private long version;

    public String getNummer() { return nummer; }
    public String getVertragspartner() { return vertragspartner; }
    public BigDecimal getMonatsbeitrag() { return monatsbeitrag; }
    public String getStatus() { return status; }
}
