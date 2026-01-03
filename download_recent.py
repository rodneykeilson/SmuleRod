import requests
import sys

url = "https://www.smule.com/recording/c%C3%A9line-dion-fallin-in-love-when-i-fall-in-love/1794751934_5163930554"
headers = {
    "User-Agent": "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/91.0.4472.124 Safari/537.36"
}

response = requests.get(url, headers=headers)
with open("recent_smule_page.html", "w", encoding="utf-8") as f:
    f.write(response.text)
print("Saved to recent_smule_page.html")
