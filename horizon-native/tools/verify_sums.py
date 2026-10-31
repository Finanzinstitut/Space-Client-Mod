"""Checks Horizon's SHA256SUMS against its Ed25519 signature, as the Space
Client launcher does (launcher/horizon.rs: the signed message is
"horizon release nightly\\n" followed by the file).

    python3 verify_sums.py SHA256SUMS SHA256SUMS.sig <base64 public key>
"""
import base64
import sys

from cryptography.hazmat.primitives.asymmetric.ed25519 import Ed25519PublicKey


def main():
    sums, sig, key = sys.argv[1:4]
    data = open(sums, "rb").read()
    public = Ed25519PublicKey.from_public_bytes(base64.b64decode(key))
    message = b"horizon release nightly\n" + data
    # One signature per line: any one by this key will do (keys rotate)
    for line in open(sig, "rb").read().splitlines():
        try:
            public.verify(base64.b64decode(line.strip()), message)
            print("SHA256SUMS: signature good")
            return
        except Exception:
            continue
    sys.exit("SHA256SUMS is not signed with Horizon's release key")


if __name__ == "__main__":
    main()
