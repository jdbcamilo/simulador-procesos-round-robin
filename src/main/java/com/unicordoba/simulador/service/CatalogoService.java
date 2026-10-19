package com.unicordoba.simulador.service;

import com.unicordoba.simulador.model.Catalogo;
import com.unicordoba.simulador.model.Proceso;
import com.unicordoba.simulador.repository.CatalogoRepository;
import com.unicordoba.simulador.repository.ProcesoRepository;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.util.List;
import java.util.Optional;

/**
 * Servicio principal para gestionar catálogos de procesos.
 */
@Service
public class CatalogoService {

    private final CatalogoRepository catalogoRepository;
    private final ProcesoRepository procesoRepository;
    private final SistemaOperativoService soService;

    @Value("${app.output-dir:./process-output}")
    private String outputDir;

    public CatalogoService(CatalogoRepository catalogoRepository,
                           ProcesoRepository procesoRepository,
                           SistemaOperativoService soService) {
        this.catalogoRepository = catalogoRepository;
        this.procesoRepository = procesoRepository;
        this.soService = soService;
    }

    /**
     * Crear un nuevo catálogo capturando procesos del SO.
     */
    @Transactional
    public Catalogo crearCatalogo(String nombreCatalogo, int cantidadProcesos, String criterio) {
        // Obtener siguiente número de catálogo
        int nextNum = catalogoRepository.findTopByOrderByNumeroCatalogoDesc()
                .map(c -> c.getNumeroCatalogo() + 1)
                .orElse(1);

        Catalogo catalogo = new Catalogo(nextNum, nombreCatalogo, criterio);

        // Obtener procesos del SO
        List<Proceso> procesos = soService.obtenerProcesos(cantidadProcesos, criterio);

        for (Proceso proceso : procesos) {
            catalogo.addProceso(proceso);
        }

        return catalogoRepository.save(catalogo);
    }

    /**
     * Obtener todos los catálogos almacenados.
     */
    public List<Catalogo> obtenerTodosCatalogos() {
        return catalogoRepository.findAll();
    }

    /**
     * Obtener un catálogo por su ID.
     */
    public Optional<Catalogo> obtenerCatalogoPorId(Long id) {
        return catalogoRepository.findById(id);
    }

    /**
     * Obtener un catálogo por su número.
     */
    public Optional<Catalogo> obtenerCatalogoPorNumero(Integer numero) {
        return catalogoRepository.findByNumeroCatalogo(numero);
    }

    /**
     * Obtener procesos de un catálogo.
     */
    public List<Proceso> obtenerProcesosDeCatalogo(Long catalogoId) {
        return procesoRepository.findByCatalogoId(catalogoId);
    }

    /**
     * Eliminar un catálogo por su ID.
     */
    @Transactional
    public void eliminarCatalogo(Long id) {
        catalogoRepository.deleteById(id);
    }

    /**
     * Guardar un catálogo con procesos previamente capturados y número personalizado.
     */
    @Transactional
    public Catalogo guardarCatalogoConProcesos(Integer numero, String nombre, String criterio, List<Proceso> procesos) {
        if (numero == null || numero <= 0) {
            numero = catalogoRepository.findTopByOrderByNumeroCatalogoDesc()
                    .map(c -> c.getNumeroCatalogo() + 1)
                    .orElse(1);
        }

        Catalogo catalogo = new Catalogo(numero, nombre, criterio);

        if (procesos != null) {
            for (Proceso p : procesos) {
                Proceso nuevo = new Proceso(p.getPid(), p.getNombre(), p.getUsuario(), p.getDescripcion(), p.getPrioridad());
                nuevo.setUsoCpu(p.getUsoCpu());
                nuevo.setUsoMemoria(p.getUsoMemoria());
                catalogo.addProceso(nuevo);
            }
        }

        return catalogoRepository.save(catalogo);
    }

    /**
     * Obtener directorio de salida de archivos de procesos.
     */
    public String getOutputDir() {
        return outputDir;
    }

