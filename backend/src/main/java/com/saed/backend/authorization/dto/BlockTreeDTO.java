package com.saed.backend.authorization.dto;

import java.util.ArrayList;
import java.util.List;

public class BlockTreeDTO {
    private Long id;
    private Long idPropiedad;
    private Long idBloquePadre;
    private String tipo;
    private String codigo;
    private String nombre;
    private Integer orden;
    private String estado;
    private Integer totalUnidades;
    private List<BlockTreeDTO> children = new ArrayList<>();

    public BlockTreeDTO() {}

    public BlockTreeDTO(BlockDTO block) {
        this.id = block.getId();
        this.idPropiedad = block.getIdPropiedad();
        this.idBloquePadre = block.getIdBloquePadre();
        this.tipo = block.getTipo();
        this.codigo = block.getCodigo();
        this.nombre = block.getNombre();
        this.orden = block.getOrden();
        this.estado = block.getEstado();
        this.totalUnidades = block.getTotalUnidades();
    }

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }

    public Long getIdPropiedad() { return idPropiedad; }
    public void setIdPropiedad(Long idPropiedad) { this.idPropiedad = idPropiedad; }

    public Long getIdBloquePadre() { return idBloquePadre; }
    public void setIdBloquePadre(Long idBloquePadre) { this.idBloquePadre = idBloquePadre; }

    public String getTipo() { return tipo; }
    public void setTipo(String tipo) { this.tipo = tipo; }

    public String getCodigo() { return codigo; }
    public void setCodigo(String codigo) { this.codigo = codigo; }

    public String getNombre() { return nombre; }
    public void setNombre(String nombre) { this.nombre = nombre; }

    public Integer getOrden() { return orden; }
    public void setOrden(Integer orden) { this.orden = orden; }

    public String getEstado() { return estado; }
    public void setEstado(String estado) { this.estado = estado; }

    public Integer getTotalUnidades() { return totalUnidades; }
    public void setTotalUnidades(Integer totalUnidades) { this.totalUnidades = totalUnidades; }

    public List<BlockTreeDTO> getChildren() { return children; }
    public void setChildren(List<BlockTreeDTO> children) { this.children = children; }

    public void addChild(BlockTreeDTO child) {
        if (this.children == null) {
            this.children = new ArrayList<>();
        }
        this.children.add(child);
    }
}
