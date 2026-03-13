package com.loannsmp.launcher;

import java.io.File;
import java.util.ArrayList;
import java.util.List;

public class LauncherWrapper {
    public static void main(String[] args) throws Exception {
        // Check if we're already running with JavaFX modules
        try {
            Class.forName("javafx.application.Application");
            // Already has JavaFX, launch main app
            LauncherApp.main(args);
            return;
        } catch (ClassNotFoundException e) {
            // JavaFX not available, restart with proper modules
        }
        
        // Restart with JavaFX modules
        String javaHome = System.getProperty("java.home");
        String javaPath = javaHome + File.separator + "bin" + File.separator + "java";
        
        List<String> command = new ArrayList<>();
        command.add(javaPath);
        
        // Add JavaFX module path and modules
        String classpath = System.getProperty("java.class.path");
        command.add("--module-path");
        command.add(classpath);
        command.add("--add-modules");
        command.add("javafx.controls,javafx.fxml,javafx.graphics");
        
        // Add current classpath
        command.add("-cp");
        command.add(classpath);
        
        // Add main class
        command.add(LauncherApp.class.getName());
        
        // Add original arguments
        command.addAll(List.of(args));
        
        // Start new process
        ProcessBuilder processBuilder = new ProcessBuilder(command);
        processBuilder.inheritIO();
        Process process = processBuilder.start();
        System.exit(process.waitFor());
    }
}
