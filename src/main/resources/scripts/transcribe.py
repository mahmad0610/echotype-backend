import sys
import logging
import subprocess

logging.basicConfig(level=logging.INFO)
logger = logging.getLogger(__name__)

try:
    logger.info("Installing openai-whisper and torch...")
    subprocess.run([
        "python3", "-m", "pip", "install", "--no-cache-dir",
        "openai-whisper==20230314",
        "torch", "--extra-index-url", "https://download.pytorch.org/whl/cpu"
    ], check=True)
    logger.info("Dependencies installed successfully")
except Exception as e:
    logger.error(f"Error installing dependencies: {str(e)}")
    print(f"Error: {str(e)}", file=sys.stderr)
    sys.exit(1)

try:
    import whisper  # Import after installation
    logger.info("Whisper module imported successfully")
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
