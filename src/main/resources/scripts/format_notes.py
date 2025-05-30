import sys
from google.generativeai import GenerativeModel
import google.generativeai as genai

genai.configure(api_key="AIzaSyAqB0HBrXoOVeP_Z92NLsj9f_t9Qm6CYao")  # Replace with your Gemini API key
model = GenerativeModel("gemini-1.5-flash")

with open(sys.argv[1], "r") as file:
    transcription = file.read()

prompt = f"Format the following transcription into concise, readable notes:\n\n{transcription}"
response = model.generate_content(prompt)
print(response.text)