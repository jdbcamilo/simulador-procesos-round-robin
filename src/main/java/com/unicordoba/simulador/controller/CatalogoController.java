package com.unicordoba.simulador.controller;

import com.unicordoba.simulador.model.Catalogo;
import com.unicordoba.simulador.model.Proceso;
import com.unicordoba.simulador.service.CatalogoService;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

/**
 * Controlador REST para gestión de catálogos y procesos.
 * Produce respuestas en XML y JSON.
 */
@RestController
@RequestMapping("/api")
@CrossOrigin(origins = "*")
public class CatalogoController {

    private final CatalogoService catalogoService;
    private final com.unicordoba.simulador.service.SistemaOperativoService soService;

    public CatalogoController(CatalogoService catalogoService,
                              com.unicordoba.simulador.service.SistemaOperativoService soService) {
        this.catalogoService = catalogoService;
        this.soService = soService;
    }

    // ==================== CATÁLOGOS ====================

    /**
     * Crear un nuevo catálogo capturando procesos del SO.
     * POST /api/catalogos
     * Body: { "nombre": "...", "cantidad": N, "criterio": "CPU"|"MEMORIA" }
     */
    @PostMapping(value = "/catalogos",
            consumes = MediaType.APPLICATION_JSON_VALUE,
            produces = {MediaType.APPLICATION_XML_VALUE, MediaType.APPLICATION_JSON_VALUE})
    public ResponseEntity<Catalogo> crearCatalogo(@RequestBody Map<String, Object> request) {
        String nombre = (String) request.getOrDefault("nombre", "Catálogo Sin Nombre");
        int cantidad = Integer.parseInt(request.getOrDefault("cantidad", "5").toString());
        String criterio = (String) request.getOrDefault("criterio", "CPU");

        if (cantidad < 1) {
            cantidad = 5;
        }

        Catalogo catalogo = catalogoService.crearCatalogo(nombre, cantidad, criterio);
        return ResponseEntity.ok(catalogo);
    }

    /**
     * Obtener todos los catálogos.
     * GET /api/catalogos
     */
    @GetMapping(value = "/catalogos",
            produces = {MediaType.APPLICATION_XML_VALUE, MediaType.APPLICATION_JSON_VALUE})
    public ResponseEntity<List<Catalogo>> obtenerCatalogos() {
        List<Catalogo> catalogos = catalogoService.obtenerTodosCatalogos();
        return ResponseEntity.ok(catalogos);
    }

    /**
     * Endpoint explícito para obtener catálogos en XML para visualización directa en navegador y consumo REST.
     * GET /api/catalogos/xml
     */
    @GetMapping(value = "/catalogos/xml", produces = MediaType.APPLICATION_XML_VALUE)
    public ResponseEntity<String> obtenerCatalogosXml() {
        return ResponseEntity.ok()
                .contentType(MediaType.APPLICATION_XML)
                .body(catalogoService.generarCatalogosXml());
    }

    /**
     * Endpoint explícito para obtener un catálogo individual en XML.
     * GET /api/catalogos/{id}/xml
     */
    @GetMapping(value = "/catalogos/{id}/xml", produces = MediaType.APPLICATION_XML_VALUE)
    public ResponseEntity<String> obtenerCatalogoIndividualXml(@PathVariable Long id) {
        String xml = catalogoService.generarCatalogoIndividualXml(id);
        if (xml == null) return ResponseEntity.notFound().build();
        return ResponseEntity.ok()
                .contentType(MediaType.APPLICATION_XML)
                .body(xml);
    }

    /**
     * Obtener un catálogo por ID.
     * GET /api/catalogos/{id}
     */
    @GetMapping(value = "/catalogos/{id}",
            produces = {MediaType.APPLICATION_XML_VALUE, MediaType.APPLICATION_JSON_VALUE})
    public ResponseEntity<Catalogo> obtenerCatalogo(@PathVariable Long id) {
        return catalogoService.obtenerCatalogoPorId(id)
                .map(ResponseEntity::ok)
                .orElse(ResponseEntity.notFound().build());
    }

    /**
     * Obtener catálogo por número de catálogo.
     * GET /api/catalogos/numero/{numero}
     */
    @GetMapping(value = "/catalogos/numero/{numero}",
            produces = {MediaType.APPLICATION_XML_VALUE, MediaType.APPLICATION_JSON_VALUE})
    public ResponseEntity<Catalogo> obtenerCatalogoPorNumero(@PathVariable Integer numero) {
        return catalogoService.obtenerCatalogoPorNumero(numero)
                .map(ResponseEntity::ok)
                .orElse(ResponseEntity.notFound().build());
    }

    /**
     * Eliminar un catálogo.
     * DELETE /api/catalogos/{id}
     */
    @DeleteMapping("/catalogos/{id}")
    public ResponseEntity<Void> eliminarCatalogo(@PathVariable Long id) {
        catalogoService.eliminarCatalogo(id);
        return ResponseEntity.noContent().build();
    }

    /**
     * Ejecutar la actividad de los procesos de un catálogo en sus propios Threads.
     * POST /api/catalogos/{id}/ejecutar-actividad?th=100
     */
    @PostMapping("/catalogos/{id}/ejecutar-actividad")
    public ResponseEntity<Map<String, Object>> ejecutarActividad(
            @PathVariable Long id,
            @RequestParam(defaultValue = "50") int th) {
        List<Proceso> procesos = catalogoService.obtenerProcesosDeCatalogo(id);
        if (procesos.isEmpty()) {
            return ResponseEntity.notFound().build();
        }

        catalogoService.ejecutarActividadEnHilos(procesos, th, null);

        return ResponseEntity.ok(Map.of(
                "status", "success",
                "mensaje", "Actividad iniciada en hilos para " + procesos.size() + " procesos.",
                "directorio", catalogoService.getOutputDir(),
                "th", th
        ));
    }

    // ==================== PROCESOS ====================

    /**
     * Obtener procesos de un catálogo.
     * GET /api/catalogos/{id}/procesos
     */
    @GetMapping(value = "/catalogos/{id}/procesos",
            produces = {MediaType.APPLICATION_XML_VALUE, MediaType.APPLICATION_JSON_VALUE})
    public ResponseEntity<List<Proceso>> obtenerProcesos(@PathVariable Long id) {
        List<Proceso> procesos = catalogoService.obtenerProcesosDeCatalogo(id);
        return ResponseEntity.ok(procesos);
    }

    /**
     * Obtener procesos directamente del SO (en vivo, sin guardar en BD).
     * GET /api/procesos/sistema?cantidad=N&criterio=CPU|MEMORIA
     */
    @GetMapping(value = "/procesos/sistema",
            produces = {MediaType.APPLICATION_XML_VALUE, MediaType.APPLICATION_JSON_VALUE})
    public ResponseEntity<List<Proceso>> obtenerProcesosDelSistema(
            @RequestParam(defaultValue = "10") int cantidad,
            @RequestParam(defaultValue = "CPU") String criterio) {
        List<Proceso> procesos = soService.obtenerProcesos(cantidad, criterio);
        return ResponseEntity.ok(procesos);
    }
}