    /**
     * Ejecuta la actividad de cada proceso en su propio hilo (Thread).
     * Crea un archivo con el nombre del proceso y escribe su descripción carácter por carácter.
     */
    public void ejecutarActividadEnHilos(List<Proceso> procesos, int th, java.util.function.Consumer<String> logConsumer) {
        if (procesos == null || procesos.isEmpty()) return;

        File dir = new File(outputDir);
        if (!dir.exists()) {
            dir.mkdirs();
        }

        for (Proceso p : procesos) {
            Thread t = new Thread(() -> {
                if (logConsumer != null) {
                    logConsumer.accept("Iniciando Thread para: " + p.getNombre() + " (PID: " + p.getPid() + ")");
                }
                try {
                    ejecutarActividadProceso(p, th);
                    if (logConsumer != null) {
                        logConsumer.accept("✔ Archivo generado: " + p.getNombre() + "_" + p.getPid() + ".txt");
                    }
                } catch (Exception e) {
                    if (logConsumer != null) {
                        logConsumer.accept("Error en proceso " + p.getNombre() + ": " + e.getMessage());
                    }
                }
            }, "Hilo-Proceso-" + p.getPid());
            t.start();
        }
    }

    /**
     * Genera la representación en formato XML de todos los catálogos almacenados.
     * Garantiza formato XML 100% válido y libre de errores de serialización.
     */
    public String generarCatalogosXml() {
        List<Catalogo> catalogos = obtenerTodosCatalogos();
        StringBuilder xml = new StringBuilder();
        xml.append("<?xml version=\"1.0\" encoding=\"UTF-8\"?>\n");
        xml.append("<catalogos total=\"").append(catalogos.size()).append("\">\n");

        for (Catalogo c : catalogos) {
            xml.append("  <catalogo id=\"").append(c.getId() != null ? c.getId() : "").append("\">\n");
            xml.append("    <numeroCatalogo>").append(c.getNumeroCatalogo() != null ? c.getNumeroCatalogo() : "").append("</numeroCatalogo>\n");
            xml.append("    <nombreCatalogo>").append(escapeXml(c.getNombreCatalogo())).append("</nombreCatalogo>\n");
            xml.append("    <criterioSeleccion>").append(escapeXml(c.getCriterioSeleccion())).append("</criterioSeleccion>\n");
            xml.append("    <fechaCreacion>").append(c.getFechaCreacion() != null ? c.getFechaCreacion().toString() : "").append("</fechaCreacion>\n");
            xml.append("    <procesos cantidad=\"").append(c.getProcesos() != null ? c.getProcesos().size() : 0).append("\">\n");

            if (c.getProcesos() != null) {
                for (Proceso p : c.getProcesos()) {
                    xml.append("      <proceso>\n");
                    xml.append("        <pid>").append(p.getPid()).append("</pid>\n");
                    xml.append("        <nombre>").append(escapeXml(p.getNombre())).append("</nombre>\n");
                    xml.append("        <usuario>").append(escapeXml(p.getUsuario())).append("</usuario>\n");
                    xml.append("        <descripcion>").append(escapeXml(p.getDescripcion())).append("</descripcion>\n");
                    xml.append("        <prioridad>").append(p.getPrioridad()).append("</prioridad>\n");
                    xml.append("        <tipoPrioridad>").append(escapeXml(p.getTipoPrioridad())).append("</tipoPrioridad>\n");
                    xml.append("        <usoCpu>").append(p.getUsoCpu() != null ? String.format(java.util.Locale.US, "%.2f", p.getUsoCpu()) : "0.00").append("</usoCpu>\n");
                    xml.append("        <usoMemoria>").append(p.getUsoMemoria() != null ? String.format(java.util.Locale.US, "%.2f", p.getUsoMemoria()) : "0.00").append("</usoMemoria>\n");
                    xml.append("      </proceso>\n");
                }
            }
            xml.append("    </procesos>\n");
            xml.append("  </catalogo>\n");
        }

        xml.append("</catalogos>");
        return xml.toString();
    }

