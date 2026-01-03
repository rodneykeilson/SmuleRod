import requests
import re
import time
import urllib.parse

user_agent = "Mozilla/5.0 (Linux; Android 14; Pixel 8 Pro Build/UD1A.230805.019; wv) AppleWebKit/537.36 (KHTML, like Gecko) Version/4.0 Chrome/131.0.6778.135 Mobile Safari/537.36"

session = requests.Session()
session.headers.update({
    "User-Agent": user_agent,
    "Accept": "text/html,application/xhtml+xml,application/xml;q=0.9,image/avif,image/webp,image/apng,*/*;q=0.8",
    "Accept-Language": "en-US,en;q=0.9",
})

url = "https://www.smule.com/c/629883263_5087867325"

print("Fetching main page...")
response = session.get(url)
print(f"Main page status: {response.status_code}")

if response.status_code == 200:
    html = response.text
    
    # Find video_media_mp4_url
    mp4_match = re.search(r'"video_media_mp4_url":"([^"]+)"', html)
    if mp4_match:
        encrypted_url = mp4_match.group(1)
        print(f"\nEncrypted MP4 URL: {encrypted_url}")
        
        # Test the redir endpoint
        timestamp = int(time.time())
        encoded_url = urllib.parse.quote(encrypted_url)
        redir_url = f"https://www.smule.com/redir?e=1&t={timestamp}.12345&url={encoded_url}"
        
        print(f"\nTesting redir (no follow)...")
        redir_response = session.get(redir_url, allow_redirects=False)
        print(f"Redir status: {redir_response.status_code}")
        print(f"Headers: {dict(redir_response.headers)}")
        
        if redir_response.status_code == 302:
            final_url = redir_response.headers.get("Location", "")
            print(f"\nFinal URL: {final_url}")
            
            # Test if the final URL works
            print("\nTesting final URL...")
            head_response = session.head(final_url)
            print(f"HEAD status: {head_response.status_code}")
            print(f"Content-Type: {head_response.headers.get('Content-Type', 'unknown')}")
            print(f"Content-Length: {head_response.headers.get('Content-Length', 'unknown')}")
        elif redir_response.status_code == 418:
            print("GOT 418 - BLOCKED!")
            print(f"Body: {redir_response.text}")
        else:
            print(f"Unexpected status: {redir_response.status_code}")
            print(f"Body: {redir_response.text[:500]}")
    else:
        print("video_media_mp4_url not found")
else:
    print(f"Failed: {response.text[:200]}")
