package com.unicordoba.simulador.repository;

import com.unicordoba.simulador.model.Catalogo;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface CatalogoRepository extends JpaRepository<Catalogo, Long> {
    Optional<Catalogo> findByNumeroCatalogo(Integer numeroCatalogo);
    Optional<Catalogo> findTopByOrderByNumeroCatalogoDesc();
}
