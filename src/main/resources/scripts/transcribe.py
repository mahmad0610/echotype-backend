import logging
import whisper
import sys
import os

logging.basicConfig(level=logging.INFO, format='%(asctime)s [%(levelname)s] %(message)s')
logger = logging.getLogger(__name__)

def main():
    try:
        # Check command line arguments
        if len(sys.argv) != 2:
            logger.error("Usage: python script.py <audio_file>")
            print("Error: Usage - python script.py <audio_file>", file=sys.stderr)
            sys.exit(1)

        audio_file = sys.argv[1]

        logger.info(f"Checking audio file: {audio_file}")

        if not os.path.exists(audio_file):
            logger.error(f"Audio file not found: {audio_file}")
            print(f"Error: Audio file not found: {audio_file}", file=sys.stderr)
            sys.exit(1)
        
        file_size = os.path.getsize(audio_file)
        logger.info(f"Audio file size: {file_size} bytes")
        if file_size == 0:
            logger.warning(f"Audio file is empty: {audio_file}")
            print("Warning: Audio file is empty", file=sys.stderr)

        logger.info("Loading Whisper tiny model...")
        model = whisper.load_model("base", download_root="/tmp", in_memory=False)
        logger.info("Model loaded successfully")

        logger.info(f"Transcribing file: {audio_file} with language: English")
        result = model.transcribe(audio_file, fp16=False, language="en")
        transcription = result.get("text", "").strip()

        logger.info(f"Raw transcription segments: {result.get('segments', [])}")
        logger.info(f"Transcription result: {transcription}")
        print(transcription if transcription else "No transcription detected")

    except whisper.WhisperError as e:
        logger.error(f"Whisper transcription error: {str(e)}")
        print(f"Error: Whisper transcription failed - {str(e)}", file=sys.stderr)
        sys.exit(1)
    except Exception as e:
        logger.error(f"Unexpected error during transcription: {str(e)}")
        print(f"Error: Unexpected failure - {str(e)}", file=sys.stderr)
        sys.exit(1)

if __name__ == "__main__":
    main()
