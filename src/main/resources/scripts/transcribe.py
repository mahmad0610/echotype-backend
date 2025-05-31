import sys
import logging
import subprocess
from subprocess import TimeoutExpired

logging.basicConfig(level=logging.INFO)
logger = logging.getLogger(__name__)

try:
    logger.info("Installing openai-whisper and torch...")
    result = subprocess.run([
        "python3", "-m", "pip", "install", "--no-cache-dir",
        "openai-whisper==20230314",
        "torch", "--extra-index-url", "https://download.pytorch.org/whl/cpu"
    ], check=False, timeout=300, capture_output=True, text=True)
    logger.info("Installation output: %s", result.stdout)
    if result.returncode != 0:
        logger.error("Installation failed: %s", result.stderr)
        print(f"Error: Installation failed - {result.stderr}", file=sys.stderr)
        sys.exit(1)
    logger.info("Dependencies installed successfully")
except TimeoutExpired as e:
    logger.error("Installation timed out: %s", e.stdout)
    print(f"Error: Installation timed out - {e.stdout}", file=sys.stderr)
    sys.exit(1)
except Exception as e:
    logger.error("Error installing dependencies: %s", str(e))
    print(f"Error: {str(e)}", file=sys.stderr)
    sys.exit(1)

try:
    import whisper
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
