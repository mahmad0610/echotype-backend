package com.example.echotype;

import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;
import java.io.*;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.io.ClassPathResource;

@RestController
@CrossOrigin(origins = "*")
public class NoteController {

    private static final Logger logger = LoggerFactory.getLogger(NoteController.class);

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

            // Run transcribe.py script
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
                String line;
                while ((line = whisperReader.readLine()) != null) {
                    transcriptionOutput.append(line).append("\n");
                }
            }
            
            int whisperExitCode = whisperProcess.waitFor();
            if (whisperExitCode != 0) {
                logger.error("Whisper transcription failed with exit code {}: {}", 
                            whisperExitCode, transcriptionOutput.toString());
                response.put("error", "Transcription failed: " + transcriptionOutput.toString());
                return response;
            }
            
            String transcription = transcriptionOutput.toString().trim();
            logger.info("Transcription: {}", transcription);

            // Create temporary transcription file
            tempTranscription = File.createTempFile("transcription", ".txt");
            Files.writeString(tempTranscription.toPath(), transcription);
            logger.info("Transcription file saved to: {}", tempTranscription.getAbsolutePath());

            // Run format_notes.py script
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
                String line;
                while ((line = geminiReader.readLine()) != null) {
                    formattingOutput.append(line).append("\n");
                }
            }
            
            int geminiExitCode = geminiProcess.waitFor();
            if (geminiExitCode != 0) {
                logger.error("Formatting failed with exit code {}: {}", 
                            geminiExitCode, formattingOutput.toString());
                response.put("error", "Formatting failed: " + formattingOutput.toString());
                return response;
            }
            
            String formattedNotes = formattingOutput.toString().trim();
            logger.info("Formatted Notes: {}", formattedNotes);

            response.put("formattedNotes", formattedNotes.isEmpty() ? "No notes generated" : formattedNotes);
            return response;

        } catch (IOException | InterruptedException e) {
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
                String line;
                while ((line = reader.readLine()) != null) {
                    output.append(line).append("\n");
                }
            }
            
            int exitCode = process.waitFor();
            if (exitCode != 0) {
                response.put("error", "Whisper test failed: " + output.toString());
                return response;
            }
            
            response.put("result", "Whisper test successful: " + output.toString());
            return response;
        } catch (IOException | InterruptedException e) {
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
                String line;
                while ((line = reader.readLine()) != null) {
                    output.append(line).append("\n");
                }
            }
            
            int exitCode = process.waitFor();
            if (exitCode != 0) {
                response.put("error", "Gemini test failed: " + output.toString());
                return response;
            }
            
            response.put("result", "Gemini test successful: " + output.toString());
            return response;
        } catch (IOException | InterruptedException e) {
            response.put("error", "Gemini test failed: " + e.getMessage());
            return response;
        } finally {
            if (tempFile != null && tempFile.exists()) tempFile.delete();
        }
    }
}
