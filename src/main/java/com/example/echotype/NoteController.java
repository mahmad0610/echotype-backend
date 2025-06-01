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
        File tempTranscription = null;
        
        try {
            // Create temporary audio file
            tempFile = File.createTempFile("audio", ".wav");
            audioFile.transferTo(tempFile);
            logger.info("Audio file saved to: {}", tempFile.getAbsolutePath());

            // Run transcribe.py script with timeout
            ProcessBuilder whisperPb = new ProcessBuilder(
                "python3", 
                "/app/scripts/transcribe.py", 
                tempFile.getAbsolutePath()
            );
            whisperPb.redirectErrorStream(true);
            Process whisperProcess = whisperPb.start();
            
            StringBuilder transcriptionOutput = new StringBuilder();
            try (BufferedReader whisperReader = new BufferedReader(
                    new InputStreamReader(whisperProcess.getInputStream()))) {
                // Wait for process to complete with timeout
                boolean completed = whisperProcess.waitFor(PROCESS_TIMEOUT_SECONDS, TimeUnit.SECONDS);
                if (!completed) {
                    whisperProcess.destroy();
                    throw new RuntimeException("Transcription process timed out");
                }
                
                // Read output after process completes
                String line;
                while ((line = whisperReader.readLine()) != null) {
                    transcriptionOutput.append(line).append("\n");
                }
            }
            
            int whisperExitCode = whisperProcess.exitValue();
            if (whisperExitCode != 0) {
                logger.error("Whisper transcription failed with exit code {}: {}", 
                            whisperExitCode, transcriptionOutput.toString());
                response.put("error", "Transcription failed (code " + whisperExitCode + "): " + transcriptionOutput.toString());
                return response;
            }
            
            String transcription = transcriptionOutput.toString().trim();
            logger.info("Transcription: {}", transcription);

            // Create temporary transcription file
            tempTranscription = File.createTempFile("transcription", ".txt");
            Files.writeString(tempTranscription.toPath(), transcription);
            logger.info("Transcription file saved to: {}", tempTranscription.getAbsolutePath());

            // Run format_notes.py script with timeout
            ProcessBuilder geminiPb = new ProcessBuilder(
                "python3", 
                "/app/scripts/format_notes.py", 
                tempTranscription.getAbsolutePath()
            );
            geminiPb.redirectErrorStream(true);
            Process geminiProcess = geminiPb.start();
            
            StringBuilder formattingOutput = new StringBuilder();
            try (BufferedReader geminiReader = new BufferedReader(
                    new InputStreamReader(geminiProcess.getInputStream()))) {
                // Wait for process to complete with timeout
                boolean completed = geminiProcess.waitFor(PROCESS_TIMEOUT_SECONDS, TimeUnit.SECONDS);
                if (!completed) {
                    geminiProcess.destroy();
                    throw new RuntimeException("Formatting process timed out");
                }
                
                // Read output after process completes
                String line;
                while ((line = geminiReader.readLine()) != null) {
                    formattingOutput.append(line).append("\n");
                }
            }
            
            int geminiExitCode = geminiProcess.exitValue();
            if (geminiExitCode != 0) {
                logger.error("Formatting failed with exit code {}: {}", 
                            geminiExitCode, formattingOutput.toString());
                response.put("error", "Formatting failed (code " + geminiExitCode + "): " + formattingOutput.toString());
                return response;
            }
            
            String formattedNotes = formattingOutput.toString().trim();
            logger.info("Formatted Notes: {}", formattedNotes);

            response.put("formattedNotes", formattedNotes.isEmpty() ? "No notes generated" : formattedNotes);
            return response;

        } catch (Exception e) {
            logger.error("Error processing audio: {}", e.getMessage(), e);
            response.put("error", "Failed to process audio: " + e.getMessage());
            return response;
        } finally {
            // Clean up temporary files
            if (tempFile != null && tempFile.exists()) tempFile.delete();
            if (tempTranscription != null && tempTranscription.exists()) tempTranscription.delete();
        }
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
