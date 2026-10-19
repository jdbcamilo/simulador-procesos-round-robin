package com.unicordoba.simulador.service;

import com.unicordoba.simulador.model.Proceso;
import org.springframework.stereotype.Service;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.stream.Collectors;

/**
 * Servicio para capturar procesos del Sistema Operativo.
 * Utiliza comandos nativos de Windows para obtener información real de procesos.
 */
@Service
public class SistemaOperativoService {

    /**
     * Obtiene N procesos del sistema operativo, ordenados por el criterio especificado.
     * @param cantidad Número de procesos a obtener
     * @param criterio "CPU" o "MEMORIA"
     * @return Lista de procesos del SO
     */
    public List<Proceso> obtenerProcesos(int cantidad, String criterio) {
        List<Proceso> procesos = new ArrayList<>();

        try {
            String sortProperty = "MEMORIA".equalsIgnoreCase(criterio) ? "WorkingSet64" : "CPU";
            String psScript = "& { Get-Process | Where-Object { $_.Id -gt 0 -and $_.ProcessName -ne 'Idle' } | " +
                    "Sort-Object " + sortProperty + " -Descending | Select-Object -First " + (cantidad + 5) + " " +
                    "Id, ProcessName, " +
                    "@{N='UserName';E={ if ($_.ProcessName -match '(?i)svchost|services|lsass|csrss|winlogon|system|smss|sihost|taskhostw|fontdrvhost|dwm|wininit|RuntimeBroker|spoolsv|MsMpEng|Memory Compression') { 'SYSTEM' } else { $env:USERNAME } }}, " +
                    "@{N='Desc';E={ if ($_.Description -and $_.Description.Trim().Length -gt 0) { $_.Description } else { 'Proceso de ejecucion ' + $_.ProcessName } }}, " +
                    "@{N='CPUUsage';E={ if ($_.CPU) { [math]::Round($_.CPU, 2) } else { 0.0 } }}, " +
                    "@{N='MemUsage';E={ [math]::Round($_.WorkingSet64 / 1MB, 2) }} " +
                    "| ConvertTo-Csv -NoTypeInformation }";

            ProcessBuilder pb = new ProcessBuilder("powershell", "-NoProfile", "-Command", psScript);
            pb.redirectErrorStream(true);
            Process process = pb.start();

            BufferedReader reader = new BufferedReader(new InputStreamReader(process.getInputStream(), "UTF-8"));
            String line;
            boolean header = true;

            while ((line = reader.readLine()) != null && procesos.size() < cantidad) {
                if (header) {
                    header = false;
                    continue;
                }
                try {
                    String[] parts = parseCSVLine(line);
                    if (parts.length >= 6) {
                        Long pid = Long.parseLong(parts[0].trim().replace("\"", ""));
                        String nombre = parts[1].trim().replace("\"", "");
                        String usuario = parts[2].trim().replace("\"", "");
                        String descripcion = parts[3].trim().replace("\"", "");

                        if (nombre.isEmpty() || pid <= 0) continue;

                        boolean esSO = "SYSTEM".equalsIgnoreCase(usuario)
                                || usuario.toUpperCase().contains("LOCAL SERVICE")
                                || usuario.toUpperCase().contains("NETWORK SERVICE")
                                || nombre.matches("(?i).*(svchost|services|lsass|csrss|winlogon|system|smss|sihost|taskhostw|fontdrvhost|dwm|wininit|RuntimeBroker|spoolsv|MsMpEng|Memory Compression).*");

                        int prioridad = esSO ? 1 : 0;
                        if (esSO && !usuario.equalsIgnoreCase("SYSTEM")) {
                            usuario = "SYSTEM";
                        }

                        if (descripcion.isEmpty()) {
                            descripcion = "Proceso " + nombre + " del sistema";
                        }

                        // Limitar la longitud de la descripción para mantener ráfagas razonables para la simulación
                        if (descripcion.length() > 60) {
                            descripcion = descripcion.substring(0, 60);
                        }

                        Proceso p = new Proceso(pid, nombre, usuario, descripcion, prioridad);

                        try {
                            p.setUsoCpu(Double.parseDouble(parts[4].trim().replace("\"", "")));
                        } catch (NumberFormatException e) {
                            p.setUsoCpu(0.0);
                        }
                        try {
                            p.setUsoMemoria(Double.parseDouble(parts[5].trim().replace("\"", "")));
                        } catch (NumberFormatException e) {
                            p.setUsoMemoria(0.0);
                        }

                        procesos.add(p);
                    }
                } catch (Exception e) {
                    // Ignorar línea no procesable
                }
            }

            process.waitFor();

        } catch (Exception e) {
            // Fallback usando ProcessHandle de Java
            procesos = obtenerProcesosJava(cantidad);
        }

        // Si faltaron procesos, complementar con Java ProcessHandle
        if (procesos.size() < cantidad) {
            List<Proceso> javaProcesses = obtenerProcesosJava(cantidad - procesos.size());
            procesos.addAll(javaProcesses);
        }

        return procesos.stream().limit(cantidad).collect(Collectors.toList());
    }

    /**
     * Fallback: obtener procesos usando la API de Java (ProcessHandle).
     */
    private List<Proceso> obtenerProcesosJava(int cantidad) {
        return ProcessHandle.allProcesses()
            .filter(ph -> ph.info().command().isPresent())
            .limit(cantidad)
            .map(ph -> {
                ProcessHandle.Info info = ph.info();
                String nombre = info.command().map(c -> {
                    String[] parts = c.replace("\\", "/").split("/");
                    return parts[parts.length - 1].replace(".exe", "");
                }).orElse("Unknown");
                
                String usuario = info.user().orElse("SYSTEM");
                String descripcion = info.commandLine().orElse("Proceso " + nombre + " ejecutándose en el sistema");
                
                // Limitar descripción a 200 caracteres para que la ráfaga no sea enorme
                if (descripcion.length() > 200) {
                    descripcion = descripcion.substring(0, 200);
                }
                
                int prioridad = usuario.contains("SYSTEM") || usuario.contains("NT AUTHORITY") ? 1 : 0;
                
                Proceso p = new Proceso(ph.pid(), nombre, usuario, descripcion, prioridad);
                p.setUsoCpu(0.0);
                p.setUsoMemoria(0.0);
                return p;
            })
            .collect(Collectors.toList());
    }

    /**
     * Parse a CSV line respecting quoted fields.
     */
    private String[] parseCSVLine(String line) {
        List<String> result = new ArrayList<>();
        boolean inQuotes = false;
        StringBuilder current = new StringBuilder();

        for (char c : line.toCharArray()) {
            if (c == '"') {
                inQuotes = !inQuotes;
            } else if (c == ',' && !inQuotes) {
                result.add(current.toString());
                current = new StringBuilder();
            } else {
                current.append(c);
            }
        }
        result.add(current.toString());

        return result.toArray(new String[0]);
    }
}
