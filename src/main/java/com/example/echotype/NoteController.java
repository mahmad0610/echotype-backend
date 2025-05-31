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

    private File extractScriptFromClasspath(String scriptName) throws IOException {
        ClassPathResource resource = new ClassPathResource("scripts/" + scriptName);
        File tempScript = File.createTempFile(scriptName.replace(".py", ""), ".py");
        try (InputStream inputStream = resource.getInputStream()) {
            Files.copy(inputStream, tempScript.toPath(), java.nio.file.StandardCopyOption.REPLACE_EXISTING);
        }
        tempScript.setExecutable(true);
        return tempScript;
    }

    @PostMapping("/transcribe")
    public Map<String, String> transcribeAudio(@RequestParam("audio") MultipartFile audioFile) {
        Map<String, String> response = new HashMap<>();
        File tempTranscribeScript = null;
        File tempFormatScript = null;
        try {
            File tempFile = File.createTempFile("audio", ".wav");
            audioFile.transferTo(tempFile);
            logger.info("Audio file saved to: {}", tempFile.getAbsolutePath());

            // Extract transcribe.py from JAR
            tempTranscribeScript = extractScriptFromClasspath("transcribe.py");
            logger.info("Transcribe script extracted to: {}", tempTranscribeScript.getAbsolutePath());

            ProcessBuilder whisperPb = new ProcessBuilder("/app/venv/bin/python3", tempTranscribeScript.getAbsolutePath(), tempFile.getAbsolutePath());
            whisperPb.redirectErrorStream(true);
            Process whisperProcess = whisperPb.start();
            StringBuilder transcriptionOutput = new StringBuilder();
            try (BufferedReader whisperReader = new BufferedReader(new InputStreamReader(whisperProcess.getInputStream()))) {
                String line;
                while ((line = whisperReader.readLine()) != null) {
                    transcriptionOutput.append(line).append("\n");
                }
            }
            int whisperExitCode = whisperProcess.waitFor();
            if (whisperExitCode != 0) {
                logger.error("Whisper transcription failed with exit code {}: {}", whisperExitCode, transcriptionOutput.toString());
                response.put("error", "Transcription failed: " + transcriptionOutput.toString());
                return response;
            }
            String transcription = transcriptionOutput.toString().trim();
            logger.info("Transcription: {}", transcription);

            File tempTranscription = File.createTempFile("transcription", ".txt");
            Files.writeString(tempTranscription.toPath(), transcription);
            logger.info("Transcription file saved to: {}", tempTranscription.getAbsolutePath());

            // Extract format_notes.py from JAR
            tempFormatScript = extractScriptFromClasspath("format_notes.py");
            logger.info("Format script extracted to: {}", tempFormatScript.getAbsolutePath());

            ProcessBuilder geminiPb = new ProcessBuilder("/app/venv/bin/python3", tempFormatScript.getAbsolutePath(), tempTranscription.getAbsolutePath());
            geminiPb.redirectErrorStream(true);
            Process geminiProcess = geminiPb.start();
            StringBuilder formattingOutput = new StringBuilder();
            try (BufferedReader geminiReader = new BufferedReader(new InputStreamReader(geminiProcess.getInputStream()))) {
                String line;
                while ((line = geminiReader.readLine()) != null) {
                    formattingOutput.append(line).append("\n");
                }
            }
            int geminiExitCode = geminiProcess.waitFor();
            if (geminiExitCode != 0) {
                logger.error("Formatting failed with exit code {}: {}", geminiExitCode, formattingOutput.toString());
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
            if (tempFile != null && tempFile.exists()) tempFile.delete();
            if (tempTranscription != null && tempTranscription.exists()) tempTranscription.delete();
            if (tempTranscribeScript != null && tempTranscribeScript.exists()) tempTranscribeScript.delete();
            if (tempFormatScript != null && tempFormatScript.exists()) tempFormatScript.delete();
        }
    }

    @GetMapping("/test-whisper")
    public Map<String, String> testWhisper() {
        Map<String, String> response = new HashMap<>();
        File tempScript = null;
        try {
            File tempFile = File.createTempFile("test", ".wav");
            Files.writeString(tempFile.toPath(), "This is a test file for Whisper");
            tempScript = extractScriptFromClasspath("transcribe.py");
            ProcessBuilder pb = new ProcessBuilder("/app/venv/bin/python3", tempScript.getAbsolutePath(), tempFile.getAbsolutePath());
            pb.redirectErrorStream(true);
            Process process = pb.start();
            StringBuilder output = new StringBuilder();
            try (BufferedReader reader = new BufferedReader(new InputStreamReader(process.getInputStream()))) {
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
            if (tempScript != null && tempScript.exists()) tempScript.delete();
        }
    }

    @GetMapping("/test-gemini")
    public Map<String, String> testGemini() {
        Map<String, String> response = new HashMap<>();
        File tempScript = null;
        try {
            File tempFile = File.createTempFile("test", ".txt");
            Files.writeString(tempFile.toPath(), "This is a test transcription for Gemini");
            tempScript = extractScriptFromClasspath("format_notes.py");
            ProcessBuilder pb = new ProcessBuilder("/app/venv/bin/python3", tempScript.getAbsolutePath(), tempFile.getAbsolutePath());
            pb.redirectErrorStream(true);
            Process process = pb.start();
            StringBuilder output = new StringBuilder();
            try (BufferedReader reader = new BufferedReader(new InputStreamReader(process.getInputStream()))) {
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
            if (tempScript != null && tempScript.exists()) tempScript.delete();
        }
    }
}
