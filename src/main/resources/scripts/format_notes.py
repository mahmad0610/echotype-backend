import sys
import os
import logging
import google.generativeai as genai

# Configure logging
logging.basicConfig(
    level=logging.INFO,
    format='%(asctime)s [%(levelname)s] %(message)s',
    handlers=[logging.StreamHandler()]
)
logger = logging.getLogger(__name__)

def main():
    try:
        if len(sys.argv) != 4:
            logger.error("Invalid arguments: Expected transcription file, history file, and conversationId")
            print("Error: Transcription file, history file, and conversationId required", file=sys.stderr)
            sys.exit(1)

        transcription_file = sys.argv[1]
        history_file = sys.argv[2]
        conversation_id = sys.argv[3]
        logger.info(f"Reading transcription: {transcription_file}, history: {history_file}, conversationId: {conversation_id}")

        if not os.path.exists(transcription_file):
            logger.error(f"Transcription file not found: {transcription_file}")
            print(f"Error: File not found: {transcription_file}", file=sys.stderr)
            sys.exit(1)

        if not os.path.exists(history_file):
            logger.error(f"History file not found: {history_file}")
            print(f"Error: File not found: {history_file}", file=sys.stderr)
            sys.exit(1)

        with open(transcription_file, "r", encoding='utf-8') as file:
            transcription = file.read().strip()

        with open(history_file, "r", encoding='utf-8') as file:
            conversation_history = file.read().strip()

        if not transcription:
            logger.warning("Empty transcription file")
            print("Please specify a topic (e.g., math, history) for detailed notes.")
            sys.exit(0)

        logger.info(f"Processing transcription: '{transcription}' (length: {len(transcription)})")
        logger.info(f"Conversation history: '{conversation_history[:50]}...' (length: {len(conversation_history)})")

        api_key = os.getenv("GEMINI_API_KEY")
        if not api_key:
            logger.error("GEMINI_API_KEY not set")
            print("Error: GEMINI_API_KEY required", file=sys.stderr)
            sys.exit(1)

        logger.info("Configuring Gemini API")
        genai.configure(api_key=api_key)
        model = genai.GenerativeModel("gemini-1.5-flash")

        prompt = f"""
You are an expert AI study assistant designed to generate detailed, structured academic notes at a high school or early college level, rivaling the quality of Grok or advanced GPT models. Support English and Roman Urdu inputs, responding in the input language with a formal, academic tone. Avoid informal phrases (e.g., "bro," "cool," "let's chat").

- **Input Handling**:
  - For vague or incomplete inputs, respond only with: "Please specify a topic (e.g., math, history) for detailed notes."
  - For academic topics, generate comprehensive notes in Markdown with LaTeX for math, including a numerical example where applicable.
  - For direct problems (e.g., "solve y >= 7"), provide a step-by-step solution in LaTeX.
- **Context Awareness**:
  - Leverage the conversation history (format: "Sender: Message") to maintain continuity and relevance.
- **Content Structure**:
  - Use Markdown:
    - `#` for the topic title (e.g., "# Linear Algebra Notes").
    - `##` for sections (e.g., "## Key Concepts", "## Example", "## Resources").
    - `-` for bullet points (e.g., "- Vectors: ..."). Use plain text for bullet titles, never `*` or `**`.
    - Paragraphs for detailed explanations.
    - For math or science topics, include a "## Example" section with a numerical problem (e.g., solve equations, compute a derivative).
  - For Roman Urdu, blend simple language with English terms (e.g., "Matrix ek rectangular array hota hai").
- **LaTeX Formatting**:
  - Use `$...$` for inline math and `$$...$$` for display math.
  - Use `\\begin{{bmatrix}}...\\end{{bmatrix}}` for matrices.
  - Use `\\begin{{align}}...\\end{{align}}` for equation systems.
  - Use `\\langle`, `\\rangle` for vectors.
  - Ensure LaTeX is valid, balanced, and renderable with flutter_math.
- **External Resources**:
  - For specific topics, include a "## Resources" section with:
    - A Google search link (e.g., "[Linear algebra basics](https://www.google.com/search?q=linear+algebra+basics+tutorial)").
    - A YouTube link from channels like Khan Academy or 3Blue1Brown (e.g., "[Linear Algebra by 3Blue1Brown](https://www.youtube.com/watch?v=fNk_zzaMoSs)").
  - Omit resources for vague inputs.
- **Output Requirements**:
  - Produce clean, UTF-8 compatible Markdown with valid LaTeX.
  - Use `-` for all bullet points.
  - Ensure compatibility with Flutter's latext package.

**Conversation History**:
{conversation_history}

**Transcription**:
{transcription}

Generate notes following these guidelines. Use the history for context. For math topics, include a LaTeX example. Ensure all output is professional and structured.
"""
        logger.info("Generating content with Gemini API")
        response = model.generate_content(prompt)
        formatted_notes = response.text.strip()
        logger.info(f"Gemini response: '{formatted_notes[:50]}...'")

        print(formatted_notes)

    except Exception as e:
        logger.error(f"Error: {str(e)}", exc_info=True)
        print(f"Error: Failed to generate notes - {str(e)}", file=sys.stderr)
        sys.exit(1)

if __name__ == "__main__":
    logger.info("Starting format_notes.py")
    main()
