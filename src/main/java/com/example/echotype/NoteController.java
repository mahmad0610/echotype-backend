package com.example.echotype;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;
import java.io.BufferedReader;
import java.io.File;
import java.io.InputStreamReader;
import java.util.concurrent.TimeUnit;

@RestController
public class NoteController {
    private static final Logger logger = LoggerFactory.getLogger(NoteController.class);

    @PostMapping("/transcribe")
    public String transcribeAudio(@RequestParam("audio") MultipartFile file) throws Exception {
        logger.info("Received /transcribe request with file: " + file.getOriginalFilename());
        File tempFile = File.createTempFile("audio", ".wav");
        try {
            file.transferTo(tempFile);
            logger.info("Audio file saved to: " + tempFile.getAbsolutePath());

            logger.info("Extracting script: transcribe.py");
            ProcessBuilder pb = new ProcessBuilder("python3", "src/main/resources/scripts/transcribe.py", tempFile.getAbsolutePath());
            pb.redirectErrorStream(true);
            Process process = pb.start();

            logger.info("Starting transcription process...");
            BufferedReader reader = new BufferedReader(new InputStreamReader(process.getInputStream()));
            StringBuilder output = new StringBuilder();
            String line;
            while ((line = reader.readLine()) != null) {
                logger.info("Transcription output: " + line);
                output.append(line).append("\n");
            }

            boolean completed = process.waitFor(60, TimeUnit.SECONDS);
            if (!completed) {
                process.destroy();
                logger.error("Transcription process timed out after 60 seconds");
                throw new RuntimeException("Transcription timed out");
            }

            int exitCode = process.exitValue();
            if (exitCode != 0) {
                logger.error("Transcription script failed with exit code: " + exitCode + ", output: " + output.toString());
                throw new RuntimeException("Transcription failed: " + output.toString());
            }

            logger.info("Transcription completed successfully");
            return output.toString().trim();
        } catch (Exception e) {
            logger.error("Error in /transcribe: " + e.getMessage(), e);
            throw e;
        } finally {
            if (tempFile.exists()) {
                tempFile.delete();
            }
        }
    }
}
