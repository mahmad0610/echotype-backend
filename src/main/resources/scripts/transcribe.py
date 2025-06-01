import sys
import logging
import whisper
import os
import traceback

# Set up logging
logging.basicConfig(level=logging.INFO)
logger = logging.getLogger(__name__)

try:
    logger.info("Starting transcription process...")
    logger.info("Checking /tmp directory permissions...")
    os.makedirs("/tmp", exist_ok=True)  # Create /tmp if it doesn’t exist
    logger.info("Loading Whisper tiny model...")
    model = whisper.load_model("tiny", download_root="/tmp")
    logger.info("Model loaded successfully!")
    logger.info(f"Transcribing file: {sys.argv[1]}")
    result = model.transcribe(sys.argv[1])
    logger.info(f"Transcription result: {result['text']}")
    print(result["text"])  # Output the transcription
except Exception as e:
    logger.error(f"Error during transcription: {str(e)}")
    logger.error(traceback.format_exc())  # Full error details
    print(f"Error: {str(e)}", file=sys.stderr)
    sys.exit(1)  # Exit with an error code
