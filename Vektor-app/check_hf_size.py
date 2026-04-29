import urllib.request
import json

token = "hf_WMGihedWWSDlSqllpmpNEarttsNjFNOLJ"

# Check file sizes via HF API
url = "https://huggingface.co/api/models/Cactus-Compute/gemma-4-E2B-it?blobs=true"
req = urllib.request.Request(url, headers={"Authorization": f"Bearer {token}"})
with urllib.request.urlopen(req) as resp:
    data = json.load(resp)

for s in data.get("siblings", []):
    name = s.get("rfilename", "")
    size = s.get("size", "unknown")
    if "int4" in name or "int8" in name:
        mb = round(int(size) / 1_048_576, 1) if isinstance(size, int) else size
        print(f"{name}: {mb} MB")
