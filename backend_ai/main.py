
import os

from dotenv import load_dotenv
from fastapi import FastAPI, HTTPException
from pydantic import BaseModel
from google import genai

load_dotenv()

app = FastAPI(title="Hospital AI Chatbot")

client = genai.Client(api_key=os.getenv("GEMINI_API_KEY"))

class ChatRequest(BaseModel):
    message: str

@app.get("/api/health")
def health():
    return {"status": "ok"}
@app.post("/api/chat")
def chat(request: ChatRequest):
    print("1. Request received", flush=True)

    try:
        print("2. Calling Gemini...", flush=True)

        response = client.models.generate_content(
            model="gemini-3.5-flash",
            contents=request.message,
            config={
                "system_instruction": (
                    "You are a public hospital information assistant. "
                    "Do not diagnose or prescribe medication."
                )
            },
        )

        print("3. Gemini responded!", flush=True)
        return {"response": response.text}

    except Exception as e:
        print(f"Gemini error: {type(e).__name__}: {e}", flush=True)
        raise HTTPException(
            status_code=502,
            detail="AI service temporarily unavailable"
        )

    
@app.post("/api/test")
def test():
    print("TEST endpoint reached", flush=True)
    return {"status": "working"}