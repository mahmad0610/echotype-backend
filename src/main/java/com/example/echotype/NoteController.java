package com.example.echotype;

import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;
import java.io.*;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.Map;

@RestController
@CrossOrigin(origins = "*") // Allow CORS for Flutter app
public class NoteController {

    @PostMapping("/transcribe")
    public Map<String, String> transcribeAudio(@RequestParam("audio") MultipartFile audioFile) throws IOException, InterruptedException {
        // Save the uploaded audio file
        File tempFile = File.createTempFile("audio", ".wav");
        audioFile.transferTo(tempFile);

        // Run Whisper transcription (transcribe.py)
        String scriptPath = "E:\\FLUTTER\\echotype\\backend\\src\\main\\resources\\scripts\\transcribe.py";
        ProcessBuilder whisperPb = new ProcessBuilder("python3", scriptPath, tempFile.getAbsolutePath());
        Process whisperProcess = whisperPb.start();
        BufferedReader whisperReader = new BufferedReader(new InputStreamReader(whisperProcess.getInputStream()));
        String transcription = whisperReader.lines().reduce("", String::concat);
        whisperProcess.waitFor();

        // Run Gemini formatting (format_notes.py)
        File tempTranscription = File.createTempFile("transcription", ".txt");
        Files.writeString(tempTranscription.toPath(), transcription);
        scriptPath = "E:\\FLUTTER\\echotype\\backend\\src\\main\\resources\\scripts\\format_notes.py";
        ProcessBuilder geminiPb = new ProcessBuilder("python3", scriptPath, tempTranscription.getAbsolutePath());
        Process geminiProcess = geminiPb.start();
        BufferedReader geminiReader = new BufferedReader(new InputStreamReader(geminiProcess.getInputStream()));
        String formattedNotes = geminiReader.lines().reduce("", String::concat);
        geminiProcess.waitFor();

        // Clean up temp files
        tempFile.delete();
        tempTranscription.delete();

        // Return response
        Map<String, String> response = new HashMap<>();
        response.put("formattedNotes", formattedNotes);
        return response;
    }
}