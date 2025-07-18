import sys
import os
import logging
import whisper

# Configure logging
logging.basicConfig(
    level=logging.INFO,
    format='%(asctime)s [%(levelname)s] %(message)s',
    handlers=[logging.StreamHandler()]
)
logger = logging.getLogger(__name__)

def main():
    try:
        # Check for audio file
        if len(sys.argv) != 2:
            logger.error("No audio file provided")
            print("Error: Audio file required", file=sys.stderr)
            sys.exit(1)

        audio_file = sys.argv[1]
        logger.info(f"Transcribing file: {audio_file}")
        if not os.path.exists(audio_file):
            logger.error(f"Audio file not found: {audio_file}")
            print(f"Error: File not found: {audio_file}", file=sys.stderr)
            sys.exit(1)

        # Load Whisper model (use cached model)
        logger.info("Loading Whisper model")
        model = whisper.load_model("base", download_root=os.path.expanduser("~/.cache/whisper"))
        logger.info("Transcribing audio")
        result = model.transcribe(audio_file, language="en")
        transcription = result["text"].strip()
        logger.info(f"Transcription result: '{transcription[:50]}...'")

        # Transcribe audio
        logger.info(f"Processing audio file: {audio_file}")
        result = model.transcribe(audio_file)
        transcription = result["text"].strip()

        if not transcription:
            logger.warning("Empty transcription")
            print("No transcription generated")
            sys.exit(0)

        logger.info(f"Transcription result: '{transcription}' (length: {len(transcription)})")
        print(transcription)

    except Exception as e:
        logger.error(f"Error: {str(e)}", exc_info=True)
        print(f"Error: Failed to transcribe audio - {str(e)}", file=sys.stderr)
        sys.exit(1)

if __name__ == "__main__":
    logger.info("Starting transcribe.py")
    main()
