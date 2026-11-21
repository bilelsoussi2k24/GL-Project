"""Haven Hospital public information assistant.

A small FastAPI service around Gemini. It only answers questions about the
hospital (visiting, departments, appointments, facilities) and refuses to give
medical advice. The Java backend proxies browser questions here, so the API key
never leaves the server.
"""

import os

from dotenv import load_dotenv
from fastapi import FastAPI, HTTPException
from fastapi.middleware.cors import CORSMiddleware
from pydantic import BaseModel, Field
from google import genai

load_dotenv()

MODEL = os.getenv("GEMINI_MODEL", "gemini-2.5-flash")
API_KEY = os.getenv("GEMINI_API_KEY")
TIMEOUT_MS = int(os.getenv("GEMINI_TIMEOUT_MS", "20000"))
ALLOWED_ORIGINS = [
    origin.strip()
    for origin in os.getenv(
        "AI_CORS_ORIGINS",
        "http://localhost:5173,http://127.0.0.1:5173,http://localhost:3000,http://127.0.0.1:3000",
    ).split(",")
    if origin.strip()
]

SYSTEM_INSTRUCTION = """
You are the public information assistant for Haven Hospital.
You help patients and visitors with practical, non-medical questions: visiting
hours, how to book or change an appointment, departments and their location,
parking, reception contact details and emergencies.

Rules:
- Never diagnose, interpret symptoms or test results, or recommend medication
  or treatment. Invite the person to book an appointment or call reception.
- For anything urgent, tell them to contact emergency services or hospital
  emergency staff immediately.
- You do not have access to patient records and must never claim to.
- If you do not know a hospital detail, say so and suggest calling reception.
- Answer in at most four short sentences, in the language of the question.
""".strip()

app = FastAPI(title="Haven Hospital AI", version="1.0.0")

app.add_middleware(
    CORSMiddleware,
    allow_origins=ALLOWED_ORIGINS,
    allow_methods=["GET", "POST"],
    allow_headers=["Content-Type"],
)

_client = None


class ChatRequest(BaseModel):
    message: str = Field(min_length=1, max_length=1000)


class ChatResponse(BaseModel):
    response: str


def get_client():
    if not API_KEY:
        raise HTTPException(
            status_code=503,
            detail="The AI service is not configured. Set GEMINI_API_KEY.",
        )
    global _client
    if _client is None:
        _client = genai.Client(
            api_key=API_KEY,
            http_options={"timeout": TIMEOUT_MS},
        )
    return _client


@app.get("/api/health")
def health():
    return {"status": "ok", "configured": bool(API_KEY), "model": MODEL}


@app.post("/api/chat", response_model=ChatResponse)
def chat(request: ChatRequest):
    message = request.message.strip()
    if not message:
        raise HTTPException(status_code=400, detail="Ask a question.")

    try:
        response = get_client().models.generate_content(
            model=MODEL,
            contents=message,
            config={"system_instruction": SYSTEM_INSTRUCTION},
        )
    except HTTPException:
        raise
    except Exception as error:  # Network, quota, invalid model or blocked content
        print(f"Gemini error: {type(error).__name__}: {error}", flush=True)
        raise HTTPException(
            status_code=502,
            detail="The assistant is temporarily unavailable. Try again later.",
        )

    text = getattr(response, "text", None)
    if not text or not text.strip():
        raise HTTPException(
            status_code=502,
            detail="The assistant could not answer that question.",
        )
    return ChatResponse(response=text.strip())
