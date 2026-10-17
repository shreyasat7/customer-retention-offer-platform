#!/usr/bin/env python
"""Capture screenshots of pages that need a login session.

Drives a real headless Chrome through Selenium, signs in where asked, and writes a
full-page retina PNG. Used for the CROP application UI and for the Jenkins interface.

  shoot.py --out FILE --url URL [--login-url U --user U --pass P
            --user-field NAME --pass-field NAME --submit CSS]
           [--click CSS]... [--type CSS=VALUE]... [--wait CSS] [--width N] [--pause S]
"""
import argparse, sys, time
from selenium import webdriver
from selenium.webdriver.chrome.options import Options
from selenium.webdriver.common.by import By
from selenium.webdriver.support.ui import WebDriverWait
from selenium.webdriver.support import expected_conditions as EC


def driver(width, height):
    o = Options()
    o.add_argument("--headless=new")
    o.add_argument(f"--window-size={width},{height}")
    o.add_argument("--force-device-scale-factor=2")
    o.add_argument("--hide-scrollbars")
    o.add_argument("--no-first-run")
    return webdriver.Chrome(options=o)


def main():
    p = argparse.ArgumentParser()
    p.add_argument("--out", required=True)
    p.add_argument("--url", required=True)
    p.add_argument("--login-url")
    p.add_argument("--user")
    p.add_argument("--password")
    p.add_argument("--user-field", default="username")
    p.add_argument("--pass-field", default="password")
    p.add_argument("--submit", default="button[type=submit]")
    p.add_argument("--click", action="append", default=[])
    p.add_argument("--type", action="append", default=[])
    p.add_argument("--wait")
    p.add_argument("--width", type=int, default=1440)
    p.add_argument("--height", type=int, default=900)
    p.add_argument("--pause", type=float, default=1.2)
    p.add_argument("--full", action="store_true", help="resize window to full document height")
    a = p.parse_args()

    d = driver(a.width, a.height)
    try:
        if a.login_url and a.user:
            d.get(a.login_url)
            WebDriverWait(d, 20).until(
                EC.presence_of_element_located((By.NAME, a.user_field)))
            d.find_element(By.NAME, a.user_field).send_keys(a.user)
            d.find_element(By.NAME, a.pass_field).send_keys(a.password)
            d.find_element(By.CSS_SELECTOR, a.submit).click()
            time.sleep(1.5)

        d.get(a.url)
        for spec in a.type:
            sel, _, val = spec.partition("=")
            el = WebDriverWait(d, 20).until(
                EC.presence_of_element_located((By.CSS_SELECTOR, sel)))
            el.clear()
            el.send_keys(val)
        for sel in a.click:
            el = WebDriverWait(d, 20).until(
                EC.element_to_be_clickable((By.CSS_SELECTOR, sel)))
            d.execute_script("arguments[0].click();", el)
            time.sleep(1.0)
        if a.wait:
            WebDriverWait(d, 25).until(
                EC.presence_of_element_located((By.CSS_SELECTOR, a.wait)))
        time.sleep(a.pause)

        if a.full:
            h = d.execute_script(
                "return Math.max(document.body.scrollHeight,"
                "document.documentElement.scrollHeight);")
            d.set_window_size(a.width, min(int(h) + 90, 6000))
            time.sleep(0.7)

        d.save_screenshot(a.out)
        print(f"captured: {a.out}  <- {d.current_url}")
    finally:
        d.quit()


if __name__ == "__main__":
    sys.exit(main())
