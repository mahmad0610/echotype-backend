package com.example.echotype;

import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;
import java.io.*;
import java.nio.file.Files;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.TimeUnit;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.io.ClassPathResource;

@RestController
@CrossOrigin(origins = "*")
public class NoteController {

    private static final Logger logger = LoggerFactory.getLogger(NoteController.class);

    private File extractScriptFromClasspath(String scriptName) throws IOException {
        logger.info("Extracting script: {}", scriptName);
        ClassPathResource resource = new ClassPathResource("scripts/" + scriptName);
        if (!resource.exists()) {
            throw new IOException("Script not found in classpath: " + scriptName);
        }
        File tempScript = File.createTempFile(scriptName.replace(".py", ""), ".py");
        try (InputStream inputStream = resource.getInputStream()) {
            Files.copy(inputStream, tempScript.toPath(), java.nio.file.StandardCopyOption.REPLACE_EXISTING);
        }
        tempScript.setExecutable(true);
        logger.info("Script extracted to: {}", tempScript.getAbsolutePath());
        return tempScript;
    }

    @PostMapping("/transcribe")
    public Map<String, String> transcribeAudio(@RequestParam("audio") MultipartFile audioFile) {
        Map<String, String> response = new HashMap<>();
        File tempFile = null;
        File tempTranscription = null;
        File tempTranscribeScript = null;
        File tempFormatScript = null;

        try {
            logger.info("Received /transcribe request with file: {}", audioFile.getOriginalFilename());

            // Create temp audio file
            tempFile = File.createTempFile("audio", ".wav");
            audioFile.transferTo(tempFile);
            logger.info("Audio file saved to: {}", tempFile.getAbsolutePath());

            // Extract and execute transcribe script
            tempTranscribeScript = extractScriptFromClasspath("transcribe.py");
            logger.info("Starting transcription process...");

            ProcessBuilder whisperPb = new ProcessBuilder("python3", 
                                                        tempTranscribeScript.getAbsolutePath(), 
                                                        tempFile.getAbsolutePath());
            whisperPb.redirectErrorStream(true);
            Process whisperProcess = whisperPb.start();

            StringBuilder transcriptionOutput = new StringBuilder();
            StringBuilder transcriptionError = new StringBuilder();
            // Capture stdout
            Thread stdoutThread = new Thread(() -> {
                try (BufferedReader reader = new BufferedReader(
                        new InputStreamReader(whisperProcess.getInputStream()))) {
                    String line;
                    while ((line = reader.readLine()) != null) {
                        logger.info("Transcription output: {}", line);
                        transcriptionOutput.append(line).append("\n");
                    }
                } catch (IOException e) {
                    logger.error("Error reading transcription stdout: {}", e.getMessage());
                }
            });
            stdoutThread.start();

            // Capture stderr
            Thread stderrThread = new Thread(() -> {
                try (BufferedReader reader = new BufferedReader(
                        new InputStreamReader(whisperProcess.getErrorStream()))) {
                    String line;
                    while ((line = reader.readLine()) != null) {
                        logger.error("Transcription error: {}", line);
                        transcriptionError.append(line).append("\n");
                    }
                } catch (IOException e) {
                    logger.error("Error reading transcription stderr: {}", e.getMessage());
                }
            });
            stderrThread.start();

            // Add timeout for transcription process
            boolean completed = whisperProcess.waitFor(300, TimeUnit.SECONDS);
            stdoutThread.join();
            stderrThread.join();

            if (!completed) {
                whisperProcess.destroy();
                logger.error("Transcription process timed out after 5 minutes");
                response.put("error", "Transcription timed out");
                return response;
            }

            int whisperExitCode = whisperProcess.exitValue();
            if (whisperExitCode != 0) {
                logger.error("Transcription failed with exit code: {}, error: {}", whisperExitCode, transcriptionError.toString());
                response.put("error", "Transcription failed with code: " + whisperExitCode + ", error: " + transcriptionError.toString());
                return response;
            }

            String transcription = transcriptionOutput.toString().trim();
            logger.info("Transcription completed successfully: {}", transcription);

            // Save transcription to temp file
            tempTranscription = File.createTempFile("transcription", ".txt");
            Files.writeString(tempTranscription.toPath(), transcription);
            logger.info("Transcription saved to: {}", tempTranscription.getAbsolutePath());

            // Extract and execute formatting script
            tempFormatScript = extractScriptFromClasspath("format_notes.py");
            logger.info("Starting formatting process...");

            ProcessBuilder geminiPb = new ProcessBuilder("python3", 
                                                       tempFormatScript.getAbsolutePath(), 
                                                       tempTranscription.getAbsolutePath());
            geminiPb.redirectErrorStream(true);
            Process geminiProcess = geminiPb.start();

            StringBuilder formattingOutput = new StringBuilder();
            StringBuilder formattingError = new StringBuilder();
            // Capture stdout
            Thread formatStdoutThread = new Thread(() -> {
                try (BufferedReader reader = new BufferedReader(
                        new InputStreamReader(geminiProcess.getInputStream()))) {
                    String line;
                    while ((line = reader.readLine()) != null) {
                        logger.info("Formatting output: {}", line);
                        formattingOutput.append(line).append("\n");
                    }
                } catch (IOException e) {
                    logger.error("Error reading formatting stdout: {}", e.getMessage());
                }
            });
            formatStdoutThread.start();

            // Capture stderr
            Thread formatStderrThread = new Thread(() -> {
                try (BufferedReader reader = new BufferedReader(
                        new InputStreamReader(geminiProcess.getErrorStream()))) {
                    String line;
                    while ((line = reader.readLine()) != null) {
                        logger.error("Formatting error: {}", line);
                        formattingError.append(line).append("\n");
                    }
                } catch (IOException e) {
                    logger.error("Error reading formatting stderr: {}", e.getMessage());
                }
            });
            formatStderrThread.start();

            // Add timeout for formatting process
            completed = geminiProcess.waitFor(120, TimeUnit.SECONDS);
            formatStdoutThread.join();
            formatStderrThread.join();

            if (!completed) {
                geminiProcess.destroy();
                logger.error("Formatting process timed out after 2 minutes");
                response.put("error", "Formatting timed out");
                return response;
            }

            int geminiExitCode = geminiProcess.exitValue();
            if (geminiExitCode != 0) {
                logger.error("Formatting failed with exit code: {}, error: {}", geminiExitCode, formattingError.toString());
                response.put("error", "Formatting failed with code: " + geminiExitCode + ", error: " + formattingError.toString());
                return response;
            }

            String formattedNotes = formattingOutput.toString().trim();
            logger.info("Formatting completed successfully: {}", formattedNotes);

            response.put("transcription", transcription.isEmpty() ? "No transcription generated" : transcription);
            response.put("formattedNotes", formattedNotes.isEmpty() ? "No notes generated" : formattedNotes);
            return response;

        } catch (IOException | InterruptedException e) {
            logger.error("Error processing audio: {}", e.getMessage(), e);
            response.put("error", "Failed to process audio: " + e.getMessage());
            return response;
        } finally {
            // Clean up temporary files
            deleteTempFile(tempFile);
            deleteTempFile(tempTranscription);
            deleteTempFile(tempTranscribeScript);
            deleteTempFile(tempFormatScript);
        }
    }

