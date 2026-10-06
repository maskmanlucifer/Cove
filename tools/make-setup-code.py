#!/usr/bin/env python3
"""Builds a Cove setup code: one block of text to paste into Me > Connect services > Paste setup code.

Usage:
  tools/make-setup-code.py                      # asks for each value, press Enter to skip one
  tools/make-setup-code.py --supabase-url https://abcd.supabase.co --gemini-key AIza...

Any subset of the values works. Treat the code like a password: it contains your keys.
"""
import argparse
import base64
import getpass
import json
import sys

FIELDS = [
    ("supabaseUrl", "--supabase-url", "Supabase Project URL (https://<ref>.supabase.co)", False),
    ("supabaseAnonKey", "--anon-key", "Supabase anon public key (starts with eyJ)", True),
    ("googleWebClientId", "--google-client-id", "Google web client ID (ends .apps.googleusercontent.com)", False),
    ("geminiApiKey", "--gemini-key", "Gemini API key (starts with AIza)", True),
    ("geminiModel", "--gemini-model", "Gemini model (optional, e.g. gemini-2.5-flash-lite)", False),
    ("geminiFallbackModel", "--gemini-fallback-model", "Gemini fallback model (optional)", False),
]


def main() -> int:
    parser = argparse.ArgumentParser(description=__doc__, formatter_class=argparse.RawDescriptionHelpFormatter)
    for key, flag, help_text, _ in FIELDS:
        parser.add_argument(flag, dest=key, help=help_text)
    args = parser.parse_args()
    values = {key: (getattr(args, key) or "").strip() for key, *_ in FIELDS}
    if not any(values.values()):
        for key, _, prompt, secret in FIELDS:
            ask = getpass.getpass if secret else input
            values[key] = ask(f"{prompt}: ").strip()
    values = {k: v for k, v in values.items() if v}
    if not values:
        print("Nothing to encode.", file=sys.stderr)
        return 1
    payload = json.dumps(values, separators=(",", ":")).encode()
    print("cove-setup:1:" + base64.urlsafe_b64encode(payload).decode().rstrip("="))
    return 0


if __name__ == "__main__":
    sys.exit(main())
