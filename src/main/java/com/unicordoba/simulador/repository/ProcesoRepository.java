package com.unicordoba.simulador.repository;

import com.unicordoba.simulador.model.Proceso;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface ProcesoRepository extends JpaRepository<Proceso, Long> {
    List<Proceso> findByCatalogoId(Long catalogoId);
}
