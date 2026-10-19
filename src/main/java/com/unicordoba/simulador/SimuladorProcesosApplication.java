package com.unicordoba.simulador;

import com.unicordoba.simulador.gui.AppEscritorioGUI;
import com.unicordoba.simulador.service.CatalogoService;
import com.unicordoba.simulador.service.SistemaOperativoService;
import org.springframework.boot.CommandLineRunner;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.builder.SpringApplicationBuilder;
import org.springframework.context.annotation.Bean;

import javax.swing.*;
import java.awt.*;

@SpringBootApplication
public class SimuladorProcesosApplication {

	public static void main(String[] args) {
		new SpringApplicationBuilder(SimuladorProcesosApplication.class)
				.headless(false)
				.run(args);
	}

	@Bean
	public CommandLineRunner launchDesktopGui(CatalogoService catalogoService, SistemaOperativoService soService) {
		return args -> {
			if (!GraphicsEnvironment.isHeadless()) {
				EventQueue.invokeLater(() -> {
					try {
						com.formdev.flatlaf.FlatLightLaf.setup();
					} catch (Exception ignored) {
						try {
							UIManager.setLookAndFeel(UIManager.getSystemLookAndFeelClassName());
						} catch (Exception ignored2) {}
					}
					AppEscritorioGUI gui = new AppEscritorioGUI(catalogoService, soService);
					gui.setVisible(true);
				});
			}
		};
	}
}

