package com.example.echotype;

import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;
import java.io.*;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.TimeUnit;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@RestController
@CrossOrigin(origins = "*")
public class NoteController {

    private static final Logger logger = LoggerFactory.getLogger(NoteController.class);
    private static final int PROCESS_TIMEOUT_SECONDS = 60; // Reduced to 1 minute

    @PostMapping("/transcribe")
public Map<String, String> transcribeAudio(@RequestParam("audio") MultipartFile audioFile) {
    Map<String, String> response = new HashMap<>();
    File tempFile = null;
    
    try {
        // Create temporary audio file
        tempFile = File.createTempFile("audio_", ".wav");
        audioFile.transferTo(tempFile);
        logger.info("Saved audio to: {}", tempFile.getAbsolutePath());

        // Build process with absolute paths
        ProcessBuilder whisperPb = new ProcessBuilder(
            "python3",
            "/app/scripts/transcribe.py",  // Updated to Docker container path
            tempFile.getAbsolutePath()
        );
        
        // Redirect errors to merge with standard output
        whisperPb.redirectErrorStream(true);
        
        // Start process with timeout
        Process whisperProcess = whisperPb.start();
        StringBuilder output = new StringBuilder();
        
        try (BufferedReader reader = new BufferedReader(
                new InputStreamReader(whisperProcess.getInputStream()))) {
            
            // Read output while process runs
            String line;
            while ((line = reader.readLine()) != null) {
                output.append(line).append("\n");
                logger.debug("Whisper: {}", line);
            }
            
            // Wait with timeout
            boolean completed = whisperProcess.waitFor(5, TimeUnit.MINUTES);
            if (!completed) {
                whisperProcess.destroyForcibly();
                throw new RuntimeException("Transcription timed out after 5 minutes");
            }
            
            // Check exit status
            int exitCode = whisperProcess.exitValue();
            if (exitCode != 0) {
                throw new RuntimeException(String.format(
                    "Transcription failed (code %d): %s",
                    exitCode,
                    output.toString().trim()
                ));
            }
            
            // Success case
            String transcription = output.toString().trim();
            response.put("transcription", transcription);
            logger.info("Successfully transcribed audio");
            
        } finally {
            whisperProcess.destroy();
        }
        
    } catch (IOException e) {
        logger.error("File operation failed: {}", e.getMessage());
        response.put("error", "File processing error: " + e.getMessage());
    } catch (InterruptedException e) {
        logger.error("Transcription interrupted: {}", e.getMessage());
        Thread.currentThread().interrupt();
        response.put("error", "Processing interrupted");
    } catch (Exception e) {
        logger.error("Transcription failed: {}", e.getMessage());
        response.put("error", "Transcription error: " + e.getMessage());
    } finally {
        // Clean up temp file
        if (tempFile != null && tempFile.exists()) {
            try {
                Files.delete(tempFile.toPath());
            } catch (IOException e) {
                logger.warn("Could not delete temp file: {}", tempFile.getAbsolutePath());
            }
        }
    }
    
    return response;
}
    @GetMapping("/test-whisper")
    public Map<String, String> testWhisper() {
        Map<String, String> response = new HashMap<>();
        File tempFile = null;
        
        try {
            tempFile = File.createTempFile("test", ".wav");
            Files.writeString(tempFile.toPath(), "This is a test file for Whisper");
            
            ProcessBuilder pb = new ProcessBuilder(
                "python3", 
                "/app/scripts/transcribe.py", 
                tempFile.getAbsolutePath()
            );
            pb.redirectErrorStream(true);
            Process process = pb.start();
            
            StringBuilder output = new StringBuilder();
            try (BufferedReader reader = new BufferedReader(
                    new InputStreamReader(process.getInputStream()))) {
                // Wait for process to complete with timeout
                boolean completed = process.waitFor(PROCESS_TIMEOUT_SECONDS, TimeUnit.SECONDS);
                if (!completed) {
                    process.destroy();
                    throw new RuntimeException("Test process timed out");
                }
                
                // Read output after process completes
                String line;
                while ((line = reader.readLine()) != null) {
                    output.append(line).append("\n");
                }
            }
            
            int exitCode = process.exitValue();
            if (exitCode != 0) {
                response.put("error", "Whisper test failed (code " + exitCode + "): " + output.toString());
                return response;
            }
            
            response.put("result", "Whisper test successful: " + output.toString());
            return response;
        } catch (Exception e) {
            response.put("error", "Whisper test failed: " + e.getMessage());
            return response;
        } finally {
            if (tempFile != null && tempFile.exists()) tempFile.delete();
        }
    }

    @GetMapping("/test-gemini")
    public Map<String, String> testGemini() {
        Map<String, String> response = new HashMap<>();
        File tempFile = null;
        
        try {
            tempFile = File.createTempFile("test", ".txt");
            Files.writeString(tempFile.toPath(), "This is a test transcription for Gemini");
            
            ProcessBuilder pb = new ProcessBuilder(
                "python3", 
                "/app/scripts/format_notes.py", 
                tempFile.getAbsolutePath()
            );
            pb.redirectErrorStream(true);
            Process process = pb.start();
            
            StringBuilder output = new StringBuilder();
            try (BufferedReader reader = new BufferedReader(
                    new InputStreamReader(process.getInputStream()))) {
                // Wait for process to complete with timeout
                boolean completed = process.waitFor(PROCESS_TIMEOUT_SECONDS, TimeUnit.SECONDS);
                if (!completed) {
                    process.destroy();
                    throw new RuntimeException("Test process timed out");
                }
                
                // Read output after process completes
                String line;
                while ((line = reader.readLine()) != null) {
                    output.append(line).append("\n");
                }
            }
            
            int exitCode = process.exitValue();
            if (exitCode != 0) {
                response.put("error", "Gemini test failed (code " + exitCode + "): " + output.toString());
                return response;
            }
            
            response.put("result", "Gemini test successful: " + output.toString());
            return response;
        } catch (Exception e) {
            response.put("error", "Gemini test failed: " + e.getMessage());
            return response;
        } finally {
            if (tempFile != null && tempFile.exists()) tempFile.delete();
        }
    }
}
