package com.unicordoba.simulador.model;

import com.fasterxml.jackson.annotation.JsonIgnore;
import jakarta.persistence.*;
import jakarta.xml.bind.annotation.XmlRootElement;
import jakarta.xml.bind.annotation.XmlElement;
import jakarta.xml.bind.annotation.XmlTransient;

/**
 * Entidad Proceso - Representa un proceso del sistema operativo.
 * Prioridad: 0 = Expulsivo, 1 = No Expulsivo
 * Los procesos del SO son No Expulsivos, los de usuario son Expulsivos.
 */
@Entity
@Table(name = "procesos")
@XmlRootElement(name = "proceso")
public class Proceso {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "pid", nullable = false)
    private Long pid;

    @Column(name = "nombre", nullable = false)
    private String nombre;

    @Column(name = "usuario")
    private String usuario;

    @Column(name = "descripcion", length = 1000)
    private String descripcion;

    @Column(name = "prioridad")
    private Integer prioridad; // 0 = Expulsivo, 1 = No Expulsivo

    @Column(name = "uso_cpu")
    private Double usoCpu;

    @Column(name = "uso_memoria")
    private Double usoMemoria;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "catalogo_id")
    @JsonIgnore
    private Catalogo catalogo;

    public Proceso() {}

    public Proceso(Long pid, String nombre, String usuario, String descripcion, Integer prioridad) {
        this.pid = pid;
        this.nombre = nombre;
        this.usuario = usuario;
        this.descripcion = descripcion;
        this.prioridad = prioridad;
    }

    // Getters y Setters
    @XmlElement
    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }

    @XmlElement
    public Long getPid() { return pid; }
    public void setPid(Long pid) { this.pid = pid; }

    @XmlElement
    public String getNombre() { return nombre; }
    public void setNombre(String nombre) { this.nombre = nombre; }

    @XmlElement
    public String getUsuario() { return usuario; }
    public void setUsuario(String usuario) { this.usuario = usuario; }

    @XmlElement
    public String getDescripcion() { return descripcion; }
    public void setDescripcion(String descripcion) { this.descripcion = descripcion; }

    @XmlElement
    public Integer getPrioridad() { return prioridad; }
    public void setPrioridad(Integer prioridad) { this.prioridad = prioridad; }

    @XmlElement
    public Double getUsoCpu() { return usoCpu; }
    public void setUsoCpu(Double usoCpu) { this.usoCpu = usoCpu; }

    @XmlElement
    public Double getUsoMemoria() { return usoMemoria; }
    public void setUsoMemoria(Double usoMemoria) { this.usoMemoria = usoMemoria; }

    @XmlTransient
    public Catalogo getCatalogo() { return catalogo; }
    public void setCatalogo(Catalogo catalogo) { this.catalogo = catalogo; }

    /**
     * Retorna el tipo de prioridad como texto legible.
     */
    public String getTipoPrioridad() {
        return prioridad != null && prioridad == 0 ? "Expulsivo" : "No Expulsivo";
    }

    /**
     * Calcula la ráfaga (burst time) basada en la cantidad de caracteres de la descripción.
     * TR = TH * (Cantidad caracteres de la descripción)
     */
    public int calcularRafaga(int th) {
        if (descripcion == null || descripcion.isEmpty()) return th;
        return th * descripcion.length();
    }
}
