package com.unicordoba.simulador.model;

import jakarta.persistence.*;
import jakarta.xml.bind.annotation.XmlRootElement;
import jakarta.xml.bind.annotation.XmlElement;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

/**
 * Entidad Catalogo - Agrupa un conjunto de procesos obtenidos del SO.
 * Contiene un número consecutivo y un nombre descriptivo.
 */
@Entity
@Table(name = "catalogos")
@XmlRootElement(name = "catalogo")
public class Catalogo {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "numero_catalogo", unique = true, nullable = false)
    private Integer numeroCatalogo;

    @Column(name = "nombre_catalogo", nullable = false)
    private String nombreCatalogo;

    @Column(name = "fecha_creacion")
    private LocalDateTime fechaCreacion;

    @Column(name = "criterio_seleccion")
    private String criterioSeleccion; // "CPU" o "MEMORIA"

    @OneToMany(mappedBy = "catalogo", cascade = CascadeType.ALL, fetch = FetchType.EAGER, orphanRemoval = true)
    private List<Proceso> procesos = new ArrayList<>();

    public Catalogo() {
        this.fechaCreacion = LocalDateTime.now();
    }

    public Catalogo(Integer numeroCatalogo, String nombreCatalogo, String criterioSeleccion) {
        this.numeroCatalogo = numeroCatalogo;
        this.nombreCatalogo = nombreCatalogo;
        this.criterioSeleccion = criterioSeleccion;
        this.fechaCreacion = LocalDateTime.now();
    }

    // Getters y Setters
    @XmlElement
    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }

    @XmlElement
    public Integer getNumeroCatalogo() { return numeroCatalogo; }
    public void setNumeroCatalogo(Integer numeroCatalogo) { this.numeroCatalogo = numeroCatalogo; }

    @XmlElement
    public String getNombreCatalogo() { return nombreCatalogo; }
    public void setNombreCatalogo(String nombreCatalogo) { this.nombreCatalogo = nombreCatalogo; }

    @XmlElement
    public LocalDateTime getFechaCreacion() { return fechaCreacion; }
    public void setFechaCreacion(LocalDateTime fechaCreacion) { this.fechaCreacion = fechaCreacion; }

    @XmlElement
    public String getCriterioSeleccion() { return criterioSeleccion; }
    public void setCriterioSeleccion(String criterioSeleccion) { this.criterioSeleccion = criterioSeleccion; }

    @XmlElement(name = "proceso")
    public List<Proceso> getProcesos() { return procesos; }
    public void setProcesos(List<Proceso> procesos) { this.procesos = procesos; }

    public void addProceso(Proceso proceso) {
        procesos.add(proceso);
        proceso.setCatalogo(this);
    }
}
