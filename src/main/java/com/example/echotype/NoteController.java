package com.example.echotype;

import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;
import java.io.*;
import java.nio.file.Files;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.io.ClassPathResource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.dao.DataIntegrityViolationException;

@RestController
@CrossOrigin(origins = "*")
public class NoteController {

    private static final Logger logger = LoggerFactory.getLogger(NoteController.class);

    @Autowired
    private NoteRepository noteRepository;

    @Autowired
    private ConversationRepository conversationRepository;

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
        logger.info("Script extracted to: {}", tempScript.getAbsolutePath());
        return tempScript;
    }

    @PostMapping("/transcribe")
    public Map<String, String> transcribeAudio(
            @RequestParam("audio") MultipartFile audioFile,
            @RequestParam("userId") Long userId,
            @RequestParam("conversationHistory") String conversationHistory,
            @RequestParam("conversationId") String conversationId) {
        Map<String, String> response = new HashMap<>();
        File tempFile = null;
        File tempTranscription = null;
        File tempTranscribeScript = null;
        File tempFormatScript = null;
        File tempHistoryFile = null;

        try {
            logger.info("Received /transcribe request with file: {}, userId: {}, conversationId: {}", audioFile.getOriginalFilename(), userId, conversationId);
            tempFile = File.createTempFile("audio", ".wav");
            audioFile.transferTo(tempFile);
            logger.info("Audio file saved to: {}, size: {} bytes", tempFile.getAbsolutePath(), tempFile.length());

            tempHistoryFile = File.createTempFile("history", ".txt");
            Files.writeString(tempHistoryFile.toPath(), conversationHistory);
            logger.info("Conversation history saved to: {}", tempHistoryFile.getAbsolutePath());

            tempTranscribeScript = extractScriptFromClasspath("transcribe.py");
            logger.info("Starting transcription process...");

            ProcessBuilder whisperPb = new ProcessBuilder(
                "F:\\backend\\.venv\\Scripts\\python.exe",
                tempTranscribeScript.getAbsolutePath(),
                tempFile.getAbsolutePath()
            );
            Process whisperProcess = whisperPb.start();

            StringBuilder transcriptionOutput = new StringBuilder();
            StringBuilder transcriptionLogs = new StringBuilder();
            Thread stdoutThread = new Thread(() -> {
                try (BufferedReader reader = new BufferedReader(
                        new InputStreamReader(whisperProcess.getInputStream()))) {
                    String line;
                    while ((line = reader.readLine()) != null) {
                        if (line.matches("\\d{4}-\\d{2}-\\d{2}\\s+\\d{2}:\\d{2}:\\d{3}\\s+\\[\\w+\\].*")) {
                            logger.info("Transcription log: {}", line);
                            transcriptionLogs.append(line).append("\n");
                        } else if (line.matches("\\s*\\d+%.*")) {
                            logger.debug("Transcription progress: {}", line);
                        } else {
                            logger.info("Transcription result: {}", line);
                            transcriptionOutput.append(line).append("\n");
                        }
                    }
                } catch (IOException e) {
                    logger.error("Error reading transcription stdout: {}", e.getMessage());
                }
            });
            stdoutThread.start();

            Thread stderrThread = new Thread(() -> {
                try (BufferedReader reader = new BufferedReader(
                        new InputStreamReader(whisperProcess.getErrorStream()))) {
                    String line;
                    while ((line = reader.readLine()) != null) {
                        logger.error("Transcription error: {}", line);
                        transcriptionLogs.append(line).append("\n");
                    }
                } catch (IOException e) {
                    logger.error("Error reading transcription stderr: {}", e.getMessage());
                }
            });
            stderrThread.start();

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
                logger.error("Transcription failed with exit code: {}, logs: {}", whisperExitCode, transcriptionLogs.toString());
                response.put("error", "Transcription failed with code: " + whisperExitCode + ", logs: " + transcriptionLogs.toString());
                return response;
            }

            String transcription = transcriptionOutput.toString().trim();
            logger.info("Transcription completed successfully: {}", transcription);

            tempTranscription = File.createTempFile("transcription", ".txt");
            Files.writeString(tempTranscription.toPath(), transcription);
            logger.info("Transcription saved to: {}", tempTranscription.getAbsolutePath());

            tempFormatScript = extractScriptFromClasspath("format_notes.py");
            logger.info("Starting formatting process...");

            ProcessBuilder geminiPb = new ProcessBuilder(
                "F:\\backend\\.venv\\Scripts\\python.exe",
                tempFormatScript.getAbsolutePath(),
                tempTranscription.getAbsolutePath(),
                tempHistoryFile.getAbsolutePath(),
                conversationId
            );
            Process geminiProcess = geminiPb.start();

            StringBuilder formattingOutput = new StringBuilder();
            StringBuilder formattingLogs = new StringBuilder();
            Thread formatStdoutThread = new Thread(() -> {
                try (BufferedReader reader = new BufferedReader(
                        new InputStreamReader(geminiProcess.getInputStream()))) {
                    String line;
                    while ((line = reader.readLine()) != null) {
                        if (line.matches("\\d{4}-\\d{2}-\\d{2}\\s+\\d{2}:\\d{2}:\\d{3}\\s+\\[\\w+\\].*")) {
                            logger.info("Formatting log: {}", line);
                            formattingLogs.append(line).append("\n");
                        } else {
                            logger.info("Formatting result: {}", line);
                            formattingOutput.append(line).append("\n");
                        }
                    }
                } catch (IOException e) {
                    logger.error("Error reading formatting stdout: {}", e.getMessage());
                }
            });
            formatStdoutThread.start();

            Thread formatStderrThread = new Thread(() -> {
                try (BufferedReader reader = new BufferedReader(
                        new InputStreamReader(geminiProcess.getErrorStream()))) {
                    String line;
                    while ((line = reader.readLine()) != null) {
                        logger.error("Formatting error: {}", line);
                        formattingLogs.append(line).append("\n");
                    }
                } catch (IOException e) {
                    logger.error("Error reading formatting stderr: {}", e.getMessage());
                }
            });
            formatStderrThread.start();

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
                logger.error("Formatting failed with exit code: {}, logs: {}", geminiExitCode, formattingLogs.toString());
                response.put("error", "Formatting failed with code: " + geminiExitCode + ", logs: " + formattingLogs.toString());
                return response;
            }

            String formattedNotes = formattingOutput.toString().trim();
            logger.info("Formatting completed successfully: {}", formattedNotes);

            response.put("transcription", transcription.isEmpty() ? "No transcription generated" : transcription);
            response.put("formattedNotes", formattedNotes.isEmpty() ? formattingLogs.toString().trim() : formattedNotes);
            return response;

        } catch (IOException | InterruptedException e) {
            logger.error("Error processing audio: {}", e.getMessage(), e);
            response.put("error", "Failed to process audio: " + e.getMessage());
            return response;
        } finally {
            deleteTempFile(tempFile);
            deleteTempFile(tempTranscription);
            deleteTempFile(tempTranscribeScript);
            deleteTempFile(tempFormatScript);
            deleteTempFile(tempHistoryFile);
        }
    }

    @PostMapping("/process-text")
    public Map<String, String> processText(@RequestBody Map<String, Object> request) {
        Map<String, String> response = new HashMap<>();
        File tempFile = null;
        File tempFormatScript = null;
        File tempHistoryFile = null;

        try {
            String text = (String) request.get("text");
            Long userId = ((Number) request.get("userId")).longValue();
            String conversationHistory = (String) request.get("conversationHistory");
            String conversationId = (String) request.get("conversationId");
            if (text == null || text.isEmpty()) {
                logger.error("Text input is empty");
                response.put("error", "Text is empty");
                return response;
            }
            logger.info("Received /process-text request with text length: {}, userId: {}, conversationId: {}", text.length(), userId, conversationId);

            tempFile = File.createTempFile("text", ".txt");
            Files.writeString(tempFile.toPath(), text);
            logger.info("Text saved to: {}", tempFile.getAbsolutePath());

            tempHistoryFile = File.createTempFile("history", ".txt");
            Files.writeString(tempHistoryFile.toPath(), conversationHistory);
            logger.info("Conversation history saved to: {}", tempHistoryFile.getAbsolutePath());

            tempFormatScript = extractScriptFromClasspath("format_notes.py");
            logger.info("Starting text formatting process...");

            ProcessBuilder geminiPb = new ProcessBuilder(
                "F:\\backend\\.venv\\Scripts\\python.exe",
                tempFormatScript.getAbsolutePath(),
                tempFile.getAbsolutePath(),
                tempHistoryFile.getAbsolutePath(),
                conversationId
            );
            Process geminiProcess = geminiPb.start();

            StringBuilder formattingOutput = new StringBuilder();
            StringBuilder formattingLogs = new StringBuilder();
            Thread formatStdoutThread = new Thread(() -> {
                try (BufferedReader reader = new BufferedReader(
                        new InputStreamReader(geminiProcess.getInputStream()))) {
                    String line;
                    while ((line = reader.readLine()) != null) {
                        if (line.matches("\\d{4}-\\d{2}-\\d{2}\\s+\\d{2}:\\d{2}:\\d{3}\\s+\\[\\w+\\].*")) {
                            logger.info("Formatting log: {}", line);
                            formattingLogs.append(line).append("\n");
                        } else {
                            logger.info("Formatting result: {}", line);
                            formattingOutput.append(line).append("\n");
                        }
                    }
                } catch (IOException e) {
                    logger.error("Error reading formatting stdout: {}", e.getMessage());
                }
            });
            formatStdoutThread.start();

            Thread formatStderrThread = new Thread(() -> {
                try (BufferedReader reader = new BufferedReader(
                        new InputStreamReader(geminiProcess.getErrorStream()))) {
                    String line;
                    while ((line = reader.readLine()) != null) {
                        logger.error("Formatting error: {}", line);
                        formattingLogs.append(line).append("\n");
                    }
                } catch (IOException e) {
                    logger.error("Error reading formatting stderr: {}", e.getMessage());
                }
            });
            formatStderrThread.start();

            boolean completed = geminiProcess.waitFor(120, TimeUnit.SECONDS);
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
                logger.error("Formatting failed with exit code: {}, logs: {}", geminiExitCode, formattingLogs.toString());
                response.put("error", "Formatting failed with code: " + geminiExitCode + ", logs: " + formattingLogs.toString());
                return response;
            }

            String formattedNotes = formattingOutput.toString().trim();
            logger.info("Text formatting completed successfully: {}", formattedNotes);

            response.put("formattedNotes", formattedNotes.isEmpty() ? formattingLogs.toString().trim() : formattedNotes);
            return response;

        } catch (IOException | InterruptedException e) {
            logger.error("Error processing text: {}", e.getMessage(), e);
            response.put("error", "Failed to process text: " + e.getMessage());
            return response;
        } finally {
            deleteTempFile(tempFile);
            deleteTempFile(tempFormatScript);
            deleteTempFile(tempHistoryFile);
        }
    }

    @PostMapping("/notes")
    public Map<String, String> saveNote(@RequestBody Note note) {
        Map<String, String> response = new HashMap<>();
        try {
            note.setIsSynced(true);
            note.setIsDeleted(false);
            noteRepository.save(note);
            logger.info("Note saved with id: {}, userId: {}", note.getId(), note.getUserId());
            response.put("message", "Note saved successfully");
            return response;
        } catch (DataIntegrityViolationException e) {
            logger.error("Failed to save note: {}", e.getMessage());
            response.put("error", "Failed to save note: " + e.getMessage());
            return response;
        }
    }

    @GetMapping("/notes/{userId}")
    public List<Note> getNotes(@PathVariable Long userId) {
        logger.info("Fetching notes for userId: {}", userId);
        return noteRepository.findByUserId(userId);
    }

    @DeleteMapping("/notes/{id}")
    public Map<String, String> deleteNote(@PathVariable String id) {
        Map<String, String> response = new HashMap<>();
        try {
            if (noteRepository.existsById(id)) {
                noteRepository.deleteById(id);
                logger.info("Deleted note with id: {}", id);
                response.put("message", "Note deleted successfully");
            } else {
                logger.warn("Note with id {} not found", id);
                response.put("error", "Note not found");
            }
        } catch (Exception e) {
            logger.error("Error deleting note with id {}: {}", id, e.getMessage());
            response.put("error", "Failed to delete note: " + e.getMessage());
        }
        return response;
    }

    @PostMapping("/api/conversations")
    public Map<String, String> saveConversation(@RequestBody Conversation conversation) {
        Map<String, String> response = new HashMap<>();
        try {
            conversation.setPinned(conversation.getPinned() != null ? conversation.getPinned() : false);
            conversationRepository.save(conversation);
            logger.info("Conversation message saved with conversationId: {}, userId: {}", conversation.getConversationId(), conversation.getUserId());
            response.put("message", "Conversation saved successfully");
            return response;
        } catch (DataIntegrityViolationException e) {
            logger.error("Failed to save conversation: {}", e.getMessage());
            response.put("error", "Failed to save conversation: " + e.getMessage());
            return response;
        }
    }

    @GetMapping("/api/conversations/{userId}")
    public List<Conversation> getConversations(@PathVariable Long userId) {
        logger.info("Fetching conversations for userId: {}", userId);
        return conversationRepository.findByUserId(userId);
    }

    @DeleteMapping("/api/conversations/{conversationId}")
    public Map<String, String> deleteConversation(@PathVariable String conversationId) {
        Map<String, String> response = new HashMap<>();
        try {
            if (conversationRepository.findByConversationId(conversationId).isEmpty()) {
                logger.warn("Conversation with conversationId {} not found", conversationId);
                response.put("error", "Conversation not found");
            } else {
                conversationRepository.deleteByConversationId(conversationId);
                logger.info("Deleted conversation with conversationId: {}", conversationId);
                response.put("message", "Conversation deleted successfully");
            }
        } catch (Exception e) {
            logger.error("Error deleting conversation with conversationId {}: {}", conversationId, e.getMessage());
            response.put("error", "Failed to delete conversation: " + e.getMessage());
        }
        return response;
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
}
