import requests
import json

user_agent = "Mozilla/5.0 (Linux; Android 14; Pixel 8 Pro Build/UD1A.230805.019; wv) AppleWebKit/537.36 (KHTML, like Gecko) Version/4.0 Chrome/131.0.6778.135 Mobile Safari/537.36"
headers = {
    "User-Agent": user_agent,
    "Accept": "application/json, text/plain, */*",
    "Referer": "https://www.smule.com/",
}

key = "3114954397_5164022791"
url = f"https://www.smule.com/s/performance/{key}"

print(f"Fetching {url}...")
response = requests.get(url, headers=headers)
print(f"Status: {response.status_code}")
print(f"Content-Type: {response.headers.get('Content-Type')}")

try:
    data = response.json()
    print("Successfully parsed JSON!")
    print(json.dumps(data, indent=2)[:500])
except:
    print("Failed to parse JSON. First 200 chars of body:")
    print(response.text[:200])
