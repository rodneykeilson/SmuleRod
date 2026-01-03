import requests

url = "https://www.smule.com/sing-recording/3187786396_5144609973"
user_agent = "Mozilla/5.0 (Linux; Android 14; Pixel 8 Pro Build/UD1A.230805.019; wv) AppleWebKit/537.36 (KHTML, like Gecko) Version/4.0 Chrome/131.0.6778.135 Mobile Safari/537.36"

session = requests.Session()
session.headers.update({
    "User-Agent": user_agent,
    "Accept": "text/html,application/xhtml+xml,application/xml;q=0.9,image/avif,image/webp,image/apng,*/*;q=0.8",
    "Accept-Language": "en-US,en;q=0.9",
})

print("Fetching main page...")
response = session.get(url)
print(f"Main page status: {response.status_code}")

if response.status_code == 200:
    import re
    mp4_match = re.search(r'"video_media_mp4_url":"([^"]+)"', response.text)
    if mp4_match:
        encrypted_url = mp4_match.group(1)
        print(f"Found encrypted URL: {encrypted_url[:50]}...")
        
        import time
        import urllib.parse
        timestamp = int(time.time())
        encoded_url = urllib.parse.quote(encrypted_url)
        redir_url = f"https://www.smule.com/redir?e=1&t={timestamp}.12345&url={encoded_url}"
        
        print(f"Fetching redir URL: {redir_url}")
        redir_response = session.get(redir_url, allow_redirects=False)
        print(f"Redir status: {redir_response.status_code}")
        if "Location" in redir_response.headers:
            print(f"Final URL: {redir_response.headers['Location'][:100]}...")
        else:
            print(f"Redir body: {redir_response.text[:100]}")
    else:
        print("Could not find encrypted URL in HTML")
else:
    print(f"Failed to fetch main page: {response.text[:200]}")
