import os
from dotenv import load_dotenv
from google import genai

load_dotenv()

client = genai.Client(
    api_key=os.getenv("GEMINI_API_KEY"),
    http_options={"timeout": 15000}
)

MODEL = os.getenv("GEMINI_MODEL", "gemini-2.5-flash")

print("Calling Gemini...", flush=True)

try:
    response = client.models.generate_content(
        model=MODEL,
        contents="Say hello in one sentence."
    )
    print("SUCCESS:", response.text)

except Exception as e:
    print(f"ERROR: {type(e).__name__}: {e}")