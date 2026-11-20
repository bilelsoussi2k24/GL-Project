
import os
from dotenv import load_dotenv
from pinecone import Pinecone

load_dotenv()

# Connect to Pinecone
pc = Pinecone(api_key=os.getenv("PINECONE_API_KEY"))

# Access our index
index_name = "hospital-knowledge"

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