    private void deleteTempFile(File file) {
        if (file != null && file.exists()) {
            if (file.delete()) {
                logger.info("Deleted temp file: {}", file.getAbsolutePath());
            } else {
                logger.warn("Failed to delete temp file: {}", file.getAbsolutePath());
            }
        }
    }

    @GetMapping("/test-whisper")
    public Map<String, String> testWhisper() {
        Map<String, String> response = new HashMap<>();
        File tempFile = null;
        File tempScript = null;

        try {
            logger.info("Starting Whisper test...");
            tempFile = File.createTempFile("test", ".wav");
            Files.writeString(tempFile.toPath(), "This is a test file for Whisper");
            logger.info("Created test audio file: {}", tempFile.getAbsolutePath());

            tempScript = extractScriptFromClasspath("transcribe.py");
            logger.info("Starting test transcription...");

            ProcessBuilder pb = new ProcessBuilder("python3", 
                                                 tempScript.getAbsolutePath(), 
                                                 tempFile.getAbsolutePath());
            pb.redirectErrorStream(true);
            Process process = pb.start();

            StringBuilder output = new StringBuilder();
            StringBuilder errorOutput = new StringBuilder();
            Thread stdoutThread = new Thread(() -> {
                try (BufferedReader reader = new BufferedReader(
                        new InputStreamReader(process.getInputStream()))) {
                    String line;
                    while ((line = reader.readLine()) != null) {
                        logger.info("Test transcription output: {}", line);
                        output.append(line).append("\n");
                    }
                } catch (IOException e) {
                    logger.error("Error reading test transcription stdout: {}", e.getMessage());
                }
            });
            stdoutThread.start();

            Thread stderrThread = new Thread(() -> {
                try (BufferedReader reader = new BufferedReader(
                        new InputStreamReader(process.getErrorStream()))) {
                    String line;
                    while ((line = reader.readLine()) != null) {
                        logger.error("Test transcription error: {}", line);
                        errorOutput.append(line).append("\n");
                    }
                } catch (IOException e) {
                    logger.error("Error reading test transcription stderr: {}", e.getMessage());
                }
            });
            stderrThread.start();

            boolean completed = process.waitFor(60, TimeUnit.SECONDS);
            stdoutThread.join();
            stderrThread.join();

            if (!completed) {
                process.destroy();
                logger.error("Whisper test timed out");
                response.put("error", "Whisper test timed out");
                return response;
            }

            int exitCode = process.exitValue();
            if (exitCode != 0) {
                logger.error("Whisper test failed with exit code: {}, error: {}", exitCode, errorOutput.toString());
                response.put("error", "Whisper test failed with exit code: " + exitCode + ", error: " + errorOutput.toString());
                return response;
            }

            logger.info("Whisper test completed successfully");
            response.put("result", "Whisper test successful: " + output.toString());
            return response;
        } catch (IOException | InterruptedException e) {
            logger.error("Whisper test failed: {}", e.getMessage(), e);
            response.put("error", "Whisper test failed: " + e.getMessage());
            return response;
        } finally {
            deleteTempFile(tempFile);
            deleteTempFile(tempScript);
        }
    }

