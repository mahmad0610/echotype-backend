import logging
import whisper
import sys
import os

logging.basicConfig(level=logging.INFO, format='%(asctime)s [%(levelname)s] %(message)s')
logger = logging.getLogger(__name__)

try:
    audio_file = sys.argv[1]
    logger.info(f"Checking audio file: {audio_file}")
    if not os.path.exists(audio_file):
        logger.error(f"Audio file not found: {audio_file}")
        sys.exit(1)

    logger.info("Loading Whisper tiny model...")
    model = whisper.load_model("tiny", download_root="/tmp", in_memory=False)  # Avoid loading model into memory
    logger.info("Model loaded successfully")

    logger.info(f"Transcribing file: {audio_file}")
    result = model.transcribe(audio_file, fp16=False)  # Disable FP16 to reduce memory usage
    logger.info(f"Transcription result: {result['text']}")
    print(result["text"])
except Exception as e:
    logger.error(f"Transcription error: {str(e)}")
    print(f"Error: {str(e)}", file=sys.stderr)
    sys.exit(1)
