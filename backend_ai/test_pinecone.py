
import os

from dotenv import load_dotenv
from pinecone import Pinecone

load_dotenv()

api_key = os.getenv("PINECONE_API_KEY")
if not api_key:
    raise SystemExit("Set PINECONE_API_KEY in backend_ai/.env before running this script.")

# Connect to Pinecone
pc = Pinecone(api_key=api_key)

# Same index as upload_knowledge.py
index_name = os.getenv("PINECONE_INDEX_NAME", "hospital-embeddings")

print("Connecting to Pinecone...", flush=True)

# List available indexes
indexes = pc.list_indexes().names()
print("Available indexes:", indexes)

if index_name in indexes:
    index = pc.Index(index_name)
    print("SUCCESS: Connected to hospital-knowledge!")
    print("Index statistics:", index.describe_index_stats())
else:
    print("ERROR: hospital-knowledge index not found.")