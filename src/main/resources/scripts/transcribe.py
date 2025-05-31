import sys
import logging

logging.basicConfig(level=logging.INFO)
logger = logging.getLogger(__name__)

try:
    import whisper
    logger.info("Whisper module imported successfully")
except ImportError as e:
    logger.error("Failed to import whisper: %s", str(e))
    print(f"Error: Failed to import whisper: {str(e)}", file=sys.stderr)
    sys.exit(1)

try:
    logger.info("Loading Whisper model...")
    model = whisper.load_model("base")
    logger.info(f"Model loaded: {model}")
    logger.info(f"Transcribing file: {sys.argv[1]}")
    result = model.transcribe(sys.argv[1])
    logger.info(f"Transcription result: {result}")
    print(result["text"])
except Exception as e:
    logger.error(f"Error: {str(e)}", exc_info=True)
    print(f"Error: {str(e)}", file=sys.stderr)
    sys.exit(1)