    /**
     * Genera la representación XML de un catálogo individual.
     */
    public String generarCatalogoIndividualXml(Long id) {
        Optional<Catalogo> opt = obtenerCatalogoPorId(id);
        if (opt.isEmpty()) return null;
        Catalogo c = opt.get();

        StringBuilder xml = new StringBuilder();
        xml.append("<?xml version=\"1.0\" encoding=\"UTF-8\"?>\n");
        xml.append("<catalogo id=\"").append(c.getId()).append("\">\n");
        xml.append("  <numeroCatalogo>").append(c.getNumeroCatalogo()).append("</numeroCatalogo>\n");
        xml.append("  <nombreCatalogo>").append(escapeXml(c.getNombreCatalogo())).append("</nombreCatalogo>\n");
        xml.append("  <criterioSeleccion>").append(escapeXml(c.getCriterioSeleccion())).append("</criterioSeleccion>\n");
        xml.append("  <fechaCreacion>").append(c.getFechaCreacion() != null ? c.getFechaCreacion().toString() : "").append("</fechaCreacion>\n");
        xml.append("  <procesos cantidad=\"").append(c.getProcesos() != null ? c.getProcesos().size() : 0).append("\">\n");

        if (c.getProcesos() != null) {
            for (Proceso p : c.getProcesos()) {
                xml.append("    <proceso>\n");
                xml.append("      <pid>").append(p.getPid()).append("</pid>\n");
                xml.append("      <nombre>").append(escapeXml(p.getNombre())).append("</nombre>\n");
                xml.append("      <usuario>").append(escapeXml(p.getUsuario())).append("</usuario>\n");
                xml.append("      <descripcion>").append(escapeXml(p.getDescripcion())).append("</descripcion>\n");
                xml.append("      <prioridad>").append(p.getPrioridad()).append("</prioridad>\n");
                xml.append("      <tipoPrioridad>").append(escapeXml(p.getTipoPrioridad())).append("</tipoPrioridad>\n");
                xml.append("      <usoCpu>").append(p.getUsoCpu() != null ? String.format(java.util.Locale.US, "%.2f", p.getUsoCpu()) : "0.00").append("</usoCpu>\n");
                xml.append("      <usoMemoria>").append(p.getUsoMemoria() != null ? String.format(java.util.Locale.US, "%.2f", p.getUsoMemoria()) : "0.00").append("</usoMemoria>\n");
                xml.append("    </proceso>\n");
            }
        }
        xml.append("  </procesos>\n");
        xml.append("</catalogo>");
        return xml.toString();
    }

    private String escapeXml(String text) {
        if (text == null) return "";
        return text.replace("&", "&amp;")
                .replace("<", "&lt;")
                .replace(">", "&gt;")
                .replace("\"", "&quot;")
                .replace("'", "&apos;");
    }

    /**
     * Ejecutar la actividad de un proceso: crear un archivo con el nombre del proceso
     * y escribir su descripción carácter por carácter (simulando quantum).
     */
    public void ejecutarActividadProceso(Proceso proceso, int th) throws IOException, InterruptedException {
        File dir = new File(outputDir);
        if (!dir.exists()) {
            dir.mkdirs();
        }

        File archivo = new File(dir, proceso.getNombre() + "_" + proceso.getPid() + ".txt");
        String descripcion = proceso.getDescripcion();

        if (descripcion == null || descripcion.isEmpty()) {
            descripcion = "Proceso " + proceso.getNombre() + " ejecutado.";
        }

        try (FileWriter writer = new FileWriter(archivo)) {
            for (int i = 0; i < descripcion.length(); i++) {
                writer.write(descripcion.charAt(i));
                writer.flush();
                if (th > 0) {
                    Thread.sleep(th); // Esperar TH milisegundos por cada carácter
                }
            }
        }
    }
}
