package io.github.diegofranciscog.inventory.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import org.hibernate.annotations.Immutable;

/** Tipo de documento de referencia: comprobantes del SRI y documentos internos (R-22). */
@Entity
@Immutable
@Table(name = "document_type")
public class DocumentType {

    @Id
    @Column(length = 4)
    private String code;

    @Column(nullable = false, length = 80)
    private String name;

    @Column(nullable = false, length = 8)
    private String source;

    protected DocumentType() {
    }

    public String getCode() {
        return code;
    }

    public String getName() {
        return name;
    }

    public String getSource() {
        return source;
    }
}
