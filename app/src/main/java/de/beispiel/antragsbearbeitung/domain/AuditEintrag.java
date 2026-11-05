package de.beispiel.antragsbearbeitung.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.LocalDateTime;

@Entity
@Table(name = "audit")
public class AuditEintrag {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(insertable = false, updatable = false)
    private LocalDateTime zeitpunkt;

    @Column(nullable = false)
    private String aktion;

    @Column(nullable = false)
    private String objekt;

    private String details;

    @Column(name = "korrelations_id")
    private String korrelationsId;

    @Column(nullable = false)
    private String akteur;

    protected AuditEintrag() {
    }

    public AuditEintrag(String aktion, String objekt, String details, String korrelationsId, String akteur) {
        this.aktion = aktion;
        this.objekt = objekt;
        this.details = details;
        this.korrelationsId = korrelationsId;
        this.akteur = akteur;
    }

    public Long getId() { return id; }
    public LocalDateTime getZeitpunkt() { return zeitpunkt; }
    public String getAktion() { return aktion; }
    public String getObjekt() { return objekt; }
    public String getDetails() { return details; }
    public String getKorrelationsId() { return korrelationsId; }
    public String getAkteur() { return akteur; }
}
