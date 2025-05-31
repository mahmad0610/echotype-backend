import sys
import logging
import whisper

logging.basicConfig(level=logging.INFO)
logger = logging.getLogger(__name__)

try:
    logger.info("Loading Whisper model...")
    model = whisper.load_model("tiny", download_root="/tmp")
    logger.info(f"Model loaded: {model}")
    logger.info(f"Transcribing file: {sys.argv[1]}")
    result = model.transcribe(sys.argv[1])
    logger.info(f"Transcription result: {result}")
    print(result["text"])
except Exception as e:
    logger.error(f"Error: {str(e)}")
    print(f"Error: {str(e)}", file=sys.stderr)
    sys.exit(1)
