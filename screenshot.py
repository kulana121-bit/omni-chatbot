import sys
from playwright.sync_api import sync_playwright
import time

def run():
    with sync_playwright() as p:
        browser = p.chromium.connect_over_cdp("http://localhost:5037")
        context = browser.contexts[0]
        page = context.pages[0]
        page.screenshot(path="screenshot.png")
run()
