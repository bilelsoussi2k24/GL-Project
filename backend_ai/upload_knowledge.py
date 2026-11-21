
import os
import requests

from dotenv import load_dotenv
from pinecone import Pinecone
from sentence_transformers import SentenceTransformer
from hospital_data import hospital_documents


# 1. Load environment variables
load_dotenv()

api_key = os.getenv("PINECONE_API_KEY")
if not api_key:
    raise SystemExit("Set PINECONE_API_KEY in backend_ai/.env before running this script.")
index_name = os.getenv(
    "PINECONE_INDEX_NAME",
    "hospital-embeddings"
)
namespace = "hospital-demo"


# 2. Connect to Pinecone
print("Connecting to Pinecone...", flush=True)

pc = Pinecone(api_key=api_key)

info = pc.describe_index(index_name)

# Fix: Pinecone may return a host with https:// already included
host = info.host.rstrip("/")

if not host.startswith("https://"):
    host = f"https://{host}"

print(f"Connected to index: {index_name}", flush=True)


# 3. Load embedding model
print("Loading embedding model...", flush=True)

model = SentenceTransformer("all-MiniLM-L6-v2")


# 4. Generate embeddings
texts = [doc["text"] for doc in hospital_documents]

print("Generating embeddings...", flush=True)

embeddings = model.encode(
    texts,
    normalize_embeddings=True
).tolist()


# 5. Prepare documents
documents = []

for doc, embedding in zip(hospital_documents, embeddings):
    documents.append({
        "_id": doc["id"],
        "text": doc["text"],
        "embedding": embedding
    })


# 6. Upload using Pinecone Documents API
print("Uploading documents to Pinecone...", flush=True)

url = f"{host}/namespaces/{namespace}/documents/upsert"

headers = {
    "Api-Key": api_key,
    "X-Pinecone-API-Version": "2026-07",
    "Content-Type": "application/json"
}

try:
    response = requests.post(
        url,
        headers=headers,
        json={"documents": documents},
        timeout=60
    )

    print("HTTP status:", response.status_code)
    print("Response:", response.text)

    response.raise_for_status()

    print(
        f"SUCCESS: Upload request accepted for "
        f"{len(documents)} hospital documents!"
    )

except requests.exceptions.RequestException as e:
    print(f"ERROR: {e}")
    raise