    @GetMapping("/test-gemini")
    public Map<String, String> testGemini() {
        Map<String, String> response = new HashMap<>();
        File tempFile = null;
        File tempScript = null;

        try {
            logger.info("Starting Gemini test...");
            tempFile = File.createTempFile("test", ".txt");
            Files.writeString(tempFile.toPath(), "This is a test transcription for Gemini");
            logger.info("Created test transcription file: {}", tempFile.getAbsolutePath());

            tempScript = extractScriptFromClasspath("format_notes.py");
            logger.info("Starting test formatting...");

            ProcessBuilder pb = new ProcessBuilder("python3", 
                                                 tempScript.getAbsolutePath(), 
                                                 tempFile.getAbsolutePath());
            pb.redirectErrorStream(true);
            Process process = pb.start();

            StringBuilder output = new StringBuilder();
            StringBuilder errorOutput = new StringBuilder();
            Thread stdoutThread = new Thread(() -> {
                try (BufferedReader reader = new BufferedReader(
                        new InputStreamReader(process.getInputStream()))) {
                    String line;
                    while ((line = reader.readLine()) != null) {
                        logger.info("Test formatting output: {}", line);
                        output.append(line).append("\n");
                    }
                } catch (IOException e) {
                    logger.error("Error reading test formatting stdout: {}", e.getMessage());
                }
            });
            stdoutThread.start();

            Thread stderrThread = new Thread(() -> {
                try (BufferedReader reader = new BufferedReader(
                        new InputStreamReader(process.getErrorStream()))) {
                    String line;
                    while ((line = reader.readLine()) != null) {
                        logger.error("Test formatting error: {}", line);
                        errorOutput.append(line).append("\n");
                    }
                } catch (IOException e) {
                    logger.error("Error reading test formatting stderr: {}", e.getMessage());
                }
            });
            stderrThread.start();

            boolean completed = process.waitFor(60, TimeUnit.SECONDS);
            stdoutThread.join();
            stderrThread.join();

            if (!completed) {
                process.destroy();
                logger.error("Gemini test timed out");
                response.put("error", "Gemini test timed out");
                return response;
            }

            int exitCode = process.exitValue();
            if (exitCode != 0) {
                logger.error("Gemini test failed with exit code: {}, error: {}", exitCode, errorOutput.toString());
                response.put("error", "Gemini test failed with exit code: " + exitCode + ", error: " + errorOutput.toString());
                return response;
            }

            logger.info("Gemini test completed successfully");
            response.put("result", "Gemini test successful: " + output.toString());
            return response;
        } catch (IOException | InterruptedException e) {
            logger.error("Gemini test failed: {}", e.getMessage(), e);
            response.put("error", "Gemini test failed: " + e.getMessage());
            return response;
        } finally {
            deleteTempFile(tempFile);
            deleteTempFile(tempScript);
        }
    }
}
