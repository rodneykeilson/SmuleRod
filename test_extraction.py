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

url = "https://www.smule.com/sing-recording/3187786396_5144609973"

print("Fetching main page...")
response = session.get(url)
print(f"Main page status: {response.status_code}")

if response.status_code == 200:
    html = response.text
    
    # Check performance type
    type_match = re.search(r'"type":"([^"]+)"', html)
    if type_match:
        print(f"Performance type: {type_match.group(1)}")
    
    # Find all media URLs
    print("\n--- All media URLs found ---")
    
    mp4_match = re.search(r'"video_media_mp4_url":"([^"]+)"', html)
    if mp4_match:
        print(f"video_media_mp4_url: {mp4_match.group(1)[:60]}...")
    else:
        print("video_media_mp4_url: NOT FOUND")
    
    video_match = re.search(r'"video_media_url":"([^"]+)"', html)
    if video_match:
        print(f"video_media_url: {video_match.group(1)[:60]}...")
    else:
        print("video_media_url: NOT FOUND")
    
    visualizer_match = re.search(r'"visualizer_media_url":"([^"]*)"', html)
    if visualizer_match:
        val = visualizer_match.group(1)
        print(f"visualizer_media_url: {val[:60] if val else 'null/empty'}...")
    else:
        print("visualizer_media_url: NOT FOUND")
    
    media_match = re.search(r'"media_url":"([^"]+)"', html)
    if media_match:
        print(f"media_url: {media_match.group(1)[:60]}...")
    else:
        print("media_url: NOT FOUND")
    
    audio_match = re.search(r'"audio_media_url":"([^"]+)"', html)
    if audio_match:
        print(f"audio_media_url: {audio_match.group(1)[:60]}...")
    else:
        print("audio_media_url: NOT FOUND")
    
    # Test resolving the MP4 URL
    print("\n--- Testing MP4 URL resolution ---")
    if mp4_match:
        encrypted_url = mp4_match.group(1)
        timestamp = int(time.time())
        encoded_url = urllib.parse.quote(encrypted_url)
        redir_url = f"https://www.smule.com/redir?e=1&t={timestamp}.12345&url={encoded_url}"
        
        redir_response = session.get(redir_url, allow_redirects=False)
        print(f"Redir status: {redir_response.status_code}")
        if "Location" in redir_response.headers:
            final_url = redir_response.headers["Location"]
            print(f"Final URL: {final_url[:100]}...")
            
            # Check if the URL is valid
            head_response = session.head(final_url)
            print(f"HEAD status: {head_response.status_code}")
            print(f"Content-Type: {head_response.headers.get('Content-Type', 'unknown')}")
            print(f"Content-Length: {head_response.headers.get('Content-Length', 'unknown')}")
        else:
            print(f"No redirect, body: {redir_response.text[:100]}")
else:
    print(f"Failed: {response.text[:200]}")
